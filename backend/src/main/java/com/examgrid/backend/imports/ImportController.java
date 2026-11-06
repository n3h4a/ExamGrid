package com.examgrid.backend.imports;

import com.examgrid.backend.common.api.PageResponse;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.storage.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

@RestController
@RequestMapping("/api/imports")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Imports")
public class ImportController {

    private final ImportService service;
    private final ImportBatchRepository batches;
    private final FileStorage storage;
    private final ObjectMapper mapper;

    public ImportController(ImportService service, ImportBatchRepository batches, FileStorage storage,
                            ObjectMapper mapper) {
        this.service = service;
        this.batches = batches;
        this.storage = storage;
        this.mapper = mapper;
    }

    public record BatchSummary(long id, ImportKind kind, String filename, long sizeBytes, String sha256,
                               boolean dryRun, ImportBatch.Status status, int totalRows, int acceptedRows,
                               int errorCount, int warningCount, String createdBy, Instant createdAt) {
        static BatchSummary of(ImportBatch b) {
            return new BatchSummary(b.getId(), b.getKind(), b.getOriginalFilename(), b.getSizeBytes(), b.getSha256(),
                    b.isDryRun(), b.getStatus(), b.getTotalRows(), b.getAcceptedRows(), b.getErrorCount(),
                    b.getWarningCount(), b.getCreatedBy(), b.getCreatedAt());
        }
    }

    public record BatchDetail(BatchSummary batch, JsonNode report) {
    }

    @PostMapping(path = "/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV or XLSX file. dryRun=true (default) only validates; "
            + "mode=REPLACE makes an enrolment file the complete list")
    public BatchDetail upload(@PathVariable String kind, @RequestPart("file") MultipartFile file,
                              @RequestParam(defaultValue = "true") boolean dryRun,
                              @RequestParam(defaultValue = "MERGE") ImportMode mode) throws IOException {
        if (file.isEmpty()) {
            throw ApiException.badRequest("empty_file", "The file is empty.");
        }
        ImportService.Outcome outcome = service.importFile(ImportKind.fromPath(kind), file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), dryRun, mode);
        return new BatchDetail(BatchSummary.of(outcome.batch()), mapper.valueToTree(outcome.report()));
    }

    @GetMapping
    public PageResponse<BatchSummary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(batches.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)),
                BatchSummary::of);
    }

    @GetMapping("/{id}")
    public BatchDetail get(@PathVariable long id) {
        ImportBatch b = batches.findById(id).orElseThrow(() -> ApiException.notFound("Import", id));
        return new BatchDetail(BatchSummary.of(b), mapper.readTree(b.getReport()));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Download the exact file that was uploaded")
    public ResponseEntity<InputStreamResource> file(@PathVariable long id) {
        ImportBatch b = batches.findById(id).orElseThrow(() -> ApiException.notFound("Import", id));
        InputStream in = storage.open(b.getStorageKey())
                .orElseThrow(() -> ApiException.notFound("Stored file for import", id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(b.getOriginalFilename()).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(b.getSizeBytes())
                .body(new InputStreamResource(in));
    }

    @GetMapping(path = "/templates/{kind}", produces = "text/csv")
    @Operation(summary = "CSV template with the expected columns and example rows")
    public ResponseEntity<byte[]> template(@PathVariable String kind) {
        ImportKind k = ImportKind.fromPath(kind);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(k.name().toLowerCase() + "-template.csv").build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(ImportService.template(k));
    }
}
