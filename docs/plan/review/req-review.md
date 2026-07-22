# Review Request

Task: Slice 15 P5 — Existing Railway Staging Reconciliation
Revision: 1
Plan: `docs/plan/ready/15-railway-demo-reconciliation-and-deployment.md`
Status: ACCEPTED
Branch: `codex/frontend-experience-redesign`
Accepted release source: `main@23a4428ad76a5fdcf694dbca83104aca389e826d`
Plan completion claim: P5 only

## Review order and scope

Read this request first, then ADR-022, `architecture-decisions/FORBIDDEN.md`,
`contracts/README.md`, the ready plan, and the actual repository/source state. Review
the real Railway staging state and the evidence below. Do not run repository-wide
suites; those are reserved for the one P6 final gate. Do not mutate Railway.

Return exactly `ACCEPT`, `FIX`, or `REPLAN`, followed by prioritized findings and the
minimum evidence needed to support them.

## Accepted prerequisites

- Slice 15 P4 is accepted at `381ed5b`.
- The user-owned frontend redesign is accepted at `fbcbb7f`, with planner state at
  `a31a18c`.
- PR 47 merged the accepted release unit to `main` at exact SHA
  `23a4428ad76a5fdcf694dbca83104aca389e826d`.
- The 2026-07-23 evidence reconciliation is accepted at `9347d03`: live document
  storage remains mandatory; live fulfillment-evidence smoke is deferred when no
  authorized public path can create `ACTIVE + FUNDED`. Seed, bypass, admin/demo
  endpoints, authorization relaxation, AI/payment changes, and sandbox/emulator use
  in Railway remain forbidden.

## Deployment evidence

Existing Railway project/environment only:

- Project: `m4trust-staging` (`134bb1e5-7b0b-478d-88e3-0872bf637d7c`)
- Environment: `staging` (`254e8bf3-562a-40fa-a4e4-7a302fd01289`)
- Public web URL: `https://m4trust-web-edge-staging.up.railway.app`
- Web deployment: `c115027a-06c8-4194-bf3b-1caf9a17ea92`, `SUCCESS`, source
  `23a4428ad76a5fdcf694dbca83104aca389e826d`, image digest
  `sha256:775a128b4694f9f8600a8b1836194cd94a5d1205779604387c818e7a6ba1ae78`
- Core deployment: `85be7459-d937-4b44-b8f2-d452116dcc3f`, `SUCCESS`, source
  `23a4428ad76a5fdcf694dbca83104aca389e826d`, image digest
  `sha256:85113ed82bf53df1e13c4f617c222dd8be13a67f66b2716030fa0f878f4c20a8`
- MinIO deployment: `bb59d5cc-3853-4ba4-9ccb-42170786d697`, `SUCCESS`, immutable image
  `minio/minio:RELEASE.2025-04-22T22-12-26Z`, digest
  `sha256:a1ea29fa28355559ef137d71fc570e508a214ec84ff8083e39bc5428980b015e`
- Staging MinIO volume: `61dc0c74-60a6-4238-aabf-4d38aafd348c`, mounted at `/data`.
- Required Web/Core release identity variables exactly match the accepted SHA.
- Core pre-deploy Flyway migration completed successfully and the runtime started
  with PostgreSQL under the staging profile.

Topology and storage:

- Web is public. Core and PostgreSQL have no public domains.
- Only the MinIO S3 API port is public; the MinIO console has no public domain.
- Bucket `m4trust-staging-documents` is private and versioning is enabled.
- CORS preflight returned 204 for the exact staging web origin, required PUT method
  and headers; actual PUT exposed `ETag` and `x-amz-version-id`.
- The pinned MinIO community server rejected bucket-level CORS as unsupported, so
  CORS is restricted through the dedicated service's global
  `MINIO_API_CORS_ALLOW_ORIGIN`. This is the main residual infrastructure risk.

## Live acceptance evidence

- Web `/healthz` returned `200` after the exact-main deployment; public
  `/actuator/health/readiness` and `/internal/contracts` both returned `404`.
- Real browser registration entered the redesigned protected application; refresh
  retained the authenticated session.
- A focused three-session API/browser smoke passed: fresh registrations and legal
  entities, Deal creation and invitation, outsider Deal read `404`, recipient
  acceptance and participant read `200`, document upload intent, direct MinIO PUT
  `200`, finalize `200`, exact finalized object-version equality, initiator and
  participant version-pinned downloads `200` with SHA-256 equality, logout `204`,
  then `/auth/me` `401`. Safe document identifier:
  `99323a62-2bfb-43ef-90f2-2e3536224be1`. No credentials, secrets, presigned URLs,
  or object content are recorded here.
- There is no authorized public path to create a fresh `ACTIVE + FUNDED` Deal without
  AI/payment infrastructure, a seed/bypass, or a new privileged surface. Live Railway
  fulfillment-evidence smoke is therefore not claimed. Accepted Slice 12 real-MinIO
  focused evidence remains the implementation evidence for that behavior.

## Boundaries and residual risk

- No production environment or persistent production resource was mutated in P5.
- No AI-owned provider/model/worker/image/deployment was changed or enabled.
- No migration, public contract, auth/session behavior, authorization rule,
  idempotency rule, or optimistic-concurrency rule was changed.
- Open registration is an explicit controlled-demo exception, not a broad-production
  readiness claim.
- The one full contract/Core/frontend/image gate has not run yet; it is reserved for
  P6 after P5 acceptance.

## Independent review

Result: `ACCEPT` on 23 July 2026. The reviewer independently confirmed the exact
Web/Core source SHA and image digests, public/private topology, exact-origin MinIO
CORS including unrelated-origin rejection, release identity, safe configuration
defaults, frozen migration/contract boundaries, and the honesty of the deferred
fulfillment-evidence claim. No P5 blocker or required fix remains.
