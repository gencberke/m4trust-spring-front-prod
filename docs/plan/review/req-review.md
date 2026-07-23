# Review Request
Task: 17-T01
Revision: 2
Plan: docs/plan/ready/17-fulfillment-experience-and-simulated-settlement.md
Phases: A, B1, B2, B3, B4 (B5 planner-owned; not assigned)
Status: IN_PROGRESS
Branch: feat/plan17-fulfillment-settlement
Base: main@846c16c
Plan completion claim: NO

## Phase outcomes
- A — DONE — living fulfillment: 5s poll, turn banner, feedback, timeline; typecheck+build PASS (`c881019`)
- B1 — DONE — additive settlement/release OpenAPI, ratification v2 disputeWindowDays 0..365, ADR-014 §2.2 amendment, validator closed sets, generated types; validate_contracts + tsc PASS (`3e550dd`)
- B2 — DONE — V23 settlement/release migration (v2 snapshot schema constraint), fulfillment.completed_at, payment domain (settlement, release operation, dispatch relay), sandbox release provider methods, settlement HTTP surface, DealDetail settlement projection, readiness refresh + v2-safe ratification status lookup, 12 ApiErrorCode synced; OPENAPI_AHEAD removed; focused unit + integration tests PASS
- B3 — NOT_STARTED
- B4 — NOT_STARTED

## Validation
- `npm run typecheck` / build (Phase A) — PASS
- `python contracts/scripts/validate_contracts.py` — PASS (OPENAPI_AHEAD cleared)
- `npm run generate:api` + `npx tsc --noEmit` (Phase B1) — PASS
- B2 focused: `ErrorCatalogExactSetTest`, `SandboxPaymentProviderBootstrapGuardTest`, `SettlementStateMachineTest`, `SettlementEligibilityEvaluatorTest`, `SettlementReleaseMigrationIntegrationTest`, `PaymentSettlementIntegrationTest` — PASS (Testcontainers)

## Decisions needed
- None

## Deviation or risk
- None for B2 gate; B3 ratification package create (v2 disputeWindowDays) not started
