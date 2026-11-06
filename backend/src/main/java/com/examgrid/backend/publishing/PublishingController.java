package com.examgrid.backend.publishing;

import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.scheduling.RunViews.RunSummary;
import com.examgrid.backend.scheduling.ScheduleRunService;
import com.examgrid.backend.storage.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/schedule-runs/{id}")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Publishing")
public class PublishingController {

    private final PublicationService publication;
    private final DisruptionService disruptions;
    private final ScheduleRunService runs;
    private final FileStorage storage;
    private final JdbcTemplate jdbc;

    public PublishingController(PublicationService publication, DisruptionService disruptions,
                                ScheduleRunService runs, FileStorage storage, JdbcTemplate jdbc) {
        this.publication = publication;
        this.disruptions = disruptions;
        this.runs = runs;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    public record ApprovalRequest(@Size(max = 500) String comment) {
    }

    public record DisruptionBody(@NotBlank @Size(max = 32) String roomCode, @Size(max = 32) String slot,
                                 @Size(max = 120) String label) {
    }

    public record DocumentView(long id, String kind, String ref, long sizeBytes, Instant createdAt) {
    }

    @PostMapping("/approvals")
    @Operation(summary = "Approve a finished plan; the second distinct approval starts publishing")
    public PublicationService.ApprovalResult approve(@PathVariable long id,
                                                     @Valid @RequestBody(required = false) ApprovalRequest body) {
        return publication.approve(id, body == null ? null : body.comment());
    }

    @DeleteMapping("/approvals/me")
    @Operation(summary = "Withdraw your approval before the plan is published")
    public PublicationService.ApprovalResult withdraw(@PathVariable long id) {
        return publication.withdraw(id);
    }

    @PostMapping("/publish")
    @Operation(summary = "Retry a publication that failed")
    public PublicationService.ApprovalResult retry(@PathVariable long id) {
        return publication.retry(id);
    }

    @PostMapping("/disruptions")
    @Operation(summary = "Re-seat the students of a lost room into a new derived run")
    public ResponseEntity<RunSummary> disrupt(@PathVariable long id, @Valid @RequestBody DisruptionBody body) {
        var child = disruptions.disrupt(id, new DisruptionService.DisruptionRequest(body.roomCode(), body.slot(),
                body.label()));
        return ResponseEntity.created(URI.create("/api/schedule-runs/" + child.getId())).body(RunSummary.of(child));
    }

    @GetMapping("/documents")
    public List<DocumentView> documents(@PathVariable long id, @RequestParam(required = false) String kind) {
        runs.get(id);
        String filter = kind == null ? "" : " and kind = ?";
        Object[] args = kind == null ? new Object[] {id} : new Object[] {id, kind.toUpperCase()};
        return jdbc.query("select id, kind, ref, size_bytes, created_at from published_document where run_id = ?"
                        + filter + " order by kind, ref",
                (rs, i) -> new DocumentView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getTimestamp(5).toInstant()), args);
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<InputStreamResource> document(@PathVariable long id, @PathVariable long docId) {
        List<String[]> rows = jdbc.query("select storage_key, kind, ref from published_document where id = ? and run_id = ?",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2), rs.getString(3)}, docId, id);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Document", docId);
        }
        String[] row = rows.getFirst();
        return pdf(storage, row[0], row[1].toLowerCase() + "-" + row[2] + ".pdf");
    }

    static ResponseEntity<InputStreamResource> pdf(FileStorage storage, String key, String filename) {
        var in = storage.open(key).orElseThrow(() -> ApiException.notFound("Stored document", key));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(in));
    }
}
