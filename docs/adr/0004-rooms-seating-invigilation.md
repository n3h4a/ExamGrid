# ADR-0004: Rooms by min-cost max-flow, seating by colour classes then CSP, invigilation by min-heap

- **Status:** accepted
- **Date:** 2026-11-06

## Context

With the timetable fixed, each slot needs three more decisions: which room each candidate
sits in, which seat, and who supervises. Hard rules:

- room capacity;
- **no two neighbouring seats hold the same paper** (left/right/front/back, optionally diagonals);
- invigilators are available, are in one room at a time, and never supervise a paper they teach.

The spec models rooms as "bipartite matching". That is not right: many students go to one
room, so this is flow with capacities.

## Decision

### Room allocation: min-cost max-flow with a mixing cap

```
source ─(candidates)→ paper ─(mixing cap)→ room ─(seats, cost = room rank)→ sink
```

- Max flow = number of candidates ⇔ everyone fits.
- The cost per unit on room → sink edges is the room's priority rank, so rooms fill in priority
  order and the plan opens as few rooms as it can.
- The **mixing cap** on paper → room edges is the room's largest colour class: the most
  candidates of one paper that can sit without being neighbours. Without it, one paper
  could fill a room, and no valid seating would exist (the spec's "trap").
- The cap is necessary but not sufficient. If seating rejects a room, that room's cap for its
  largest paper is lowered to its current count − 1 and the flow is solved again. Each round
  tightens one cap strictly, so the loop terminates.
- SPFA-based successive shortest paths. The networks are tiny (papers in a slot + rooms), so
  that beats implementing a more complex algorithm.

### Seating: two steps

1. **Colour-class packing.** Seats are split into independent classes: a checkerboard, or
   four `(row%2, col%2)` classes with diagonals. Seats in one class are never adjacent. If every
   paper fits entirely inside one class (a 2- or 4-bin packing, solved exactly by backtracking
   with symmetry breaking), the seating is valid by construction. With two papers this is the
   checkerboard from the spec.
2. **CSP fallback.** Otherwise a paper must straddle classes. Seats are then assigned in
   row-major order by backtracking with forward checking: after each assignment, every paper
   must still have enough unassigned seats without a same-paper neighbour. Both steps are
   budgeted; failure feeds back into the cap-tightening loop above.

Students are then given their paper's seats in roll-number order, so exam-cell staff and
students can find seats quickly.

### Invigilation: min-heap

Faculty sit in a heap keyed by `(duties so far, index)`. For each required invigilator, entries
are popped until an eligible one appears; skipped entries are pushed back afterwards. O(log F)
per assignment. Without constraints, loads differ by at most one (property-tested); with
constraints the measured spread stays at 1–2 duties.

### Incremental re-seating ("Room 204 is flooded")

The timetable is frozen and every unaffected student keeps their seat. Displaced students
first fill spare seats in rooms already open. A seat takes the waiting paper with the most
students that is not sat by any neighbour and not taught by that room's invigilators; the
validator caught that last rule missing in the first version. Remaining students go to
reserve rooms through the normal allocator. Invigilators are topped up through the same
heap, seeded with current loads. The result reports "N students moved, 0 others affected".

### Validation

`SolutionValidator` re-checks every hard constraint from raw data, sharing no code with the
algorithms. The pipeline refuses to return a plan that fails it.

## Consequences

- Seating failures are reported per slot with the papers involved, not as a stack trace.
- Diagonal adjacency is a run parameter, not a code change.
- The flow model leaves room to add costs later (e.g. keep a department in one building)
  without changing the algorithm.
