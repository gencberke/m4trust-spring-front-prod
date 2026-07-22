# Slice 15 P5 Implementation Review Handoff

Date: 23 July 2026
Result: `ACCEPT`
Release source: `main@23a4428ad76a5fdcf694dbca83104aca389e826d`
Environment: existing Railway `m4trust-staging` / `staging`

## Accepted deployment

- Web deployment `c115027a-06c8-4194-bf3b-1caf9a17ea92`, image digest
  `sha256:775a128b4694f9f8600a8b1836194cd94a5d1205779604387c818e7a6ba1ae78`.
- Core deployment `85be7459-d937-4b44-b8f2-d452116dcc3f`, image digest
  `sha256:85113ed82bf53df1e13c4f617c222dd8be13a67f66b2716030fa0f878f4c20a8`.
- MinIO deployment `bb59d5cc-3853-4ba4-9ccb-42170786d697`, pinned image digest
  `sha256:a1ea29fa28355559ef137d71fc570e508a214ec84ff8083e39bc5428980b015e`.
- Web and Core use the exact accepted source SHA. Flyway pre-deploy and Core startup
  passed. No production resource was changed in P5.

## Topology and storage

- Web is public; Core and PostgreSQL have no public domains.
- Only MinIO port 9000 is public for presigned S3 traffic. Console port 9001 is not
  public. The bucket is private and versioning is enabled.
- Exact staging-origin preflight and PUT passed with `ETag` and
  `x-amz-version-id` exposed. An unrelated origin did not receive CORS permission.
- The pinned MinIO community build does not implement bucket-level CORS; the exact
  origin is therefore configured at the dedicated MinIO service level. This remains
  an explicitly accepted controlled-demo risk.

## Live evidence

- `/healthz` returned `200`; public actuator and internal contract paths returned
  `404`.
- Real browser registration, protected-app entry, refresh/session retention and
  legal-entity creation/selection passed on the exact-main deployment.
- A separate three-session smoke passed registration, entity isolation, Deal and
  invitation participation, outsider non-disclosing `404`, direct MinIO document
  PUT, finalize, exact version equality, initiator/participant version-pinned
  downloads with SHA-256 equality, and logout followed by `/auth/me` `401`.
- No credentials, secrets, presigned URLs or object content are recorded.

## Review decision

The independent reviewer returned `ACCEPT` after repository, Railway, public-edge,
storage-boundary and configuration inspection. No migration, OpenAPI/generated
contract, AI-owned system, auth/session rule, authorization rule, idempotency rule or
optimistic-concurrency rule changed.

Live fulfillment-evidence smoke is not claimed: no authorized public path can create
a fresh `ACTIVE + FUNDED` Deal without excluded AI/payment infrastructure or a
forbidden seed/bypass. Accepted Slice 12 real-MinIO evidence remains the focused
implementation evidence. This is a deferred controlled-demo risk, not a P5 blocker.

The repository-wide final gate was intentionally not run in P5. It runs once in P6.
