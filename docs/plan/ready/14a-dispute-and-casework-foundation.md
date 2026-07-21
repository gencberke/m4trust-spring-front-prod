# Slice 14A — Dispute and Casework Foundation

- Status: ready — human-approved 21 July 2026
- Draft date: 21 July 2026
- Repository baseline: `main@dbcad17949b9063b9ef385a858f728d1d0f94536`
- Predecessor: accepted Slice 13 Video Analysis
- Accepted decision:
  `../../../architecture-decisions/ADR-013-Dispute-and-Casework-Foundation.md`
- Successor: gated Slice 14B Settlement and Release
- Approval record: ADR-013 and this decision-complete plan were human-approved
  on 21 July 2026. Implementer work still requires a planner-issued task packet.
- Execution ownership:
  - The implementer executes P1–P8 only after ADR-013 and this plan are
    human-approved.
  - P8 is implementer-owned automated validation and review handoff.
  - Section 6 real-browser acceptance is planner-owned and must not be claimed
    by the implementer.

## 1. Goal

Provide a durable, auditable dispute case for buyer and seller organizations
after fulfillment has started.

A buyer or seller legal entity `ADMIN` opens a dispute, both parties read and
comment, the counterparty explicitly acknowledges it, and the opening party
may withdraw it. An active dispute appears as the `DISPUTE` Deal lifecycle only
to buyer/seller actors and is hidden from unrelated Deal participants.

The capability must not change Deal, fulfillment, evidence, funding, payment,
settlement, provider, or AI state. The principal success path must work through
the real frontend and Core API against PostgreSQL, with retained fulfillment
evidence and advisory video-analysis history.

## 2. Current State and Accepted Inputs

- Slice 0–13 and ADR-001–ADR-012 are accepted.
- V15–V21 are frozen accepted history; new persistence begins with V22.
- ADR-003 assigns dispute, comments, assignment, and resolution ownership to a
  future `casework` module and defines `OPEN`, `UNDER_REVIEW`, `RESOLVED`, and
  `WITHDRAWN`.
- ADR-008 makes Deal visibility participant-based while allowing a nested
  resource to have a narrower non-disclosure boundary.
- ADR-009 forbids unilateral ACTIVE cancellation.
- ADR-011 keeps fulfillment completion independent of Deal completion and
  money movement.
- ADR-012 makes video-analysis results advisory and forbids automatic dispute
  creation or business-state mutation.
- The repository has no casework package, persistence, API, frontend feature,
  or active-dispute input to the lifecycle calculator.
- Current fulfillment evidence preserves immutable finalized object identity,
  verified hash/size, status history, and version. Successful video-analysis
  results are immutable and safely projectable by ID.
- The next relevant fulfillment-history browser run must visibly retire the
  Slice 13 historical VIDEO/MP4 panel regression debt recorded in the accepted
  closure documents.
- `docs/plan/planning/14b-settlement-and-release.md` is a separate gated future
  plan. Nothing in 14B is an input to 14A implementation.

## 3. Scope

### In scope

- One casework-owned `DisputeCase` aggregate; “case” is the operational
  container and `DISPUTE` is the only V1 case type.
- Buyer or seller `ADMIN` opening for an `ACTIVE` Deal whose fulfillment is
  `IN_PROGRESS`, `EVIDENCE_REQUIRED`, `REVIEW_REQUIRED`, or `COMPLETED`.
- At most one `OPEN` or `UNDER_REVIEW` dispute per Deal.
- Closed opening reason codes:
  - `NON_DELIVERY`
  - `EVIDENCE_QUALITY`
  - `EVIDENCE_REJECTION`
  - `CONTRACT_NON_CONFORMANCE`
  - `OTHER`
- A trimmed plaintext subject of 1–200 characters and opening statement of
  1–4000 characters; blank values are invalid. `OTHER` uses the statement and
  adds no second free-text field.
