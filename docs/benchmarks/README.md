# Solver benchmarks

All numbers below were **measured**, not estimated, on a 4-vCPU Intel Xeon cloud VM with
OpenJDK 21, using synthetic universities from `SyntheticInstanceGenerator`. That generator
produces departments × 4 year cohorts; core courses are shared by a cohort, each student
takes one elective, and about 8 % carry a backlog paper. Every instance has 12 exam days × 2
sessions = 24 slots and about 13 students per course. The optimiser budget is 300,000 moves.

Raw data: [`scaling.csv`](scaling.csv), [`jmh.csv`](jmh.csv). The result-day HTTP load test
(k6, 1,000 concurrent students) is in [`load-test.md`](load-test.md).

![Median solve time rises roughly linearly from 25 ms at 3,295 enrolments to 181 ms at 65,390 enrolments](scaling.svg)

## End-to-end scaling (timetable + rooms + seats + invigilators)

Median of 5 runs after 2 warm-up runs (`ScalingReport`):

| Courses | Students | Enrolments | Slots | Rooms | Median solve | DSatur score | Final score | Same-day pairs | Back-to-back pairs | Seat use | Duty spread |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 48 | 650 | 3,295 | 24 | 6 | 25 ms | 13,512 | 0 | 0 | 0 | 54 % | 5–6 |
| 96 | 1,300 | 6,581 | 24 | 10 | 38 ms | 22,988 | 0 | 0 | 0 | 82 % | 5–6 |
| 192 | 2,600 | 13,160 | 24 | 20 | 59 ms | 51,380 | 0 | 0 | 0 | 90 % | 5–6 |
| 480 | 6,500 | 65,390 | 24 | 98 | 181 ms | 325,560 | 1,938 | 168 | 81 | 99 % | 7–9 |

- **Seat use** is candidates ÷ seats in the rooms actually opened. It rises with size
  because min-cost flow fills rooms in priority order and opens a new room only when needed.
- **Duty spread** is the lightest and heaviest invigilation load across faculty. The min-heap
  keeps it within a couple of duties, even with availability and "not your own paper" rules.
- Every plan passes `SolutionValidator`: no clashes, no over-capacity rooms, no neighbours
  with the same paper, and exactly one seat per enrolment.

## Per-stage micro-benchmarks (JMH)

`java -jar benchmarks/target/examgrid-benchmarks.jar -f 1 -wi 2 -w 1s -i 3 -r 1s`, average
time per operation:

| Courses | Conflict graph | DSatur | Full pipeline |
|---:|---:|---:|---:|
| 50 | 0.08 ms | 0.04 ms | 14.8 ms |
| 100 | 0.16 ms | 0.11 ms | 24.9 ms |
| 200 | 0.32 ms | 0.27 ms | 37.1 ms |
| 500 | 3.39 ms | 1.66 ms | 202 ms |

These were short runs on a shared VM, so the error bars are wide (see `jmh.csv`). Treat them
as orders of magnitude. The conclusion holds regardless: graph construction and DSatur are
negligible, and almost all the time goes to the optimiser and seating, which are bounded by
the move budget.

## Reproduce

```bash
mvn -pl solver,benchmarks package -DskipTests
java -cp benchmarks/target/examgrid-benchmarks.jar com.examgrid.benchmarks.ScalingReport docs/benchmarks
java -jar benchmarks/target/examgrid-benchmarks.jar -f 1 -wi 3 -i 5
```
