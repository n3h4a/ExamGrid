# Architecture

ExamGrid is a modular monolith. A pure-Java solver library sits inside a Spring Boot API.
A React single-page app is served by Nginx on the same origin. Postgres is the source of
truth and Redis is a disposable cache. The reasons behind each choice are recorded as
decision records in [`docs/adr/`](adr).

## System context and containers

```mermaid
flowchart LR
    subgraph Users
        EC["Exam cell staff<br/>(EXAM_CELL)"]
        FA["Faculty<br/>(FACULTY)"]
        ST["Students<br/>(STUDENT or anonymous)"]
    end

    subgraph Edge
        CADDY["Caddy<br/>TLS, HSTS<br/>(production only)"]
        NGINX["Nginx<br/>SPA, security headers,<br/>/api proxy, unbuffered SSE"]
    end

    subgraph API["Spring Boot API (Java 21)"]
        WEB["REST controllers<br/>JWT filter · RBAC · rate limiter"]
        IMP["Import pipeline<br/>CSV/XLSX, dry run,<br/>all-or-nothing commit"]
        RUN["Run worker pool<br/>bounded, restart recovery"]
        SOLVER["examgrid-solver library<br/>DSatur → SA/tabu → min-cost flow<br/>→ seating CSP → invigilation heap"]
        PUB["Publisher<br/>PDFs, seat index,<br/>pointer switch"]
    end

    PG[("PostgreSQL 16<br/>catalog, runs, plans,<br/>audit, documents")]
    RD[("Redis 7<br/>seat index, published-run<br/>pointer, rate-limit windows")]
    FS[["File storage<br/>uploaded files, PDFs"]]

    EC & FA & ST --> CADDY --> NGINX
    NGINX -->|"/api/*"| WEB
    WEB --> IMP & RUN & PUB
    RUN --> SOLVER
    IMP & RUN & PUB --> PG
    IMP & PUB --> FS
    PUB --> RD
    WEB -->|"seat lookups"| RD
    WEB -.->|"fallback when Redis is down"| PG
```

| Container | Responsibility | Scales by |
|---|---|---|
| Nginx (`frontend/`) | Serves the built SPA with a strict CSP and long-lived caching for hashed assets. Proxies `/api`, `/v3/api-docs` and `/swagger-ui` to the API, with buffering off for SSE. | Static; any number |
| API (`backend/`) | All business logic. Stateless apart from in-flight solver jobs: JWT auth, data in Postgres and Redis. | Instances behind the proxy. Solver concurrency is a bounded pool per instance. |
| Solver (`solver/`) | Pure algorithms with no Spring and no I/O. It is also usable as a CLI and is benchmarked on its own (`benchmarks/`). | CPU per run |
| Postgres | Source of truth: master data, runs, plans, approvals, audit log, document metadata. | Vertical; read replicas if needed |
| Redis | Result-day cache. Losing it costs speed, not correctness (see [load test](benchmarks/load-test.md)). | Vertical |

### Inside the API

The API is organised by feature. Each package owns its controller, service, persistence
and DTOs:

| Package | Owns |
|---|---|
| `auth`, `user`, `security` | login, JWT issue and verify, users, role rules, two filter chains (API and docs) |
| `catalog`, `search` | courses, students, rooms with seat grids, faculty, slots, locks, same-slot groups; trigram search |
| `imports`, `storage` | CSV/XLSX parsing, row validation, advisory-locked commit, raw file archive with SHA-256 |
| `scheduling` | run lifecycle, worker pool, SSE broadcaster, plan persistence and queries, comparison |
| `publishing` | approvals, PDF rendering, seat index, disruption re-seating |
| `portal` | student and faculty self-service; public seat finder |
| `ratelimit`, `common` | Redis fixed-window limiter, Redis circuit breaker, problem responses, request ids |
| `audit` | append-only audit log written in the same transaction as the change |

## Solver pipeline

```mermaid
flowchart LR
    A["ProblemInstance<br/>courses, students, slots,<br/>rooms, faculty, rules"] --> B["Union-find<br/>same-slot groups"]
    B --> C["Weighted<br/>conflict graph"]
    C --> D{"DSatur<br/>with domains,<br/>locks, capacity"}
    D -- "runs out of slots" --> E["TabuCol repair"]
    D -- "clash-free" --> F["Simulated annealing<br/>+ tabu search<br/>(move, swap, Kempe chain)"]
    E -- "repaired" --> F
    E -- "impossible" --> X["Infeasibility report<br/>Hall's condition, cliques,<br/>lock clashes, empty domains"]
    F --> G["Min-cost max-flow<br/>rooms per slot"]
    G --> H["Seating: colour-class packing,<br/>forward-checking CSP fallback"]
    H --> I["Min-heap<br/>invigilation"]
    I --> V["SolutionValidator"]
    V --> S["Solution + metrics"]
```

The details and trade-offs are in ADRs [0002](adr/0002-colouring-strategy.md),
[0003](adr/0003-penalty-model-and-optimisers.md) and
[0004](adr/0004-rooms-seating-invigilation.md).

## Run lifecycle

```mermaid
stateDiagram-v2
    [*] --> QUEUED: POST /api/schedule-runs (202)
    QUEUED --> RUNNING: worker claims it (conditional UPDATE)
    QUEUED --> CANCELLED: cancel
    RUNNING --> CANCELLED: cancel (solver stops at its next check)
    RUNNING --> DONE: valid plan saved
    RUNNING --> INFEASIBLE: report saved
    RUNNING --> FAILED: error, or interrupted by a restart
    DONE --> PUBLISHING: second distinct approval
    PUBLISHING --> PUBLISHED: PDFs + seat index ready, pointer switched
    PUBLISHING --> DONE: publication failed (approvals kept, retry allowed)
    PUBLISHED --> SUPERSEDED: a newer run is published
```

