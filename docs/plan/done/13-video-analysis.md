# Slice 13 — Video Analysis

- Status: done
- Draft date: 20 July 2026
- Completion date: 21 July 2026
- Accepted implementation HEAD:
  `codex/slice-13-video-analysis@cdfb97a4dbb65644a42e16a7c26eb120cf8980c5`
- Repository baseline: `codex/slice-12-fulfillment@43b14391aaeec964d6a1379397ba91dc8cf2d65e`
- Predecessor: accepted Slice 12 Fulfillment and Evidence
- Successor: proposed Slice 14A Dispute and Casework Foundation
- Accepted decision: `../../../architecture-decisions/ADR-012-Video-Analysis-V1.md`
  and this complete plan are human-accepted.
- Contract boundary: additive public Core API design is included. The committed
  video-analysis v1 JSON Schemas, fixtures, AsyncAPI, and AI-internal OpenAPI
  are sufficient and must remain unchanged.
- Deployment boundary: Railway/staging, real FastAPI/model integration,
  production secrets/storage, real payment providers, release, settlement,
  refund, and dispute work are excluded.
- Execution ownership:
  - The implementer executes P1 through P7 in order after this plan and ADR-012
    are human-approved and moved to their accepted/ready states.
  - P7 is implementer-owned automated validation and review handoff.
  - The implementer does not execute or claim the real-browser acceptance in
    Section 6. The planner owns it after implementation review.

### Completion evidence and accepted deviation

Planner review, automated validation, and the real-browser runs are recorded in
`review/slice-13-acceptance-2026-07-21.md`.

Browser acceptance found three defects before closure: canonical result
retention, genuine cross-tenant job persistence/runtime MIME serialization, and
historical VIDEO/MP4 panel visibility. All three were corrected. At the user's
explicit direction, the final historical-panel fix was not followed by one more
scoped browser observation. Repository inspection plus frontend typecheck/build
is accepted for that final delta, with the exact regression debt retained in the
acceptance record. All other Section 6 paths were exercised through the combined
real-browser runs.

## 1. Purpose and user outcome

While a finalized MP4 evidence submission is awaiting buyer review, a buyer
legal entity `ADMIN` explicitly requests asynchronous video analysis. The user
sees a queued state, refresh-safe progress, and then either a safe advisory
result or a technical failure through the real application.

The Mock AI Worker receives the existing contract-valid `VIDEO_ANALYSIS`
command through RabbitMQ, downloads the exact immutable MinIO object version
through a short-lived reference, verifies size and SHA-256, and returns a
canonical success, warning, duplicate, or failure event. Spring validates and
stores the result as immutable fulfillment-owned history.

All Deal participants may read the safe result. Observations, anomalies,
confidence, warnings, and `advisoryOutcome` are visibly labeled as advisory.
They never approve or reject evidence, complete fulfillment, change the Deal,
open a dispute, or release money. Buyer manual accept/reject remains available
and authoritative whether analysis is not requested, queued, failed, or
available.

## 2. Scope and boundaries

### In scope

- Explicit buyer `ADMIN` request/retry for the current finalized VIDEO/MP4
  evidence submission
- Participant-readable per-evidence video-analysis status and safe result
- A fulfillment-owned job and immutable canonical result history bound to the
  exact evidence object version and verified hash
- One active job per evidence, HTTP idempotency, optimistic evidence-version
  revalidation, retained failed attempts, and explicit new-job retry
- Transactional outbox request publication and inbox-idempotent terminal result
  consumption through the existing RabbitMQ topology
- A shared result router and committed-schema validator that safely dispatch
  the existing single Spring AI-results queue by `jobType` without regressing
  document extraction
- Mock AI Worker support for the existing video request/result/failure schemas,
  real presigned object download, hash verification, warnings, failures, and
  duplicate delivery
- Additive Core API OpenAPI, exact validator expectations, generated frontend
  types, contract README/changelog updates, and stable Problem Details codes
- Frontend empty, queued, result, warning, failure, retry, read-only,
  loading/error, refresh, and polling behavior
- A new forward-only migration after V20 and focused architecture/invariant
  tests
