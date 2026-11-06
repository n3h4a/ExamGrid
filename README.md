# ExamGrid

**Exam timetabling, seating and invigilation for universities.** Upload who takes which
course; ExamGrid works out **when** every exam happens, **where** every student sits and
**who** supervises each room, then publishes hall tickets and a "find my seat" page.

> 🚧 Under active development — see [the roadmap](docs/ROADMAP.md).

## Repository layout

| Path | What it is |
|---|---|
| [`solver/`](solver) | Framework-free Java 21 scheduling engine and CLI ([details](solver/README.md)) |
| [`frontend/`](frontend) | React 19 + TypeScript admin console, portals and seat finder ([details](frontend/README.md)) |
| [`backend/`](backend) | Spring Boot 4 REST API: PostgreSQL, JWT, audit log ([details](backend/README.md)) |
| [`benchmarks/`](benchmarks) | JMH benchmarks and the scaling report |
| [`samples/`](samples) | Small fictional dataset for trying imports |
| [`docs/spec`](docs/spec/PROJECT_SPEC.md) | Product and algorithm specification |
| [`docs/benchmarks`](docs/benchmarks/README.md) | Measured solver performance |
| [`docs/imports.md`](docs/imports.md) | CSV/Excel import formats and rules |
| [`docs/security.md`](docs/security.md) | Threats and controls |
| [`docs/adr`](docs/adr) | Architecture decision records |

## Try the solver

```bash
mvn -pl solver package
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400 --days 5 --optimizer tabu
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400 --out plan/   # CSV export
```

## Run the API locally

```bash
docker compose up -d
mvn -pl solver -am install -DskipTests
SPRING_PROFILES_ACTIVE=dev mvn -pl backend spring-boot:run   # admin / admin12345, demo data seeded
open http://localhost:8080/swagger-ui.html
```

## Admin console

![Dashboard](docs/screenshots/dashboard.png)

```bash
cd frontend && npm ci && npm run dev   # http://localhost:5173 (backend on :8080)
```

## How a run works

`POST /api/schedule-runs` returns **202** immediately; a worker pool solves the snapshot of
the catalog in the background while the browser watches **Server-Sent Events**
(`phase`, `pct`, `score`). The stored plan can be browsed as a timetable, room seating charts,
invigilation duties or per-student schedules, compared with other runs, and is flagged
stale if the data changes afterwards ([ADR-0007](docs/adr/0007-async-runs-and-sse.md)).

## Publishing and result day

Two **different** exam-cell members must approve a plan. The publication job then renders
every hall ticket, seating chart, the timetable and the invigilation roster as PDFs, and
pre-computes `roll → {course, slot, room, seat}` into **Redis**. On result day a seat lookup
is one Redis `GET` (with a Postgres fallback), the public finder is rate-limited per IP and
never shows names, and students download their own hall ticket with identity taken from the
JWT only ([ADR-0008](docs/adr/0008-publishing-and-result-day.md), [security model](docs/security.md)).

## Algorithms

- **Conflict graph** built in O(Σ k²) over students, weighted by shared candidates.
- **Union-find** merges "same-slot" common papers into one node.
- **DSatur** with slot domains, locks and seat capacity, O((n + m) log n).
- **TabuCol** repair when greedy colouring runs out of slots.
- **Certified infeasibility**: Hall's condition via bipartite matching per student and on
  the largest clique, lock clashes, capacity checks — each issue names courses and roll
  numbers.
- **Pairwise penalty model** (same day, overnight back-to-back, hard-paper gap) with O(deg)
  incremental deltas.
- **Simulated annealing** over move, swap and Kempe-chain neighbourhoods with automatic
  temperature calibration, and **tabu search** as an alternative
  ([ADR-0003](docs/adr/0003-penalty-model-and-optimisers.md)).

- **Min-cost max-flow** room allocation with per-room mixing caps.
- **Seating CSP**: colour-class packing (checkerboard) with a forward-checking backtracking
  fallback, so no two neighbours ever sit the same paper.
- **Min-heap invigilation** that keeps duty loads fair and never assigns a teacher to their
  own paper.
- **Incremental re-seating** when a room is lost: "37 students moved, 0 others affected".
  ([ADR-0004](docs/adr/0004-rooms-seating-invigilation.md))

Measured on synthetic data: a full plan (timetable, rooms, seats and invigilators) for
**2,600 students in 59 ms** and for **6,500 students / 65,000 enrolments in 181 ms**; see
[benchmarks](docs/benchmarks/README.md).

## License

[MIT](LICENSE)
