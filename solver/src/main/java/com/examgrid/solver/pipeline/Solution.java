package com.examgrid.solver.pipeline;

import java.util.List;

/** A complete exam plan: timetable, seats and invigilators. */
public record Solution(List<ExamAssignment> exams, List<SeatAssignment> seats,
                       List<InvigilationAssignment> invigilations, SolutionMetrics metrics, List<String> warnings) {

    public Solution {
        exams = List.copyOf(exams);
        seats = List.copyOf(seats);
        invigilations = List.copyOf(invigilations);
        warnings = List.copyOf(warnings);
    }
}
