package com.examgrid.backend.security;

import com.examgrid.backend.common.error.Problems;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 401 and 403 responses from the security filter chain, in the same problem format as the API. */
@Component
public class ProblemSecurityHandlers {

    private final ObjectMapper mapper;

    public ProblemSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    AuthenticationEntryPoint entryPoint() {
        return (request, response, e) -> write(response, Problems.of(HttpStatus.UNAUTHORIZED, "unauthenticated",
                "A valid bearer token is required."));
    }

    AccessDeniedHandler accessDeniedHandler() {
        return (request, response, e) -> write(response, Problems.of(HttpStatus.FORBIDDEN, "forbidden",
                "You do not have permission to perform this action."));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (problem.getStatus() == HttpStatus.UNAUTHORIZED.value()) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
