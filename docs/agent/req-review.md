# Review Request

Task: 13-T01
Revision: 11
Plan: docs/plan/ready/13-video-analysis.md
Phases: P1-P7 + browser-acceptance fix (D1, D2, D3)
Status: FIX
Branch: codex/slice-13-video-analysis
Base: main@d342b01
Plan completion claim: NO

## Phase outcomes

- P1 — DONE — Additive Core API OpenAPI contract for video-analysis request/read on finalized VIDEO/MP4 evidence; contract validator and generated frontend types updated.
- P2 — DONE — Forward-only `V21__video_analysis.sql` with evidence-bound jobs/results, one-queued/one-successful partial indexes, and migration integration coverage.
- P3 — DONE — Buyer ADMIN request/read/retry vertical (`VideoAnalysisService`, repository, outbox enqueue, HTTP endpoints, idempotency, eligibility/lock order) with request integration tests.
- P4 — DONE — Shared AI terminal routing (`AiResultsMessageRouter`, `AiResultsRabbitListener`, `AiTerminalResultHandler`); document handler extracted; integration-layer committed-event validator; video terminal consumption ports/adapters.
- P5 — DONE — Mock AI Worker `VIDEO_ANALYSIS` dispatch/fixtures; advisory frontend panel/labels; canonical payload persisted at completion; public DTO built only on read.
- P6 — DONE — Hardening matrix: latch-ordered concurrent review/request races, FAILED-first late COMPLETED, authorization boundaries (seller/buyer member, other participant, initiator-only non-buyer/seller), HTTP VIDEO finalize without auto-job, ACTIVE/FULFILLMENT lifecycle assertions, duplicate-terminal idempotency.
- P7 — DONE — Full implementer validation (`mvn verify` 292 tests), final fast check, regression truncate fixes for V21 FK tables across 13 legacy integration tests, review handoff. Section 6 browser acceptance not run (planner-owned).
- Browser-acceptance fix — DONE — Corrected V21 tenant integrity model and `EvidenceMediaType` wire serialization; added genuine cross-tenant HTTP integration coverage and MockMvc MIME regression tests. Section 6 browser acceptance not re-run here (planner-owned).
- Historical video-analysis visibility fix — DONE — `DealFulfillmentPanel` now renders `EvidenceVideoAnalysisPanel` under every historical VIDEO/`video/mp4` evidence item; request/retry remains gated on backend `canRequest === true` only. Section 6 browser acceptance not re-run here (planner-owned).

## Browser-discovered defects and regression coverage

### D1 — Cross-tenant video-analysis request persistence (HTTP 500)

- **Symptom:** Buyer ADMIN POST `/fulfillment/evidence/{id}/video-analysis` returned HTTP 500 with `DataIntegrityViolationException` when the Deal hosting tenant differed from the seller fulfillment actor tenant (cross-participant deal topology).
- **Root cause:** `V21__video_analysis.sql` required `(fulfillment_id, tenant_id) -> fulfillment(id, tenant_id)` while `VideoAnalysisService` correctly persisted `job.tenant_id` as the Deal hosting tenant used by the canonical AI envelope; `fulfillment.tenant_id` remains the seller actor tenant from `FulfillmentService.start()`.
- **Fix:** Removed the incompatible `(fulfillment_id, tenant_id)` FK and the additive `fulfillment(id, tenant_id)` unique constraint; kept `(deal_id, tenant_id) -> deal(id, tenant_id)` and `(fulfillment_id, deal_id) -> fulfillment(id, deal_id)`.
- **Regression:**
  - `VideoAnalysisMigrationIntegrationTest.allowsDealHostingJobTenantWhenFulfillmentUsesSellerActorTenant`
  - `VideoAnalysisCrossTenantIntegrationTest.crossTenantSellerFulfillmentVideoAnalysisRequestUsesDealHostingTenantForJob` — seller starts fulfillment on separate tenant; buyer ADMIN request returns 202; asserts `job.tenant_id == deal hosting tenant`, `fulfillment.tenant_id == seller tenant`, and atomic delta for job/outbox/`VIDEO_ANALYSIS_REQUESTED` audit/idempotency without HTTP 500.
- **Removed:** `VideoAnalysisRequestIntegrationTest.crossTenantBuyerAdminRequestUsesActorTenantForAuditAndDealTenantForJob` (JDBC-seeded fulfillment under Deal tenant; did not exercise real seller start path).

### D2 — EvidenceMediaType response serialization (`VIDEO_MP4` vs `video/mp4`)

- **Symptom:** GET `/fulfillment` responses serialized `EvidenceMediaType` enum constant names (`VIDEO_MP4`, `APPLICATION_PDF`) instead of committed MIME wire values; frontend video panel gate (`currentEvidence.mediaType === "video/mp4"`) never rendered.
- **Root cause:** Jackson default enum serialization on response DTOs using `EvidenceMediaType`.
- **Fix:** `@JsonValue` on `EvidenceMediaType.value()`; no OpenAPI or generated type change.
- **Regression:** `FulfillmentIntegrationTest.fulfillmentResponsesSerializeEvidenceMediaTypeAsMimeWireValues` — MockMvc asserts finalized VIDEO evidence returns `"video/mp4"` and PDF evidence returns `"application/pdf"`.

