# Review Request
Task: 14A
Revision: 1
Plan: docs/plan/ready/14a-dispute-and-casework-foundation.md
Phases: P1, P2, P3, P3-fix
Status: IN_PROGRESS
Branch: feature/14a-dispute-casework-foundation
Base: main@dbcad17949b9063b9ef385a858f728d1d0f94536
Plan completion claim: NO

## Phase outcomes
- P1 — DONE — Added Slice 14A casework OpenAPI paths/schemas, Deal casework projection, validator allowlists, README/CHANGELOG, and regenerated `frontend/src/generated/core-api.d.ts`; `python3 contracts/scripts/validate_contracts.py` PASS.
- P2 — DONE — Added `V22__dispute_casework_foundation.sql`, casework aggregates/repositories, `CaseworkSourcePorts` with deal/fulfillment adapters, `ModuleArchitectureTest` updates; compile PASS and `ModuleArchitectureTest` PASS. `DisputeCaseworkMigrationIntegrationTest` added but requires Docker/Testcontainers locally.
- P3 — DONE — Added `DisputeController`, `DisputeService`, DTOs, `DisputeQuery`, `CaseworkExceptions`/`CaseworkExceptionHandler`, `RequestedOperation` dispute ops, deal/fulfillment projection adapters (`CaseworkLegalEntityNameAdapter`, `CaseworkVideoAnalysisProjectionAdapter`), repository pagination/bulk insert, and `DisputeIntegrationTest`; `mvn compile` PASS and `ModuleArchitectureTest` PASS. `DisputeIntegrationTest` requires Docker/Testcontainers locally.
- P3-fix — DONE — Addressed review findings: V22 REJECTED snapshot lifecycle, actor tenant FK integrity, cross-evidence video trigger, monotonic version/status mutation guard, video job lock-all-then-pin adapter, open auth 404 vs 403 split, semantic validation codes, idempotency-after-lock order, OpenAPI `DisputeOpenForbidden` clarification, expanded migration/integration tests.
- P4 — NOT_STARTED
- P5 — NOT_STARTED
- P6 — NOT_STARTED
- P7 — NOT_STARTED
- P8 — NOT_STARTED

## Validation
- `python3 contracts/scripts/validate_contracts.py` — PASS
- `npm run generate:api` — PASS
- `./mvnw -Dtest=ModuleArchitectureTest test` — PASS
- `./mvnw -Dtest=DisputeIntegrationTest test` — NOT_RUN (Docker unavailable in agent environment)
- `./mvnw -Dtest=DisputeCaseworkMigrationIntegrationTest test` — NOT_RUN (Docker unavailable in agent environment)
- AI/messaging/payment contract byte-identity — NOT_RUN (pending P8)

## Decisions needed
- None

## Deviation or risk
- None
