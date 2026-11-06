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

### Backend layers

Every feature package follows the same shape. Cross-cutting concerns live in servlet
filters and one exception handler, never in controllers.

```mermaid
flowchart TB
    subgraph F["Servlet filters (in order, on every request)"]
        direction LR
        RID["RequestIdFilter<br/>X-Request-Id → MDC + response"] --> RL["RateLimitFilter<br/>/api/public/** and login<br/>Redis fixed window"] --> SEC["Spring Security<br/>JWT decode → roles<br/>401 / 403 as problems"]
    end
    SEC --> C["Controller<br/>@PreAuthorize role check<br/>@Valid request DTOs"]
    C --> SV["Service<br/>@Transactional business rules<br/>audit entry in the same transaction"]
    SV --> DB[("JPA repositories for aggregates<br/>JdbcTemplate for batch SQL")]
    SV --> AS["Async work<br/>run worker pool · publication job"]
    AS --> SOL["examgrid-solver"]
    C -. "any exception" .-> EH["ApiExceptionHandler<br/>RFC 9457 problem + code + requestId"]
```

JPA handles aggregates that are edited one at a time, such as users, runs and catalog rows.
Plain JDBC handles bulk work, where an ORM would get in the way: batch upserts during
imports, writing tens of thousands of seat assignments, and the plan read models.

## Request lifecycle

An authenticated call, for example starting a run, passes through these layers:

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant C as Caddy
    participant N as Nginx
    participant F as Filters
    participant S as Spring Security
    participant API as Controller → Service
    participant PG as Postgres

    B->>C: POST /api/schedule-runs (HTTPS, Bearer token)
    C->>N: proxy, sets X-Forwarded-For to the client IP
    N->>F: proxy /api/*, adds X-Request-Id if absent
    F->>F: request id into MDC (every log line carries it)
    F->>S: rate limit applies only to /api/public/** and login
    S->>S: verify HS256 signature, issuer and expiry, map role
    S->>API: authenticated principal
    API->>API: @PreAuthorize("hasRole('EXAM_CELL')"), validate body
    API->>PG: insert run (QUEUED) + audit entry, one transaction
    API-->>B: 202 Accepted, Location, X-Request-Id
    Note over API,PG: a worker claims the run with a conditional UPDATE<br/>and streams progress on GET /events (SSE)
```

When anything fails, `ApiExceptionHandler` turns the exception into a problem document with a
stable `code` and the same `requestId` that appears in the logs and the audit trail. Stack
traces and SQL never reach the client.

## Authentication flow

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant UI as React app
    participant A as AuthService
    participant T as TokenService
    participant P as Protected endpoint

    U->>UI: username + password
    UI->>A: POST /api/auth/login (rate limited: 10/min per IP)
    A->>A: look up user, and if unknown compare against a dummy hash<br/>so timing does not reveal which usernames exist
    alt wrong password, unknown user or disabled account
        A-->>UI: 401 invalid_credentials (same body for all three), audited
    else valid
        A->>T: issue HS256 token: sub, uid, role, roll or faculty code
        A-->>UI: accessToken, expiresAt, user
        UI->>UI: store the session in sessionStorage<br/>schedule sign-out at expiry
    end
    UI->>P: Authorization: Bearer token
    P->>P: role check, and portal endpoints read the roll or faculty code<br/>from the token only, never from the URL
    P-->>UI: 200, or 401, which signs the UI out
```

Tokens are stateless, so any API instance can verify them without a session store. The
token lives in `sessionStorage` and is sent only as a header: it survives a reload, dies with
the tab, and with no cookies there is no CSRF surface. See [security](security.md).

## Frontend

```mermaid
flowchart LR
    R["React Router<br/>/admin/*, /me, /duties, /seat, /login"] --> G["RequireRole<br/>role-aware guard"] --> L["Layout<br/>role-specific navigation"] --> P["Feature pages<br/>features/*"]
    P --> Q["TanStack Query<br/>cache + invalidation"] --> CL["api/client.ts<br/>bearer token · problem parsing<br/>401 → sign out"] --> API["/api"]
    P --> E["useRunEvents<br/>lib/sse.ts parses the<br/>authenticated fetch stream"] --> API
```

The app has no global store. Server state lives in TanStack Query, and mutations invalidate
the queries they affect. Every screen is a folder under `features/`. Shared UI primitives sit
in `components/ui.tsx`, and pure logic (seat-label maths, formatting, the SSE parser) sits in
`lib/` with unit tests. The [frontend README](../frontend/README.md) covers the design choices.

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

## Deployment

```mermaid
flowchart LR
    subgraph CI["GitHub Actions (every push)"]
        T1["Maven verify<br/>unit, property, Testcontainers"] --> IMG["Build images<br/>push to GHCR on main"]
        T2["Frontend lint, typecheck,<br/>tests, build"] --> IMG
    end
    IMG --> GHCR[("ghcr.io/n3h4a/<br/>examgrid-api · examgrid-web")]

    subgraph VM["Option A: one VM (deploy/docker-compose.prod.yml)"]
        CAD["Caddy :80/:443<br/>automatic TLS, HSTS"] --> WEB["examgrid-web<br/>Nginx + SPA"] --> APIC["examgrid-api"]
        APIC --> PGV[("postgres<br/>volume")]
        APIC --> RDV[("redis")]
        APIC --> FSV[["/data volume<br/>uploads, PDFs"]]
        BK["backup.sh<br/>pg_dump + storage, 14 days"] -.-> PGV
    end

    subgraph RENDER["Option B: Render blueprint (render.yaml)"]
        RW["examgrid-web"] --> RA["examgrid-api"] --> RP[("Render Postgres")]
        RA --> RK[("Render Key Value")]
    end

    GHCR --> VM
    GHCR -.-> RENDER
```

Only the edge proxy publishes ports. Postgres, Redis and the API sit on a private network.
Flyway migrates the schema on start-up, and readiness waits for the database. Upgrading means
pulling new images and recreating the containers. The [deployment guide](deployment.md) covers
both options from scratch.

## Data model

The tables, their constraints, the indexing strategy, the Redis keyspace and the file-storage
layout are documented in [**Data model**](database.md), with a full entity-relationship
diagram.

## Cross-cutting concerns

| Concern | How |
|---|---|
| Security | Stateless JWT (HS256), method-level RBAC, portal identity taken only from the token, CSP and security headers at Nginx, rate limiting, audit log. See [`security.md`](security.md). |
| Errors | RFC 9457 problem documents with a stable `code` and a `requestId` |
| Observability | `X-Request-Id` correlation in MDC and responses; structured JSON logs (ECS) in `prod`; Actuator health, liveness and readiness (database included) and metrics |
| Consistency | Imports serialised by a Postgres advisory lock; runs claimed by conditional updates; approvals under a row lock; publication switched in one transaction |
| Resilience | Restart recovery for runs and publications; `RedisCircuit` fails fast to Postgres; rate limiter fails open |
| Delivery | GitHub Actions → tests, coverage, images to GHCR; Render blueprint or single-VM Compose with Caddy. See [`deployment.md`](deployment.md). |
