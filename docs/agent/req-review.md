# Review Request
Task: 07-T01
Revision: 1
Plan: docs/plan/ready/07-staging-deployment.md
Phases: §5 Image and network; §5 Migration — successful pre-deploy path; §5 Runtime security — staging provisioning and release identity
Status: COMPLETED
Branch: codex/slice-07-staging-foundation
Base: main@555e8e427be9b11cc219bffdfe149cd84ac60df3
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
- Source revision deployed to both services: `555e8e427be9b11cc219bffdfe149cd84ac60df3`.
- Release identity: build version `0.1.0-staging.555e8e4`, full commit SHA, staging
  environment label and build time, carried as image labels, `/actuator/info`
  fields and every structured log line.
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
- Consequence of the same limitation: `frontend/railway.json` is not discovered
  for the web edge, because its declared Dockerfile path is repository-root
  relative while the file itself lives under `frontend/`. The web edge builds
  from the correct Dockerfile through a Railway build variable, but its
  `healthcheckPath` and restart policy are not applied. Setting the web-edge
  custom config-file path once in the Railway service settings resolves this.
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
- Deployments are upload-based rather than GitHub-triggered, so staging does not
  yet redeploy automatically on merge to main.
