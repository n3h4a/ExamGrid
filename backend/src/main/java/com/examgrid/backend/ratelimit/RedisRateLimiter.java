package com.examgrid.backend.ratelimit;

import com.examgrid.backend.common.redis.RedisCircuit;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Fixed-window counter in Redis: {@code INCR} the key for (bucket, client, current window) and
 * set its expiry on first use, atomically in one Lua script. All API instances share the
 * counters. If Redis is unreachable the limiter fails open, because refusing every student on
 * result day is worse than briefly not limiting.
 */
@Component
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]) "
                    + "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return n", Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final RedisCircuit circuit;

    public RedisRateLimiter(StringRedisTemplate redis, Clock clock, RedisCircuit circuit) {
        this.redis = redis;
        this.clock = clock;
        this.circuit = circuit;
    }

    /** @param retryAfterSeconds seconds until the window resets (only meaningful when refused) */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }

    public Decision tryAcquire(String bucket, String client, int limit, int windowSeconds) {
        long now = clock.millis() / 1000;
        long window = now / windowSeconds;
        long retryAfter = (window + 1) * windowSeconds - now;
        String key = "examgrid:rl:" + bucket + ":" + client + ":" + window;
        if (!circuit.allows()) {
            return new Decision(true, limit, 0);
        }
        try {
            Long count = redis.execute(INCREMENT, List.of(key), String.valueOf(windowSeconds + 1));
            circuit.recordSuccess();
            long n = count == null ? 0 : count;
            return new Decision(n <= limit, Math.max(0, limit - n), retryAfter);
        } catch (DataAccessException e) {
            circuit.recordFailure(e);
            return new Decision(true, limit, 0);
        }
    }
}
