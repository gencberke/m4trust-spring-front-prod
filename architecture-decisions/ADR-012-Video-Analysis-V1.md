# ADR-012: Video Analysis V1

- Status: Accepted
- Date: 20 July 2026
- Accepted: 21 July 2026
- Decision owners: M4Trust architecture team
- Scope: Video-analysis subject, actor model, job/result lifecycle, advisory
  boundary, messaging reuse, and fulfillment ownership
- Related decisions:
  - ADR-001: System Boundaries and Data Ownership
  - ADR-002: Spring-AI Contract and Compatibility Policy
  - ADR-003: Core Domain Model and Deal Lifecycle
  - ADR-004: Vertical Slice Delivery and Acceptance Testing
  - ADR-005: Authentication and Security Baseline
  - ADR-006: Public API and Error Conventions
  - ADR-008: Cross-Tenant Deal Participation
  - ADR-011: Fulfillment and Evidence V1

## 1. Context

ADR-002 already defines the `VIDEO_ANALYSIS` asynchronous job, canonical request
and result schemas, RabbitMQ routing keys, at-least-once delivery, retry
semantics, and the rule that a completed event is only a technical result.
ADR-003 assigns evidence and advisory video-result evaluation to the
`fulfillment` module. ADR-011 establishes immutable, storage-verified MP4
evidence versions and explicitly reserves them as the only Slice 13 input.

The accepted ADRs do not yet decide:

- who may spend the system resource by requesting video analysis;
- whether analysis is explicit or automatic;
- which evidence state is eligible;
- job cardinality, retry, and result-history behavior;
- whether an anomaly opens a durable casework item or only informs the existing
  buyer review; or
- how an analysis request races with manual evidence acceptance/rejection.

Those decisions affect fulfillment, messaging, public API, persistence, and
frontend behavior. They must be accepted before implementation starts.

## 2. Decision

### 2.1 Subject and trigger

- V1 video analysis is an explicit user action. Evidence finalize never creates
  an AI job automatically.
- The sole subject is a Slice 12 `EvidenceSubmission` whose immutable finalized
  metadata is either `evidenceType = VIDEO` with `mediaType = video/mp4`, or
  `evidenceType = PHOTO` with `mediaType = image/jpeg` or `image/png`.
- A new request is eligible only while that submission is the milestone's
  current `SUBMITTED` evidence and fulfillment is `REVIEW_REQUIRED`.
- Client input never supplies object identity, object version, hash, size,
  media type, processing profile, or expected objects. Spring derives all AI
  input from the verified evidence record.
- V1 sends an empty `expectedObjects` list. Ratified rules and advisory
  extraction `deliveryRequirements` are not reinterpreted into object-count
  expectations.

### 2.2 Actor and visibility model

- Only a buyer legal entity `ADMIN` may request video analysis. This keeps the
  optional cost-bearing advisory action with the actor who owns manual evidence
  review.
- Seller users, buyer `MEMBER` users, initiator-only entities, and other
  participants cannot request or retry analysis.
- Every Deal participant may read the analysis status and safe canonical result
  for an eligible video evidence submission, including retained rejected or
  accepted evidence history.
- Nonparticipants, a mismatched active entity, and cross-Deal nested references
  receive the established non-disclosing behavior.
- Frontend action availability is backend-derived and every request is
  re-authorized in the application layer.

### 2.3 Job cardinality, state, and retry

- Each request creates a fulfillment-owned immutable job identity bound to the
  exact evidence ID, object version, verified SHA-256, verified size, Deal, and
  tenant.
- V1 public job states are `NOT_REQUESTED`, `QUEUED`, `RESULT_AVAILABLE`, and
  `FAILED`. There is no progress event and therefore no synthetic public
  `PROCESSING` transition.
- At most one `QUEUED` job may exist for an evidence submission. Concurrent
  requests have one winner under a database invariant.
- A `RESULT_AVAILABLE` evidence submission cannot be analyzed again in V1.
- A terminal `FAILED` job may be retried only by a new explicit request with a
  new job ID and idempotency key. The new job retains predecessor provenance.
- HTTP idempotent replay of the same canonical request returns the original job
  projection. Reusing a key for a different canonical request is a conflict.
- All job and result history is retained. A canonical result is a one-time,
  immutable record for its job.

### 2.4 Advisory result and manual review independence

- `advisoryOutcome`, observations, anomalies, confidence values, review reasons,
  and warnings are advisory information only.
- V1 creates no separate casework/dispute/manual-review task from an AI result.
  The durable result is displayed inside the existing evidence review context.
- Analysis does not block, enable, or automatically execute buyer
  accept/reject. Buyer manual review remains authoritative and may finish while
  a video job is queued.