- Planner-owned real-browser acceptance with PostgreSQL, RabbitMQ, MinIO, and
  the Compose Mock AI Worker

### Out of scope

- Automatic analysis on evidence finalize or upload
- Seller, buyer `MEMBER`, initiator-only, or general participant request rights
- User-supplied expected object labels/counts, analysis profiles, object
  identity, storage metadata, or AI schema versions
- Deriving expected objects from extraction `deliveryRequirements`, milestone
  rules, or ratified rule text
- A separate manual-review queue, durable casework item, dispute, hold, or
  assignment created from AI output
- Blocking or enabling buyer accept/reject based on analysis status or outcome
- Evidence mutation, replacement, deletion, overwrite, or new evidence status
- Deal completion, lifecycle change, settlement, release, payout, refund,
  reversal, provider call, or payment operation
- AI contract schema/fixture/AsyncAPI changes, a new event version, or a new
  RabbitMQ topology
- Job cancellation, progress events, SSE/WebSocket, batch analysis, multiple
  analysis profiles, or real model inference
- Railway/staging, production runtime/secrets/storage, real FastAPI, and
  production Mock AI Worker use
- Implementer-run browser acceptance

## 3. Decisions and relevant ADRs

### ADR sufficiency and escalation result

Accepted ADR-001 through ADR-011 already decide:

- Spring is the only business authority and frontend talks only to Spring;
- AI work is asynchronous through RabbitMQ and at-least-once delivery;
- canonical video output is advisory and cannot complete fulfillment, open a
  dispute, or release payment;
- fulfillment owns evidence and advisory video-result evaluation;
- business mutation, audit, and outbox/inbox records share transaction
  boundaries;
- object storage is private and raw video never travels in a broker message;
- modules collaborate through ports/IDs/projections rather than shared
  repositories/entities;
- public API is contract-first, actor-aware, idempotent where risky, and
  fail-closed; and
- the principal AI acceptance path uses real RabbitMQ plus the local Mock AI
  Worker.

They do not decide the V1 request actor, exact eligible evidence state,
job/retry cardinality, result-history behavior, or durable review/casework
effect. Those are cross-module, high-impact decisions and trigger ADR-INDEX
Layer 3. Proposed ADR-012 closes them. No implementation task may be issued
until a human accepts ADR-012 and approves this plan.

### Binding V1 decisions after approval

1. **Explicit trigger:** evidence finalize creates no AI job. Buyer `ADMIN`
   explicitly requests analysis.
2. **Exact subject:** only the current `SUBMITTED` evidence with
   `evidenceType = VIDEO` and `mediaType = video/mp4`, backed by verified size,
   SHA-256, object key, and immutable object version.
3. **Actor model:** buyer entity `ADMIN` requests/retries; all participants
   read; every other actor is mutation-forbidden.
4. **Input authority:** Spring derives all AI input from the evidence record.
   V1 sends `expectedObjects = []`; no contractual requirement is inferred.
5. **Job model:** public states are `NOT_REQUESTED`, `QUEUED`,
   `RESULT_AVAILABLE`, and `FAILED`; one queued job and at most one successful
   immutable result exist per evidence.
6. **Retry:** a failed job is retained and a new explicit request creates a new
   job with predecessor provenance. A successful result cannot be rerun in V1.
7. **Manual-review independence:** buyer accept/reject never waits for AI.
   Request-vs-review races use one lock order. A late valid result may attach to
   immutable history but cannot reopen or change review.
8. **No new review aggregate:** anomaly/low confidence produces only an
   advisory UI signal and immutable result. Casework/dispute is later work.
9. **Ownership:** fulfillment owns jobs/results and public behavior;
   integration owns transport/router/schema-validation mechanics only.
10. **Frozen history:** V15-V20 are not edited. New persistence is forward-only.

### Existing AI contract compatibility

No shared AI contract delta is required:

- `subjectId = EvidenceSubmission.id`
- `payload.input.videoId = EvidenceSubmission.id`
- `transactionId = Deal.id`
- `input.fileName/mediaType/sizeBytes/sha256` come from verified evidence
- `analysisProfile = DELIVERY_EVIDENCE_DEFAULT`
- `expectedObjects = []`
- requested output schema is
  `m4trust.video-analysis-result` version `1.0.0`
