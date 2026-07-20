# Slice 12 — Fulfillment and Evidence

- Status: done
- Completion date: 20 July 2026
- Material deviation: At the user's direction, planner-owned browser acceptance
  used the minimum critical two-party path with sequential seller ADMIN and
  buyer ADMIN sessions instead of repeating the complete seller MEMBER, buyer
  MEMBER, participant, and concurrency matrix in browsers. The omitted
  authorization, participant visibility, stale-version, idempotency, and
  concurrency cases are covered by the accepted automated integration suite.
  The critical real-storage path (start, upload/finalize, reject, replacement,
  accept, terminal completion, and immutable rejected history) passed against
  PostgreSQL and MinIO. Browser acceptance found and fixed a missing initial
  finalize idempotency key before the path was rerun successfully.
- Slice order: ADR-004 §24 “Fulfillment and Evidence” (Slice 12 in the split
  roadmap)
- Predecessor: accepted Slice 11 Funding Foundation; the Deal is `ACTIVE` and
  its single FundingUnit is `FUNDED`
- Successor: Slice 13 Video Analysis
- Contract boundary: the additive Core API fulfillment/evidence surface is
  included in this approval. AI JSON Schema/fixtures, AsyncAPI, and AI-internal
  OpenAPI are out of scope.
- Deployment boundary: Railway/staging, environment secrets, production object
  storage selection, and real-provider setup are deferred work and are not
  included in any implementation phase.
- Execution ownership:
  - The implementer executes P1 through P6 in order. After a phase passes its
    exit checks, the implementer continues directly to the next phase.
  - P6 is the implementer-owned automated validation, final fast check, and
    review handoff.
  - The implementer does **not** perform or claim the real-browser acceptance
    in §6. The planner owns that acceptance after implementation review.

ADR-011 makes this plan’s V1 actor, cardinality, state, evidence, and completion
decisions binding. The human approval covers the complete plan and the ordered
P1–P6 implementation assignment.

## 1. Purpose and user outcome

For an `ACTIVE + FUNDED` Deal, a seller user starts fulfillment and uploads
PDF, DOCX, photo, or video evidence for the primary milestone directly to
private object storage. A buyer `ADMIN` reviews and downloads the exact
immutable evidence version in the real application, then accepts or rejects it
with an explicit reason.

Rejected evidence remains immutable history. The seller submits a replacement
as a new evidence version, and both sides see the same current review state.
Other Deal participants can read metadata, history, and download projections;
participant visibility never implies mutation authority.

Accepted evidence moves only the milestone and FulfillmentStatus to
`COMPLETED`. The Deal remains `ACTIVE`. This slice never produces Deal
completion, settlement, release, payout, refund, provider calls, or AI
decisions.

## 2. Scope and boundaries

### In scope

- A `fulfillment` owning module for one V1 fulfillment record, one primary
  milestone, evidence submissions/history, and manual review outcome per Deal
- The V1 FulfillmentStatus state machine and centralized Deal lifecycle
  projection
- A narrow payment-owned read projection for verifying the `FUNDED`
  prerequisite
- An explicit idempotent seller action that starts fulfillment
- Evidence upload intent → browser direct PUT → storage-verified finalize
- Immutable object version, size, SHA-256, media type, and evidence type
  metadata
- Participant-readable fulfillment detail, evidence history, and short-lived
  download links
- Buyer `ADMIN` accept/reject actions, bounded rejection reason, optimistic
  concurrency, and stale-state recovery
- Atomic audit and HTTP idempotency behavior for relevant business mutations
- Optional fulfillment summary and actor-aware actions on Deal detail
- New forward-only migrations; V15–V19 remain frozen
- Implementer-owned automated validation and final fast check
- Planner-owned real-browser acceptance after implementation review

### Out of scope

- Deal `ACTIVE → COMPLETED`
- Payment release, payout, refund, reversal, settlement, or provider calls
- Dispute/casework, ACTIVE cancellation, or mutual cancellation
- Video Analysis, RabbitMQ events, AI result/review, or FastAPI changes
- Turning AI extraction `deliveryRequirements` into automatic contractual
  milestones, checklists, or completion rules
- Multiple milestones, partial milestone acceptance, amount allocation, or a
  funding/payment unit per milestone
- Adding a new contractual obligation after ratification; fulfillment tracking
  does not mutate the ratified package
- Evidence deletion or overwrite, antivirus/malware scanning, or OCR
- Public buckets or binary upload proxying through the Core API
- Railway/staging configuration, deployment pipelines, production
  secrets/credentials, production storage selection, or real payment-provider
  integration
