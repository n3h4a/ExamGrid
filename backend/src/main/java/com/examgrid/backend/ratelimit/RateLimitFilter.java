package com.examgrid.backend.ratelimit;

import com.examgrid.backend.common.error.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Per-IP limits on unauthenticated endpoints: the public seat finder and the login form.
 * Runs before authentication so abusive traffic is turned away cheaply. Client IPs come from
 * {@code X-Forwarded-For} only through Spring's forwarded-header handling, which is enabled
 * because the API sits behind Nginx.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ObjectMapper mapper;

    public RateLimitFilter(RedisRateLimiter limiter, RateLimitProperties properties, ObjectMapper mapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !properties.enabled()
                || !(path.startsWith("/api/public/") || ("POST".equals(request.getMethod())
                && path.equals("/api/auth/login")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean login = request.getRequestURI().equals("/api/auth/login");
        int limit = login ? properties.loginPerMinute() : properties.publicPerMinute();
        RedisRateLimiter.Decision decision = limiter.tryAcquire(login ? "login" : "public", request.getRemoteAddr(),
                limit, 60);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), Problems.of(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                "Too many requests; try again in " + decision.retryAfterSeconds() + " seconds."));
    }
}
