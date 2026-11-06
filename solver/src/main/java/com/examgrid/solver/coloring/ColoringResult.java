package com.examgrid.solver.coloring;

import java.util.Arrays;
import java.util.List;

/**
 * Outcome of a colouring attempt.
 *
 * @param slotOf    slot per node, or -1 for a node that could not be placed
 * @param uncolored nodes that could not be placed
 */
public record ColoringResult(int[] slotOf, List<Integer> uncolored) {

    public ColoringResult {
        slotOf = slotOf.clone();
        uncolored = List.copyOf(uncolored);
    }

    public boolean complete() {
        return uncolored.isEmpty();
    }

    /** Number of distinct slots used. */
    public int colorsUsed() {
        return (int) Arrays.stream(slotOf).filter(s -> s >= 0).distinct().count();
    }

    @Override
    public int[] slotOf() {
        return slotOf.clone();
    }
}
