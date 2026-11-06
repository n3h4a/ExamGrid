package com.examgrid.solver.seating;

/** Lets tests in other packages reuse the seating plan assertions. */
public final class SeatingSolverTestAccess {

    private SeatingSolverTestAccess() {
    }

    public static void assertValid(SeatingPlan plan, int[] counts, boolean diagonal) {
        SeatingSolverTest.assertValid(plan, counts, diagonal);
    }
}
