package com.examgrid.backend.user;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class UserIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, mapper);
    }

    @Test
    void examCellCreatesStudentsWhoCannotManageUsers() throws Exception {
        String admin = api.adminToken();
        String roll = "21CS" + (100 + (int) (Math.random() * 800));
        String username = "s-" + UUID.randomUUID().toString().substring(0, 8);
        api.post("/api/users", admin, Map.of("username", username, "displayName", "Asha", "password", "student123",
                        "role", "STUDENT", "studentRoll", roll + UUID.randomUUID().toString().substring(0, 4)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        String student = api.login(username, "student123");
        api.get("/api/users", student).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
        api.get("/api/audit", student).andExpect(status().isForbidden());
        api.get("/actuator/metrics", student).andExpect(status().isForbidden());
        api.get("/api/auth/me", student).andExpect(status().isOk());
    }

    @Test
    void validatesInputAndRoleLinks() throws Exception {
        String admin = api.adminToken();
        api.post("/api/users", admin, Map.of("username", "x", "displayName", "", "password", "short", "role", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")));
        api.post("/api/users", admin, Map.of("username", "no-roll-" + UUID.randomUUID().toString().substring(0, 6),
                        "displayName", "S", "password", "student123", "role", "STUDENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("student_roll_required"));
        api.post("/api/users", admin, Map.of("username", "admin", "displayName", "Dup", "password", "password123",
                "role", "EXAM_CELL")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("username_taken"));
    }

    @Test
    void adminsCannotLockThemselvesOut() throws Exception {
        String admin = api.adminToken();
        long me = api.read(api.get("/api/auth/me", admin)).get("id").asLong();
        api.patch("/api/users/" + me, admin, Map.of("enabled", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cannot_disable_self"));
        api.patch("/api/users/" + me, admin, Map.of("role", "FACULTY", "facultyCode", "F1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cannot_demote_self"));
    }

    @Test
    void searchFiltersByRoleAndText() throws Exception {
        String admin = api.adminToken();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        api.post("/api/users", admin, Map.of("username", "fac-" + tag, "displayName", "Dr " + tag, "password",
                "password123", "role", "FACULTY", "facultyCode", "F-" + tag)).andExpect(status().isCreated());
        api.get("/api/users?role=FACULTY&q=" + tag, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].facultyCode").value("F-" + tag));
        api.get("/api/users?role=STUDENT&q=" + tag, admin).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unknownUserIs404() throws Exception {
        api.get("/api/users/999999", api.adminToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }
}
