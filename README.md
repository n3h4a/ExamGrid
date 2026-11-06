# ExamGrid

**Exam timetabling, seating and invigilation for universities.** Upload who takes which
course; ExamGrid works out **when** every exam happens, **where** every student sits and
**who** supervises each room, then publishes hall tickets and a "find my seat" page.

> 🚧 Under active development — see [the roadmap](docs/ROADMAP.md).

## Repository layout

| Path | What it is |
|---|---|
| [`solver/`](solver) | Framework-free Java 21 scheduling engine and CLI ([details](solver/README.md)) |
| [`docs/spec`](docs/spec/PROJECT_SPEC.md) | Product and algorithm specification |
| [`docs/adr`](docs/adr) | Architecture decision records |

## Try the solver

```bash
mvn -pl solver package
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400 --days 5 --optimizer tabu
```

## Algorithms (so far)

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

On a synthetic 168-course, 2,400-student university the solver goes from a DSatur score of
50,021 to 0 (no student with two papers on one day) in about 120 ms.

## License

[MIT](LICENSE)
