package com.examgrid.backend.audit;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuditIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void changesAreAuditedWithActorDetailsAndRequestId() throws Exception {
        ApiClient api = new ApiClient(mvc, mapper);
        String admin = api.adminToken();
        String username = "audited-" + UUID.randomUUID().toString().substring(0, 8);
        long id = api.read(api.post("/api/users", admin, Map.of("username", username, "displayName", "Before",
                "password", "password123", "role", "EXAM_CELL"))).get("id").asLong();
        api.patch("/api/users/" + id, admin, Map.of("displayName", "After")).andExpect(status().isOk());

        JsonNode page = api.read(api.get("/api/audit?entityType=user&entityId=" + id, admin).andExpect(status().isOk()));
        assertThat(page.get("totalItems").asLong()).isEqualTo(2);
        JsonNode newest = page.get("items").get(0);
        assertThat(newest.get("action").asString()).isEqualTo("user.updated");
        assertThat(newest.get("actor").asString()).isEqualTo("admin");
        assertThat(newest.at("/details/displayName/before").asString()).isEqualTo("Before");
        assertThat(newest.at("/details/displayName/after").asString()).isEqualTo("After");
        assertThat(newest.get("requestId").asString()).isNotBlank();
        assertThat(page.get("items").get(1).get("action").asString()).isEqualTo("user.created");
    }
}
