package com.examgrid.backend.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A minimal circuit breaker for Redis, which ExamGrid uses only as a cache and a rate-limit
 * store. After a failure, Redis is skipped for a cool-down period, so callers go straight to
 * their fallback (Postgres, or failing open) instead of every request waiting for a timeout.
 * The next call after the cool-down probes Redis again.
 *
 * <p>Measured with k6: with Redis stopped and no breaker, each seat lookup waited out several
 * timeouts (about 8 s); with the breaker it answers from Postgres at normal speed.
 */
@Component
public class RedisCircuit {

    private static final Logger log = LoggerFactory.getLogger(RedisCircuit.class);
    static final Duration COOL_DOWN = Duration.ofSeconds(10);

    private final Clock clock;
    private final AtomicLong openUntil = new AtomicLong();

    public RedisCircuit(Clock clock) {
        this.clock = clock;
    }

    /** True when Redis may be called (closed circuit, or cool-down over). */
    public boolean allows() {
        return clock.millis() >= openUntil.get();
    }

    public void recordFailure(RuntimeException e) {
        long until = clock.millis() + COOL_DOWN.toMillis();
        long previous = openUntil.getAndSet(until);
        if (previous < clock.millis()) {
            log.warn("Redis unavailable ({}); using fallbacks for {} s", e.getMessage(), COOL_DOWN.toSeconds());
        }
    }

    public void recordSuccess() {
        openUntil.set(0);
    }
}