- Implementer-run browser acceptance

## 3. Decisions and relevant ADRs

### Existing binding constraints

- `fulfillment` owns milestones, evidence evaluation, and fulfillment state
  (ADR-003 §4.7).
- Evidence is a first-class business record tied to a Deal and milestone.
  Binary content lives in object storage; Spring owns authoritative metadata
  and business state (ADR-001 §4.1, §6; ADR-003 §22).
- FulfillmentStatus is independent of DealStatus, FundingStatus, and
  SettlementStatus. The frontend does not derive lifecycle or action
  availability (ADR-003 §8, §13, §16, §29; ADR-006 §33, §41).
- AI or video output cannot automatically complete fulfillment, open a
  dispute, or release payment (ADR-003 §13, §22; FORBIDDEN §1).
- Modules communicate through ports, stable IDs, events, or read projections;
  they never share repositories or JPA entities (ADR-003 §23).
- Business mutation, audit, and any required outbox record share one
  transaction. Object-storage calls never run inside a DB transaction
  (ADR-003 §24).
- Authorization uses authenticated user, tenant, active legal entity, Deal,
  and requested operation in the application layer. Deal visibility is based
  only on participation (ADR-005 §20–§21; ADR-008 §2.4).
- Risky mutations use `expectedVersion` and server-side `Idempotency-Key`
  behavior (ADR-006 §21–§25, §42–§47).

### Binding ADR-011 V1 decisions

1. **Actor model:** seller legal entity `ADMIN` and `MEMBER` users may start
   fulfillment and submit evidence. Only buyer legal entity `ADMIN` users may
   accept or reject evidence. Buyer `MEMBER`, initiator-only entities, and
   other participants are read-only.
2. **V1 cardinality:** each Deal has one fulfillment record and one primary
   milestone. Start creates both atomically and binds them to the immutable
   current ratification package. Multiple milestones are later additive work.
3. **Contractual source:** the milestone is traceable to the ratification
   package and any `DELIVERY`/`QUALITY` rule references in that package. It does
   not reinterpret those rules. AI extraction `deliveryRequirements` were not
   carried through Slice 9 review and ratification, so they are not required
   checklists, accepted obligations, or automatic completion inputs.
4. **Exact V1 fulfillment flow:** no record means `NOT_STARTED`; seller start
   produces `IN_PROGRESS`; upload intent produces `EVIDENCE_REQUIRED`;
   storage-verified finalize produces `REVIEW_REQUIRED`; buyer reject returns
   to `EVIDENCE_REQUIRED`; buyer accept produces `COMPLETED`. `CANCELLED` may
   remain in the closed public set for forward compatibility, but Slice 12 has
   no action that reaches it.
5. **Evidence history:** each upload is a distinct immutable-object-version
   `EvidenceSubmission` with
   `PENDING_UPLOAD → SUBMITTED → ACCEPTED | REJECTED`. Rejected history is
   neither mutated nor deleted; a replacement has a new ID and object version.
6. **Completion boundary:** buyer acceptance completes only fulfillment and
   its primary milestone. The Deal stays `ACTIVE`, lifecycle remains
   `FULFILLMENT`, and settlement remains unavailable. The future plan that
   opens Deal `COMPLETE` must lock the Deal-status reads in payment
   initiate/reconcile and test that race.
7. **Initial evidence types:** `DELIVERY_NOTE`, `INVOICE`, `VIDEO`, `PHOTO`,
   `SIGNED_DOCUMENT`, and `OTHER`. Initial media types are PDF, DOCX, JPEG,
   PNG, and MP4. `UNKNOWN` is not a user submission type. Media-class size
   limits are configuration values documented as public behavior.

## 4. Public interface, state, and data impact

### Binding Core API behavior surface

The committed OpenAPI is designed before implementation and covers:

- Deal fulfillment detail: status, source ratification package, primary
  milestone, current evidence, immutable history, and actor-aware actions
- Seller start: Deal `expectedVersion`, required `Idempotency-Key`,
  synchronous created-resource response, and `Location`
- Milestone evidence upload intent: declared evidence/media metadata, size,
  client SHA-256, and a short-lived direct-PUT URL
- Evidence finalize: target milestone/evidence version, required
  `Idempotency-Key`, and the storage-verified current submission
- Evidence history and short-lived download-link reads
- Buyer `ADMIN` accept and reject: target evidence/milestone version, required
  `Idempotency-Key`, and bounded rejection reason
- Optional fulfillment summary and action members on Deal detail; absent or
  unknown actions are false/read-only

Required HTTP and disclosure behavior:

