# ExamGrid

**Exam timetabling, seating and invigilation for universities.** Upload who takes which
course; ExamGrid works out **when** every exam happens, **where** every student sits and
**who** supervises each room, then publishes hall tickets and a "find my seat" page.

> 🚧 Under active development — see [the roadmap](docs/ROADMAP.md).

## Repository layout

| Path | What it is |
|---|---|
| [`solver/`](solver) | Framework-free Java 21 scheduling engine and CLI |
| [`docs/spec`](docs/spec/PROJECT_SPEC.md) | Product and algorithm specification |
| [`docs/adr`](docs/adr) | Architecture decision records |

## Try the solver

```bash
mvn -pl solver package
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400
```

## Algorithms (so far)

- **Conflict graph** built in O(Σ k²) over students, weighted by shared candidates.
- **Union-find** merges "same-slot" common papers into one node.
- **DSatur** with slot domains, locks and seat capacity, O((n + m) log n).
- **TabuCol** repair when greedy colouring runs out of slots.
- **Certified infeasibility**: Hall's condition via bipartite matching per student and on
  the largest clique, lock clashes, capacity checks — each issue names courses and roll
  numbers.

## License

[MIT](LICENSE)
