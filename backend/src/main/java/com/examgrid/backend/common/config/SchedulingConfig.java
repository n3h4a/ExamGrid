package com.examgrid.backend.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} housekeeping such as SSE heartbeats. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
