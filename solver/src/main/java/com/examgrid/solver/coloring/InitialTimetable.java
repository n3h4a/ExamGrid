package com.examgrid.solver.coloring;

import java.util.List;

/**
 * Stage 1 of the pipeline: a clash-free timetable or a proof/explanation of why there is
 * none.
 *
 * <ol>
 *   <li>Cheap structural checks; any that prove infeasibility stop here.</li>
 *   <li>DSatur.</li>
 *   <li>If DSatur leaves exams unplaced, TabuCol repair within the same slots.</li>
 *   <li>If that fails too, a full {@link InfeasibilityReport}.</li>
 * </ol>
 */
public final class InitialTimetable {

    /** @param colorsUsed distinct slots in the result; repaired is true when TabuCol was needed */
    public record Result(int[] slotOf, int colorsUsed, boolean repaired) {
        public Result {
            slotOf = slotOf.clone();
        }

        @Override
        public int[] slotOf() {
            return slotOf.clone();
        }
    }

    private final long repairIterations;
    private final long seed;

    public InitialTimetable(long repairIterations, long seed) {
        this.repairIterations = repairIterations;
        this.seed = seed;
    }

    public Result build(InfeasibilityAnalyzer analyzer, ColoringProblem problem) {
        List<InfeasibilityIssue> structural = analyzer.structuralIssues();
        if (structural.stream().anyMatch(InfeasibilityIssue::proof)) {
            throw new InfeasibleException(new InfeasibilityReport(structural));
        }
        ColoringResult greedy = new DSaturColoring().color(problem);
        if (greedy.complete()) {
            return new Result(greedy.slotOf(), greedy.colorsUsed(), false);
        }
        ColoringResult repaired = new TabuColRepair(repairIterations, seed).repair(problem, greedy);
        if (repaired.complete()) {
            return new Result(repaired.slotOf(), repaired.colorsUsed(), true);
        }
        throw new InfeasibleException(analyzer.analyse(greedy.uncolored()));
    }
}
