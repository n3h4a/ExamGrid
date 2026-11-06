package com.examgrid.solver.invigilation;

import com.examgrid.solver.model.Faculty;
import com.examgrid.solver.model.ProblemInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Fair invigilation with a min-heap of faculty keyed by duties assigned so far (ties broken
 * by staff index, so results are deterministic).
 *
 * <p>For each duty, faculty are popped in load order until one is eligible: available in the
 * slot, not already supervising another room in the slot, and not teaching any paper sat in
 * the room. Ineligible faculty are set aside and pushed back afterwards. Each assignment
 * costs O(log F) plus the skipped entries. With no availability constraints the greedy
 * keeps every two loads within one duty of each other.
 */
public final class InvigilationAssigner {

    /** Faculty index per assigned seat of a duty; {@code unfilled} counts missing invigilators. */
    public record Result(List<int[]> assignments, int unfilled, int[] load) {

        public Result {
            assignments = List.copyOf(assignments);
            load = load.clone();
        }

        @Override
        public int[] load() {
            return load.clone();
        }
    }

    private final ProblemInstance instance;
    private final boolean[][] teaches;
    private final boolean[][] unavailable;

    public InvigilationAssigner(ProblemInstance instance) {
        this.instance = instance;
        int f = instance.faculty().size();
        teaches = new boolean[f][instance.courseCount()];
        unavailable = new boolean[f][instance.slotCount()];
        for (int i = 0; i < f; i++) {
            Faculty fac = instance.faculty().get(i);
            for (String code : fac.teaches()) {
                teaches[i][instance.courseIndex(code)] = true;
            }
            for (String slotId : fac.unavailableSlotIds()) {
                unavailable[i][instance.slotIndex(slotId)] = true;
            }
        }
    }

    /**
     * @param duties        duties to staff
     * @param initialLoad   duties each faculty member already has (e.g. from an earlier plan)
     * @param busy          {@code busy[f][s]} true if faculty f is already assigned in slot s
     * @return one {@code {dutyIndex, facultyIndex}} pair per assigned invigilator
     */
    public Result assign(List<Duty> duties, int[] initialLoad, boolean[][] busy) {
        int f = instance.faculty().size();
        int[] load = initialLoad.clone();
        boolean[][] booked = new boolean[f][instance.slotCount()];
        for (int i = 0; i < f; i++) {
            booked[i] = busy[i].clone();
        }
        PriorityQueue<Integer> heap = new PriorityQueue<>(Math.max(1, f),
                (a, b) -> load[a] != load[b] ? Integer.compare(load[a], load[b]) : Integer.compare(a, b));
        for (int i = 0; i < f; i++) {
            heap.add(i);
        }
        List<int[]> out = new ArrayList<>();
        int unfilled = 0;
        for (int d = 0; d < duties.size(); d++) {
            Duty duty = duties.get(d);
            for (int k = 0; k < duty.required(); k++) {
                List<Integer> skipped = new ArrayList<>();
                Integer chosen = null;
                while (!heap.isEmpty()) {
                    int candidate = heap.poll();
                    if (eligible(candidate, duty, booked)) {
                        chosen = candidate;
                        break;
                    }
                    skipped.add(candidate);
                }
                if (chosen == null) {
                    unfilled++;
                } else {
                    load[chosen]++;
                    booked[chosen][duty.slot()] = true;
                    out.add(new int[] {d, chosen});
                    heap.add(chosen);
                }
                heap.addAll(skipped);
            }
        }
        return new Result(out, unfilled, load);
    }

    public Result assign(List<Duty> duties) {
        int f = instance.faculty().size();
        return assign(duties, new int[f], new boolean[f][instance.slotCount()]);
    }

    private boolean eligible(int faculty, Duty duty, boolean[][] booked) {
        if (unavailable[faculty][duty.slot()] || booked[faculty][duty.slot()]) {
            return false;
        }
        for (int c : duty.courses()) {
            if (teaches[faculty][c]) {
                return false;
            }
        }
        return true;
    }

    /** Invigilators needed for a room holding {@code candidates} students. */
    public static int required(int candidates, int perInvigilator, int minimum) {
        return Math.max(minimum, (candidates + perInvigilator - 1) / perInvigilator);
    }
}
