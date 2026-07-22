# Slice 14B — Settlement and Release

- Status: planning — post-production-fix gated work; not implementation-authorizing
- Draft date: 21 July 2026; simulation-only replan started 22 July 2026
- Current repository baseline:
  `main@b2ae2dee63d58a44cddacb49814f17049c7720d3`
- Required predecessors:
  - accepted Slice 14A Dispute and Casework Foundation;
  - accepted Slice 7 staging deployment;
  - accepted Slice 11B-A simulation transport foundation;
  - accepted ADR-014 settlement/demo-simulation authority; and
  - accepted Slice 15 Production Reconciliation, a `MAIN-PROD-READY` decision, and
    successful seven-day main-application production pilot exit.
- Decision authority: ADR-014 Settlement, Release and Production Demo
  Simulation is accepted. Its schema, state, authorization, race, lock,
  transaction, query-first and production-demo boundaries are binding.
- Approval boundary: this plan cannot move to `ready/`, and no 14B task packet
  may be issued, until Slice 15 and its main-application production pilot are
  accepted and this eight-section plan receives separate human ready approval.
- Deployment/payment boundary: production may run only the separately deployed
  `DEMO_SIMULATED` service defined by ADR-014/ADR-016. Moka sandbox credentials,
  test scenario controls, provider fallback and real money movement remain
  forbidden.

## 1. Goal

After simulated funding and fulfillment completion, allow the buyer
legal entity `ADMIN` to explicitly initiate release after an immutable,
ratified dispute window has expired.

An active dispute blocks release before dispatch. A release already dispatched
when a dispute opens is handled through query-first simulator reconciliation
and is never assumed cancelled. Only query-verified `SIMULATED_SETTLED` may
change the demo Deal from `ACTIVE` to `COMPLETED`.

This document plans the complete dependency, implementation, validation, and
acceptance sequence under accepted ADR-014. It deliberately remains gated by
Slice 15 production readiness, pilot exit, and separate ready approval. This
plan never claims real money movement or financial settlement.

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
  `docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`. Do not restate that
  matrix as open debt for 14B acceptance.
- Slice 7 staging/C2/G4b and Slice 11B-A were accepted. The real-provider
  Slice 11B-B/G1 route is superseded; no credential or provider evidence is
  required or allowed.
- The founder accepted simulation-only payment/release on 22 July 2026. G1-S
  and G4c are accepted for that scope. Moka is only an internal emulator label;
  it conveys no provider or financial claim. Authority:
  `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`.
- G3 remains accepted. G2's former merchant-pool route is superseded while its
  production KYC/custody/fee/split/Law 6493/payout non-claims remain.
  Ratification schema v2 and immutable fulfillment `completedAt` compatibility
  are fixed by
  `docs/plan/planning/gates/g2-g3-founder-decision-2026-07-21.md`.
- Existing ratification packages contain no `disputeWindowDays` term. They
  must remain release-ineligible rather than being silently reinterpreted.
- Refund, reversal, chargeback, and post-settlement recovery remain a separate
  later ADR/slice.

## 3. Scope

### Target scope after every gate closes

- Immutable, versioned, party-ratified `disputeWindowDays` commercial term.
- Exactly one simulated-settlement aggregate per funded unit/Deal under the
  accepted V1 single-plan/single-unit model.
- Explicit buyer-ADMIN release request only when:
  - Deal remains `ACTIVE`;
  - funding is simulator-query-verified `FUNDED` in `SIMULATED` mode;
  - fulfillment is `COMPLETED`;
  - the ratified dispute window has elapsed;
  - no active Slice 14A dispute exists; and
  - no release/settlement operation is already active or terminal.
- Durable simulator release dispatch with a lifetime-fixed operation identity.
- Query-first reconciliation for crashes, timeout, unknown outcome, duplicate
  delivery, restart, and release-first/dispute-later races.
- Participant-readable safe settlement projection with backend-derived
  actions; buyer ADMIN remains the sole mutation actor.
- Deal `ACTIVE -> COMPLETED` only when simulator query proves
  `SIMULATED_SETTLED`; this is demo workflow completion, not financial finality.
- Contract-first Core API, forward-only persistence, audit, concurrency,
  staging plus limited-production demo validation, and planner-owned browser
  acceptance.

### Permanently out of scope for 14B