- existing requested/completed/failed routes and the
  `m4trust.ai.video-analysis.v1` / `m4trust.core.ai-results.v1` queues are reused

Spring validates the persisted job snapshot against immutable evidence metadata
when applying a result. It does not require an echoed result hash that the
existing v1 result schema does not define.

Any discovered need to change a video JSON Schema, canonical fixture, AsyncAPI,
AI-internal OpenAPI, routing key, required field, profile, or enum is not an
implementer choice. It stops the task and returns to planner/human contract
review under ADR-002 Sections 15, 24, and 25.

## 4. Public interface, state, and data impact

### Additive Core API surface

Design and commit before implementation:

```text
GET  /api/v1/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis
POST /api/v1/deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis
```

`GET` is participant-readable. It returns `NOT_REQUESTED` for a finalized
VIDEO/MP4 evidence item with no job, the latest job/result projection when one
exists, and safe backend-derived action availability. It can read retained
accepted/rejected history. A PENDING or non-video item is not an eligible video
analysis resource.

`POST` is buyer entity `ADMIN` only, requires CSRF and `Idempotency-Key`, and
accepts a closed request containing only `expectedEvidenceVersion`. It returns
`202 Accepted`, the queued projection, and `Location` pointing to the same GET
resource. It never waits for RabbitMQ, storage download, or AI processing.

The additive closed public schemas cover at least:

- `VideoAnalysisStatus`:
  `NOT_REQUESTED | QUEUED | RESULT_AVAILABLE | FAILED`
- `RequestVideoAnalysisRequest` with non-negative
  `expectedEvidenceVersion`
- `VideoAnalysisDetail` with evidence ID, nullable job ID, status, timestamps,
  nullable safe failure, nullable result, and required actor-aware actions
- `VideoAnalysisAvailableActions` with positive `canRequest`
- `VideoAnalysisResult` with duration, observations, anomalies, summary, and
  safe warnings; no provider/model/prompt/native payload
- closed observation type, anomaly severity, advisory outcome, warning
  severity, and time-range shapes compatible with the committed canonical
  video contract

The public result is a use-case DTO, not the internal event payload. It omits
technical metadata and any storage URL, key, credential, native provider
response, or raw video content.

### HTTP, authorization, and disclosure behavior

- Missing/invalid session -> 401
- Authenticated user without active-entity membership -> established 403/404
  context behavior
- Nonparticipant, cross-Deal evidence ID, or hidden nested resource ->
  non-disclosing 404
- Visible participant who is not buyer entity `ADMIN` forcing POST ->
  403 `VIDEO_ANALYSIS_REQUEST_FORBIDDEN`
- Malformed UUID/header/JSON -> 400
- Negative or semantically invalid request field -> field-level 422
- PENDING, wrong evidence/media type, non-current evidence request, fulfillment
  not `REVIEW_REQUIRED`, or manual review already decided ->
  409 `VIDEO_ANALYSIS_EVIDENCE_NOT_ELIGIBLE`
- Evidence version changed -> 409 `EVIDENCE_STALE_VERSION`
- Active job exists -> 409 `VIDEO_ANALYSIS_ACTIVE_JOB_EXISTS`
- Successful result already exists -> 409
  `VIDEO_ANALYSIS_ALREADY_COMPLETED`
- Same idempotency key plus same canonical request -> original/equivalent 202
  result; same key plus different canonical request ->
  409 `IDEMPOTENCY_KEY_REUSED`

Frontend recovery branches only on HTTP status and stable `code`, refetches the
authoritative evidence/analysis projection after state conflicts, and treats
missing/unknown action data as false/read-only.

### Job and result state

```text
no job -> NOT_REQUESTED
explicit request -> QUEUED
QUEUED + valid completed event -> RESULT_AVAILABLE
QUEUED + valid failed event -> FAILED
FAILED + explicit retry -> new QUEUED job linked to predecessor
```

The single results queue has no progress event; V1 does not synthesize
`PROCESSING`. For a job, the first valid terminal event wins. Later terminal
events are inboxed/audited as ignored and make no second transition or result.

