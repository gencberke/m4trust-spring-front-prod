# Slice 13 Acceptance Record — 21 July 2026

## Result

Slice 13 Video Analysis is accepted on `codex/slice-13-video-analysis` at
reviewed implementation HEAD
`cdfb97a4dbb65644a42e16a7c26eb120cf8980c5`, plus the planner-owned closure
documentation in the working tree.

ADR-012 is accepted with this record. V21 is the accepted Slice 13 migration;
V15-V21 are frozen history after merge and any later database change must use a
new forward-only migration.

Railway/staging, a real AI/model provider, production storage/secrets, real
payment-provider work, release, settlement, refund, dispute, and casework remain
outside this acceptance.

## Accepted capability

- Buyer entity `ADMIN` explicitly requests or retries analysis for current,
  finalized VIDEO/MP4 evidence; no analysis is created automatically.
- All Deal participants can read safe advisory status/results, including
  retained accepted or rejected evidence history. Mutation authority remains
  backend-derived and buyer-ADMIN-only.
- Jobs are bound to the exact evidence version, SHA-256, size, Deal hosting
  tenant, fulfillment, milestone, and evidence identity.
- Request persistence, audit, HTTP idempotency, and outbox enqueue are atomic.
- The shared results queue routes document and video terminal events through
  committed-schema validation, inbox idempotency, first-terminal-wins state,
  immutable canonical results, and safe public projection.
- The local-only Mock AI Worker consumes the existing video queue, downloads
  the version-pinned MinIO object, verifies size/hash, and emits deterministic
  success, warning, failure, and duplicate scenarios.
- AI output remains advisory. It never accepts/rejects evidence, completes a
  Deal, releases money, invokes a provider, or creates dispute/casework state.

## Planner review and browser-found corrections

Planner review and real-browser acceptance found and corrected:

1. canonical completed payloads were initially reduced to the public DTO before
   persistence; the full canonical payload is now retained and filtered only on
   public read;
2. the P6 role, terminal-order, finalize/no-auto-job, and lock-order matrix was
   expanded and made deterministic;
3. the original V21 job/fulfillment tenant FK was impossible for genuine
   cross-tenant Deals; job tenant now remains the Deal hosting tenant while
   fulfillment keeps the seller actor tenant, with Deal/fulfillment integrity
   preserved;
4. `EvidenceMediaType` initially serialized enum constant names instead of MIME
   wire values; runtime responses now emit `video/mp4` and `application/pdf`;
5. accepted/rejected VIDEO/MP4 history initially omitted the advisory panel;
   the final frontend fix renders the read-only analysis panel for historical
   video evidence.

## Real-browser acceptance evidence

Acceptance used fresh local PostgreSQL with V1-V21, real RabbitMQ, real MinIO,
the rebuilt Compose Mock AI Worker, the real Core API, and the real React UI.
The tested Deal was genuinely cross-tenant: the Deal hosting tenant differed
from the seller-owned fulfillment tenant.

The combined browser runs proved:

- seller direct-to-MinIO VIDEO/MP4 upload and finalize;
- finalize creates no automatic analysis job;
- seller and buyer MEMBER read-only behavior, forced POST 403, and
  nonparticipant non-disclosing 404;
- buyer ADMIN UI request, queued refresh, idempotent replay, and one-job
  persistence;
- real RabbitMQ/worker/MinIO download and hash verification through
  `RESULT_AVAILABLE`;
- safe advisory observations, anomalies, warnings, summary, and explicit
  advisory-only wording with no technical/provider/storage disclosure;
- reject and immutable historical result retention at the API/data boundary,
  followed by isolated replacement evidence;
- retryable failure UI, explicit new-job retry, retained failed history, and
  eventual successful result;
- duplicate delivery producing one result;
- request-vs-manual-review behavior from independent authenticated contexts;
- manual acceptance completing fulfillment while Deal remains `ACTIVE` with
  lifecycle `FULFILLMENT` and no payment/provider/dispute side effect;
- document extraction through the shared real messaging path and a non-video
  Slice 12 evidence regression.

Automation limitations were recorded rather than hidden: browser sessions were
established through same-origin authenticated fetch when the automated login
form did not fire, and DOM/inner-text evidence was used when screenshot capture
timed out. State-changing product actions remained real UI actions.

## Accepted material deviation and regression debt

After the final historical-panel frontend fix at `cdfb97a`, the user explicitly
directed the planner to skip one more scoped browser rerun and close Slice 13.
Therefore the corrected panel's visibility for a REJECTED/ACCEPTED historical
VIDEO/MP4 row is accepted from repository inspection plus frontend typecheck and
production build, not from a post-fix browser observation.

This is an explicit accepted deviation, not evidence that the browser step ran.
The next browser regression that exercises fulfillment evidence history must
confirm all of the following and retire this debt:

1. the historical VIDEO/MP4 row renders `EvidenceVideoAnalysisPanel`;
2. the retained `RESULT_AVAILABLE` advisory result is visible after reject or
   accept;
3. no request/retry or accept/reject mutation control is exposed for history;
4. the historical panel does not duplicate the current evidence panel.

## Automated validation

- Contract validator — PASS.
- Core API full `mvn verify` — PASS: 292 tests, 0 failures/errors.
- Slice 13 final focused matrix — PASS: 65 tests.
- Planner D1/D2 focused integration verification — PASS: 17 tests.
- Mock AI Worker pytest — PASS: 27 tests.
- Frontend typecheck and production build — PASS, including the final
  historical-panel fix.
- Compose configuration — PASS.
- `git diff --check` — PASS.

## Accepted boundaries

- V21 is accepted; V15-V21 must not be rewritten after merge.
- Existing video JSON Schemas, fixtures, AsyncAPI, and AI-internal OpenAPI remain
  unchanged.
- Video analysis is fulfillment-owned and evidence-bound; document-analysis
  repositories/entities are not reused.
- Deal remains `ACTIVE`, lifecycle remains `FULFILLMENT`, and manual evidence
  review stays independent of AI status or outcome.
- Deployment, production AI/provider integration, settlement/release/refund,
  dispute, and casework require separate user-approved work.
