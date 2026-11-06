package com.examgrid.solver.pipeline;

import com.examgrid.solver.model.SeatPosition;

/** Where one student sits one exam. */
public record SeatAssignment(String roll, String courseCode, String slotId, String roomCode, SeatPosition seat) {

    public String seatLabel() {
        return seat.label();
    }
}
