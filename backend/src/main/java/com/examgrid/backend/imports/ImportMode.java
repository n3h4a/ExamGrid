package com.examgrid.backend.imports;

/** How an import treats existing data. */
public enum ImportMode {
    /** Add new records and update existing ones; nothing is removed. */
    MERGE,
    /** Enrolments only: the file becomes the complete enrolment list; enrolments not in it are removed. */
    REPLACE
}