Manual evidence review is an independent existing state machine:

```text
SUBMITTED -> ACCEPTED | REJECTED
```

It does not read advisory outcome. If accept/reject changes the evidence before
the request obtains the established lock sequence, the request fails as
ineligible. If the request commits first, accept/reject proceeds without waiting
for the queued job. A later result is historical advisory metadata only.

### Persistence and integrity

Use a new forward-only migration after V20. Do not edit V15-V20.

The fulfillment-owned persistence model provides:

- a job row containing tenant, Deal, fulfillment/milestone/evidence IDs,
  immutable evidence object version, verified SHA-256/size/media/file snapshot,
  status, predecessor job when retrying, safe failure data, timestamps, and
  optimistic version;
- a one-to-one immutable canonical result row containing job ID, exact schema
  version, canonical result JSON, and creation timestamp;
- same-Deal/evidence composite integrity, including any additive unique key
  needed on the existing evidence table through the new migration;
- a partial unique invariant for one queued job per evidence;
- a unique invariant preventing more than one successful result per evidence;
- predecessor integrity that cannot link jobs from different evidence;
- check constraints for exact state/timestamp/failure relationships; and
- update/delete protection for canonical result identity/content.

The result JSON is retained as the canonical Spring-side copy, while public DTO
assembly validates and selects only the approved safe fields. No query-critical
identity, status, authorization, or relation is hidden only inside JSONB.

### Transaction, external-call, and routing boundaries

Request sequence:

1. Resolve participant visibility and buyer `ADMIN` authority.
2. Read the exact finalized evidence snapshot and reject active/completed jobs.
3. Outside a DB transaction, mint a short-lived GET URL pinned to the recorded
   object version.
4. In one short transaction, lock
   Deal -> fulfillment/milestone -> evidence, revalidate authority/current
   status/version/immutable snapshot, claim HTTP idempotency, create the job,
   enqueue the contract-valid outbox event, append audit, and record the
   idempotency result.
5. Return 202 after commit. RabbitMQ publication remains relay-owned and
   outside the request transaction.

Inbound sequence:

1. The shared integration listener parses only enough envelope identity to
   route by supported `jobType`; unknown/invalid traffic is rejected without
   payload logging.
2. A shared committed-schema validator validates the exact document or video
   completed/failed schema before business dispatch.
3. The fulfillment handler starts one transaction, records inbox identity,
   locks the job, validates tenant/Deal/subject/job identity and immutable
   evidence snapshot, applies first-terminal-wins state, persists one immutable
   result when completed, and appends audit.
4. Duplicate delivery performs no second business mutation. Contract/identity
   violations roll back inbox and job changes and follow the existing bounded
   retry/dead-letter behavior.

The shared router/validator refactor must preserve document-extraction request,
completed, failed, duplicate, late, invalid-contract, and DLQ behavior.

## 5. Implementation phases

The implementer owns P1-P7 and executes them in order after plan approval.
Passing a phase exit gate means continuing to the next phase. A required AI
contract change, forbidden boundary, or decision missing from ADR-012 is a
`BLOCKED` report, not an invitation to improvise.

### P1 — Reviewed additive Core API contract

Outcome:
The complete Spring/frontend Video Analysis V1 surface is designed and locked
before runtime implementation, while the existing Spring-AI contract remains
byte-for-byte unchanged.

Direction:

- Add both per-evidence endpoints and the closed request/response/error schemas
  described in Section 4.
- Keep every action and result explicitly advisory in descriptions. Do not
  expose technical metadata, storage identifiers/URLs, or event payloads.
- Define exact 202 + `Location`, participant read, buyer-ADMIN mutation,
  non-disclosure, 400/401/403/404/409/422, idempotency, and stale-version
  semantics.
- Add exact validator allowlists for paths, operation IDs, parameters,
  response codes, required headers, required/optional members, closed enums,
  error components/codes, and forbidden AI/business-side-effect fields.
- Update `../../../contracts/README.md` and `../../../contracts/CHANGELOG.md` in the same review
  unit and regenerate committed frontend types only from OpenAPI.
