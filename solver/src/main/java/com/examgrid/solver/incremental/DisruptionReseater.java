package com.examgrid.solver.incremental;

import com.examgrid.solver.coloring.InfeasibilityIssue;
import com.examgrid.solver.coloring.InfeasibilityReport;
import com.examgrid.solver.coloring.InfeasibleException;
import com.examgrid.solver.invigilation.Duty;
import com.examgrid.solver.invigilation.InvigilationAssigner;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.pipeline.InvigilationAssignment;
import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionMetrics;
import com.examgrid.solver.pipeline.SolutionValidator;
import com.examgrid.solver.rooms.RoomAllocator;
import com.examgrid.solver.seating.SeatingPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal-change repair when a room becomes unusable ("Room 204 is flooded").
 *
 * <p>The timetable is frozen and every student outside the lost room keeps their seat. For
 * each affected slot:
 * <ol>
 *   <li>Displaced students fill <em>spare seats in rooms already in use</em>, in room
 *       priority order. A seat takes the displaced paper with the most students left that
 *       none of the seat's occupied neighbours sits and that the room's invigilators do not
 *       teach.</li>
 *   <li>Whoever is left goes to <em>reserve rooms</em> (rooms unused in that slot), seated with
 *       the regular room allocator and seating solver.</li>
 *   <li>The lost room's invigilators are released. Rooms whose candidate count grew get
 *       extra invigilators through the fair heap, seeded with everyone's current load.</li>
 * </ol>
 * The result reports exactly how many students moved; the validator confirms that nothing
 * else changed hard-constraint status.
 */
public final class DisruptionReseater {

    /** Outcome of a repair. */
    public record Result(Solution solution, int studentsMoved, int studentsUnaffected, List<String> reserveRoomsOpened,
                         List<String> affectedSlots) {
        public Result {
            reserveRoomsOpened = List.copyOf(reserveRoomsOpened);
            affectedSlots = List.copyOf(affectedSlots);
        }

        public String summary() {
            return studentsMoved + " students moved, 0 others affected"
                    + (reserveRoomsOpened.isEmpty() ? "" : "; reserve rooms opened: " + String.join(", ",
                    reserveRoomsOpened));
        }
    }

    private final ProblemInstance instance;
    private final boolean diagonal;

    public DisruptionReseater(ProblemInstance instance) {
        this.instance = instance;
        this.diagonal = instance.params().diagonalAdjacency();
    }

