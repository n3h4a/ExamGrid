package com.examgrid.solver.model;

/** Thrown when the input data is structurally invalid, e.g. an enrolment for an unknown course. */
public class InvalidInstanceException extends IllegalArgumentException {

    public InvalidInstanceException(String message) {
        super(message);
    }
}