- Do not modify `../../../contracts/schemas`, video fixtures,
  `../../../contracts/asyncapi/m4trust-ai-v1.yaml`, or
  `../../../contracts/openapi/ai-internal-v1.yaml`.

Depends on:
None after ADR-012 and plan approval.

Exit checks:

- Contract validation passes, including expected-invalid public shape/header/
  status/action checks.
- Generated frontend types match committed OpenAPI and no handwritten parallel
  transport type exists.
- A changed-file check proves the shared AI contracts are unchanged.

### P2 — Fulfillment-owned persistence and input boundary

Outcome:
A forward-only persistence foundation safely binds video jobs/results to exact
immutable Slice 12 evidence without reusing document-analysis ownership.

Direction:

- Add the new migration after V20; never edit V15-V20.
- Implement the job/result state and integrity described in Section 4,
  including one active job, one successful result, retry predecessor, same
  evidence/Deal linkage, optimistic version, and immutable result guards.
- Keep query-critical columns relational. Canonical result JSON is the result
  body, not the source of job identity or authorization.
- Add a narrow fulfillment-owned input operation that returns only the exact
  verified evidence snapshot and can mint a version-pinned AI download through
  the existing fulfillment storage adapter.
- Keep storage presign outside transactions and do not expose object key or
  presigned URL through the public API.
- Extend module architecture checks so fulfillment cannot use
  contractintelligence/document repositories or entities.

Depends on:
P1.

Exit checks:

- Migration applies on a clean database and on the accepted V1-V20 chain.
- Persistence tests prove composite ownership, one-active/one-result
  invariants, predecessor integrity, state checks, and result immutability.
- Module-cycle/repository-ownership tests pass.

### P3 — Request/read vertical with frontend queued state

Outcome:
A buyer `ADMIN` can explicitly queue a video job through the real Core API and
all participants can read a refresh-safe `NOT_REQUESTED` or `QUEUED` projection.

Direction:

- Add request/read operation contexts and centralized authorization consistent
  with the exact actor/disclosure matrix.
- Implement preflight presign plus transactional lock/revalidation in the
  Section 4 order. Never hold a transaction while minting storage access.
- Derive the event exclusively from verified evidence:
  evidence ID for `subjectId`/`videoId`, Deal ID for `transactionId`, immutable
  metadata, fixed profile/schema values, empty expected objects, and a deadline
  compatible with the presigned expiry.
- Write job, audit, idempotency result, and outbox atomically. Use the existing
  outbox exchange/routing key exactly.
- Enforce one active/completed job and new-job retry only after failure under
  both application checks and database authority.
- Add frontend API/query/error wrappers from generated types and a
  per-evidence analysis panel. It shows empty/read-only/request/queued/loading/
  backend-error states and polls only while queued.
- The request/retry button renders only from backend `canRequest`; frontend
  does not infer actor eligibility from buyer/seller/status values.

Depends on:
P2.

Exit checks:

- Buyer ADMIN request, participant read, forbidden actors, nonparticipant,
  cross-Deal evidence, non-video/PENDING/non-current evidence, stale version,
  idempotent replay/reuse, and concurrent request cases are server-tested.
- Job/outbox/audit/idempotency rollback together on an injected failure.
- The frontend uses 202/Location semantics, survives refresh, and stops polling
  when the projection is no longer queued.

### P4 — Shared result routing and immutable video result consumption

Outcome:
The single Spring AI-results queue safely dispatches document and video
terminal events, and valid video results become one immutable
`RESULT_AVAILABLE` or `FAILED` projection without touching evidence state.

Direction:

- Refactor the existing listener into an integration-owned job-type router that
  depends only on technical handler interfaces. Integration must not import
  business repositories or decide job state.
- Generalize the existing committed-schema validator once; support exact
  document and video completed/failed schemas without duplicating a handwritten
  JSON validator. Preserve unsupported-vocabulary fail-closed behavior.
- Keep document extraction's existing consumer behavior and tests intact.
- Add a fulfillment handler that performs schema validation before its
  inbox/business transaction, then validates event/job/evidence identity and
  immutable size/hash/object-version snapshot.
