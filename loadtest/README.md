# Load tests

[k6](https://k6.io) scripts for the result-day read path (Nginx → API → Redis).

```bash
docker compose -f docker-compose.full.yml up -d      # then publish a plan (UI or API)
docker compose -f docker-compose.full.yml exec -T postgres \
  psql -U examgrid -t -A -c "select roll from student" > loadtest/rolls.txt
k6 run -e BASE_URL=http://localhost:8088 -e ROLLS=rolls.txt loadtest/seat-search.js
k6 run -e BASE_URL=http://localhost:8088 -e MODE=stress -e ROLLS=rolls.txt loadtest/seat-search.js
k6 run -e BASE_URL=http://localhost:8088 loadtest/rate-limit.js
```

`seat-search.js` ramps to 1,000 concurrent students, each looking up a random roll number
every 0.5–1.5 s, and fails if more than 1 % of requests fail or p95 exceeds 250 ms.
`MODE=stress` drops the think time and holds 200 clients for 30 s to find the throughput
ceiling; run it again after `docker compose -f docker-compose.full.yml stop redis` to measure
the Postgres fallback. Each virtual user sends a random private `X-Forwarded-For` so the
per-IP rate limiter sees many students rather than one load generator.
`rate-limit.js` checks that a single client is throttled with `429` and `Retry-After`.

Measured results are in [docs/benchmarks/load-test.md](../docs/benchmarks/load-test.md).
