package com.examgrid.solver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An exam hall laid out as a {@code rows × columns} grid. Blocked seats (pillars, broken
 * benches, aisles) are never used.
 */
public record Room(String code, String building, int rows, int columns, Set<SeatPosition> blockedSeats) {

    public Room {
        code = Validation.requireText(code, "room code");
        building = building == null ? "" : building.strip();
        Validation.requirePositive(rows, "room rows");
        Validation.requirePositive(columns, "room columns");
        blockedSeats = blockedSeats == null ? Set.of() : Set.copyOf(blockedSeats);
        for (SeatPosition p : blockedSeats) {
            if (p.row() >= rows || p.column() >= columns) {
                throw new IllegalArgumentException("blocked seat " + p.label() + " lies outside room " + code);
            }
        }
        if (blockedSeats.size() == rows * columns) {
            throw new IllegalArgumentException("room " + code + " has no usable seats");
        }
    }

    public static Room grid(String code, int rows, int columns) {
        return new Room(code, "", rows, columns, Set.of());
    }

    public int capacity() {
        return rows * columns - blockedSeats.size();
    }

    public boolean isUsable(int row, int column) {
        return row >= 0 && row < rows && column >= 0 && column < columns
                && !blockedSeats.contains(new SeatPosition(row, column));
    }

    /** Usable seats in row-major order. */
    public List<SeatPosition> usableSeats() {
        List<SeatPosition> seats = new ArrayList<>(capacity());
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (isUsable(r, c)) {
                    seats.add(new SeatPosition(r, c));
                }
            }
        }
        return seats;
    }

    /**
     * Number of independent colour classes of the seat grid: two for edge adjacency
     * (checkerboard), four when diagonal neighbours also count.
     */
    public static int colourClassCount(boolean diagonalAdjacency) {
        return diagonalAdjacency ? 4 : 2;
    }

    /** Colour class of a cell. Two cells of the same class are never adjacent. */
    public static int colourClass(int row, int column, boolean diagonalAdjacency) {
        return diagonalAdjacency ? (row % 2) * 2 + (column % 2) : (row + column) % 2;
    }

    /** Sizes of each colour class, counting only usable seats. */
    public int[] colourClassSizes(boolean diagonalAdjacency) {
        int[] sizes = new int[colourClassCount(diagonalAdjacency)];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                if (isUsable(r, c)) {
                    sizes[colourClass(r, c, diagonalAdjacency)]++;
                }
            }
        }
        return sizes;
    }

    /**
     * Largest number of students sitting the same paper this room can hold without two of
     * them being neighbours, using the colour-class layout the seating solver produces.
     */
    public int maxSamePaper(boolean diagonalAdjacency) {
        int max = 0;
        for (int size : colourClassSizes(diagonalAdjacency)) {
            max = Math.max(max, size);
        }
        return max;
    }
}