- Buyer/seller `ADMIN` and `MEMBER` read active and withdrawn history and have
  append-only active-case comment access. Comment bodies are trimmed plaintext,
  1–4000 characters, and public attribution snapshots the author legal entity
  ID/name and display name without email or internal actor identity.
- Explicit counterparty-`ADMIN` acknowledgement:
  `OPEN -> UNDER_REVIEW`.
- Opening-entity-`ADMIN` withdrawal:
  `OPEN | UNDER_REVIEW -> WITHDRAWN`.
- Server-built immutable opening snapshot of fulfillment provenance and every
  finalized evidence record present at the lock point.
- Existing successful immutable video-analysis job/result references when
  present at opening.
- Party-only, actor-aware `DISPUTE` Deal lifecycle and casework summary.
- Additive Core API OpenAPI, validator expectations, generated types, stable
  Problem Details codes, V22, audit, HTTP idempotency, optimistic concurrency,
  frontend states, focused tests, full validation, and planner-owned browser
  acceptance.

### Out of scope

- `RESOLVED` transition, decision records, assignment, platform-operator role,
  SLA/escalation, notification, or case-specific binary upload.
- Mutual cancellation or casework-driven ACTIVE cancellation.
- Blocking or changing fulfillment upload, evidence review, funding, payment,
  or other accepted actions.
- Settlement hold mutation, release, payout, refund, reversal, provider calls,
  ledger work, or real money movement.
- Automatic case creation, acknowledgement, priority, evidence attachment, or
  transition from AI/video output.
- RabbitMQ, outbox, inbox, AI schemas/fixtures, AsyncAPI, AI-internal OpenAPI,
  topology, or Mock AI Worker changes.
- Railway/staging, production credentials, real-provider integration, and
  deployment work.
- Implementer-run browser acceptance.

## 4. Architecture and Contract Decisions

### Ownership and state

- Add top-level Spring module `casework` and include it in architecture rules.
- `casework` owns dispute aggregates, snapshots, comments, authorization,
  repositories, public DTOs, action projections, and status transitions.
- The public status enum includes `OPEN`, `UNDER_REVIEW`, `RESOLVED`, and
  `WITHDRAWN`, but Slice 14A exposes no action leading to `RESOLVED`.
- Comments never change status; acknowledgement is explicit.
- Withdrawal retains the case, snapshot, comments, and audit history.
- Modules collaborate through consumer-owned ports, stable IDs, and read
  projections. Casework does not access Deal, fulfillment, payment, or video
  repositories/entities directly.

### Authorization and disclosure

- Only buyer/seller legal entity `ADMIN` users open a dispute.
- Only counterparty legal entity `ADMIN` users acknowledge.
- Any `ADMIN` of the legal entity that opened the case may withdraw.
- Buyer/seller `ADMIN` and `MEMBER` users may read and comment while the case is
  active.
- Comments on `WITHDRAWN` or future `RESOLVED` cases are rejected.
- Initiator-only entities, other participants, and nonparticipants receive
  non-disclosing 404 responses from casework endpoints.
- Other participants also receive no casework summary and do not see lifecycle
  `DISPUTE`; their Deal projection keeps its previous derived lifecycle.
- Every mutation is re-authorized in the application layer. Frontend action
  availability is presentation guidance, not authorization evidence.

### Snapshot and storage boundary

Opening captures in one transaction:

- current ratification package ID;
- fulfillment and primary milestone IDs, statuses, and versions;
- every `SUBMITTED`, `ACCEPTED`, or `REJECTED` evidence record at the lock
  point;
- evidence identity, status/version-at-open, type, media type, filename,
  object version, verified size/hash, relevant timestamps, and rejection
  reason; and
- successful immutable video-analysis job/result IDs already available at
  opening.

Pinned successful video results are read only as the existing safe advisory
projection through a fulfillment-owned port keyed by the stored job/result IDs.
Casework never resolves a live latest result for historical detail.

