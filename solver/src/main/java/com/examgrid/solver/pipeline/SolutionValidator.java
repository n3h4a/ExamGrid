package com.examgrid.solver.pipeline;

import com.examgrid.solver.model.Course;
import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.model.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent checker of every hard constraint of a plan. It shares no code with the
 * algorithms, so it catches their bugs; the pipeline runs it on every solution and the
 * property tests run it on random instances.
 */
public final class SolutionValidator {

    private static final int MAX_VIOLATIONS = 50;

    private SolutionValidator() {
    }

    /** Returns human readable violations; empty means the plan is valid. */
    public static List<String> validate(ProblemInstance instance, Solution solution) {
        List<String> v = new ArrayList<>();
        boolean diagonal = instance.params().diagonalAdjacency();

        Map<String, String> slotOfCourse = new HashMap<>();
        for (ExamAssignment e : solution.exams()) {
            if (slotOfCourse.put(e.courseCode(), e.slotId()) != null) {
                add(v, "course " + e.courseCode() + " is scheduled twice");
            }
        }
        for (Course c : instance.courses()) {
            String slotId = slotOfCourse.get(c.code());
            if (slotId == null) {
                add(v, "course " + c.code() + " has no slot");
                continue;
            }
            Slot slot = instance.slot(instance.slotIndex(slotId));
            if (!c.allows(slot.session())) {
                add(v, c.code() + " is in a " + slot.session() + " slot it does not allow");
            }
            Integer lock = instance.lockedSlots().get(instance.courseIndex(c.code()));
            if (lock != null && lock != instance.slotIndex(slotId)) {
                add(v, c.code() + " moved away from its locked slot");
            }
        }
        for (int[] group : instance.sameSlotGroups()) {
            Set<String> slots = new HashSet<>();
            for (int c : group) {
                slots.add(slotOfCourse.get(instance.course(c).code()));
            }
            if (slots.size() > 1) {
                add(v, "same-slot group split across " + slots);
            }
        }

        // Seats: one per enrolment, in the exam's slot, unique, usable, within capacity.
        Map<String, SeatAssignment> seatOfEnrolment = new HashMap<>();
        Map<String, String> seatTaken = new HashMap<>();
        Map<String, Set<String>> slotsOfStudent = new HashMap<>();
        Map<String, Map<SeatPosition, String>> grid = new HashMap<>();
        for (SeatAssignment a : solution.seats()) {
            String key = a.roll() + "|" + a.courseCode();
            if (seatOfEnrolment.put(key, a) != null) {
                add(v, a.roll() + " has two seats for " + a.courseCode());
            }
            if (!a.slotId().equals(slotOfCourse.get(a.courseCode()))) {
                add(v, a.roll() + " is seated for " + a.courseCode() + " in the wrong slot");
            }
            if (!slotsOfStudent.computeIfAbsent(a.roll(), k -> new HashSet<>()).add(a.slotId())) {
                add(v, a.roll() + " has two exams in slot " + a.slotId());
            }
            if (!instance.hasRoom(a.roomCode())) {
                add(v, "unknown room " + a.roomCode());
                continue;
            }
            Room room = instance.room(instance.roomIndex(a.roomCode()));
            if (!room.isUsable(a.seat().row(), a.seat().column())) {
                add(v, "seat " + a.seatLabel() + " in " + room.code() + " is not usable");
            }
            String seatKey = a.slotId() + "|" + a.roomCode() + "|" + a.seatLabel();
            String previous = seatTaken.put(seatKey, a.roll());
            if (previous != null) {
                add(v, "seat " + seatKey + " given to both " + previous + " and " + a.roll());
            }
            grid.computeIfAbsent(a.slotId() + "|" + a.roomCode(), k -> new HashMap<>()).put(a.seat(), a.courseCode());
        }
        for (int c = 0; c < instance.courseCount(); c++) {
            String code = instance.course(c).code();
            for (int s : instance.studentsOf(c)) {
                if (!seatOfEnrolment.containsKey(instance.student(s) + "|" + code)) {
                    add(v, instance.student(s) + " has no seat for " + code);
                }
            }
        }
        if (seatOfEnrolment.size() != instance.enrolmentCount()) {
            add(v, seatOfEnrolment.size() + " seats for " + instance.enrolmentCount() + " enrolments");
        }
        for (Map.Entry<String, Map<SeatPosition, String>> e : grid.entrySet()) {
            String roomCode = e.getKey().substring(e.getKey().indexOf('|') + 1);
            if (!instance.hasRoom(roomCode)) {
                continue;
            }
            Room room = instance.room(instance.roomIndex(roomCode));
            if (e.getValue().size() > room.capacity()) {
                add(v, e.getKey() + " is over capacity");
            }
            for (Map.Entry<SeatPosition, String> seat : e.getValue().entrySet()) {
                int r = seat.getKey().row();
                int c = seat.getKey().column();
                int[][] forward = diagonal ? new int[][] {{0, 1}, {1, -1}, {1, 0}, {1, 1}} : new int[][] {{0, 1}, {1, 0}};
                for (int[] d : forward) {
                    int rr = r + d[0];
                    int cc = c + d[1];
                    if (rr < 0 || cc < 0) {
                        continue;
                    }
                    String other = e.getValue().get(new SeatPosition(rr, cc));
                    if (seat.getValue().equals(other)) {
                        add(v, "adjacent seats " + seat.getKey().label() + " and " + new SeatPosition(rr, cc).label()
                                + " in " + e.getKey() + " both sit " + other);
                    }
                }
            }
        }

        // Invigilation: available, single booking per slot, not teaching a paper in the room.
        Map<String, Faculty> faculty = new HashMap<>();
        instance.faculty().forEach(f -> faculty.put(f.id(), f));
        Set<String> booked = new HashSet<>();
        for (InvigilationAssignment a : solution.invigilations()) {
            Faculty f = faculty.get(a.facultyId());
            if (f == null) {
                add(v, "unknown faculty " + a.facultyId());
                continue;
            }
            if (!f.isAvailable(a.slotId())) {
                add(v, f.id() + " invigilates in " + a.slotId() + " while unavailable");
            }
            if (!booked.add(f.id() + "|" + a.slotId())) {
                add(v, f.id() + " invigilates two rooms in " + a.slotId());
            }
            Map<SeatPosition, String> room = grid.get(a.slotId() + "|" + a.roomCode());
            if (room == null) {
                add(v, f.id() + " invigilates empty room " + a.roomCode() + " in " + a.slotId());
            } else {
                for (String code : room.values()) {
                    if (f.teaches().contains(code)) {
                        add(v, f.id() + " invigilates their own paper " + code);
                        break;
                    }
                }
            }
        }
        return v;
    }

    private static void add(List<String> violations, String message) {
        if (violations.size() < MAX_VIOLATIONS) {
            violations.add(message);
        }
    }
}
