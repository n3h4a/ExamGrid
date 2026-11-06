package com.examgrid.backend.imports;

import com.examgrid.backend.imports.TabularFileReader.Table;

import java.util.List;
import java.util.Map;

/** Validates and writes one kind of file. Implementations are stateless Spring beans. */
public interface ImportHandler {

    ImportKind kind();

    /** Validates without writing anything; {@code plan} is passed back to {@link #commit}. */
    Validation validate(Table table, ImportMode mode);

    /** Applies a validated plan inside the caller's transaction. */
    void commit(Validation validation, ImportMode mode, long batchId);

    /**
     * @param plan     handler-specific normalised records
     * @param accepted rows that will be written
     * @param changes  what a commit does, e.g. created / updated / unchanged counts
     */
    record Validation(List<ImportIssue> issues, int accepted, Map<String, Integer> changes, Object plan) {
    }
}
