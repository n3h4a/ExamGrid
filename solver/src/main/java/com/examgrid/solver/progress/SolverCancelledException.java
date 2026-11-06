package com.examgrid.solver.progress;

/** Raised inside the solver when its {@link CancellationToken} is cancelled. */
public class SolverCancelledException extends RuntimeException {

    public SolverCancelledException() {
        super("solve was cancelled");
    }
}
