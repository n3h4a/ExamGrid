# ADR-0003: Pairwise penalty model, simulated annealing by default, tabu search as alternative

- **Status:** accepted
- **Date:** 2026-11-06

## Context

DSatur (ADR-0002) produces a *valid* timetable, but it packs exams into the lowest-numbered
slots, so many students get two papers on one day. The spec's score is:

```
score = 10 × same-day + 3 × back-to-back + 5 × hard papers less than N days apart
```

The optimiser evaluates millions of candidate moves, so the score must be cheap to update.

## Decision

### Penalty model

Every term is counted **per pair of exams a student sits**. Because of that, the score is a
sum over conflict-graph edges:

```
cost(u, v) = students(u, v) · pair[s_u][s_v] + hardStudents(u, v) · hard[s_u][s_v]
```

`pair` and `hard` are precomputed K × K matrices:

| Term | Condition on slots a ≠ b |
|---|---|
| same day | same calendar date |
| back-to-back | adjacent in slot order **and** on consecutive calendar days (afternoon → next morning); Friday PM → Monday AM is not back-to-back |
| hard gap | fewer than `hardMinGapDays` calendar days apart (applies on top of same day) |

Moving node v costs O(deg v). Both matrices are symmetric, so a swap or Kempe-chain
interchange leaves the cost of the edges inside the move unchanged, and only edges that
leave it need re-evaluating.

`TimetableMetrics` recomputes the score from raw enrolments, independently of the graph. A
property test asserts both always agree.

### Hard constraints are never relaxed

Every move is checked before its delta is computed: no neighbour already in the target slot,
target slot in the node's domain, node not locked, target slot keeps enough seats. The search
only ever visits feasible timetables, so whatever it returns at any point can be used.

### Simulated annealing (default)

- Neighbourhoods: move (50 %), swap (25 %), Kempe chain (25 %). Kempe chains swap two slots
  on a connected component of the subgraph induced by those slots, so they are clash-free by
  construction. They are the move that lets the search escape plateaus that single moves
  cannot.
- `T0 = −avg(Δ⁺) / ln(0.8)`: about 80 % of worsening moves are accepted at first, measured on
  500 sampled moves.
- Geometric cooling `T ← 0.995 · T`. The block length is chosen so the iteration budget runs
  out when even the **cheapest** worsening move (one student × smallest non-zero weight) is
  accepted only 0.1 % of the time. A first version calibrated the end temperature on the
  *average* worsening move from the DSatur start. That move is huge, so the search never got
  cold enough to fine-tune: on the 168-course benchmark it finished at score 331 instead of 0.
- The best solution seen is restored at the end. Seeded `SplittableRandom` keeps runs
  reproducible.

### Tabu search (alternative)

Best non-tabu move among all slots for a sample of 40 nodes per iteration, tenure 7–11,
aspiration on new bests. The budget counts evaluated moves, so `maxIterations` means the
same amount of work for both optimisers.

## Measurements (synthetic data, 300 k move budget, one core)

| Instance | DSatur score | Annealing | Tabu |
|---|---|---|---|
| 168 courses / 2 400 students / 20 slots | 50 021 | **0** (122 ms) | 0 (75 ms) |
| 168 courses / 2 400 students / 10 slots | 50 514 | **2 390** | 5 934 |
| 480 courses / 6 000 students / 24 slots | 298 168 | **1 717** (217 ms) | 4 183 |

Tabu converges faster on easy instances; annealing is clearly better when slots are
scarce, which is the case that matters. Annealing is therefore the default.

## Consequences

- The score is explainable line by line: the review screen shows each term separately.
- Changing a weight never needs code changes; weights are run parameters.
- Pairwise counting means a student with three papers on one day counts as three same-day
  pairs. That is the standard proximity-cost convention, and it pushes harder against the
  worst cases than counting students would.