- Missing session → 401
- Authenticated caller without active-entity membership or operation authority
  on a visible Deal → 403
- Nonparticipant, cross-Deal milestone/evidence, or hidden resource →
  non-disclosing 404
- Parseable but invalid evidence metadata, size/type, or rejection reason →
  field-level 422
- Non-ACTIVE, non-FUNDED, stale, terminal, or wrong-current-evidence state →
  stable-code 409
- Same idempotency key + same canonical request → original/equivalent result;
  same key + different request → 409 `IDEMPOTENCY_KEY_REUSED`

Compatibility rules:

- All new paths and schemas are additive.
- New members on closed `DealDetail` and `DealAvailableActions` schemas are
  optional; existing required lists and semantics do not change.
- OpenAPI changes, validator exact allowlists/response/closed-shape checks,
  `contracts/README.md`, and `contracts/CHANGELOG.md` are one review unit.
- AI JSON Schema/fixtures, AsyncAPI, and AI-internal OpenAPI remain unchanged.

### Persistence and transaction impact

- V15–V19 are frozen history. Fulfillment tables and constraints use new
  forward-only migrations starting at V20 or later.
- The database enforces at least one fulfillment per Deal, one V1 primary
  milestone per fulfillment, same-Deal/milestone evidence ownership,
  immutable object key/version, and valid status/type relationships.
- A milestone has at most one current `SUBMITTED` evidence awaiting review.
  Concurrent finalize attempts leave one current winner; the loser receives a
  stale/state conflict.
- Upload intent/presign and storage size/checksum/version verification run
  outside a DB transaction.
- Finalize atomically writes SUBMITTED evidence, current pointer,
  FulfillmentStatus, audit, and idempotency result.
- Accept/reject locks Deal → fulfillment/milestone → current evidence in that
  deterministic order. It validates the target version and current pointer
  under lock, so only one terminal decision wins.
- `fulfillment` never uses Deal, payment, ratification, or document repositories
  or entities. It reads FUNDED and immutable ratification provenance through
  consumer-owned narrow ports. A storage adapter implements a
  fulfillment-owned port; the document aggregate is not reused.
- `ModuleArchitectureTest` covers fulfillment ownership and dependency
  direction.

## 5. Implementation phases

The implementer owns every phase in this section and executes them in order.
Passing a phase exit gate means continuing directly to the next phase; it does
not require a new task packet or planner approval. Scope conflicts still require
an immediate `BLOCKED` report.

### P1 — Reviewed Core API contract

Outcome:
The committed additive OpenAPI completely specifies the Slice 12
fulfillment/evidence behavior and is locked by exact contract validation.

Direction:

- Design every use-case surface and error/disclosure rule in §4.
- Update exact validator expectations, `contracts/README.md`, and
  `contracts/CHANGELOG.md` in the same review unit.
- Regenerate committed frontend types only from the OpenAPI.
- Do not change AI contracts.

Depends on:
None — ADR-011 and plan approval are complete prerequisites.

Exit checks:

- Contract validation covers new paths, operations, schemas, required headers,
  responses, optional Deal projection members, and expected-invalid cases.
- Generated frontend types match the committed contract; no handwritten
  parallel transport model exists.

### P2 — Fulfillment ownership and persistence foundation

Outcome:
The fulfillment module, forward-only migration, and narrow source/storage ports
protect the one-fulfillment/one-milestone state in PostgreSQL.

Direction:

- Do not edit V15–V19.
- Split unique, same-Deal/milestone, status, immutable-object-reference, and
  optimistic-version invariants appropriately between DB and application.
- Read payment/Deal/ratification data through consumer-owned ports without
  repository sharing.
- Extend module architecture enforcement for fulfillment.

Depends on:
P1

Exit checks:

- Migrations apply on a clean database and on top of the accepted migration
  chain.
- Persistence invariant and module-cycle tests pass.

### P3 — Start and participant-readable fulfillment vertical

Outcome:
A seller user starts fulfillment through the real API, and all participants see
the same fulfillment/milestone projection.

Direction:

- Start works only for `ACTIVE + FUNDED` Deals and the seller entity.
- DB uniqueness protects one fulfillment/milestone under concurrent starts;
  idempotent replay returns the same result.
- Persist ratification-package provenance and accepted rule references for
  traceability without reinterpreting contractual content.
- Add frontend loading, error, empty, read-only, and seller-start states backed
  by the real API.

Depends on:
P2

Exit checks:

- Seller `ADMIN`/`MEMBER`, buyer, other participant, initiator-only, and
  nonparticipant authorization cases are server-tested.
