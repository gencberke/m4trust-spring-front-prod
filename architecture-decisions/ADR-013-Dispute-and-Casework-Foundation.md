# ADR-013: Dispute and Casework Foundation

- Status: Proposed
- Date: 21 July 2026
- Decision owners: M4Trust architecture team
- Scope: Dispute-case ownership, actor model, lifecycle, evidence snapshot,
  disclosure, concurrency, and no-side-effect boundary
- Related decisions:
  - ADR-001: System Boundaries and Data Ownership
  - ADR-003: Core Domain Model and Deal Lifecycle
  - ADR-004: Vertical Slice Delivery and Acceptance Testing
  - ADR-005: Authentication and Security Baseline
  - ADR-006: Public API and Error Conventions
  - ADR-008: Cross-Tenant Deal Participation
  - ADR-009: Deal Commitment and Cancellation Consent
  - ADR-010: Ratification Commercial Terms and Funding Foundation
  - ADR-011: Fulfillment and Evidence V1
  - ADR-012: Video Analysis V1

## 1. Context

ADR-003 assigns manual cases, disputes, comments, assignments, decisions, and
resolution to a Spring `casework` module. It also names the initial dispute
states and gives an active dispute higher lifecycle-projection precedence than
settlement or fulfillment. ADR-009 permits an authorized casework/dispute
resolution to become one future path to ACTIVE Deal cancellation.

Those accepted decisions do not define:

- whether a case and a dispute are separate aggregates or lifecycles;
- who may open, acknowledge, read, comment on, or withdraw a dispute;
- which Deal and fulfillment states permit opening;
- whether unrelated Deal participants may discover casework;
- which evidence context is retained at opening time;
- how opening races fulfillment evidence review;
- whether casework blocks or mutates fulfillment, payment, or Deal state; or
- whether AI/video-analysis output may create or advance a dispute.

These choices cross Deal, fulfillment, authorization, public API, persistence,
frontend, and future settlement boundaries. They require an accepted decision
before Slice 14A can become implementation-ready.

## 2. Decision

### 2.1 Aggregate and V1 lifecycle

- `casework` is a top-level Spring modular-monolith module.
- V1 uses one `DisputeCase` aggregate. “Case” is the operational container and
  `DISPUTE` is the sole V1 case type; there is no independent `CaseStatus` that
  can drift from `DisputeStatus`.
- The public status set remains the ADR-003 set:
  `OPEN`, `UNDER_REVIEW`, `RESOLVED`, and `WITHDRAWN`.
- Slice 14A implements only:
  - case creation into `OPEN`;
  - explicit counterparty acknowledgement from `OPEN` to `UNDER_REVIEW`; and
  - opening-party withdrawal from `OPEN` or `UNDER_REVIEW` to `WITHDRAWN`.
- `RESOLVED` remains reserved and unreachable in Slice 14A. Resolution,
  decision records, assignment, platform-operator authority, SLA/escalation,
  mutual cancellation, and casework-driven ACTIVE cancellation require later
  human-approved decisions.
- Comments never cause an implicit status transition. Acknowledgement is an
  explicit business action.
- Withdrawn cases, snapshots, comments, and audit history are retained.

### 2.2 Opening eligibility and cardinality

- Only the Deal buyer or seller legal entity may open a dispute.
- The authenticated user must hold `ADMIN` membership in the active buyer or
  seller legal entity.
- The Deal must be `ACTIVE`, fulfillment must already exist, and its status
  must be one of `IN_PROGRESS`, `EVIDENCE_REQUIRED`, `REVIEW_REQUIRED`, or
  `COMPLETED`.
- Fulfillment start is the minimum opening boundary. Pre-fulfillment funding,
  ratification, invitation, or DRAFT disagreements are not Slice 14A cases.
- At most one `OPEN` or `UNDER_REVIEW` dispute may exist for a Deal. A later
  dispute may be opened only after the prior one is terminal.
- V1 opening reason codes are:
  - `NON_DELIVERY`
  - `EVIDENCE_QUALITY`
  - `EVIDENCE_REJECTION`
  - `CONTRACT_NON_CONFORMANCE`
  - `OTHER`
- Opening carries a bounded subject and statement. It does not accept a client
  supplied storage, evidence, AI-result, provider, or settlement identity.

### 2.3 Visibility, comments, and mutation authority

- Buyer and seller legal entity `ADMIN` and `MEMBER` users may read dispute
  summaries, detail, immutable snapshots, and comments.
- Buyer and seller `ADMIN` and `MEMBER` users may append comments while a case
  is `OPEN` or `UNDER_REVIEW`.
- Only a counterparty legal entity `ADMIN` may acknowledge an `OPEN` case.
- Any `ADMIN` of the legal entity that opened the case may withdraw it while
  `OPEN` or `UNDER_REVIEW`.
- `MEMBER` never gains open, acknowledge, or withdraw authority.
- Initiator-only entities, buyer/seller-unassigned participants, other Deal
  participants, and nonparticipants have no casework visibility or mutation
  authority. Casework endpoints return the established non-disclosing 404.
- The existence of a dispute is also hidden from unrelated Deal participants:
  their Deal list/detail lifecycle remains the pre-dispute derived lifecycle
  and their Deal projection contains no casework summary.
- Frontend action availability is backend-derived. Every mutation is
  re-authorized in the application layer.

### 2.4 Immutable opening snapshot

The server builds the opening snapshot. In the opening transaction it captures:

- current ratification package ID;
- fulfillment and primary milestone IDs, statuses, and versions;
- every finalized evidence record existing at the lock point, meaning
  `SUBMITTED`, `ACCEPTED`, or `REJECTED` evidence;
