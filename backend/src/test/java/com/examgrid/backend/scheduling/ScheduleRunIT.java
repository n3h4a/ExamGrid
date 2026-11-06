package com.examgrid.backend.scheduling;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.CatalogFixture;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ScheduleRunIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, mapper);
        admin = api.adminToken();
        CatalogFixture.reset(jdbc);
    }

    @Test
    void refusesToRunWithoutData() throws Exception {
        api.post("/api/schedule-runs", admin, Map.of())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("not_ready"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("enrolments")));
    }

    @Test
    void acceptedRunProducesAValidStoredPlan() throws Exception {
        CatalogFixture.loadSamples(api, admin, 6);
        JsonNode accepted = api.read(api.post("/api/schedule-runs", admin, Map.of("label", "nightly", "timeLimitSeconds", 20))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/schedule-runs/")))
                .andExpect(jsonPath("$.status").value("QUEUED")));
        long id = accepted.get("id").asLong();
        JsonNode done = awaitStatus(id, Set.of("DONE"));

        assertThat(done.at("/run/label").asString()).isEqualTo("nightly");
        assertThat(done.at("/params/timeLimitSeconds").asInt()).isEqualTo(20);
        assertThat(done.at("/params/optimizer").asString()).isEqualTo("ANNEALING");
        assertThat(done.at("/metrics/solver/timetable/clashPairs").asInt()).isZero();
        assertThat(done.at("/metrics/slots").size()).isEqualTo(12);
        assertThat(done.at("/inputSummary/enrolments").asInt()).isEqualTo(486);
        assertThat(done.at("/inputSummary/fingerprint").asString()).hasSize(64);
        assertThat(done.get("dataChanged").asBoolean()).isFalse();

        long seats = jdbc.queryForObject("select count(*) from seat_assignment where run_id = ?", Long.class, id);
        assertThat(seats).isEqualTo(486);
        JsonNode timetable = api.read(api.get("/api/schedule-runs/" + id + "/timetable", admin).andExpect(status().isOk()));
        assertThat(timetable.size()).isEqualTo(24);
        String slot = timetable.get(0).get("slot").asString();
        JsonNode rooms = api.read(api.get("/api/schedule-runs/" + id + "/rooms?slot=" + slot, admin));
        assertThat(rooms.get(0).get("seats").size()).isPositive();
        assertThat(rooms.get(0).get("invigilators").size()).isPositive();
        api.get("/api/schedule-runs/" + id + "/students/24cs001", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exams.length()").value(4));
        api.get("/api/schedule-runs/" + id + "/invigilation?faculty=F001", admin).andExpect(status().isOk());

        // Changing the catalog marks the plan as stale.
        api.post("/api/courses", admin, Map.of("code", "NEW101", "name", "New"));
        assertThat(api.read(api.get("/api/schedule-runs/" + id, admin)).get("dataChanged").asBoolean()).isTrue();
    }

    @Test
    void infeasibleInputsProduceAnExplainedReport() throws Exception {
        CatalogFixture.loadSamples(api, admin, 6);
        // CS201 and CS202 share every 2nd-year CS student; pinning both to one slot is impossible.
        api.put("/api/locks/CS201", admin, Map.of("slotKey", "2026-12-07-MORNING")).andExpect(status().isOk());
        api.put("/api/locks/CS202", admin, Map.of("slotKey", "2026-12-07-MORNING")).andExpect(status().isOk());
        long id = api.read(api.post("/api/schedule-runs", admin, Map.of())).get("id").asLong();
        JsonNode run = awaitStatus(id, Set.of("INFEASIBLE"));
        JsonNode issue = run.at("/infeasibility/issues/0");
        assertThat(issue.get("type").asString()).isEqualTo("LOCK_CONFLICT");
        assertThat(issue.get("proof").asBoolean()).isTrue();
        assertThat(issue.get("students").get(0).asString()).startsWith("24CS");
        api.get("/api/schedule-runs/" + id + "/timetable", admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("no_plan"));
    }

    @Test
    void runningSolvesCanBeCancelled() throws Exception {
        // Three days for students with four exams: some same-day pairs are unavoidable, so the
        // optimiser never reaches zero and keeps going until cancelled.
        CatalogFixture.loadSamples(api, admin, 3);
        long id = api.read(api.post("/api/schedule-runs", admin, Map.of("timeLimitSeconds", 60,
                "maxIterations", 20_000_000))).get("id").asLong();
        awaitStatus(id, Set.of("RUNNING"));
        Thread.sleep(500);
        api.post("/api/schedule-runs/" + id + "/cancel", admin, Map.of()).andExpect(status().isOk());
        awaitStatus(id, Set.of("CANCELLED"));
        api.post("/api/schedule-runs/" + id + "/cancel", admin, Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_active"));
        api.delete("/api/schedule-runs/" + id, admin).andExpect(status().isNoContent());
    }

    @Test
    void eventStreamOfAFinishedRunSendsTheFinalStatus() throws Exception {
        CatalogFixture.loadSamples(api, admin, 6);
        long id = api.read(api.post("/api/schedule-runs", admin, Map.of())).get("id").asLong();
        awaitStatus(id, Set.of("DONE"));
        MvcResult result = mvc.perform(get("/api/schedule-runs/" + id + "/events")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(request().asyncStarted())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:status").contains("\"status\":\"DONE\"");
    }

    @Test
    void runsCanBeComparedAndOnlyExamCellMayStartThem() throws Exception {
        CatalogFixture.loadSamples(api, admin, 6);
        long a = api.read(api.post("/api/schedule-runs", admin, Map.of("optimizer", "NONE"))).get("id").asLong();
        long b = api.read(api.post("/api/schedule-runs", admin, Map.of("optimizer", "ANNEALING"))).get("id").asLong();
        awaitStatus(a, Set.of("DONE"));
        awaitStatus(b, Set.of("DONE"));
        JsonNode compare = api.read(api.get("/api/schedule-runs/compare?ids=" + a + "," + b, admin)
                .andExpect(status().isOk()));
        long noOpt = compare.get(0).at("/run/score").asLong();
        long annealed = compare.get(1).at("/run/score").asLong();
        assertThat(annealed).isLessThanOrEqualTo(noOpt);

        api.post("/api/users", admin, Map.of("username", "runstudent", "displayName", "S", "password", "password123",
                "role", "STUDENT", "studentRoll", "24CS002"));
        api.post("/api/schedule-runs", api.login("runstudent", "password123"), Map.of())
                .andExpect(status().isForbidden());
        api.post("/api/schedule-runs", admin, Map.of("timeLimitSeconds", 0))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation_failed"));
    }

    private JsonNode awaitStatus(long id, Set<String> wanted) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        JsonNode run;
        do {
            run = api.read(api.get("/api/schedule-runs/" + id, admin));
            String status = run.at("/run/status").asString();
            if (wanted.contains(status)) {
                return run;
            }
            assertThat(status).as("run %d ended unexpectedly: %s", id, run).isIn("QUEUED", "RUNNING");
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("run " + id + " did not reach " + wanted + ": " + run);
    }
}
