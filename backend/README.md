# ExamGrid backend

Spring Boot 4 (Java 21) REST API on PostgreSQL. It wraps the framework-free
[solver](../solver) and owns persistence, security, imports, background runs and publishing.

## Run locally

```bash
docker compose up -d                                  # PostgreSQL + Redis
mvn -pl solver -am install -DskipTests                # once
SPRING_PROFILES_ACTIVE=dev mvn -pl backend spring-boot:run
```

The `dev` profile uses a throwaway JWT secret and creates `admin / admin12345` on an empty
database.

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
| `SPRING_PROFILES_ACTIVE` | none | `dev` locally, `prod` for JSON (ECS) logs |
| `PORT` | `8080` | HTTP port |

## Package structure

Package by feature: each feature owns its entity, repository, service, controller and DTOs.

```
com.examgrid.backend
├── auth        login, /me, password change
├── user        accounts, roles, bootstrap admin
├── audit       append-only audit log + search API
├── security    JWT issue/verify, filter chains, CurrentUser, roles
└── common      problem-detail errors, request ids, paging, OpenAPI, clock
```

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
