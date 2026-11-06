# ExamGrid documentation

Start with the [project README](../README.md) for the overview. The documents below go one
level deeper, and each is written against the code as it stands.

| Read this | To learn |
|---|---|
| [Architecture](architecture.md) | Containers, backend layers, request lifecycle, authentication flow, frontend, solver pipeline, run lifecycle, publishing, deployment |
| [Data model](database.md) | Tables and constraints, ER diagram, indexing strategy, concurrency control, Redis keys, file storage |
| [Algorithms](algorithms.md) | How the engine plans a season: worked example, each algorithm's rationale, complexity and alternatives |
| [Technology choices](technology.md) | Why each technology was chosen, what it costs, and what was considered instead |
| [Decision records](adr) | Why each major design choice was made, with the alternatives considered |
| [API reference](api/README.md) | Conventions, a walkthrough of one season with real requests and responses, every endpoint, every error code; the full [OpenAPI 3.1 spec](api/openapi.json) |
| [Imports](imports.md) | Accepted file formats, columns and validation rules |
| [Security](security.md) | Trust boundaries, authentication, authorisation, uploads, headers, OWASP Top 10 mapping, known limitations |
| [Testing](testing.md) | Test suites, property tests, coverage, how to run them |
| [Benchmarks](benchmarks/README.md) | Solver scaling and per-stage JMH measurements |
| [Load test](benchmarks/load-test.md) | Result-day HTTP performance, including a Redis outage |
| [Deployment](deployment.md) | Single VM with Caddy, Render blueprint, configuration, operations |
| [Specification](spec/PROJECT_SPEC.md) | The product specification ExamGrid was built against |
| [Roadmap](ROADMAP.md) | The ten milestones and what each delivered |

Component guides: [solver](../solver/README.md) · [backend](../backend/README.md) ·
[frontend](../frontend/README.md) · [load tests](../loadtest/README.md) ·
[sample data](../samples/README.md) · [asset tools](../tools/README.md).

## Decision records

| # | Decision |
|---|---|
| [0001](adr/0001-standalone-solver-module.md) | The solver is a standalone Java module |
| [0002](adr/0002-colouring-strategy.md) | DSatur with TabuCol repair and certified infeasibility |
| [0003](adr/0003-penalty-model-and-optimisers.md) | Pairwise penalty model, simulated annealing by default, tabu search as alternative |
| [0004](adr/0004-rooms-seating-invigilation.md) | Rooms by min-cost max-flow, seating by colour classes then CSP, invigilation by min-heap |
| [0005](adr/0005-backend-architecture.md) | Backend architecture: package by feature, stateless JWT, problem details, snapshot runs |
| [0006](adr/0006-import-pipeline.md) | All-or-nothing imports with dry runs, archived originals and JDBC batch upserts |
| [0007](adr/0007-async-runs-and-sse.md) | Runs as a job table with a bounded worker pool and SSE progress |
| [0008](adr/0008-publishing-and-result-day.md) | Two-person publishing, precomputed documents, Redis seat index with Postgres fallback |
| [0009](adr/0009-containers-and-single-origin.md) | Two images, one origin behind Nginx |
