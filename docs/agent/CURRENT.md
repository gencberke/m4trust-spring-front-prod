# Current Project State

Last updated: 2026-07-18

## Phase

Slice 0 platform foundation and Slice 1 authentication are merged into `main`
and accepted under `docs/plan/done/`.

Slice 2 organization and membership is accepted under `docs/plan/done/`.
Contract, backend, frontend, and the real two-browser end-to-end isolation flow
passed; the accepted branch is merged into `main`.

Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
Contract, backend, frontend, automated validation, and the real browser
stale-version and two-profile participant-isolation flows passed.

Slice 4 Deal invitations and cross-entity participation is accepted under
`docs/plan/done/`. Contract design review, expand/switch migrations, invitation
state machine, reusable idempotency, backend/frontend validation, and the real
two-browser acceptance flow passed.

Slice 5 Deal parties and activation readiness is accepted under
`docs/plan/done/`. Buyer/seller assignment, participant-only validation,
explicit initiator authorization, backend-derived party action availability,
optimistic stale-version recovery, and the real two-browser acceptance flow passed.
Deal remains DRAFT in this slice; no activation endpoint or action exists.

## Accepted foundations

- ADR-001 through ADR-009 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- Slice 1 authentication is accepted under `docs/plan/done/`.
- Slice 2 organization and membership is accepted under `docs/plan/done/`.
- Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
- Slice 4 Deal invitations and cross-entity participation is accepted under
  `docs/plan/done/`.
- Slice 5 Deal parties and activation readiness is accepted under
  `docs/plan/done/`.
- The Spring–AI contract foundation, schema fixtures, validators, AsyncAPI, and
  public OpenAPI foundation exist under `contracts/`.
- The system direction remains Vite/React/TypeScript + Spring Boot modular
  monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ +
  S3-compatible object storage.

## Current repository shape

- `services/core-api`: Java 21 / Spring Boot 4.1 with PostgreSQL JDBC, Flyway,
  identity/session authentication, tenant provisioning, legal entities,
  memberships, Deals, cross-tenant participant visibility, email-based Deal
  invitations, reusable HTTP idempotency, state/optimistic-lock enforcement,
  buyer/seller participant integrity and explicit initiator-only party
  assignment,
  append-only audit, centralized legal-entity authorization, Problem Details,
  structured logging, health probes, and module-cycle checks.
- `frontend`: generated OpenAPI types, TanStack Query-backed authentication and
  organization state, protected routing, legal-entity create/list/switch/detail
  and member views, Deal create/list/detail/update/cancel views, incoming Deal
  invitations, acceptance entity selection, participant and pending-invitation
  projections, buyer/seller role visibility and backend-derived party-management
  availability, filtering, sorting and pagination, stale-version recovery,
  versioned per-tab selection, centralized scoped request headers,
  session-expiry handling, and logout against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, and PowerShell reset/seed entrypoints.
- `contracts/`: the reviewed Slice 5 public OpenAPI surface and existing
  Spring–AI contract foundations.

## Validation state

- Contract validation passes for the committed Slice 5 OpenAPI.
- Core API `mvn verify` passes against Testcontainers PostgreSQL.
- Frontend `npm run typecheck` and production `npm run build` pass.
- Slice 4 invitation/participation regression and Slice 5 real two-browser
  acceptance passed on 2026-07-18.

## Not yet stable or accepted

- FastAPI AI service skeleton and Mock AI Worker implementation
- Railway service configuration

## Active work

Slice 6 document upload is the next approved implementation plan under
`docs/plan/ready/`.

## Known blockers

No architectural, implementation, or acceptance blocker.

## Next likely capability

Implement Slice 6 document upload.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
