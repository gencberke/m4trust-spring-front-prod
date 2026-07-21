# Review Request
Task: 07-T01
Revision: 1
Plan: docs/plan/ready/07-staging-deployment.md
Phases: §5 Image and network; §5 Migration — successful pre-deploy path; §5 Runtime security — staging provisioning and release identity
Status: COMPLETED
Branch: codex/slice-07-staging-foundation
Base: main@555e8e427be9b11cc219bffdfe149cd84ac60df3
HEAD: this commit; every preceding commit on this branch, through
`c1485a09dca04bb1269764b4bf517c6396321c8f`, is pushed to
`origin/codex/slice-07-staging-foundation`
Plan completion claim: NO

## Repository impact
No application, configuration, or migration source changed. The PR #13
config-as-code foundation was used as-is; no deployment defect required a source
fix. The only changed file on this branch is this review request.

## Railway topology (staging)
- Project `m4trust-staging`, new environment `staging` (the pre-existing default
  environment and its services were left untouched).
- `m4trust-web-edge` — public HTTPS edge, Railway service domain only.
- `m4trust-core-api` — private service, no service domain, no TCP proxy.
- PostgreSQL — private service, no service domain. Railway provisioned it with a
  public TCP proxy by default; that proxy was deleted so the database has no
  public ingress.
- Core API and PostgreSQL are reachable only through `*.railway.internal`
  private networking; neither internal host resolves publicly.

## Deployed revision
- Core API is deployed from source revision
  `555e8e427be9b11cc219bffdfe149cd84ac60df3`.
- The web edge is deployed from its connected GitHub source at
  `c1485a09dca04bb1269764b4bf517c6396321c8f`, and its release identity variables
  are pinned to that same commit. Later commits on this branch are documentation
  only and do not match its declared watch patterns, so they do not change the
  deployed web-edge revision.
- Release identity for each service is its build version, full commit SHA,
  staging environment label and build time, carried as image labels, and for the
  Core API additionally through `/actuator/info` and every structured log line.
- `latest` is not used as release or rollback identity; image tags and OCI
  `image.revision` labels carry the exact commit SHA.

## Migration result
- Flyway ran only through the one-shot `m4trust-core-api migrate` pre-deploy
  command declared in `services/core-api/railway.json`.
- Pre-deploy run created the schema history on an empty database and applied 22
  migrations, then the process exited before the web process started.
- The runtime web process started with the `staging` profile and produced no
  Flyway activity; readiness reported UP.

## Validation
- Core API deployment applied `services/core-api/railway.json`: the deployment's
  applied manifest matches that file exactly, including `preDeployCommand`,
  `startCommand`, `healthcheckPath`, healthcheck timeout and restart policy — PASS
- Web edge deployment applied `frontend/railway.json` through the service's
  custom config-file path: the applied manifest carries `healthcheckPath`
  `/healthz`, its timeout, the restart policy and the declared watch patterns — PASS
- Focused deployment configuration and migration-launcher tests — PASS
- Core API and web-edge image builds with explicit `APP_VERSION` and
  `GIT_COMMIT_SHA` — PASS
- Image `migrate` and `run` launchers verified separately against a disposable
  local database — PASS
- Railway pre-deploy and runtime logs show migration ownership and release
  identity — PASS
- HTTPS edge reachability, SPA deep-link fallback, `/healthz`, and `/actuator`
  blocked at the edge — PASS
- `/api/*` proxied privately to Core API (authenticated-session problem+json
  returned through the edge) — PASS
- Absence of public Core API and PostgreSQL ingress (no domains, no TCP proxies,
  no public DNS for internal hosts) — PASS
- Frontend production bundle and image metadata free of private URLs and secrets;
  bundle uses the relative `/api/v1` base — PASS
- Required secrets present only as Railway variables — PASS
- `git diff --check` and changed-file review — PASS
- Planner-owned §7 browser acceptance, migration failure gate, rollback
  rehearsal — NOT RUN (out of task scope)

## Decisions needed
- None

## Deviation or risk
- Railway CLI v5.27.2 `environment edit` accepts `--service-config` but applies
  nothing, so service root directory and custom config-file path cannot be set
  from the CLI. Deployments therefore use direct uploads with the service source
  as the archive root, which is what makes `services/core-api/railway.json` (and
  its pre-deploy migration command) apply correctly.
- `frontend/railway.json` declares a repository-root relative Dockerfile path
  while the file itself lives under `frontend/`, so it is only discoverable
  through the service's custom config-file path setting, which exists only in the
  Railway dashboard. That setting is now applied and verified for the web edge.
- The web edge is deployed from its connected GitHub source rather than an
  upload. With an upload source the two available CLI paths never intersect:
  a fresh upload reads the config file but is skipped by its own watch patterns,
  while a redeploy rolls out but reuses the previous snapshot's manifest. The
  connected source also gives the web edge a genuine per-commit release identity.
  Consequence to be aware of: pushes to this branch now trigger staging web-edge
  deployments automatically when they touch the declared watch patterns.
- The Core API is still deployed from uploads rooted at `services/core-api`,
  which is what makes its `railway.json` discoverable without a dashboard
  setting. Setting its custom config-file path to `/services/core-api/railway.json`
  would allow it to move to a connected source as well.
- Object storage is out of this task's scope and is not provisioned in staging.
  Core API startup validates those properties, so non-functional placeholder
  values are set as Railway variables. Document upload paths will not work in
  staging until Slice 6 staging provisioning selects a real backend.
- Core API binds the IPv6 wildcard through a Railway variable so private
  networking reaches it. This is environment configuration only; the base
  configuration is unchanged.
- RabbitMQ is not provisioned in staging and the deployed Core API logs a
  repeating broker connection failure. Readiness is unaffected. Messaging is
  outside this task's scope.
- Neither service deploys automatically on merge to `main`; the web edge tracks
  this task branch and the Core API is deployed manually from uploads. Wiring
  staging to `main` remains open.
