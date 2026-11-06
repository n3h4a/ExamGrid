package com.examgrid.backend.imports;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the files in {@code samples/} and the steps in its README importable. */
@IntegrationTest
class SampleDataIT {

    private static final Path SAMPLES = Path.of("..", "samples");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void samplesImportCleanlyInTheDocumentedOrder() throws Exception {
        ApiClient api = new ApiClient(mvc, mapper);
        String admin = api.adminToken();
        commit(api, admin, "courses", "courses.csv");
        commit(api, admin, "rooms", "rooms.csv");
        api.post("/api/slots/generate", admin, Map.of("startDate", "2026-12-07", "endDate", "2026-12-12",
                "sessions", List.of("MORNING", "AFTERNOON"), "skipWeekdays", List.of("SUNDAY")));
        JsonNode enrolments = commit(api, admin, "enrolments", "enrolments.csv");
        assertThat(enrolments.at("/report/acceptedRows").asInt()).isEqualTo(486);
        JsonNode faculty = commit(api, admin, "faculty", "faculty.csv");
        assertThat(faculty.at("/report/changes/unavailableSlots").asInt()).isEqualTo(2);
    }

    private JsonNode commit(ApiClient api, String token, String kind, String file) throws Exception {
        JsonNode r = api.read(api.upload("/api/imports/" + kind + "?dryRun=false", token, file,
                Files.readAllBytes(SAMPLES.resolve(file))));
        assertThat(r.at("/batch/status").asString()).as(r.toString()).isEqualTo("COMMITTED");
        assertThat(r.at("/report/warningCount").asInt()).as(r.toString()).isZero();
        return r;
    }
}
