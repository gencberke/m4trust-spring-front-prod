# Review Request
Task: 15-T01
Revision: 2
Plan: docs/plan/ready/15-production-reconciliation-and-readiness.md
Phases: P1
Status: PARTIAL
Branch: codex/s15-t01-error-authority
Base: codex/production-reconciliation-fix@6205be89274494aa5f035371fc673dd3096cd140
Plan completion claim: NO

## Phase outcomes
- P1 — PARTIAL — Closed OpenAPI/Java ApiErrorCode (89) and FieldErrorCode (10) catalogs with validator exact-set and expected-invalid coverage; fulfillment/evidence emit granular DEAL/FULFILLMENT/EVIDENCE_NOT_FOUND; frontend uses generated ApiErrorCode types. Full `mvnw verify` blocked by local Docker daemon unavailable (0 assertion failures, 34 Testcontainers bootstrap errors).

## Validation
- `git merge-base --is-ancestor 6205be89274494aa5f035371fc673dd3096cd140 HEAD` — PASS
- `python contracts/scripts/validate_contracts.py` — PASS
- Focused Core unit tests (`ErrorCatalogExactSetTest`, `FulfillmentExceptionHandlerTest`, `AnalysisExceptionHandlerTest`, `FulfillmentServiceTest`, `ApiInfrastructureTest`) — PASS
- `cd services/core-api; .\mvnw.cmd verify` — FAIL (environment: Docker engine not running; 0 assertion failures)
- `cd frontend; npm.cmd ci && npm.cmd run generate:api && npm.cmd run typecheck && npm.cmd run build` — PASS
- `rg removed combined fulfillment codes in production sources` — PASS
- `git diff --check` — PASS

## Decisions needed
- None for catalog contents. Historical runtime `ACCESS_DENIED` is not in OpenAPI/ADR-006 named globals and was not added to the catalog.

## Deviation or risk
- `ProblemDetailsAccessDeniedHandler` non-CSRF branch no longer emits uncatalogued `ACCESS_DENIED`. Unexpected `AccessDeniedException` logs and returns `INTERNAL_ERROR` / HTTP 500. CSRF remains `CSRF_TOKEN_INVALID` / 403. Under the current `anyRequest().authenticated()` filter chain this branch is defensive only.
- Full Core verify requires a running Docker engine for Testcontainers; re-run after Docker is available before planner acceptance.