- Automatic release on fulfillment completion, window expiry, scheduler, AI
  output, callback, or browser redirect.
- Refund, reversal, chargeback, approve-then-refund, or post-settlement recovery.
- Moka/test-sandbox production enablement, public scenario controls, provider
  credentials, real money movement, or any non-`DEMO_SIMULATED` release path.
- Platform-held/manual-payout assumption without accepted legal approval.
- AI involvement.
- Case resolution, mutual cancellation, or casework-driven ACTIVE cancellation.
- Any provider-specific behavior or claim of provider/financial finality.
- Implementer-run staging/browser acceptance.

## 4. Architecture and Contract Decisions

### Binding target decisions already approved for this planning draft

- Release is an explicit buyer legal entity `ADMIN` action. Fulfillment
  completion and dispute-window expiry create eligibility only; they create no
  simulator operation.
- The dispute window is an immutable ratification-package term approved by both
  parties. Existing packages without the term remain release-ineligible.
- Refund/reversal is separate later work.
- Dispute-first wins the agreed Deal lock and prevents release intent/dispatch.
- Release-first followed by a dispute never implies cancellation. The same
  simulated operation is queried and local state remains fail-closed.
- Financial `SETTLED` is unreachable. Only query-verified
  `SIMULATED_SETTLED` may produce demo DealStatus `COMPLETED`.
- Release acceptance, local dispatch completion, synchronous success-like
  response, timeout or callback-like input is not terminal simulation proof.

### G1-S — Accepted simulation safety gate, reconciled by ADR-014

Accepted by the founder/user on 22 July 2026:

- deterministic test simulator only in local/CI/staging simulation modes;
- a separate private `DEMO_SIMULATED` production service, with no test-sandbox
  fallback or production runtime scenario-control surface;
- lifetime-fixed operation identity and query-only terminal proof;
- explicit `SIMULATED` projection and `SIMULATED_SETTLED` terminal state;
- unknown/timeout/crash remains reconcilable and blocks another release; and
- no Moka/provider/financial/custody/payout claim.

Evidence: `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`.

### G2 — Accepted non-claims

The former merchant-pool/test-credential selection is superseded. No custody,
marketplace, sub-dealer, KYC, fee, split, payout, Law 6493, real settlement or
manual-payout conclusion is made. Buyer-ADMIN authority is a demo workflow
rule, not a legal/payment-services opinion.

### G3 — Accepted ratification contract compatibility gate

ADR-014 fixes the decision as follows:

- ratification schema v2 carries integer `disputeWindowDays` in range `1..365`;
- one day is exactly 24 UTC hours and eligibility begins at
  `completedAt + disputeWindowDays * 24 hours`, inclusive;
- the value is an immutable canonical snapshot/hash input and is required for
  newly created release-eligible packages;
- schema v1 remains readable but permanently release-ineligible; and
- committed OpenAPI/JSON Schema and generated-client compatibility are delivered
  contract-first in B-P1.

No migration may invent contractual consent for an existing package.

### G4 — Accepted and remaining prerequisite gates

Before this plan may move to `ready/`:

- Slice 7 staging is accepted;
- Slice 11B-A simulation transport foundation is accepted;
- Slice 14A is accepted;
- the accepted external emulator/adapter exposes query-first provider-neutral
  primitives; and
- no real-provider, credential, refund workaround or legal-compliance claim is
  introduced;
- Slice 15 Production Reconciliation is accepted `MAIN-PROD-READY`; and
- its seven-day limited main-application production pilot exits within the accepted thresholds.

### Target ownership and module boundaries

- `payment` owns settlement eligibility, settlement/release aggregates,
  simulated operation state, durable dispatch, reconciliation, and public
  settlement projections.
- `deal` owns the Deal lock/status transition through a consumer-owned port;
  payment never reads DealRepository directly.
- `casework` owns active-dispute state and exposes only a narrow gate/lock
  projection; payment never reads casework tables or repositories directly.
- `ratification` owns the versioned contractual window; payment consumes a
  stable immutable projection rather than interpreting rule text or AI output.
- `integration` owns simulator adapters and relay mechanics and makes no release,
  eligibility, dispute, or terminal-state decision.
- Simulator calls occur after durable intent commit and outside database
  transactions.

### Target public API — fixed by ADR-014

