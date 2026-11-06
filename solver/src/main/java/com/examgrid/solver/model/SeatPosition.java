package com.examgrid.solver.model;

/**
 * Zero-based position of a seat inside a room grid. Row 0 is the front row.
 */
public record SeatPosition(int row, int column) implements Comparable<SeatPosition> {

    public SeatPosition {
        if (row < 0 || column < 0) {
            throw new IllegalArgumentException("seat position must be non-negative: " + row + "," + column);
        }
    }

    /** Label printed on hall tickets and seating charts, e.g. row 1 column 3 is {@code B4}. */
    public String label() {
        return rowLabel(row) + (column + 1);
    }

    /** Spreadsheet-style row letters: 0 → A, 25 → Z, 26 → AA. */
    public static String rowLabel(int row) {
        StringBuilder sb = new StringBuilder();
        int r = row;
        do {
            sb.insert(0, (char) ('A' + r % 26));
            r = r / 26 - 1;
        } while (r >= 0);
        return sb.toString();
    }

    /** Parses a label such as {@code B4} or {@code AA12} back into a position. */
    public static SeatPosition parse(String label) {
        String text = Validation.requireText(label, "seat label").toUpperCase(java.util.Locale.ROOT);
        int i = 0;
        int row = 0;
        while (i < text.length() && Character.isLetter(text.charAt(i))) {
            row = row * 26 + (text.charAt(i) - 'A' + 1);
            i++;
        }
        if (i == 0 || i == text.length()) {
            throw new IllegalArgumentException("invalid seat label " + label);
        }
        int column;
        try {
            column = Integer.parseInt(text.substring(i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid seat label " + label, e);
        }
        if (column < 1) {
            throw new IllegalArgumentException("invalid seat label " + label);
        }
        return new SeatPosition(row - 1, column - 1);
    }

    @Override
    public int compareTo(SeatPosition o) {
        return row != o.row ? Integer.compare(row, o.row) : Integer.compare(column, o.column);
    }
}
