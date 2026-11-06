# Sample data

A small, entirely fictional university for trying the import flow by hand: 24 courses, 120
students, 4 rooms and 12 faculty members.

Import in this order, because enrolments and faculty refer to courses, and faculty
unavailability refers to exam slots:

1. `courses.csv`
2. `rooms.csv`
3. Generate slots, e.g. 2026-12-07 to 2026-12-12, MORNING + AFTERNOON, skipping Sunday
4. `enrolments.csv`
5. `faculty.csv` (F003 is away on 2026-12-09)

```bash
TOKEN=$(curl -s -XPOST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .accessToken)
curl -s -XPOST "localhost:8080/api/imports/courses?dryRun=false" -H "Authorization: Bearer $TOKEN" -F file=@samples/courses.csv
curl -s -XPOST "localhost:8080/api/imports/rooms?dryRun=false" -H "Authorization: Bearer $TOKEN" -F file=@samples/rooms.csv
curl -s -XPOST localhost:8080/api/slots/generate -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"startDate":"2026-12-07","endDate":"2026-12-12","sessions":["MORNING","AFTERNOON"],"skipWeekdays":["SUNDAY"]}'
curl -s -XPOST "localhost:8080/api/imports/enrolments?dryRun=false" -H "Authorization: Bearer $TOKEN" -F file=@samples/enrolments.csv
curl -s -XPOST "localhost:8080/api/imports/faculty?dryRun=false" -H "Authorization: Bearer $TOKEN" -F file=@samples/faculty.csv
```
