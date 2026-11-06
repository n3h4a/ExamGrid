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

## Walkthrough: one season over HTTP

Every request and response below was captured from the demo stack
(`docker compose -f docker-compose.full.yml up`). Responses are trimmed where marked with `…`.

### 1. Sign in

```http
POST /api/auth/login
Content-Type: application/json

{"username": "admin", "password": "admin12345"}
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJ…",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-07T04:07:27Z",
  "user": { "id": 1, "username": "admin", "displayName": "Exam cell administrator", "role": "EXAM_CELL",
            "studentRoll": null, "facultyCode": null, "enabled": true, "…": "…" }
}
```

Send the token as `Authorization: Bearer <accessToken>` on every other call.

### 2. Check a file before importing it

```bash
curl -X POST "localhost:8088/api/imports/enrolments?dryRun=true" \
  -H "Authorization: Bearer $TOKEN" -F "file=@enrolments.csv;type=text/csv"
```

```json
{
  "batch": { "id": 2, "kind": "ENROLMENTS", "status": "REJECTED", "dryRun": true,
             "totalRows": 3, "acceptedRows": 0, "errorCount": 1,
             "sha256": "9833d5af99b6455e84550dda5ce389e863ef8656b59a6b4053a298ed7e2b58a4", "…": "…" },
  "report": {
    "issues": [
      { "row": 4, "column": "course", "severity": "ERROR",
        "message": "Unknown course ZZ999. Import or create the course first." }
    ],
    "changes": { "enrolmentsAdded": 1, "alreadyEnrolled": 1, "studentsCreated": 1, "…": "…" }
  }
}
```

The file is archived either way. Repeat with `dryRun=false` once the report is clean; the
commit is all-or-nothing.

### 3. Start a run and follow it live

```http
POST /api/schedule-runs
Content-Type: application/json

{"label": "API example", "optimizer": "ANNEALING", "timeLimitSeconds": 5}
```

```http
HTTP/1.1 202 Accepted
Location: /api/schedule-runs/3
X-Request-Id: 514d049b3874397a821b948559885408

{"id":3,"label":"API example","status":"QUEUED","progressPct":0,"score":null,"createdBy":"admin", …}
```

`GET /api/schedule-runs/{id}/events` streams Server-Sent Events. Here is a real stream,
trimmed from 29 progress events:

```text
event:progress
data:{"runId":4,"status":"RUNNING","phase":"COLOURING","pct":3,"score":null,"message":"120 exams, 531 conflicts"}

event:progress
data:{"runId":4,"status":"RUNNING","phase":"OPTIMISING","pct":8,"score":30864,"message":"DSatur used 11 slots"}

event:progress
data:{"runId":4,"status":"RUNNING","phase":"OPTIMISING","pct":85,"score":395,"message":"timetable score 395"}

event:progress
data:{"runId":4,"status":"RUNNING","phase":"ROOMS","pct":85,"score":395,"message":"seating 2026-11-30-MORNING"}

event:progress
data:{"runId":4,"status":"RUNNING","phase":"SEATING","pct":96,"score":395,"message":"7590 seats assigned"}

event:status
data:{"runId":4,"status":"DONE","phase":"DONE","pct":100,"score":395,"message":"plan ready"}
```

The stream ends after the `status` event. Browse the plan with `GET …/{id}` (metrics),
`…/timetable`, `…/rooms?slot=…`, `…/invigilation` and `…/students/{roll}`.

### 4. Approve as two different people

```http
POST /api/schedule-runs/4/approvals
Content-Type: application/json

{"comment": "Checked clashes and rooms"}
```

```json
{"approvals": 1, "required": 2, "publishing": false}
```

The second approval, by a different exam-cell member, returns `"publishing": true`, and the
run moves to `PUBLISHING` and then `PUBLISHED`. Approving twice as the same person:

```json
{
  "type": "https://examgrid.dev/problems/already_approved",
  "title": "Conflict",
  "status": 409,
  "detail": "You have already approved run 4. A different exam-cell member must give the next approval.",
  "instance": "/api/schedule-runs/4/approvals",
  "code": "already_approved",
  "requestId": "a7e7aadeaa7382083c2772083478a82a"
}
```

### 5. Result day: find a seat (no login)

```http
GET /api/public/seat?roll=23CE001
```

```json
{
  "roll": "23CE001",
  "exams": [
    { "courseCode": "CE401", "courseName": "CE paper 4.1", "slot": "2026-12-01-AFTERNOON",
      "date": "2026-12-01", "session": "AFTERNOON", "roomCode": "R103", "building": "Block A", "seat": "A1" },
    "…"
  ]
}
```

No names are returned. Over the limit, the same network gets:

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
Retry-After: 13