- Concurrent and replayed starts produce one fulfillment and one milestone.

### P4 — Direct evidence upload, history, and download vertical

Outcome:
A seller uploads evidence directly to private object storage, finalizes a
verified immutable version, and participants can read/download its history.

Direction:

- Reuse the Slice 6 intent → direct PUT → verify → finalize behavior pattern,
  while keeping evidence a separate aggregate and object-key namespace.
- Treat storage-verified size, checksum, and object version as authoritative.
- Pending or expired intents never become current; physical orphan cleanup is
  not a Slice 12 Done condition.
- Keep finalize status/current-pointer/audit/idempotency changes atomic.
- Implement frontend progress, retry, expired-intent recovery, type/size/
  checksum errors, history, download, and read-only participant states.

Depends on:
P3

Exit checks:

- The allowed PDF/DOCX/JPEG/PNG/MP4 media/evidence-type matrix is tested.
- Concurrent finalize attempts leave one current SUBMITTED evidence.
- Downloads are pinned to immutable object versions and reject cross-Deal
  references.

### P5 — Buyer review and fulfillment completion vertical

Outcome:
A buyer `ADMIN` accepts or rejects current evidence, the seller can submit a
replacement after rejection, and the terminal fulfillment result is visible to
both parties.

Direction:

- Accept/reject works only against current SUBMITTED evidence and expected
  versions.
- Reject preserves history and returns fulfillment to EVIDENCE_REQUIRED.
- Accept completes the milestone/fulfillment without creating Deal, payment,
  settlement, provider, outbox-external-effect, or AI side effects.
- Frontend conflicts refetch authoritative projections and never derive action
  availability.

Depends on:
P4

Exit checks:

- Accept ↔ reject and accept ↔ stale-finalize races have one valid winner.
- Buyer MEMBER, seller, and other participants cannot review.
- All fulfillment mutations fail closed after completion.

### P6 — Automated validation, final fast check, and review handoff

Outcome:
The complete P1–P5 implementation is automatically validated, receives a final
implementer sanity pass, and is submitted for planner review without claiming
browser acceptance.

Direction:

- Run all implementer-owned automated validation in §7.
- After full validation passes, run the §7 final fast check against the complete
  base-to-HEAD change.
- Fix in-scope failures before reporting `COMPLETED`; report `PARTIAL` or
  `BLOCKED` when the workflow requires it.
- Replace `docs/agent/req-review.md` using the implementer workflow.
- Set `Plan completion claim: NO`: §6 browser acceptance is still planner-owned.
- Do not run or claim the real-browser matrix.

Depends on:
P5

Exit checks:

- Required automated commands pass.
- The final fast check passes and confirms scope/frozen-history boundaries.
- The review request reports P1–P6 outcomes and identifies planner-owned
  browser acceptance as not run by the implementer.

## 6. Real-browser acceptance — planner-owned

Owner: **planner**. The implementer must not execute these steps, claim them as
validation, or mark the plan complete. After the implementer submits P1–P6 and
the user returns the report, the planner reviews the real diff and automated
evidence, then runs this matrix with PostgreSQL and MinIO. A failure produces a
planner `FIX` or `REPLAN` decision under `docs/agent/WORKFLOW.md`.

Use at least three browser contexts: seller `MEMBER`, buyer `ADMIN`, and buyer
`MEMBER` or another read-only participant.

1. Ratify an ACTIVE Deal and fund it through the sandbox until `FUNDED`.
   Lifecycle shows `FULFILLMENT`. DRAFT or ACTIVE-but-not-FUNDED Deals expose no
   start action, and a forced start returns 409.
2. Seller `MEMBER` starts fulfillment. Double-click/same-key replay creates one
   fulfillment and one primary milestone; the buyer sees the same state.
3. Seller creates an intent for PDF or photo evidence, PUTs directly to MinIO,
   and finalizes it. Status becomes REVIEW_REQUIRED and survives refresh.
4. Buyer and another participant see metadata/history and download the exact
   immutable object version. A nonparticipant gains no visibility.
5. Buyer `MEMBER` sees no accept/reject action and a forced request returns
   403. Buyer `ADMIN` rejects with a reason; the evidence remains REJECTED
   history and status becomes EVIDENCE_REQUIRED.
6. Seller uploads replacement evidence. The old evidence remains unchanged;
   the new submission becomes REVIEW_REQUIRED.
7. Buyer `ADMIN` races accept against reject from two tabs/contexts. One
   terminal result wins, the loser receives stale/state 409, and same-key replay
   creates no second decision.
