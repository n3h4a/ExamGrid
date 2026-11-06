package com.examgrid.backend.scheduling;

/**
 * Progress message pushed over SSE, e.g.
 * {@code {"runId":7,"status":"RUNNING","phase":"OPTIMISING","pct":62,"score":41,"message":"annealing"}}.
 */
public record RunEvent(long runId, RunStatus status, String phase, int pct, Long score, String message) {

    public boolean terminal() {
        return status.hasPlan() || RunStatus.TERMINAL.contains(status);
    }
}
