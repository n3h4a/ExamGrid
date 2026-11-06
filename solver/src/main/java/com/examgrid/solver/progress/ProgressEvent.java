package com.examgrid.solver.progress;

/**
 * A progress update, e.g. {@code {"phase":"OPTIMISING","pct":62,"score":41}}.
 *
 * @param phase   current phase
 * @param percent overall progress 0–100
 * @param score   best soft-constraint score so far, or null before one exists
 * @param message short human readable note
 */
public record ProgressEvent(Phase phase, int percent, Long score, String message) {
}
