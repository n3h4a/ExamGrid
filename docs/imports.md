# Importing data

Exam cells live in Excel, so every kind of master data can be uploaded as **CSV** (comma,
semicolon or tab separated; UTF-8 or Windows-1252) or **XLSX** (first sheet). Download a
template with `GET /api/imports/templates/{kind}`.

## Flow

```
upload ─► archive original bytes (SHA-256) ─► parse ─► validate every row ─► report
                                                                 │
                                       errors? ── yes ─► REJECTED (nothing written)
                                                                 │ no
                                       dryRun? ── yes ─► VALIDATED (nothing written)
                                                                 │ no
                                                  one transaction ─► COMMITTED
```

- `POST /api/imports/{kind}?dryRun=true` is the default: always look before you leap.
- **All or nothing.** A single error rejects the whole file. Warnings (e.g. duplicate rows)
  skip the row and let the import proceed.
- Every upload is kept in file storage with its SHA-256 and can be downloaded again
  (`GET /api/imports/{id}/file`), so there is a record of exactly what was imported.
- Every upload is audited (`import.validated`, `import.rejected`, `import.committed`).
- Imports are serialised with a PostgreSQL advisory lock, so two people uploading at once
  cannot interleave validation and writes.

## Column formats

Header names are matched loosely: case, spaces, `_`, `-` and `.` are ignored, and common
aliases work (`Roll No`, `Registration Number`, `Course Code`, `Subject Code`, `Branch`, ...).
List cells use `;` (or `|`) as separator.

| Kind | Required | Optional | Notes |
|---|---|---|---|
| `courses` | `code`, `name` | `department`, `hard` (yes/no), `sessions` (`AFTERNOON`) | Upsert by code |
| `students` | `roll` | `name`, `department`, `email` | Upsert by roll |
| `enrolments` | `roll`, `course` | `name`, `department` | Creates students on first sight; **unknown courses are errors** |
| `rooms` | `code`, `rows`, `columns` | `building`, `blocked` (`A1;H10`), `priority` | Priority defaults to file order |
| `faculty` | `code`, `name` | `email`, `department`, `teaches` (course codes), `unavailable` (`2026-12-14` or `2026-12-14-AFTERNOON`) | Replaces teaching and unavailability of each listed person |

Roll numbers must match `EXAMGRID_ROLL_PATTERN` (default `[0-9]{2}[A-Z]{2,4}[0-9]{3}`, e.g.
`21CS045`).

## Modes (enrolments)

- `MERGE` (default) adds what is new.
- `REPLACE` makes the file the complete enrolment list; enrolments not in the file are
  removed. The dry-run report shows `enrolmentsRemoved` before anything happens.

## Report

```json
{
  "batch": { "id": 12, "status": "REJECTED", "sha256": "e776…", "totalRows": 4 },
  "report": {
    "errorCount": 2, "warningCount": 1,
    "issues": [
      { "row": 4, "column": "roll", "severity": "ERROR", "message": "'BAD-ROLL' does not look like a roll number …" },
      { "row": 5, "column": "course", "severity": "ERROR", "message": "Unknown course ZZ999. Import or create the course first." },
      { "row": 3, "column": null, "severity": "WARNING", "message": "Duplicate of row 2 (21CS045 in CS101); skipped." }
    ],
    "changes": { "enrolmentsAdded": 0, "studentsCreated": 0 }
  }
}
```

Row numbers are the ones the user sees in Excel: the header is row 1, and blank lines count.
