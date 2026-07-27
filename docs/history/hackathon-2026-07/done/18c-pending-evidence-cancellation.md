# Plan 18C — Pending Evidence Cancellation

- Status: done — accepted on main 2026-07-24. Implementation merged at
  `main@47f3d2a` (Plan 18 integration). Child of Plan 18; no further
  implementation authority.
- Baseline: Plan 18B accepted implementation base.
- Parent: `18-fulfillment-and-closure-simplification.md`.
- Scope type: narrow recovery improvement for unfinished upload intents; true
  multi-file evidence remains deferred.

## 1. Purpose and user outcome

Let a seller abandon a wrong, failed or unwanted pending upload and immediately
start a fresh one. Cancellation must not delete business history, mutate a
finalized object reference or imply support for multiple simultaneous evidence
files.

## 2. Scope and boundaries

In scope:

- Seller-only cancellation of a current, unfinalized `PENDING_UPLOAD`.
- Preserve the row with explicit cancellation metadata and audit.
- Make the cancelled pending intent non-current and non-finalizable.
- Allow one fresh upload intent immediately after cancellation.
- Wire the existing UI `Vazgeç`/failed-upload recovery to server cancellation
  when a pending intent exists.
- Preserve and clarify existing reject → replacement UX.

Out of scope:

- Cancelling/deleting `SUBMITTED`, `ACCEPTED`, or `REJECTED` evidence.
- Deleting an uploaded storage object synchronously.
- Background orphan retention/cleanup.
- Multiple current pending/submitted files, bundles or multi-select UI.
- Changing evidence policy, uploader role, buyer review or completion.
- Generic DELETE endpoints or soft-delete flags.

## 3. Decisions and related ADRs

- ADR-011 immutable-history rules remain absolute after finalize.
- A pending upload is an incomplete intent, not accepted/rejected evidence;
  cancellation is an explicit domain action, not generic delete.
- This founder-approved ready decision treats non-null `cancelledAt` on
  `PENDING_UPLOAD` as the explicit terminal state of the upload intent. It is
  not ADR-003’s forbidden generic soft-delete flag: the row remains readable,
  cannot be restored or finalized, and no submitted business evidence is
  hidden or deleted. 18C-P1 records this clarification in ADR-011 and its
  derived index/FORBIDDEN surfaces.
- ADR-003/006 require audit, optimistic versioning, stable errors and
  idempotency for the risky state mutation.
- Storage calls do not occur in the cancellation transaction. Any bytes already
  uploaded become bounded orphan-cleanup input.
- Existing status enum remains unchanged. `PENDING_UPLOAD` plus non-null
  `cancelledAt` means cancelled and can never become current/finalized.
- One-current-file cardinality remains unchanged.

## 4. Public interface, state and data impact

Additive public action:

```text
POST /api/v1/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/cancel-upload
```

- Seller legal-entity `ADMIN` or `MEMBER` only.
- Request carries `expectedEvidenceVersion`.
- Requires `Idempotency-Key`, session/CSRF and active entity context.
- Same key/request replays the same cancelled projection; reused key with a
  different request fails with the existing idempotency error.
- Stable failures distinguish hidden resource, forbidden actor, expired/already
  cancelled, finalized/wrong state and stale evidence version.

Projection additions:

- `PendingEvidenceSubmission.cancelledAt` is nullable.
- `EvidenceAvailableActions.canCancelUpload` is an optional additive property
  on the existing shared action schema; absent means false, and the backend
  returns true only for an active pending projection.
- Cancelled pending evidence is returned in history but never as
  `currentEvidence`.

Persistence:

- Next forward-only migration after Plan 18B adds nullable `cancelled_at` and
  consistency checks; accepted migrations remain frozen.
- Cancellation transaction writes `cancelledAt`, optimistic version, audit and
  HTTP idempotency result atomically.
- Finalize rechecks `cancelledAt IS NULL` under lock.
- No object-storage delete or external call occurs.

## 5. Implementation phases

