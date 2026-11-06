package com.examgrid.backend.portal;

import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.SeatIndex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Unauthenticated, rate-limited result-day endpoints. The seat finder returns only course,
 * time, room and seat (never a name), which is what is pinned on notice boards anyway.
 */
@RestController
@RequestMapping("/api/public")
@SecurityRequirements
@Tag(name = "Public")
public class PublicController {

    private final SeatIndex seats;
    private final JdbcTemplate jdbc;

    public PublicController(SeatIndex seats, JdbcTemplate jdbc) {
        this.seats = seats;
        this.jdbc = jdbc;
    }

    public record SeatLookup(String roll, List<SeatInfo> exams) {
    }

    public record PublicationStatus(boolean published, Instant publishedAt, LocalDate firstExam, LocalDate lastExam,
                                    int exams) {
    }

    @GetMapping("/seat")
    @Operation(summary = "Find my seat: exams, rooms and seats for a roll number")
    public ResponseEntity<SeatLookup> seat(@RequestParam @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,32}") String roll) {
        long runId = seats.publishedRun().orElseThrow(() -> ApiException.notFound("Published timetable", "yet"));
        List<SeatInfo> exams = seats.lookup(runId, roll)
                .orElseThrow(() -> ApiException.notFound("Roll number", roll.toUpperCase(Locale.ROOT)));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(new SeatLookup(roll.toUpperCase(Locale.ROOT), exams));
    }

    @GetMapping("/status")
    @Operation(summary = "Whether a timetable is published, and its date range")
    public PublicationStatus status() {
        List<Map<String, Object>> rows = jdbc.queryForList("select r.published_at, min(sl.slot_date) first_exam, "
                + "max(sl.slot_date) last_exam, count(ea.course_id) exams from schedule_run r "
                + "join exam_assignment ea on ea.run_id = r.id join exam_slot sl on sl.id = ea.slot_id "
                + "where r.status = 'PUBLISHED' group by r.published_at");
        if (rows.isEmpty()) {
            return new PublicationStatus(false, null, null, null, 0);
        }
        Map<String, Object> r = rows.getFirst();
        return new PublicationStatus(true, ((java.sql.Timestamp) r.get("published_at")).toInstant(),
                ((java.sql.Date) r.get("first_exam")).toLocalDate(), ((java.sql.Date) r.get("last_exam")).toLocalDate(),
                ((Number) r.get("exams")).intValue());
    }
}
