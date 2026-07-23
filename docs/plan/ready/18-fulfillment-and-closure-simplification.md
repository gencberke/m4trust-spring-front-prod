# Plan 18 — Fulfillment and Closure Simplification

- Status: ready — founder/user approved on 2026-07-23 through the explicit
  request to write the minimum-complexity master/sub-plans under `ready/` and
  the recorded questionnaire decisions below.
- Baseline: `main@693add7` (`Plan 17 A–B4` merged; its B5 staging acceptance is
  still a separate prerequisite/debt and is not silently claimed here).
- Child plans:
  - `18a-deal-scoped-closure-workspace.md`
  - `18b-ratified-evidence-policy.md`
  - `18c-pending-evidence-cancellation.md`
- Authority: ADR-003, ADR-006, ADR-009, ADR-010, ADR-011, ADR-013, ADR-014,
  `FORBIDDEN.md`, and Plan 17 remain binding except for the explicit ADR-011
  amendment authorized by the founder decision recorded in §3.

## 1. Purpose and user outcome

Make delivery and Deal closure visibly separate, while keeping the implementation
small and preserving the accepted safety model:

- seller starts fulfillment and submits evidence when evidence is required;
- buyer `ADMIN` remains the only fulfillment acceptance authority;
- parties choose `REQUIRED` or `NOT_REQUIRED` evidence while ratifying the
  immutable package, not during delivery;
- `NOT_REQUIRED` permits an explicit buyer-ADMIN acceptance without a file;
- the Deal-scoped workspace gains a distinct `Kapanış` stage;
- pending uploads can be cancelled and replaced without weakening immutable
  submitted/accepted/rejected history;
- Deal closure still requires the existing explicit, backend-gated simulated
  release and query-verified `SIMULATED_SETTLED`.

The user should never need to infer whether delivery completion and Deal closure
are the same event: the UI, lifecycle projection, actions and audit keep them
separate.

## 2. Scope and boundaries

In scope:

- A sixth Deal-scoped workspace area named `Kapanış`; no global application-nav
  destination.
- Central backend lifecycle projection of `SETTLEMENT` after fulfillment
  completion and before Deal completion.
- A ratified evidence policy with exact values `REQUIRED` and `NOT_REQUIRED`.
- Backward-compatible ratification snapshot schema v3.
- Existing v1/v2 packages and existing fulfillment rows retain effective
  `REQUIRED` behavior.
- Buyer-ADMIN explicit no-file acceptance for `NOT_REQUIRED` fulfillment.
- Seller cancellation of an unfinished `PENDING_UPLOAD`, followed by a fresh
  upload.
- Existing reject → immutable replacement flow remains the only replacement
  path after finalize.
- Contract-first API/type changes, forward-only migrations, focused backend and
  frontend implementation, and real-browser acceptance.

Out of scope:

- Buyer-uploaded delivery evidence or widening upload authority beyond seller
  `ADMIN`/`MEMBER`.
- Buyer `MEMBER` acceptance.
- Automatic completion on upload/finalize, automatic release, or proof-only
  Deal closure.
- Parallel evidence files, evidence bundles, multiple current submissions,
  multiple milestones, partial acceptance, or per-file review.
- Deleting/overwriting `SUBMITTED`, `ACCEPTED`, or `REJECTED` evidence or its
  immutable object version.
- Storage-object deletion during pending cancellation; orphan cleanup remains a
  bounded later capability.
- Real Moka/provider, payment, custody, payout, refund, reversal, production
  simulation or AI changes.

## 3. Decisions and related ADRs

Founder decisions recorded 2026-07-23:

1. Keep the accepted actor split: seller starts/uploads; buyer `ADMIN`
   accepts/rejects.
2. Evidence policy is selected in the ratification package as
   `REQUIRED | NOT_REQUIRED`.
3. Existing package schemas v1/v2 and existing fulfillment data keep effective
   `REQUIRED`; no migration invents a less strict historical agreement.
4. `NOT_REQUIRED` uses explicit buyer-ADMIN no-file acceptance after seller
   start. It does not auto-complete on start and has no no-file reject action.
