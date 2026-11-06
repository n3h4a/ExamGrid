package com.examgrid.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Containers shared by every integration test class: the Spring test context cache reuses the
 * context, and with it these beans, so each container starts once per run.
 *
 * <p>Redis runs in a container too, unless {@code EXAMGRID_TEST_REDIS_URL} points at an
 * existing server (useful where pulling images is rate-limited); the test profile then reads
 * that URL.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    @Conditional(NoExternalRedis.class)
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    static class NoExternalRedis implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String url = System.getenv("EXAMGRID_TEST_REDIS_URL");
            return url == null || url.isBlank();
        }
    }
}
