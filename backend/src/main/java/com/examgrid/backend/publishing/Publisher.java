package com.examgrid.backend.publishing;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.publishing.PlanSnapshot.StudentTicket;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The publication job, run once two approvals are in. It does all the expensive work up
 * front, so result day is read-only:
 * <ol>
 *   <li>render every hall ticket, one seating chart per slot, the timetable and the roster,
 *       and store them in file storage;</li>
 *   <li>pre-compute the Redis seat index for every student;</li>
 *   <li>in one transaction: the previous live run becomes {@code SUPERSEDED} and this one
 *       {@code PUBLISHED}; then the Redis pointer switches and the old keys expire. The
 *       database commits first so Redis can never point at a plan that is not published;
 *       for the few milliseconds in between, lookups still answer from the previous plan.</li>
 * </ol>
 * If anything fails the run returns to {@code DONE} with its approvals intact, so publishing
 * can simply be retried.
 */
@Component
public class Publisher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Publisher.class);

    private final PlanReader reader;
    private final PdfRenderer pdf;
    private final FileStorage storage;
    private final SeatIndex seats;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "publisher");
        t.setDaemon(true);
        return t;
    });

    public Publisher(PlanReader reader, PdfRenderer pdf, FileStorage storage, SeatIndex seats, JdbcTemplate jdbc,
                     TransactionTemplate tx, AuditService audit) {
        this.reader = reader;
        this.pdf = pdf;
        this.storage = storage;
        this.seats = seats;
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
    }

    public void submit(long runId) {
        executor.submit(() -> publish(runId));
    }

    /** Publications interrupted by a restart are resumed. */
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        jdbc.queryForList("select id from schedule_run where status = 'PUBLISHING'", Long.class).forEach(this::submit);
    }

    void publish(long runId) {
        MDC.put("requestId", "publish-" + runId);
        long started = System.nanoTime();
        try {
            PlanSnapshot plan = reader.load(runId);
            jdbc.update("delete from published_document where run_id = ?", runId);
            List<Object[]> docs = new ArrayList<>();
            for (StudentTicket student : plan.students().values()) {
                docs.add(store(runId, "HALL_TICKET", student.roll(), "hall-tickets/" + student.roll() + ".pdf",
                        pdf.hallTicket(plan.label(), student)));
            }
            for (Map.Entry<String, List<RoomPlan>> slot : plan.roomsBySlot().entrySet()) {
                docs.add(store(runId, "SEATING", slot.getKey(), "seating/" + slot.getKey() + ".pdf",
                        pdf.seatingCharts(plan.label(), slot.getKey(), slot.getValue())));
            }
            docs.add(store(runId, "TIMETABLE", "all", "timetable.pdf", pdf.timetable(plan.label(), plan.timetable())));
            docs.add(store(runId, "INVIGILATION", "all", "invigilation.pdf",
                    pdf.invigilationRoster(plan.label(), plan.invigilation())));
            jdbc.batchUpdate("insert into published_document (run_id, kind, ref, storage_key, size_bytes) "
                    + "values (?, ?, ?, ?, ?)", docs);

            seats.load(runId, plan.students());

            List<Long> previous = jdbc.queryForList("select id from schedule_run where status = 'PUBLISHED' and id <> ?",
                    Long.class, runId);
            tx.executeWithoutResult(status -> {
                jdbc.update("update schedule_run set status = 'SUPERSEDED' where status = 'PUBLISHED' and id <> ?", runId);
                jdbc.update("update schedule_run set status = 'PUBLISHED', published_at = now(), error_message = null "
                        + "where id = ? and status = 'PUBLISHING'", runId);
            });
            seats.activate(runId);
            previous.forEach(old -> seats.retire(old, Duration.ofHours(1)));
            long ms = (System.nanoTime() - started) / 1_000_000;
            audit.recordIndependently("system", "run.published", "schedule_run", runId, Map.of(
                    "documents", docs.size(), "students", plan.students().size(), "superseded", previous, "ms", ms));
            log.info("Published run {}: {} documents, {} students indexed in {} ms", runId, docs.size(),
                    plan.students().size(), ms);
        } catch (RuntimeException e) {
            log.error("Publishing run {} failed", runId, e);
            jdbc.update("update schedule_run set status = 'DONE', error_message = ? where id = ? and status = 'PUBLISHING'",
                    "Publishing failed: " + e.getMessage(), runId);
            audit.recordIndependently("system", "run.publish_failed", "schedule_run", runId,
                    Map.of("error", String.valueOf(e.getMessage())));
        } finally {
            MDC.remove("requestId");
        }
    }

    private Object[] store(long runId, String kind, String ref, String name, byte[] content) {
        String key = "runs/" + runId + "/" + name;
        storage.put(key, content, "application/pdf");
        return new Object[] {runId, kind, ref, key, (long) content.length};
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
