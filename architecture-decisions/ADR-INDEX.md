# ADR Reading Index

Use this file to choose the minimum architectural context required for a task. It does not override or replace the accepted ADR documents.

## Fast routing

| Task area | Read first | Focus headings |
| --- | --- | --- |
| New service, database ownership, object storage, or cross-service boundary | `ADR-001-System-Boundaries-and-Data-Ownership.md` | Public API boundary, data ownership, database isolation, object storage, Spring–AI communication, outbox/inbox |
| AI job, RabbitMQ event, schema, worker, result, failure, or cancellation | `ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md`, then relevant files under `contracts/` | Event names, schema versions, validation ownership, compatibility, duplicate safety, failure policy |
| Deal model, aggregate, module boundary, state transition, business validation | `ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md` | Modular monolith, Spring modules, aggregate roots, lifecycle dimensions, immutable versions, transaction boundaries |
| Slice planning, completion criteria, testing, Mock AI Worker usage | `ADR-004-Vertical-Slice-Delivery-and-Acceptance-Testing.md` | Vertical slices, frontend acceptance, minimum tests, manual browser testing, AI slice flow |
| Login, registration, session, cookie, CSRF, CORS, password, authorization | `ADR-005-Authentication-and-Security-Baseline.md` | Opaque session, Spring Session JDBC, cookie policy, timeouts, CSRF/CORS, legal-entity context, application-layer authorization |
| Controller, DTO, frontend API client, errors, pagination, concurrency, idempotency | `ADR-006-Public-API-and-Error-Conventions.md` | API naming, Problem Details, HTTP status policy, `expectedVersion`, `Idempotency-Key`, correlation, async responses, OpenAPI |
| Docker, Compose, Railway, environment, migration, health, logging, backup | `ADR-007-Deployment-and-Runtime-Environments.md` | Runtime topology, configuration, secrets, Flyway, deployment pipeline, liveness/readiness, structured logging, recovery |

All files are under `architecture-decisions/` unless another path is shown.

## Reading rules

- Planner-reviewers may read multiple ADRs when shaping a cross-cutting task.
- Implementers should start with the relevant ADR references and headings selected by the planner.
- Expand to nearby code or additional documentation only when implementation or validation requires it.
- Do not paste full ADR text into implementer prompts.
- Read the complete ADR when changing its core policy, not merely the heading named here.
- When an ADR and this index disagree, the ADR wins.
- Surface new cross-cutting decisions to the planner before encoding them as accidental conventions.
