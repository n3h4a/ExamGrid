package com.examgrid.backend.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code examgrid.rate-limit.*}: requests per client IP per minute.
 *
 * @param enabled         switch for local experiments
 * @param publicPerMinute public endpoints such as the seat finder
 * @param loginPerMinute  login attempts
 */
@ConfigurationProperties("examgrid.rate-limit")
public record RateLimitProperties(Boolean enabled, Integer publicPerMinute, Integer loginPerMinute) {

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        publicPerMinute = publicPerMinute == null ? 60 : publicPerMinute;
        loginPerMinute = loginPerMinute == null ? 10 : loginPerMinute;
    }
}
