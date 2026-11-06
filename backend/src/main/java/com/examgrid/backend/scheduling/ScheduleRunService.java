package com.examgrid.backend.scheduling;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates, cancels and deletes runs. Creating a run is cheap and synchronous (validate, store
 * the resolved parameters, 202); the solve happens on {@link RunWorker} once the row is
 * committed, so the worker can never pick up a run that a rollback later erases.
 */
@Service
public class ScheduleRunService {

    private final ScheduleRunRepository runs;
    private final RunWorker worker;
    private final RunProgressBroadcaster broadcaster;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SchedulingProperties properties;
    private final Clock clock;

    public ScheduleRunService(ScheduleRunRepository runs, RunWorker worker, RunProgressBroadcaster broadcaster,
                              AuditService audit, JdbcTemplate jdbc, ObjectMapper mapper,
                              SchedulingProperties properties, Clock clock) {
        this.runs = runs;
        this.worker = worker;
        this.broadcaster = broadcaster;
        this.audit = audit;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ScheduleRun create(RunParams request) {
        List<String> missing = new ArrayList<>();
        if (count("select count(*) from enrolment") == 0) {
            missing.add("enrolments");
        }
        if (count("select count(*) from exam_slot") == 0) {
            missing.add("exam slots");
        }
        if (count("select count(*) from room where active") == 0) {
            missing.add("active rooms");
        }
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "not_ready",
                    "Cannot schedule yet: add " + String.join(", ", missing) + " first.");
        }
        if (runs.countByStatusIn(RunStatus.ACTIVE) >= properties.maxActiveRuns()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_runs",
                    "Too many runs are queued or running; wait for one to finish or cancel one.");
        }
        RunParams resolved = request.resolved();
        ScheduleRun run = runs.save(new ScheduleRun(resolved.label(), mapper.writeValueAsString(resolved), null,
                CurrentUser.actorName(), clock.instant()));
        audit.record("run.created", "schedule_run", run.getId(), Map.of("params", resolved));
        long id = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.submit(id);
            }
        });
        return run;
    }

    @Transactional
    public ScheduleRun cancel(long id) {
        ScheduleRun run = get(id);
        if (!run.getStatus().isActive()) {
            throw ApiException.conflict("not_active", "Run " + id + " is " + run.getStatus() + " and cannot be cancelled.");
        }
        int dequeued = jdbc.update("update schedule_run set status = 'CANCELLED', finished_at = now() "
                + "where id = ? and status = 'QUEUED'", id);
        if (dequeued == 1) {
            broadcaster.publish(new RunEvent(id, RunStatus.CANCELLED, "DONE", 100, null, "cancelled"));
        } else if (!worker.cancel(id)) {
            throw ApiException.conflict("not_cancellable", "Run " + id + " is not running on this server.");
        }
        audit.record("run.cancel_requested", "schedule_run", id, Map.of());
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        ScheduleRun run = get(id);
        if (run.getStatus().isActive() || run.getStatus() == RunStatus.PUBLISHED
                || run.getStatus() == RunStatus.PUBLISHING) {
            throw ApiException.conflict("run_in_use", "Run " + id + " is " + run.getStatus() + " and cannot be deleted.");
        }
        if (count("select count(*) from schedule_run where parent_run_id = " + id) > 0) {
            throw ApiException.conflict("run_has_children", "Run " + id + " has derived runs; delete those first.");
        }
        runs.delete(run);
        audit.record("run.deleted", "schedule_run", id, Map.of("status", run.getStatus().name()));
    }

    @Transactional(readOnly = true)
    public ScheduleRun get(long id) {
        return runs.findById(id).orElseThrow(() -> ApiException.notFound("Run", id));
    }

    /** Requires that the run has a plan to show. */
    public ScheduleRun requirePlan(long id) {
        ScheduleRun run = get(id);
        if (!run.getStatus().hasPlan()) {
            throw ApiException.conflict("no_plan", "Run " + id + " is " + run.getStatus() + " and has no plan.");
        }
        return run;
    }

    public RunEvent currentEvent(ScheduleRun run) {
        return new RunEvent(run.getId(), run.getStatus(), run.getStatus().isActive() ? "QUEUED" : "DONE",
                run.getProgressPct(), run.getScore(), run.getStatus().name().toLowerCase());
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
