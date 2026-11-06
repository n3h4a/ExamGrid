package com.examgrid.solver.coloring;

/** Raised when no plan can be produced; carries the diagnostics. */
public class InfeasibleException extends RuntimeException {

    private final transient InfeasibilityReport report;

    public InfeasibleException(InfeasibilityReport report) {
        super(report.summary());
        this.report = report;
    }

    public InfeasibilityReport report() {
        return report;
    }
}
