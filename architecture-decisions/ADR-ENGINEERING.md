# Engineering authority and validation

## Status

Accepted (2026-07-28). Merged `main@13d8f0a` (PR #53).

## Context

M4Trust needs rules that make change safe without turning documentation or test
counts into a proxy for confidence. Product behavior is deliberately described
by contracts and accepted project state, while this ADR defines how work is
planned, implemented and evidenced.

## Decisions

### Authority

- Read authority in the order published by `ADR-INDEX.md`.
- Treat committed OpenAPI, AsyncAPI and JSON Schema as exact interface truth.
- Use `CURRENT.md` for accepted capability, deliberate limitations and runtime
  carve-outs; do not recreate that detail in an ADR.
- Treat plans and history as rationale and delivery records, not standing rules.
- Keep ADRs principle-oriented; feature-specific lifecycle details belong in
  contracts and accepted state.

### Change discipline

- Start work from a focused, reviewable plan when it changes interfaces,
  persistence, authorization, external boundaries or durable architecture.
- Prefer the smallest change that preserves ownership and compatibility.
- Do not use a refactor, mock or test-only path to evade a stated boundary.
- Preserve unrelated working-tree changes and do not silently expand scope.
- Surface a genuine conflict rather than inventing a new rule during delivery.
- Public or shared API changes are contract-first and regenerate committed client
  declarations where applicable.
- Applied database migrations are forward-only and require explicit compatibility
  consideration.

### Test policy

- Tests prove critical risks, not implementation detail or coverage volume.
- Keep one happy path for each critical workflow and one representative
  authorization or tenant-isolation negative case.
- Retain proofs for money/state corruption, idempotency, concurrency, contract
  drift, migration compatibility, architecture boundaries and complex pure rules.
- Prefer compact parameterized cases where they express the same invariant.
- Remove tests that only duplicate CRUD, DTO mapping, rendering detail or a
  stronger public-boundary proof.
- Do not retain a test solely to raise a metric, preserve a historical count or
  satisfy a coverage threshold.
- Browser, provider, deployment and AI-internal testing are owned only where the
  relevant boundary is actually controlled by this repository.

### Validation

- Spotless, TypeScript checks, formatting, contract validation and critical tests
  remain hard correctness gates.
- JaCoCo is optional diagnostic reporting; it has no minimum percentage gate.
- There is no minimum test-count gate.
- A warm repository validation covers backend, frontend, contracts, local tools
  and repository hygiene; it targets under three minutes and must remain at or
  below five minutes on a dependency-ready environment.
- Container-image build and deployment smoke remain separate release validation;
  their time is not part of the developer-loop target.
- Validation wrappers report elapsed subsystem and total timing but never fail
  merely because a machine is slower.

### Review checkpoints

- Identify the user-visible or integrity risk before adding a test.
- Name the authoritative contract before changing an interface.
- Keep implementation reports factual and bounded to observed evidence.
- Keep planner acceptance separate from implementer delivery.
- Prefer one strong proof over repeated similar examples.
- Keep test fixtures legible enough to show the protected behavior.
- Measure warm validation only after prerequisites are ready.
- Keep release validation independent of local developer convenience.
- Record deferred production work in CURRENT or ROADMAP, not in a test waiver.
- Revisit the policy when a new critical boundary is introduced.

## Non-negotiables

- Do not change wire contracts without contract-first review.
- Do not modify an applied migration.
- Do not claim acceptance or edit accepted project state during implementation.
- Do not substitute coverage, test count or documentation volume for risk proof.
- Do not weaken a critical invariant merely to make validation faster.

## Consequences

The repository can evolve with smaller evidence sets, but each retained test
must have an explicit risk it protects. A future feature may add a focused test
when it introduces a new critical risk; it must not restore broad matrices by
default.

## Change triggers

Revise this ADR when authority order, contract-first policy, validation ownership
or the definition of a critical risk changes.