- evidence ID, status and version at opening, type, media type, filename,
  object version, verified size and SHA-256, relevant timestamps, and rejection
  reason; and
- existing immutable successful video-analysis job and result IDs when a
  result exists at opening time.

The snapshot does not copy binary content, object keys, presigned URLs,
canonical AI payloads, provider/model metadata, credentials, or raw video.
Pending uploads are excluded. Later evidence, status changes, comments, AI
completion, or replacement evidence never change or automatically extend the
snapshot.

Evidence download continues through the fulfillment-owned, re-authorized,
short-lived download boundary. Casework does not own object storage.

### 2.5 Persistence and tenant integrity

New persistence is forward-only after accepted V21. V15–V21 are frozen.

The V1 model contains:

- `dispute_case` for aggregate identity, Deal/fulfillment provenance, opening
  actor, status, reason, statement, acknowledgement/withdrawal facts, version,
  and timestamps;
- `dispute_evidence_snapshot` for immutable evidence/result references and the
  copied opening-time metadata; and
- `dispute_comment` for append-only actor-attributed comments.

Database authority enforces:

- hosting tenant and Deal integrity;
- distinct opening actor tenant, legal entity, and user identity suitable for
  a genuine cross-tenant Deal;
- one active dispute per Deal through a partial unique invariant;
- valid status/timestamp relationships;
- non-negative monotonic aggregate version;
- same-Deal evidence snapshot integrity;
- immutable opening identity, provenance, snapshots, and comments; and
- stable comment ordering.

No generic delete or soft-delete behavior is introduced. Query-critical
identity, status, authorization, and relations remain relational rather than
being hidden only in JSONB.

### 2.6 Transactions, idempotency, and concurrency

Opening follows the established lock order:

```text
Deal -> fulfillment -> milestone -> finalized evidence in deterministic ID order
```

This serializes opening against evidence accept/reject and produces a complete
pre-review or post-review snapshot. It does not block later manual review.

One opening transaction contains:

```text
case + snapshot + audit + HTTP idempotency claim/result
```

Acknowledge, withdraw, and comment lock the dispute aggregate, require
`expectedVersion`, and atomically write their mutation, audit, idempotency
result, and new aggregate version. Concurrent mutation never uses silent
last-write-wins.

Every casework mutation requires `Idempotency-Key`. Same-key/same-canonical-
request replay returns the original or equivalent result. Reusing a key for a
different request is `IDEMPOTENCY_KEY_REUSED`. The database active-case
invariant remains authoritative under concurrent distinct keys.

Casework performs no external call and publishes no RabbitMQ message or outbox
event in Slice 14A.

### 2.7 Deal lifecycle and no-side-effect boundary

- An active case means status `OPEN` or `UNDER_REVIEW`.
- Buyer/seller Deal projections show lifecycle `DISPUTE` while an active case
  exists.
- Other participant projections hide the case and retain the lifecycle that
  would have been derived without casework.
- Withdrawal restores the normal derived lifecycle.
- Deal lifecycle remains an actor-aware read projection, not persisted
  authoritative state.
- Deal remains `ACTIVE` throughout Slice 14A.
- Opening, acknowledgement, comments, withdrawal, or case existence changes no
  fulfillment, milestone, evidence, funding, payment, settlement, provider, or
  AI state and does not disable accepted fulfillment actions.
- An active dispute may be a future fail-closed settlement/release input, but
  Slice 14A contains no settlement hold or money mutation.

### 2.8 AI and deployment boundary

- AI/video output remains advisory.
- No AI event, anomaly, confidence value, warning, failure, or late result may
  open, acknowledge, comment on, prioritize, withdraw, resolve, or otherwise
  mutate casework.
- Existing results may be referenced only when already immutable and available
  at case opening.
- AI schemas, fixtures, AsyncAPI, AI-internal OpenAPI, RabbitMQ topology, and
  Mock AI Worker behavior remain unchanged.
- Railway/staging, real-provider work, production credentials, settlement,
  release, payout, refund, reversal, and production money movement remain
  outside this decision.

## 3. Consequences

- Buyer and seller organizations gain a durable collaboration record without
  making casework a payment or cancellation authority.
- Complete opening-time evidence context is retained without duplicating
  binaries or allowing late AI output to rewrite history.
- Party-only disclosure requires actor-aware Deal lifecycle projection rather
  than a single globally identical lifecycle for every participant.
- The foundation is intentionally not a complete dispute-resolution system.
  Resolution, assignment, cancellation, settlement hold, and refund semantics
  remain gated later work.
- A new module, migration, public API surface, and frontend panel are required,
  together with focused cross-tenant and concurrency testing.

## 4. Acceptance gates

This ADR may move to `Accepted` only when human review approves:

1. one `DisputeCase` aggregate and the foundation-only reachable lifecycle;
2. post-fulfillment ACTIVE eligibility and one-active-per-Deal cardinality;
3. buyer/seller party-only visibility and exact ADMIN/MEMBER authority;
4. server-built immutable full finalized-evidence snapshot;
5. explicit acknowledgement and opener-entity withdrawal semantics;
6. actor-aware non-disclosing Deal lifecycle projection;
7. lock order, idempotency, audit, tenant, and immutability boundaries; and
8. no fulfillment, payment, settlement, provider, cancellation, messaging, or
   AI side effect.

Until those gates and the accompanying Slice 14A plan receive explicit human
approval, no implementation task may be issued and neither document may move
to an accepted/ready state.
