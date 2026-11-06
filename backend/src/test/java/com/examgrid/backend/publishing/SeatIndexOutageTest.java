package com.examgrid.backend.publishing;

import com.examgrid.backend.common.redis.RedisCircuit;
import com.examgrid.backend.publishing.PlanSnapshot.SeatInfo;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** With Redis down, only the first lookup pays for the failed call; the rest go straight to Postgres. */
class SeatIndexOutageTest {

    @Test
    @SuppressWarnings("unchecked")
    void skipsRedisAfterTheFirstFailure() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("connection refused"));

        PlanReader plans = mock(PlanReader.class);
        SeatInfo seat = new SeatInfo("CS101", "Programming", "2026-12-01-MORNING", LocalDate.of(2026, 12, 1),
                "MORNING", "LH-1", "Main", "A1");
        when(plans.seatsOf(7L, "24CS001")).thenReturn(Optional.of(List.of(seat)));

        SeatIndex index = new SeatIndex(redis, mock(JdbcTemplate.class), plans, JsonMapper.builder().build(),
                new RedisCircuit(Clock.systemUTC()));
        for (int i = 0; i < 50; i++) {
            assertThat(index.lookup(7L, "24cs001")).contains(List.of(seat));
        }
        verify(values, times(1)).get(anyString());
        verify(plans, times(50)).seatsOf(7L, "24CS001");
    }
}