```text
GET  /api/v1/deals/{dealId}/settlement
POST /api/v1/deals/{dealId}/settlement/release

GET  /api/v1/release-operations/{operationId}
POST /api/v1/release-operations/{operationId}/reconcile
```

- Release/reconcile return `202 Accepted`, `Location`, and a safe operation
  projection; neither waits for the simulator.
- Mutations require session, CSRF, legal-entity context, `Idempotency-Key`, and
  the exact expected versions ADR-014 assigns.
- Deal participants may read a safe settlement summary; only buyer ADMIN may
  request release or reconciliation.
- Public DTOs omit raw simulator payloads, internal ledger data, unsafe
  transport messages, and full internal operation references.
- Public settlement projections expose exact mode `SIMULATED`; the frontend
  visibly states that no real money movement occurred.
- Backend actions include `canRequestRelease` and `canReconcileRelease`.
- Stable errors distinguish missing contractual window, unelapsed window,
  active dispute, stale state, operation already active, terminal settlement,
  unknown simulated outcome, forbidden actor, hidden resource, validation, and
  idempotency reuse.

The schema details are committed contract-first in B-P1. State, lock order,
compatibility and transaction semantics may narrow only within ADR-014; any
contradiction requires `REPLAN`, not implementer invention.

### Target state, transactions, and persistence

- SettlementStatus follows ADR-003:
  `NOT_READY`, `READY`, `PROCESSING`, `ON_HOLD`, `SETTLED`, `FAILED`, and
  `CANCELLED`.
- ADR-014 adds `SIMULATED_SETTLED`. Financial `SETTLED`, refund/reversal states
  and real-money paths are unreachable in 14B. The explicit production
  `DEMO_SIMULATED` path is allowed; no generic free status setter is permitted.
- A durable release operation distinguishes local intent, simulator dispatch,
  unknown outcome, query-verified simulated result and terminal simulation.
- Unknown/timeout/crash state remains reconcilable and never becomes automatic
  success or failure.
- The migration version is allocated only from the then-current accepted
  Flyway history; no existing migration is rewritten.
- Eligibility/release intent, audit, HTTP idempotency, and durable dispatch are
  atomic. Simulator-result application and terminal completion each use short,
  separately verified transactions.
- The final simulation transaction locks in the ADR-014 order, applies
  SettlementStatus `SIMULATED_SETTLED`, changes Deal `ACTIVE -> COMPLETED`, and
  appends audit atomically. It creates no refund, reversal, cancellation,
  archive or financial-settlement claim.

## 5. Detailed Implementation Phases

B-P1–B-P7 remain sequenced future phases. No implementer may execute them until
the Slice 15 `MAIN-PROD-READY` decision, seven-day main pilot exit, and separate human
approval move this plan to `ready/`.

### G0 — Close the production-readiness entry gate

Objective:
Prove that 14B will start from the hardened main-application production baseline
rather than reopening Core/Web deployment, identity, storage, release or recovery
debt. AI internal readiness is separately owned and is not a 14B implementation
authority.

Exact scope and evidence:

- Slice 15 is archived as accepted with every automated, staging, recovery and
  operational gate complete.
- The seven-day main pilot exits within ADR-016 thresholds with exact-digest rollback
  and restore evidence.
- Contract bundle/runtime OpenAPI authority, scanned storage, invite-only
  identity, provider fail-closed behavior and production observability remain
  green at the 14B base commit.
- ADR-014 remains accepted without an unresolved contradiction from pilot
  evidence; any material change is decided by a new superseding ADR.

Stop/escalation conditions:

- Do not start B-P1 while Slice 15/pilot evidence is partial, a production gate
  is waived, or ADR-014 requires reinterpretation.

Planner review checkpoint:
Explicit G0 acceptance and a new exact-base ready revision are required before
the first 14B implementation packet.

### B-P1 — Contract-first simulation design

Objective:
Apply accepted ADR-014 decisions to the reviewed public contract before
persistence or application implementation.

Exact scope and likely boundaries:

- Preserve the ADR-014 simulator mappings, operation states, simulated
  terminality, authorization, errors and compatibility exactly.
- Add the approved ratification and settlement/release API shapes to committed
  OpenAPI, validator expectations, contract docs/changelog, and generated types.

Tests and validation:

- Contract validator, expected-invalid matrix, generated-type drift, package
  canonical/hash compatibility, and simulator-adapter contract tests.

Completion evidence:

