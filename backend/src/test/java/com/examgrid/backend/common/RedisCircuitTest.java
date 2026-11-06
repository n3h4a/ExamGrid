package com.examgrid.backend.common;

import com.examgrid.backend.common.redis.RedisCircuit;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCircuitTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-12-01T09:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void opensOnFailureAndProbesAgainAfterTheCoolDown() {
        MutableClock clock = new MutableClock();
        RedisCircuit circuit = new RedisCircuit(clock);
        assertThat(circuit.allows()).isTrue();

        circuit.recordFailure(new IllegalStateException("connection refused"));
        assertThat(circuit.allows()).isFalse();

        clock.now = clock.now.plus(Duration.ofSeconds(9));
        assertThat(circuit.allows()).isFalse();
        clock.now = clock.now.plus(Duration.ofSeconds(2));
        assertThat(circuit.allows()).isTrue();

        circuit.recordSuccess();
        assertThat(circuit.allows()).isTrue();
    }
}
