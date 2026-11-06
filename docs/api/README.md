# ExamGrid HTTP API

The full contract is [`openapi.json`](openapi.json) (OpenAPI 3.1, 69 operations), exported
from the running service. With the stack up, the interactive Swagger UI is at
`/swagger-ui.html` and the live spec at `/v3/api-docs`. To hide them on a public
deployment, set `SPRINGDOC_APIDOCS_ENABLED=false` and `SPRINGDOC_SWAGGERUI_ENABLED=false`.

## Conventions

**Authentication.** `POST /api/auth/login` returns a bearer token (HS256 JWT, 8 h by
default). Send it as `Authorization: Bearer <token>`. The API is stateless: no session or
cookie.

```bash
TOKEN=$(curl -s -X POST localhost:8088/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .accessToken)
curl -s localhost:8088/api/catalog/summary -H "Authorization: Bearer $TOKEN"
```

**Roles.** `EXAM_CELL` runs the system. `STUDENT` and `FACULTY` reach only the portal
endpoints. Their identity comes from the token, never from a path or query parameter, so
one student cannot ask for another's seats.

**Errors** are RFC 9457 problem documents with two extra fields. `code` is stable for
clients to switch on. `requestId` matches the `X-Request-Id` response header and the server
logs. Validation errors list each field:

```json
{
  "type": "https://examgrid.dev/problems/validation_failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request has invalid fields.",
  "instance": "/api/auth/login",
  "code": "validation_failed",
  "requestId": "3ce2875823204cd0ea210cbfb975d772",
  "errors": [{ "field": "password", "message": "must not be blank" }]
}
```

**Rate limits.** `/api/public/**` allows 60 requests per minute per client IP and login
allows 10. Over the limit, the API answers `429` with `Retry-After`. Both limits can be
configured (`EXAMGRID_RATE_LIMIT_PUBLIC`, `EXAMGRID_RATE_LIMIT_LOGIN`).

**Long-running work.** `POST /api/schedule-runs` returns `202 Accepted` at once. Follow the
solve on `GET /api/schedule-runs/{id}/events`, a Server-Sent Events stream: `progress` events
while solving, then a final `status` event. A client that connects late gets the latest
event replayed first.

**Imports** default to `dryRun=true`: the file is validated and the report returned, but
nothing changes. Repeat with `dryRun=false` to commit. A commit is all-or-nothing. See
[`docs/imports.md`](../imports.md).

**Pagination.** List endpoints take `page` (from 0) and `size`, and return
`{ items, page, size, totalItems, totalPages }`.

## Endpoints

### Authentication

_Access: anyone for login; signed in otherwise_

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/login` | Exchange username and password for a bearer token |
| `GET` | `/api/auth/me` | The authenticated user |
| `POST` | `/api/auth/password` | Change your own password |

### Public

_Access: anyone (rate limited)_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/public/seat` | Find my seat: exams, rooms and seats for a roll number |
| `GET` | `/api/public/status` | Whether a timetable is published, and its date range |

### Portals

_Access: STUDENT or FACULTY (own data only)_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/faculty/me/duties` | My invigilation duties in the published timetable |
| `GET` | `/api/students/me/exams` | My exams, rooms and seats in the published timetable |
| `GET` | `/api/students/me/hall-ticket` | Download my hall ticket (PDF) |

### Imports

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/imports` |  |
| `GET` | `/api/imports/templates/{kind}` | CSV template with the expected columns and example rows |
| `GET` | `/api/imports/{id}` |  |
| `GET` | `/api/imports/{id}/file` | Download the exact file that was uploaded |
| `POST` | `/api/imports/{kind}` | Upload a CSV or XLSX file. dryRun=true (default) only validates; mode=REPLACE makes an enrolment file the complete list |

### Courses

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/courses` | List or search courses (code or name), optionally by department |
| `POST` | `/api/courses` |  |
| `GET` | `/api/courses/{id}` |  |
| `PUT` | `/api/courses/{id}` |  |
| `DELETE` | `/api/courses/{id}` | Delete a course and its enrolments (refused if a saved run uses it) |
| `GET` | `/api/courses/{id}/students` | Candidates enrolled in a course |

### Students

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/students` |  |
| `POST` | `/api/students` |  |
| `GET` | `/api/students/{id}` |  |
| `PUT` | `/api/students/{id}` |  |
| `DELETE` | `/api/students/{id}` |  |

### Rooms

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/rooms` |  |
| `POST` | `/api/rooms` |  |
| `GET` | `/api/rooms/{id}` |  |
| `PUT` | `/api/rooms/{id}` |  |
| `DELETE` | `/api/rooms/{id}` |  |

### Faculty

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/faculty` |  |
| `POST` | `/api/faculty` |  |
| `GET` | `/api/faculty/{id}` |  |
| `PUT` | `/api/faculty/{id}` |  |
| `DELETE` | `/api/faculty/{id}` |  |

### Schedule setup

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/catalog/summary` | Counts of every kind of master data, for the dashboard |
| `GET` | `/api/locks` |  |
| `PUT` | `/api/locks/{courseCode}` | Pin a course's exam to a slot |
| `DELETE` | `/api/locks/{courseCode}` |  |
| `GET` | `/api/same-slot-groups` |  |
| `POST` | `/api/same-slot-groups` |  |
| `PUT` | `/api/same-slot-groups/{id}` |  |
| `DELETE` | `/api/same-slot-groups/{id}` |  |
| `GET` | `/api/slots` |  |
| `POST` | `/api/slots` |  |
| `POST` | `/api/slots/generate` | Create all slots in a date range, skipping weekdays and holidays |
| `DELETE` | `/api/slots/{id}` |  |

### Schedule runs

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/schedule-runs` |  |
| `POST` | `/api/schedule-runs` | Start a solve. Returns 202 at once; follow progress on /events |
| `GET` | `/api/schedule-runs/compare` | Side-by-side parameters and metrics of up to five runs |
| `GET` | `/api/schedule-runs/{id}` | Run detail: parameters, metrics, infeasibility report, approvals |
| `DELETE` | `/api/schedule-runs/{id}` |  |
| `POST` | `/api/schedule-runs/{id}/cancel` |  |
| `GET` | `/api/schedule-runs/{id}/events` | Server-Sent Events: 'progress' events while solving, then one 'status' event |
| `GET` | `/api/schedule-runs/{id}/invigilation` |  |
| `GET` | `/api/schedule-runs/{id}/rooms` | Seating charts of every room used in a slot |
| `GET` | `/api/schedule-runs/{id}/students/{roll}` | One student's exams and seats in this run |
| `GET` | `/api/schedule-runs/{id}/timetable` | Every exam with its slot, candidates and rooms |

### Publishing

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/schedule-runs/{id}/approvals` | Approve a finished plan; the second distinct approval starts publishing |
| `DELETE` | `/api/schedule-runs/{id}/approvals/me` | Withdraw your approval before the plan is published |
| `POST` | `/api/schedule-runs/{id}/disruptions` | Re-seat the students of a lost room into a new derived run |
| `GET` | `/api/schedule-runs/{id}/documents` |  |
| `GET` | `/api/schedule-runs/{id}/documents/{docId}` |  |
| `POST` | `/api/schedule-runs/{id}/publish` | Retry a publication that failed |

### Search

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/search` | Search students, courses, faculty and rooms |

### Users

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/users` | List or search users |
| `POST` | `/api/users` | Create a user |
| `GET` | `/api/users/{id}` |  |
| `PATCH` | `/api/users/{id}` | Update name, role, enabled flag or reset the password |

### Audit log

_Access: EXAM_CELL_

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/audit` | Search the audit log, newest first |
