package com.examgrid.backend.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Small helper around MockMvc for authenticated JSON calls. */
public final class ApiClient {

    public static final String ADMIN = "admin";
    public static final String ADMIN_PASSWORD = "admin-test-password";

    private final MockMvc mvc;
    private final ObjectMapper mapper;

    public ApiClient(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public String login(String username, String password) throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("accessToken").asString();
    }

    public String adminToken() throws Exception {
        return login(ADMIN, ADMIN_PASSWORD);
    }

    public ResultActions get(String path, String token) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(path), token));
    }

    public ResultActions post(String path, String token, Object body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(path), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public ResultActions patch(String path, String token, Object body) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.patch(path), token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    public JsonNode read(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    public String json(Object body) {
        return body instanceof String s ? s : mapper.writeValueAsString(body);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }
}
