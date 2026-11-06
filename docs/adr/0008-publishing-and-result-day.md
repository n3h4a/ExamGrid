# ADR-0008: Two-person publishing, precomputed documents, Redis seat index with Postgres fallback

- **Status:** accepted
- **Date:** 2026-11-06

## Context

Publishing a timetable is irreversible in practice: thousands of students act on it. On
result day, every student looks up their seat within the same few minutes, and the data is
read-only by then.

## Decisions

### Two-person rule

A `DONE` run is published only after `examgrid.publishing.required-approvals` (default 2)
**different** exam-cell accounts approve it.

- One approval per person per run is the `approval` primary key; a second click by the
  same person is a `409 already_approved`.
- Approvals take a `SELECT … FOR UPDATE` row lock on the run, so two simultaneous second
  approvals cannot both start publishing.
- An approval is refused (`409 data_changed`) if the catalog fingerprint differs from the one
  the run was solved on. Nobody can publish a plan that does not match current enrolments.
- Approvals can be withdrawn until publishing starts. Everything is audited.

### Publish once, serve many

The publication job (single background thread, resumed after restart) does all the expensive
work before the plan goes live:

1. Renders **every hall ticket, one seating chart per slot (one page per room), the timetable
   and the invigilation roster** with OpenPDF, stores them in `FileStorage`, and indexes them
   in `published_document`. About 1,500 hall tickets plus charts take under 4 s.
2. Writes the **Redis seat index**, `examgrid:run:<id>:seat:<ROLL>` → JSON list of
   `{course, slot, room, seat}`, in pipelined batches of 1,000.
3. In one transaction, marks the previous live run `SUPERSEDED` and this one `PUBLISHED`
   (the partial unique index guarantees one), then switches the Redis pointer
   `examgrid:published-run` and gives the old run's keys a one-hour TTL (found with `SCAN`,
   never `KEYS`).

If any step fails, the run goes back to `DONE` with its approvals intact and
`POST /publish` retries.

### Redis is a cache, Postgres is the truth

`SeatIndex.lookup` is one `GET` on a hit. On a miss it reads Postgres and re-populates the
key. On a Redis error it reads Postgres directly. A lost pointer is rebuilt from the
`PUBLISHED` row. Losing Redis therefore costs speed, never correctness. For the same reason
Redis is excluded from the readiness probe.

### Rate limiting

`/api/public/**` (default 60/min) and `POST /api/auth/login` (default 10/min) are limited per
client IP. The counters live in Redis (one atomic Lua `INCR` + `EXPIRE` per request), so every
API instance shares them, and `429` responses carry `Retry-After`. The limiter **fails open**
if Redis is down, because rejecting every student on result day is worse than briefly not
limiting.

### What is public

`GET /api/public/seat?roll=` returns course, time, room and seat, never the student's name:
the same information a notice board shows. Names and hall tickets require a student login,
and the student comes from the JWT (`/api/students/me/...`), never from a parameter.

### Disruptions create derived runs

`POST /api/schedule-runs/{id}/disruptions {roomCode, slot}` rebuilds the stored plan, runs the
solver's `DisruptionReseater` (only the displaced students move) and stores the result as a
new `DONE` run with `parent_run_id`. That run goes through the same two-person approval.
Emergency changes get the same scrutiny as the original plan.

## Consequences

- Result-day reads are O(1) Redis lookups; Postgres sees only cache misses.
- Generated documents are immutable per run, so a reprint always matches what was approved.
- Horizontal scaling of the read path needs nothing beyond more API instances.
