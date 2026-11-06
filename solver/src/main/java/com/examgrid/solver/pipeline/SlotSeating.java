package com.examgrid.solver.pipeline;

import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;
import com.examgrid.solver.rooms.RoomAllocator;
import com.examgrid.solver.seating.SeatingPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Seats the candidates of one slot: room allocation (min-cost max-flow) followed by grid
 * seating, then hands seats out in roll-number order, room by room in priority order.
 */
final class SlotSeating {

    private SlotSeating() {
    }

    /**
     * @param courses   course indexes sitting in the slot
     * @param students  candidates per course (student indexes, sorted by roll), parallel to courses
     * @param rooms     rooms available, in priority order
     * @return seat assignments, or {@code null} when the candidates cannot be seated
     */
    static List<SeatAssignment> seat(ProblemInstance instance, String slotId, List<Integer> courses,
                                     List<int[]> students, List<Room> rooms, RoomAllocator allocator) {
        int[] sizes = students.stream().mapToInt(a -> a.length).toArray();
        RoomAllocator.Allocation allocation = allocator.allocate(sizes, rooms);
        if (allocation == null) {
            return null;
        }
        List<SeatAssignment> out = new ArrayList<>();
        for (int p = 0; p < courses.size(); p++) {
            String code = instance.course(courses.get(p)).code();
            int[] candidates = students.get(p);
            int next = 0;
            for (int r = 0; r < rooms.size(); r++) {
                SeatingPlan plan = allocation.plans()[r];
                if (plan == null || allocation.counts()[p][r] == 0) {
                    continue;
                }
                for (SeatPosition seat : plan.seatsOf(p)) {
                    out.add(new SeatAssignment(instance.student(candidates[next++]), code, slotId,
                            rooms.get(r).code(), seat));
                }
            }
            if (next != candidates.length) {
                throw new IllegalStateException("allocated " + next + " of " + candidates.length + " for " + code);
            }
        }
        return out;
    }
}
