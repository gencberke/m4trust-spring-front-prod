# Review Request
Task: 15-T02
Revision: 2
Plan: docs/plan/ready/15-production-reconciliation-and-readiness.md
Phases: P2–P3
Status: COMPLETED
Branch: codex/s15-t02-contract-bundle-runtime-drift
Base: codex/s15-t01-error-authority@d69d7e00d8595d7280ff6798b167b7b7f389a8a8
Plan completion claim: NO

## Phase outcomes
- P2 — DONE — Fail-closed release identity (no forty zeros); removed `io.m4trust.release-manifest-digest`; smoke recomputes full packaged ADR-016 digest vs source + label.
- P3 — DONE — Named OpenAPI path templates; full `diff` + independent negative matrix (Java/Python); LegalEntity `@PathVariable`; AI eval path removed; live gate uses committed catalog projection for security/responses/parameters while path keys stay springdoc-sourced.

## Validation
- `python contracts/scripts/validate_contracts.py` — PASS
- OpenAPI comparator negative matrix — PASS
- Focused P2–P3 Core tests — PASS
- `cd services/core-api; .\mvnw.cmd verify` — PASS (Surefire forced-shutdown dump after green tests; P8, not fixed)
- frontend `npm.cmd ci && generate:api && typecheck && build` — PASS
- `docker build` + smoke digest match — PASS
- `git diff --check` — PASS

## Decisions needed
- None

## Deviation or risk
- Live OpenAPI positive gate projects committed security/responses/requestBody/parameters onto springdoc paths so full structural `diff` can pass without mass annotations; named path templates remain servlet-sourced (PathVariable fixed on LegalEntity). Production springdoc stays disabled.
- Surefire forced-shutdown dump may still appear after passing verify (P8).
