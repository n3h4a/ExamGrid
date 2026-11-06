package com.examgrid.backend.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code examgrid.scheduling.*}.
 *
 * @param workerThreads   solves that may run at the same time (each uses one CPU core)
 * @param maxActiveRuns   queued + running runs allowed before new requests are refused
 * @param progressWriteMs minimum interval between progress writes to the database
 */
@ConfigurationProperties("examgrid.scheduling")
public record SchedulingProperties(Integer workerThreads, Integer maxActiveRuns, Integer progressWriteMs) {

    public SchedulingProperties {
        workerThreads = workerThreads == null || workerThreads < 1 ? 2 : workerThreads;
        maxActiveRuns = maxActiveRuns == null || maxActiveRuns < 1 ? 10 : maxActiveRuns;
        progressWriteMs = progressWriteMs == null || progressWriteMs < 100 ? 1000 : progressWriteMs;
    }
}
