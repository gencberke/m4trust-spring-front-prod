# Review Request

Task: 13-T01
Revision: 9
Plan: docs/plan/ready/13-video-analysis.md
Phases: P1-P7
Status: COMPLETED
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
- P7 — DONE — Full implementer validation (`mvn verify` 290 tests), final fast check, regression truncate fixes for V21 FK tables across 13 legacy integration tests, review handoff. Section 6 browser acceptance not run (planner-owned).

## Validation

- `python contracts/scripts/validate_contracts.py` — PASS
- `services/core-api` `mvnw verify` — PASS (290 tests)
- `npm run typecheck` — PASS
- `npm run build` (frontend) — PASS
- `python -m pytest tools/mock-ai-worker/tests` — PASS (27 tests)
- `docker compose -f infra/compose.yaml config` — PASS
- `git diff --check` — PASS
- Final fast check (Slice 13 + router + document regression) — PASS (63 tests):
  - `VideoAnalysisHardeningIntegrationTest` — 18
  - `VideoAnalysisRequestIntegrationTest` — 14
  - `VideoAnalysisResultConsumerIntegrationTest` — 4
  - `VideoAnalysisMigrationIntegrationTest` — 2
  - `AiResultsRabbitListenerTest` — 1
  - `CommittedEventSchemaValidatorTest` — 3
  - `AnalysisResultConsumerIntegrationTest` — 7
  - `AnalysisRequestIntegrationTest` — 9
  - `ModuleArchitectureTest` — 4
  - `AsyncApiTopologyTest` — 1
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

- Base-to-HEAD: 61 files changed on feature branch (contracts, core-api, frontend, mock worker, tests).
- Migration: `V21__video_analysis.sql` only; V15-V20 unchanged.
- Public contract: additive video-analysis paths/schemas in `contracts/openapi/core-api-v1.yaml`.
- Shared router: document + video terminal dispatch via `integration/messaging`; document extraction regression covered.
- Unchanged by design: `docs/agent/CURRENT.md`, ready/done plans, deployment/Railway, payment/provider/settlement/dispute/casework scope.

## Decisions needed

- None

## Deviation or risk

- P7 required adding V21 fulfillment/video-analysis tables to TRUNCATE lists in 13 pre-existing integration tests so full `mvn verify` passes after migration FK constraints; behavior under test unchanged.
