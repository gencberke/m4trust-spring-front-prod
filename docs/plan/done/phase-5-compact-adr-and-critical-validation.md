# Phase 5 — Compact ADR Authority and Critical Validation Spine

Status: Accepted (2026-07-28)
Base: `main@06d304ce6220da8959e69896e8a7926dd5bed029`
Implementation branch: `codex/phase-5-compact-governance-tests`
Merged: `main@13d8f0a` (PR #53); all six CI checks (core-api, frontend,
local-tools, validate-contracts, container-images, hygiene) passed.

## 1. Purpose and observable outcome

Phase 5 replaces broad, repetitive governance and test coverage with a small,
risk-based validation spine.

The accepted outcome is:

- five concise, topic-based ADRs and one short authority index;
- no numbered ADR files and no standalone `FORBIDDEN.md`;
- only critical workflow, invariant, contract, migration, architecture,
  security and complex business-rule tests remain;
- deleted tests are removed rather than moved to a slow or nightly profile;
- no coverage-percentage or test-count gate;
- warm, dependency-ready repository validation aims for under three minutes
  and must remain at or below five minutes for acceptance;
- no runtime, public API, persistence or user-visible behavior change.

The baseline contains 22 numbered ADRs, roughly 10,000 ADR lines, 90 backend
test classes with 434 cases, 27 Spring contexts, 37 Testcontainers users,
10 frontend test files with 54 cases, and Python tool suites that are not part
of CI. Previous warm Maven evidence is approximately 4:27, while normal
developer runs can exceed ten minutes.

## 2. Scope and authority replacement

Create five English ADRs of approximately 100–200 lines:

| ADR | Durable responsibility |
| --- | --- |
| `ADR-ENGINEERING.md` | Authority hierarchy, contract-first development, focused plans, risk-based testing and validation timing |
| `ADR-FRONTEND.md` | React/TypeScript boundaries, generated contracts, same-origin Core access, session/CSRF behavior and frontend tests |
| `ADR-BACKEND.md` | Module ownership, API/domain/infra boundaries, authorization, transactions, audit, idempotency and concurrency |
| `ADR-DEPLOYMENT.md` | Environments, secrets, network exposure, release identity, migrations, health and recovery |
| `ADR-AI-INTEGRATION.md` | Spring–FastAPI ownership, asynchronous contracts, duplicate-safe delivery, object references and AI-owner scope |

Each replacement ADR has status `Proposed — pending independent acceptance`
and contains only context, decisions, non-negotiables, consequences and change
triggers. Feature chronology and detailed acceptance history are not copied.

The replacement authority order is:

1. `contracts/**` — exact wire truth.
2. The five active ADRs — durable principles.
3. `docs/plan/CURRENT.md` — accepted capabilities and limitations.
4. `docs/history/**` — rationale only and never normative.

Authentication, authorization and tenant isolation belong in Backend. Browser
session, CSRF and route behavior belong in Frontend. Secrets, network,
environment, release and recovery rules belong in Deployment. AI provider,
model, prompt, worker internals and AI deployment remain outside this
repository's authority.

Delete every `ADR-NNN-*.md` and `FORBIDDEN.md` without copying them into an
archive; Git history is the archive. Historical ADR identifiers may remain as
plaintext in historical plans and frozen migrations. Broken links to deleted
ADR files become plaintext. Frozen Flyway SQL is not edited for reference
cleanup.

Old decision families consolidate as follows:

- ADR-001/002/019 → Backend, Frontend and AI Integration;
- ADR-003/005/006/008–015 → Backend principles, while exact feature behavior
  remains in contracts and CURRENT;
- ADR-004/021 → Engineering;
- ADR-007/016/020/022 → Deployment;
- ADR-017/018 deferrals → CURRENT and Deployment.

`docs/plan/CURRENT.md` is not changed by the implementer.

## 3. Critical validation spine

Use one reusable PostgreSQL Testcontainer per Maven JVM and reuse the Spring
context wherever bean configuration permits. Tests use the existing local/test
lifecycle profile, deterministic controllable fakes only at external
boundaries, unique identities, deterministic reset behavior, no method
ordering, and no hidden cross-test scenario state.

Five public-boundary workflows remain:

1. Identity and organization: registration, login/session/me and legal-entity
   membership, plus one invalid entity or tenant access case.
2. Deal commitment: deal creation, cross-tenant invitation/acceptance, party
   assignment, immutable package approvals and `ACTIVE`, plus one participant
   authorization denial.
3. Document analysis and review: upload/finalize, extraction request, one
   outgoing contract event, one completed result, human acceptance and an
   immutable `RuleSetVersion`, plus participant/outsider denial and one
   duplicate or late-result invariant.
4. Funding and fulfillment: funding through query-first reconciliation,
   fulfillment start, evidence finalize and buyer acceptance, plus wrong-actor
   denial and one no-second-dispatch replay.
