package com.examgrid.solver.seating;

import com.examgrid.solver.model.Room;
import com.examgrid.solver.model.SeatPosition;

import java.util.ArrayList;
import java.util.List;

/**
 * Paper per seat in one room for one slot. Papers are local indexes into the counts passed
 * to the solver; {@link #EMPTY} marks an empty or blocked seat.
 */
public final class SeatingPlan {

    public static final int EMPTY = -1;

    private final Room room;
    private final int[][] paperAt;
    private final int papers;

    SeatingPlan(Room room, int[][] paperAt, int papers) {
        this.room = room;
        this.paperAt = paperAt;
        this.papers = papers;
    }

    public Room room() {
        return room;
    }

    public int paperAt(int row, int column) {
        return paperAt[row][column];
    }

    /** Seats holding {@code paper} in the order candidates are seated (roll-number order). */
    public List<SeatPosition> seatsOf(int paper) {
        List<SeatPosition> seats = new ArrayList<>();
        for (int r = 0; r < room.rows(); r++) {
            for (int c = 0; c < room.columns(); c++) {
                if (paperAt[r][c] == paper) {
                    seats.add(new SeatPosition(r, c));
                }
            }
        }
        return seats;
    }

    public int count(int paper) {
        return seatsOf(paper).size();
    }

    public int paperCount() {
        return papers;
    }
}
