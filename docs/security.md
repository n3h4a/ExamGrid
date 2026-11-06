# Security model

| Threat | Control | Where |
|---|---|---|
| Unauthenticated access | Stateless JWT (HS256) on every `/api/**` route except login, health and `/api/public/**` | `SecurityConfig` |
| Privilege escalation | Roles `EXAM_CELL`, `FACULTY`, `STUDENT`; `@PreAuthorize` on every controller | controllers |
| IDOR (one student reading another's data) | Student and faculty identity comes **only** from JWT claims; `/api/students/me/**` and `/api/faculty/me/**` take no identifying parameter | `PortalController`, `CurrentUser` |
| Rogue or mistaken publish | Two distinct exam-cell approvals; row lock against races; refusal when data changed since solving | `PublicationService` |
| Repudiation | Append-only audit log of logins, failed logins, imports, runs, approvals, publications and edits, each with request id | `AuditService` |
| Credential stuffing / brute force | Per-IP login rate limit in Redis; uniform failure response and constant-work password check | `RateLimitFilter`, `AuthService` |
| Scraping the seat finder | Per-IP rate limit; responses contain no names | `PublicController` |
| User enumeration | Same 401 body for unknown user, wrong password and disabled account | `AuthService` |
| Path traversal via uploads | Sanitised file names; storage keys validated and resolved strictly inside the root | `ImportService`, `LocalFileStorage` |
| Malicious spreadsheets | Size and row limits, legacy `.xls` refused, content-sniffed format | `TabularFileReader` |
| SQL injection | Parameterised queries everywhere; the few dynamic identifiers are constants | repositories, `SearchController` |
| Information leakage | RFC 9457 problems without stack traces or SQL; `server.error.include-stacktrace: never` | `ApiExceptionHandler` |
| XSS / clickjacking on API responses | `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `X-Frame-Options: DENY`, `nosniff`, `no-referrer` | `SecurityConfig` |
| Lockout of administrators | Cannot disable or demote yourself or the last exam-cell account | `UserService` |
| Secrets in code | JWT secret and passwords only from environment; the app refuses to start without a 32+ character secret | `SecurityProperties` |
