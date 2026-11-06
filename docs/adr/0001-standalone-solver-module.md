# ADR-0001: The solver is a standalone Java module

- **Status:** accepted
- **Date:** 2026-11-06

## Context

Scheduling is the heart of ExamGrid and the part with the most algorithmic risk. It must
be unit-tested exhaustively (including property tests over random inputs), benchmarked
in isolation, and explained in interviews without first explaining Spring.

## Decision

`solver/` is a plain Java 21 Maven module with **zero runtime dependencies**. It owns the
problem model, the algorithms and a CLI. The Spring Boot backend depends on it and maps its
JPA entities to `ProblemInstance`; the solver never sees a database, an HTTP request or a
Spring bean.

## Consequences

- Solver tests run in well under a second and need no containers.
- JMH benchmarks measure the algorithms, not framework overhead.
- The backend owns an anti-corruption layer (entity ↔ solver model mapping).
- Progress reporting and cancellation are plain interfaces (`ProgressListener`,
  `CancellationToken`) that the backend adapts to SSE and to its job table.
