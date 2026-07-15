# Current Project State

Last updated: 2026-07-15

## Phase

Slice 0 platform code and configuration are assembled on a feature branch. The execution and browser acceptance phase remains before Slice 0 can be marked done.

## Accepted foundations

- ADR-001 through ADR-007 are accepted; documentation drift between ADR-002 examples and the schemas was reconciled (contracts changelog 1.0.2; envelope `transactionId` documented as the Deal id).
- The Spring–AI contract foundation exists under `contracts/`.
- AI schemas, canonical fixtures, AsyncAPI/OpenAPI references, validation tooling, and contract CI are present.
- `architecture-decisions/ADR-INDEX.md` is a layered router; `architecture-decisions/FORBIDDEN.md` consolidates ADR prohibitions.
- Slice 0 (platform foundation) is human-approved under `docs/plan/ready/`; Slice plans 01–03 remain under `docs/plan/planning/`.
- The system direction is Vite/React/TypeScript + Spring Boot modular monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ + S3-compatible object storage.

## Current repository shape

The repository now contains the Slice 0 implementation foundations:

- `services/core-api`: Java 21 / Spring Boot 4.1, PostgreSQL JDBC + Flyway baseline, database-aware readiness, process-only liveness, Problem Details and correlation infrastructure, ArchUnit cycle protection, structured JSON logging, and a non-root Dockerfile. Slice 0 exposes no public application endpoint.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services with health checks and persistent volumes, plus PowerShell reset/seed entry points.
- `contracts/openapi/core-api-v1.yaml`: the reviewed OpenAPI 3.1 public contract skeleton with an empty path set and reusable error schemas.
- `frontend/`: Vite/React/TypeScript with Router, TanStack Query, real Actuator readiness loading/healthy/error states, environment-driven dev proxy, and OpenAPI type-generation wiring.
- `tools/mock-ai-worker/`: README-only placeholder; no worker implementation.
- Application build CI is configured for the Core API and frontend alongside contract validation, but has not yet been executed.

Not yet present as stable foundations:

- FastAPI AI service skeleton
- Mock AI Worker implementation
- Spring Session/security and all business capabilities
- package lock and generated frontend contract types
- successful build/runtime/browser acceptance evidence for Slice 0
- Railway service configuration

## Active work

Slice 0 code/configuration implementation is complete on `codex/slice0-local-infrastructure`. Package installation, generated types, contract validation, Spring/frontend builds, live Compose/Spring execution, browser healthy/error checks, reset reproducibility, and CI execution remain intentionally deferred to the next execution phase.

## Known blockers

No architectural blocker. Docker Engine was unavailable during the earlier local infrastructure check, so live container health remains unverified.

## Next likely capability

Run the separate Slice 0 execution phase: install frontend dependencies and commit the lock/generated types, switch CI to `npm ci`, run contract/Maven/frontend validation, start the local stack, exercise readiness healthy/error states in a real browser, verify reset reproducibility, and only then move Slice 0 to done. Slice 1 authentication must not begin before that acceptance gate.

## Update rule

Update this file only when accepted project state materially changes. Keep it short and factual. Do not use it as a backlog, architecture document, or conversation transcript.
