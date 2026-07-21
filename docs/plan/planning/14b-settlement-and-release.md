# Slice 14B — Settlement and Release

- Status: planning — gated future work; not implementation-authorizing
- Draft date: 21 July 2026
- Current repository baseline:
  `main@0282c0e103a2fd3c0cacd32b11cb639c098b803c`
- Required predecessors:
  - accepted Slice 14A Dispute and Casework Foundation;
  - accepted Slice 7 staging deployment;
  - accepted Slice 11B real-provider integration; and
  - accepted provider, legal, operational, and ratification-contract decisions.
- Future decision: ADR-014 Settlement and Release must be created from accepted
  gate evidence before this plan can become decision-complete or move to
  `ready/`.
- Approval boundary: no implementer task packet may be produced from this plan
  while any G1–G4 gate remains unresolved.
- Deployment/payment boundary: planning is reopened, but implementation,
  credentials, staging operations, provider calls, and real money movement are
  not authorized by this document.

## 1. Goal

After provider-verified funding and fulfillment completion, allow the buyer
legal entity `ADMIN` to explicitly initiate release after an immutable,
ratified dispute window has expired.

An active dispute blocks release before dispatch. A release already dispatched
when a dispute opens is handled through query-first provider reconciliation and
is never assumed cancelled. Only provider-verified final settlement may change
the Deal from `ACTIVE` to `COMPLETED`.

This document plans the complete dependency, decision, implementation,
validation, and acceptance sequence. It deliberately remains gated because the
provider, legal, operational, and contract facts required to specify safe money
movement do not yet exist in accepted project state.

## 2. Current State and Accepted Inputs

- ADR-003 keeps funding, payment, settlement eligibility, release, refund, and
  reversal in the Spring `payment` business boundary and defines the initial
  `SettlementStatus` set.
- ADR-006 requires async `202 + Location`, server-side idempotency, stable
  Problem Details, expected versions, and backend-derived actions for risky
  operations.
- ADR-010 provides provider-independent funding/payment intent, lifetime-fixed
  provider idempotency, durable dispatch, query-first reconciliation, and
  fail-closed unknown outcomes.
- ADR-010 explicitly excludes release, settlement, refund, and undocumented
  provider behavior. It forbids automatic approve-then-refund workarounds.
- ADR-011 keeps fulfillment completion independent of release, settlement, and
  Deal completion and requires the future Deal-completion plan to align lock
  order with payment operations.
- Accepted Slice 14A and ADR-013 define the future active-dispute gate. The
  implementation is merged into `main`; V22 is frozen accepted history.
- Gate C0 retired the transferred Slice 14A Section 6 browser debt and the
  inherited Slice 13 historical VIDEO/MP4 observation on 21 July 2026. Evidence:
  `docs/agent/c0-14a-browser-debt-acceptance-2026-07-21.md`. Do not restate that
  matrix as open debt for 14B acceptance.
- Slice 7 staging and C2/G4b were accepted on 21 July 2026; Slice 11B
  real-provider integration remains deferred and unaccepted.
- Moka is only a research candidate. No provider release/capture, settlement
  finality, status-query, callback, duplicate, timeout, dispute-after-dispatch,
  void, credential, or operational behavior is accepted.
- No custody, marketplace/sub-dealer, KYC, fee/split, Law 6493, manual
  intervention, or payout-ownership decision is accepted.
- Existing ratification packages contain no `disputeWindowDays` term. They
  must remain release-ineligible rather than being silently reinterpreted.
- Refund, reversal, chargeback, and post-settlement recovery remain a separate
  later ADR/slice.

## 3. Scope

### Target scope after every gate closes

- Immutable, versioned, party-ratified `disputeWindowDays` commercial term.
- Exactly one settlement aggregate per funded unit/Deal under the accepted V1
  single-plan/single-unit model.
- Explicit buyer-ADMIN release request only when:
  - Deal remains `ACTIVE`;
  - funding is provider-verified `FUNDED`;
  - fulfillment is `COMPLETED`;
  - the ratified dispute window has elapsed;
  - no active Slice 14A dispute exists; and
  - no release/settlement operation is already active or terminal.
- Durable provider release dispatch with a lifetime-fixed provider idempotency
  identity.
- Query-first reconciliation for crashes, timeout, unknown outcome, duplicate
  delivery/callback, restart, and release-first/dispute-later races.