### D3 — Historical VIDEO/MP4 evidence hides video-analysis panel

- **Symptom:** After evidence moved to ACCEPTED/REJECTED history, prior VIDEO/`video/mp4` submissions showed no video-analysis status/result even when backend retained read access and advisory data.
- **Root cause:** `DealFulfillmentPanel` rendered `EvidenceVideoAnalysisPanel` only for `currentEvidence` in `SUBMITTED` state; historical list showed summary/download only.
- **Fix:** Render `EvidenceVideoAnalysisPanel` beneath each historical evidence item where `evidenceType === "VIDEO"` and `mediaType === "video/mp4"`, skipping the active `currentEvidence` row to preserve existing SUBMITTED behavior without duplicate panels. Panel continues to show backend status as-is, poll only while `QUEUED`, and expose request/retry solely when `availableActions.canRequest === true`.
- **Regression:** Frontend-only; `npm run typecheck` and `npm run build` on focused `DealFulfillmentPanel` change; no OpenAPI/generated-type drift.

## Validation

- `python contracts/scripts/validate_contracts.py` — PASS
- `services/core-api` `mvn verify` — PASS (292 tests)
- `npm run typecheck` — PASS
- `npm run build` (frontend) — PASS
- `python -m pytest tools/mock-ai-worker/tests` — PASS (27 tests)
- `docker compose -f infra/compose.yaml config` — PASS
- `git diff --check` — PASS
- Focused historical visibility fix (frontend-only) — PASS:
  - `npm run typecheck` — PASS (no generated-type drift in `src/generated/core-api.d.ts`)
  - `npm run build` — PASS
  - `git diff --check` — PASS
- Focused browser-acceptance fix tests — PASS:
  - `VideoAnalysisCrossTenantIntegrationTest` — 1
  - `VideoAnalysisMigrationIntegrationTest` — 3 (includes D1 migration boundary)
  - `FulfillmentIntegrationTest#fulfillmentResponsesSerializeEvidenceMediaTypeAsMimeWireValues` — 1
- Final fast check (Slice 13 + router + document regression) — PASS (65 tests):
  - `VideoAnalysisHardeningIntegrationTest` — 18
  - `VideoAnalysisRequestIntegrationTest` — 13
  - `VideoAnalysisResultConsumerIntegrationTest` — 4
  - `VideoAnalysisMigrationIntegrationTest` — 3
  - `VideoAnalysisCrossTenantIntegrationTest` — 1
  - `AiResultsRabbitListenerTest` — 1
  - `CommittedEventSchemaValidatorTest` — 3
  - `AnalysisResultConsumerIntegrationTest` — 7
  - `AnalysisRequestIntegrationTest` — 9
  - `ModuleArchitectureTest` — 4
  - `AsyncApiTopologyTest` — 1
  - `FulfillmentIntegrationTest#fulfillmentResponsesSerializeEvidenceMediaTypeAsMimeWireValues` — 1
- RabbitMQ smoke (prior revision, unchanged):

```powershell
docker compose -f infra/compose.yaml --profile mock-ai build mock-ai-worker
docker compose -f infra/compose.yaml up -d rabbitmq
docker compose -f infra/compose.yaml --profile mock-ai up -d mock-ai-worker
$env:PYTHONPATH='tools/mock-ai-worker/src'
python tools/mock-ai-worker/tests/smoke_rabbitmq.py
```

Result: `RabbitMQ smoke PASS: document and video download plus completed and failed publishes`

## Scope confirmation

- Base-to-HEAD: 76 files changed on feature branch (contracts, core-api, frontend, mock worker, tests, req-review).
- Migration: `V21__video_analysis.sql` corrected on feature branch (not merged history); V15-V20 unchanged.
- Public contract: unchanged in this fix; additive video-analysis paths/schemas remain in `contracts/openapi/core-api-v1.yaml`.
- Shared router: document + video terminal dispatch via `integration/messaging`; document extraction regression covered.
- Unchanged by design: `docs/agent/CURRENT.md`, ready/done plans, deployment/Railway, payment/provider/settlement/dispute/casework scope, OpenAPI/AsyncAPI/fixture/AI schema.

## Decisions needed

- None

## Deviation or risk

- P7 required adding V21 fulfillment/video-analysis tables to TRUNCATE lists in 13 pre-existing integration tests so full `mvn verify` passes after migration FK constraints; behavior under test unchanged.
- V21 tenant model now allows `fulfillment_video_analysis_job.tenant_id` (Deal hosting) to differ from `fulfillment.tenant_id` (seller actor); integrity is enforced via deal and fulfillment/deal FKs plus service-layer tenant selection for AI envelope/outbox/audit.