Do not copy binary content, object key, presigned URL, canonical AI payload,
provider/model metadata, credential, or raw video. Pending uploads and later
evidence/results are not attached automatically. Evidence download remains a
fulfillment-owned, participant-authorized, short-lived-link operation.

### Persistence and migration

V22 adds:

- `dispute_case`;
- `dispute_evidence_snapshot`; and
- `dispute_comment`.

Required database authority:

- Deal hosting-tenant composite integrity;
- separate actor tenant/entity/user identity for cross-tenant opening;
- one active dispute per Deal via a partial unique invariant;
- valid status and timestamp relationships;
- non-negative monotonic aggregate version;
- same-Deal evidence/result reference integrity;
- immutable opening identity/provenance and snapshot rows;
- append-only immutable comments and stable ordering; and
- no generic delete or soft-delete behavior.

Query-critical identity, status, authorization, and relations remain
relational rather than being hidden only in JSONB.

### Transactions, idempotency, and lock order

Opening uses:

```text
Deal -> fulfillment -> milestone
     -> finalized evidence in deterministic ID order
     -> those evidence records' video-analysis jobs in deterministic ID order
```

The atomic transaction contains case, snapshot, audit, and HTTP idempotency
claim/result. It serializes against evidence accept/reject and captures a
complete pre- or post-review view without blocking later manual review.

Video job rows are locked through a fulfillment-owned port. If terminal result
application wins first, opening sees the committed result; if opening wins the
job lock, the later result is excluded permanently from that snapshot. New
analysis requests already serialize through the Deal/fulfillment/evidence lock
path. No casework code accesses a fulfillment/video repository directly.

Acknowledge, withdraw, and comment lock the case, require `expectedVersion`,
and atomically write mutation, audit, idempotency result, and the new case
version. Every mutation requires `Idempotency-Key`. Same-key/same-request
replay returns the original or equivalent result; different-request reuse is
`IDEMPOTENCY_KEY_REUSED`. No external call or broker publish occurs inside or
after these operations.

### Additive public API

Design and review before runtime implementation:

```text
POST /api/v1/deals/{dealId}/disputes
GET  /api/v1/deals/{dealId}/disputes
GET  /api/v1/deals/{dealId}/disputes/{disputeId}

GET  /api/v1/deals/{dealId}/disputes/{disputeId}/comments
POST /api/v1/deals/{dealId}/disputes/{disputeId}/comments

POST /api/v1/deals/{dealId}/disputes/{disputeId}/acknowledge
POST /api/v1/deals/{dealId}/disputes/{disputeId}/withdraw
```

- Create request: `reasonCode`, `subject`, `statement`,
  `expectedDealVersion`, and `expectedFulfillmentVersion`.
- Comment request: trimmed plaintext `body` of 1–4000 characters and
  `expectedVersion`.
- Acknowledge/withdraw request: `expectedVersion`.
- Every mutation requires session, CSRF, legal-entity context, and
  `Idempotency-Key`.
- Create returns `201 Created` and `Location`; actions return the updated
  projection; list/comments use stable pagination and ordering.
- Summary exposes identity, status, reason, subject, opening legal entity,
  lifecycle timestamps, version, and required backend-derived `canComment`,
  `canAcknowledge`, and `canWithdraw`. Detail adds the opening statement and
  safe immutable snapshot; comments remain separately paged.
- Deal detail gains an optional actor-aware `casework` summary and a
  backend-derived `canOpenDispute`; absent/unknown values are fail-closed.
- Public DTOs expose no object key, presigned URL, raw AI payload, provider
  detail, or internal entity/repository shape.

Dispute and comment collections use the standard zero-based public page DTO,
default `size=20`, and maximum `size=100`. Disputes include active and withdrawn
history, allow only `openedAt,asc|desc`, and default to `openedAt,desc` with an
`id` tie-break. Comments allow only `createdAt,asc|desc` and default to
`createdAt,asc` with an `id` tie-break. V1 adds no status filter or free-text
search.

