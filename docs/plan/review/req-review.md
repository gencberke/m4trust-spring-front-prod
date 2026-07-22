# Review Request
Task: 15-T04
Revision: 1
Plan: docs/plan/ready/15-railway-demo-reconciliation-and-deployment.md
Phases: P4
Status: COMPLETED
Branch: codex/s15-t04-railway-demo-reconcile
Base: codex/s15-railway-demo-replan@09fca0a43ce5a31c4537b5f630ec04dccd8ee95f
Plan completion claim: NO

## Phase outcomes
- P4 — DONE — Removed ten unreleased ADR-017/018-only ApiErrorCode values from OpenAPI ownership/enum, Java, generated frontend types, and changelog (ACCESS_DENIED and DEAL_INVITATION_* preserved). Core `railway.json` uses monorepo-root `services/core-api/Dockerfile` and watches `services/core-api/**` + `contracts/**`. AI Rabbit listener gated on messaging topology so broker is optional when messaging is disabled. Auth/business behavior unchanged.

## Validation
- `python contracts/scripts/validate_contracts.py` — PASS
- Focused `ErrorCatalogExactSetTest`, `DeploymentConfigurationTest`, `ReleaseIdentityPropertiesTest` — PASS (10)
- frontend `generate:api` + `typecheck` — PASS
- `docker build -f services/core-api/Dockerfile .` + image smoke (non-root, 21 schemas, packaged digest matches source+label) — PASS
- `git diff --check` — PASS
- `mvnw verify` / frontend build / full suites — NOT_RUN (packet)

## Decisions needed
- None

## Deviation or risk
- Image smoke run via Python/PowerShell equivalent (Git Bash unavailable on this host).
- Railway dashboard Root Directory for Core must be `/` to match the updated dockerfilePath (documented in DEVELOPMENT.md).
