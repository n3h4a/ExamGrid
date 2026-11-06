package com.examgrid.backend.scheduling;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Slot;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.pipeline.ExamGridSolver;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.ProgressListener;
import com.examgrid.solver.progress.SolverCancelledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Executes runs on a small pool of platform threads (solving is CPU-bound, so virtual threads
 * would not help), keeping the HTTP threads free. The {@code schedule_run} row is the job
 * record: a worker claims a run with a conditional {@code QUEUED → RUNNING} update, so a run
 * can never execute twice, and every state change is a single atomic update.
 */
@Component
public class RunWorker implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final InstanceLoader loader;
    private final PlanWriter writer;
    private final RunProgressBroadcaster broadcaster;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final SchedulingProperties properties;
    private final ExecutorService pool;
    private final Map<Long, CancellationToken> tokens = new ConcurrentHashMap<>();

    public RunWorker(JdbcTemplate jdbc, TransactionTemplate tx, InstanceLoader loader, PlanWriter writer,
                     RunProgressBroadcaster broadcaster, AuditService audit, ObjectMapper mapper,
                     SchedulingProperties properties) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.loader = loader;
        this.writer = writer;
        this.broadcaster = broadcaster;
        this.audit = audit;
        this.mapper = mapper;
        this.properties = properties;
        AtomicInteger n = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(properties.workerThreads(), r -> {
            Thread t = new Thread(r, "solver-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Queues a run for execution. Call only after the run row is committed. */
    public void submit(long runId) {
        tokens.putIfAbsent(runId, CancellationToken.none());
        pool.submit(() -> execute(runId));
    }

    /** Requests cancellation; returns false if this instance is not running the run. */
    public boolean cancel(long runId) {
        CancellationToken token = tokens.get(runId);
        if (token == null) {
            return false;
        }
        token.cancel();
        return true;
    }

    /** After a restart, runs that were mid-solve are marked failed and queued runs resume. */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int interrupted = jdbc.update("update schedule_run set status = 'FAILED', finished_at = now(), "
                + "error_message = 'Interrupted by a server restart; start the run again.' where status = 'RUNNING'");
        List<Long> queued = jdbc.queryForList("select id from schedule_run where status = 'QUEUED' order by id",
                Long.class);
        queued.forEach(this::submit);
        if (interrupted > 0 || !queued.isEmpty()) {
            log.info("Run recovery: {} interrupted run(s) marked failed, {} queued run(s) resumed", interrupted,
                    queued.size());
        }
    }

    void execute(long runId) {
        MDC.put("requestId", "run-" + runId);
        CancellationToken token = tokens.computeIfAbsent(runId, id -> CancellationToken.none());
        try {
            int claimed = jdbc.update("update schedule_run set status = 'RUNNING', started_at = now(), progress_pct = 0 "
                    + "where id = ? and status = 'QUEUED'", runId);
            if (claimed == 0) {
                return;
            }
            broadcaster.publish(new RunEvent(runId, RunStatus.RUNNING, "QUEUED", 0, null, "starting"));
            RunParams params = mapper.readValue(jdbc.queryForObject("select params::text from schedule_run where id = ?",
                    String.class, runId), RunParams.class);
            SolverParams solverParams = params.toSolverParams();
            long started = System.nanoTime();

            InstanceLoader.Snapshot snapshot = loader.load(solverParams);
            jdbc.update("update schedule_run set input_summary = ?::jsonb where id = ?",
                    mapper.writeValueAsString(snapshot.summary()), runId);
            ProblemInstance instance = snapshot.instance();
            token.throwIfCancelled();

            Solution solution = new ExamGridSolver().solve(instance, listener(runId), token);
            token.throwIfCancelled();
            RunMetrics metrics = RunMetrics.of(solution, instance.slots().stream().map(Slot::id).toList());
            tx.executeWithoutResult(status -> {
                writer.write(runId, solution);
                jdbc.update("update schedule_run set status = 'DONE', metrics = ?::jsonb, score = ?, progress_pct = 100, "
                                + "finished_at = now() where id = ? and status = 'RUNNING'",
                        mapper.writeValueAsString(metrics), solution.metrics().score(), runId);
            });
            finish(runId, RunStatus.DONE, solution.metrics().score(), "plan ready", Map.of(
                    "score", solution.metrics().score(), "seats", solution.seats().size(),
                    "ms", (System.nanoTime() - started) / 1_000_000));
        } catch (InfeasibleException e) {
            jdbc.update("update schedule_run set status = 'INFEASIBLE', infeasibility = ?::jsonb, finished_at = now() "
                    + "where id = ?", mapper.writeValueAsString(e.report()), runId);
            finish(runId, RunStatus.INFEASIBLE, null, e.report().proven() ? "no plan exists" : "no plan found",
                    Map.of("issues", e.report().issues().size(), "proven", e.report().proven()));
        } catch (SolverCancelledException e) {
            jdbc.update("update schedule_run set status = 'CANCELLED', finished_at = now() where id = ?", runId);
            finish(runId, RunStatus.CANCELLED, null, "cancelled", Map.of());
        } catch (RuntimeException e) {
            log.error("Run {} failed", runId, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            jdbc.update("update schedule_run set status = 'FAILED', error_message = ?, finished_at = now() where id = ?",
                    message.length() > 2000 ? message.substring(0, 2000) : message, runId);
            finish(runId, RunStatus.FAILED, null, "failed: " + message, Map.of("error", message));
        } finally {
            tokens.remove(runId);
            MDC.remove("requestId");
        }
    }

    private ProgressListener listener(long runId) {
        AtomicLong lastWrite = new AtomicLong();
        return (ProgressEvent e) -> {
            broadcaster.publish(new RunEvent(runId, RunStatus.RUNNING, e.phase().name(), e.percent(), e.score(),
                    e.message()));
            long now = System.currentTimeMillis();
            long previous = lastWrite.get();
            if (now - previous >= properties.progressWriteMs() && lastWrite.compareAndSet(previous, now)) {
                jdbc.update("update schedule_run set progress_pct = ?, score = ? where id = ? and status = 'RUNNING'",
                        e.percent(), e.score(), runId);
            }
        };
    }

    private void finish(long runId, RunStatus status, Long score, String message, Map<String, ?> details) {
        broadcaster.publish(new RunEvent(runId, status, "DONE", 100, score, message));
        audit.recordIndependently("system", "run." + status.name().toLowerCase(), "schedule_run", runId, details);
        log.info("Run {} finished: {} ({})", runId, status, message);
    }

    @Override
    public void destroy() throws InterruptedException {
        tokens.values().forEach(CancellationToken::cancel);
        pool.shutdown();
        if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }
    }
}
