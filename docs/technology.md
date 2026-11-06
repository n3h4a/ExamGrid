# Technology choices

Each choice below states what it buys, what it costs, and what was considered instead.
Versions are the ones pinned in the build files. Larger decisions have their own
[ADRs](adr).

## The shape: a modular monolith

| | |
|---|---|
| **Chosen** | One Spring Boot service with feature packages, plus the solver as a separate Maven module with no framework dependencies |
| **Why** | One exam cell, one database and one deployable. Feature packages (`imports`, `scheduling`, `publishing`, …) keep the seams clean, and the solver boundary is enforced by the build: it cannot import Spring |
| **Cost** | Solving and serving share a JVM, so a heavy solve competes with requests. A bounded worker pool (2 threads by default) and a cap on active runs contain that |
| **Instead** | Microservices (a separate solver service and queue) would add network calls, deployment units and failure modes with no benefit at this scale. The solver module could become its own service later without code changes ([ADR-0001](adr/0001-standalone-solver-module.md)) |

## Backend

### Java 21 and Spring Boot 4.1

- **Why.** Records model the solver's immutable inputs and the API's DTOs without
  boilerplate. Spring Boot provides Spring Security
  (resource-server JWT), Bean Validation, Flyway, Actuator, problem details and first-class
  Testcontainers support, all maintained together.
- **Cost.** A larger memory footprint and slower start-up (seconds) than a micro-framework,
  which doesn't matter for a long-running service.
- **Instead.** Quarkus or Micronaut start faster but would not change anything a user notices.
  Node or Python would make the solver hot loops (millions of moves per second) much slower
  or push them into native code.

### PostgreSQL 16, with Flyway, JPA and plain JDBC

- **Why Postgres.** The problem is relational and full of invariants, so the database
  enforces them: unique seats, a single published plan (a partial unique index), distinct
  approvers. Postgres also brings advisory locks to serialise imports, row locks for
  approvals, `jsonb` for run parameters and reports, and `pg_trgm` for search. One store
  covers everything ([data model](database.md)).
- **Why JPA and JDBC.** JPA suits aggregates edited one at a time (users, runs, catalog rows).
  `JdbcTemplate` handles bulk paths such as import upserts and writing tens of thousands of
  seat assignments, where an ORM would add overhead and hide the SQL. Hibernate validates the
  schema at start-up, so the two cannot drift.
- **Why Flyway.** The schema lives in versioned SQL in the repository and is migrated on
  start-up: reviewable, repeatable, no manual steps.
- **Cost.** It scales vertically. One university's data (thousands of students, tens of
  thousands of enrolments) is far inside a small instance.
- **Instead.** MySQL has no partial indexes or `pg_trgm`. A document store would move the
  invariants into application code.

### Redis 7, as a cache only

- **Why.** On result day, a seat lookup is one `GET` of a precomputed key, and the rate
  limiter is an atomic Lua `INCR` + `EXPIRE`. The p95 latency was 3.4 ms with 1,000
  concurrent students ([load test](benchmarks/load-test.md)).
- **Cost.** One more service to run, and a cache that can go stale. Staleness is avoided by
  design: keys are namespaced per run, and a single pointer switch makes a new plan live.
  Outages are absorbed by `RedisCircuit`, which skips Redis for 10 s after a failure and
  answers from Postgres. Losing Redis costs speed, never correctness.
- **Instead.** Caching inside the application would be lost on restart and not shared
  between instances. Serving every lookup from Postgres works (it is the fallback) but at
  about a third of the throughput.

### A job table instead of a message broker

- **Why.** Runs are rare (a handful a day) and long (seconds). A `schedule_run` row with a
  status column is the queue: a worker claims a run with a conditional
  `UPDATE … WHERE status = 'QUEUED'`, progress goes to subscribers over SSE, and a restart
  re-queues waiting runs and marks interrupted ones as failed
  ([ADR-0007](adr/0007-async-runs-and-sse.md)).
- **Cost.** It is not built for high-throughput messaging, and doesn't need to be.
- **Instead.** RabbitMQ or Kafka would add infrastructure, a second source of truth for run
  state, and delivery semantics to reason about, all to move a few messages a day.

### Server-Sent Events for progress

- **Why.** Progress is one-way, server to browser. SSE is plain HTTP, so it passes proxies.
  The client reconnects with backoff, and the server replays the latest event to anyone who
  joins late, so a reload never shows a blank progress bar.
- **Cost.** `EventSource` cannot send an `Authorization` header, so the frontend reads the
  stream with `fetch` and a small parser (`lib/sse.ts`). Nginx must not buffer that location.
