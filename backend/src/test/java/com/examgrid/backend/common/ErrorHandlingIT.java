package com.examgrid.backend.common;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ErrorHandlingIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void malformedJsonIsAProblemNotAStackTrace() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("request_error"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void requestIdIsEchoedAndIncludedInProblems() throws Exception {
        mvc.perform(get("/api/users/1").header("X-Request-Id", "trace-12345678"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "trace-12345678"))
                .andExpect(jsonPath("$.requestId").value("trace-12345678"));
        mvc.perform(get("/api/users/1").header("X-Request-Id", "bad id with spaces"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("bad id with spaces")));
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        String token = new ApiClient(mvc, mapper).adminToken();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ExamGrid API"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
    }
}
