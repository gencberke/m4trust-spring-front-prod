# Review Request
Task: 17-T01
Revision: 3
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
- B3 — DONE — ratification create path emits v2 snapshot when disputeWindowDays supplied (0..365); v1 byte-identical when absent; read/projection exposes schemaVersion+disputeWindowDays; frontend form+detail; focused ratification tests + typecheck PASS (`66a9426`)
- B4 — DONE — settlement UI ("Kapanış" section): `DealSettlementPanel`, API client/queries/errors, release/reconcile with backend-derived actions, operation polling, server `releaseEligibleAt` deadline display, mandatory DEMO_SIMULATED labeling, fulfillment accept copy update, DealDetail closure-separation header notes; typecheck+build PASS

## Validation
- `npm run typecheck` / build (Phase A) — PASS
- `python contracts/scripts/validate_contracts.py` — PASS (OPENAPI_AHEAD cleared)
- `npm run generate:api` + `npx tsc --noEmit` (Phase B1) — PASS
- B2 focused: `ErrorCatalogExactSetTest`, `SandboxPaymentProviderBootstrapGuardTest`, `SettlementStateMachineTest`, `SettlementEligibilityEvaluatorTest`, `SettlementReleaseMigrationIntegrationTest`, `PaymentSettlementIntegrationTest` — PASS (Testcontainers)
- B3 focused: `RatificationSnapshotAssemblerTest`, `RatificationPackageCreateServiceTest`, `RatificationPackageReadServiceTest`, `RatificationV2CreateIntegrationTest`, `RatificationIntegrationTest`, `RatificationRepositoryIntegrationTest` — PASS (Testcontainers)
- `npm run typecheck` (B3 frontend) — PASS
- `npm run typecheck` + `npm run build` (B4 frontend) — PASS
- `git diff --check` (B4) — PASS

## Decisions needed
- None

## Deviation or risk
- None for B4 gate; B5 staging acceptance remains planner-owned

## B4 copy checklist (Plan §0.3 / B4)
- [x] Mandatory label: "Demo simülasyonu — gerçek para hareketi yok" in Kapanış section and on COMPLETED deal header
- [x] Terminal SIMULATED_SETTLED rendered as "Kapanış (simüle) tamamlandı"
- [x] Deal header distinguishes COMPLETED (anlaşma kapatıldı) from fulfillment COMPLETED while deal ACTIVE
- [x] No paid/settled/funds-transferred wording; release button "Simüle kapanışı başlat"
- [x] No provider logo
- [x] Fulfillment accept notice: "Teslimat tamamlandı — kapanış için Kapanış bölümüne geçin"
- [x] `releaseEligibleAt` displayed from server; button enablement follows `canRequestRelease` / `canReconcileRelease` only