The exact stable casework business errors are:

- malformed path/header/body (400);
- session and active-entity failures (401/403/404 as established);
- hidden collection/target: `CASEWORK_NOT_FOUND_OR_HIDDEN` or
  `DISPUTE_NOT_FOUND_OR_HIDDEN` (404);
- wrong actor: `DISPUTE_OPEN_FORBIDDEN`, `DISPUTE_COMMENT_FORBIDDEN`,
  `DISPUTE_ACKNOWLEDGE_FORBIDDEN`, or `DISPUTE_WITHDRAW_FORBIDDEN` (403);
- stale targets: `DEAL_STALE_VERSION`, `FULFILLMENT_STALE_VERSION`, or
  `DISPUTE_STALE_VERSION` (409);
- state/cardinality: `DEAL_STATE_CONFLICT`, `FULFILLMENT_STATE_CONFLICT`,
  `DISPUTE_ACTIVE_CASE_EXISTS`, or `DISPUTE_STATE_CONFLICT` (409);
- different-request key reuse: `IDEMPOTENCY_KEY_REUSED` (409); and
- semantic fields: `VALIDATION_FAILED` with `REQUIRED`, `OUT_OF_RANGE`, or
  `INVALID_ENUM` field errors (422).

An authorized actor facing terminal/invalid state receives 409; a valid-state
wrong actor receives 403; a hidden or mismatched target receives 404.

### Deal lifecycle and side-effect boundary

- Buyer/seller list/detail projections show `DISPUTE` for `OPEN` or
  `UNDER_REVIEW` cases.
- Other participants receive the lifecycle derived without casework and no
  casework summary.
- `WITHDRAWN` restores the normal derived lifecycle.
- Lifecycle remains an actor-aware read projection and is not persisted.
- Deal remains `ACTIVE`; fulfillment, evidence, funding, payment, provider,
  settlement, and accepted action availability remain unchanged.
- AI/video output cannot create or mutate casework.

## 5. Detailed Implementation Phases

The implementer owns P1–P8 and executes them in order only after human approval.
A required resolution/operator/payment/provider/messaging capability, a
FORBIDDEN boundary, or an ADR conflict is a `BLOCKED` report, not permission to
improvise.

### P1 — Implement the accepted public contract

Objective:
Lock complete Slice 14A behavior before runtime code.

Exact scope and likely boundaries:

- Implement ADR-013's accepted additive casework paths/schemas, optional Deal
  casework projection, action availability, pagination, headers, status codes,
  stable errors, exact text bounds, history ordering, and attribution contract.
- Update `contracts/openapi/core-api-v1.yaml`, exact validator expectations,
  `contracts/README.md`, `contracts/CHANGELOG.md`, and generated frontend types
  as one review unit.
- Keep AI schemas/fixtures, AsyncAPI, AI-internal OpenAPI, payment/provider
  contracts, and messaging topology byte-for-byte unchanged.

Authorization, idempotency, concurrency, and audit:

- Document the exact buyer/seller ADMIN/MEMBER and hidden-participant matrix.
- Mark every mutation as CSRF- and idempotency-protected and every mutable
  target request as version-aware.
- Document audit semantics without exposing audit internals publicly.

Tests and validation:

- Add exact validator allowlists and expected-invalid checks for endpoints,
  operations, headers, closed enums, required/optional members, status codes,
  action fields, forbidden disclosure, and error components.
- Run `python .\contracts\scripts\validate_contracts.py` and verify generated
  type drift.

Completion evidence:

- Contract validation passes and changed-file inspection proves shared AI,
  messaging, payment/provider, and deployment contracts are unchanged.

Stop/escalation conditions:

- Stop for any required resolution, assignment, operator role, settlement,
  cancellation, AI-triggered case, breaking API change, or unapproved enum
  semantics.

