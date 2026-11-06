# ADR-0005: Backend architecture — package by feature, stateless JWT, problem details, snapshot runs

- **Status:** accepted
- **Date:** 2026-11-06

## Decisions

### Package by feature, not by layer

`auth`, `user`, `audit`, and later `catalog`, `imports`, `scheduling`, `publishing` and `portal`
each own their entity, repository, service, controller and DTOs. A change to imports touches
one package, and cross-feature dependencies are visible in imports. `common` holds only
genuinely shared infrastructure.

### Stateless JWT (HS256) instead of server sessions

- The API scales horizontally without sticky sessions or a session store.
- Tokens carry `uid`, `role`, and `roll` or `faculty`. Student and faculty endpoints take the
  identity from these claims only, so a student cannot ask for another student's hall ticket
  by changing an ID in the URL (IDOR is ruled out by construction).
- Spring Security's resource-server support verifies signature, issuer and expiry. Tokens are
  issued with Nimbus via `NimbusJwtEncoder`; no hand-written JWT code.
- CSRF protection is off because no cookie authenticates a request.
- Trade-off: a token cannot be revoked before expiry. The TTL defaults to 8 hours (one
  working day), and disabling an account blocks the next login. A deny-list in Redis can be
  added if needed.

### Errors are RFC 9457 problem details

One `@RestControllerAdvice` plus the security entry point and access-denied handler produce
`application/problem+json` with `type`, `title`, `status`, `detail`, a stable `code` and the
`requestId`. Stack traces and SQL never reach clients. Validation errors list each invalid
field.

### Correlation ids

`RequestIdFilter` accepts a well-formed `X-Request-Id` or generates one, puts it in the MDC,
echoes it, and stores it on every audit entry. A user's error report can be traced to the
exact log lines and audit records.

### Runs are immutable snapshots

Everything hanging off `schedule_run` (exam, seat and invigilation assignments) references
master data with `ON DELETE RESTRICT`, and runs are never edited in place: a disruption
creates a derived run (`parent_run_id`). A partial unique index guarantees at most one
`PUBLISHED` run. JSONB holds run parameters, metrics, infeasibility reports, import reports
and room layouts, which have no relational queries.

### Hibernate validates, Flyway migrates

`ddl-auto: validate` makes the application fail fast if entities and schema drift apart.

## Consequences

- The API surface is documented automatically (springdoc) and testable with plain HTTP.
- Integration tests use a real PostgreSQL via Testcontainers, so JSONB, partial indexes and
  constraint behaviour are tested as they run in production.