- Participant-readable safe settlement projection with backend-derived
  actions; buyer ADMIN remains the sole mutation actor.
- Deal `ACTIVE -> COMPLETED` only when the approved provider query proves final
  settlement.
- Contract-first Core API, forward-only persistence, audit, concurrency,
  staging/provider-sandbox validation, and planner-owned browser acceptance.

### Permanently out of scope for 14B

- Automatic release on fulfillment completion, window expiry, scheduler, AI
  output, callback, or browser redirect.
- Refund, reversal, chargeback, approve-then-refund, or post-settlement recovery.
- Production credentials, production money movement, or production deployment.
- Platform-held/manual-payout assumption without accepted legal approval.
- AI involvement.
- Case resolution, mutual cancellation, or casework-driven ACTIVE cancellation.
- Provider-specific behavior not proven by an accepted sandbox probe.
- Implementer-run staging/browser acceptance.

## 4. Architecture and Contract Decisions

### Binding target decisions already approved for this planning draft

- Release is an explicit buyer legal entity `ADMIN` action. Fulfillment
  completion and dispute-window expiry create eligibility only; they create no
  provider operation.
- The dispute window is an immutable ratification-package term approved by both
  parties. Existing packages without the term remain release-ineligible.
- Refund/reversal is separate later work.
- Dispute-first wins the agreed Deal lock and prevents release intent/dispatch.
- Release-first followed by a dispute never implies external cancellation. The
  provider is queried and local state remains fail-closed until verified.
- Only provider-verified final settlement produces SettlementStatus `SETTLED`
  and DealStatus `COMPLETED`.
- Redirects, callbacks, release acceptance, local dispatch completion, timeout,
  or a provider-native “success” string are not finality unless ADR-014 maps
  them from accepted probe evidence.

### G1 — Provider capability gate

Human acceptance must identify one provider and retain reproducible sandbox
evidence for:

- release/capture initiation and exact request identity;
- authoritative status query and settlement finality;
- duplicate provider-key behavior;
- decline versus unknown/timeout behavior;
- crash before call and crash after call/before local commit;
- callback/redirect authentication and whether either is authoritative;
- dispute appearing before and after external dispatch;
- maximum pending/processing duration and operational recovery;
- safe provider-reference persistence/public projection; and
- credential, PII, and raw-payload handling.

If query-first finality cannot be proved, stop. Do not implement optimistic
release, automatic retry with a new key, or approve-then-refund.

### G2 — Legal and operational gate

Human acceptance must record:

- custody, marketplace, sub-dealer, or other legal payment model;
- KYC/onboarding responsibility;
- fee, split, payout, and settlement ownership;
- Law 6493 and related legal review;
- buyer release authority and dispute-window enforceability;
- manual intervention role, authorization, audit, and escalation;
- provider-operation cutoffs and finality expectations; and
- support/incident ownership for unknown outcomes.

No platform-held/manual-payout or operator-authority default may be inferred.

### G3 — Ratification contract compatibility gate

Human acceptance must choose and fully specify:

- the versioned ratification snapshot carrying `disputeWindowDays`;
- its integer unit, allowed range, canonical ordering/hash input, and validation;
- whether a new endpoint/schema version or additive compatibility transition is
  used;
- frontend/generated-client migration;
- behavior of new package creation when the term is absent; and
- proof that existing clients and already-ratified packages are not silently
  changed.

Existing accepted packages remain release-ineligible. A migration must not
invent contractual consent for them.

### G4 — Prerequisite acceptance gate

Before ADR-014 or a ready plan:

- Slice 7 staging is accepted;
- Slice 11B real-provider integration is accepted;
- Slice 14A is accepted;
- the accepted provider adapter exposes the exact verified operations through
  provider-neutral ports; and
- no provider unknown, refund workaround, or unresolved legal decision remains.

### Target ownership and module boundaries

- `payment` owns settlement eligibility, settlement/release aggregates,
  provider operation state, durable dispatch, reconciliation, and public
  settlement projections.
- `deal` owns the Deal lock/status transition through a consumer-owned port;
  payment never reads DealRepository directly.
- `casework` owns active-dispute state and exposes only a narrow gate/lock
  projection; payment never reads casework tables or repositories directly.
- `ratification` owns the versioned contractual window; payment consumes a
  stable immutable projection rather than interpreting rule text or AI output.
