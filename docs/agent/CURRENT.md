# Current Project State

Last updated: 2026-07-16

## Phase

Slice 0 platform foundation and Slice 1 authentication are merged into `main`
and accepted under `docs/plan/done/`.

Slice 2 organization and membership is accepted under `docs/plan/done/`.
Contract, backend, frontend, and the real two-browser end-to-end isolation flow
passed; the accepted branch is merged into `main`.

Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
Contract, backend, frontend, automated validation, and the real browser
stale-version and two-profile participant-isolation flows passed.

## Accepted foundations

- ADR-001 through ADR-007 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- Slice 1 authentication is accepted under `docs/plan/done/`.
- Slice 2 organization and membership is accepted under `docs/plan/done/`.
- Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
- The Spring–AI contract foundation, schema fixtures, validators, AsyncAPI, and
  public OpenAPI foundation exist under `contracts/`.
- The system direction remains Vite/React/TypeScript + Spring Boot modular
  monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ +
  S3-compatible object storage.

## Current repository shape

- `services/core-api`: Java 21 / Spring Boot 4.1 with PostgreSQL JDBC, Flyway,
  identity/session authentication, tenant provisioning, legal entities,
  memberships, Deals, participant-scoped access, state/optimistic-lock
  enforcement, append-only audit, centralized legal-entity authorization,
  Problem Details, structured logging, health probes, and module-cycle checks.
- `frontend`: generated OpenAPI types, TanStack Query-backed authentication and
  organization state, protected routing, legal-entity create/list/switch/detail
  and member views, Deal create/list/detail/update/cancel views, filtering,
  sorting and pagination, stale-version recovery, versioned per-tab selection,
  centralized scoped request headers, session-expiry handling, and logout
  against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, and PowerShell reset/seed entrypoints.
- `contracts/`: the reviewed Slice 3 public OpenAPI surface and existing
  Spring–AI contract foundations.

## Validation state

- Contract validation passes for the committed Slice 3 OpenAPI.
- Core API `mvn verify` passes with 32 tests against Testcontainers PostgreSQL.
- Frontend `npm run typecheck` and production `npm run build` pass.
- Slice 3 human browser acceptance passed on 2026-07-16.

## Not yet stable or accepted

- FastAPI AI service skeleton and Mock AI Worker implementation
- Railway service configuration

## Active work

`codex/slice3-deal-creation-listing` contains the complete Slice 3 contract,
Flyway V5 persistence, Core API and frontend Deal workspace. The accepted plan
is archived at `docs/plan/done/03-deal-creation-and-listing.md`; the branch is
ready for merge.

## Known blockers

No architectural, implementation, or acceptance blocker.

## Next likely capability

Merge the accepted Slice 3 branch, then review and approve the next slice plan.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
