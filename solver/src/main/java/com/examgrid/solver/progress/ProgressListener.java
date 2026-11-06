package com.examgrid.solver.progress;

/** Receives progress updates from a running solve. Implementations must be cheap and thread-safe. */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = event -> { };

    void onProgress(ProgressEvent event);
}
