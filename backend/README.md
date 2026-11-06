# ExamGrid backend

Spring Boot 4 (Java 21) REST API on PostgreSQL. It wraps the framework-free
[solver](../solver) and owns persistence, security, imports, background runs and publishing.

## Run locally

```bash
docker compose up -d                                  # PostgreSQL + Redis
mvn -pl solver -am install -DskipTests                # once
SPRING_PROFILES_ACTIVE=dev mvn -pl backend spring-boot:run
```

The `dev` profile uses a throwaway JWT secret, creates `admin / admin12345` on an empty
database and seeds a **synthetic demo university** (120 courses, 1,500 students, 14 rooms,
61 faculty, 20 slots) with demo accounts `examcell2`, a student and a faculty member
(password `demo12345`).

- API docs: <http://localhost:8080/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`)
- Health: <http://localhost:8080/actuator/health>

```bash
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .accessToken)
curl -s localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `EXAMGRID_DB_URL` | `jdbc:postgresql://localhost:5432/examgrid` | JDBC URL |
| `EXAMGRID_DB_USERNAME` / `_PASSWORD` | `examgrid` | Database credentials |
| `EXAMGRID_JWT_SECRET` | none, **required** | HS256 signing secret, ≥ 32 characters |
| `EXAMGRID_TOKEN_TTL` | `8h` | Access token lifetime |
| `EXAMGRID_CORS_ORIGINS` | `http://localhost:5173` | Comma-separated browser origins |
| `EXAMGRID_BOOTSTRAP_ADMIN_USERNAME` / `_PASSWORD` | empty | First admin on an empty database |
| `EXAMGRID_STORAGE_ROOT` | `./data/storage` | Where uploaded files and PDFs are stored |
| `EXAMGRID_ROLL_PATTERN` | `[0-9]{2}[A-Z]{2,4}[0-9]{3}` | Roll number format enforced by imports |
| `EXAMGRID_DEMO_ENABLED` | `false` (`true` in `dev`) | Seed the synthetic demo university into an empty database |
| `EXAMGRID_SCHEDULING_WORKER_THREADS` | `2` | Solves that may run concurrently |
| `EXAMGRID_SCHEDULING_MAX_ACTIVE_RUNS` | `10` | Queued + running runs before `429` |
| `SPRING_PROFILES_ACTIVE` | none | `dev` locally, `prod` for JSON (ECS) logs |
| `PORT` | `8080` | HTTP port |

## Package structure

Package by feature: each feature owns its entity, repository, service, controller and DTOs.

```
com.examgrid.backend
├── auth        login, /me, password change
├── user        accounts, roles, bootstrap admin
├── catalog     courses, students, rooms, faculty, slots, locks, same-slot groups
├── imports     CSV/XLSX upload → validate → report → all-or-nothing commit
├── storage     FileStorage abstraction, local-disk implementation
├── scheduling  runs as jobs: snapshot loader, worker pool, SSE progress, plan storage and views
├── search      trigram search across students, courses, faculty, rooms
├── demo        synthetic demo university seeder
├── audit       append-only audit log + search API
├── security    JWT issue/verify, filter chains, CurrentUser, roles
└── common      problem-detail errors, request ids, paging, OpenAPI, clock
```

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/password` |
| Users | `GET/POST /api/users`, `GET/PATCH /api/users/{id}` |
| Master data | `/api/courses`, `/api/students`, `/api/rooms`, `/api/faculty` (CRUD), `GET /api/catalog/summary` |
| Schedule setup | `GET/POST /api/slots`, `POST /api/slots/generate`, `PUT/DELETE /api/locks/{course}`, `/api/same-slot-groups` |
| Imports | `POST /api/imports/{kind}?dryRun&mode`, `GET /api/imports`, `GET /api/imports/{id}[/file]`, `GET /api/imports/templates/{kind}` |
| Search | `GET /api/search?q=` |
| Schedule runs | `POST /api/schedule-runs` (202), `GET /api/schedule-runs[/{id}]`, `GET …/{id}/events` (SSE), `POST …/{id}/cancel`, `DELETE …/{id}` |
| Plan views | `GET …/{id}/timetable`, `…/{id}/rooms?slot=`, `…/{id}/invigilation`, `…/{id}/students/{roll}`, `GET /api/schedule-runs/compare?ids=` |
| Audit | `GET /api/audit` |

See [docs/imports.md](../docs/imports.md) for file formats.

## Watching a run

```bash
curl -s -XPOST localhost:8080/api/schedule-runs -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"label":"first try","timeLimitSeconds":30}'
curl -N localhost:8080/api/schedule-runs/1/events -H "Authorization: Bearer $TOKEN"
# event:progress  data:{"runId":1,"status":"RUNNING","phase":"OPTIMISING","pct":38,"score":132,...}
# event:status    data:{"runId":1,"status":"DONE","phase":"DONE","pct":100,"score":0,...}
```

See [ADR-0007](../docs/adr/0007-async-runs-and-sse.md) for the job model.

## Conventions

- **Errors** are RFC 9457 `application/problem+json` with a stable `code` and the `requestId`.
  Clients switch on `code`, never on the message.
- **Every state change is audited** in the same transaction as the change; failed logins are
  audited in their own transaction so they survive the rollback.
- **Identity for portal users comes only from the JWT** (`roll`, `faculty` claims) via
  `CurrentUser`, never from a URL parameter.
- **Schema changes are Flyway migrations**; Hibernate only validates (`ddl-auto: validate`).

## Tests

```bash
mvn -pl backend verify    # unit tests (surefire) + *IT integration tests (failsafe, Testcontainers)
```

Integration tests start one shared `postgres:16-alpine` container and run the full application
against it, so Docker must be available.
