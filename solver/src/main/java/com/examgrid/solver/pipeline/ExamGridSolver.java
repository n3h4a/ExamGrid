package com.examgrid.solver.pipeline;

import com.examgrid.solver.coloring.InfeasibilityIssue;
import com.examgrid.solver.coloring.InfeasibilityReport;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.invigilation.Duty;
import com.examgrid.solver.invigilation.InvigilationAssigner;
import com.examgrid.solver.model.InvalidInstanceException;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SolverParams;
import com.examgrid.solver.progress.CancellationToken;
import com.examgrid.solver.progress.Phase;
import com.examgrid.solver.progress.ProgressEvent;
import com.examgrid.solver.progress.ProgressListener;
import com.examgrid.solver.rooms.RoomAllocator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole pipeline: timetable (graph → DSatur → optimiser), then for every slot room
 * allocation and seating, then invigilation.
 *
 * <p>The result is validated with {@link SolutionValidator} before it is returned, so a bug
 * in any stage surfaces as an exception and never as a bad plan.
 */
public final class ExamGridSolver {

    public Solution solve(ProblemInstance instance) {
        return solve(instance, ProgressListener.NONE, CancellationToken.none());
    }

    public Solution solve(ProblemInstance instance, ProgressListener listener, CancellationToken cancel) {
        if (instance.roomCount() == 0) {
            throw new InvalidInstanceException("at least one room is required to build a seating plan");
        }
        long started = System.nanoTime();
        SolverParams params = instance.params();
        TimetableStage.Outcome timetable = new TimetableStage().run(instance, listener, cancel);

        List<ExamAssignment> exams = new ArrayList<>();
        for (int c = 0; c < instance.courseCount(); c++) {
            exams.add(new ExamAssignment(instance.course(c).code(), instance.slot(timetable.slotOfCourse(c)).id()));
        }

        // Rooms and seats, slot by slot.
        RoomAllocator allocator = new RoomAllocator(params.diagonalAdjacency());
        List<SeatAssignment> seats = new ArrayList<>();
        List<InfeasibilityIssue> seatingIssues = new ArrayList<>();
        List<Room> rooms = instance.rooms();
        for (int s = 0; s < instance.slotCount(); s++) {
            cancel.throwIfCancelled();
            listener.onProgress(new ProgressEvent(Phase.ROOMS, Phase.ROOMS.overall(100 * s / instance.slotCount()),
                    timetable.metrics().score(), "seating " + instance.slot(s).id()));
            List<Integer> courses = new ArrayList<>();
            List<int[]> students = new ArrayList<>();
            for (int c = 0; c < instance.courseCount(); c++) {
                if (timetable.slotOfCourse(c) == s && instance.studentsOf(c).length > 0) {
                    courses.add(c);
                    students.add(instance.studentsOf(c));
                }
            }
            if (courses.isEmpty()) {
                continue;
            }
            List<SeatAssignment> slotSeats = SlotSeating.seat(instance, instance.slot(s).id(), courses, students,
                    rooms, allocator);
            if (slotSeats == null) {
                List<String> codes = courses.stream().map(c -> instance.course(c).code()).toList();
                int candidates = students.stream().mapToInt(a -> a.length).sum();
                seatingIssues.add(new InfeasibilityIssue(InfeasibilityIssue.Type.SEATING,
                        "Slot " + instance.slot(s).id() + " has " + candidates + " candidates across "
                                + codes.size() + " papers, which cannot be seated in the " + rooms.size()
                                + " rooms without neighbours sharing a paper. Add rooms or slots.",
                        codes, List.of(), false));
                continue;
            }
            seats.addAll(slotSeats);
        }
        if (!seatingIssues.isEmpty()) {
            throw new InfeasibleException(new InfeasibilityReport(seatingIssues));
        }
        listener.onProgress(new ProgressEvent(Phase.SEATING, Phase.SEATING.overall(100), timetable.metrics().score(),
                seats.size() + " seats assigned"));

        // Invigilation.
        cancel.throwIfCancelled();
        listener.onProgress(new ProgressEvent(Phase.INVIGILATION, Phase.INVIGILATION.overall(0),
                timetable.metrics().score(), "assigning invigilators"));
        Map<String, List<SeatAssignment>> byRoomSlot = groupByRoomSlot(seats);
        List<Duty> duties = new ArrayList<>();
        for (Map.Entry<String, List<SeatAssignment>> e : byRoomSlot.entrySet()) {
            SeatAssignment first = e.getValue().getFirst();
            Set<Integer> courses = new LinkedHashSet<>();
            e.getValue().forEach(a -> courses.add(instance.courseIndex(a.courseCode())));
            duties.add(new Duty(instance.slotIndex(first.slotId()), instance.roomIndex(first.roomCode()), courses,
                    InvigilationAssigner.required(e.getValue().size(), params.studentsPerInvigilator(),
                            params.minInvigilatorsPerRoom())));
        }
        List<InvigilationAssignment> invigilations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int unfilled = 0;
        int[] load = new int[0];
        if (!instance.faculty().isEmpty()) {
            InvigilationAssigner.Result inv = new InvigilationAssigner(instance).assign(duties);
            for (int[] a : inv.assignments()) {
                Duty duty = duties.get(a[0]);
                invigilations.add(new InvigilationAssignment(instance.faculty().get(a[1]).id(),
                        instance.room(duty.room()).code(), instance.slot(duty.slot()).id()));
            }
            unfilled = inv.unfilled();
            load = inv.load();
        } else {
            unfilled = duties.stream().mapToInt(Duty::required).sum();
        }
        if (unfilled > 0) {
            warnings.add(unfilled + " invigilator position(s) could not be filled: not enough eligible faculty.");
        }
        if (timetable.repaired()) {
            warnings.add("DSatur alone could not fit the timetable; TabuCol repair was used.");
        }

        long seatsInUsedRooms = 0;
        for (List<SeatAssignment> group : byRoomSlot.values()) {
            seatsInUsedRooms += instance.room(instance.roomIndex(group.getFirst().roomCode())).capacity();
        }
        SolutionMetrics metrics = new SolutionMetrics(timetable.metrics(), timetable.dsaturColours(),
                timetable.repaired(), timetable.optimisation().initialScore(), timetable.optimisation().iterations(),
                timetable.optimisation().stopReason().name(), byRoomSlot.size(),
                seatsInUsedRooms == 0 ? 0 : (double) seats.size() / seatsInUsedRooms,
                invigilations.size(), unfilled,
                load.length == 0 ? 0 : Arrays.stream(load).min().orElse(0),
                load.length == 0 ? 0 : Arrays.stream(load).max().orElse(0),
                (System.nanoTime() - started) / 1_000_000);
        Solution solution = new Solution(exams, seats, invigilations, metrics, warnings);

        List<String> violations = SolutionValidator.validate(instance, solution);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("solver produced an invalid plan: " + violations);
        }
        listener.onProgress(new ProgressEvent(Phase.DONE, 100, metrics.score(), "done"));
        return solution;
    }

    static Map<String, List<SeatAssignment>> groupByRoomSlot(List<SeatAssignment> seats) {
        Map<String, List<SeatAssignment>> out = new LinkedHashMap<>();
        for (SeatAssignment a : seats) {
            out.computeIfAbsent(a.slotId() + "|" + a.roomCode(), k -> new ArrayList<>()).add(a);
        }
        return out;
    }
}
