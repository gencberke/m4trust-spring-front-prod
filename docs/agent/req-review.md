# Review Request
Task: 14A
Revision: 8
Plan: docs/plan/ready/14a-dispute-and-casework-foundation.md
Phases: P6, P7
Status: COMPLETED
Branch: feature/14a-dispute-casework-foundation
Base: main@dbcad17949b9063b9ef385a858f728d1d0f94536
Plan completion claim: NO

## Phase outcomes
- P1 — DONE — Added Slice 14A casework OpenAPI paths/schemas, Deal casework projection, validator allowlists, README/CHANGELOG, and regenerated `frontend/src/generated/core-api.d.ts`; `python3 contracts/scripts/validate_contracts.py` PASS.
- P2 — DONE — Added `V22__dispute_casework_foundation.sql`, casework aggregates/repositories, `CaseworkSourcePorts` with deal/fulfillment adapters, `ModuleArchitectureTest` updates; compile PASS and `ModuleArchitectureTest` PASS. `DisputeCaseworkMigrationIntegrationTest` added but requires Docker/Testcontainers locally.
- P3 — DONE — Added `DisputeController`, `DisputeService`, DTOs, `DisputeQuery`, `CaseworkExceptions`/`CaseworkExceptionHandler`, `RequestedOperation` dispute ops, deal/fulfillment projection adapters, repository pagination/bulk insert, and `DisputeIntegrationTest`. Review deltas applied: V22 REJECTED snapshot lifecycle, actor tenant FK integrity, cross-evidence video trigger, insert guard for `RESOLVED`, monotonic version/status mutation guard, video job lock-all-then-pin adapter with concurrent race tests, open auth 404 vs 403 split, semantic validation codes, idempotency-after-lock order, and OpenAPI `DisputeOpenForbidden` clarification.
- P4 — DONE — Added paginated comment list/create, acknowledge, and withdraw endpoints with DTOs, `DisputeCommentQuery`, versioned case-lock mutations, idempotency, audit, `UserDisplayNames` port/adapter, forbidden exception mappings, and expanded `DisputeIntegrationTest` lifecycle/concurrency coverage; `mvn test-compile` PASS and `ModuleArchitectureTest` PASS. `DisputeIntegrationTest` requires Docker/Testcontainers locally.
- P5 — DONE — Added `CaseworkDealProjectionPort` with adapter, `DealCaseworkSummary`, `canOpenDispute` on `DealAvailableActions`, actor-aware `DISPUTE` lifecycle overlay in `DealService`/`DealLifecycleProjectionCalculator`, and Deal projection tests in `DealStatusTest`, `DisputeIntegrationTest`, and updated Deal service unit tests; `DealStatusTest` PASS and `ModuleArchitectureTest` PASS. `DisputeIntegrationTest` requires Docker/Testcontainers locally.
- P6 — DONE — Added `frontend/src/features/casework/` API/query/error layer and `DealCaseworkPanel` after fulfillment on Deal detail with backend-driven open/comment/acknowledge/withdraw actions, paginated comments, immutable snapshot evidence download reuse, withdrawn history, idempotency/retry/stale handling, and casework panel styles. Review deltas applied: wrapper/body split so hooks run unconditionally; panel visibility driven by dispute list authority (`partyReader` / HTTP 200) including empty state for MEMBER; field validation messages mapped from stable codes (`REQUIRED`, `OUT_OF_RANGE`, `INVALID_ENUM`) instead of server `message`; `npm run typecheck` PASS and `npm run build` PASS.
- P7 — DONE — Hardened casework invariants in `DisputeIntegrationTest`: open-vs-evidence accept/reject lock-order races (both winners), both video-terminal/open job-lock winners, late video result does not mutate opened snapshot, comment/acknowledge/withdraw leave Deal/fulfillment/evidence/funding/payment/outbox/AI state unchanged, and fulfillment review remains available during an active dispute. Updated integration-test TRUNCATE cleanup lists for V22 across Slice 12/13 suites. Fixed P5 Deal response size expectation (`casework` field), V22 migration-test fixtures (unique deal references, WITHDRAWN timestamp ordering, cross-tenant actor user, video media/completed_at constraints), and concurrent same-key video hardening tolerance. Full matrix PASS: 99 tests.

## Validation
- `python3 contracts/scripts/validate_contracts.py` — PASS (unchanged from P1)
- `./mvnw -Dtest=DisputeIntegrationTest,DealStatusTest,ModuleArchitectureTest,DisputeCaseworkMigrationIntegrationTest,VideoAnalysisHardeningIntegrationTest,FulfillmentIntegrationTest,PaymentFundingIntegrationTest,DealIntegrationTest test` — PASS (99 tests)
- `npm run typecheck` — PASS (from P6)
- `npm run build` — PASS (from P6)
- AI/messaging/payment contract byte-identity — NOT_RUN (pending P8)

## Decisions needed
- None

## Deviation or risk
- None
