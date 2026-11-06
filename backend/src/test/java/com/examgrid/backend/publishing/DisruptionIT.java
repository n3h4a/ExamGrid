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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DisruptionIT {

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

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, mapper);
        admin = api.adminToken();
        CatalogFixture.reset(jdbc, redis);
        CatalogFixture.loadSamples(api, admin, 6);
    }

    @Test
    void lostRoomCreatesADerivedRunThatMovesOnlyItsStudents() throws Exception {
        long parent = api.read(api.post("/api/schedule-runs", admin, Map.of("timeLimitSeconds", 10))).get("id").asLong();
        awaitDone(parent);
        Map<String, Object> busiest = jdbc.queryForMap("select r.code room, to_char(sl.slot_date, 'YYYY-MM-DD') || '-' "
                + "|| sl.session slot, count(*) n from seat_assignment sa join room r on r.id = sa.room_id "
                + "join exam_slot sl on sl.id = sa.slot_id where sa.run_id = ? group by 1, 2 order by 3 desc limit 1", parent);
        String room = (String) busiest.get("room");
        String slot = (String) busiest.get("slot");
        long displaced = ((Number) busiest.get("n")).longValue();

        JsonNode child = api.read(api.post("/api/schedule-runs/" + parent + "/disruptions", admin,
                Map.of("roomCode", room.toLowerCase(), "slot", slot)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentRunId").value(parent))
                .andExpect(jsonPath("$.status").value("DONE")));
        long childId = child.get("id").asLong();
        JsonNode detail = api.read(api.get("/api/schedule-runs/" + childId, admin));
        assertThat(detail.at("/disruption/studentsMoved").asLong()).isEqualTo(displaced);
        assertThat(detail.at("/disruption/summary").asString()).contains("0 others affected");

        long inLostRoom = jdbc.queryForObject("select count(*) from seat_assignment sa join room r on r.id = sa.room_id "
                + "join exam_slot sl on sl.id = sa.slot_id where sa.run_id = ? and r.code = ? "
                + "and to_char(sl.slot_date, 'YYYY-MM-DD') || '-' || sl.session = ?", Long.class, childId, room, slot);
        assertThat(inLostRoom).isZero();
        long unchanged = jdbc.queryForObject("select count(*) from seat_assignment a join seat_assignment b "
                + "on b.run_id = ? and b.student_id = a.student_id and b.course_id = a.course_id "
                + "and b.room_id = a.room_id and b.seat_row = a.seat_row and b.seat_col = a.seat_col "
                + "where a.run_id = ?", Long.class, childId, parent);
        assertThat(unchanged).isEqualTo(486 - displaced);

        api.post("/api/schedule-runs/" + parent + "/disruptions", admin, Map.of("roomCode", "NOPE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_room"));
        api.delete("/api/schedule-runs/" + parent, admin)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("run_has_children"));
    }

    private void awaitDone(long id) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            if ("DONE".equals(api.read(api.get("/api/schedule-runs/" + id, admin)).at("/run/status").asString())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run " + id + " did not finish");
    }
}
