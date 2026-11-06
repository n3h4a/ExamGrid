package com.examgrid.backend.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** One click of "Generate": parameters, status, results and diagnostics. */
@Entity
@Table(name = "schedule_run")
public class ScheduleRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metrics;

    private Long score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String infeasibility;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct;

    @Column(name = "parent_run_id")
    private Long parentRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String disruption;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_summary", columnDefinition = "jsonb")
    private String inputSummary;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ScheduleRun() {
    }

    public ScheduleRun(String label, String params, String inputSummary, String createdBy, Instant createdAt) {
        this.label = label;
        this.params = params;
        this.inputSummary = inputSummary;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.status = RunStatus.QUEUED;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public RunStatus getStatus() { return status; }
    public String getParams() { return params; }
    public String getMetrics() { return metrics; }
    public Long getScore() { return score; }
    public String getInfeasibility() { return infeasibility; }
    public String getErrorMessage() { return errorMessage; }
    public int getProgressPct() { return progressPct; }
    public Long getParentRunId() { return parentRunId; }
    public String getDisruption() { return disruption; }
    public String getInputSummary() { return inputSummary; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getPublishedAt() { return publishedAt; }

    public void setParentRunId(Long parentRunId) { this.parentRunId = parentRunId; }
    public void setDisruption(String disruption) { this.disruption = disruption; }
    public void markPublishing() { this.status = RunStatus.PUBLISHING; }

    public void markPublished(Instant at) {
        this.status = RunStatus.PUBLISHED;
        this.publishedAt = at;
    }

    public void markSuperseded() { this.status = RunStatus.SUPERSEDED; }

    /** For derived runs created synchronously (disruptions): already complete when saved. */
    public void completeImmediately(String metricsJson, long finalScore, Instant at) {
        this.status = RunStatus.DONE;
        this.metrics = metricsJson;
        this.score = finalScore;
        this.progressPct = 100;
        this.startedAt = at;
        this.finishedAt = at;
    }
}