- Enforce runtime video semantics documented by the contract, including
  `endMs > startMs`, ranges within result duration, and unique observation/
  anomaly references. Semantic-invalid traffic is an integration violation,
  not evidence rejection.
- Completed persists the canonical result once and marks only the video job
  `RESULT_AVAILABLE`. Failed stores only safe contract code/retry guidance and
  marks only the video job `FAILED`.
- First valid terminal event wins. Duplicates or later conflicting terminal
  events are inboxed/audited as ignored without a second result or state
  mutation.
- Never log raw event payload, video content, presigned URL, provider error, or
  PII.

Depends on:
P3.

Exit checks:

- Video completed/failed, duplicate, conflicting-terminal, identity mismatch,
  immutable-input mismatch, unsupported schema/enum, semantic-invalid range,
  and late result after evidence review are integration-tested.
- Inbox + job/result + audit atomicity is proven.
- Existing document extraction consumer, invalid-message DLQ, and duplicate/
  late-result tests pass after the router/validator refactor.
- No fulfillment, evidence, Deal, payment, settlement, dispute, or provider row
  changes when a video terminal event is applied.

### P5 — Mock AI Worker video capability and advisory result UI

Outcome:
The local worker processes real video commands through RabbitMQ and MinIO, and
participants see safe advisory results/failures in the real frontend.

Direction:

- Extend the current worker's contract registry and dispatch by `jobType`;
  preserve document extraction behavior.
- Consume the existing video queue/binding and publish the existing video
  completed/failed routing keys to the existing events exchange.
- Validate request identity (`subjectId == input.videoId`), fixed profile/output
  schema/version, deadline, VIDEO MP4 metadata, size, and SHA-256.
- Download the exact presigned object and verify size/hash before producing any
  successful result. Run no real video model.
- Use committed video fixture payloads for deterministic success, advisory
  warning, retryable failure, and duplicate-result scenarios. Scenario
  selection remains local/test-only (for example filename/config) and adds no
  production contract field.
- Use the canonical producer service identity from ADR-002/ADR-007 and keep
  logs limited to safe event/job/correlation identifiers.
- Keep the worker local/test-only and make its startup guard reject staging,
  production, and other non-local production-like environments.
- Render duration, observations, anomalies, summary/review reasons, and
  warnings with an explicit “advisory only” message. Unknown open warning/reason
  codes receive a safe generic presentation; the frontend never interprets
  them as acceptance/rejection.
- Show safe failure state and retry only when backend action availability
  allows it. Manual accept/reject controls remain independent and usable.

Depends on:
P4.

Exit checks:

- Worker tests cover document and video request validation, exact object
  download/hash, success, warning, failure, duplicate, deadline/profile/subject
  rejection, routing key, and non-local startup guard.
- Real local RabbitMQ smoke processing works for both job types.
- Frontend result/failure/warning/retry states use generated public types and
  do not know the worker/FastAPI address or internal event schema.

### P6 — Review races, historical visibility, and no-side-effect hardening

Outcome:
Video analysis remains advisory under role, retry, delivery, and manual-review
races, and previous-slice behavior remains stable.

Direction:

- Test request racing accept/reject in both lock orders:
  review-first makes the request ineligible; request-first permits review to
  proceed without waiting.
- Test a valid result arriving after evidence becomes ACCEPTED or REJECTED:
  result remains attached to immutable history and changes no business state.
- Test failure then explicit new-job retry; the failed job remains history and
  successful retry creates exactly one result.
- Test same-key replay, different-request key reuse, active-job double-click,
  duplicate result, and completed/failed terminal races.
- Verify seller ADMIN/MEMBER, buyer MEMBER, other participant, initiator-only,
  and nonparticipant boundaries independently of frontend visibility.
- Verify no auto-job appears after VIDEO finalize and no non-video evidence can
  be analyzed.
- Regress Slice 8 document messaging, Slice 12 evidence download/reject/
  replacement/accept, Deal lifecycle, and module architecture with focused
  checks.
- Do not add casework, payment, deployment, or generic abstractions for future
  job types.

Depends on:
P5.

Exit checks:

- The focused concurrency/authorization/idempotency/no-side-effect matrix
  passes.