Planner review checkpoint:
The committed public contract must be reviewed against accepted ADR-013 before P2.

### P2 — V22 persistence and module boundary

Objective:
Establish casework-owned aggregate, snapshots, comments, and DB authority.

Exact scope and likely boundaries:

- Add `V22__dispute_casework_foundation.sql`; never edit V15–V21.
- Implement the three persistence records and constraints in Section 4.
- Add `casework` to `ModuleArchitectureTest` and protect repository/entity
  ownership and cycles.
- Add narrow casework-consumed ports toward Deal and fulfillment for visible
  target lookup, lock/revalidation, and server-owned snapshot input.
- Keep storage download and video result canonical data in their owning
  modules; casework stores only approved identities and copied safe metadata.

Authorization, idempotency, concurrency, and audit:

- Persistence contains actor/hosting tenant identities but makes no
  authorization decision by tenant equality alone.
- Add version/state primitives required by later transactional actions.

Tests and validation:

- Test clean V1–V22 migration and the accepted-chain upgrade.
- Prove one-active, status/timestamp, cross-tenant actor/hosting, same-Deal
  snapshot, immutable snapshot/comment, and version constraints.
- Run focused migration and architecture tests.

Completion evidence:

- Invalid duplicate, cross-Deal, cross-evidence, mutation, and terminal-state
  writes fail at the database boundary; module rules remain acyclic.

Stop/escalation conditions:

- Stop if an invariant appears to require rewriting a frozen migration,
  sharing a foreign repository/entity, or hiding critical fields only in JSONB.

Planner review checkpoint:
Review V22 invariants and port directions before P3.

### P3 — Open, list, and detail vertical

Objective:
Allow authorized party ADMINs to create one durable dispute and party users to
read safe casework through the real Core API.

Exact scope and likely boundaries:

- Add casework operation contexts, controller, service, aggregate, repository,
  source ports/adapters, DTOs, Problem Details mapping, and paginated reads.
- Perform preflight visibility/eligibility reads, then lock/revalidate using the
  Section 4 order.
- Build the full server-owned snapshot; ignore no current finalized history,
  lock related video jobs through the fulfillment-owned port, pin only the
  lock-point successful results, and accept no client-supplied
  evidence/storage/AI identity.
- Write case, snapshot, audit, and idempotency result atomically.
- Return actor-aware action projections and hidden-resource behavior.

Authorization, idempotency, concurrency, and audit:

- Only buyer/seller ADMIN opens; party MEMBER reads; other participants and
  nonparticipants receive 404.
- Enforce Deal ACTIVE, accepted fulfillment statuses, expected Deal and
  fulfillment versions, and one active case at both application and DB layers.
- Audit is written under the actor tenant with the dispute as subject.

Frontend states:

- None in this phase beyond generated types; do not create a mock UI.

Tests and validation:

- Cover every eligible and ineligible fulfillment status; exact text bounds,
  buyer/seller ADMIN,
  MEMBER, initiator-only, other participant, nonparticipant, cross-Deal IDs,
  stale versions, same-key replay, different-request reuse, transaction
  rollback, and concurrent distinct-key opens.
- Run the focused casework HTTP/integration suite.

Completion evidence:

- One active case and the exact complete snapshot exist under race; failures
  leave no partial case, snapshot, audit, or idempotency record.

Stop/escalation conditions:

- Stop if opening is made to mutate, block, or consult payment/settlement or if
  visibility is reduced to a tenant-ID check.

Planner review checkpoint:
Review open/read authorization and snapshot behavior before P4.

### P4 — Comments, acknowledgement, and withdrawal

Objective:
Deliver the complete foundation-only collaboration lifecycle.

Exact scope and likely boundaries:

- Add append-only paginated comments, explicit acknowledge, and withdraw
  actions with their DTOs, repositories, actions, and errors.
- Comment body uses the accepted exact bound; comments are immutable and
  ordered by stable server fields. Public attribution is the immutable
  legal-entity/display-name snapshot and exposes no email or internal actor
  identity.
