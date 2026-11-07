# Contributing to ExamGrid

Thank you for helping. This guide gets you from clone to a passing pull request.

## Set up in five minutes

You need Docker, Java 21, Maven 3.9 and Node 22.

```bash
git clone https://github.com/n3h4a/ExamGrid.git && cd ExamGrid
make infra        # Postgres + Redis in Docker
make api          # API on :8080 with demo data and Swagger UI   (terminal 1)
make web          # web app on :5173 with hot reload             (terminal 2)
```

Sign in at <http://localhost:5173> as `admin` / `admin12345`. Run `make` to see every command,
and see [the project bible](PROJECT_OVERVIEW.md) for how the whole system fits together.

## Before you open a pull request

```bash
make test         # everything CI runs: Maven verify (Testcontainers needs Docker) + frontend checks
```

A pull request is ready when:

- **CI is green.** Never skip, disable or weaken a test to get there.
- **Behaviour changes come with tests** at the right level:
  - pure solver logic gets a unit test, plus a jqwik property when an invariant is involved;
  - API behaviour gets an integration test in `backend/src/test/**/*IT.java`;
  - UI behaviour gets a Vitest test next to the component.
- **Docs match the code.** Update the README, `docs/`, or the OpenAPI export
  (`docs/api/openapi.json`, from `/v3/api-docs`) when behaviour or the API changes. If the UI
  changed, regenerate the screenshots with `make screenshots`.
- **Significant design decisions get an ADR.** Copy the format of the files in
  [`docs/adr/`](docs/adr), number it next in sequence, and record the context, the decision,
  the alternatives and the consequences.

## Conventions

| Area | Convention |
|---|---|
| Commits | [Conventional Commits](https://www.conventionalcommits.org): `feat(solver): …`, `fix: …`, `docs: …`, `ci: …`. The subject says what changes for a user or developer |
| Java | Package by feature in the backend. The solver stays free of Spring and I/O. Prefer records and immutable inputs. Constructor injection only |
| SQL | Schema changes only through a new Flyway migration (`V<n>__description.sql`); never edit an applied one. Every query uses bind parameters |
| API | Errors are RFC 9457 problems with a stable `code`. Request DTOs are validated records. New endpoints get `@Operation` summaries for the OpenAPI document |
| TypeScript | Strict mode. Server state goes in TanStack Query, never a global store. API types live in `src/api/types.ts` |
| Formatting | `.editorconfig`: 4 spaces in Java, 2 in TypeScript, YAML and Markdown; LF line endings. `npm run lint` must pass |
| Docs | Short sentences and measured numbers only. If you cannot measure it, do not claim it |

## Repository map

| Path | What lives there |
|---|---|
| `solver/` | The engine: graph colouring, optimisation, rooms, seating, invigilation ([guide](solver/README.md)) |
| `backend/` | Spring Boot API ([guide](backend/README.md)) |
| `frontend/` | React app ([guide](frontend/README.md)) |
| `benchmarks/`, `loadtest/` | JMH and k6 |
| `deploy/`, `render.yaml` | Production configurations |
| `tools/` | Scripts that regenerate the banner and the screenshots |
| `docs/` | Architecture, ADRs, API, data model, security, testing, benchmarks |

## Reporting bugs and security issues

Open an issue using the bug-report template, with steps to reproduce and the `requestId`
from the error message if there is one. **Do not report security vulnerabilities in public
issues**: follow [SECURITY.md](.github/SECURITY.md) instead.

By contributing, you agree that your contributions are licensed under the [MIT licence](LICENSE).
