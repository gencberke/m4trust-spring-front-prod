# Review Request
Task: 17-T01
Revision: 3
Plan: docs/plan/ready/17-fulfillment-experience-and-simulated-settlement.md
Phases: A, B1, B2, B3, B4 (B5 planner-owned; not assigned)
Status: COMPLETED
Branch: feat/plan17-fulfillment-settlement
Base: main@846c16c
Plan completion claim: NO

## Phase outcomes
- A — DONE — living fulfillment: 5s poll, turn banner, feedback, timeline; typecheck+build PASS (`c881019`)
- B1 — DONE — additive settlement/release OpenAPI, ratification v2 disputeWindowDays 0..365, ADR-014 §2.2 amendment, validator closed sets, generated types; validate_contracts + tsc PASS (`3e550dd`)
- B2 — DONE — V23 settlement/release migration, payment domain, sandbox release, HTTP, DealDetail projection, focused unit+integration tests PASS (`893b7ec`, `a4bd800`)
- B3 — DONE — ratification v2 create/read + frontend form/detail; focused tests + typecheck PASS (`66a9426`)
- B4 — DONE — Kapanış settlement UI with simulated labeling and closure separation; typecheck+build PASS (`b6510a5`)

## Validation
- Phase A–B4 focused gates — PASS (see prior revision notes)
- `npm run typecheck` + `npm run build` (B4 re-verify) — PASS
- Repository-wide suite before B5 — PASS: `validate_contracts.py`; `mvn verify` 406 tests / 0 failures (`9402189`); frontend typecheck+build PASS
- V23 settlement FK fix: integration-test TRUNCATE lists include `release_dispatch`, `release_operation`, `settlement` (19 classes; `9402189`)

## Decisions needed
- None

## Deviation or risk
- None for implementer-assigned phases A–B4. B5 staging deploy/acceptance remains planner-owned.

## B4 copy checklist (Plan §0.3 / B4)
- [x] Mandatory label: "Demo simülasyonu — gerçek para hareketi yok"
- [x] Terminal SIMULATED_SETTLED → "Kapanış (simüle) tamamlandı"
- [x] Deal COMPLETED distinct from fulfillment COMPLETED
- [x] No paid/settled/funds-transferred wording; no provider logo
- [x] Fulfillment accept → "Teslimat tamamlandı — kapanış için Kapanış bölümüne geçin"
- [x] Server `releaseEligibleAt`; actions from backend only