- If manual accept/reject wins before a queued job finishes, a later valid result
  may still be stored against the same immutable historical evidence. It cannot
  reopen review or change any state.
- A completed, warning, low-confidence, anomalous, failed, duplicate, late, or
  conflicting terminal event changes no Deal, fulfillment, milestone, evidence,
  payment, settlement, dispute, or provider state.
- Deal remains `ACTIVE`; its lifecycle remains `FULFILLMENT`.

### 2.5 Existing AI contract mapping

The committed video-analysis v1 JSON Schemas and AsyncAPI are sufficient and
remain unchanged:

- envelope `jobType` is `VIDEO_ANALYSIS`;
- envelope `transactionId` is the owning Deal ID;
- envelope `subjectId` and payload `input.videoId` are both the
  `EvidenceSubmission` ID;
- payload file metadata comes from the verified immutable evidence record;
- `analysisProfile` is `DELIVERY_EVIDENCE_DEFAULT`;
- `expectedObjects` is an empty array;
- requested output schema and version remain
  `m4trust.video-analysis-result` / `1.0.0`; and
- the existing video requested/completed/failed routing keys and queues are
  reused.

Spring validates event identity against the persisted job and verifies that the
job's input snapshot still equals the evidence record's immutable verified
metadata. The result schema does not need a new echoed input-hash field.

Any future requirement for non-empty expected objects, another analysis
profile, a new required result field, or changed enum semantics is a separate
Spring/FastAPI contract decision and follows ADR-002 versioning and review.

### 2.6 Ownership, transactions, and concurrency

- The `fulfillment` module owns video-analysis jobs, canonical result copies,
  authorization, persistence, public projections, and advisory interpretation.
- The existing integration messaging infrastructure owns RabbitMQ delivery,
  outbox/inbox mechanics, shared committed-schema validation, and result
  routing. It makes no business decision.
- `fulfillment` accesses no `contractintelligence` repository, table, JPA
  entity, or document-analysis job. Shared infrastructure is reused through
  technical interfaces, not by reusing the document aggregate.
- Presigned evidence download creation happens outside a database transaction.
  The atomic request transaction writes job, audit, HTTP idempotency result, and
  outbox event together after authorization and evidence revalidation.
- The request path uses the established
  Deal -> fulfillment/milestone -> evidence lock order so it cannot deadlock
  against accept/reject. If accept/reject wins first, the analysis request
  fails closed. If the analysis request wins first, manual review may still
  proceed without waiting for AI.
- Inbound processing writes inbox identity, job transition, immutable result
  when applicable, and audit in one transaction. Duplicate delivery is
  mutation-free.
- Contract-invalid or identity-invalid traffic is an integration violation, is
  never converted into evidence rejection, and exposes no raw payload in logs.
- New persistence uses a forward-only migration after V20. V15-V20 remain
  frozen accepted history.

### 2.7 Deployment boundary

V1 acceptance uses local PostgreSQL, RabbitMQ, MinIO, and the local-only Mock AI
Worker. The worker downloads and verifies the exact presigned evidence object
but runs no real video model.

Railway/staging, real FastAPI/model integration, production object storage,
production secrets, real payment providers, release, settlement, and dispute
work remain outside this decision.

## 3. Consequences

- The capability is useful during buyer evidence review without making AI a
  business authority.
- No AI contract migration is required; implementation extends already accepted
  video contract fixtures and topology support.
- Separate fulfillment-owned job/result tables preserve module ownership but
  require a focused shared result-router/schema-validator refactor so document
  extraction and video analysis can share one Spring results queue safely.
- Analysis results remain available as immutable evidence history even if the
  buyer completes manual review first.
- V1 intentionally does not create a durable casework item. That may be added
  only with the later casework/dispute decisions.

## 4. Acceptance gates

This ADR may move to `Accepted` only when human review approves:

1. buyer `ADMIN` as the sole request/retry actor;
2. explicit request on current `SUBMITTED` VIDEO/MP4 or PHOTO/JPEG|PNG evidence only;
3. one active job, immutable result history, and new-job retry after failure;
4. no separate review/casework item and no gating of manual accept/reject;
5. the no-change mapping to the committed video-analysis v1 contracts; and
6. the fulfillment ownership, transaction, lock-order, and no-side-effect
   boundaries above.

**Amendment (2026-07-23):** PHOTO evidence with `image/jpeg` or `image/png` is
accepted as a single-frame analysis input with the same OBJECT_COUNT advisory
semantics as VIDEO/MP4. No side-effect, actor, job-cardinality, or messaging
contract changes. The AI worker owner already supports JPEG/PNG for this path.
