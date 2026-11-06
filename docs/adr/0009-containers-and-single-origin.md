# ADR-0009: Two images, one origin behind Nginx

- **Status:** accepted
- **Date:** 2026-11-06

## Context

The product is a React SPA plus a Spring Boot API with PostgreSQL and Redis. It must run
anywhere Docker runs, from a single college VM to a PaaS. SSE progress must survive the proxy,
and the browser should not need CORS or cookies.

## Decision

- **Two images.**
  - `examgrid-api`: a multi-stage Maven build, extracted into Spring Boot layers (dependencies,
    loader, snapshots, application) on `eclipse-temurin:21-jre-alpine`. It runs as a non-root
    user, sizes the heap from the container limit (`MaxRAMPercentage=75`), keeps uploads and PDFs
    on a `/data` volume, and has a healthcheck on `/actuator/health/readiness`.
  - `examgrid-web`: Vite builds the static bundle, which `nginx:1.27-alpine` serves. Nginx also
    reverse-proxies `/api`, `/v3/api-docs` and `/swagger-ui`, with the upstream from
    `API_UPSTREAM` (rendered with envsubst at start-up).
- **One origin.** The browser only talks to Nginx, so there is no CORS, the bearer token never
  crosses origins, and the CSP can be `connect-src 'self'`.
- **SSE-aware proxying.** The `/api/schedule-runs/{id}/events` location turns off buffering and
  caching, keeps HTTP/1.1 upstream connections, and allows a one-hour read timeout. Measured
  through Nginx, progress events arrive every ~150 ms rather than in one burst.
- **Headers by location.** Nginx discards server-level `add_header` in any location that sets
  its own, so the SPA locations include one snippet (CSP, `X-Frame-Options`, `nosniff`,
  `Referrer-Policy`, `Permissions-Policy`, and a `Cache-Control` from a map). Fingerprinted
  `/assets/` are immutable for a year, and the HTML shell is `no-cache`. Proxied API responses
  keep the backend's own stricter headers (`default-src 'none'`).
- **Request ids** pass through (`X-Request-Id`, or Nginx's `$request_id`), so a log line in Nginx
  can be followed into the API.

## Consequences

- `docker compose -f docker-compose.full.yml up` starts the complete product on port 8088.
- Swapping the proxy (Caddy, a PaaS router) only requires the same SSE settings.
- Database and Redis stay private on the Compose network; only the web container publishes a
  port.