- The contract contains explicit `SIMULATED` mode and `SIMULATED_SETTLED`, has
  no financial/provider claim and matches accepted ADR-014.

Stop/escalation conditions:

- Stop if implementation needs a decision absent from ADR-014 or changes an
  accepted simulation/G3 prerequisite.

Planner review checkpoint:
Contract approval before persistence.

### B-P2 — Forward-only settlement persistence and ports

Objective:
Establish settlement, release-operation, immutable result/history, and durable
dispatch persistence under accepted ownership boundaries.

Exact scope and likely boundaries:

- Add the next forward-only migration after the then-accepted history.
- Enforce one settlement per funding unit/Deal, one active/terminal release as
  specified by ADR-014, operation-key uniqueness, status/timestamp checks,
  immutable verified results, dispute-hold state, and optimistic version.
- Add payment-owned repositories/services/ports, Deal target/lock adapter,
  casework dispute gate, ratification term projection, and integration-owned
  simulator adapter boundary.
- Keep money relational using integer minor units and ISO currency.

Authorization, idempotency, concurrency, and audit:

- Provide the primitives for lifetime-fixed operation keys, version checks,
  audit, and durable dispatch; no external call in migration/persistence work.

Tests and validation:

- Clean/upgrade migration, cardinality, cross-Deal/unit integrity, state checks,
  immutability, operation-key uniqueness, and module architecture.

Completion evidence:

- Invalid duplicate, cross-Deal, cross-unit, terminal, and mutation writes fail
  at DB/application boundaries.

Stop/escalation conditions:

- Stop on frozen-migration edits, simulator-specific transport fields leaking
  into the business domain, float money, or foreign repository/entity access.

Planner review checkpoint:
Persistence/port review before eligibility.

### B-P3 — Eligibility, ratified window, and dispute gate

Objective:
Compute release eligibility entirely in Spring and create no external effect
until every invariant is revalidated.

Exact scope and likely boundaries:

- Derive the deadline from the accepted immutable ratification snapshot and
  ADR-014-defined fulfillment completion timestamp.
- Require ACTIVE, simulator-query-verified FUNDED in SIMULATED mode, COMPLETED
  fulfillment, elapsed window, no active dispute, and no active/terminal
  release conflict.
- Use the ADR-014 deterministic lock order beginning with Deal and including
  simulated-settlement/funding/casework gates.
- Atomically create release intent, audit, HTTP idempotency result, and durable
  simulator dispatch.
- Return async projection without waiting for the simulator.

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
  callback-like input or non-query simulator state.

Planner review checkpoint:
Eligibility/lock-order review before external dispatch.

### B-P4 — Simulator dispatch and query-first reconciliation

Objective:
Execute simulated release safely outside database transactions and recover
unknown outcomes without duplicate operations.

Exact scope and likely boundaries:

- Relay claims durable dispatch in a short transaction, commits, then calls the
  simulator port with the lifetime-fixed key.
- Apply verified results in a new transaction under ADR-014 mappings.
- Query before repeating any uncertain external mutation.
- For release-first/dispute-later, enter the accepted fail-closed state and
  reconcile; never silently cancel or create a replacement operation.
- No callback/redirect path exists. Only status query may prove
  `SIMULATED_SETTLED`.

Authorization, idempotency, concurrency, and audit:

- Simulator delivery/result application is duplicate-safe and audited.
- Unknown state blocks new release and terminal settlement until query resolves.

Tests and validation:

- Success, decline, timeout, crash-before-call, crash-after-call/before-commit,
  duplicate result, restart recovery, late result, unknown outcome,
  and dispute-after-dispatch.

Completion evidence:

- Simulator call counts prove no duplicate release and database state proves no
  false `SIMULATED_SETTLED`, financial `SETTLED` or `FAILED` classification.

Stop/escalation conditions:

- Stop on unsafe automatic retry, new operation key for an unknown operation,
  non-`DEMO_SIMULATED` production path or proposed refund workaround.

Planner review checkpoint:
Simulator/reconciliation evidence review before terminal completion.

### B-P5 — Simulated terminal result, Deal completion, and frontend

Objective:
Show explicit simulated progress and complete the demo Deal only after
query-verified `SIMULATED_SETTLED`.

Exact scope and likely boundaries:

- Apply only the ADR-014 query-verified terminal result to
  `SIMULATED_SETTLED`; financial `SETTLED` is unreachable.
