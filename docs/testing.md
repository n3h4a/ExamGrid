# Testing report

Every number below comes from a local run of the same commands CI runs
(`mvn -B -ntp verify`, then `npm run lint`, `typecheck`, `test:coverage` and `build` in
`frontend/`). CI publishes the same coverage table on each workflow run.

## Summary

| Suite | Tool | Tests | Result | Line coverage | Branch coverage |
|---|---|---:|---|---:|---:|
| Solver: unit and property tests | JUnit 6, jqwik, AssertJ | 112 | ✅ all pass | **92.7 %** | 87.3 % |
| Backend: unit tests | JUnit 6, Mockito | 19 | ✅ all pass | — | — |
| Backend: integration tests | Spring Boot Test, Testcontainers (Postgres 16, Redis 7) | 53 | ✅ all pass | — | — |
| Backend: unit + integration combined | JaCoCo merged report | 72 | | **84.7 %** | 67.2 % |
| Frontend | Vitest, Testing Library, jsdom | 31 | ✅ all pass | **50.0 %** | 78.5 % |
| Load: result day | k6 (1,000 concurrent students) | 55,429 requests | ✅ thresholds met | — | — |

The whole Maven build, with both Docker-backed integration suites, takes about one minute.

## Solver (`solver/`)

The solver is pure Java with no I/O, so it is tested hardest. Coverage by package:
coloring 96 %, graph 97 %, optimize 96 %, rooms 98 %, seating 98 %, invigilation 98 %,
incremental 98 %, pipeline 88 %, io 98 %. The CLI wrapper (58 %) is the least covered.

**Property-based tests (jqwik)** generate thousands of random universities and check
invariants instead of hand-picked examples:

| Property | What it guarantees |
|---|---|
| `dsaturNeverUsesMoreThanMaxDegreePlusOneColours` | the classical DSatur bound holds |
| `dsaturIsExactOnBipartiteGraphs` | DSatur is optimal where theory says it must be |
| `initialTimetableIsValidOrExplained` | a timetable is either clash-free or comes with an infeasibility report, never a silent failure |
| `incrementalCostMatchesFullRecomputationAndStaysFeasible` | every O(degree) move delta equals a full recomputation |
| `graphScoreEqualsStudentLevelScore` | the pairwise penalty matrix scores exactly what students experience |
| `optimisersNeverWorsenNorBreakHardConstraints` | annealing and tabu never introduce a clash, break a lock or end worse than DSatur |
| `anyReturnedPlanIsValid` | end to end, every returned plan passes `SolutionValidator`: no clash, no over-full room, no neighbours with the same paper, exactly one seat per enrolment |
| `mostRandomUniversitiesAreSolvable` | generated instances are not trivially infeasible (jqwik statistics: about 78 % solved), so the other properties exercise real plans |
| `parseInvertsLabel` | seat labels round-trip |

**Example-based tests** pin known answers: chromatic numbers of triangles, odd and even
cycles and wheels; Hall's-condition and clique witnesses in infeasibility reports; min-cost
flow costs on small networks; a provably impossible 2×3 seating case; disruption re-seating
that never seats a student in a room invigilated by their own paper's teacher.

## Backend (`backend/`)

Integration tests run the real application against real Postgres and Redis in
Testcontainers. Nothing is mocked: Flyway migrations, Hibernate schema validation, JDBC
batch imports, advisory locks, SSE and Redis Lua scripts all run for real. Test
highlights:

| Area | Tests |
|---|---|
| Security | wrong password and unknown user get the same answer and are audited; disabled accounts; missing or forged tokens; role checks on imports, runs and users; `portalsServeOnlyTheCallersOwnData` (IDOR); security headers on API responses |
| Imports | `dryRunReportsEveryProblemAndWritesNothing`, idempotent re-import, replace mode, missing columns reported against the header, room layouts, faculty links, XLSX workbooks, original file archived with its hash; `SampleDataIT` keeps `samples/` importable |
| Runs | `acceptedRunProducesAValidStoredPlan`, `infeasibleInputsProduceAnExplainedReport`, `runningSolvesCanBeCancelled`, `eventStreamOfAFinishedRunSendsTheFinalStatus` |
| Publishing | `twoDistinctApprovalsPublishDocumentsAndTheSeatIndex`, `plansBuiltFromStaleDataCannotBeApproved`, `publishingANewRunSupersedesTheOldOne`, `seatLookupFallsBackToPostgresAndRepopulatesRedis`, `publicEndpointsAreRateLimitedPerClient` |
| Errors | malformed JSON is a problem document, not a stack trace; the request id is echoed in headers and problems |

Unit tests cover token signing and verification, run-parameter validation, the CSV and
XLSX readers, PDF rendering of every document kind, local file storage, the Redis
circuit breaker, and a seat-index outage test proving that with Redis down only the first
lookup calls Redis.

Coverage of the merged report by package: auth, audit, search 100 %; portal 98 %;
security 97 %; ratelimit 92 %; publishing, scheduling, user 90 %; imports 89 %; catalog
80 %. The demo-data seeder (17 %) runs only with `EXAMGRID_DEMO_ENABLED=true`, which the
tests leave off.

## Frontend (`frontend/`)

Component tests render real pages with React Router and TanStack Query. Only `fetch` is
stubbed, so the API client, error parsing and cache invalidation are exercised too.

| Journey | Test file |
|---|---|
| Sign in, wrong password shows the API problem | `LoginPage.test.tsx` |
| Role guard sends users to their own home | `RequireRole.test.tsx` |
| Import: dry run first, commit only a clean file, replace mode | `ImportsPage.test.tsx`, `ImportReportView.test.tsx` |
| Draw a room's seat grid, block seats | `SeatGridEditor.test.tsx` |
| Start a run with edited weights | `NewRunForm.test.tsx` |
| Live progress over SSE (event parser across chunk boundaries, bearer token, stop at final status) | `sse.test.ts`, `useRunEvents.test.tsx` |
| Approve, withdraw, approval blocked after a data change, documents tab | `RunDetailPage.test.tsx` |
| Timetable grid and seating charts | `TimetableGrid.test.tsx`, `SeatingView.test.tsx` |
| Public seat finder: found, unknown roll, rate limited | `SeatFinderPage.test.tsx` |
| Student portal shows only student navigation and own exams | `MyExamsPage.test.tsx` |

Simple CRUD screens (courses, students, faculty, slots, users, audit) have no tests of their
own. They are thin forms over the same tested components and API client, and the end-to-end
walkthrough below exercises them.

## End to end

The full Docker stack (`docker-compose.full.yml`) was driven with Playwright through the
whole lifecycle:

1. sign in;
2. load the sample data;
3. start a run and watch live progress;
4. approve as two different users and wait for publication;
5. download the PDFs;
6. disrupt a room and compare the runs;
7. use the student portal, faculty portal and public seat finder.

The screenshots in [`docs/screenshots/`](screenshots) come from those runs. The production
build ran under the strict CSP with no console errors.

## Performance

- Solver scaling and JMH micro-benchmarks: [`docs/benchmarks/README.md`](benchmarks/README.md)
- Result-day HTTP load test, including the Redis outage: [`docs/benchmarks/load-test.md`](benchmarks/load-test.md)

## Reproduce

```bash
mvn -B -ntp verify                          # needs Docker for Testcontainers
open backend/target/site/jacoco-merged/index.html
cd frontend && npm ci && npm run test:coverage
```
