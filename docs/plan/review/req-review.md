# Review Request
Task: 15-T01
Revision: 3
Plan: docs/plan/ready/15-production-reconciliation-and-readiness.md
Phases: P1
Status: COMPLETED
Branch: codex/s15-t01-error-authority
Base: codex/s15-t01-error-authority@b9e53578b5c104381952b54549c166abd67d7093
Plan completion claim: NO

## Phase outcomes
- P1 — DONE — Restored non-CSRF AccessDenied to ACCESS_DENIED/403 (CSRF unchanged); added contract-first ACCESS_DENIED and catalog-independent OpenAPI error ownership with exact-set + negative drift detection; replaced public Conflict String/valueOf escapes with ApiErrorCode; nested evidence checks Deal visibility before evidence lookup and distinguishes FULFILLMENT_NOT_FOUND vs EVIDENCE_NOT_FOUND; service/API tests cover those boundaries. Windows Moka emulator tests launch via `python` (not Store `python3` stub) so verify includes real emulator health.

## Validation
- `python contracts/scripts/validate_contracts.py` — PASS
- Focused Core tests (`ProblemDetailsAccessDeniedHandlerTest`, `ErrorCatalogExactSetTest`, `FulfillmentExceptionHandlerTest`, `FulfillmentServiceTest`, `MokaEmulatorClientIntegrationTest`) — PASS
- `cd services/core-api; .\mvnw.cmd verify` — PASS (0 failures/errors, including Moka)
- `cd frontend; npm.cmd ci && npm.cmd run typecheck && npm.cmd run build` — PASS
- `rg DEAL_OR_LEGAL_ENTITY_NOT_FOUND_OR_HIDDEN|FULFILLMENT_OR_EVIDENCE_NOT_FOUND_OR_HIDDEN` in production sources — PASS (no matches)
- `rg ApiErrorCode\.valueOf|Conflict\(String` in `services/core-api/src/main/java` — PASS (no matches)
- `git diff --check` — PASS

## Decisions needed
- None

## Deviation or risk
- Moka emulator ProcessBuilder uses `python` on Windows and `python3` elsewhere (override via `M4TRUST_PYTHON`). Payment/Moka business behavior is unchanged; this only avoids the Windows Store `python3.exe` stub so the real emulator can start during verify.
