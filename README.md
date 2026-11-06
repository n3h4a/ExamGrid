# ExamGrid

**Exam timetabling, seating and invigilation for universities.** Upload who takes which
course; ExamGrid works out **when** every exam happens, **where** every student sits and
**who** supervises each room, then publishes hall tickets and a "find my seat" page.

> 🚧 Under active development — see [the roadmap](docs/ROADMAP.md).

## Repository layout

| Path | What it is |
|---|---|
| [`solver/`](solver) | Framework-free Java 21 scheduling engine and CLI ([details](solver/README.md)) |
| [`backend/`](backend) | Spring Boot 4 REST API: PostgreSQL, JWT, audit log ([details](backend/README.md)) |
| [`benchmarks/`](benchmarks) | JMH benchmarks and the scaling report |
| [`samples/`](samples) | Small fictional dataset for trying imports |
| [`docs/spec`](docs/spec/PROJECT_SPEC.md) | Product and algorithm specification |
| [`docs/benchmarks`](docs/benchmarks/README.md) | Measured solver performance |
| [`docs/imports.md`](docs/imports.md) | CSV/Excel import formats and rules |
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