- `integration` owns provider adapters and relay mechanics and makes no release,
  eligibility, dispute, or finality decision.
- Provider calls occur after durable intent commit and outside database
  transactions.

### Target public API — provisional until G1–G4 and ADR-014

```text
GET  /api/v1/deals/{dealId}/settlement
POST /api/v1/deals/{dealId}/settlement/release

GET  /api/v1/release-operations/{operationId}
POST /api/v1/release-operations/{operationId}/reconcile
```

- Release/reconcile return `202 Accepted`, `Location`, and a safe operation
  projection; neither waits for the provider.
- Mutations require session, CSRF, legal-entity context, `Idempotency-Key`, and
  the exact expected versions ADR-014 assigns.
- Deal participants may read a safe settlement summary; only buyer ADMIN may
  request release or reconciliation.
- Public DTOs omit credentials, raw provider payloads, internal ledger data,
  unsafe provider messages, and full provider references.
- Backend actions include `canRequestRelease` and `canReconcileRelease`.
- Stable errors distinguish missing contractual window, unelapsed window,
  active dispute, stale state, operation already active, terminal settlement,
  unknown provider state, forbidden actor, hidden resource, validation, and
  idempotency reuse.

Exact schema, enum, provider mapping, state transitions, and compatibility
cannot be finalized until G1–G4. This section is a target boundary, not an
implementation contract.

### Target state, transactions, and persistence

- SettlementStatus follows ADR-003:
  `NOT_READY`, `READY`, `PROCESSING`, `ON_HOLD`, `SETTLED`, `FAILED`, and
  `CANCELLED`.
- ADR-014 must define which values are reachable in 14B and the exact verified
  provider mappings. No generic free status setter is permitted.
- A durable release operation distinguishes local intent, external dispatch,
  unknown outcome, verified provider result, and final settlement.
- Unknown/timeout/crash state remains reconcilable and never becomes automatic
  success or failure.
- The migration version is allocated only from the then-current accepted
  Flyway history; no existing migration is rewritten.
- Eligibility/release intent, audit, HTTP idempotency, and durable dispatch are
  atomic. Provider application and settlement finality each use short,
  separately verified transactions.
- The final settlement transaction locks in the ADR-014 order, applies
  SettlementStatus `SETTLED`, changes Deal `ACTIVE -> COMPLETED`, and appends
  audit atomically. It creates no refund, reversal, cancellation, or archive.

## 5. Detailed Implementation Phases

Only G0 is actionable planning/research after explicit user authorization.
B-P1–B-P7 are sequenced future phases, but no implementer may execute them
until G1–G4 are accepted and the resulting ADR-014 and revised plan receive
explicit human approval.

### G0 — Close external decision gates

Objective:
Produce the evidence required for a decision-complete ADR-014.

Exact scope:

- Run approved provider sandbox probes for initiation, query, duplicates,
  decline, timeout, crash recovery, callback/redirect, finality, and
  dispute-after-dispatch behavior.
- Obtain the G2 legal/operational decision record.
- Decide the G3 ratification contract/version rollout.
- Record exact accepted Slice 7, 11B, and 14A prerequisites.
- Store no credential, raw payment data, or provider secret in the repository.
- Change no application code, migration, public contract, or accepted ADR.

Validation and completion evidence:

- Reproducible redacted capability matrix, authoritative provider documents,
  sandbox request/query evidence, legal/operations approval, and explicit
  human gate acceptance.

Stop/escalation conditions:

- Stop if provider query finality, duplicate safety, legal model, authority,
  ratification rollout, or dispute race remains unknown.
- Do not design a workaround or start B-P1.

Planner review checkpoint:
Explicit acceptance of G1–G4 is required before ADR-014 drafting.

### B-P1 — ADR-014 and contract-first design

Objective:
Convert accepted gate evidence into a decision-complete Settlement and Release
ADR and reviewed public contract.

Exact scope and likely boundaries:

- Define provider mappings, release-operation states, settlement finality,
  lock order, dispute race, contractual-window versioning, ledger/accounting
  boundary, authorization, idempotency, audit, errors, and compatibility.
- Revise this planning document with exact behavior and obtain human approval
  before moving it to `ready/`.
- Add the approved ratification and settlement/release API shapes to committed
  OpenAPI, validator expectations, contract docs/changelog, and generated types.