5. Keep one-current-file V1. True multi-file bundles are deferred.
6. Only unfinished pending upload attempts may be cancelled. Finalized history
   remains immutable.
7. `Kapanış` is Deal-scoped, not global navigation.

ADR effect:

- 18B-P1 records a dated ADR-011 amendment for the two evidence policies while
  preserving ADR-011’s seller/buyer actor model, immutable history, manual
  acceptance and no-side-effect completion boundary.
- ADR-014 is unchanged: fulfillment completion only creates potential release
  eligibility. `canRequestRelease` remains backend-derived and Deal
  `ACTIVE -> COMPLETED` remains exclusive to query-verified
  `SIMULATED_SETTLED`.
- ADR-003 lifecycle authority remains centralized in Spring; the frontend never
  derives `SETTLEMENT` or action availability from raw status combinations.
- AI `deliveryRequirements` remain advisory and cannot select evidence policy
  or required evidence type.

## 4. Public interface, state and data impact

Public contract changes are additive within `/api/v1`:

- Ratification schema v3 adds required `evidencePolicy` alongside the existing
  required v3 `disputeWindowDays`; v1/v2 stay readable.
- Package-create input accepts `evidencePolicy` only with
  `disputeWindowDays`; that pair creates v3. Existing input combinations retain
  v1/v2 behavior.
- Fulfillment projections expose effective evidence policy and backend-derived
  `canAcceptWithoutEvidence`.
- A buyer-ADMIN idempotent no-file acceptance action carries current Deal and
  fulfillment versions.
- Pending evidence exposes cancellation availability and cancellation metadata;
  a seller-only idempotent cancel-upload action carries the pending evidence
  version.

State:

- `REQUIRED`: current ADR-011 flow is unchanged.
- `NOT_REQUIRED`: seller start leaves fulfillment `IN_PROGRESS`; buyer `ADMIN`
  may explicitly complete it without evidence. Completion writes the same
  immutable `completedAt` used by settlement eligibility.
- Pending upload cancellation leaves finalized-state enums unchanged and makes
  the cancelled intent non-current so a new intent can be created.
- Lifecycle priority becomes:
  terminal Deal → actor-visible active dispute → fulfillment completed
  (`SETTLEMENT`) → funded fulfillment (`FULFILLMENT`) → earlier stages.

Persistence:

- Use only migrations after the merged V23 baseline; Plan 17 B5/plan acceptance
  remains separate and this wording does not pre-accept it.
- Permit ratification snapshot schema v3.
- Persist effective evidence policy with fulfillment; historical rows backfill
  to `REQUIRED`.
- Pending cancellation metadata is append/audit preserving; no finalized
  evidence row or object identity is removed.

## 5. Implementation phases

### 18-P1 — Separate the Deal-scoped closure workspace

Outcome:
`Teslimat` and `Kapanış` are separate Deal stages and Spring emits the matching
authoritative lifecycle.

Direction:
- Execute child plan `18a-deal-scoped-closure-workspace.md`.
- Reuse the existing settlement API, panel and backend actions.
- Do not change settlement/release semantics.

Depends on:
Plan 17 A–B4 merged.

Exit checks:
- Fulfillment-completed ACTIVE Deals project `SETTLEMENT`.
- The separate tab works without frontend lifecycle invention.

### 18-P2 — Ratify and enforce evidence policy

Outcome:
Both parties ratify whether evidence is required; required flows remain
unchanged and no-file flows end only through explicit buyer-ADMIN acceptance.

Direction:
- Execute child plan `18b-ratified-evidence-policy.md`.
- Complete its ADR/contract gate before persistence or behavior changes.

Depends on:
18-P1 may run independently; 18B phases remain internally ordered.

Exit checks:
- v1/v2 compatibility and v3 canonical hashing pass.
- Actor, state, concurrency and no-side-effect boundaries pass.

### 18-P3 — Cancel unfinished upload attempts

Outcome:
A seller can abandon a pending intent and immediately start a fresh upload,
without deleting immutable history.

