# Slice 12 Acceptance Record — 20 July 2026

## Result

Slice 12 Fulfillment and Evidence is accepted on
`codex/slice-12-fulfillment` at reviewed HEAD
`a21a75ecd9fd21c93f001527f9343049e7ed0ef0`, plus the planner review fixes
present in the working tree.

Deployment, Railway, real payment-provider integration, release, payout,
refund, settlement, dispute, and AI/video analysis remain deferred.

## Planner review corrections

The implementation review corrected:

- exact status-specific evidence response shapes, including pending `expiresAt`;
- stable conflict Problem Details codes and frontend recovery mapping;
- seller-entity start authorization and participant-readable fulfillment;
- Deal → fulfillment → milestone lock order and revalidation;
- same-Deal composite database integrity, evidence lifecycle constraints, and
  immutable storage identity;
- media-type verification failure handling and focused regression coverage.

Real-browser acceptance then found one additional frontend defect: the first
evidence finalize request did not initialize its `Idempotency-Key`; the key was
created only by the retry path. The file-selection path now initializes the key
before hashing/upload. The complete critical path was rerun successfully after
the fix.

## Real-browser acceptance

The user requested the minimum browser scope. Acceptance used an isolated
PostgreSQL database, the local sandbox payment adapter, real MinIO direct PUT,
and sequential authenticated seller ADMIN and buyer ADMIN sessions:

1. Buyer and seller ratified the same immutable package through the visible UI.
2. Buyer created and funded the funding plan through the visible UI; lifecycle
   advanced to `FULFILLMENT`.
3. Seller started fulfillment through the visible UI.
4. Seller selected a PDF; the browser calculated SHA-256, created an upload
   intent, uploaded directly to MinIO, and finalized it.
5. Buyer saw `REVIEW_REQUIRED` and rejected the submitted evidence with a
   reason.
6. Seller saw `EVIDENCE_REQUIRED`, the preserved rejected history, and uploaded
   a replacement through the same direct-storage path.
7. Buyer accepted the replacement.
8. Fulfillment and the primary milestone became `COMPLETED`; the rejected record
   remained in history. Deal remained `ACTIVE` with lifecycle `FULFILLMENT`.
9. No release, settlement, refund, provider-payment, dispute, or AI side effect
   was exposed or created by fulfillment completion.

The ready plan's additional seller MEMBER, buyer MEMBER, participant,
stale-version, replay, and terminal-race cases were not repeated in browsers at
the user's direction. Their server-side invariants are covered by the accepted
automated integration suite. This is recorded as the plan's material
acceptance deviation.

## Automated validation

- Contract validator — PASS.
- Implementer full Core API verify — PASS: 250 tests, 0 failures, 0 errors.
- Planner targeted Core API tests
  (`FulfillmentServiceTest`, `FulfillmentIntegrationTest`,
  `FulfillmentMigrationIntegrationTest`) — PASS: 17 tests.
- Frontend typecheck and production build — PASS.
- Compose configuration — PASS.
- `git diff --check` — PASS, with line-ending warnings only.

## Accepted boundaries

- V20 is the accepted Slice 12 migration; future changes use a new forward-only
  migration.
- Evidence objects retain immutable object keys and versions after finalize.
- Rejected and accepted evidence history is append-only.
- Fulfillment completion does not complete the Deal and cannot release money.
- Lifecycle and action availability remain backend-derived and fail closed.
