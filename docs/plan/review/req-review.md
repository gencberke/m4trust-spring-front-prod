# Review Request
Task: 18B-T02
Revision: 1
Plan: docs/plan/ready/18b-ratified-evidence-policy.md
Phases: 18B-P2, 18B-P3, 18B-P4
Status: IN_PROGRESS
Branch: plan/fulfillment-closure-simplification
Base: main@693add7 (+ 18A fc1bd09 + 18B-P1 f5ed12c)
Plan completion claim: NO

## Phase outcomes
- 18B-P1 — DONE — prior packet at f5ed12c.
- 18B-P2 — DONE — RatificationSnapshotAssembler v3 (both disputeWindowDays+evidencePolicy); create combinations + 422 when evidencePolicy alone; read/projection guards for schema 1/2/3; SettlementRatificationSourceAdapter + SettlementEligibilityEvaluator accept v2|v3 windows; V24 schema_version IN (1,2,3). Unit test sources updated (assembler v3, settlement eligibility).
- 18B-P3 — DONE — V25 fulfillment.evidence_policy NOT NULL + REQUIRED backfill; persist effective policy at start via FulfillmentDealSourceAdapter/RatificationPackageProjectionPort; detail/summary expose evidencePolicy; canAcceptWithoutEvidence on Deal+fulfillment actions; POST accept-without-evidence (buyer ADMIN, Deal→fulfillment lock, atomic completedAt, audit+idempotency); NOT_REQUIRED hides upload server-side; ApiErrorCode FULFILLMENT_EVIDENCE_POLICY_CONFLICT/PRESENT. Compile-source test fixes for FulfillmentRecord/Target/inserts/transitions.
- 18B-P4 — IN_PROGRESS — frontend UX pending.

## Validation
- mvn/npm test/build — NOT_RUN (packet policy: skip)
- Contract/OpenAPI edits — NOT_TOUCHED (adapt Java/UI to f5ed12c)

## Decisions needed
- None

## Deviation or risk
- IN_PROGRESS→COMPLETED now allowed on Fulfillment/Milestone aggregates for no-file acceptance; REQUIRED path still goes through review.
- Integration seed INSERTs updated to include evidence_policy='REQUIRED' after V25.