{"type":"https://examgrid.dev/problems/rate_limited","title":"Too Many Requests","status":429,
 "detail":"Too many requests; try again in 13 seconds.","code":"rate_limited","requestId":"216d8ccf31afb4a717107a3b1e660b39"}
```

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

## Error codes

Every error is a problem document whose `type` ends in its `code`. Clients should switch on
`code`, never on `detail`, which is written for people.

**Generic**

| Status | Code | Meaning |
|---|---|---|
| 400 | `validation_failed` | Body or parameter validation failed; `errors` lists each field |
| 400 | `malformed_request` | Unreadable JSON or a wrong type; `detail` names the field |
| 4xx | `request_error` | Any other request Spring rejects, with its standard status: missing parameter (400), wrong method (405), upload too large (413), wrong media type (415) |
| 401 | `unauthenticated` | No token, or an invalid or expired token |
| 401 | `invalid_credentials` | Login failed; deliberately the same for unknown user, wrong password and disabled account |
| 403 | `forbidden` | Signed in, but the role may not do this |
| 404 | `not_found` | The resource does not exist |
| 409 | `conflict` | A database constraint rejected the change |
| 409 | `concurrent_update` | Someone else changed the record first; reload and retry |
| 429 | `rate_limited` | Too many public or login requests from this network; see `Retry-After` |
| 500 | `internal_error` | Unexpected failure; quote `requestId` to the operator |

**Catalog and setup**

| Status | Code | Meaning |
|---|---|---|
| 400 | `code_immutable` | Codes and roll numbers cannot be changed after creation |
| 409 | `duplicate_code`, `duplicate_roll`, `duplicate_slot`, `duplicate_name` | That code, roll number, slot or group name already exists |
| 400 | `invalid_seat`, `no_usable_seats` | A blocked-seat label is malformed or outside the grid, or every seat is blocked |
| 400 | `invalid_slot`, `unknown_slot`, `invalid_range`, `too_many_slots` | Slot key or date range is invalid |
| 400 | `session_not_allowed` | A lock puts a course in a session it is not allowed in |
| 400 | `unknown_course` | A group or lock names courses that do not exist |
| 400 | `group_too_small` | A same-slot group needs at least two courses |
| 409 | `course_in_other_group` | A course can be in only one same-slot group |

**Imports**

| Status | Code | Meaning |
|---|---|---|
| 400 | `empty_file` | The upload is empty |
| 400 | `unsupported_format` | Legacy `.xls`; save as `.xlsx` or CSV |
| 400 | `malformed_csv`, `malformed_xlsx` | The file cannot be parsed |
| 400 | `too_many_rows`, `too_many_columns` | Outside the safety limits |
| 400 | `unsupported_mode` | `mode=REPLACE` is only for enrolments |

Row-level problems are not errors: they come back in the import report with row, column and
severity.

**Runs**

| Status | Code | Meaning |
|---|---|---|
| 422 | `not_ready` | Not enough data to solve (no slots, rooms, enrolments…); `detail` says what to add |
| 429 | `too_many_runs` | The cap on queued + running runs is reached |
| 409 | `not_active` | Only queued or running runs can be cancelled |
| 409 | `not_cancellable` | The run is not executing on this API instance (it runs elsewhere or has just finished) |
| 409 | `run_in_use` | Active, publishing or published runs cannot be deleted |
| 409 | `run_has_children` | Delete the runs derived from it first |
| 409 | `no_plan` | The run has no plan to view or repair |

**Approval, publishing and repair**

| Status | Code | Meaning |
|---|---|---|
| 409 | `not_approvable` | Only `DONE` runs can be approved |
| 409 | `already_approved` | The same person cannot approve twice |
| 409 | `data_changed` | The catalog changed since the solve; start a new run |
| 409 | `not_withdrawable` | Publication already started |
| 409 | `not_publishable` | Retry needs a `DONE` run with enough approvals |
| 400 | `unknown_room`, `room_not_used` | The room to repair does not exist or is not used in that slot |
| 409 | `plan_mismatch` | The stored plan no longer seats every current enrolment |
| 422 | `cannot_reseat` | Not enough spare or reserve seats; `detail` explains |

**Users and portals**

| Status | Code | Meaning |
|---|---|---|
| 409 | `username_taken` | Usernames are unique |
| 400 | `student_roll_required`, `faculty_code_required` | Portal accounts must link to a student or teacher |
| 409 | `cannot_disable_self`, `cannot_demote_self`, `last_admin` | Guards against locking every administrator out |
| 400 | `wrong_password` | The current password is incorrect |
| 403 | `not_a_student`, `not_faculty` | A portal endpoint called by the wrong role |
