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

The repository currently contains architectural decisions and shared AI contracts. Application and runtime skeletons have not yet been established.

Not yet present as stable foundations:

- React application skeleton
- Spring Core API skeleton
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

Choose and implement the smallest runnable platform increment. The planner should inspect the latest repository state before deciding whether the first task starts with repository layout, Spring Core API, frontend, or local runtime orchestration.

## Update rule

Update this file only when accepted project state materially changes. Keep it short and factual. Do not use it as a backlog, architecture document, or conversation transcript.
