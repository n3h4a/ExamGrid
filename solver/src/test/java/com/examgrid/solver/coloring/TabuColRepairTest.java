package com.examgrid.solver.coloring;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.model.ProblemInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.examgrid.solver.coloring.ColoringFixtures.assertProperColouring;
import static com.examgrid.solver.coloring.ColoringFixtures.problem;
import static org.assertj.core.api.Assertions.assertThat;

class TabuColRepairTest {

    @Test
    void completesAPartialColouringWithinTheSameSlots() {
        // Even cycle with every node deliberately in slot 0 except two left unplaced.
        ProblemInstance instance = TestInstances.fromEdges(12, TestInstances.cycle(12), 2).build();
        ColoringProblem p = problem(instance);
        int[] slots = new int[12];
        slots[3] = -1;
        slots[7] = -1;
        ColoringResult repaired = new TabuColRepair(10_000, 1).repair(p, new ColoringResult(slots, List.of(3, 7)));
        assertThat(repaired.complete()).isTrue();
        assertProperColouring(p, repaired.slotOf());
    }

    @Test
    void returnsThePartialResultWhenNoColouringExists() {
        ProblemInstance instance = TestInstances.fromEdges(4, TestInstances.complete(4), 3).build();
        ColoringProblem p = problem(instance);
        ColoringResult greedy = new DSaturColoring().color(p);
        ColoringResult repaired = new TabuColRepair(2_000, 1).repair(p, greedy);
        assertThat(repaired.complete()).isFalse();
        assertThat(repaired.uncolored()).isEqualTo(greedy.uncolored());
    }

    @Test
    void repairFindsColouringsGreedyMisses() {
        // Crown-like graph on which a fixed slot order makes greedy waste colours; give K = 2.
        int n = 10;
        int[][] edges = TestInstances.completeBipartite(5, 5);
        int[][] crown = Arrays.stream(edges).filter(e -> e[1] - 5 != e[0]).toArray(int[][]::new);
        ProblemInstance instance = TestInstances.fromEdges(n, crown, 2).build();
        ColoringProblem p = problem(instance);
        int[] start = new int[n];
        Arrays.fill(start, -1);
        List<Integer> all = java.util.stream.IntStream.range(0, n).boxed().toList();
        ColoringResult repaired = new TabuColRepair(10_000, 3).repair(p, new ColoringResult(start, all));
        assertThat(repaired.complete()).isTrue();
        assertProperColouring(p, repaired.slotOf());
    }
}
