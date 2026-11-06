package com.examgrid.backend.publishing;

import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import com.examgrid.backend.publishing.PlanSnapshot.StudentTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Result-day read path: {@code roll → [{course, slot, room, seat}]} pre-computed into Redis
 * when a run is published, so a lookup is one {@code GET} and Postgres is never touched while
 * thousands of students search at once.
 *
 * <p>Keys are namespaced per run ({@code examgrid:run:<id>:seat:<ROLL>}) and a pointer
 * ({@code examgrid:published-run}) names the live run, so switching to a new plan is one
 * atomic {@code SET}. The old run's keys are expired afterwards. Redis is a cache, not the
 * source of truth: on a miss or a Redis outage the lookup falls back to Postgres and
 * re-populates the key, so losing Redis costs speed, never correctness.
 */
@Component
public class SeatIndex {

    private static final Logger log = LoggerFactory.getLogger(SeatIndex.class);
    static final String POINTER = "examgrid:published-run";
    private static final TypeReference<List<SeatInfo>> SEATS = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final PlanReader plans;
    private final ObjectMapper mapper;

    public SeatIndex(StringRedisTemplate redis, JdbcTemplate jdbc, PlanReader plans, ObjectMapper mapper) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.plans = plans;
        this.mapper = mapper;
    }

    static String key(long runId, String roll) {
        return "examgrid:run:" + runId + ":seat:" + roll.toUpperCase(Locale.ROOT);
    }

    /** Writes every student's seats for a run with one pipelined round trip per batch. */
    public void load(long runId, Map<String, StudentTicket> students) {
        List<Map.Entry<String, StudentTicket>> entries = new ArrayList<>(students.entrySet());
        int batch = 1000;
        for (int from = 0; from < entries.size(); from += batch) {
            List<Map.Entry<String, StudentTicket>> slice = entries.subList(from, Math.min(entries.size(), from + batch));
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (Map.Entry<String, StudentTicket> e : slice) {
                    connection.stringCommands().set(bytes(key(runId, e.getKey())),
                            bytes(mapper.writeValueAsString(e.getValue().exams())));
                }
                return null;
            });
        }
    }

    /** Makes {@code runId} the live run for lookups. */
    public void activate(long runId) {
        redis.opsForValue().set(POINTER, String.valueOf(runId));
    }

    /** Expires the keys of a run that is no longer live (SCAN, never KEYS). */
    public void retire(long runId, Duration grace) {
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                    .match("examgrid:run:" + runId + ":seat:*").count(1000).build())) {
                cursor.forEachRemaining(keys::add);
            }
            for (String k : keys) {
                redis.expire(k, grace);
            }
        } catch (DataAccessException e) {
            log.warn("Could not retire Redis keys of run {}: {}", runId, e.getMessage());
        }
    }

    /** Which run is live, from Redis if possible, else from Postgres. */
    public Optional<Long> publishedRun() {
        try {
            String id = redis.opsForValue().get(POINTER);
            if (id != null) {
                return Optional.of(Long.parseLong(id));
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for published-run pointer: {}", e.getMessage());
        }
        List<Long> ids = jdbc.queryForList("select id from schedule_run where status = 'PUBLISHED'", Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        try {
            activate(ids.getFirst());
        } catch (DataAccessException e) {
            log.debug("Could not restore pointer: {}", e.getMessage());
        }
        return Optional.of(ids.getFirst());
    }

    /** A student's seats in the live run: Redis hit, or Postgres fallback that re-populates Redis. */
    public Optional<List<SeatInfo>> lookup(long runId, String roll) {
        String key = key(runId, roll);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return Optional.of(mapper.readValue(cached, SEATS));
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for seat lookup, using Postgres: {}", e.getMessage());
            return plans.seatsOf(runId, roll.toUpperCase(Locale.ROOT));
        }
        Optional<List<SeatInfo>> fromDb = plans.seatsOf(runId, roll.toUpperCase(Locale.ROOT));
        fromDb.ifPresent(seats -> {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(seats));
            } catch (DataAccessException e) {
                log.debug("Could not re-populate {}: {}", key, e.getMessage());
            }
        });
        return fromDb;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
