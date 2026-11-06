package com.examgrid.backend.portal;

import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.SeatIndex;
import com.examgrid.backend.scheduling.PlanQueries;
import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.security.CurrentUser;
import com.examgrid.backend.storage.FileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Personal views for students and faculty. The identity comes only from the verified JWT
 * ({@code roll} / {@code faculty} claims): there is no path or query parameter to change, so
 * one student can never fetch another student's hall ticket (no IDOR).
 */
@RestController
@Tag(name = "Portals")
public class PortalController {

    private final SeatIndex seats;
    private final PlanQueries plans;
    private final FileStorage storage;
    private final JdbcTemplate jdbc;

    public PortalController(SeatIndex seats, PlanQueries plans, FileStorage storage, JdbcTemplate jdbc) {
        this.seats = seats;
        this.plans = plans;
        this.storage = storage;
        this.jdbc = jdbc;
    }

    public record MyExams(String roll, String name, List<SeatInfo> exams) {
    }

    @GetMapping("/api/students/me/exams")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My exams, rooms and seats in the published timetable")
    public MyExams myExams() {
        String roll = CurrentUser.require().requireStudentRoll();
        long runId = publishedRun();
        List<SeatInfo> exams = seats.lookup(runId, roll).orElse(List.of());
        List<String> name = jdbc.queryForList("select name from student where roll = ?", String.class, roll);
        return new MyExams(roll, name.isEmpty() ? "" : name.getFirst(), exams);
    }

    @GetMapping("/api/students/me/hall-ticket")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Download my hall ticket (PDF)")
    public ResponseEntity<InputStreamResource> myHallTicket() {
        String roll = CurrentUser.require().requireStudentRoll();
        long runId = publishedRun();
        List<String> keys = jdbc.queryForList("select storage_key from published_document where run_id = ? "
                + "and kind = 'HALL_TICKET' and ref = ?", String.class, runId, roll);
        if (keys.isEmpty()) {
            throw ApiException.notFound("Hall ticket for", roll);
        }
        var in = storage.open(keys.getFirst()).orElseThrow(() -> ApiException.notFound("Hall ticket for", roll));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("hall-ticket-" + roll + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(in));
    }

    @GetMapping("/api/faculty/me/duties")
    @PreAuthorize("hasRole('FACULTY')")
    @Operation(summary = "My invigilation duties in the published timetable")
    public List<InvigilationEntry> myDuties() {
        String code = CurrentUser.require().requireFacultyCode();
        return plans.invigilation(publishedRun(), code);
    }

    private long publishedRun() {
        return seats.publishedRun().orElseThrow(() -> ApiException.notFound("Published timetable", "yet"));
    }
}
