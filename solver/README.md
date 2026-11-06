# ExamGrid solver

A framework-free Java 21 engine that turns enrolments into an exam plan. It has no runtime
dependencies, and the Spring backend uses it as a plain library ([ADR-0001](../docs/adr/0001-standalone-solver-module.md)).

## Pipeline

```
ProblemInstance
   │  ExamGroups (union-find over same-slot groups)
   ▼
ConflictGraph ── weighted by shared students / shared hard-paper students
   │
   ▼
InitialTimetable ── structural checks → DSatur → TabuCol repair → InfeasibilityReport
   │
   ▼
Optimizer ── SimulatedAnnealing (move · swap · Kempe chain) | TabuSearch
   │
   ▼
TimetableStage.Outcome ── slot per exam + TimetableMetrics
```

Rooms, seating and invigilation are added on top of this in milestone 3.

## Packages

| Package | Contents |
|---|---|
| `model` | Immutable input records (`Course`, `Slot`, `Room`, `Faculty`, `SolverParams`) and the indexed `ProblemInstance` |
| `graph` | `ExamGroups`, `ConflictGraph`, `CliqueFinder` |
| `coloring` | `DSaturColoring`, `TabuColRepair`, `InfeasibilityAnalyzer`, `InitialTimetable` |
| `optimize` | `PenaltyModel`, incremental `Timetable`, `SimulatedAnnealing`, `TabuSearch`, `TimetableMetrics` |
| `pipeline` | `TimetableStage` orchestration |
| `progress` | `ProgressListener`, `ProgressEvent`, `Phase`, `CancellationToken` |
| `io` | Dependency-free CSV reader/loader and `SyntheticInstanceGenerator` |
| `cli` | `SolverCli` |

## Complexity

| Step | Cost |
|---|---|
| Conflict graph | O(Σ k²) over students with k exams each |
| DSatur | O((n + m) log n + n·K) |
| Annealing move / swap / Kempe | O(deg) / O(deg a + deg b) / O(chain · deg) |
| Hall check per student | O(k² · K) with Kuhn's matching |

## CLI

```bash
mvn -pl solver package
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --synthetic 180:2400 --days 5 --optimizer tabu
java -jar solver/target/examgrid-solver-1.0.0-SNAPSHOT.jar --enrolments enrolments.csv --rooms rooms.csv
```

## Testing

```bash
mvn -pl solver verify
```

Besides example-based tests on known graph families (cycles, wheels, complete and bipartite
graphs), jqwik property tests check that:

- DSatur never uses more than Δ + 1 colours and is exact on bipartite graphs;
- every initial timetable is clash-free, or a non-empty explanation is raised;
- after any random sequence of moves, swaps and Kempe chains, the incremental cost equals a
  full recomputation and every hard constraint still holds;
- the graph-based score equals the score recomputed from raw enrolments;
- optimisers never worsen the start and never move a locked exam.
