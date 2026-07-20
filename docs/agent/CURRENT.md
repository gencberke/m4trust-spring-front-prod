# Current Project State

Last updated: 2026-07-20

## Phase

Slice 0 platform foundation and Slice 1 authentication are merged into `main`
and accepted under `docs/plan/done/`.

Slice 2 organization and membership is accepted under `docs/plan/done/`.
Contract, backend, frontend, and the real two-browser end-to-end isolation flow
passed; the accepted branch is merged into `main`.

Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
Contract, backend, frontend, automated validation, and the real browser
stale-version and two-profile participant-isolation flows passed.

Slice 4 Deal invitations and cross-entity participation is accepted under
`docs/plan/done/`. Contract design review, expand/switch migrations, invitation
state machine, reusable idempotency, backend/frontend validation, and the real
two-browser acceptance flow passed.

Slice 5 Deal parties and activation readiness is accepted under
`docs/plan/done/`. Buyer/seller assignment, participant-only validation,
explicit initiator authorization, backend-derived party action availability,
optimistic stale-version recovery, and the real two-browser acceptance flow passed.
Deal remains DRAFT in this slice; no activation endpoint or action exists.

Slice 6 Document upload is accepted under `docs/plan/done/`. Direct
browser-to-private-storage upload (client SHA-256 → intent → direct PUT to
MinIO → finalize), storage-verified size/checksum, immutable object versioning,
atomic finalize (AVAILABLE + previous SUPERSEDED + Deal current-document pointer
+ audit + idempotency in one transaction), same-Deal pointer integrity enforced
at the DB level, initiator-only mutation with participant read/download,
history and short-lived download links, and the real two-browser acceptance
flow (§7.1–7.8, initiator + participant) passed against real MinIO.

Slice 8 AI Document Extraction is accepted under `docs/plan/done/`. The
OpenAPI analysis surface, transactional outbox, idempotent inbox, RabbitMQ
topology, contractintelligence flow, local-only Mock AI Worker, lifecycle
projection and frontend analysis view are implemented. Real-browser acceptance
§7.1–§7.8 passed with real RabbitMQ and the Compose worker; successful results
always land at REVIEW_REQUIRED.

Slice 9 Manual Review and RuleSetVersion is accepted under
`docs/plan/done/`. Initiator review, typed rule corrections/exclusions/manual
rules, immutable version history, atomic acceptance, document supersession,
participant read-only visibility and real two-session concurrency acceptance
passed against PostgreSQL, RabbitMQ, MinIO and the Compose Mock AI Worker.

Slice 10 Ratification is accepted under `docs/plan/done/`. Immutable canonical
package snapshots, RFC 8785/JCS content hashing, structured commercial terms,
entity-scoped ADMIN approvals, rejection/supersession, atomic RATIFIED+ACTIVE
and the real two-browser race matrix passed.

Slice 11 Funding Foundation is accepted under `docs/plan/done/`. Explicit
single-plan/single-unit funding, ratification-package provenance, durable
dispatch, two-layer idempotency, query-first reconciliation, local-sandbox
SUCCESS/DECLINE/TIMEOUT_THEN_SUCCESS flows and FUNDING→FULFILLMENT projection
passed. Release, payout, refund, settlement and a real provider remain out of
scope.

Slice 12 Fulfillment and Evidence is accepted under `docs/plan/done/`.
Seller start, participant-readable fulfillment, private-storage evidence
upload/finalize, immutable history, buyer accept/reject/replacement, terminal
completion, backend-derived lifecycle/actions, and the user-directed minimum
real-browser acceptance path passed against PostgreSQL and MinIO. Fulfillment
completion leaves the Deal ACTIVE and creates no release, settlement, refund,
provider-payment, dispute, or AI side effect. Deployment/provider work remains
deferred.

## Accepted foundations

- ADR-001 through ADR-011 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- Slice 1 authentication is accepted under `docs/plan/done/`.
- Slice 2 organization and membership is accepted under `docs/plan/done/`.
- Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
- Slice 4 Deal invitations and cross-entity participation is accepted under
  `docs/plan/done/`.
- Slice 5 Deal parties and activation readiness is accepted under
  `docs/plan/done/`.
- Slice 6 Document upload is accepted under `docs/plan/done/`.
- Slice 8 AI Document Extraction is accepted under `docs/plan/done/`.
- Slice 9 Manual Review and RuleSetVersion is accepted under
  `docs/plan/done/`.
- Slice 10 Ratification is accepted under `docs/plan/done/`.
- Slice 11 Funding Foundation is accepted under `docs/plan/done/`.
- Slice 12 Fulfillment and Evidence is accepted under `docs/plan/done/`.
- V15–V20 migrations are frozen accepted history; future database changes use
  new versioned migrations.
- The Spring–AI contract foundation, schema fixtures, validators, AsyncAPI, and
  public OpenAPI foundation exist under `contracts/`.
- The system direction remains Vite/React/TypeScript + Spring Boot modular
  monolith + PostgreSQL + separate FastAPI AI API/workers + RabbitMQ +
  S3-compatible object storage.

