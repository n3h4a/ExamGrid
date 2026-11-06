package com.examgrid.backend.imports;

/**
 * One problem found in a file.
 *
 * @param row    1-based row number as the user sees it (the header is row 1)
 * @param column canonical column name, or null for whole-row / whole-file issues
 */
public record ImportIssue(int row, String column, Severity severity, String message) {

    public enum Severity {
        /** Blocks the import. */
        ERROR,
        /** The row is skipped or adjusted; the import can proceed. */
        WARNING
    }

    public static ImportIssue error(int row, String column, String message) {
        return new ImportIssue(row, column, Severity.ERROR, message);
    }

    public static ImportIssue warning(int row, String column, String message) {
        return new ImportIssue(row, column, Severity.WARNING, message);
    }
}
