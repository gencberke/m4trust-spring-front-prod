# Review Request

Task: Slice 15 P6 — Final Gate and Production-Demo Deployment
Revision: 1
Plan: `docs/plan/done/15-railway-demo-reconciliation-and-deployment.md`
Status: ACCEPTED
Branch: `codex/frontend-experience-redesign`
Release source: `main@23a4428ad76a5fdcf694dbca83104aca389e826d`
Claim: P6 only; target state is `RAILWAY_DEMO_READY`, not broad production

## Review order and scope

Read this request first, then ADR-022, `architecture-decisions/FORBIDDEN.md`,
`contracts/README.md`, the ready plan, and the real repository diff/state. Review
the evidence critically and independently. Do not mutate Railway or repository
state. Do not rerun any repository-wide suite: the one permitted P6 final gate has
already run. Use only small read-only checks if a claim needs verification.

Return exactly `ACCEPT`, `FIX`, or `REPLAN`, followed by prioritized findings and
the minimum supporting evidence.

## Accepted prerequisites

- P5 staging is independently accepted for the same exact main SHA; evidence is in
  `docs/plan/done/review/15p5-implementation-review-handoff.md`.
- The founder explicitly accepted the data-loss risk and waived native backup
  evidence only for this first controlled production demo because Railway requires
  Pro. The narrow waiver is recorded under ADR-022 §2.7 and has an independent
  `ACCEPT` review. It grants no backup/PITR/RPO/RTO or broad-production claim.
- Production persistent inventory was recorded before mutation. No service, volume,
  bucket, or database was deleted, detached, reset, purged, or seeded.

## One final repository gate

Run once on 23 July 2026:

- Contracts: 21 schemas and 13 fixtures passed, including expected-invalid cases.
- Core `mvn verify`: 383 tests, zero failures/errors/skips.
- Frontend clean install, typecheck, and production build passed (188 modules;
  448.03 kB output, 124.57 kB gzip).
- Core and Web local Docker builds passed; manifest digests were respectively
  `sha256:f506b9b478be8f503305c759431ac3ecc5e66c8426d6ac6f15a71f39e19b96dc`
  and `sha256:1eeabcb2f9d453ad77580fdc355dc45cff727c5b0ffeff20d35e5b0e4aba20d2`.
- `git diff --check` passed. The separately owned AI baseline remains honestly
  `UNVERIFIED_EXTERNAL_GATE`.
- `npm audit` reported two high-severity findings; no dependency upgrade or broad
  hardening claim is included in this controlled-demo acceptance.

## Existing production inventory and topology

- Railway project `m4trust-staging` (`134bb1e5-7b0b-478d-88e3-0872bf637d7c`),
  existing `production` environment (`e39496b7-39fd-4c22-8512-38d9ee76e74e`).
- PostgreSQL service `8f0b7dae-9082-4768-bc54-df6308eecb6f`; volume
  `33211e96-0c4a-4f1b-861f-5f8f1cdebd60`, `/var/lib/postgresql/data`, READY,
  approximately 135.08 MB. Deployment
  `27926f21-2fa7-4102-9ebe-835c0a81593a` is online.
- Existing MinIO service `f7d92661-8de2-4dc3-83d6-8da5fc07a16b`; volume
  `7aa9d7b1-fef3-429c-b0b8-efd51f17b728`, `/data`, READY, approximately
  207.40 MB. Current deployment `c9d882ab-3ec8-4ae4-9ca3-3bb435c88857` uses pinned
  `minio/minio:RELEASE.2025-04-22T22-12-26Z`, digest
  `sha256:a1ea29fa28355559ef137d71fc570e508a214ec84ff8083e39bc5428980b015e`.
- Web is public at `https://m4trust-web-edge-production.up.railway.app`. Core and
  PostgreSQL have no public domains. Only MinIO S3 port 9000 is public; no console
  domain exists.
- Production bucket `m4trust-production-documents` is private and versioning is
  enabled. Global MinIO CORS is restricted to the exact production Web origin; the
  pinned community build does not support bucket-level CORS.
- A production MinIO credential became visible in the authenticated operator UI
  while applying the CORS change. It was immediately replaced with a fresh secret;
  no secret value is recorded. Core was then redeployed to resolve the existing
  Railway service-variable references against the rotated credential.

## Exact deployment evidence

- Web deployment `f2f54d39-5b07-417b-809b-21d5a4187f05`, exact source SHA above,
  image digest
  `sha256:9311f3e19ce4fcfd7aac6c5d61527384437341a8cd16b53f1e5bb3fe182829e7`.
- Core initial deployment `1b368452-a045-4888-a780-b21ec6126fb5`, exact source SHA,
  image digest
  `sha256:1a5229a88eae79f02231060fb48ba856edd2f3e259af4c37e1460c00b2e9e887`.
- Flyway created the production schema history and successfully applied exactly 22
  migrations, ending at v22. No migration file was changed and no clean/reset ran.
- Core same-commit redeploy `93d62d3c-b722-4e5f-9158-0866fc65df95` rebuilt the same
  digest and passed `/actuator/health/readiness`. The previous Core deployment ID is
  the recorded rollback target; schema rollback is not claimed.
- Web current deployment and its digest are the recorded Web rollback target. A
  needless live rollback was not executed; Railway's same-commit redeploy path was
  exercised on Core after credential rotation.

## Production live smoke

- Public edge: `/healthz` 200; `/actuator/health/readiness` 404;
  `/internal/contracts` 404. The redesigned login UI rendered at `/login`.
- Controlled three-session run passed with Deal
  `c0260ac9-ccd0-4a69-81bb-f8010d28869f`, invitation
  `f04af765-6afc-4624-a5da-b5224e4cb738`, and document
  `d33e674d-0055-45ea-9489-0cab4cb5fea2`.
- Outsider Deal read returned 404. The invited recipient accepted and participant
  read returned 200.
- Upload intent returned 201; direct MinIO PUT returned 200; finalize returned 200
  with `AVAILABLE` and a non-empty immutable object version. Initiator and participant
  both obtained version-pinned download links; downloaded bytes matched the original
  SHA-256.
- Initiator logout returned 204, then `/auth/me` returned 401.
- One earlier throwaway attempt stopped safely at expected 422 because the test used
  an invalid media-type enum. The corrected run passed; no bypass or cleanup occurred.
- Fulfillment-evidence live smoke remains deferred because no legitimate public path
  was used to create `ACTIVE + FUNDED`; no seed, state override, AI/payment change, or
  authorization bypass was attempted.

## Boundaries and observations

- No AI-owned provider/model/worker/image/deployment was changed or enabled. Slice
  14B and V1–V22 remained untouched.
- Existing register/login/session, authorization, contracts, idempotency, and
  optimistic-concurrency behavior were preserved.
- Short-lived Hikari closed-connection validation warnings appeared after initial
  database/application startup. The later exact-commit Core redeploy, healthcheck,
  and full live smoke passed; no continuing functional failure was observed.
- Native backups remain unavailable under the accepted first-demo waiver. Data may
  be unrecoverable. This is the principal controlled-demo infrastructure risk.

## Independent review

Result: `ACCEPT` on 23 July 2026. The reviewer independently confirmed production
edge health and non-exposure, security headers, exact-origin MinIO CORS with no
allow-origin response for an unrelated origin, exact release identity, frozen V1–V22
migration history, private service topology, successful three-session storage and
authorization smoke, the same-commit Core redeploy path, and the narrow ADR-022
backup waiver. No P6 blocker or required fix remains.
