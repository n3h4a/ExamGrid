# ExamGrid: project specification

> This is the product specification ExamGrid was built against, kept as the original brief.
> Where the implementation refined it, the decision is recorded in [`docs/adr/`](../adr) and
> summarised in [section 10](#10-where-the-implementation-refines-this-spec). For how the
> system works today, start with the [architecture](../architecture.md).

## 1. The idea in one line

You upload the list of which student takes which course. ExamGrid works out **when** each exam happens (timetable), **where** each student sits (seating) and **who** supervises each room (invigilation). It then produces PDFs and a "find my seat" page. Today the exam cell does all of this by hand in Excel.

---

## 2. The end-to-end flow

```
 Exam cell uploads CSV/Excel        Admin clicks "Generate"         Students on result day
 (student × course)                         │                               │
        │                                   ▼                               ▼
        ▼                          ┌──────────────────┐          GET /api/public/seat?roll=
 POST /api/imports/enrolments      │ POST /api/sched- │                     │
        │                          │ ule-runs  → 202  │                     ▼
        ▼                          └────────┬─────────┘             ┌──────────────┐
 Validate → Postgres                        │ job queued            │ Redis cache  │ ← fast
 (raw file → file storage)                  ▼                       └──────┬───────┘
                                   ┌──────────────────┐                    │ miss
                                   │  Solver worker   │                    ▼
                                   │ 1. conflict graph│              Postgres
                                   │ 2. DSatur colour │
                                   │ 3. annealing     │──SSE progress──► React UI
                                   │ 4. rooms + seats │   "62%… score 41"
                                   │ 5. invigilators  │
                                   └────────┬─────────┘
                                            ▼
                                schedule_run = DONE, score saved
                                            ▼
                         Admin A approves + Admin B approves → PUBLISHED
                                            ▼
                         PDFs generated, schedule pushed into Redis
```

### Step by step

1. **Import.** The exam cell uploads a file of rows like `21CS045, CS301`. The backend checks for unknown courses, duplicate rows and bad roll numbers, then writes `student`, `course` and `enrolment`. The original file goes to file storage so there is a record of exactly what was imported.
2. **Configure.** The admin defines the **slots** (for example 10 days × Morning/Afternoon = 20 slots), the **rooms** (capacity plus a seat-grid layout) and the **faculty** available to invigilate.
3. **Start a run.** `POST /api/schedule-runs` returns **202 Accepted** straight away with a run ID. A solve can take seconds to minutes, so the HTTP request must not wait for it.
4. **Watch progress.** The UI opens `GET /api/schedule-runs/{id}/events`, a Server-Sent Events stream. The worker pushes messages such as `{"phase":"OPTIMISING","pct":62,"score":41}`.
5. **Review.** The admin looks at the result: clashes (must be 0), students with two exams on one day, room usage and the overall score. They can re-run with different parameters. Every run is saved, so runs can be compared.
6. **Publish.** One admin approves and a **second, different** admin must also approve. After that the plan is locked, hall-ticket and seating PDFs are generated, and everything is cached in Redis.
7. **Result day.** Students log in to download their hall ticket, or search by roll number for their seat. These reads hit Redis, not the solver or a heavy SQL query.

---

## 3. The algorithms

### 3a. Conflict graph

- **Nodes** are courses.
- **Edges** connect two courses that share at least one student.

```
Students:  Asha → CS301, MA201       Ravi → CS301, PH101 (backlog)
           Meena → MA201, PH101      Kiran → EE210

      CS301 ──── MA201
         \        /
          \      /
          PH101            EE210  (no edges, so it can go in any slot)
```

Two courses joined by an edge can never be in the same slot. **Slots become colours**, so "clash-free timetable" means "colour the graph with at most K colours so that no edge joins two nodes of the same colour". That is exactly graph colouring.

### 3b. DSatur (Degree of Saturation): the first, clash-free solution

Graph colouring is **NP-hard**. DSatur is a greedy heuristic that works very well in practice:

1. Pick the uncoloured course with the **most distinct colours already used by its neighbours** (its saturation). Break ties by highest degree.
2. Give it the lowest-numbered slot that does not clash.
3. Repeat until every course has a slot.

It is exact for bipartite graphs, cycles and wheels, and runs in O((n + m) log n) with a heap.

**If DSatur needs more colours than you have slots**, the instance may truly be infeasible. The tool must report *which* students or courses cause it rather than fail silently.

### 3c. Simulated annealing / tabu search: improving the soft constraints

```
score = 10 × (students with 2 exams on the same day)
      +  3 × (students with exams in back-to-back slots)
      +  5 × (hard papers less than N days apart)
```

Simulated annealing loops: make a small move (move one course, or swap two), **reject at once if it creates a clash**, accept improvements, accept worse moves with probability `e^(−Δ/T)`, and cool `T = T × 0.995`. Start T so that about 80% of bad moves are accepted at first. **Tabu search** is the alternative: take the best neighbouring move while keeping a short tabu list of recent moves.

### 3d. Room allocation

Modelled as **max-flow**: source → course (capacity = number of students) → room (capacity = seats) → sink. If max-flow equals the total number of students, everyone fits. (This is flow with capacities, not one-to-one bipartite matching.)

### 3e. Seating: constraint satisfaction on a grid

A room is a grid graph; **adjacent seats never hold the same paper**. Two papers → checkerboard. Three or more → backtracking with forward checking. If one paper makes up more than about half a room a valid seating is impossible, so room allocation must mix papers. Seats are numbered and ordered by roll number within each paper.

### 3f. Fair invigilation: min-heap

A **min-heap of faculty keyed by duties assigned so far**. For each (room, slot), pop the lowest-load faculty member who is free in that slot and not teaching that paper. O(log F) per assignment.

### 3g. Incremental re-solve: "Room 204 is flooded"

Freeze the timetable, take only the students who were in Room 204, re-seat them into spare capacity in **the same slot** or into a reserve room, and report the change: "37 students moved, 0 others affected."

---

## 4. Tech stack

| Layer | Choice |
|---|---|
| Frontend | React + Vite + TypeScript, TanStack Query |
| Backend API | Spring Boot (Java 21), Spring Security |
| Solver | Plain Java module with no Spring dependency |
| Background jobs | Bounded thread pool backed by the `schedule_run` table |
| Progress | Server-Sent Events |
| Database | PostgreSQL + Flyway, JSONB for room layouts and run params |
| Cache | Redis for published seat lookups and rate limiting |
| File storage | Local disk volume (pluggable interface) |
| PDF | OpenPDF |
| Excel import | Apache POI / Commons CSV |
| Auth | Spring Security + JWT; roles `EXAM_CELL`, `FACULTY`, `STUDENT` |
| Testing | JUnit, jqwik, Testcontainers, JMH |
| Load testing | k6 |
| Deployment | Docker Compose, GitHub Actions, Nginx |

## 5. Data model

```
student ─┐                         ┌─ course
         └── enrolment(student, course) ──┘     ← the input; builds the conflict graph

slot(date, session)    room(capacity, layout_json)    faculty

schedule_run(id, params, status, score)          ← one row per "Generate" click
   ├── exam_assignment(run, course → slot)
   ├── seat_assignment(run, student → room, seat)
   └── invigilation(run, faculty → room, slot)

audit_log(who, what, when, before, after)
approval(run_id, admin_id)                       ← two rows needed to publish
```

## 6. Security

- **RBAC:** students can call only `/api/students/me/...`; the student ID comes from the JWT, never from a URL parameter (prevents IDOR).
- **Two-person publish:** two distinct admin approvals before `PUBLISHED`.
- **Audit log:** every import, run, manual edit and publish.
- **Seat search:** public, rate-limited, returns only room and seat — never names.

## 7. Scalability: result day

Pre-compute `roll → {room, seat, slot}` into Redis at publish time; a lookup is one Redis `GET`. Hall-ticket PDFs are generated once at publish. The solver runs on a separate worker pool so a big solve cannot slow down the API. Benchmark solve time against 50/100/200/500 courses.

## 8. Testing

- **Property tests** (jqwik): no student has two exams in the same slot, no room over capacity, no two adjacent seats hold the same paper, every enrolled student has exactly one seat per exam.
- **Integration tests** with Testcontainers: real Postgres and Redis, covering import → run → publish.
- **Benchmarks** on synthetic data.

## 9. Things to watch out for

- Real rules are messy: "labs only in the afternoon", "this professor is away on the 14th", "common papers across branches must be in the same slot".
- Infeasibility needs good error messages.
- Manual overrides: admins pin a course to a slot ("locked" assignments).
- Only claim résumé numbers once measured.

## 10. Where the implementation refines this spec

| Spec | Implementation | Why |
|---|---|---|
| Rooms by max-flow (3d) | **Min-cost** max-flow with a per-room mixing cap, iterated with seating | Rooms fill in priority order, and a single paper can no longer fill a room it could not be seated in ([ADR-0004](../adr/0004-rooms-seating-invigilation.md)) |
| `room(capacity, layout_json)` | `seat_rows`, `seat_columns`, `blocked_seats` (JSON) and a derived `capacity` | The seat grid is what seating needs; capacity follows from it |
| `audit_log(who, what, when, before, after)` | `actor`, `action`, `entity_type`, `entity_id`, `details` (JSON), `request_id` | One flexible `details` document per action, linked to the request id in the logs |
| DSatur may need more colours than slots (3b) | DSatur with per-paper slot domains, then TabuCol repair, then certified infeasibility proofs | Greedy failure is not proof; the exam cell gets a proof or a plan ([ADR-0002](../adr/0002-colouring-strategy.md)) |
| Score counts *students* with two exams on a day | Counts *pairs of papers* a student sits | Makes the score a sum over graph edges, so moves cost O(degree) ([ADR-0003](../adr/0003-penalty-model-and-optimisers.md)) |
| Redis as the result-day store | Redis as a cache with a circuit breaker and a Postgres fallback | Losing Redis costs speed, never correctness ([load test](../benchmarks/load-test.md)) |
| Admin approvals | Approvals refused when the catalog changed after the solve | A plan must describe the data it will be published against |