- Acknowledge changes only `OPEN -> UNDER_REVIEW`.
- Withdraw changes only `OPEN | UNDER_REVIEW -> WITHDRAWN` and retains history.

Authorization, idempotency, concurrency, and audit:

- Buyer/seller ADMIN/MEMBER comment while active.
- Counterparty ADMIN acknowledges; opener-entity ADMIN withdraws.
- All actions require case `expectedVersion`, idempotency, case lock, audit, and
  atomic result recording.
- Comments, acknowledge, and withdraw races have one version-authoritative
  winner; stale losers receive stable 409 responses.

Frontend states:

- None beyond generated types; frontend integration remains P6.

Tests and validation:

- Cover same/opposite entity, MEMBER limits, hidden actors, terminal comments,
  exact bounds, page ordering/tie-breaks, replay/reuse, stale versions,
  comment-vs-withdraw,
  acknowledge-vs-withdraw, two-comment concurrency, immutable comments, and
  rollback atomicity.
- Run focused transition/concurrency tests.

Completion evidence:

- Exactly one transition winner, no lost-write behavior, immutable comments,
  and retained withdrawn history.

Stop/escalation conditions:

- Stop for resolution, assignment, ACTIVE cancellation, notification, or any
  payment/provider effect.

Planner review checkpoint:
Review lifecycle/actor matrix before P5.

### P5 — Actor-aware Deal lifecycle integration

Objective:
Expose `DISPUTE` to buyer/seller without disclosing case existence to other
participants.

Exact scope and likely boundaries:

- Add a narrow casework projection port consumed by Deal.
- Extend the central Deal lifecycle calculator/service composition with an
  actor-authorized active-dispute input.
- Add optional casework summary and `canOpenDispute` to Deal detail/actions.
- Apply the same non-disclosure rule to Deal list and detail projections.
- Do not add a persisted Deal lifecycle or duplicate status calculation in the
  frontend.

Authorization, idempotency, concurrency, and audit:

- Projection reads are party-aware; no mutation or audit is introduced here.
- Deal remains ACTIVE and existing action projections remain unchanged.

Frontend states:

- Generated types expose optional/fail-closed casework fields; no UI inference.

Tests and validation:

- Cover buyer, seller, other participant, and nonparticipant list/detail;
  OPEN/UNDER_REVIEW priority; WITHDRAWN restoration; and unchanged Deal,
  fulfillment, funding, and action projections.
- Run focused Deal projection and architecture tests.

Completion evidence:

- Other participants cannot distinguish “no dispute” from “hidden dispute” in
  Deal list/detail while parties see `DISPUTE` and the safe summary.

Stop/escalation conditions:

- Stop if the implementation persists lifecycle, calculates it in React, or
  discloses an active case to other participants.

Planner review checkpoint:
Review actor-aware projection behavior before P6.

### P6 — Real frontend casework panel

Objective:
Provide a refresh-safe generated-type-driven casework experience on Deal detail.

Exact scope and likely boundaries:

- Add a casework feature API/query/error layer and a Deal casework panel after
  fulfillment.
- Implement party-visible empty/open form, active, under-review, withdrawn
  history, paginated comments, snapshot evidence, loading, backend error,
  stale, retry, refresh, and read-only states.
- Reuse existing evidence download operations; do not expose storage identity.
- Invalidate Deal, dispute list/detail, and comment queries after mutations.
- Render open/comment/acknowledge/withdraw only from backend action fields.

Authorization, idempotency, concurrency, and audit:

- Generate and retain idempotency keys per user attempt/retry according to the
  existing frontend pattern.
- On stale/state conflict, show a stable message and refetch authoritative
  projections; do not silently retry a changed command.

Frontend states and errors:

- Localize using HTTP status/stable `code`, never `detail` parsing.
- Absent/unknown action and optional casework fields are false/hidden.
- Other participants receive no casework panel or dispute lifecycle cue.

