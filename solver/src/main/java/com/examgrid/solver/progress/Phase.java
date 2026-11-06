package com.examgrid.solver.progress;

/** Pipeline phases, in execution order, with the share of overall progress each covers. */
public enum Phase {
    GRAPH(0, 3),
    COLOURING(3, 8),
    OPTIMISING(8, 85),
    ROOMS(85, 90),
    SEATING(90, 96),
    INVIGILATION(96, 100),
    DONE(100, 100);

    private final int from;
    private final int to;

    Phase(int from, int to) {
        this.from = from;
        this.to = to;
    }

    /** Maps progress within this phase (0–100) onto overall progress (0–100). */
    public int overall(int withinPhase) {
        int clamped = Math.max(0, Math.min(100, withinPhase));
        return from + (to - from) * clamped / 100;
    }
}
