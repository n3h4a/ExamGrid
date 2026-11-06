package com.examgrid.backend.publishing;

import com.examgrid.backend.audit.AuditService;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.scheduling.InstanceLoader;
import com.examgrid.backend.scheduling.PlanWriter;
import com.examgrid.backend.scheduling.RunMetrics;
import com.examgrid.backend.scheduling.RunParams;
import com.examgrid.backend.scheduling.ScheduleRun;
import com.examgrid.backend.scheduling.ScheduleRunRepository;
import com.examgrid.backend.security.CurrentUser;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.incremental.DisruptionReseater;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Slot;
import com.examgrid.solver.pipeline.ExamAssignment;
import com.examgrid.solver.pipeline.InvigilationAssignment;
import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Room 204 is flooded": freezes a run's timetable, re-seats only the students of the lost
 * room and stores the result as a new run whose parent is the original. The derived run is a
 * normal {@code DONE} plan, so it goes through the same two-person approval before it
 * replaces the published plan.
 */
@Service
public class DisruptionService {

    private static final String SLOT_KEY = "to_char(sl.slot_date, 'YYYY-MM-DD') || '-' || sl.session";

    private final ScheduleRunRepository runs;
    private final InstanceLoader loader;
    private final PlanWriter writer;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DisruptionService(ScheduleRunRepository runs, InstanceLoader loader, PlanWriter writer, AuditService audit,
                             JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.runs = runs;
        this.loader = loader;
        this.writer = writer;
        this.audit = audit;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** @param slot a slot key, or null for every slot the room is used in */
    public record DisruptionRequest(String roomCode, String slot, String label) {
    }

    @Transactional
    public ScheduleRun disrupt(long parentId, DisruptionRequest request) {
        ScheduleRun parent = runs.findById(parentId).orElseThrow(() -> ApiException.notFound("Run", parentId));
        if (!parent.getStatus().hasPlan()) {
            throw ApiException.conflict("no_plan", "Run " + parentId + " has no plan to repair.");
        }
        RunParams params = mapper.readValue(parent.getParams(), RunParams.class);
        InstanceLoader.Snapshot snapshot = loader.load(params.toSolverParams());
        JsonNode parentSummary = parent.getInputSummary() == null ? null : mapper.readTree(parent.getInputSummary());
        if (parentSummary != null && !parentSummary.get("fingerprint").asString()
                .equals(snapshot.summary().fingerprint())) {
            throw ApiException.conflict("data_changed", "The catalog has changed since run " + parentId
                    + " was solved, so its seats cannot be repaired in place; start a new run.");
        }
        ProblemInstance instance = snapshot.instance();
        String room = request.roomCode().strip().toUpperCase();
        if (!instance.hasRoom(room)) {
            throw ApiException.badRequest("unknown_room", "Room " + room + " is not an active room.");
        }
        Solution plan = loadSolution(parent, instance);

        DisruptionReseater.Result result;
        try {
            result = new DisruptionReseater(instance).reseat(plan, room, blankToNull(request.slot()));
        } catch (InfeasibleException e) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "cannot_reseat", e.report().summary());
        }
        if (result.affectedSlots().isEmpty()) {
            throw ApiException.badRequest("room_not_used", "Room " + room + " is not used"
                    + (request.slot() == null ? "" : " in " + request.slot()) + " in run " + parentId + ".");
        }

        String label = request.label() != null && !request.label().isBlank() ? request.label().strip()
                : "Without " + room + (request.slot() == null ? "" : " on " + request.slot()) + " (from run " + parentId + ")";
        ScheduleRun child = new ScheduleRun(label, parent.getParams(), parent.getInputSummary(),
                CurrentUser.actorName(), clock.instant());
        child.setParentRunId(parentId);
        Map<String, Object> disruption = new LinkedHashMap<>();
        disruption.put("lostRoom", room);
        disruption.put("slot", request.slot());
        disruption.put("studentsMoved", result.studentsMoved());
        disruption.put("studentsUnaffected", result.studentsUnaffected());
        disruption.put("reserveRoomsOpened", result.reserveRoomsOpened());
        disruption.put("affectedSlots", result.affectedSlots());
        disruption.put("summary", result.summary());
        child.setDisruption(mapper.writeValueAsString(disruption));
        RunMetrics metrics = RunMetrics.of(result.solution(), instance.slots().stream().map(Slot::id).toList());
        child.completeImmediately(mapper.writeValueAsString(metrics), result.solution().metrics().score(),
                clock.instant());
        runs.saveAndFlush(child);
        writer.write(child.getId(), result.solution());
        audit.record("run.disruption", "schedule_run", child.getId(), disruption);
        return child;
    }

    private Solution loadSolution(ScheduleRun run, ProblemInstance instance) {
        long id = run.getId();
        List<ExamAssignment> exams = jdbc.query("select c.code, " + SLOT_KEY + " from exam_assignment ea "
                + "join course c on c.id = ea.course_id join exam_slot sl on sl.id = ea.slot_id where ea.run_id = ?",
                (rs, i) -> new ExamAssignment(rs.getString(1), rs.getString(2)), id);
        List<SeatAssignment> seats = jdbc.query("select s.roll, c.code, " + SLOT_KEY + ", r.code, sa.seat_row, sa.seat_col "
                        + "from seat_assignment sa join student s on s.id = sa.student_id join course c on c.id = sa.course_id "
                        + "join exam_slot sl on sl.id = sa.slot_id join room r on r.id = sa.room_id where sa.run_id = ?",
                (rs, i) -> new SeatAssignment(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        new SeatPosition(rs.getInt(5), rs.getInt(6))), id);
        List<InvigilationAssignment> duties = jdbc.query("select f.code, r.code, " + SLOT_KEY + " from invigilation i "
                        + "join faculty f on f.id = i.faculty_id join room r on r.id = i.room_id "
                        + "join exam_slot sl on sl.id = i.slot_id where i.run_id = ?",
                (rs, i) -> new InvigilationAssignment(rs.getString(1), rs.getString(2), rs.getString(3)), id);
        if (seats.size() != instance.enrolmentCount()) {
            throw ApiException.conflict("plan_mismatch", "Run " + id + " does not seat every current enrolment.");
        }
        SolutionMetrics metrics = mapper.treeToValue(mapper.readTree(run.getMetrics()).get("solver"),
                SolutionMetrics.class);
        List<String> warnings = List.of();
        return new Solution(exams, seats, duties, metrics, warnings);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
