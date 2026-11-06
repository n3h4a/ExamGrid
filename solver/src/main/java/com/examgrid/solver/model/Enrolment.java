package com.examgrid.solver.model;

/** One student sitting one course's exam. */
public record Enrolment(String roll, String courseCode) {

    public Enrolment {
        roll = Validation.requireText(roll, "roll number");
        courseCode = Validation.requireText(courseCode, "course code");
    }
}
