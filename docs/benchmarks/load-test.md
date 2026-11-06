# Result-day load test

Result day is the one moment ExamGrid has real traffic: every student opens the seat finder
within minutes of the plan going live. These numbers come from the k6 scripts in
[`loadtest/`](../../loadtest), run against the full Docker stack
(`docker-compose.full.yml`: Nginx → Spring Boot API → Redis, Postgres behind it).

## Setup

| | |
|---|---|
| Machine | one 4 vCPU / 15 GB Linux VM; k6, Nginx, the API, Postgres and Redis all on the same box |
| Stack | images built from this repository; API with defaults (Hikari pool 10, Redis timeout 500 ms) |
| Data | the published demo plan: 1,500 students, 120 exams, 7,590 enrolments, 14 rooms, 20 slots |
| Request | `GET /api/public/seat?roll=<random roll>`, through Nginx on port 8088 |
| Clients | each request carries a random `X-Forwarded-For: 10.x.x.x`, which Nginx trusts from a private address. This simulates students on different networks. Without it, the per-IP rate limiter (60 requests a minute) would throttle k6 itself. |
| Checks | status 200, at least one exam in the body, and no `"name"` field (the public endpoint must not leak names) |

k6 competes with the system under test for the same four cores, so these figures are a
lower bound for a dedicated server.

## Results

| Scenario | Requests | Throughput | Failed | Median | p95 | p99 | Max |
|---|---:|---:|---:|---:|---:|---:|---:|
| **Result day**: ramp to 1,000 students, 0.5–1.5 s think time | 55,429 | 780 req/s | 0.00 % | 1.6 ms | **3.4 ms** | 6.1 ms | 25 ms |
| **Stress**: 200 clients, no think time | 115,976 | 3,859 req/s | 0.00 % | 46 ms | 101 ms | 161 ms | 1.08 s |
| **Stress, Redis stopped**: Postgres fallback | 33,611 | 1,115 req/s | 0.00 % | 117 ms | 564 ms | 988 ms | 2.25 s |

All checks passed in all three runs: every answer had seats and none leaked a student name.
The result-day scenario meets its thresholds (p95 < 250 ms, p99 < 500 ms, < 1 % errors) with
a wide margin. The Redis-down stress run fails the p95 threshold (k6 exit code 99) but
answers every request correctly.

```bash
k6 run -e ROLLS=rolls.txt loadtest/seat-search.js                 # result day
k6 run -e MODE=stress -e ROLLS=rolls.txt loadtest/seat-search.js  # stress
docker compose -f docker-compose.full.yml stop redis               # then stress again
```

### Losing Redis

Redis is a cache in ExamGrid, not the source of truth, so a Redis outage should cost speed,
not correctness. The first measurement of that path found a real problem:

| Redis stopped | Throughput | p95 |
|---|---:|---:|
| Before: each request waited for its Redis calls to time out (rate limiter, published-run pointer, pointer repair, seat key) | 25 req/s | 8.15 s |
| After: `RedisCircuit` skips Redis for 10 s after a failure; timeouts lowered to 500 ms | 1,115 req/s | 564 ms |

With the breaker open, the rate limiter fails open and seat lookups go straight to Postgres
(two indexed queries). The pool of 10 database connections is then the most likely ceiling. After Redis
returned, the next probe closed the breaker, the published-run pointer was rewritten and
lookups re-populated the cache. No restart was needed.

## Rate limiting

`loadtest/rate-limit.js` sends 100 rapid requests from one client address to
`/api/public/status`. The limiter (Redis fixed window, 60 per minute) let the first 60
through and answered the other **40 with `429 Too Many Requests` and a `Retry-After`
header**.

## What this does not show

- Network latency between real students and the server; these requests never leave the VM.
- A multi-instance API: the API is stateless (JWT, Redis), so it scales out behind the proxy,
  but only one instance was measured.
- Authenticated portal traffic (`/api/students/me/exams`), which adds JWT verification
  but follows the same read path.
