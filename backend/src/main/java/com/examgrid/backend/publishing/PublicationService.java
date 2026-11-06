package com.examgrid.backend.publishing;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.scheduling.InstanceLoader;
import com.examgrid.backend.scheduling.RunStatus;
import com.examgrid.backend.scheduling.ScheduleRun;
import com.examgrid.backend.scheduling.ScheduleRunRepository;
import com.examgrid.backend.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Two-person rule: a plan goes live only after {@code requiredApprovals} <em>different</em>
 * exam-cell accounts approve it. The database enforces one approval per person per run
 * (primary key) and one published run at a time (partial unique index). The service refuses
 * approvals of plans built from data that has changed since.
 */
@Service
public class PublicationService {

    private final ScheduleRunRepository runs;
    private final InstanceLoader loader;
    private final Publisher publisher;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PublishingProperties properties;

    public PublicationService(ScheduleRunRepository runs, InstanceLoader loader, Publisher publisher,
                              AuditService audit, JdbcTemplate jdbc, ObjectMapper mapper,
                              PublishingProperties properties) {
        this.runs = runs;
        this.loader = loader;
        this.publisher = publisher;
        this.audit = audit;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
    }

    /** Result of an approval: how many approvals exist and whether publishing started. */
    public record ApprovalResult(int approvals, int required, boolean publishing) {
    }

    @Transactional
    public ApprovalResult approve(long runId, String comment) {
        ScheduleRun run = lockedRun(runId);
        if (run.getStatus() != RunStatus.DONE) {
            throw ApiException.conflict("not_approvable", "Run " + runId + " is " + run.getStatus()
                    + "; only finished plans (DONE) can be approved.");
        }
        requireFreshData(run);
        CurrentUser me = CurrentUser.require();
        int inserted = jdbc.update("insert into approval (run_id, user_id, comment) values (?, ?, ?) "
                + "on conflict do nothing", runId, me.id(), comment);
        if (inserted == 0) {
            throw ApiException.conflict("already_approved", "You have already approved run " + runId
                    + ". A different exam-cell member must give the next approval.");
        }
        int count = approvals(runId);
        audit.record("run.approved", "schedule_run", runId, Map.of("approvals", count, "required",
                properties.requiredApprovals()));
        boolean start = count >= properties.requiredApprovals();
        if (start) {
            startPublishing(run);
        }
        return new ApprovalResult(count, properties.requiredApprovals(), start);
    }

    @Transactional
    public ApprovalResult withdraw(long runId) {
        ScheduleRun run = lockedRun(runId);
        if (run.getStatus() != RunStatus.DONE) {
            throw ApiException.conflict("not_withdrawable", "Run " + runId + " is " + run.getStatus()
                    + "; approvals can only be withdrawn before publishing.");
        }
        int removed = jdbc.update("delete from approval where run_id = ? and user_id = ?", runId,
                CurrentUser.require().id());
        if (removed == 0) {
            throw ApiException.notFound("Your approval of run", runId);
        }
        audit.record("run.approval_withdrawn", "schedule_run", runId, Map.of());
        return new ApprovalResult(approvals(runId), properties.requiredApprovals(), false);
    }

    /** Retries a publication that failed (the run is back in DONE with enough approvals). */
    @Transactional
    public ApprovalResult retry(long runId) {
        ScheduleRun run = lockedRun(runId);
        int count = approvals(runId);
        if (run.getStatus() != RunStatus.DONE || count < properties.requiredApprovals()) {
            throw ApiException.conflict("not_publishable", "Run " + runId + " needs status DONE and "
                    + properties.requiredApprovals() + " approvals (has " + count + ").");
        }
        requireFreshData(run);
        startPublishing(run);
        audit.record("run.publish_retried", "schedule_run", runId, Map.of());
        return new ApprovalResult(count, properties.requiredApprovals(), true);
    }

    private void startPublishing(ScheduleRun run) {
        run.markPublishing();
        long id = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.submit(id);
            }
        });
    }

    private void requireFreshData(ScheduleRun run) {
        if (run.getInputSummary() == null) {
            return;
        }
        JsonNode summary = mapper.readTree(run.getInputSummary());
        if (summary.has("fingerprint")
                && !summary.get("fingerprint").asString().equals(loader.currentFingerprint())) {
            throw ApiException.conflict("data_changed", "The catalog has changed since run " + run.getId()
                    + " was solved; start a new run so the plan matches current enrolments.");
        }
    }

    private ScheduleRun lockedRun(long runId) {
        // Row lock: concurrent approvals of the same run are serialised, so exactly one of them
        // observes the second approval and starts publishing.
        jdbc.queryForList("select id from schedule_run where id = ? for update", Long.class, runId);
        return runs.findById(runId).orElseThrow(() -> ApiException.notFound("Run", runId));
    }

    private int approvals(long runId) {
        Integer n = jdbc.queryForObject("select count(*) from approval where run_id = ?", Integer.class, runId);
        return n == null ? 0 : n;
    }
}
