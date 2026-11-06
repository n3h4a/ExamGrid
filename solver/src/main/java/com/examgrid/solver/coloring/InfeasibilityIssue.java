package com.examgrid.solver.coloring;

import java.util.List;

/**
 * One reason a timetable could not be produced, phrased for the exam cell.
 *
 * @param type     category of the problem
 * @param message  human readable explanation
 * @param courses  course codes involved
 * @param students roll numbers that witness the problem (a sample when there are many)
 * @param proof    true when the issue proves no timetable exists; false when it only
 *                 explains where the search got stuck
 */
public record InfeasibilityIssue(Type type, String message, List<String> courses, List<String> students,
                                 boolean proof) {

    public InfeasibilityIssue {
        courses = List.copyOf(courses);
        students = List.copyOf(students);
    }

    public enum Type {
        /** A student sits two courses that must share a slot. */
        SAME_SLOT_GROUP_CONFLICT,
        /** Course rules and locks leave no slot at all for an exam. */
        EMPTY_DOMAIN,
        /** Two locked exams with a shared student are pinned to the same slot. */
        LOCK_CONFLICT,
        /** A student sits more exams than there are usable slots for them. */
        STUDENT_OVERLOAD,
        /** A set of pairwise-conflicting exams is larger than the slots available to it. */
        CLIQUE_TOO_LARGE,
        /** An exam has more students than all rooms together can seat. */
        PAPER_TOO_LARGE,
        /** Total seats needed exceed total seats across all slots. */
        CAPACITY_EXCEEDED,
        /** No proof found, but the search could not place these exams. */
        UNRESOLVED
    }
}
