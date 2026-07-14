# Current Project State

Last updated: 2026-07-14

## Phase

Pre-implementation foundation is complete enough to begin coding through small vertical increments.

## Accepted foundations

- ADR-001 through ADR-007 are accepted.
- The Spring–AI contract foundation exists under `contracts/`.
- AI schemas, canonical fixtures, AsyncAPI/OpenAPI references, validation tooling, and contract CI are present.
- The system direction is React + Spring Boot modular monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ + S3-compatible object storage.

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

Agent workflow foundation:

- root agent guidance,
- ADR reading router,
- compact planner–reviewer/implementer protocol,
- compact continuity state.

## Known blockers

None. Implementation details are expected to evolve during coding as long as accepted system boundaries remain intact.

## Next likely capability

The Spring Core API skeleton is in place. The next runnable increment can extend Slice 0 (PostgreSQL/Flyway foundation with DB-backed readiness, correlation ID, public error format, local Docker Compose runtime, or the frontend skeleton) before starting the first business vertical slice (Identity and authentication). The planner should inspect the latest repository state before deciding.

## Update rule

Update this file only when accepted project state materially changes. Keep it short and factual. Do not use it as a backlog, architecture document, or conversation transcript.