- In the accepted lock order, atomically transition Deal `ACTIVE -> COMPLETED`,
  apply final settlement state, and append audit.
- Do not archive, cancel, refund, reverse, or open/resolve casework.
- Add settlement frontend/query/error states for unavailable, missing term,
  window pending, dispute-blocked, ready, processing, on hold, unknown/
  reconcile, simulated failure, `SIMULATED_SETTLED`, loading, backend error,
  stale, and read-only. No generic “settled” label is rendered.
- Render action availability exclusively from backend projections.

Authorization, idempotency, concurrency, and audit:

- Buyer ADMIN mutation; participant-safe read; terminal transaction is
  duplicate-safe and version-authoritative.

Tests and validation:

- Participant/actor matrix, release acceptance versus simulated terminality,
  settlement-vs-Deal completion, payment initiate/reconcile-vs-completion,
  dispute visibility, late/duplicate terminal results, and no premature completion.
- Frontend typecheck/build and stable-code recovery.

Completion evidence:

- Release acceptance or unknown outcome leaves Deal ACTIVE; only a
  query-verified `SIMULATED_SETTLED` result completes it, with visible
  `SIMULATED` mode and no-real-money wording.

Stop/escalation conditions:

- Stop if terminal simulation cannot be verified by the authoritative simulator
  query or requires refund/cancellation behavior.

Planner review checkpoint:
Simulated terminality and frontend-label review before hardening.

### B-P6 — Dispute, simulator, and accepted-slice hardening

Objective:
Prove money-safety invariants and compatibility under races and recovery.

Exact scope and tests:

- Dispute-first, release-first, dispatch claim, simulator call, reconciliation,
  duplicate dispatch, late result, and settlement/new-dispute timing.
- Funding/payment reconciliation, fulfillment completion, 14A disclosure,
  Deal lifecycle, ratification hashing/versioning, and module architecture.
- Before/after assertions for no refund, reversal, cancellation, automatic
  release, AI effect, sandbox fallback, credential or financial claim.

Completion evidence:

- Exact simulator-call counts, durable operation history, deterministic race
  outcomes, and no unknown outcome classified as success/failure.

Stop/escalation conditions:

- Stop on any FORBIDDEN behavior, ambiguous simulated mode, or need to broaden
  refund/legal/real-money scope.

Planner review checkpoint:
Focused hardening review before full validation.

### B-P7 — Full validation and review handoff

Objective:
Submit the implementation for planner review without claiming staging/browser
acceptance or plan completion.

Required validation after the ready revision fixes exact commands:

- contract validator and compatibility checks;
- full Core API verify;
- frontend typecheck and production build;
- external simulator adapter/integration suite;
- Compose/config checks where applicable;
- bootstrap/profile guard proving production rejects test/sandbox simulator
  modes and accepts only the private `DEMO_SIMULATED` configuration;
- CI/config test proving scenario controls remain confined to explicit
  local/CI/`staging-simulated` profiles;
- focused simulator/reconciliation/dispute/terminality matrix; and
- `git diff --check`, status, and complete prerequisite-base-to-HEAD diff.

The implementer report must include exact simulation profile/scenario, call
counts, unknown outcomes, migrations, contract delta, branch/base/HEAD,
production demo-mode/sandbox-exclusion proof, deviations, and
`Plan completion claim: NO`.

Stop/escalation conditions:

- Report `PARTIAL` or `BLOCKED` rather than weakening money-safety checks,
  exposing secrets, or claiming planner-owned acceptance.

## 6. Browser Acceptance

Owner: planner. This section is blocked until G0 and B-P1–B-P7 are accepted.

Run the matrix first on accepted staging and then on the limited production
`DEMO_SIMULATED` deployment using the exact promoted digests. Never use a test
sandbox in production or real money.

Before or as part of this section, confirm gate C0 remains accepted:
`docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md` already retires the
transferred Slice 14A §6 matrix and Slice 13 historical VIDEO/MP4 debt. Do not
re-open that debt as a 14B prerequisite; produce settlement/release browser
evidence separately.

1. Create and ratify a new package version containing the accepted dispute
   window term and verify its canonical hash/projection.
2. Activate, fund through the accepted external simulator adapter, and complete
   fulfillment; UI visibly identifies `SIMULATED` mode.
3. Before expiry, buyer ADMIN sees release unavailable and forced release POST
   receives the accepted stable conflict.
