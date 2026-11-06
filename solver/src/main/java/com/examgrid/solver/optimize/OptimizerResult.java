package com.examgrid.solver.optimize;

/**
 * Outcome of an optimisation run.
 *
 * @param slotOf        best assignment found (exam node → slot)
 * @param initialScore  score of the starting timetable
 * @param finalScore    score of {@code slotOf}
 * @param iterations    moves attempted
 * @param accepted      moves accepted
 * @param elapsedMillis wall-clock time
 * @param stopReason    why the search stopped
 */
public record OptimizerResult(int[] slotOf, long initialScore, long finalScore, long iterations, long accepted,
                              long elapsedMillis, StopReason stopReason) {

    public OptimizerResult {
        slotOf = slotOf.clone();
    }

    @Override
    public int[] slotOf() {
        return slotOf.clone();
    }

    public enum StopReason {
        ITERATION_LIMIT,
        TIME_LIMIT,
        OPTIMAL,
        NOTHING_TO_OPTIMISE,
        DISABLED
    }
}