    /**
     * @param lostRoom room that can no longer be used
     * @param slotId   the affected slot, or {@code null} for every slot the room is used in
     */
    public Result reseat(Solution plan, String lostRoom, String slotId) {
        if (!instance.hasRoom(lostRoom)) {
            throw new IllegalArgumentException("unknown room " + lostRoom);
        }
        Set<String> affected = new LinkedHashSet<>();
        for (SeatAssignment a : plan.seats()) {
            if (a.roomCode().equals(lostRoom) && (slotId == null || a.slotId().equals(slotId))) {
                affected.add(a.slotId());
            }
        }
        List<SeatAssignment> seats = new ArrayList<>();
        Map<String, List<SeatAssignment>> displacedBySlot = new LinkedHashMap<>();
        for (SeatAssignment a : plan.seats()) {
            if (a.roomCode().equals(lostRoom) && affected.contains(a.slotId())) {
                displacedBySlot.computeIfAbsent(a.slotId(), k -> new ArrayList<>()).add(a);
            } else {
                seats.add(a);
            }
        }

        List<String> reserveOpened = new ArrayList<>();
        int moved = 0;
        for (String slot : affected) {
            List<SeatAssignment> displaced = displacedBySlot.get(slot);
            List<SeatAssignment> placed = reseatSlot(slot, lostRoom, displaced, seats, taughtByInvigilators(plan, slot),
                    reserveOpened);
            moved += placed.size();
            seats.addAll(placed);
        }

        List<InvigilationAssignment> invigilations = reassignInvigilators(plan, seats, lostRoom, affected);
        int unfilled = countUnfilled(seats, invigilations);
        List<String> warnings = new ArrayList<>(plan.warnings());
        warnings.add("Room " + lostRoom + " removed from " + affected.size() + " slot(s): " + moved
                + " students moved.");
        if (unfilled > 0) {
            warnings.add(unfilled + " invigilator position(s) could not be filled after the change.");
        }
        SolutionMetrics m = plan.metrics();
        SolutionMetrics metrics = new SolutionMetrics(m.timetable(), m.dsaturColours(), m.repaired(), m.initialScore(),
                m.optimiserIterations(), m.optimiserStop(), countRoomSlots(seats),
                utilisation(seats), invigilations.size(), unfilled, dutyLoad(invigilations, true),
                dutyLoad(invigilations, false), m.elapsedMillis());
        Solution repaired = new Solution(plan.exams(), seats, invigilations, metrics, warnings);
        List<String> violations = SolutionValidator.validate(instance, repaired);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("re-seating produced an invalid plan: " + violations);
        }
        return new Result(repaired, moved, plan.seats().size() - moved, reserveOpened, List.copyOf(affected));
    }

    /** Papers taught by the invigilators already supervising each room in {@code slot}. */
    private Map<String, Set<String>> taughtByInvigilators(Solution plan, String slot) {
        Map<String, Set<String>> teachersOf = new HashMap<>();
        Map<String, Set<String>> taught = new HashMap<>();
        instance.faculty().forEach(f -> teachersOf.put(f.id(), f.teaches()));
        for (InvigilationAssignment a : plan.invigilations()) {
            if (a.slotId().equals(slot)) {
                taught.computeIfAbsent(a.roomCode(), k -> new HashSet<>())
                        .addAll(teachersOf.getOrDefault(a.facultyId(), Set.of()));
            }
        }
        return taught;
    }

    private List<SeatAssignment> reseatSlot(String slot, String lostRoom, List<SeatAssignment> displaced,
                                            List<SeatAssignment> kept, Map<String, Set<String>> taughtInRoom,
                                            List<String> reserveOpened) {
        // Occupancy of rooms already in use in this slot.
        Map<String, Map<SeatPosition, String>> occupied = new LinkedHashMap<>();
        for (SeatAssignment a : kept) {
            if (a.slotId().equals(slot)) {
                occupied.computeIfAbsent(a.roomCode(), k -> new HashMap<>()).put(a.seat(), a.courseCode());
            }
        }
        // Displaced students grouped by paper, in roll order.
        Map<String, List<String>> waiting = new LinkedHashMap<>();
        displaced.stream()
                .sorted(Comparator.comparing(SeatAssignment::courseCode).thenComparing(SeatAssignment::roll))
                .forEach(a -> waiting.computeIfAbsent(a.courseCode(), k -> new ArrayList<>()).add(a.roll()));

        List<SeatAssignment> placed = new ArrayList<>();
        for (Room room : instance.rooms()) {
            Map<SeatPosition, String> grid = occupied.get(room.code());
            if (grid == null || room.code().equals(lostRoom)) {
                continue;
            }
            for (SeatPosition seat : room.usableSeats()) {
                if (grid.containsKey(seat)) {
                    continue;
                }
                String paper = bestPaper(room, grid, seat, waiting,
                        taughtInRoom.getOrDefault(room.code(), Set.of()));
                if (paper == null) {
                    continue;
                }
                String roll = waiting.get(paper).removeFirst();
                grid.put(seat, paper);
                placed.add(new SeatAssignment(roll, paper, slot, room.code(), seat));
            }
        }
        waiting.values().removeIf(List::isEmpty);
        if (waiting.isEmpty()) {
            return placed;
        }

        // Reserve rooms: unused in this slot and not the lost room.
        List<Room> reserve = instance.rooms().stream()
                .filter(r -> !occupied.containsKey(r.code()) && !r.code().equals(lostRoom))
                .toList();
        List<String> papers = new ArrayList<>(waiting.keySet());
        int[] sizes = papers.stream().mapToInt(p -> waiting.get(p).size()).toArray();
        RoomAllocator.Allocation allocation = reserve.isEmpty() ? null
                : new RoomAllocator(diagonal).allocate(sizes, reserve);
        if (allocation == null) {
            int left = waiting.values().stream().mapToInt(List::size).sum();
            throw new InfeasibleException(new InfeasibilityReport(List.of(new InfeasibilityIssue(
                    InfeasibilityIssue.Type.SEATING,
                    "Without " + lostRoom + ", " + left + " candidate(s) in " + slot
                            + " cannot be seated: no spare or reserve seats keep same-paper candidates apart.",
                    papers, List.of(), true))));
        }
        for (int p = 0; p < papers.size(); p++) {
            List<String> rolls = waiting.get(papers.get(p));
            int next = 0;
            for (int r = 0; r < reserve.size(); r++) {
                SeatingPlan plan = allocation.plans()[r];
                if (plan == null || allocation.counts()[p][r] == 0) {
                    continue;
                }
                if (!reserveOpened.contains(reserve.get(r).code() + " (" + slot + ")")) {
                    reserveOpened.add(reserve.get(r).code() + " (" + slot + ")");
                }
                for (SeatPosition seat : plan.seatsOf(p)) {
                    placed.add(new SeatAssignment(rolls.get(next++), papers.get(p), slot, reserve.get(r).code(), seat));
                }
            }
        }
        return placed;
    }

    /**
     * Waiting paper with most students left that no occupied neighbour of {@code seat} sits and
     * that none of the room's invigilators teaches.
     */
    private String bestPaper(Room room, Map<SeatPosition, String> grid, SeatPosition seat,
                             Map<String, List<String>> waiting, Set<String> excluded) {
        Set<String> neighbourPapers = new HashSet<>(excluded);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if ((dr == 0 && dc == 0) || (!diagonal && dr != 0 && dc != 0)) {
                    continue;
                }
                int r = seat.row() + dr;
                int c = seat.column() + dc;
                if (room.isUsable(r, c)) {
                    String p = grid.get(new SeatPosition(r, c));
                    if (p != null) {
                        neighbourPapers.add(p);
                    }
                }
            }
        }
        String best = null;
        for (Map.Entry<String, List<String>> e : waiting.entrySet()) {
            if (!e.getValue().isEmpty() && !neighbourPapers.contains(e.getKey())
                    && (best == null || e.getValue().size() > waiting.get(best).size())) {
                best = e.getKey();
            }
        }
        return best;
    }

    private List<InvigilationAssignment> reassignInvigilators(Solution plan, List<SeatAssignment> seats,
                                                              String lostRoom, Set<String> affected) {
        List<InvigilationAssignment> kept = new ArrayList<>();
        for (InvigilationAssignment a : plan.invigilations()) {
            if (!(a.roomCode().equals(lostRoom) && affected.contains(a.slotId()))) {
                kept.add(a);
            }
        }
        if (instance.faculty().isEmpty()) {
            return kept;
        }
        Map<String, Integer> facultyIndex = new HashMap<>();
        for (int i = 0; i < instance.faculty().size(); i++) {
            facultyIndex.put(instance.faculty().get(i).id(), i);
        }
        int f = instance.faculty().size();
        int[] load = new int[f];
        boolean[][] busy = new boolean[f][instance.slotCount()];
        Map<String, Integer> staffed = new HashMap<>();
        for (InvigilationAssignment a : kept) {
            int i = facultyIndex.get(a.facultyId());
            load[i]++;
            busy[i][instance.slotIndex(a.slotId())] = true;
            staffed.merge(a.slotId() + "|" + a.roomCode(), 1, Integer::sum);
        }
        Map<String, List<SeatAssignment>> byRoomSlot = new LinkedHashMap<>();
        for (SeatAssignment a : seats) {
            if (affected.contains(a.slotId())) {
                byRoomSlot.computeIfAbsent(a.slotId() + "|" + a.roomCode(), k -> new ArrayList<>()).add(a);
            }
        }
        List<Duty> duties = new ArrayList<>();
        for (Map.Entry<String, List<SeatAssignment>> e : byRoomSlot.entrySet()) {
            SeatAssignment first = e.getValue().getFirst();
            int required = InvigilationAssigner.required(e.getValue().size(),
                    instance.params().studentsPerInvigilator(), instance.params().minInvigilatorsPerRoom());
            int missing = required - staffed.getOrDefault(e.getKey(), 0);
            if (missing > 0) {
                Set<Integer> courses = new HashSet<>();
                e.getValue().forEach(a -> courses.add(instance.courseIndex(a.courseCode())));
                duties.add(new Duty(instance.slotIndex(first.slotId()), instance.roomIndex(first.roomCode()), courses,
                        missing));
            }
        }
        InvigilationAssigner.Result result = new InvigilationAssigner(instance).assign(duties, load, busy);
        List<InvigilationAssignment> out = new ArrayList<>(kept);
        for (int[] a : result.assignments()) {
            Duty duty = duties.get(a[0]);
            out.add(new InvigilationAssignment(instance.faculty().get(a[1]).id(), instance.room(duty.room()).code(),
                    instance.slot(duty.slot()).id()));
        }
        return out;
    }

    private int countUnfilled(List<SeatAssignment> seats, List<InvigilationAssignment> invigilations) {
        Map<String, Integer> candidates = new HashMap<>();
        seats.forEach(a -> candidates.merge(a.slotId() + "|" + a.roomCode(), 1, Integer::sum));
        Map<String, Integer> staff = new HashMap<>();
        invigilations.forEach(a -> staff.merge(a.slotId() + "|" + a.roomCode(), 1, Integer::sum));
        int unfilled = 0;
        for (Map.Entry<String, Integer> e : candidates.entrySet()) {
            int required = InvigilationAssigner.required(e.getValue(), instance.params().studentsPerInvigilator(),
                    instance.params().minInvigilatorsPerRoom());
            unfilled += Math.max(0, required - staff.getOrDefault(e.getKey(), 0));
        }
        return unfilled;
    }

    private int dutyLoad(List<InvigilationAssignment> invigilations, boolean min) {
        if (instance.faculty().isEmpty()) {
            return 0;
        }
        Map<String, Integer> load = new HashMap<>();
        instance.faculty().forEach(f -> load.put(f.id(), 0));
        invigilations.forEach(a -> load.merge(a.facultyId(), 1, Integer::sum));
        return min ? load.values().stream().mapToInt(Integer::intValue).min().orElse(0)
                : load.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static int countRoomSlots(List<SeatAssignment> seats) {
        Set<String> keys = new HashSet<>();
        seats.forEach(a -> keys.add(a.slotId() + "|" + a.roomCode()));
        return keys.size();
    }

    private double utilisation(List<SeatAssignment> seats) {
        Set<String> keys = new HashSet<>();
        long capacity = 0;
        for (SeatAssignment a : seats) {
            if (keys.add(a.slotId() + "|" + a.roomCode())) {
                capacity += instance.room(instance.roomIndex(a.roomCode())).capacity();
            }
        }
        return capacity == 0 ? 0 : (double) seats.size() / capacity;
    }
}