- **Instead.** WebSockets are bidirectional, which is unnecessary here, and need upgrade
  handling at every proxy. Polling is wasteful and laggy.

### Stateless JWT (HS256, Nimbus via Spring Security)

- **Why.** No session store: any API instance verifies any request, and portal identity (roll
  number, faculty code) travels in the signed claims. That makes cross-student access
  impossible by construction.
- **Cost.** A token cannot be revoked before it expires (8 h by default, configurable).
  Disabling an account blocks new logins immediately. HS256 means the API both signs and
  verifies tokens, which is fine for a single service.
- **Instead.** Server sessions with cookies need a shared store and CSRF protection. RS256
  becomes worthwhile once other services must verify tokens without being able to issue them.

### Documents and imports: OpenPDF 3.0, Apache POI 5.5, Commons CSV 1.14

- **Why.** Pure-Java libraries with no native dependencies. PDFs are generated once at
  publication and then served as files. POI reads `.xlsx` exports straight from the exam
  cell's spreadsheets. Legacy `.xls` files are refused, and files are size- and row-limited.
- **Cost.** PDF layout in code is verbose compared with HTML-to-PDF.
- **Instead.** Headless-browser HTML-to-PDF gives prettier layouts but needs a browser in the
  API container.

## Frontend

### React 19, TypeScript 5.9 (strict), Vite 7

- **Why.** The admin console is interactive: live progress, seat-grid editing, timetable
  highlighting, modal workflows. React's component model and TypeScript's checked API types
  (`api/types.ts` mirrors the backend DTOs) catch contract drift at build time. Vite gives
  instant dev reloads and a small production bundle (about 140 kB of JavaScript, gzipped).
- **Cost.** A client-rendered single-page app has to load its JavaScript before the first
  render. For a staff tool and a one-screen seat finder, that is acceptable.
- **Instead.** Server-rendered templates (Thymeleaf) would make the live run view and the
  seat-grid editor awkward. Next.js would bring a Node server that this static app doesn't need.

### TanStack Query 5, React Router 7, Tailwind CSS 4

- **TanStack Query** owns all server state: caching, refetching and invalidation after
  mutations (a committed import refreshes the dashboard counts). No Redux and no hand-rolled
  global store.
- **React Router** with a `RequireRole` guard gives role-aware routes. The backend enforces
  the same rules independently.
- **Tailwind** keeps styling next to the markup with one design scale. The shared primitives
  in `components/ui.tsx` keep screens consistent.

## Delivery

### Docker multi-stage images, Nginx 1.27, Caddy 2.8

- **API image.** Built by Maven, then copied as layered Spring Boot jar layers onto
  `eclipse-temurin:21-jre-alpine`, running as a non-root user. Dependency layers stay cached
  between releases.
- **Web image.** Built by Node 22, then served by `nginx:1.27-alpine`. Nginx serves the SPA
  with a strict CSP, caches hashed assets for a year, proxies `/api` and keeps SSE unbuffered.
  Serving the app and the API from one origin removes CORS from production
  ([ADR-0009](adr/0009-containers-and-single-origin.md)).
- **Caddy** terminates TLS with automatic certificates in the single-VM deployment.
- **Cost.** Two images and a proxy rather than one fat jar serving static files. In return,
  the API stays a pure API and the UI's caching and security headers live in one place.

### GitHub Actions and GHCR

- **Why.** CI sits next to the code and runs on every push and pull request: Maven verify
  (including Testcontainers integration tests), frontend lint, typecheck, tests and build, a
  coverage summary, and then images pushed to GHCR from `main`. Dependabot proposes grouped
  minor and patch updates monthly.
- **Instead.** An external CI service would add another account and integration for no gain.

## Testing tools

| Tool | Used for | Why this one |
|---|---|---|
| JUnit 6 + AssertJ | Unit tests everywhere in the JVM | Standard and expressive |
| **jqwik** 1.10 | Property tests on generated universities | Finds edge cases example tests miss, with shrinking to minimal failures |
| **Testcontainers** | Integration tests against real Postgres 16 and Redis 7 | Tests exercise Flyway, SQL, locks and Lua scripts for real; no in-memory database pretending to be Postgres |
| JMH 1.37 | Solver micro-benchmarks | Avoids JIT and dead-code pitfalls of hand-rolled timing |
| Vitest 3 + Testing Library | Frontend components and journeys | Vite-native and fast; tests what users see, not implementation details |
| k6 | Result-day load tests | Scriptable scenarios with thresholds, run against the real container stack |
| Playwright | Screenshots and the demo walkthrough (`tools/`) | Drives the real UI, so documentation images stay true |
