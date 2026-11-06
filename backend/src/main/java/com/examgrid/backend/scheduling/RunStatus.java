package com.examgrid.backend.scheduling;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a schedule run. */
public enum RunStatus {
    QUEUED,
    RUNNING,
    /** A complete, valid plan exists and can be reviewed and approved. */
    DONE,
    /** The solver proved (or could not avoid) that no plan exists; see the report. */
    INFEASIBLE,
    FAILED,
    CANCELLED,
    PUBLISHING,
    PUBLISHED,
    /** Was published, then replaced by a newer published run. */
    SUPERSEDED;

    public static final Set<RunStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING);
    public static final Set<RunStatus> TERMINAL = EnumSet.of(DONE, INFEASIBLE, FAILED, CANCELLED, PUBLISHED, SUPERSEDED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean hasPlan() {
        return this == DONE || this == PUBLISHING || this == PUBLISHED || this == SUPERSEDED;
    }
}
