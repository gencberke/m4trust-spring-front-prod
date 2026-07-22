# Slice 15 P6 Implementation Review Handoff

Date: 23 July 2026
Result: `ACCEPT`
State: `RAILWAY_DEMO_READY`
Release source: `main@23a4428ad76a5fdcf694dbca83104aca389e826d`
Environment: existing Railway `m4trust-staging` / `production`

## Final repository gate

- Contract validation passed 21 schemas and 13 fixtures, including expected-invalid
  cases. The separately owned AI baseline remains `UNVERIFIED_EXTERNAL_GATE`.
- Core `mvn verify` passed 383 tests with zero failures/errors/skips.
- Frontend clean install, typecheck and production build passed.
- Core/Web Docker builds and `git diff --check` passed. The final gate ran once.

## Accepted production deployment

- Public Web URL: `https://m4trust-web-edge-production.up.railway.app`.
- Web deployment `f2f54d39-5b07-417b-809b-21d5a4187f05`, image digest
  `sha256:9311f3e19ce4fcfd7aac6c5d61527384437341a8cd16b53f1e5bb3fe182829e7`.
- Core initial deployment `1b368452-a045-4888-a780-b21ec6126fb5`; accepted
  same-commit redeploy `93d62d3c-b722-4e5f-9158-0866fc65df95`, image digest
  `sha256:1a5229a88eae79f02231060fb48ba856edd2f3e259af4c37e1460c00b2e9e887`.
- PostgreSQL deployment `27926f21-2fa7-4102-9ebe-835c0a81593a`; Flyway applied
  exactly V1–V22 and ended at v22.
- MinIO deployment `c9d882ab-3ec8-4ae4-9ca3-3bb435c88857`, pinned image digest
  `sha256:a1ea29fa28355559ef137d71fc570e508a214ec84ff8083e39bc5428980b015e`.
- Web is public; Core/PostgreSQL are private. Only MinIO S3 port 9000 is public;
  no console domain exists. The production bucket is private and versioned.

## Persistent-resource and security evidence

- Existing PostgreSQL volume `33211e96-0c4a-4f1b-861f-5f8f1cdebd60` and MinIO
  volume `7aa9d7b1-fef3-429c-b0b8-efd51f17b728` remained attached and were not
  deleted, reset, detached, purged or seeded.
- Railway native backups require Pro and both volumes reported `No Backups`. The
  founder accepted the data-loss risk under the narrow first-demo ADR-022 §2.7
  waiver. No PITR/RPO/RTO or broad-production claim is made.
- MinIO exact-origin preflight returned 204 with the production Web origin and PUT
  headers/method; an unrelated origin received no allow-origin header.
- A production MinIO operator credential exposed during authenticated UI inspection
  was immediately rotated. Core was redeployed through the existing Railway variable
  references; no secret value is recorded.

## Live smoke

- `/healthz` returned 200; public actuator readiness and internal contract paths
  returned 404. The redesigned login UI rendered.
- Three throwaway sessions passed registration, entities, Deal invitation and
  acceptance, outsider 404, participant 200, document intent 201, direct PUT 200,
  finalize 200/AVAILABLE, exact immutable object version, initiator and participant
  version-pinned downloads with matching SHA-256, logout 204 and `/auth/me` 401.
- Safe identifiers: Deal `c0260ac9-ccd0-4a69-81bb-f8010d28869f`, invitation
  `f04af765-6afc-4624-a5da-b5224e4cb738`, document
  `d33e674d-0055-45ea-9489-0cab4cb5fea2`.
- Fulfillment-evidence live smoke remains deferred because no legitimate public
  `ACTIVE + FUNDED` creation path was used. No seed, bypass, state override, AI or
  payment change was attempted.

## Review decision and residual risks

The independent reviewer returned `ACCEPT` after checking release integrity,
migration freeze, topology, public-edge security, exact-origin CORS, persistent
inventory, live authorization/storage evidence, same-commit redeploy and the backup
waiver. No P6 blocker remains.

Residual controlled-demo risks are explicit: first-demo data may be unrecoverable;
fulfillment-evidence live smoke is deferred; MinIO uses service-level exact-origin
CORS; the AI external gate is unverified; and `npm audit` reports two high findings
outside this controlled-demo hardening scope. The result is not broad-production,
AI-readiness, malware-safety, real-money or settlement readiness.
