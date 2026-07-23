# Plan 18B — Ratified Evidence Policy

- Status: ready — approved as a child of Plan 18 on 2026-07-23.
- Baseline: `main@693add7`.
- Parent: `18-fulfillment-and-closure-simplification.md`.
- Founder decision: preserve seller upload/buyer-ADMIN acceptance; add
  ratified `REQUIRED | NOT_REQUIRED`; historical behavior remains `REQUIRED`.

## 1. Purpose and user outcome

Allow both parties to agree during ratification whether delivery requires a
file. Required-evidence Deals keep the accepted flow unchanged. In a
no-evidence Deal, seller still starts fulfillment and buyer `ADMIN` explicitly
accepts delivery without a file; fulfillment completes but the Deal remains
ACTIVE until the existing simulated closure flow succeeds.

## 2. Scope and boundaries

In scope:

- Dated ADR-011 amendment and synchronized ADR index/FORBIDDEN wording.
- Ratification snapshot schema v3 with immutable `evidencePolicy`.
- Exact values: `REQUIRED`, `NOT_REQUIRED`.
- v1/v2 effective policy is permanently `REQUIRED`.
- v3 requires both `disputeWindowDays` and `evidencePolicy`.
- Persist effective policy on new/existing fulfillment rows.
- Buyer-ADMIN no-file acceptance for started `NOT_REQUIRED` fulfillment.
- Policy-aware backend actions, UI, audit, versioning and idempotency.
- Existing settlement eligibility accepts ratified schema v2 or v3 windows.

Out of scope:

- Buyer evidence upload, buyer MEMBER acceptance or seller self-acceptance.
- A no-file rejection workflow or seller “mark delivered” second action.
- Optional evidence upload in `NOT_REQUIRED`; that policy exposes no upload
  action.
- Multiple files/bundles, required type lists, counts or per-type validation.
- AI-selected policy or promotion of advisory `deliveryRequirements`.
- Any automatic Deal closure/release or provider/payment change.

## 3. Decisions and related ADRs

### ADR-011 amendment gate

18B-P1 records and accepts this narrow extension:

- The V1 seller-start/seller-upload/buyer-review actor model remains binding.
- Each ratified package has an effective evidence policy.
- v1/v2 packages are interpreted as `REQUIRED`, matching their original
  implemented behavior; this is compatibility, not invented consent.
- `REQUIRED` retains the exact evidence state machine and immutable-history
  rules.
- `NOT_REQUIRED` permits buyer `ADMIN` to complete a started fulfillment
  without an EvidenceSubmission.
- Start and no-file acceptance remain distinct explicit actions.
- No-file acceptance has no Deal, settlement, release, provider, casework or AI
  side effect.

Other binding decisions:

- ADR-009/010: package content is immutable and jointly ratified; a policy
  change creates a new package/version before ACTIVE.
- ADR-006: contract-first, stable errors, expected versions, backend actions
  and server idempotency.
- ADR-014: v3 carries the same contractual dispute window as v2; settlement is
  still explicit and simulated.
- ADR-013: after fulfillment start, existing dispute eligibility remains
  available; this plan adds no no-file reject state.

## 4. Public interface, state and data impact

### Ratification contract

- Add enum `EvidencePolicy = REQUIRED | NOT_REQUIRED`.
- Add `RatificationPackageSnapshotV3`, preserving v2 fields and requiring
  `evidencePolicy`.
- Package-create combinations:
  - neither field → schema v1, effective `REQUIRED`;
  - `disputeWindowDays` only → schema v2, effective `REQUIRED`;
  - `disputeWindowDays` + `evidencePolicy` → schema v3;
  - `evidencePolicy` without `disputeWindowDays` → field-level 422.
- v1/v2 serialized bytes and hashes must not change.
- v3 policy is a canonical snapshot/hash input and is shown to both parties
  before approval.

### Fulfillment contract

- Fulfillment detail/summary exposes required `evidencePolicy`.
- Add optional backend-derived `canAcceptWithoutEvidence` to both
  Deal-level and fulfillment-level available-action projections; absent means
  false for old clients.
- Add:

```text
POST /api/v1/deals/{dealId}/fulfillment/accept-without-evidence
```

- Request carries `expectedDealVersion` and `expectedFulfillmentVersion`;
  requires `Idempotency-Key`, session/CSRF and active legal-entity context.
- Success returns the updated fulfillment detail/resource projection.
- Stable failures distinguish forbidden actor, wrong policy, not-started or
  terminal state, evidence already present, stale versions and idempotency reuse.

### State and persistence

- New forward-only migration after V23:
  - allow ratification snapshot schema version 3;
  - add non-null fulfillment effective evidence policy;
  - backfill existing fulfillment rows to `REQUIRED`.
- Start copies the effective immutable source-package policy through a narrow
  ratification/deal projection; fulfillment does not read foreign repositories.
- `REQUIRED`:
  existing `IN_PROGRESS → EVIDENCE_REQUIRED → REVIEW_REQUIRED → COMPLETED`.
- `NOT_REQUIRED`:
  seller start creates `IN_PROGRESS`; buyer-ADMIN no-file acceptance moves
  directly to `COMPLETED` and writes immutable `completedAt`.
- No-file acceptance is invalid if any current/finalized submission exists.

## 5. Implementation phases

### 18B-P1 — Accept the ADR and contract delta

