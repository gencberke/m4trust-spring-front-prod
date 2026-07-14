# ADR Reading Index

Use this file to choose the minimum architectural context required for a task. It does not override or replace the accepted ADR documents.

## Fast routing

| Task area | Read first | Focus headings |
| --- | --- | --- |
| New service, database ownership, object storage, or cross-service boundary | ADR-001 | Public API boundary, data ownership, database isolation, object storage, Spring–AI communication, outbox/inbox |
| AI job, RabbitMQ event, schema, worker, result, failure, or cancellation | ADR-002, then relevant files under `contracts/` | Event names, schema versions, validation ownership, compatibility, duplicate safety, failure policy |
| Deal model, aggregate, module boundary, state transition, business validation | ADR-003 | Modular monolith, Spring modules, aggregate roots, lifecycle dimensions, immutable versions, transaction boundaries |
| Slice planning, completion criteria, testing, Mock AI Worker usage | ADR-004 | Vertical slices, frontend acceptance, minimum tests, manual browser testing, AI slice flow |
| Login, registration, session, cookie, CSRF, CORS, password, authorization | ADR-005 | Opaque session, Spring Session JDBC, cookie policy, timeouts, CSRF/CORS, legal-entity context, application-layer authorization |
| Controller, DTO, frontend API client, errors, pagination, concurrency, idempotency | ADR-006 | API naming, Problem Details, HTTP status policy, `expectedVersion`, `Idempotency-Key`, correlation, async responses, OpenAPI |
| Docker, Compose, Railway, environment, migration, health, logging, backup | ADR-007 | Runtime topology, configuration, secrets, Flyway, deployment pipeline, liveness/readiness, structured logging, recovery |

## ADR summaries

### ADR-001 — System Boundaries and Data Ownership

Defines which runtime component owns which data and behavior. Read it when a task crosses Spring, FastAPI, PostgreSQL, RabbitMQ, or object storage boundaries.

File: `architecture-decisions/ADR-001-System-Boundaries-and-Data-Ownership.md`

### ADR-002 — Spring–AI Contract and Compatibility Policy

Defines the asynchronous Spring–AI contract, versioning policy, validation responsibilities, event compatibility, and failure rules. Pair it with the concrete AsyncAPI, OpenAPI, JSON Schema, and fixtures under `contracts/`.

File: `architecture-decisions/ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md`

### ADR-003 — Core Domain Model and Deal Lifecycle

Defines `Deal` as the primary business aggregate, the modular-monolith boundaries, aggregate roots, lifecycle dimensions, immutable business versions, and transaction rules.

File: `architecture-decisions/ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md`

### ADR-004 — Vertical Slice Delivery and Acceptance Testing

Defines how work is sliced, implemented, validated, and accepted. The decisive test is the real frontend flow; broad code-level test suites are not the goal.

File: `architecture-decisions/ADR-004-Vertical-Slice-Delivery-and-Acceptance-Testing.md`

### ADR-005 — Authentication and Security Baseline

Defines browser authentication through a server-side opaque session, PostgreSQL-backed Spring Session, cookie and CSRF policy, password rules, timeouts, and authorization context.

File: `architecture-decisions/ADR-005-Authentication-and-Security-Baseline.md`

### ADR-006 — Public API and Error Conventions

Defines `/api/v1`, resource and JSON naming, stable public DTOs, RFC 9457 Problem Details, HTTP status semantics, pagination, `expectedVersion`, idempotency, correlation IDs, async responses, and committed OpenAPI ownership.

File: `architecture-decisions/ADR-006-Public-API-and-Error-Conventions.md`

### ADR-007 — Deployment and Runtime Environments

Defines Railway as the initial staging/production platform, same-origin web edge, private services, OCI containers, environment-based configuration, Flyway, health, logging, recovery, and deployment controls.

File: `architecture-decisions/ADR-007-Deployment-and-Runtime-Environments.md`

## Reading rules

- Planner-reviewers may read multiple ADRs when shaping a cross-cutting task.
- Implementers should normally receive only the relevant ADR references and headings.
- Do not paste full ADR text into implementer prompts.
- Read the complete ADR when changing its core policy, not merely the heading named here.
- When an ADR and a summary disagree, the ADR wins.
- When implementation reveals a new cross-cutting decision, surface it to the planner before encoding it as an accidental convention.