Direction:
- Execute child plan `18c-pending-evidence-cancellation.md`.
- Reuse existing upload, history, stale-version and idempotency patterns.

Depends on:
18-P2 contract/migration baseline.

Exit checks:
- Cancelled pending intent is non-current; finalized evidence cannot be
  cancelled or deleted.

### 18-P4 — Combined acceptance and closure regression

Outcome:
Required and no-file Deals both reach fulfillment completion safely, then use
the unchanged simulated closure stage.

Direction:
- Run the combined browser and validation matrix in §6–§7 once.
- Do not repeat repository-wide suites after every child phase.

Depends on:
18-P1–18-P3 and Plan 17 B5 acceptance or an explicitly combined B5/18-P4
staging gate approved by the planner.

Exit checks:
- Both policy paths preserve delivery/closure separation.
- Production remains untouched and payment/provider boundaries remain inert.

## 6. Real-browser acceptance

Planner-owned, with seller `MEMBER` or `ADMIN`, buyer `ADMIN`, and one read-only
participant:

1. Existing v2 package follows `REQUIRED`: seller starts, uploads, buyer rejects,
   seller replaces, buyer accepts; fulfillment completes and Deal remains ACTIVE.
2. A v3 `REQUIRED` package behaves identically.
3. A v3 `NOT_REQUIRED` package is visibly ratified by both parties; seller
   starts, sees no upload action, buyer `ADMIN` sees no-file acceptance, and
   other actors cannot accept.
4. Buyer no-file acceptance completes fulfillment once, records `completedAt`,
   and leaves the Deal ACTIVE.
5. The workspace moves from `Teslimat` to `Kapanış`; the settlement summary and
   simulated non-claim label remain visible.
6. Existing backend release gates still block wrong actor, unelapsed window and
   active dispute; proof presence alone never enables closure.
7. In a REQUIRED Deal, seller creates a pending upload, cancels it, creates a
   new intent and finalizes successfully.
8. Forced cancellation of SUBMITTED/ACCEPTED/REJECTED evidence fails and history
   remains downloadable and unchanged.
9. Eligible simulated release reaches query-verified `SIMULATED_SETTLED` and
   only then changes the Deal to COMPLETED.

## 7. Minimum invariants and validation

- Contract validator and generated-type drift checks cover v1/v2/v3,
  action/error closed sets and new endpoints.
- JCS/hash fixtures prove v1/v2 unchanged and deterministic v3 hashing.
- Forward migration applies on clean databases and on the merged V23 database
  baseline.
- Existing fulfillment rows and packages resolve to `REQUIRED`.
- Seller remains the only upload actor; buyer `ADMIN` remains the only
  acceptance actor.
- REQUIRED cannot complete without accepted current evidence.
- NOT_REQUIRED cannot use the no-file action before seller start and cannot
  create evidence upload actions.
- No-file acceptance is versioned, idempotent, audited and race-safe.
- Fulfillment completion creates no Deal completion, release, provider, refund,
  AI or casework side effect.
- Lifecycle and all actions are backend-derived.
- Pending cancel cannot mutate or delete finalized evidence.
- Core verify, frontend typecheck/build and `git diff --check` pass once at the
  final combined gate.

## 8. Done definition

- [ ] ADR-011 amendment, ADR index and FORBIDDEN synchronization accepted
- [ ] 18A Deal-scoped closure workspace accepted
- [ ] Ratification v3 contract/hash/compatibility accepted
- [ ] REQUIRED and NOT_REQUIRED backend behavior accepted
- [ ] Evidence-policy UI and role-specific turn copy accepted
- [ ] Pending cancellation and replacement UX accepted
- [ ] All actor, state, idempotency, race and immutable-history invariants pass
- [ ] Combined real-browser matrix passes
- [ ] Plan 17 B5 is independently accepted or explicitly covered by the same
      accepted staging evidence
- [ ] No Moka/real-money/production/AI scope entered
- [ ] All child plans and this master plan are archived together only after
      every assigned phase and acceptance item is proven