Tests and validation:

- Run `npm run typecheck`, `npm run build`, and focused component/query checks
  where the existing frontend test foundation supports them.

Completion evidence:

- No handwritten transport model, frontend role/status authorization rule,
  frontend mock, object key, presigned URL persistence, or AI payload use.

Stop/escalation conditions:

- Stop if the UI needs a contract field not approved in P1 or requires direct
  storage/AI/provider knowledge.

Planner review checkpoint:
Review the real API/UI boundary before P7.

### P7 — Authorization, race, and regression hardening

Objective:
Prove the agreed invariants against accepted Slice 12/13 behavior.

Exact scope and likely boundaries:

- Test open racing evidence accept/reject in both lock orders.
- Test both video-terminal/open job-lock winners and verify late video results
  do not attach to an existing snapshot or mutate a case.
- Verify comments/actions change no Deal, fulfillment, evidence, funding,
  payment, settlement, provider, outbox, or AI row/state.
- Regress cross-tenant Deal visibility, evidence history/download,
  video-analysis advisory behavior, funding projection, lifecycle, and module
  architecture.
- Update integration-test cleanup lists for V22 where required without changing
  the behavior those tests assert.

Authorization, idempotency, concurrency, and audit:

- Re-run the full buyer/seller/member/other-participant matrix and active-case,
  stale, replay, terminal, and lock-order races.

Tests and validation:

- Run the targeted casework plus Slice 12/13/Deal regression matrix.
- Assert database before/after deltas for no-side-effect behavior.

Completion evidence:

- Deterministic race results, one active case, immutable snapshots/history, and
  no unrelated business mutation.

Stop/escalation conditions:

- Stop on any FORBIDDEN boundary, AI/messaging contract need, payment/provider
  effect, or resolution/cancellation scope expansion.

Planner review checkpoint:
Review the focused hardening evidence before P8.

### P8 — Full validation and review handoff

Objective:
Submit P1–P7 for planner review without claiming browser acceptance or plan
completion.

Exact scope and validation commands:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm run typecheck
npm run build

