# Review Request
Task: 17-T01
Revision: 1
Plan: docs/plan/ready/17-fulfillment-experience-and-simulated-settlement.md
Phases: B1 (implementer scope this unit)
Status: IN_PROGRESS
Branch: feat/plan17-fulfillment-settlement
Base: main@846c16c
Plan completion claim: NO

## Phase outcomes
- A — DONE — living fulfillment (prior unit)
- B1 — DONE — additive settlement/release OpenAPI paths, schemas, error catalog, ratification v2 disputeWindowDays 0..365, ADR-014 §2.2 amendment, validator Plan 17 closed sets, generated types; `validate_contracts.py` PASS; `npm run generate:api` + `npx tsc --noEmit` PASS
- B2 — NOT_STARTED
- B3 — NOT_STARTED
- B4 — NOT_STARTED

## Validation
- `python contracts/scripts/validate_contracts.py` — PASS
- `npm run generate:api` — PASS
- `npx tsc --noEmit` — PASS
- `git diff --check` — PASS

## Decisions needed
- None

## Deviation or risk
- Validator allows 12 settlement/release ApiErrorCode values in OpenAPI ahead of Java enum sync until B2 (`OPENAPI_AHEAD_OF_JAVA_API_ERROR_CODES`).
