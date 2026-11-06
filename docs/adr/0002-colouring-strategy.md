# ADR-0002: DSatur with TabuCol repair and certified infeasibility

- **Status:** accepted
- **Date:** 2026-11-06

## Context

A clash-free timetable is a K-colouring of the conflict graph, which is NP-hard. Real
instances also carry side constraints the textbook problem does not have: session rules
("labs only in the afternoon"), locked courses, same-slot groups (common papers across
branches) and slot seat capacity. When no timetable exists the exam cell needs to know
*why*, in their terms.

## Decision

1. **Same-slot groups** are merged into a single node with union-find before the graph is
   built, so the colouring cannot separate them.
2. **DSatur**, generalised to domains: pick the node with the fewest open slots (equal to
   highest saturation when domains are uniform), break ties by saturation, degree, index.
   Assign the lowest-numbered slot that is open and has seat capacity. A `TreeSet` priority
   queue gives O((n + m) log n).
3. If DSatur leaves nodes uncoloured, **TabuCol** repair searches within the same K slots
   (objective `1000 × clashes + overflow seats`, Galinier–Hao tenure). DSatur failing is
   not proof of infeasibility, so this matters on tight instances.
4. **Infeasibility analysis** produces issues with a `proof` flag. Proof-producing checks:
   same-slot group conflicts, empty domains, lock clashes, papers larger than all rooms or
   than the rooms' same-paper capacity, total seat capacity, **Hall's condition per student**
   (bipartite matching of a student's exams to their allowed slots) and **Hall's condition on
   the largest clique found** by a greedy heuristic. Only if none applies is the failure
   reported as "unresolved" with the exams the search could not place.

## Alternatives considered

- **Welsh–Powell / largest-first greedy:** simpler but noticeably worse on clustered graphs.
- **Exact colouring (ILP / branch and bound):** exact but exponential; unusable for 500+
  nodes inside an interactive run. Exactness is not required because the optimiser
  (ADR-0003) only needs a feasible start.
- **Timefold/OptaPlanner:** writing the algorithms is the point of the project; a
  comparison can be added later without changing the model.

## Consequences

- Structural checks run before any search, so obviously impossible inputs fail in
  milliseconds with an actionable message.
- Every infeasibility message names courses and sample roll numbers.
