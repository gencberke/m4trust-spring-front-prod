# Review Request
Task: 11BA-T03
Revision: 1
Plan: docs/plan/done/11b-a-moka-provider-foundation.md
Phases: A-P5–A-P6
Status: COMPLETED
Branch: codex/11ba-t03
Base: main@3b52f60128ab05b94df34b1bc6e62c076f3db527
HEAD at validation: 7a5bac910e7dd3a8c9670611d5886e5e1043b6b0 (the following report-only commit is intentionally not part of validation)
Plan completion claim: NO
Planner decision: ACCEPT — Slice 11B-A accepted at `main@7e773d9`; see
`docs/plan/done/review/slice-11b-a-acceptance-2026-07-22.md`.

## Phase outcomes
- A-P5 — DONE — `PaymentFundingIntegrationTest` uses the real Core API durable relay and separately started Python HTTP emulator. Success is FUNDED only after verified emulator status; timeout stays UNCONFIRMED/PENDING, blocks a new charge, then query recovery reaches FUNDED.
- A-P5 — DONE — Exact adapter-to-emulator calls are success: 1 query + 1 initiate; timeout recovery: 3 queries + 1 initiate, all with the one lifetime-fixed provider key. The test asserts no external call observes an active DB transaction and checks unchanged funding-plan/deal projections.
- A-P6 — DONE — Focused emulator, Moka transport/client/bootstrap and sandbox guards, payment recovery, and module architecture validations pass. No browser E2E or full backend suite was run.

## Validation
- `python3 -m unittest discover -s tests -v` (tools/moka-emulator) — PASS (11 tests; local loopback HTTP)
- `mvn -q -Dtest=MokaTransportSafetyTest,MokaEmulatorClientIntegrationTest,MokaPaymentProviderBootstrapGuardTest,SandboxPaymentProviderBootstrapGuardTest,ModuleArchitectureTest,PaymentFundingIntegrationTest test` (services/core-api) — PASS; focused reports: Moka transport 3, emulator client 2, Moka bootstrap 3, sandbox bootstrap 3, payment funding 11.
- `mvn -q -Dtest=ModuleArchitectureTest test` (services/core-api) — PASS (6 tests; separate run because it was not emitted by the combined Surefire selection)
- `git diff --check` — PASS
- `git diff --name-only main...HEAD` — PASS for scope: only `PaymentFundingIntegrationTest` and this review request; zero contract, frontend, migration, production config, settlement/release, Deal-completion, refund/void/payout, or business pool-operation diff.
- Generated Python cache check — PASS; no `__pycache__` artifact remains.

## Configuration profiles
- Core vertical test: `local`, with durable relay enabled and a test-scoped port delegating to the separate emulator process.
- Moka adapter bootstrap: `local-moka` only with non-secret fixture settings and bounded timeouts/body sizes.
- Exclusion: Moka local adapter rejects `staging`, `production`, and `local-sandbox`; emulator startup rejects staging/production.

## Known provider gaps
- This is local emulator evidence only: it does not prove real Moka semantics, callback/3D authority, pool finality, duplicate behavior, pending cutoffs, or release capability.
- Pool approve/query remains integration-only probe evidence and non-final. G1 and real-provider acceptance remain open.

## Decisions needed
- None

## Deviation or risk
- Plan completion claim remains NO because independent planner review/acceptance is not performed by this implementation task.