8. When accept wins, milestone and fulfillment show COMPLETED. Deal remains
   ACTIVE, lifecycle remains FULFILLMENT, settlement is unavailable, and no
   release/refund/payout/provider operation exists.
9. On another Deal, upload MP4 evidence and verify participant download. No
   Video Analysis job starts automatically.
10. Non-seller participants cannot start/upload; non-buyer actors cannot review;
    an initiator that is neither buyer nor seller gains no extra mutation
    authority.
11. Document, ratification, and funding read/mutation regressions remain sound
    for their existing actor boundaries.

## 7. Minimum invariants and validation

### Implementer-owned automated invariants

- Start requires authoritative `ACTIVE + FUNDED`; caller/frontend input cannot
  assert FUNDED.
- Concurrent/idempotent start produces one fulfillment and one milestone.
- Seller `ADMIN`/`MEMBER` submit; buyer `ADMIN` reviews; buyer MEMBER, wrong
  party, initiator-only entity, and nonparticipant attempts are rejected.
- Evidence always belongs to the same Deal and milestone; DB/application reject
  cross-Deal current pointers.
- Storage-verified size/checksum/media mismatches are rejected; object version
  remains immutable and bucket access remains private.
- Presign, verify, and download calls do not hold DB transactions.
- Finalize atomically writes mutation, current pointer, status, audit, and
  idempotency result; rollback leaves no partial current evidence.
- Concurrent finalize leaves one current SUBMITTED evidence.
- Accept/reject enforces current target and expected version.
- Accept ↔ reject races have one winner; idempotent replay and different-request
  key reuse behave correctly.
- Accepted/rejected evidence history cannot be deleted or overwritten.
- Fulfillment COMPLETED creates no Deal COMPLETED, settlement, release, refund,
  PaymentOperation, provider call, or AI job.
- Lifecycle and action availability are backend-derived; absent optional
  actions are false/read-only.
- `ModuleArchitectureTest` protects fulfillment repository/entity ownership and
  cycle boundaries.

### Required implementer validation

Run from the repository root:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm run typecheck
npm run build

Set-Location ..
docker compose -f .\infra\compose.yaml config
git diff --check
```

### Final implementer fast check

After P1–P5 and the full automated validation pass, perform one short final
sanity pass before writing `req-review.md`:

- Re-run the contract validator.
- Re-run the targeted Slice 12 fulfillment integration tests selected by their
  implemented test names.
- Re-run frontend typecheck.
- Run `git diff --check`.
- Inspect `git status --short` and the complete base-to-HEAD changed-file list.
- Confirm no AI contract/messaging, Railway/deployment, accepted ready-plan,
  `docs/agent/CURRENT.md`, or V15–V19 migration file changed.

The implementer records this fast check in the review request. Automated tests
do not replace §6. The planner performs §6 after implementation review.

## 8. Done definition

- [x] ADR-011 actor, cardinality, contractual source, state, and completion
      decisions are accepted; ADR index/FORBIDDEN are synchronized
- [x] P1 additive Core API contract and exact validator/docs/generated-type
      review unit is complete; AI contracts remain unchanged
- [x] New forward-only migrations are implemented; V15–V19 remain unchanged
- [x] Fulfillment ownership, narrow source/storage ports, and module
      architecture constraints are implemented
- [x] ACTIVE + FUNDED start, one fulfillment/one milestone, and exact
      FulfillmentStatus flow work
- [x] Direct private-storage evidence upload/finalize verifies size, checksum,
      and immutable object version
- [x] Participant-readable evidence history/download and party/role mutation
      authorization work server-side
- [x] Buyer ADMIN accept/reject, replacement after rejection, stale recovery,
      idempotency, and terminal races are tested
- [x] Fulfillment COMPLETED creates no Deal COMPLETE, release, settlement,
      refund, provider, or AI side effect
- [x] Deal-detail lifecycle/action projection is backend-derived; frontend
      loading/error/empty/read-only states use the real API
- [x] Implementer-owned §7 automated validation passes
- [x] Implementer-owned final fast check passes and is recorded in
      `docs/agent/req-review.md`
- [x] Implementer reports P1–P6 `COMPLETED` with
      `Plan completion claim: NO`
- [x] Planner independently reviews the implementation and completes the
      user-directed minimum §6 PostgreSQL + MinIO two-party real-browser path;
      omitted matrix cases remain covered by automated integration tests
- [x] Previous-slice regressions pass and the planner accepts the complete plan

Implementer completion of P1–P6 is not plan completion. The plan moves to
`done/` only after planner-owned browser acceptance and final planner `ACCEPT`.