- Deal remains ACTIVE, lifecycle FULFILLMENT, and existing fulfillment/evidence
  versions change only through explicit manual actions.
- No release, settlement, refund, provider, dispute, casework, or automatic AI
  side effect exists.

### P7 — Automated validation, final fast check, and review handoff

Outcome:
P1-P6 are proportionally validated and submitted for planner review without
claiming browser acceptance or plan completion.

Direction:

- Run every implementer-owned command in Section 7.
- After full validation, run the final fast check against the complete approved
  base-to-HEAD diff.
- Fix in-scope failures; report `PARTIAL` or `BLOCKED` rather than weakening
  checks or changing contracts/ADR scope.
- Replace `../review/req-review.md` using the implementer workflow.
- Report P1-P7 outcomes, exact validation counts, branch/base/HEAD, migration,
  public contract changes, shared router refactor, and known deviations.
- Set `Plan completion claim: NO`; Section 6 remains planner-owned.
- Do not move the plan, update accepted project state, or run browser
  acceptance.

Depends on:
P6.

Exit checks:

- All required automated commands and the final fast check pass.
- Review handoff is complete, factual, and does not claim unrun browser work.

## 6. Real-browser acceptance — planner-owned

Owner: planner. The implementer must not execute or claim this section. After
P1-P7 review, run against real PostgreSQL, RabbitMQ, MinIO, and the Compose Mock
AI Worker with buyer `ADMIN`, buyer `MEMBER`, and seller contexts.

1. Prepare an `ACTIVE + FUNDED` Deal, start fulfillment as seller, and upload/
   finalize VIDEO + MP4 evidence through the visible direct-storage UI.
2. Keep the worker running briefly and verify finalize alone creates no job:
   the analysis panel is `NOT_REQUESTED`, fulfillment remains
   `REVIEW_REQUIRED`, and no result appears automatically.
3. Seller and buyer MEMBER see the read-only analysis panel but no request
   action. Forced POST requests return 403. A nonparticipant cannot discover
   the evidence/analysis resource.
4. Stop the worker. Buyer ADMIN explicitly requests analysis, receives queued
   UI state, double-click/same-key replay creates one job, and refresh preserves
   `QUEUED`.
5. Restart the worker. It downloads the exact version-pinned MinIO object,
   verifies it, and publishes a warning result. Buyer, seller, and buyer MEMBER
   see the same advisory observations/anomaly/review reasons. No participant
   sees model/provider/native payload or a business-decision label.
6. Confirm the result did not change Deal, lifecycle, fulfillment, milestone,
   evidence, funding/payment, settlement, dispute, or provider state. Buyer
   manual accept/reject is still available.
7. Reject that evidence manually. Its advisory result remains readable in
   immutable REJECTED history. Upload a replacement video and verify the old
   evidence/job/result do not become the replacement's analysis.
8. Run a retryable-failure scenario on the replacement. UI shows safe failure
   and buyer ADMIN retry; retry creates a new job and reaches
   `RESULT_AVAILABLE` while the failed attempt remains durable history.
9. Exercise duplicate-result delivery and verify only one visible/persisted
   result.
10. On another current submitted video, race request against manual accept from
    two contexts. One lock order is accepted: review-first returns an
    eligibility conflict for request, or request-first queues analysis and
    accept completes independently. Any later result remains advisory history.
11. When buyer accepts, fulfillment becomes `COMPLETED`, Deal stays `ACTIVE`,
    lifecycle stays `FULFILLMENT`, and no settlement/release/refund/provider/
    dispute/casework action appears.
12. Recheck document extraction through real RabbitMQ/Mock Worker and a
    non-video Slice 12 evidence path to prove the shared router/worker changes
    did not regress accepted capabilities.

A browser failure produces a planner `FIX` or `REPLAN` decision. Automated
tests do not replace this section.

## 7. Minimum invariants and validation

### Implementer-owned automated invariants

- Only buyer entity ADMIN requests/retries; participant visibility never grants
  mutation authority.
- Subject is the current finalized VIDEO/MP4 evidence; identity, Deal,
  milestone, immutable object version, verified size, and SHA-256 match.
- Client cannot supply or override AI/storage input; expected objects remain
  empty in V1.
