# Slice 7 Acceptance Record — 21 July 2026

## Result

Slice 7 Railway Staging Deployment is accepted. The repository wiring is merged
through `main@832cccab8e6f4e2c32bed8230520bdc76ec9df82`; the final implementation
report was reviewed at
`codex/slice-07-staging-foundation@2c613262f5cddd21803bc3514a21c0f710d0f974`.

Gate `C2/G4b` is `ACCEPTED`. This acceptance closes staging as a Slice 14B
prerequisite but grants no Slice 11B, ADR-014, or Slice 14B implementation
authority.

## Accepted deployment

- Railway project `m4trust-staging`, environment `staging`.
- Public HTTPS service: `m4trust-web-edge`; private services:
  `m4trust-core-api` and PostgreSQL.
- Core service root `/services/core-api` with config file
  `/services/core-api/railway.json`; web config file
  `/frontend/railway.json`.
- Both services track `main`, keep serverless scaling disabled, and expose exact
  commit and immutable image identity.
- Accepted core deployment `b810a1f3-4a1f-4db2-9e7e-cd660b2100b0`, digest
  `sha256:5d581b5c8670f975be3112d3f7cfc83448842dd979141ed1849118cbba7df77e`.
- Accepted web deployment `9c613818-2856-4b36-a673-020e22d4b3eb`, digest
  `sha256:53f40495437b7c57575819c6b7725bff1b9d02b287910ad88a098724a249ebdc`.
- Railway metadata and core structured logs agree on release SHA
  `832cccab8e6f4e2c32bed8230520bdc76ec9df82`.

## Planner review

The planner reviewed the ready plan, ADR-007 deployment boundaries, ADR-005
cookie/session boundaries, ADR-004 browser-acceptance direction, the consolidated
FORBIDDEN rules, the complete T01–T03 report history, merged repository wiring,
Railway manifests, deployment/build/runtime logs, and live acceptance behavior.

Decisions:

- `07-T01` — `ACCEPT`: topology, images, launcher smoke, successful pre-deploy
  migration, config-as-code, private networking, and initial release evidence.
- `07-T02` — `ACCEPT`: isolated migration failure gate and schema-compatible
  immutable rollback.
- `07-T03` — `ACCEPT`: main-bound deploy, final release identity, runtime
  security, browser flow, and authorization non-disclosure.
- Plan completion — `ACCEPT`: every Done item is proven; plan archived to
  `docs/plan/done/07-staging-deployment.md`.

No forbidden public database/core ingress, insecure production cookie fallback,
secret-in-repository/bundle, uncontrolled replica migration, mutable `latest`
rollback target, applied-migration rewrite, or Railway-specific business logic
was accepted.

## Migration and rollback evidence

- The core image `migrate` and `run` launchers were exercised separately.
- Railway pre-deploy validated 22 Flyway migrations; shared staging remained at
  schema V22 and the runtime process performed no startup migration.
- Disposable environment `ce61d9f8-fcef-4758-9d0a-e6d8f700d62d` received an
  intentionally invalid disposable-only V23. Deployment
  `a5ae4489-ae2c-4d19-8bad-4e72b023ff44` failed before network/post-deploy and
  left the prior runtime active.
- Rollback deployment `41c3d581-7b2e-4afa-9d43-38bcffc96069` restored digest
  `sha256:6740d2fa95d58d04ebd5b04dcb17d6738b5ab4fc2f830200e73643da9ba55846`;
  Flyway reported V22 with no migration necessary and readiness passed.
- The disposable environment was deleted after evidence capture; shared staging
  history was not polluted by the injected migration.

## Browser and security acceptance

Two independent real browser contexts against the Railway HTTPS origin proved:

1. register, explicit login, session restoration after navigation, and
   server-backed logout;
2. legal-entity creation and active selection;
3. Deal create, list, detail, update, DRAFT cancellation, and closed mutation
   controls after cancellation;
4. direct SPA deep-link reload;
5. unrelated legal-entity access returning the non-disclosing “Deal bulunamadı”
   state for the exact Deal URL; and
6. same-origin API behavior through the public web edge.

Security spot checks passed: HTTPS `/healthz` returned 200 with `nosniff`, frame
denial and strict referrer policy; public `/actuator/health/readiness` returned
404; unauthenticated `/api/v1/auth/me` returned 401; and the final browser bundle
contained no Railway private hostname, Core service identifier, database
variable name, or private-origin variable name.

## Accepted boundary and operational note

RabbitMQ, object storage, FastAPI, and AI workers are outside Slice 7 staging
scope. RabbitMQ is not provisioned in this environment, so the core logs broker
connection retries while HTTP readiness remains healthy. This acceptance does
not claim asynchronous messaging, document storage, or AI behavior in staging.
