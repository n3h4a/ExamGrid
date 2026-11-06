package com.examgrid.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resets the catalog and loads the {@code samples/} university through the real import API.
 * Scheduling tests solve the whole catalog, so they need a known, isolated dataset.
 */
public final class CatalogFixture {

    private static final Path SAMPLES = Path.of("..", "samples");

    private CatalogFixture() {
    }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute("truncate table approval, invigilation, seat_assignment, exam_assignment, schedule_run, "
                + "faculty_unavailability, faculty_course, faculty, course_lock, same_slot_group_member, "
                + "same_slot_group, enrolment, student, course, room, exam_slot, import_batch restart identity cascade");
    }

    /** Loads the samples with {@code days} exam days (2 sessions each) from 2026-12-07. */
    public static void loadSamples(ApiClient api, String admin, int days) throws Exception {
        load(api, admin, "courses");
        load(api, admin, "rooms");
        String end = java.time.LocalDate.of(2026, 12, 7).plusDays(days - 1).toString();
        api.post("/api/slots/generate", admin, Map.of("startDate", "2026-12-07", "endDate", end,
                "sessions", List.of("MORNING", "AFTERNOON")));
        load(api, admin, "enrolments");
        if (days >= 3) {
            load(api, admin, "faculty");
        }
    }

    private static void load(ApiClient api, String admin, String kind) throws Exception {
        var r = api.read(api.upload("/api/imports/" + kind + "?dryRun=false", admin, kind + ".csv",
                Files.readAllBytes(SAMPLES.resolve(kind + ".csv"))));
        assertThat(r.at("/batch/status").asString()).as(r.toString()).isEqualTo("COMMITTED");
    }
}