Outcome:
The new policy is authoritative and every wire/state decision is reviewable
before code.

Direction:
- Amend ADR-011, ADR-INDEX and FORBIDDEN without weakening finalized-history,
  actor or completion-side-effect rules.
- Update committed OpenAPI, validator closed sets, contract docs/changelog and
  generated frontend types as one review unit.
- Add v1/v2/v3 valid and expected-invalid fixtures, including deterministic
  hashes.

Depends on:
Founder decision recorded in this ready plan.

Exit checks:
- Contract validation passes.
- v1/v2 bytes/hashes are unchanged.
- No AI contract changes.

### 18B-P2 — Ratification v3 and compatibility

Outcome:
New packages can bind evidence policy without changing historical packages.

Direction:
- Implement v3 assembly/read/projection and strict input combinations.
- Update every schema-version guard in package assembly/read, the settlement
  ratification source projection and settlement eligibility to accept v2/v3
  `disputeWindowDays`; no single exact-v2 guard may leave v3 silently
  release-ineligible.
- Keep v1 release-ineligible and v2 behavior unchanged.
- Add the forward-only schema-version migration; do not edit V23.

Depends on:
18B-P1.

Exit checks:
- Canonical hash parity and v1/v2/v3 read/create tests pass.
- Both parties see the exact policy they approve.

### 18B-P3 — Policy-owned fulfillment behavior

Outcome:
Fulfillment enforces the ratified policy server-side.

Direction:
- Persist effective policy at start and backfill historical fulfillment to
  REQUIRED.
- Reuse existing Deal → fulfillment lock order, audit and HTTP idempotency
  infrastructure for no-file acceptance.
- Authorize buyer legal-entity `ADMIN` only.
- Require ACTIVE + FUNDED, started NOT_REQUIRED fulfillment, no evidence and
  current versions under lock.
- Complete milestone/fulfillment and write `completedAt` atomically; create no
  external event/dispatch.

Depends on:
18B-P2.

Exit checks:
- Actor/state/stale/idempotent/concurrent matrices pass.
- REQUIRED behavior is byte/API compatible apart from additive fields.

### 18B-P4 — Minimum policy UX

Outcome:
Users choose and understand the policy without extra workflow concepts.

Direction:
- Add one required evidence-policy control beside `disputeWindowDays` when
  creating a v3 package; default the control to `REQUIRED`.
- Show policy in package review/history and fulfillment summary.
- REQUIRED keeps existing upload/review UI.
- NOT_REQUIRED hides upload controls and shows buyer-only
  `Teslimatı kanıtsız kabul et`; seller/read-only copy clearly says buyer
  confirmation is awaited.
- All actions use generated types and backend flags.

Depends on:
18B-P3.

Exit checks:
- Frontend typecheck/build and focused role-state UI checks pass.

### 18B-P5 — Final focused gate

Outcome:
Both policies are proven without re-running unrelated acceptance loops.

Direction:
- Run contract, migration, ratification hash, fulfillment, settlement
  compatibility and frontend validation once.
- Submit for planner review before real-browser acceptance.

Depends on:
18B-P1–P4.

Exit checks:
- No forbidden auto-completion, actor widening or provider side effect exists.

## 6. Real-browser acceptance

1. Create/approve a v3 REQUIRED package. Seller upload/reject/replacement/accept
   behaves exactly as before.
2. Create/approve a v3 NOT_REQUIRED package. Both parties see the immutable
   policy before approval.
3. Seller starts fulfillment. Seller sees no upload action; buyer MEMBER and
   other participants see no acceptance action.
4. Buyer ADMIN explicitly accepts without evidence. Refresh/replay creates one
   completion, one `completedAt` and one audit result.
5. A stale second buyer tab receives 409 and refetches authoritative state.
6. Deal remains ACTIVE and moves to the separate Kapanış stage.
7. Existing window/dispute/release gates remain unchanged through simulated
   completion.
8. Existing v1/v2 Deal still follows REQUIRED and gains no no-file action.

## 7. Minimum invariants and validation

- v1/v2 canonical snapshots and hashes do not change.
- v3 requires both policy and dispute window; unknown policy fails closed.
- Historical package/fulfillment behavior resolves to REQUIRED.
- Seller-only upload and buyer-ADMIN-only acceptance remain enforced in the
  application layer.
- NOT_REQUIRED exposes no evidence upload action.
- No-file acceptance requires no evidence submission and current versions.
- Concurrent/replayed acceptance produces one terminal completion.
- `completedAt` is written once and remains valid for ADR-014 eligibility.
- Fulfillment completion leaves Deal ACTIVE and creates no release/provider/AI
  effect.
- Settlement accepts v2 and v3 contractual windows without making v1 eligible.
- Contract validator, focused Core tests, frontend typecheck/build and
  `git diff --check` pass.

## 8. Done definition

- [ ] ADR-011 amendment and derived docs accepted
- [ ] Reviewed OpenAPI/validator/changelog/generated-type unit complete
- [ ] Ratification v3 hash and compatibility accepted
- [ ] Forward-only migration accepted; V23 unchanged
- [ ] REQUIRED behavior unchanged and fully regressed
- [ ] NOT_REQUIRED no-file acceptance actor/state/race matrix passes
- [ ] Policy-aware frontend states pass
- [ ] Settlement compatibility and no-side-effect checks pass
- [ ] Real-browser acceptance passes