5. Dispute and settlement: active dispute blocks release, withdrawal reopens
   release, reconciliation completes the deal, plus wrong-actor denial and one
   single-operation concurrency invariant.

Retain or consolidate only these generic proofs:

- append-only and transaction-coupled audit;
- HTTP idempotency, outbox rollback and inbox duplicate suppression;
- the complete Flyway chain on empty PostgreSQL and a clean rerun;
- runtime OpenAPI drift, stable error inventory and private contract metadata;
- top-level module cycles, domain-to-api/infra isolation, repository ownership,
  and one deliberate negative architecture fixture;
- session, CSRF, timeout, safe Problem Details and production configuration;
- one focused payment, storage and AI messaging boundary;
- parameterized deal/fulfillment transitions, ratification canonicalization,
  review mapping and payment/settlement rules.

Delete all other backend tests and obsolete fixtures, including per-module
migration suites, repository CRUD duplication, mapper/DTO echo tests, exhaustive
state/authorization/concurrency matrices, duplicate OpenAPI fingerprint and
contract-digest parity tests, and tests whose purpose is aggregate coverage.

Frontend retains exactly:

- `coreApi.test.ts` for same-origin credentials, CSRF refresh, legal-entity
  context and API/session-expiry handling;
- one authentication boundary test for protected rendering, unauthenticated
  redirect and expired-session reset;
- `money.test.ts` for minor-unit parsing/formatting and safe boundaries.

Delete presentation, dialog, badge, button, form, panel and simple date-format
tests. Do not add browser automation. Remove unused frontend coverage
configuration and `@vitest/coverage-v8`.

Python tools retain only:

- mock worker fail-closed configuration, one contract-valid document success,
  publish-confirm-before-ACK and invalid-request no-success-publish;
- Moka deterministic repeated identity/query-first late recovery,
  production-like startup refusal and bounded request safety.

Delete scenario/video matrices, the uncollected RabbitMQ smoke script and the
static Moka transport-matrix test/fixture when unreferenced.

## 4. Validation and CI behavior

Remove JaCoCo from the default Maven lifecycle. An explicit `coverage` profile
may generate a diagnostic report, but it has no minimum threshold. Spotless
remains a hard backend gate.

Repository validation wrappers for shell and PowerShell must run:

1. contract validation and the structural negative fixture;
2. default backend `mvn verify`;
3. non-mutating generated TypeScript drift, lint, format, retained frontend
   tests and production build;
4. retained mock-worker and Moka tests;
5. Markdown/ADR references, Flyway history and repository-map freshness;
6. `git diff --check`.

Validation must not leave generated tracked files modified. Each subsystem and
the total duration are printed, but elapsed time does not itself fail CI.

The Flyway history guard records SHA-256 values for V1–V27. Local checks reject
missing or modified recorded migrations. CI also compares the base revision and
rejects modification, deletion or rename of an existing migration. A new
migration must be add-only, have a version above the base maximum and add its
checksum.

Python tool tests run in a parallel CI job. The Core job no longer installs
contract-validator dependencies after removal of the Java shell parity test.
Numeric ADR and `FORBIDDEN.md` live references fail repository hygiene except
under history and frozen migrations.

Container image and routing smoke validation move to a separate
release-artifact lane without weakening their checks. Container builds,
deployment smoke and dependency installation are outside the five-minute
developer-validation budget.

## 5. Compatibility, exclusions and acceptance

No endpoint, request/response shape, event schema, state transition, database
structure or production runtime behavior changes.

Contract edits are limited to documentation-only stale-reference cleanup.
Because contract bundles hash exact bytes, the implementation records the new
digest, proves OpenAPI structure against the base, regenerates committed
TypeScript declarations and reports any optional external AI baseline as
unverified until its owner accepts the byte-only delta.

Out of scope:

- new product features;
- large frontend decomposition;
- framework-free domain conversion;
- browser/E2E tests;
- security, dependency, license, SBOM or provenance scanning;
- real payment-provider, KYC, custody or production AI decisions;
- unrelated production defect fixes.

Final implementation evidence must include:

- all five critical workflows and generic invariant groups passing;
- default Maven verification without coverage enforcement;
- optional coverage profile configuration without a threshold;
- frontend and Python-tool gates passing;
- contract validation and base-versus-head OpenAPI structure passing;
- no V1–V27 SQL diff;
- no links to deleted ADRs or unapproved live numbered references;
- clean, idempotent repository-map validation;
- one warm all-repository timing, with an acceptance ceiling of five minutes;
- no generated validation residue.

The implementer reports outcomes, exact branch/base/HEAD, before/after
inventory, timings, contract digest delta and residual risks. The implementer
does not claim `ACCEPT` and does not edit CURRENT.

Independent planner closeout reviews the full diff and survivor semantics,
marks the five ADRs accepted only when their prose matches this plan, updates
CURRENT and ROADMAP, moves this plan to `docs/plan/done/`, regenerates final
documentation artifacts and issues `ACCEPT`, `FIX` or `REPLAN`.
