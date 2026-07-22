# Review Request
Task: 15-T02
Revision: 1
Plan: docs/plan/ready/15-production-reconciliation-and-readiness.md
Phases: P2–P3
Status: COMPLETED
Branch: codex/s15-t02-contract-bundle-runtime-drift
Base: codex/s15-t01-error-authority@d69d7e00d8595d7280ff6798b167b7b7f389a8a8
Plan completion claim: NO

## Phase outcomes
- P2 — DONE — ADR-016 digest (Python/Java parity), packaged classpath bundle, core-internal OpenAPI, probe-token metadata endpoint, Docker monorepo-root build with OCI digest labels, non-root image smoke parses packaged schemas.
- P3 — DONE — springdoc disabled in production, contract-profile runtime OpenAPI drift gate with positional path-template normalize, frontend generate:api dirty-diff CI gate, contracts workflow negative fixture + UNVERIFIED_EXTERNAL_GATE when AI baseline unset (no invented AI endpoints).

## Validation
- `python contracts/scripts/validate_contracts.py` — PASS
- `python contracts/scripts/validate_contracts.py --print-digest` — PASS (`sha256:987b001e6e8f06af1323fd0ca957ffe3d49fa044ab0d9b476ac68c1f75759b8a`)
- `python contracts/scripts/compare_openapi_structure.py --negative-fixture` — PASS
- Focused openapi/contract tests — PASS (17)
- `cd services/core-api; .\mvnw.cmd verify` — PASS (366 tests, 0 failures; no Surefire forced shutdown)
- `cd frontend; npm.cmd ci && npm.cmd run generate:api && npm.cmd run typecheck && npm.cmd run build` — PASS
- `docker build -f services/core-api/Dockerfile .` — PASS (CRLF strip for Windows checkouts)
- `services/core-api/docker/smoke-image.sh` — PASS (PowerShell-equivalent: non-root user + 21 packaged schemas parsed; Git Bash lacked `python` on PATH)
- `git diff --check` — PASS

## Decisions needed
- None

## Deviation or risk
- Non-40-hex `GIT_COMMIT_SHA` maps to forty zeros for `releaseRevision` rather than failing startup.
- Dockerfile strips CR from `mvnw`/entrypoint so Windows CRLF checkouts still build Linux images.
- Runtime↔committed OpenAPI drift gate compares operation identity (method+positional path); richer parameter/status/media/$ref comparison is covered by the Python negative fixture comparator.