4. Open a Slice 14A dispute; release remains blocked and no release operation
   or dispatch is created.
5. Withdraw the dispute; release remains unavailable until the exact ratified
   window expires.
6. After expiry, buyer MEMBER, seller, and other participants cannot release;
   buyer ADMIN explicitly requests release through the UI.
7. Confirm queued/processing state survives refresh, double action/replay
   produces one simulator operation, and safe participants see no credential,
   raw transport detail or real-money claim.
8. Exercise timeout/unknown behavior. UI shows fail-closed reconciliation, not
   success, decline, or a new release action.
9. Reconcile through authoritative simulator query and reach
   `SIMULATED_SETTLED`.
10. Confirm only `SIMULATED_SETTLED` changes the demo Deal to COMPLETED and the
    UI states that no real money movement occurred.
11. Exercise the accepted release-first/dispute-later simulator path and
    confirm query-first fail-closed reconciliation.
12. Confirm no financial `SETTLED`, refund, reversal, automatic release,
    cancellation, credential, sandbox fallback, real money movement or AI side
    effect.
13. Regress Slice 14A party-only disclosure and accepted fulfillment/video
    history behavior.

Any browser/simulator discrepancy produces `FIX` or `REPLAN`. Focused tests do
not replace this single final user-visible simulation check.

## 7. Validation and Review Handoff

- ADR-014 is accepted; this document cannot move to `ready/` before accepted
  Slice 15/pilot G0 evidence and separate ready approval.
- G0 is planner-owned acceptance work, not an implementer authorization.
- After a future implementation request, reviewer independently verifies
  simulation decision, contract compatibility, migration safety,
  money types, authorization, idempotency, external-call boundaries,
  reconciliation, dispute races, simulated terminality, production demo-mode
  isolation and the full diff.
- Simulation screenshots/log references contain no raw transport payload,
  unnecessary PII or wording that implies real payment.
- Acceptance requires automated validation plus staging and limited-production
  `DEMO_SIMULATED` browser evidence over the same promoted digests.
- If simulation semantics or accepted G3 contradict ADR-014, return `REPLAN`;
  never create a workaround.
- Task acceptance does not complete the plan. All phases, browser steps,
  invariants, gates, validations, and Done items require evidence.
- `docs/plan/CURRENT.md` changes only after accepted project state materially
  changes.

## 8. Done Definition

- [x] G1-S simulation safety is decision-complete and human-accepted
- [x] G2 non-production operating-model decision is recorded and human-accepted
- [x] G3 ratification contract/version rollout is decision-complete and accepted
- [x] G4a Slice 14A prerequisite is accepted
- [x] G4b Slice 7 staging prerequisite is accepted
- [x] G4c Slice 11B-A simulation foundation is accepted; 11B-B is superseded
- [x] ADR-014 is decision-complete and human-accepted
- [ ] Slice 15 is accepted `MAIN-PROD-READY` and its seven-day main pilot exits successfully
- [ ] This revised 14B plan is human-approved and moved to `ready/`
- [ ] Ratified dispute-window terms are immutable, versioned, and canonical-hash inputs
- [ ] Existing packages without the term remain release-ineligible
- [ ] Forward-only settlement/release persistence and module ports are complete
- [ ] Buyer-ADMIN explicit release and participant-safe reads work
- [ ] Fulfillment completion/window expiry never creates automatic release
- [ ] Active dispute blocks pre-dispatch release
- [ ] Release-first/dispute-later uses verified query-first reconciliation
- [ ] Unknown simulator outcomes never become automatic success or failure
- [ ] Lifetime-fixed operation identity prevents duplicate release
- [ ] Only query-verified SIMULATED_SETTLED completes the demo Deal
- [ ] No automatic release, refund, reversal, cancellation, AI effect, or real production money movement exists
- [ ] Implementer-owned full validation and focused simulator matrix pass
- [ ] Implementer reports all phases with `Plan completion claim: NO`
- [ ] Planner independently reviews the complete diff and evidence
- [ ] Planner-owned staging plus limited-production demo browser acceptance passes
- [x] Transferred Slice 14A Section 6 and Slice 13 historical VIDEO/MP4 browser debt is visibly retired (gate C0; `docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`)
- [ ] The plan is archived only after every gate, phase, invariant, browser step, validation, and Done item is proven
