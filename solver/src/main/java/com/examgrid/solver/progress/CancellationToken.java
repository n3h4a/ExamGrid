package com.examgrid.solver.progress;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation flag checked by long-running loops. */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    public static CancellationToken none() {
        return new CancellationToken();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Throws {@link SolverCancelledException} if cancellation was requested. */
    public void throwIfCancelled() {
        if (cancelled.get()) {
            throw new SolverCancelledException();
        }
    }
}