A disruption ("room 204 is flooded") never edits a plan in place. It re-seats only the
affected students into a **new** `DONE` run whose `parent_run_id` is the original, and that
run goes through the same two-person approval.

A queued run survives a restart and is picked up again. A run that was mid-solve when the
server stopped is marked `FAILED` with an explanation, because a half-finished optimisation
cannot be resumed. See [ADR-0007](adr/0007-async-runs-and-sse.md).

## Publishing and result day

```mermaid
sequenceDiagram
    autonumber
    actor A1 as Exam cell (1st)
    actor A2 as Exam cell (2nd)
    participant API
    participant PG as Postgres
    participant W as Publisher
    participant RD as Redis
    actor S as Student

    A1->>API: POST /schedule-runs/7/approvals
    API->>PG: lock run row, check DONE + data fingerprint, insert approval
    A2->>API: POST /schedule-runs/7/approvals
    API->>PG: second distinct approver → status PUBLISHING
    API-->>W: start publication job
    W->>PG: read plan
    W->>W: render hall tickets, seating charts, timetable, roster (PDF)
    W->>PG: store document metadata
    W->>RD: SET examgrid:run:7:seat:<ROLL> for every student
    W->>PG: one transaction: old run SUPERSEDED, run 7 PUBLISHED
    W->>RD: SET examgrid:published-run = 7, expire run 6 keys
    S->>API: GET /api/public/seat?roll=24CS001
    API->>RD: rate-limit INCR, GET pointer, GET seat key
    alt Redis unavailable (circuit open)
        API->>PG: published run id, seats of roll
    end
    API-->>S: exams, rooms and seats (no names)
```

Two people must approve, and the same person cannot approve twice. An approval is refused
if the catalog has changed since the run was solved, because the plan would describe
students who no longer match the data. See [ADR-0008](adr/0008-publishing-and-result-day.md).

## Data model

```mermaid
erDiagram
    COURSE ||--o{ ENROLMENT : has
    STUDENT ||--o{ ENROLMENT : takes
    IMPORT_BATCH |o--o{ ENROLMENT : "loaded by"
    FACULTY ||--o{ FACULTY_COURSE : teaches
    COURSE ||--o{ FACULTY_COURSE : "taught by"
    FACULTY ||--o{ FACULTY_UNAVAILABILITY : "away in"
    EXAM_SLOT ||--o{ FACULTY_UNAVAILABILITY : ""
    COURSE ||--o| COURSE_LOCK : "pinned by"
    EXAM_SLOT ||--o{ COURSE_LOCK : ""
    SAME_SLOT_GROUP ||--|{ SAME_SLOT_GROUP_MEMBER : contains
    COURSE ||--o| SAME_SLOT_GROUP_MEMBER : ""

    SCHEDULE_RUN |o--o{ SCHEDULE_RUN : "derived from"
    SCHEDULE_RUN ||--o{ EXAM_ASSIGNMENT : ""
    SCHEDULE_RUN ||--o{ SEAT_ASSIGNMENT : ""
    SCHEDULE_RUN ||--o{ INVIGILATION : ""
    SCHEDULE_RUN ||--o{ APPROVAL : ""
    SCHEDULE_RUN ||--o{ PUBLISHED_DOCUMENT : ""
    APP_USER ||--o{ APPROVAL : gives

    COURSE ||--o{ EXAM_ASSIGNMENT : ""
    EXAM_SLOT ||--o{ EXAM_ASSIGNMENT : ""
    STUDENT ||--o{ SEAT_ASSIGNMENT : ""
    ROOM ||--o{ SEAT_ASSIGNMENT : ""
    FACULTY ||--o{ INVIGILATION : ""
    ROOM ||--o{ INVIGILATION : ""

    SCHEDULE_RUN {
        bigint id PK
        varchar status
        jsonb params
        jsonb input_summary "counts + data fingerprint"
        jsonb metrics
        jsonb infeasibility
        jsonb disruption
        bigint parent_run_id FK
    }
    SEAT_ASSIGNMENT {
        bigint run_id FK
        bigint student_id FK
        bigint course_id FK
        bigint slot_id FK
        bigint room_id FK
        int seat_row
        int seat_col
    }
    ROOM {
        varchar code UK
        int seat_rows
        int seat_columns
        jsonb blocked_seats
        int capacity
        int priority "fill order for min-cost flow"
    }
```

`audit_log` and `app_user` stand apart from the diagram. Every plan table hangs off
`schedule_run` with `on delete cascade`. Catalog rows referenced by a saved plan are
protected with `on delete restrict`, so an old plan is never silently changed. The schema is
in [`backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration/).

## Cross-cutting concerns

| Concern | How |
|---|---|
| Security | Stateless JWT (HS256), method-level RBAC, portal identity taken only from the token, CSP and security headers at Nginx, rate limiting, audit log. See [`security.md`](security.md). |
| Errors | RFC 9457 problem documents with a stable `code` and a `requestId` |
| Observability | `X-Request-Id` correlation in MDC and responses; structured JSON logs (ECS) in `prod`; Actuator health, liveness and readiness (database included) and metrics |
| Consistency | Imports serialised by a Postgres advisory lock; runs claimed by conditional updates; approvals under a row lock; publication switched in one transaction |
| Resilience | Restart recovery for runs and publications; `RedisCircuit` fails fast to Postgres; rate limiter fails open |
| Delivery | GitHub Actions → tests, coverage, images to GHCR; Render blueprint or single-VM Compose with Caddy. See [`deployment.md`](deployment.md). |
