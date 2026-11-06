# ExamGrid roadmap: 10 milestones

Each milestone ends in a single commit that leaves the repository buildable and
its tests green. Status is updated as milestones land.

| # | Milestone | Status |
|---|---|---|
| 1 | Solver core: conflict graph, DSatur, infeasibility diagnostics | ✅ |
| 2 | Solver optimisation: penalty model, simulated annealing, tabu search | ✅ |
| 3 | Solver allocation: rooms (min-cost max-flow), seating CSP, invigilation heap, incremental re-seat, benchmarks | ✅ |
| 4 | Backend foundation: Spring Boot, Flyway schema, JWT auth, RBAC, audit log, OpenAPI | ✅ |
| 5 | Master data and import system: CSV/Excel import, validation, file storage, search | ✅ |
| 6 | Scheduling engine: async runs, SSE progress, persisted plans, comparison, cancel | ✅ |
| 7 | Publishing: two-person approval, PDFs, Redis seat cache, rate limiting, portals, disruptions | ✅ |
| 8 | Frontend I: React + TS app shell, auth, dashboard, imports, setup screens | ✅ |
| 9 | Frontend II: runs with live progress, timetable/seating views, approvals, student portal; full-stack Docker | ✅ |
| 10 | CI/CD, deployment, load tests, benchmarks, docs, demo assets | ✅ |

---

## M1 — Solver core

- **Goal:** a pure-Java solver module that turns enrolments into a clash-free timetable or explains why none exists.
- **Features:** problem model; union-find merging of "same-slot" course groups; weighted conflict graph; DSatur with a heap, slot domains (session rules), locks and slot capacity; TabuCol repair when greedy DSatur runs out of slots; infeasibility report (overloaded students, clique lower bound with witness students, lock clashes, empty domains, oversized papers); CSV reader; synthetic instance generator; CLI.
- **Files:** `pom.xml`, `solver/**`, `docs/**`.
- **Tests:** unit tests for graph construction and DSatur on known graphs (triangle, bipartite, cycles, wheels); jqwik properties (valid colouring whenever DSatur succeeds; never more colours than `max degree + 1`).
- **Docs:** spec, roadmap, ADR-0001 (standalone solver), ADR-0002 (colouring strategy).
- **Deployment impact:** none (library + CLI).
- **Done when:** `mvn -pl solver verify` is green and the CLI prints a timetable for synthetic data.
- **Commit:** `feat(solver): add conflict graph, DSatur colouring and infeasibility diagnostics`

## M2 — Solver optimisation

- **Goal:** improve the soft-constraint score without ever breaking a hard constraint.
- **Features:** pairwise penalty model (same day, overnight back-to-back, hard-paper gap); incremental timetable state; move, swap and Kempe-chain neighbourhoods; simulated annealing with automatic temperature calibration (80 % initial acceptance); tabu search; progress listener; cancellation; seeded determinism.
- **Tests:** property test that every incremental delta equals a full recomputation; property test that optimisation never introduces a clash, never breaks a lock and never lowers quality below the DSatur start.
- **Docs:** ADR-0003 (penalty model), solver README.
- **Commit:** `feat(solver): optimise timetables with simulated annealing and tabu search`

## M3 — Solver allocation

- **Goal:** complete plans: rooms, seats and invigilators, plus incremental repair.
- **Features:** min-cost max-flow room allocation with a mixing cap; grid seating with colour-class partitioning and forward-checking backtracking; min-heap invigilation; disruption re-seating; full pipeline and validator; JMH benchmark module.
- **Tests:** jqwik end-to-end properties (no clash, no over-capacity room, no adjacent same paper, exactly one seat per enrolment); targeted tests for each allocator.
- **Docs:** `docs/benchmarks/README.md` with measured numbers, ADR-0004.
- **Commit:** `feat(solver): allocate rooms, seats and invigilators with incremental re-seating`

## M4 — Backend foundation

- **Goal:** a secure, observable Spring Boot service on Postgres.
- **Features:** Flyway schema for the full data model; JWT login; roles `EXAM_CELL`, `FACULTY`, `STUDENT`; user management; RFC 9457 problem responses; audit log; request correlation IDs; OpenAPI; health/metrics.
- **Tests:** Testcontainers integration tests for auth and RBAC; unit tests for token service.
- **Commit:** `feat(backend): bootstrap Spring Boot API with schema, JWT auth and audit log`

## M5 — Master data and imports

- **Goal:** get real exam-cell data in safely.
- **Features:** CRUD for courses, students, rooms (seat-grid layouts), faculty, slots (generator), locks and same-slot groups; CSV and XLSX import with dry run, row-level errors, all-or-nothing commit; raw file archiving with SHA-256; trigram search.
- **Tests:** parser unit tests; import integration tests (unknown course, duplicate rows, bad roll numbers).
- **Commit:** `feat(backend): add master data APIs and validated CSV/Excel imports`

## M6 — Scheduling engine

- **Goal:** run the solver as a background job with live progress.
- **Features:** `POST /schedule-runs` → 202; bounded worker pool; restart recovery; SSE progress stream with last-event replay; persisted assignments and metrics; run comparison; cancellation; infeasibility report surfaced via API.
- **Tests:** integration test from import → run → DONE; SSE test.
- **Commit:** `feat(backend): run the solver asynchronously with SSE progress`

## M7 — Publishing and result day

- **Goal:** lock, publish and serve the plan at scale.
- **Features:** two-person approval; PDF hall tickets, seating charts, timetable and invigilation roster; Redis pre-computed seat index; Redis rate limiting; `/students/me/**` and `/faculty/me/**`; disruption endpoint creating a derived run.
- **Tests:** approval rules, IDOR test, cache hit/miss test, PDF smoke tests.
- **Commit:** `feat(backend): add two-person publishing, PDFs and Redis-backed seat search`

## M8 — Frontend I

- **Goal:** a usable admin console.
- **Features:** Vite + React + TS + TanStack Query + Tailwind; login; role-aware routing; dashboard; import wizard with dry-run report; setup screens for slots, rooms, faculty and courses.
- **Tests:** Vitest + Testing Library.
- **Commit:** `feat(frontend): add admin console with imports and setup screens`

## M9 — Frontend II and full-stack Docker

- **Goal:** the whole workflow in the browser, running in containers.
- **Features:** run creation with live SSE progress; run detail (metrics, timetable grid, seating chart, invigilation); run comparison; approvals; student portal; faculty duties; public seat finder; Dockerfiles and Compose with Nginx.
- **Commit:** `feat(frontend): add live schedule runs, plan views and portals; dockerise stack`

## M10 — Ship it

- **Goal:** CI/CD, deployment and a portfolio-grade repository.
- **Features:** GitHub Actions (tests, build, images to GHCR); Render blueprint and single-VM production Compose; k6 load test; benchmark chart; README, architecture diagrams, API docs, testing report, screenshots, GIF and video.
- **Commit:** `chore: add CI/CD, deployment configs, load tests and project documentation`
