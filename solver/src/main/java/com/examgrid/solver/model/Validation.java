package com.examgrid.solver.model;

/** Small argument checks shared by the model records. */
final class Validation {

    private Validation() {
    }

    static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return value.strip();
    }

    static int requirePositive(int value, String what) {
        if (value <= 0) {
            throw new IllegalArgumentException(what + " must be positive but was " + value);
        }
        return value;
    }
}