Set-Location ..
git diff --check
git status --short
```

- After full validation, re-run focused migration, authorization, idempotency,
  concurrency, lifecycle-disclosure, and Slice 12/13 regression tests.
- Inspect the complete approved-base-to-HEAD diff.
- Confirm V15–V21, AI/messaging contracts, payment/provider, deployment,
  ready/done plans, `docs/agent/CURRENT.md`, and unrelated user files remain
  unchanged.
- Replace `docs/agent/req-review.md` with branch/base/HEAD, P1–P8 outcomes,
  exact test counts, contract/migration summary, deviations, and
  `Plan completion claim: NO`.
- Do not run or claim Section 6.

Completion evidence:

- All implementer-owned commands and the final focused matrix pass; review
  handoff is complete and factual.

Stop/escalation conditions:

- Report `PARTIAL` or `BLOCKED` rather than weakening validation, changing ADR
  scope, or claiming unrun browser work.

## 6. Browser Acceptance

Owner: planner. The implementer must not execute or claim this section.

Run against real PostgreSQL, MinIO, RabbitMQ, the Mock AI Worker, Core API, and
frontend with separate buyer ADMIN, seller ADMIN, buyer/seller MEMBER, other
participant, and nonparticipant contexts.

1. Prepare a genuine cross-tenant ACTIVE + FUNDED Deal and start fulfillment.
2. Create rejected and replacement evidence, including VIDEO/MP4 with a
   retained successful advisory result.
3. Retire the accepted Slice 13 debt: visibly confirm the historical VIDEO/MP4
   row shows the retained advisory result, has no mutation controls, and does
   not duplicate the current evidence panel.
4. Buyer ADMIN opens a dispute. Refresh preserves `OPEN`, its complete
   fulfillment provenance, every finalized evidence snapshot, and only the
   video result that existed at opening.
5. Same-key replay creates one case; concurrent distinct-key opening produces
   one active-case winner and a stable conflict loser.
6. Buyer/seller MEMBER users read and comment but cannot open, acknowledge, or
   withdraw. Forced forbidden mutations are rejected server-side.
7. Seller ADMIN explicitly acknowledges; status becomes `UNDER_REVIEW` without
   altering comments or fulfillment.
8. Buyer/seller see lifecycle `DISPUTE`. An unrelated Deal participant sees no
   casework summary, retains the prior lifecycle, and receives 404 from forced
   casework reads/mutations.
9. Existing evidence upload/review and advisory video-analysis actions remain
   available and independent while the dispute is active.
10. Produce a late video result or later evidence change and confirm the case
    snapshot is unchanged.
11. Opening-entity ADMIN withdraws; case/comments/snapshot remain immutable and
    Deal lifecycle returns to the previous `FULFILLMENT` projection.
12. Exercise empty, loading, backend-error, stale-version recovery, refresh,
    pagination, and basic responsive presentation.
13. Verify API/database state contains no casework-caused Deal, fulfillment,
    evidence, funding, payment, settlement, release, refund, provider, outbox,
    or AI mutation.

Any browser defect produces planner `FIX` or `REPLAN`. Automated tests do not
replace this section.

## 7. Validation and Review Handoff

- Reviewer reads `docs/agent/req-review.md` only as an index/claim, then
  independently verifies the approved plan, branch/base/HEAD, complete diff,
  V22, contract compatibility, module ownership, tenant integrity,
  authorization, audit, idempotency, concurrency, and no-side-effect claims.
- Minimum focused reviewer checks cover:
  - buyer/seller/other-participant disclosure;
  - one active dispute;
  - complete immutable snapshot;
  - open-vs-evidence-review race;
  - comment/acknowledge/withdraw races;
  - actor-aware lifecycle and withdrawal restoration; and
  - Slice 13 advisory/history regression.
- If only assigned phases are accepted, this plan remains in `ready/`.
- No implementation acceptance alone completes the plan. Section 6, all
  invariants, full validation, and every Done item require proof.
- ADR-013 and this plan were human-approved on 21 July 2026; the accepted plan
  remains in `ready/` until every completion condition is proven.
- Only after complete acceptance may the planner move it to `done/`, record
  material deviations, and update `docs/agent/CURRENT.md` if accepted project
  state materially changed.

## 8. Done Definition

- [x] ADR-013 is human-accepted and ADR index/README/FORBIDDEN are synchronized
- [x] This complete plan is human-approved and moved to `ready/`
- [ ] P1 public OpenAPI, validator, docs, and generated types are complete
- [ ] V22 applies forward-only and V15–V21 remain unchanged
- [ ] Casework owns disputes, snapshots, comments, authorization, and public behavior
- [ ] Module ownership and consumer-owned port directions are enforced
- [ ] Open/list/detail, comments, acknowledge, and withdraw work as specified
- [ ] One-active, immutable snapshot/comment, version, and tenant invariants hold
- [ ] Authorization and non-disclosure matrix passes
- [ ] Actor-aware DISPUTE lifecycle does not leak case existence
- [ ] AI/video output never creates, advances, or mutates casework
- [ ] Deal, fulfillment, evidence, funding, payment, and accepted actions remain unchanged
- [ ] No resolution, assignment, cancellation, settlement, release, refund, provider, messaging, or deployment work exists
- [ ] Implementer-owned full validation and final focused matrix pass
- [ ] Implementer reports P1–P8 with `Plan completion claim: NO`
- [ ] Planner independently reviews the complete diff and validation evidence
- [ ] Planner-owned real-browser acceptance passes
- [ ] Historical VIDEO/MP4 regression debt is visibly retired
- [ ] Planner archives the plan only after every phase, invariant, browser step, validation, and Done item is proven
