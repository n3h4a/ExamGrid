package com.examgrid.backend.scheduling;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response bodies of the schedule-run API. */
public final class RunViews {

    private RunViews() {
    }

    public record RunSummary(long id, String label, RunStatus status, int progressPct, Long score, String createdBy,
                             Instant createdAt, Instant startedAt, Instant finishedAt, Long durationMs,
                             Long parentRunId) {

        public static RunSummary of(ScheduleRun r) {
            Long duration = r.getStartedAt() != null && r.getFinishedAt() != null
                    ? r.getFinishedAt().toEpochMilli() - r.getStartedAt().toEpochMilli() : null;
            return new RunSummary(r.getId(), r.getLabel(), r.getStatus(), r.getProgressPct(), r.getScore(),
                    r.getCreatedBy(), r.getCreatedAt(), r.getStartedAt(), r.getFinishedAt(), duration,
                    r.getParentRunId());
        }
    }

    /**
     * @param dataChanged true when the catalog has changed since the run read it, so the plan
     *                    may no longer match current enrolments
     */
    public record RunDetail(RunSummary run, JsonNode params, JsonNode metrics, JsonNode infeasibility,
                            JsonNode inputSummary, JsonNode disruption, String errorMessage, Boolean dataChanged,
                            List<Approval> approvals) {
    }

    public record Approval(String username, String displayName, Instant approvedAt, String comment) {
    }

    public record TimetableEntry(String courseCode, String courseName, String department, String slot,
                                 LocalDate date, String session, int candidates, List<String> rooms) {
    }

    public record SeatView(int row, int column, String label, String roll, String courseCode) {
    }

    public record RoomPlan(String slot, String roomCode, String building, int rows, int columns,
                           List<String> blockedSeats, int capacity, List<SeatView> seats, List<String> invigilators,
                           List<String> courses) {
    }

    public record InvigilationEntry(String facultyCode, String facultyName, String slot, LocalDate date,
                                    String session, String roomCode) {
    }

    public record StudentExam(String courseCode, String courseName, String slot, LocalDate date, String session,
                              String roomCode, String building, String seat) {
    }

    public record StudentPlan(String roll, String name, List<StudentExam> exams) {
    }
}
