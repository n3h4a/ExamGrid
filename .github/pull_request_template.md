## What changes

<!-- One or two sentences on the behaviour that changes, for a user or a developer. -->

## Why

<!-- The problem this solves. Link the issue: "Closes #…". -->

## How it was verified

- [ ] `make test` passes locally (Maven verify with Testcontainers, frontend checks)
- [ ] New or changed behaviour has tests at the right level (solver unit/property, backend IT, frontend Vitest)
- [ ] Manually checked in the running stack (`make up`), if the UI or API changed

## Docs

- [ ] README / `docs/` updated where behaviour changed
- [ ] `docs/api/openapi.json` re-exported if the API changed
- [ ] Screenshots regenerated (`make screenshots`) if the UI changed
- [ ] ADR added for a significant design decision
