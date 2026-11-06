package com.examgrid.solver.coloring;

import java.util.List;

/** Everything known about why an instance could not be timetabled. */
public record InfeasibilityReport(List<InfeasibilityIssue> issues) {

    public InfeasibilityReport {
        issues = List.copyOf(issues);
    }

    /** True when at least one issue is a proof rather than a heuristic failure. */
    public boolean proven() {
        return issues.stream().anyMatch(InfeasibilityIssue::proof);
    }

    public String summary() {
        if (issues.isEmpty()) {
            return "No timetable found.";
        }
        StringBuilder sb = new StringBuilder(proven()
                ? "No clash-free timetable exists:"
                : "Could not find a clash-free timetable:");
        for (InfeasibilityIssue issue : issues) {
            sb.append(System.lineSeparator()).append(" - ").append(issue.message());
        }
        return sb.toString();
    }
}
