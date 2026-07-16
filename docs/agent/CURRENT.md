# Current Project State

Last updated: 2026-07-16

## Phase

Slice 0 platform foundation and Slice 1 authentication are merged into `main`
and accepted under `docs/plan/done/`.

Slice 2 organization and membership contract, backend, and frontend
implementation is complete on `codex/slice2-organization-membership`. Human
two-browser end-to-end acceptance is still pending, so Slice 2 remains
`ready` rather than accepted.

## Accepted foundations

- ADR-001 through ADR-007 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- Slice 1 authentication is accepted under `docs/plan/done/`.
- The Spring–AI contract foundation, schema fixtures, validators, AsyncAPI, and
  public OpenAPI foundation exist under `contracts/`.
- The system direction remains Vite/React/TypeScript + Spring Boot modular
  monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ +
  S3-compatible object storage.

## Current repository shape

- `services/core-api`: Java 21 / Spring Boot 4.1 with PostgreSQL JDBC, Flyway,
  identity/session authentication, tenant provisioning, legal entities,
  memberships, append-only audit, centralized legal-entity authorization,
  Problem Details, structured logging, health probes, and module-cycle checks.
- `frontend`: generated OpenAPI types, TanStack Query-backed authentication and
  organization state, protected routing, legal-entity create/list/switch/detail
  and member views, versioned per-tab selection, centralized scoped request
  headers, session-expiry handling, and logout against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, and PowerShell reset/seed entrypoints.
- `contracts/`: the reviewed Slice 2 public OpenAPI surface and existing
  Spring–AI contract foundations.

## Validation state

- Contract validation passes for the committed Slice 2 OpenAPI.
- Core API `mvn verify` passes with 21 tests against Testcontainers PostgreSQL.
- Frontend `npm run typecheck` and production `npm run build` pass.
- Human two-browser isolation and browser acceptance remain pending.

## Not yet stable or accepted

- Slice 2 until the manual two-browser acceptance flow passes
- FastAPI AI service skeleton and Mock AI Worker implementation
- Railway service configuration

## Active work

`codex/slice2-organization-membership` contains the complete Slice 2 contract,
persistence, Core API, frontend workspace, and handoff documentation. The plan
remains at `docs/plan/ready/02-organization-and-membership.md`.

## Known blockers

No architectural or implementation blocker. Slice 2 awaits human browser
acceptance only.

## Next likely capability

Run the documented Slice 2 two-profile browser acceptance flow. If it passes,
record the evidence, move Plan 02 to `done/`, and merge the accepted feature
branch before planning Slice 3.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