### 18C-P1 — Contract and pending-state extension

Outcome:
Cancellation behavior is fixed in the committed contract before code.

Direction:
- Record the accepted pending-cancellation clarification in ADR-011 and keep
  ADR-INDEX/FORBIDDEN synchronized.
- Add endpoint, request, projection/action fields, stable error expectations,
  validator rules, contract docs/changelog and generated types together.
- Do not add a finalized evidence status or DELETE operation.

Depends on:
Plan 18B accepted contract baseline.

Exit checks:
- Contract validation and generated-type checks pass.

### 18C-P2 — Atomic cancellation behavior

Outcome:
The current pending intent can be cancelled once and cannot later finalize.

Direction:
- Add forward-only persistence and seller-authorized action through the
  fulfillment module.
- Lock Deal/fulfillment/milestone/pending evidence consistently with finalize.
- Recheck current pointer/state/version under lock.
- Record cancellation/audit/idempotency atomically and leave storage untouched.

Depends on:
18C-P1.

Exit checks:
- Cancel-vs-finalize race has one winner.
- Same-key replay is stable; stale/different-key conflicts are deterministic.

### 18C-P3 — Minimal recovery UX

Outcome:
`Vazgeç` actually releases the server-side pending slot and replacement is
obvious.

Direction:
- If an intent exists, `Vazgeç` calls cancel before resetting local upload
  state; before intent creation it remains a local reset.
- Show cancelled entries distinctly in the evidence timeline.
- After rejection, retain existing “new upload expected” guidance and
  immutable rejected entry.
- Never show cancel on finalized entries or to non-seller actors.

Depends on:
18C-P2.

Exit checks:
- Failed/abandoned upload can be replaced without waiting for expiry.
- Frontend actions remain backend-derived.

### 18C-P4 — Focused validation

Outcome:
Recovery works without weakening evidence integrity.

Direction:
- Run contract, migration, cancel/finalize race, authorization, immutable
  history, frontend typecheck/build and focused browser checks.

Depends on:
18C-P1–P3.

Exit checks:
- No finalized object/row delete or mutation path exists.

## 6. Real-browser acceptance

1. In a REQUIRED Deal, seller starts and creates an upload intent.
2. Seller chooses `Vazgeç`; the pending entry shows cancelled and a fresh
   upload control becomes available immediately.
3. Seller uploads/finalizes the replacement; buyer sees only that submission as
   current and sees the cancelled attempt in history.
4. Buyer/other participant cannot cancel; forced calls return the accepted
   authorization response.
5. A cancel/finalize race produces exactly one winner. If cancel wins, finalize
   cannot submit; if finalize wins, cancellation cannot mutate it.
6. Buyer rejects finalized evidence; seller replacement still uses the
   existing immutable-history path.
7. Accepted/rejected/download history remains unchanged and no generic delete
   surface exists.

## 7. Minimum invariants and validation

- Only seller entity ADMIN/MEMBER can cancel a visible current pending intent.
- Cancellation requires current evidence version and server idempotency.
- Cancelled/expired pending intent is never current or finalizable.
- One fresh intent is allowed after cancellation; concurrent attempts still
  obey one-current cardinality.
- Finalized evidence status, object key/version, checksum and history are
  immutable.
- No storage call runs in the transaction; no synchronous object deletion.
- Audit/idempotency/cancellation commit atomically.
- Contract validator, migration tests, focused Core tests, frontend
  typecheck/build and `git diff --check` pass.

## 8. Done definition

- [ ] Additive cancel-upload contract review unit accepted
- [ ] Forward-only cancellation metadata migration accepted
- [ ] Seller authorization, stale, replay and cancel/finalize races pass
- [ ] Cancelled intent immediately permits a replacement
- [ ] Existing reject/replacement flow remains unchanged
- [ ] Finalized evidence deletion/overwrite remains unreachable
- [ ] Minimal UI recovery and timeline states pass
- [ ] Real-browser acceptance passes
