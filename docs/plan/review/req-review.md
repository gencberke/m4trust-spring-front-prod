# Review Request
Task: 18B-T01
Revision: 1
Plan: docs/plan/ready/18b-ratified-evidence-policy.md
Phases: 18B-P1
Status: COMPLETED
Branch: plan/fulfillment-closure-simplification
Base: main@693add7 (+ 18A commit fc1bd09)
Plan completion claim: NO

## Phase outcomes
- 18A — DONE — already landed at fc1bd09 (Kapanış/SETTLEMENT lifecycle + UI); not reworked in this task.
- 18B-P1 — DONE — ADR-011 dated evidence-policy amendment + ADR-INDEX/FORBIDDEN sync; OpenAPI EvidencePolicy, RatificationPackageSnapshotV3, CreateRatificationPackageRequest combinations, fulfillment evidencePolicy, canAcceptWithoutEvidence, POST accept-without-evidence + AcceptWithoutEvidenceConflict codes; validator closed sets; changelog; ratification example fixtures; generated frontend types.
- 18B-P2+ — NOT_STARTED — out of this packet.

## Validation
- `npm run generate:api` — PASS (core-api.d.ts refreshed)
- `python contracts/scripts/validate_contracts.py` — NOT_RUN — deferred to coordinator final gate
- mvn/npm test/build — NOT_RUN (coordinator policy: skip until end)

## Decisions needed
- None

## Deviation or risk
- New ApiErrorCode values `FULFILLMENT_EVIDENCE_POLICY_CONFLICT` and `FULFILLMENT_EVIDENCE_PRESENT` are OpenAPI-ahead of Java via validator tolerance until 18B-P3 behavior wiring.
- Required `evidencePolicy` on existing fulfillment summary/detail projections is an additive wire break for old clients that ignored unknown fields but assumed exact required sets; packet requires the field.