Tests and validation:

- Contract validator, expected-invalid matrix, generated-type drift, package
  canonical/hash compatibility, and provider-adapter contract tests.

Completion evidence:

- ADR-014 contains no open provider, legal, product, state, or compatibility
  decision and the contract matches the accepted probe evidence.

Stop/escalation conditions:

- Stop if any G1–G4 assumption changes or ADR-014 cannot be decision-complete.

Planner review checkpoint:
ADR-014 and contract approval before persistence.

### B-P2 — Forward-only settlement persistence and ports

Objective:
Establish settlement, release-operation, immutable result/history, and durable
dispatch persistence under accepted ownership boundaries.

Exact scope and likely boundaries:

- Add the next forward-only migration after the then-accepted history.
- Enforce one settlement per funding unit/Deal, one active/terminal release as
  specified by ADR-014, provider-key uniqueness, status/timestamp checks,
  immutable verified results, dispute-hold state, and optimistic version.
- Add payment-owned repositories/services/ports, Deal target/lock adapter,
  casework dispute gate, ratification term projection, and integration-owned
  provider adapter boundary.
- Keep money relational using integer minor units and ISO currency.

Authorization, idempotency, concurrency, and audit:

- Provide the primitives for lifetime-fixed provider keys, version checks,
  audit, and durable dispatch; no external call in migration/persistence work.

Tests and validation:

- Clean/upgrade migration, cardinality, cross-Deal/unit integrity, state checks,
  immutability, provider-key uniqueness, and module architecture.

Completion evidence:

- Invalid duplicate, cross-Deal, cross-unit, terminal, and mutation writes fail
  at DB/application boundaries.

Stop/escalation conditions:

- Stop on frozen-migration edits, provider-specific domain fields not approved
  in ADR-014, float money, or foreign repository/entity access.

Planner review checkpoint:
Persistence/port review before eligibility.

### B-P3 — Eligibility, ratified window, and dispute gate

Objective:
Compute release eligibility entirely in Spring and create no external effect
until every invariant is revalidated.

Exact scope and likely boundaries:

- Derive the deadline from the accepted immutable ratification snapshot and
  ADR-014-defined fulfillment completion timestamp.
- Require ACTIVE, provider-verified FUNDED, COMPLETED fulfillment, elapsed
  window, no active dispute, and no active/terminal release conflict.
- Use the ADR-014 deterministic lock order beginning with Deal and including
  settlement/funding/casework gates.
- Atomically create release intent, audit, HTTP idempotency result, and durable
  provider dispatch.
- Return async projection without waiting for the provider.

Authorization, idempotency, concurrency, and audit:

- Buyer ADMIN only; other participants read safe status only.
- Same-key replay and different-key concurrent release are DB-authoritative.
- Dispute-first produces no release operation or dispatch.

Frontend states:

- Generated/action projections distinguish no term, waiting window, active
  dispute, ready, and forbidden/read-only states.

Tests and validation:

- Every eligibility condition, exact time boundary, stale Deal/funding/
  fulfillment/settlement versions, actor matrix, replay/reuse, concurrent
  release, and dispute-first race.

Completion evidence:

- Fulfillment completion/window expiry alone produces no release; exactly one
  explicit eligible action creates one durable dispatch.

Stop/escalation conditions:

- Stop if eligibility depends on frontend time, unratified config, AI output,
  callback, or undocumented provider state.

Planner review checkpoint:
Eligibility/lock-order review before external dispatch.

### B-P4 — Provider dispatch and query-first reconciliation

Objective:
Execute release safely outside database transactions and recover unknown
outcomes without duplicate money movement.

Exact scope and likely boundaries:

- Relay claims durable dispatch in a short transaction, commits, then calls the
  accepted provider port with the lifetime-fixed key.
- Apply verified results in a new transaction under ADR-014 mappings.
- Query before repeating any uncertain external mutation.
- For release-first/dispute-later, enter the accepted fail-closed state and
  reconcile; never assume provider cancellation.
- Validate callback/redirect only if G1/ADR-014 makes it authoritative;
  otherwise treat it only as a wake-up/UX signal followed by query.

Authorization, idempotency, concurrency, and audit:

- Provider delivery/result application is duplicate-safe and audited.
- Unknown state blocks new release and terminal settlement until query resolves.

