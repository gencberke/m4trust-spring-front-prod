# Review Request
Task: 18B-T02
Revision: 1
Plan: docs/plan/ready/18b-ratified-evidence-policy.md
Phases: 18B-P2, 18B-P3, 18B-P4
Status: COMPLETED
Branch: plan/fulfillment-closure-simplification
Base: main@693add7 (+ 18A fc1bd09 + 18B-P1 f5ed12c)
Plan completion claim: NO

## Phase outcomes
- 18B-P1 — DONE — prior packet at f5ed12c.
- 18B-P2 — DONE — commit 50be5c0: RatificationSnapshotAssembler v3; create combinations + 422 evidencePolicy alone; read/projection schema 1/2/3; settlement v2|v3 windows; V24 schema_version IN (1,2,3).
- 18B-P3 — DONE — commit 50be5c0: V25 fulfillment.evidence_policy NOT NULL + REQUIRED backfill; start persists effective policy; detail/summary + canAcceptWithoutEvidence; POST accept-without-evidence; NOT_REQUIRED hides upload; ApiErrorCode wiring.
- 18B-P4 — DONE — DealRatificationPanel evidence-policy control (default REQUIRED) + always send disputeWindowDays+evidencePolicy (v3 creates); policy in review/history; DealFulfillmentPanel policy display, hide upload via backend canUpload, buyer-only "Teslimatı kanıtsız kabul et" via canAcceptWithoutEvidence; acceptWithoutEvidence API helper + error strings.

## Validation
- mvn/npm test/build — NOT_RUN (packet policy: skip)
- Contract/OpenAPI edits — NOT_TOUCHED (adapt Java/UI to f5ed12c)

## Decisions needed
- None

## Deviation or risk
- UI create path always produces v3 (requires dispute window + policy); API still accepts v1/v2 combinations.
- IN_PROGRESS→COMPLETED allowed for no-file acceptance; REQUIRED path unchanged through review.
- Integration seed INSERTs include evidence_policy='REQUIRED' after V25.
