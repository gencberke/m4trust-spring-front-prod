# Current Project State

Last updated: 2026-07-16

## Phase

Slice 0 is done. Slice 1 authentication implementation is merged into `main`;
completion fixes are on `codex/slice1-completion-fixes`, and the real browser
acceptance pass remains before Slice 1 can be marked done.

## Accepted foundations

- ADR-001 through ADR-007 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- The Spring–AI contract foundation, schema fixtures, validators, AsyncAPI, and
  public OpenAPI foundation exist under `contracts/`.
- The system direction remains Vite/React/TypeScript + Spring Boot modular
  monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ +
  S3-compatible object storage.

## Current repository shape

- `services/core-api`: Java 21 / Spring Boot 4.1 with PostgreSQL JDBC, Flyway,
  Problem Details, correlation IDs, structured logging, readiness/liveness,
  module-cycle protection, and a non-root container image.
- `identity`: register/login/logout/me/CSRF endpoints, Argon2id credentials,
  normalized unique email, PostgreSQL-backed Spring Session, profile-specific
  secure cookies, CSRF enforcement, session fixation protection, and idle plus
  absolute session timeouts.
- `frontend`: generated OpenAPI types, TanStack Query-backed auth state,
  register/login screens, protected routing, session restore, centralized
  session-expiry handling, and logout against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, and PowerShell reset/seed entrypoints.
- Application and contract CI have completed successfully for the Slice 1
  implementation commits.

## Not yet stable or accepted

- Slice 1 real browser acceptance and sensitive-log spot check
- Slice 2 organization/membership plan approval and implementation
- FastAPI AI service skeleton and Mock AI Worker implementation
- Railway service configuration

## Active work

`codex/slice1-completion-fixes` centralizes frontend
`AUTH_SESSION_EXPIRED` handling, records the deferred login-throttling follow-up
in GitHub issue #7, and reconciles Slice 1 documentation. Browser acceptance is
intentionally left to the user.

## Known blockers

No architectural blocker. Docker Engine was unavailable in the current local
session, so the remaining full runtime/browser acceptance could not be run here.

## Next likely capability

Run `docs/plan/ready/01-authentication.md` §7 end to end, complete the sensitive
log spot check, then move the plan to `done/` and record Slice 1 as accepted.
Slice 2 remains in `planning/` until human approval.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