## Current repository shape

- `services/core-api`: Java 21 / Spring Boot 4.1 with PostgreSQL JDBC, Flyway,
  identity/session authentication, tenant provisioning, legal entities,
  memberships, Deals, cross-tenant participant visibility, email-based Deal
  invitations, reusable HTTP idempotency, state/optimistic-lock enforcement,
  buyer/seller participant integrity and explicit initiator-only party
  assignment, direct-to-storage document upload (unpredictable object keys,
  presign/verify outside DB transactions, atomic finalize with same-Deal
  current-document pointer integrity, immutable object versioning, initiator-only
  mutation via a narrow deal port with participant read/download, history and
  short-lived download links), immutable ratification packages and approvals,
  atomic Deal activation, provider-independent funding plans/units/payment
  operations, durable payment dispatch and query-first reconciliation,
  fulfillment/milestone/evidence state with immutable private-storage object
  identity, buyer review and fail-closed completion, append-only audit,
  centralized legal-entity authorization, Problem Details, structured logging,
  health probes, and module-cycle checks.
- `frontend`: generated OpenAPI types, TanStack Query-backed authentication and
  organization state, protected routing, legal-entity create/list/switch/detail
  and member views, Deal create/list/detail/update/cancel views, incoming Deal
  invitations, acceptance entity selection, participant and pending-invitation
  projections, buyer/seller role visibility and backend-derived party-management
  availability, filtering, sorting and pagination, stale-version recovery,
  versioned per-tab selection, centralized scoped request headers,
  a Deal document management view (select → client SHA-256 → intent → direct PUT
  → finalize with progress/retry/expired-intent recovery, current document and
  SUPERSEDED history, download links, and a read-only card for non-initiator
  participants), ratification package creation/approve/reject/history and
  actor-aware funding/payment/reconciliation panels, and a fulfillment panel
  for seller start, direct evidence upload/finalize, history/download, buyer
  accept/reject, replacement, and terminal state,
  session-expiry handling, and logout against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, optional Mock AI Worker profile, and
  PowerShell reset/seed entrypoints.
- `contracts/`: reviewed Core API OpenAPI and AI extraction AsyncAPI/JSON Schema
  contracts, fixtures and validators.

## Validation state

- Contract validation passes for the accepted Slice 12 additive OpenAPI,
  fixtures, generated types, and all expected-invalid checks.
- Core API `mvn verify` passes 250/250 against Testcontainers PostgreSQL,
  including module architecture and ratification/payment/fulfillment
  concurrency tests.
- Frontend `npm run typecheck` and production `npm run build` pass.
- Slice 4 invitation/participation regression and Slice 5 real two-browser
  acceptance passed on 2026-07-18.
- Slice 6 document upload real two-browser acceptance (§7.1–7.8) passed against
  real MinIO on 2026-07-18: direct browser PUT with CORS, supersede, download
  objectVersion pinning, participant read-only + non-initiator mutation 403,
  non-participant non-disclosing 404, and CANCELLED-deal upload block.

- Slice 8 real-browser acceptance passed on 2026-07-19 with real RabbitMQ and
  the Compose Mock AI Worker, including queued recovery, duplicate delivery
  idempotency, retry, supersede, authorization and Slice 6 regression.
- Slice 9 real-browser acceptance passed on 2026-07-19 with initiator and
  participant sessions, field-level 422 recovery, immutable history,
  supersession, a real concurrent acceptance race and responsive read-only UI.
- Slice 10–11 real-browser acceptance passed on 2026-07-20 with buyer ADMIN,
  second buyer ADMIN, buyer MEMBER and seller ADMIN sessions. MONEY suggestion,
  immutable hash, approve/reject/supersede/withdraw races, ACTIVE mutation
  closure, SUCCESS/DECLINE/TIMEOUT_THEN_SUCCESS, retry/reconcile and actor
  visibility flows passed. Evidence:
  `docs/agent/slice-10-11-acceptance-2026-07-20.md`.
- Slice 12 planner review and the user-directed minimum real-browser acceptance
  passed on 2026-07-20. The critical seller start → direct MinIO upload/finalize
  → buyer reject → replacement → buyer accept path completed; rejected history
  remained immutable. Targeted reviewer tests passed 17/17 and frontend
  production build passed. Evidence:
  `docs/agent/slice-12-acceptance-2026-07-20.md`.
- Mock AI Worker tests remain 14/14; frontend typecheck/build pass.

## Not yet stable or accepted

- FastAPI AI service skeleton
- Railway service configuration
- Slice 13 Video Analysis

## Active work

No content slice is currently active. Slice 12 is accepted on
`codex/slice-12-fulfillment`; its ready plan is archived to `done/` and V20 is
frozen accepted history. Railway staging and real-provider integration remain
explicitly deferred until the user reopens that work.

## Known blockers

No architectural, implementation, or acceptance blocker.

## Next likely capability

Plan Slice 13 Video Analysis as the next content capability, using Slice 12's
immutable evidence version as input and preserving the advisory-only AI
boundary. Deployment and real-provider work are not part of the active content
line.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
