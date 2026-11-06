package com.examgrid.solver.coloring;

import com.examgrid.solver.graph.ConflictGraph;
import com.examgrid.solver.graph.ExamGroups;
import com.examgrid.solver.model.ProblemInstance;

final class ColoringFixtures {

    private ColoringFixtures() {
    }

    static ColoringProblem problem(ProblemInstance instance) {
        ConflictGraph g = ConflictGraph.build(instance, ExamGroups.of(instance));
        return ColoringProblem.from(instance, g);
    }

    static InfeasibilityAnalyzer analyzer(ProblemInstance instance, ColoringProblem problem) {
        return new InfeasibilityAnalyzer(instance, problem.graph(), problem);
    }

    static void assertProperColouring(ColoringProblem p, int[] slotOf) {
        org.assertj.core.api.Assertions.assertThat(TabuColRepair.isFeasible(p, slotOf))
                .as("colouring is complete, inside every domain, clash-free and within capacity")
                .isTrue();
    }
}
