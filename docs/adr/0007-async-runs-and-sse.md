# ADR-0007: Runs as a job table with a bounded worker pool and SSE progress

- **Status:** accepted
- **Date:** 2026-11-06

## Context

A solve takes from milliseconds to minutes (the time limit goes up to 10 minutes). An HTTP
request must not wait for it, the API must stay responsive while a solve burns a CPU core,
and the exam cell wants to watch progress ("62 %… score 41") and cancel a run that is
going nowhere.

## Decision

- **`POST /api/schedule-runs` returns `202 Accepted`** with `Location` and the run in state
  `QUEUED`. The request only validates readiness (enrolments, slots, rooms), stores the fully
  resolved parameters (so the run is reproducible) and registers an *after-commit* hook that
  hands the id to the worker. The worker can never start a run whose row a rollback removed.
- **The `schedule_run` row is the job record.** A worker claims a run with
  `update … set status = 'RUNNING' where id = ? and status = 'QUEUED'`; if the update touches
  zero rows, someone else has it (or it was cancelled), so a run never executes twice. Every
  later transition is a single conditional update.
- **Bounded pool of platform threads** (`examgrid.scheduling.worker-threads`, default 2).
  Solving is CPU-bound, so virtual threads would add nothing; HTTP handling keeps virtual
  threads. A cap on queued + running runs (`max-active-runs`) returns `429` beyond it.
- **Snapshot isolation.** `InstanceLoader` reads the catalog in one repeatable-read
  transaction and computes a SHA-256 fingerprint of the input. The run detail reports
  `dataChanged` when the catalog has moved on since.
- **Results are written in one transaction** with JDBC batches (exam, seat and invigilation
  rows), together with metrics JSON and the score. An infeasible instance stores the solver's
  structured report (`INFEASIBLE`), not a stack trace.
- **Progress over Server-Sent Events** (`GET /api/schedule-runs/{id}/events`). The solver's
  `ProgressListener` publishes into `RunProgressBroadcaster`, which keeps the latest event per
  run. A browser that connects late, or reconnects, immediately gets the current state, and
  one terminal `status` event closes the stream. Heartbeat comments every 15 s keep proxies
  from closing idle connections. Progress is also written to the row at most once per second,
  so list views and reconnects after a restart show sensible numbers without hammering the
  database.
- **Cancellation** is cooperative through the solver's `CancellationToken`, checked inside
  the annealing loop. Queued runs are cancelled with a conditional update.
- **Restart recovery.** On startup, runs left `RUNNING` are marked `FAILED` ("interrupted by
  a server restart") and `QUEUED` runs are resubmitted.

## Why SSE, not WebSockets or polling

Updates flow one way. SSE is plain HTTP: it passes Nginx and corporate proxies, works with
standard auth headers via `fetch`, and the browser's reconnect plus our latest-event replay
give resumable progress for free. Polling would either lag or waste requests.

## Consequences and limits

- Single-node: the broadcaster and cancellation tokens are in-memory. Scaling the API
  horizontally would need a shared channel (Redis pub/sub) for events and cancellation.
  The job-table claim already works across nodes.
- Runs are immutable once written; disruptions create derived runs (milestone 7).