Tests and validation:

- Success, decline, timeout, crash-before-call, crash-after-call/before-commit,
  duplicate result/callback, restart recovery, late result, unknown outcome,
  and dispute-after-dispatch.

Completion evidence:

- Provider call counts prove no duplicate release and database state proves no
  false `SETTLED` or `FAILED` classification.

Stop/escalation conditions:

- Stop on undocumented provider behavior, unsafe automatic retry, new provider
  key for an unknown operation, or proposed refund workaround.

Planner review checkpoint:
Provider/reconciliation evidence review before finality.

### B-P5 — Settlement finality, Deal completion, and frontend

Objective:
Show authoritative settlement progress and complete the Deal only at verified
provider finality.

Exact scope and likely boundaries:

- Apply only the ADR-014 verified final settlement result to `SETTLED`.
- In the accepted lock order, atomically transition Deal `ACTIVE -> COMPLETED`,
  apply final settlement state, and append audit.
- Do not archive, cancel, refund, reverse, or open/resolve casework.
- Add settlement frontend/query/error states for unavailable, missing term,
  window pending, dispute-blocked, ready, processing, on hold, unknown/
  reconcile, failed, settled, loading, backend error, stale, and read-only.
- Render action availability exclusively from backend projections.

Authorization, idempotency, concurrency, and audit:

- Buyer ADMIN mutation; participant-safe read; finality transaction is
  duplicate-safe and version-authoritative.

Tests and validation:

- Participant/actor matrix, release acceptance versus settlement finality,
  settlement-vs-Deal completion, payment initiate/reconcile-vs-completion,
  dispute visibility, late/duplicate finality, and no premature completion.
- Frontend typecheck/build and stable-code recovery.

Completion evidence:

- Release acceptance, callback, or unknown outcome leaves Deal ACTIVE; only a
  provider-verified SETTLED result completes it.

Stop/escalation conditions:

- Stop if finality cannot be verified by the authoritative provider query or
  requires refund/cancellation behavior.

Planner review checkpoint:
Finality and frontend review before hardening.

### B-P6 — Dispute, provider, and accepted-slice hardening

Objective:
Prove money-safety invariants and compatibility under races and recovery.

Exact scope and tests:

- Dispute-first, release-first, dispatch claim, provider call, reconciliation,
  duplicate dispatch, late result, and settlement/new-dispute timing.
- Funding/payment reconciliation, fulfillment completion, 14A disclosure,
  Deal lifecycle, ratification hashing/versioning, and module architecture.
- Before/after assertions for no refund, reversal, cancellation, automatic
  release, AI effect, or credential/raw-payload persistence.

Completion evidence:

- Exact provider-call counts, durable operation history, deterministic race
  outcomes, and no unknown outcome classified as success/failure.

Stop/escalation conditions:

- Stop on any FORBIDDEN behavior, unverified provider mapping, or need to
  broaden refund/legal scope.

Planner review checkpoint:
Focused hardening review before full validation.

### B-P7 — Full validation and review handoff

Objective:
Submit the implementation for planner review without claiming staging/browser
acceptance or plan completion.

Required validation after ADR-014 makes commands exact:

- contract validator and compatibility checks;
- full Core API verify;
- frontend typecheck and production build;
- approved provider sandbox adapter/integration suite;
- Compose/config checks where applicable;
- focused provider/reconciliation/dispute/finality matrix; and
- `git diff --check`, status, and complete prerequisite-base-to-HEAD diff.

The implementer report must include exact provider sandbox environment,
redacted probe identity, call counts, unknown outcomes, migrations, contract
delta, branch/base/HEAD, deviations, and `Plan completion claim: NO`.

Stop/escalation conditions:

- Report `PARTIAL` or `BLOCKED` rather than weakening money-safety checks,
  exposing secrets, or claiming planner-owned acceptance.

## 6. Browser Acceptance

Owner: planner. This section is blocked until G1–G4 and B-P1–B-P7 are accepted.

Use accepted staging plus the approved provider sandbox. Never use production
credentials or real money.

Before or as part of this section, confirm gate C0 remains accepted:
`docs/agent/c0-14a-browser-debt-acceptance-2026-07-21.md` already retires the
transferred Slice 14A §6 matrix and Slice 13 historical VIDEO/MP4 debt. Do not
re-open that debt as a 14B prerequisite; produce settlement/release browser
evidence separately.

