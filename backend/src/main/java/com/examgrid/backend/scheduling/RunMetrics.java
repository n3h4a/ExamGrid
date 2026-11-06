package com.examgrid.backend.scheduling;

import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;
import com.examgrid.solver.pipeline.SolutionMetrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * What the review screen shows about a plan: the solver's metrics, per-slot load and the
 * solver's warnings. Stored as JSONB on the run.
 */
public record RunMetrics(SolutionMetrics solver, List<SlotLoad> slots, List<String> warnings) {

    /** Exams, candidates and rooms used in one slot. */
    public record SlotLoad(String slot, int exams, int candidates, int rooms) {
    }

    public static RunMetrics of(Solution solution, List<String> slotOrder) {
        Map<String, Set<String>> exams = new TreeMap<>();
        Map<String, Set<String>> rooms = new TreeMap<>();
        Map<String, Integer> candidates = new TreeMap<>();
        solution.exams().forEach(e -> exams.computeIfAbsent(e.slotId(), k -> new HashSet<>()));
        for (SeatAssignment s : solution.seats()) {
            exams.computeIfAbsent(s.slotId(), k -> new HashSet<>()).add(s.courseCode());
            rooms.computeIfAbsent(s.slotId(), k -> new HashSet<>()).add(s.roomCode());
            candidates.merge(s.slotId(), 1, Integer::sum);
        }
        List<SlotLoad> loads = new ArrayList<>();
        for (String slot : slotOrder) {
            loads.add(new SlotLoad(slot, exams.getOrDefault(slot, Set.of()).size(), candidates.getOrDefault(slot, 0),
                    rooms.getOrDefault(slot, Set.of()).size()));
        }
        return new RunMetrics(solution.metrics(), loads, solution.warnings());
    }
}
