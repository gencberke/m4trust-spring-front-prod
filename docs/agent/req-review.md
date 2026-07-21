# Review Request
Task: 07-T03
Revision: 1
Plan: docs/plan/done/07-staging-deployment.md
Phases: §5 main-bound release wiring; §5 Runtime security; §6 Frontend; §7 browser/security acceptance
Status: COMPLETED
Branch: codex/slice-07-staging-foundation
Base: main@555e8e427be9b11cc219bffdfe149cd84ac60df3
Plan completion claim: NO

## Phase outcomes
- Main-bound staging release — DONE — core deployment `b810a1f3-4a1f-4db2-9e7e-cd660b2100b0` and web deployment `9c613818-2856-4b36-a673-020e22d4b3eb` succeeded from `main@832cccab8e6f4e2c32bed8230520bdc76ec9df82`; both are non-serverless and expose immutable image digests.
- Runtime security and edge topology — DONE — only the web edge is public; core and PostgreSQL remain private. The public edge returned HTTPS security headers, blocked `/actuator/**` with 404, and proxied unauthenticated `/api/v1/auth/me` to the expected 401 boundary.
- Browser/security acceptance — DONE — two independent browser contexts exercised register, login, session restoration after navigation, logout, legal-entity creation/selection, Deal create/list/detail/update/DRAFT cancel, SPA deep-link reload, and non-disclosing access denial for an unrelated legal entity.

## Validation
- Exact main source revision and release identity — PASS — Railway metadata and runtime logs agree on `832cccab8e6f4e2c32bed8230520bdc76ec9df82`; core digest `sha256:5d581b5c8670f975be3112d3f7cfc83448842dd979141ed1849118cbba7df77e`, web digest `sha256:53f40495437b7c57575819c6b7725bff1b9d02b287910ad88a098724a249ebdc`.
- Flyway pre-deploy and readiness — PASS — 22 migrations validated, schema V22 required no migration, runtime and `/actuator/health/readiness` started successfully.
- HTTPS, auth refresh/logout, entity and Deal browser smoke — PASS
- Independent-context authorization visibility — PASS — unrelated context received the non-disclosing “Deal bulunamadı” state on the exact deep link.
- Same-origin API, forwarded scheme/host, SPA fallback — PASS
- Private core/database and edge actuator block — PASS
- Focused secret/bundle/network spot check — PASS — final browser bundle contained no Railway private host, core service identifier, database variable name, or private-origin variable name.
- `git diff --check` — PASS

## Decisions needed
- None

## Deviation or risk
- RabbitMQ is not provisioned in Slice 7 staging, so the otherwise-ready core logs retry the default local broker connection. RabbitMQ-dependent asynchronous behavior remains outside this task; accepted HTTP/browser behavior and readiness are unaffected.
