# Changelog

All notable changes to ExamGrid are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org). The public API version is the one in
[`docs/api/openapi.json`](docs/api/openapi.json).

## [1.0.0] - 2026-11-06

The first complete release, built in ten milestones ([roadmap](docs/ROADMAP.md)).

### Scheduling engine
- Conflict graph over enrolments, with union-find merging of same-slot groups.
- DSatur colouring with per-paper slot domains, locks and seat capacity, plus TabuCol repair.
- Certified infeasibility reports (Hall's condition per student and on cliques, lock
  clashes, empty domains, oversized papers) that name courses and roll numbers.
- A pairwise penalty model (same day, overnight back-to-back, hard papers too close),
  optimised by simulated annealing with move, swap and Kempe-chain moves, or by tabu search.
- Room allocation by min-cost max-flow with mixing caps; seating by colour-class packing
  with a forward-checking CSP fallback; min-heap invigilation.
- Incremental re-seating when a room is lost, and an independent plan validator.
- A solver CLI and JMH benchmarks.

### API
- Spring Boot 4 on Java 21 with a Flyway-managed PostgreSQL schema.
- JWT authentication, `EXAM_CELL` / `FACULTY` / `STUDENT` roles, an audit log, RFC 9457
  errors with request ids, and OpenAPI documentation.
- CRUD for courses, students, rooms with seat grids, faculty, slots, locks and same-slot
  groups; trigram search.
- CSV and Excel imports with dry runs, row-level reports, all-or-nothing commits and
  archived originals.
- Background runs on a bounded worker pool with SSE progress, cancellation, restart recovery
  and run comparison.
- Two-person approval, PDF documents, a Redis seat index with Postgres fallback, per-IP rate
  limiting, student and faculty portals, and derived disruption runs.

### Web app
- React 19 admin console: dashboard, import wizard, seat-grid editor and setup screens.
- Live run progress, timetable grid, seating charts, invigilation roster, approvals,
  disruption repair and run comparison.
- Student portal with hall tickets, faculty duties and a public seat finder.

### Operations
- Docker images for the API and the web app (Nginx), with Compose files for development,
  local evaluation and production (Caddy with automatic TLS).
- GitHub Actions CI with coverage reporting, images published to GHCR, a Render blueprint,
  a backup script and Dependabot.
- k6 load tests and a measured performance report.

### Fixed
- Seat lookups during a Redis outage no longer wait for Redis timeouts: a circuit breaker
  routes them to Postgres (p95 went from 8.15 s to 564 ms under stress).
- The backend `storage` package was ignored by an over-broad `.gitignore` rule and missing
  from the repository; the rule is now anchored to the runtime data directories.

### Documentation
- A README written as a guided tour, with a banner, annotated screenshots and a demo GIF
  and video.
- Guides for architecture, the data model, algorithms, technology choices, the API (with
  real requests and an error catalogue), security (with an OWASP Top 10 mapping), testing,
  benchmarks, load testing and deployment, plus nine ADRs.
- Scripts that regenerate the banner and every screenshot from the running product.

[1.0.0]: https://github.com/n3h4a/ExamGrid/releases/tag/v1.0.0
