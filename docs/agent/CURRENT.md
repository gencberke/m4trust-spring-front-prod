# Current Project State

Last updated: 2026-07-15

## Phase

Pre-implementation foundation is complete enough to begin coding through small vertical increments.

## Accepted foundations

- ADR-001 through ADR-007 are accepted; documentation drift between ADR-002 examples and the schemas was reconciled (contracts changelog 1.0.2; envelope `transactionId` documented as the Deal id).
- The Spring–AI contract foundation exists under `contracts/`.
- AI schemas, canonical fixtures, AsyncAPI/OpenAPI references, validation tooling, and contract CI are present.
- `architecture-decisions/ADR-INDEX.md` is a layered router (cheat sheet, trigger dictionary, task recipes, escalation, glossary); `architecture-decisions/FORBIDDEN.md` consolidates all ADR prohibitions.
- Slice plans 00–03 (platform foundation, authentication, organization/membership, deal creation/listing) exist under `docs/plan/planning/` and move to `docs/plan/ready/` after human approval.
- The system direction is Vite/React/TypeScript + Spring Boot modular monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ + S3-compatible object storage.

## Current repository shape

The repository contains architectural decisions, shared AI contracts, and a first runnable Spring Core API skeleton under `services/core-api` (Java 21, Spring Boot 4.1; web + validation + actuator only; `/api/v1/meta`, Actuator health with liveness/readiness probes, Problem Details validation with correlation ID propagation via `X-Correlation-ID`, multi-stage non-root Dockerfile). No business capability, database, security, or messaging yet.

Not yet present as stable foundations:

- React application skeleton
- FastAPI AI service skeleton
- Mock AI Worker implementation
- local Docker Compose runtime
- PostgreSQL/Flyway application foundation
- RabbitMQ and object-storage runtime wiring
- `contracts/openapi/core-api-v1.yaml`
- Railway service configuration

## Active work

Slice plans 00–03 are drafted and await human review in `docs/plan/planning/`. The agent workflow foundation (root guidance, layered ADR router, FORBIDDEN list, planner–implementer protocol, this state file) is complete.

## Known blockers

None. Implementation details are expected to evolve during coding as long as accepted system boundaries remain intact.

## Next likely capability

The Spring Core API skeleton is in place. The next runnable increments complete Slice 0 per `docs/plan/planning/00-platform-foundation.md` (PostgreSQL/Flyway foundation, local Docker Compose runtime, frontend skeleton, `core-api-v1.yaml` skeleton) before the first business slice (`01-authentication`). The planner should inspect the latest repository state and the approved plan in `docs/plan/ready/` before deciding.

## Update rule

Update this file only when accepted project state materially changes. Keep it short and factual. Do not use it as a backlog, architecture document, or conversation transcript.