- Presign/storage access and RabbitMQ publish do not hold a DB transaction.
- Job + audit + HTTP idempotency + outbox are atomic.
- One queued job and one successful result per evidence are database-enforced.
- Failure retry creates a new linked job; result history is immutable.
- Inbox + first terminal transition + result + audit are atomic and
  duplicate-safe.
- Contract-invalid, semantic-invalid, or identity-invalid events do not become
  evidence rejection or partial results.
- The shared result router dispatches exact document/video job types and safely
  rejects unknown traffic without logging payload.
- Request vs accept/reject uses the established lock order and never blocks
  manual review on AI.
- Any video result leaves Deal, fulfillment, milestone, evidence, payment,
  settlement, dispute, casework, and provider state unchanged.
- Frontend uses generated types, stable codes, polling only while queued, and
  backend-derived actions; absent/unknown actions are read-only.
- Mock scenarios never alter production contracts and the worker cannot start
  in staging, production, or another non-local production-like environment.
- `ModuleArchitectureTest` protects fulfillment result ownership and cycle/
  repository boundaries.

### Required implementer validation

From the repository root:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm run typecheck
npm run build

Set-Location ..
$env:PYTHONPATH='tools/mock-ai-worker/src'
python -m pytest .\tools\mock-ai-worker\tests

docker compose -f .\infra\compose.yaml config
git diff --check
```

Do not run dependency installation unless the local environment genuinely lacks
an already-declared dependency. This plan introduces no new runtime library by
default.

### Final implementer fast check

After P1-P6 and the full validation pass:

- Re-run the contract validator.
- Re-run targeted Slice 13 request/result/migration/router tests and the
  existing document-analysis request/result regression tests by their
  implemented test names.
- Re-run Mock AI Worker tests and frontend typecheck.
- Run `git diff --check`.
- Inspect `git status --short` and the complete approved-base-to-HEAD file list.
- Confirm V15-V20, shared video AI contracts, Railway/deployment, payment/
  provider, settlement, dispute/casework, ready/done plans,
  `../CURRENT.md`, and unrelated user files did not change.

## 8. Done definition

- [x] ADR-012 is human-accepted; ADR index/README/FORBIDDEN are synchronized as
      required
- [x] This eight-section plan is human-approved and moved to `ready/`
- [x] P1 additive Core API contract, exact validator/docs, and generated types
      are complete; shared AI contracts remain unchanged
- [x] New forward-only persistence is complete; V15-V20 remain unchanged
- [x] Fulfillment owns exact evidence-bound jobs/results and module boundaries
      remain acyclic
- [x] Buyer ADMIN explicit request/read/retry, idempotency, lock/revalidation,
      and one-active/one-result behavior work
- [x] Shared result router and committed-schema validator support both job types
      without document extraction regression
- [x] Completed/failed/duplicate/late/invalid events obey inbox atomicity,
      immutable history, and first-terminal-wins behavior
- [x] Mock AI Worker downloads/verifies real video evidence and produces
      contract-valid success/warning/failure/duplicate outcomes locally
- [x] Participant frontend states use the real API and visibly preserve the
      advisory-only/manual-review distinction
- [x] Request-vs-review, authorization, retry, contract, persistence, and
      no-side-effect invariants pass automated validation
- [x] Implementer-owned full validation and final fast check pass
- [x] Implementer reports P1-P7 `COMPLETED` with
      `Plan completion claim: NO`
- [x] Planner independently reviews the real diff and validation evidence
- [x] Planner accepts the combined Section 6 evidence with real PostgreSQL,
      RabbitMQ, MinIO, Mock AI Worker, and browser contexts; the user-directed
      final scoped rerun waiver is recorded as regression debt
- [x] Deal remains ACTIVE/FULFILLMENT and no release, settlement, refund,
      provider, dispute, casework, or automatic business side effect exists
- [x] Planner accepts the complete plan, records the material deviation, moves
      it to `done/`, and updates `CURRENT.md` only if accepted project state
      materially changes

Slice 13 is accepted and archived. Future changes must not rewrite V21 or this
historical done plan; new database needs use a forward-only migration.
