package com.examgrid.backend.publishing;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.CatalogFixture;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PublishingIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    ApiClient api;
    String admin;
    String approver;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, mapper);
        admin = api.adminToken();
        CatalogFixture.reset(jdbc, redis);
        CatalogFixture.loadSamples(api, admin, 6);
        ensureUser("approver2", "EXAM_CELL", null, null);
        approver = api.login("approver2", "password123");
    }

    @Test
    void twoDistinctApprovalsPublishDocumentsAndTheSeatIndex() throws Exception {
        api.get("/api/public/seat?roll=24CS001", null).andExpect(status().isNotFound());
        long run = solvedRun();

        api.post("/api/schedule-runs/" + run + "/approvals", admin, Map.of("comment", "looks good"))
                .andExpect(jsonPath("$.approvals").value(1)).andExpect(jsonPath("$.publishing").value(false));
        api.post("/api/schedule-runs/" + run + "/approvals", admin, Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_approved"));
        api.post("/api/schedule-runs/" + run + "/approvals", approver, Map.of())
                .andExpect(jsonPath("$.approvals").value(2)).andExpect(jsonPath("$.publishing").value(true));
        JsonNode published = awaitStatus(run, "PUBLISHED");
        assertThat(published.get("approvals").size()).isEqualTo(2);
        assertThat(published.at("/approvals/0/comment").asString()).isEqualTo("looks good");

        assertThat(count("select count(*) from published_document where run_id = ? and kind = 'HALL_TICKET'", run))
                .isEqualTo(120);
        assertThat(count("select count(*) from published_document where run_id = ? and kind = 'TIMETABLE'", run))
                .isEqualTo(1);
        assertThat(count("select count(*) from published_document where run_id = ? and kind = 'SEATING'", run))
                .isPositive();
        long ticket = jdbc.queryForObject("select id from published_document where run_id = ? and kind = 'HALL_TICKET' "
                + "and ref = '24CS001'", Long.class, run);
        byte[] pdf = api.get("/api/schedule-runs/" + run + "/documents/" + ticket, admin)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        JsonNode seat = api.read(api.get("/api/public/seat?roll=24cs001", null).andExpect(status().isOk()));
        assertThat(seat.get("roll").asString()).isEqualTo("24CS001");
        assertThat(seat.get("exams").size()).isEqualTo(4);
        assertThat(seat.toString()).doesNotContain("Aarav").doesNotContain("\"name\"");
        assertThat(redis.hasKey(SeatIndex.key(run, "24CS001"))).isTrue();
        awaitRedis(() -> String.valueOf(run).equals(redis.opsForValue().get(SeatIndex.POINTER)));
        api.get("/api/public/seat?roll=99ZZ999", null).andExpect(status().isNotFound());
        api.get("/api/public/status", null).andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.exams").value(24));
    }

    @Test
    void seatLookupFallsBackToPostgresAndRepopulatesRedis() throws Exception {
        long run = publish(solvedRun());
        awaitRedis(() -> String.valueOf(run).equals(redis.opsForValue().get(SeatIndex.POINTER)));
        redis.delete(SeatIndex.key(run, "24EC003"));
        redis.delete(SeatIndex.POINTER);
        api.get("/api/public/seat?roll=24EC003", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.exams.length()").value(4));
        assertThat(redis.hasKey(SeatIndex.key(run, "24EC003"))).isTrue();
        assertThat(redis.opsForValue().get(SeatIndex.POINTER)).isEqualTo(String.valueOf(run));
    }

    @Test
    void portalsServeOnlyTheCallersOwnData() throws Exception {
        long run = publish(solvedRun());
        ensureUser("s24cs001", "STUDENT", "24CS001", null);
        ensureUser("f001", "FACULTY", null, "F001");
        String student = api.login("s24cs001", "password123");
        String faculty = api.login("f001", "password123");

        api.get("/api/students/me/exams", student).andExpect(status().isOk())
                .andExpect(jsonPath("$.roll").value("24CS001"))
                .andExpect(jsonPath("$.exams.length()").value(4));
        byte[] ticket = api.get("/api/students/me/hall-ticket", student).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(ticket, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        JsonNode duties = api.read(api.get("/api/faculty/me/duties", faculty).andExpect(status().isOk()));
        assertThat(duties).allSatisfy(d -> assertThat(d.get("facultyCode").asString()).isEqualTo("F001"));

        api.get("/api/students/me/exams", faculty).andExpect(status().isForbidden());
        api.get("/api/faculty/me/duties", student).andExpect(status().isForbidden());
        api.get("/api/schedule-runs/" + run, student).andExpect(status().isForbidden());
        api.get("/api/students/me/exams", admin).andExpect(status().isForbidden());
    }

    @Test
    void publishingANewRunSupersedesTheOldOne() throws Exception {
        long first = publish(solvedRun());
        long second = publish(solvedRun());
        assertThat(api.read(api.get("/api/schedule-runs/" + first, admin)).at("/run/status").asString())
                .isEqualTo("SUPERSEDED");
        assertThat(count("select count(*) from schedule_run where status = 'PUBLISHED'")).isEqualTo(1);
        // The Redis switch happens right after the database commit, so it is awaited.
        awaitRedis(() -> String.valueOf(second).equals(redis.opsForValue().get(SeatIndex.POINTER))
                && redis.getExpire(SeatIndex.key(first, "24CS001")) > 0);
        api.delete("/api/schedule-runs/" + second, admin).andExpect(status().isConflict());
    }

    @Test
    void plansBuiltFromStaleDataCannotBeApproved() throws Exception {
        long run = solvedRun();
        api.post("/api/courses", admin, Map.of("code", "LATE101", "name", "Added later"));
        api.post("/api/schedule-runs/" + run + "/approvals", admin, Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("data_changed"));
    }

    @Test
    void approvalsCanBeWithdrawnBeforePublishing() throws Exception {
        long run = solvedRun();
        api.post("/api/schedule-runs/" + run + "/approvals", admin, Map.of()).andExpect(status().isOk());
        api.delete("/api/schedule-runs/" + run + "/approvals/me", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvals").value(0));
        api.delete("/api/schedule-runs/" + run + "/approvals/me", admin).andExpect(status().isNotFound());
    }

    @Test
    void publicEndpointsAreRateLimitedPerClient() throws Exception {
        int limit = 25;
        for (int i = 0; i < limit; i++) {
            mvc.perform(get("/api/public/status").with(r -> {
                r.setRemoteAddr("10.42.0.7");
                return r;
            })).andExpect(status().isOk()).andExpect(header().exists("X-RateLimit-Remaining"));
        }
        mvc.perform(get("/api/public/status").with(r -> {
                    r.setRemoteAddr("10.42.0.7");
                    return r;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
        mvc.perform(get("/api/public/status").with(r -> {
            r.setRemoteAddr("10.42.0.8");
            return r;
        })).andExpect(status().isOk());
    }

    private static void awaitRedis(java.util.function.BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean()) {
            assertThat(Instant.now()).as("Redis did not reach the expected state").isBefore(deadline);
            Thread.sleep(50);
        }
    }

    private long solvedRun() throws Exception {
        long id = api.read(api.post("/api/schedule-runs", admin, Map.of("timeLimitSeconds", 10))).get("id").asLong();
        awaitStatus(id, "DONE");
        return id;
    }

    private long publish(long run) throws Exception {
        api.post("/api/schedule-runs/" + run + "/approvals", admin, Map.of()).andExpect(status().isOk());
        api.post("/api/schedule-runs/" + run + "/approvals", approver, Map.of()).andExpect(status().isOk());
        awaitStatus(run, "PUBLISHED");
        return run;
    }

    private void ensureUser(String username, String role, String roll, String faculty) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("username", username, "displayName", username,
                "password", "password123", "role", role));
        if (roll != null) {
            body.put("studentRoll", roll);
        }
        if (faculty != null) {
            body.put("facultyCode", faculty);
        }
        int status = api.post("/api/users", admin, body).andReturn().getResponse().getStatus();
        assertThat(status).isIn(201, 409);
    }

    private JsonNode awaitStatus(long id, String wanted) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        JsonNode run;
        do {
            run = api.read(api.get("/api/schedule-runs/" + id, admin));
            if (wanted.equals(run.at("/run/status").asString())) {
                return run;
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("run " + id + " did not reach " + wanted + ": " + run);
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
