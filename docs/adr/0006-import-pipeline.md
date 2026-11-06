# ADR-0006: All-or-nothing imports with dry runs, archived originals and JDBC batch upserts

- **Status:** accepted
- **Date:** 2026-11-06

## Context

The exam cell's source of truth is a spreadsheet. A bad upload (wrong course code, shifted
column, duplicate rows) must never leave the database half-updated, and the cell must be
able to prove later exactly which file produced a timetable.

## Decision

- **Validate everything first, write once.** Handlers (`ImportHandler` per kind) validate
  every row against the file *and* the database, and produce a plan plus a change summary.
  Any error rejects the whole file; otherwise the plan is applied in one transaction.
- **Dry run by default.** `dryRun=true` returns the same report without writing, including
  destructive counts such as `enrolmentsRemoved` in `REPLACE` mode.
- **Unknown courses are errors, unknown students are created.** A mistyped course code would
  otherwise create a phantom exam that gets a slot, a room and an invigilator. New students
  appearing in an enrolment file are expected.
- **Archive the original** bytes under `imports/yyyy/mm/<sha-prefix>-<name>` with the full
  SHA-256 on the batch row, behind a `FileStorage` interface (local disk now, S3/MinIO later).
- **JDBC batch upserts** (`insert … on conflict … do update`) instead of JPA entity-by-entity
  saves. Validation loads existing keys into hash maps once, so a 13 000-row enrolment file is
  O(rows) with a few queries, not O(rows) round-trips.
- **REPLACE via batch tagging.** Every enrolment in the file is upserted with the batch id; one
  `delete … where import_batch_id is distinct from :batch` removes the rest.
- **Serialised imports** with `pg_advisory_xact_lock`, so concurrent uploads cannot validate
  against data the other is about to change.
- **Forgiving reading, strict validation.** Content-sniffed XLSX vs CSV, delimiter detection,
  UTF-8 with Windows-1252 fallback, header aliases, and cells read as Excel displays them (so
  `21CS045` never becomes a number). Values themselves are validated strictly.

## Consequences

- An exam-cell member can upload, read the report, fix the sheet and re-upload without
  any cleanup.
- Re-importing the same file is idempotent (reported as "already enrolled").
- The audit log plus archived files make every timetable traceable to its inputs.
