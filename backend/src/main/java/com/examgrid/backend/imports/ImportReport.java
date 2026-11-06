package com.examgrid.backend.imports;

import java.util.List;
import java.util.Map;

/**
 * Result of validating (and possibly committing) a file. {@code changes} describes what a
 * commit does or did, e.g. {@code {"created": 12, "updated": 3, "unchanged": 160}}.
 */
public record ImportReport(ImportKind kind, ImportMode mode, int totalRows, int acceptedRows, int errorCount,
                           int warningCount, List<ImportIssue> issues, boolean issuesTruncated,
                           Map<String, Integer> changes) {

    public static final int MAX_ISSUES = 500;

    public boolean hasErrors() {
        return errorCount > 0;
    }
}