1. Create and ratify a new package version containing the accepted dispute
   window term and verify its canonical hash/projection.
2. Activate, fund through the accepted real-provider sandbox adapter, and
   complete fulfillment.
3. Before expiry, buyer ADMIN sees release unavailable and forced release POST
   receives the accepted stable conflict.
4. Open a Slice 14A dispute; release remains blocked and no provider operation
   or dispatch is created.
5. Withdraw the dispute; release remains unavailable until the exact ratified
   window expires.
6. After expiry, buyer MEMBER, seller, and other participants cannot release;
   buyer ADMIN explicitly requests release through the UI.
7. Confirm queued/processing state survives refresh, double action/replay
   produces one provider operation, and safe participants see no credential or
   raw provider detail.
8. Exercise timeout/unknown behavior. UI shows fail-closed reconciliation, not
   success, decline, or a new release action.
9. Reconcile through authoritative provider query and reach verified SETTLED.
10. Confirm only verified SETTLED changes Deal to COMPLETED.
11. Exercise the accepted release-first/dispute-later provider-sandbox path and
    confirm query-first fail-closed reconciliation.
12. Confirm no refund, reversal, automatic release, cancellation, credential
    disclosure, production money movement, or AI side effect.
13. Regress Slice 14A party-only disclosure and accepted fulfillment/video
    history behavior.

Any browser/provider discrepancy produces `FIX` or `REPLAN`. Automated or fake
provider tests do not replace this section.

## 7. Validation and Review Handoff

- This document cannot move to `ready/` while any G1–G4 item remains unresolved.
- G0 evidence is planner/human decision input, not an implementer authorization.
- After a future implementation request, reviewer independently verifies
  provider evidence, legal decisions, contract compatibility, migration safety,
  money types, authorization, idempotency, external-call boundaries,
  reconciliation, dispute races, finality, secret handling, and the full diff.
- Provider sandbox screenshots/log references must be redacted and contain no
  credential, raw card/payment data, secret, or unnecessary PII.
- Acceptance requires automated validation plus real staging/provider-sandbox
  browser evidence.
- If provider behavior or law/operations contradict ADR-014, return `REPLAN`;
  never create a workaround.
- Task acceptance does not complete the plan. All phases, browser steps,
  invariants, gates, validations, and Done items require evidence.
- `docs/agent/CURRENT.md` changes only after accepted project state materially
  changes.

## 8. Done Definition

- [ ] G1 provider capability evidence is complete and human-accepted
- [ ] G2 legal and operational approval is recorded and human-accepted
- [ ] G3 ratification contract/version rollout is decision-complete and accepted
- [x] G4a Slice 14A prerequisite is accepted
- [x] G4b Slice 7 staging prerequisite is accepted
- [ ] G4c Slice 11B real-provider prerequisite is accepted
- [ ] ADR-014 is decision-complete and human-accepted
- [ ] This revised 14B plan is human-approved and moved to `ready/`
- [ ] Ratified dispute-window terms are immutable, versioned, and canonical-hash inputs
- [ ] Existing packages without the term remain release-ineligible
- [ ] Forward-only settlement/release persistence and module ports are complete
- [ ] Buyer-ADMIN explicit release and participant-safe reads work
- [ ] Fulfillment completion/window expiry never creates automatic release
- [ ] Active dispute blocks pre-dispatch release
- [ ] Release-first/dispute-later uses verified query-first reconciliation
- [ ] Unknown provider outcomes never become automatic success or failure
- [ ] Lifetime-fixed provider idempotency prevents duplicate release
- [ ] Only provider-verified SETTLED completes the Deal
- [ ] No automatic release, refund, reversal, cancellation, AI effect, or production money movement exists
- [ ] Implementer-owned full validation and focused provider matrix pass
- [ ] Implementer reports all phases with `Plan completion claim: NO`
- [ ] Planner independently reviews the complete diff and evidence
- [ ] Planner-owned staging/provider-sandbox browser acceptance passes
- [x] Transferred Slice 14A Section 6 and Slice 13 historical VIDEO/MP4 browser debt is visibly retired (gate C0; `docs/agent/c0-14a-browser-debt-acceptance-2026-07-21.md`)
- [ ] The plan is archived only after every gate, phase, invariant, browser step, validation, and Done item is proven
