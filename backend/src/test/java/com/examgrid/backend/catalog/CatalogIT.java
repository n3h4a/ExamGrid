package com.examgrid.backend.catalog;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import com.examgrid.backend.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CatalogIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String admin;
    String dept;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, mapper);
        admin = api.adminToken();
        dept = TestData.letters();
    }

    @Test
    void courseLifecycleWithNormalisedImmutableCodes() throws Exception {
        String code = dept.toLowerCase() + "101";
        long id = api.read(api.post("/api/courses", admin, Map.of("code", code, "name", "Algorithms", "hard", true,
                        "allowedSessions", List.of("MORNING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code.toUpperCase()))).get("id").asLong();
        api.post("/api/courses", admin, Map.of("code", code, "name", "Again"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("duplicate_code"));
        api.put("/api/courses/" + id, admin, Map.of("code", code, "name", "Algorithms II", "hard", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Algorithms II"))
                .andExpect(jsonPath("$.allowedSessions").isEmpty());
        api.put("/api/courses/" + id, admin, Map.of("code", "OTHER1", "name", "X"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("code_immutable"));
        api.get("/api/courses?q=" + dept.toLowerCase(), admin).andExpect(jsonPath("$.totalItems").value(1));
        api.delete("/api/courses/" + id, admin).andExpect(status().isNoContent());
        api.get("/api/courses/" + id, admin).andExpect(status().isNotFound());
    }

    @Test
    void studentsCarryTheirEnrolments() throws Exception {
        api.post("/api/courses", admin, Map.of("code", dept + "201", "name", "A"));
        api.post("/api/courses", admin, Map.of("code", dept + "202", "name", "B"));
        long id = api.read(api.post("/api/students", admin, Map.of("roll", TestData.roll(dept, 1), "name", "Asha",
                        "courses", List.of(dept + "201", dept + "202")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courses.length()").value(2))).at("/student/id").asLong();
        api.put("/api/students/" + id, admin, Map.of("roll", TestData.roll(dept, 1), "name", "Asha R",
                "courses", List.of(dept + "202"))).andExpect(jsonPath("$.courses[0]").value(dept + "202"));
        api.post("/api/students", admin, Map.of("roll", TestData.roll(dept, 2), "courses", List.of("NOPE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_course"));
        long courseId = api.read(api.get("/api/courses?q=" + dept + "202", admin)).at("/items/0/id").asLong();
        api.get("/api/courses/" + courseId + "/students", admin)
                .andExpect(jsonPath("$.items[0].roll").value(TestData.roll(dept, 1)));
        api.get("/api/courses/" + courseId, admin).andExpect(jsonPath("$.candidates").value(1));
    }

    @Test
    void roomsValidateTheirSeatGrid() throws Exception {
        api.post("/api/rooms", admin, Map.of("code", dept + "R1", "building", "Main", "rows", 4, "columns", 5,
                        "blockedSeats", List.of("a1", "D5", "A1"), "priority", 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(18))
                .andExpect(jsonPath("$.blockedSeats[0]").value("A1"))
                .andExpect(jsonPath("$.blockedSeats.length()").value(2));
        api.post("/api/rooms", admin, Map.of("code", dept + "R2", "rows", 4, "columns", 5, "blockedSeats", List.of("E1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_seat"));
        api.post("/api/rooms", admin, Map.of("code", dept + "R3", "rows", 1, "columns", 1, "blockedSeats", List.of("A1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("no_usable_seats"));
        api.post("/api/rooms", admin, Map.of("code", dept + "R4", "rows", 0, "columns", 5))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[*].field", hasItem("rows")));
    }

    @Test
    void slotGeneratorSkipsWeekdaysHolidaysAndExistingSlots() throws Exception {
        // 2032-01-05 is a Monday; Sunday 2032-01-11 and the holiday 2032-01-07 are skipped.
        Map<String, Object> request = Map.of("startDate", "2032-01-05", "endDate", "2032-01-11",
                "sessions", List.of("MORNING", "AFTERNOON"), "skipWeekdays", List.of("SUNDAY"),
                "skipDates", List.of("2032-01-07"));
        JsonNode created = api.read(api.post("/api/slots/generate", admin, request).andExpect(status().isCreated()));
        assertThat(created.size()).isEqualTo(10);
        assertThat(created.get(0).get("key").asString()).isEqualTo("2032-01-05-MORNING");
        assertThat(api.read(api.post("/api/slots/generate", admin, request)).size()).isZero();
        api.post("/api/slots/generate", admin, Map.of("startDate", "2032-02-10", "endDate", "2032-02-01",
                "sessions", List.of("MORNING"))).andExpect(jsonPath("$.code").value("invalid_range"));
    }

    @Test
    void locksRespectSessionRulesAndCanBeMoved() throws Exception {
        api.post("/api/courses", admin, Map.of("code", dept + "391", "name", "Lab", "allowedSessions",
                List.of("AFTERNOON")));
        api.post("/api/slots", admin, Map.of("date", "2033-04-04", "session", "MORNING"));
        api.post("/api/slots", admin, Map.of("date", "2033-04-04", "session", "AFTERNOON"));
        api.put("/api/locks/" + dept + "391", admin, Map.of("slotKey", "2033-04-04-MORNING"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("session_not_allowed"));
        api.put("/api/locks/" + dept + "391", admin, Map.of("slotKey", "2033-04-04-AFTERNOON"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lockedBy").value("admin"));
        api.get("/api/locks", admin).andExpect(jsonPath("$[*].courseCode", hasItem(dept + "391")));
        api.delete("/api/locks/" + dept + "391", admin).andExpect(status().isNoContent());
        api.delete("/api/locks/" + dept + "391", admin).andExpect(status().isNotFound());
    }

    @Test
    void aCourseBelongsToAtMostOneSameSlotGroup() throws Exception {
        for (String c : List.of("1", "2", "3")) {
            api.post("/api/courses", admin, Map.of("code", dept + "C" + c, "name", c));
        }
        api.post("/api/same-slot-groups", admin, Map.of("name", dept + " maths", "courses",
                List.of(dept + "C1", dept + "C2"))).andExpect(status().isCreated());
        api.post("/api/same-slot-groups", admin, Map.of("name", dept + " other", "courses",
                        List.of(dept + "C2", dept + "C3")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("course_in_other_group"));
        api.post("/api/same-slot-groups", admin, Map.of("name", dept + " single", "courses", List.of(dept + "C3")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facultyUnavailabilityAcceptsWholeDays() throws Exception {
        api.post("/api/courses", admin, Map.of("code", dept + "T1", "name", "Taught"));
        api.post("/api/slots", admin, Map.of("date", "2034-05-08", "session", "MORNING"));
        api.post("/api/slots", admin, Map.of("date", "2034-05-08", "session", "AFTERNOON"));
        api.post("/api/faculty", admin, Map.of("code", dept + "F1", "name", "Dr Rao", "teaches", List.of(dept + "T1"),
                        "unavailableSlots", List.of("2034-05-08")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unavailableSlots.length()").value(2))
                .andExpect(jsonPath("$.teaches[0]").value(dept + "T1"));
        api.post("/api/faculty", admin, Map.of("code", dept + "F2", "name", "Dr X", "unavailableSlots",
                List.of("2099-01-01"))).andExpect(jsonPath("$.code").value("unknown_slot"));
    }

    @Test
    void summaryAndSearchCoverTheCatalog() throws Exception {
        api.post("/api/courses", admin, Map.of("code", dept + "S1", "name", "Searchable " + dept));
        api.post("/api/students", admin, Map.of("roll", TestData.roll(dept, 5), "name", "Findme " + dept));
        JsonNode summary = api.read(api.get("/api/catalog/summary", admin).andExpect(status().isOk()));
        assertThat(summary.get("courses").asLong()).isPositive();
        assertThat(summary.get("students").asLong()).isPositive();
        JsonNode hits = api.read(api.get("/api/search?q=" + dept, admin).andExpect(status().isOk()));
        assertThat(hits.get("courses").get(0).get("code").asString()).isEqualTo(dept + "S1");
        assertThat(hits.get("students").get(0).get("code").asString()).isEqualTo(TestData.roll(dept, 5));
        api.get("/api/search?q=a", admin).andExpect(status().isBadRequest());
    }

    @Test
    void deletingACourseRemovesItsEnrolments() throws Exception {
        long courseId = api.read(api.post("/api/courses", admin, Map.of("code", dept + "D1", "name", "Doomed")))
                .get("id").asLong();
        api.post("/api/students", admin, Map.of("roll", TestData.roll(dept, 9), "courses", List.of(dept + "D1")));
        api.delete("/api/courses/" + courseId, admin).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from enrolment where course_id = ?", Long.class, courseId))
                .isZero();
    }
}
