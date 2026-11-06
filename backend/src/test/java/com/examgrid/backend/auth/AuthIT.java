package com.examgrid.backend.auth;

import com.examgrid.backend.audit.AuditLogRepository;
import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    AuditLogRepository audit;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, mapper);
    }

    @Test
    void bootstrapAdminCanLogInAndSeeThemselves() throws Exception {
        String token = api.adminToken();
        api.get("/api/auth/me", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("EXAM_CELL"))
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswerAndAreAudited() throws Exception {
        String unknown = "ghost-" + UUID.randomUUID().toString().substring(0, 8);
        api.post("/api/auth/login", null, Map.of("username", "admin", "password", "nope-nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        api.post("/api/auth/login", null, Map.of("username", unknown, "password", "nope-nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        assertThat(audit.search(unknown, "auth.login_failed", null, null, PageRequest.of(0, 5)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void disabledAccountsCannotLogIn() throws Exception {
        String admin = api.adminToken();
        String username = "disabled-" + UUID.randomUUID().toString().substring(0, 8);
        long id = api.read(api.post("/api/users", admin, Map.of("username", username, "displayName", "D",
                "password", "password123", "role", "EXAM_CELL")).andExpect(status().isCreated())).get("id").asLong();
        api.login(username, "password123");
        api.patch("/api/users/" + id, admin, Map.of("enabled", false)).andExpect(status().isOk());
        api.post("/api/auth/login", null, Map.of("username", username, "password", "password123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutOrWithForgedTokensAreRejected() throws Exception {
        api.get("/api/auth/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        String token = api.adminToken();
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        api.get("/api/auth/me", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void usersCanChangeTheirOwnPassword() throws Exception {
        String admin = api.adminToken();
        String username = "changer-" + UUID.randomUUID().toString().substring(0, 8);
        api.post("/api/users", admin, Map.of("username", username, "displayName", "C", "password", "first-pass-1",
                "role", "EXAM_CELL")).andExpect(status().isCreated());
        String token = api.login(username, "first-pass-1");
        api.post("/api/auth/password", token, Map.of("currentPassword", "wrong-one", "newPassword", "second-pass-2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("wrong_password"));
        api.post("/api/auth/password", token, Map.of("currentPassword", "first-pass-1", "newPassword", "second-pass-2"))
                .andExpect(status().isNoContent());
        api.login(username, "second-pass-2");
    }
}
