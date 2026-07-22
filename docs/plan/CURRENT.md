# Current Project State

Last updated: 2026-07-22

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

Slice 7 Railway Staging Deployment is accepted under `docs/plan/done/`.
Main-bound immutable core/web deployments, public HTTPS edge with private Core
API/PostgreSQL, one-shot Flyway pre-deploy, isolated migration failure gate,
schema-compatible immutable rollback, release identity, secret/network checks,
and the real two-context browser/security flow passed. Evidence:
`docs/plan/done/review/slice-07-acceptance-2026-07-21.md`. RabbitMQ, object storage and AI
workers remain outside this staging slice.

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

Slice 11B-A Moka Provider Foundation is accepted under `docs/plan/done/`.
A separate deterministic HTTP emulator, bounded Moka authentication/money
transport, the existing durable funding port over real HTTP, query-first
timeout recovery and probe-only pool transport passed focused validation. This
local/CI evidence is not real-provider proof; after the founder's scope change
it supports G1-S simulation safety and never authorizes real-provider behavior.
Evidence: `docs/plan/done/review/slice-11b-a-acceptance-2026-07-22.md`.

Slice 12 Fulfillment and Evidence is accepted under `docs/plan/done/`.
Seller start, participant-readable fulfillment, private-storage evidence
upload/finalize, immutable history, buyer accept/reject/replacement, terminal
completion, backend-derived lifecycle/actions, and the user-directed minimum
real-browser acceptance path passed against PostgreSQL and MinIO. Fulfillment
completion leaves the Deal ACTIVE and creates no release, settlement, refund,
provider-payment, dispute, or AI side effect. Deployment/provider work remains
deferred.

Slice 13 Video Analysis is accepted under `docs/plan/done/`. Buyer ADMIN can
explicitly request/retry analysis for immutable current VIDEO/MP4 evidence;
participants read safe advisory results, including accepted/rejected history.
Fulfillment-owned jobs/results reuse the Slice 8 outbox/inbox and shared
RabbitMQ result routing, and the local-only Mock AI Worker verifies the exact
version-pinned MinIO object. Results never accept/reject evidence, complete the
Deal, release money, call a provider, or create dispute/casework state. Browser
acceptance found and corrected canonical-result, cross-tenant tenant/FK, MIME
wire-serialization, and historical-panel defects. The previously waived
historical VIDEO/MP4 panel observation was later retired by gate C0 on
2026-07-21 (`docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`).

Slice 14A Dispute and Casework Foundation is accepted under
`docs/plan/done/`. Buyer/seller ADMIN dispute opening, party-only read/comment,
counterparty acknowledgement, opener withdrawal, immutable evidence/video
snapshotting, actor-aware non-disclosing `DISPUTE` lifecycle, V22 authority,
and frontend casework are implemented. The reported contract, backend, focused
regression, frontend, and diff validations passed. The planner-owned real
browser Section 6 matrix that was deferred at 14A closure was completed and
accepted as gate C0 on 2026-07-21
(`docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`).

## Accepted foundations

- ADR-001 through ADR-013 are accepted and remain authoritative.
- Slice 0 platform foundation is accepted under `docs/plan/done/`.
- Slice 1 authentication is accepted under `docs/plan/done/`.
- Slice 2 organization and membership is accepted under `docs/plan/done/`.
- Slice 3 Deal creation and listing is accepted under `docs/plan/done/`.
- Slice 4 Deal invitations and cross-entity participation is accepted under
  `docs/plan/done/`.
- Slice 5 Deal parties and activation readiness is accepted under
  `docs/plan/done/`.
- Slice 6 Document upload is accepted under `docs/plan/done/`.
- Slice 7 Railway Staging Deployment is accepted under `docs/plan/done/`.
- Slice 8 AI Document Extraction is accepted under `docs/plan/done/`.
- Slice 9 Manual Review and RuleSetVersion is accepted under
  `docs/plan/done/`.
- Slice 10 Ratification is accepted under `docs/plan/done/`.
- Slice 11 Funding Foundation is accepted under `docs/plan/done/`.
- Slice 11B-A Moka Provider Foundation is accepted under `docs/plan/done/`.
- Slice 12 Fulfillment and Evidence is accepted under `docs/plan/done/`.
- Slice 13 Video Analysis is accepted under `docs/plan/done/`.
- Slice 14A Dispute and Casework Foundation is accepted under
  `docs/plan/done/`.
- V15–V22 migrations are frozen accepted history; future database changes use
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
  identity, buyer review and fail-closed completion, evidence-bound video
  analysis jobs/results with explicit buyer-ADMIN request/retry, shared
  document/video terminal routing, immutable canonical advisory history,
  dispute casework with immutable opening evidence/video snapshots,
  party-only authorization and disclosure, append-only comments, explicit
  acknowledgement/withdrawal, actor-aware lifecycle projection,
  append-only audit,
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
  accept/reject, replacement, and terminal state, plus current and historical
  VIDEO/MP4 advisory analysis panels with queued polling, safe failure/retry,
  observations/anomalies/warnings, and backend-derived action availability,
  plus a generated-type-driven party-only dispute panel for open/history,
  snapshot, comment, acknowledge, withdraw, stale recovery and evidence access,
  session-expiry handling, and logout against the real Core API.
- `infra/` and `scripts/`: local PostgreSQL, RabbitMQ, and MinIO Compose services
  with health checks, persistent volumes, optional document/video Mock AI
  Worker profile, and PowerShell reset/seed entrypoints.
- `contracts/`: reviewed Core API OpenAPI plus document/video AI
  AsyncAPI/JSON Schema contracts, fixtures and validators.

## Validation state

- Contract validation passes for the accepted Slice 13 additive OpenAPI,
  fixtures, generated types, and all expected-invalid checks.
- Core API `mvn verify` passes 292/292 against Testcontainers PostgreSQL,
  including module architecture, cross-tenant video analysis, messaging,
  ratification/payment/fulfillment, and concurrency tests.
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
  `docs/plan/done/review/slice-10-11-acceptance-2026-07-20.md`.
- Slice 12 planner review and the user-directed minimum real-browser acceptance
  passed on 2026-07-20. The critical seller start → direct MinIO upload/finalize
  → buyer reject → replacement → buyer accept path completed; rejected history
  remained immutable. Targeted reviewer tests passed 17/17 and frontend
  production build passed. Evidence:
  `docs/plan/done/review/slice-12-acceptance-2026-07-20.md`.
- Slice 13 planner review and combined real-browser acceptance passed on
  2026-07-21 against PostgreSQL, RabbitMQ, MinIO, the rebuilt Mock AI Worker,
  Core API, and frontend. Cross-tenant request, participant authorization,
  queued/result/failure/retry/duplicate flows, manual-review races,
  replacement isolation, no-side-effect behavior, and Slice 8/12 regressions
  passed. The user explicitly waived only the final post-fix browser observation
  of the historical VIDEO/MP4 panel; that exact regression debt is recorded in
  `docs/plan/done/review/slice-13-acceptance-2026-07-21.md`.
- Mock AI Worker tests pass 27/27; frontend typecheck/build pass.
- Slice 14A implementer validation reports contract validation passing
  21 schemas and 13 fixtures, Core API `mvn verify` passing 331 tests, the
  focused migration/authorization/concurrency/regression matrix passing,
  frontend typecheck/build passing, V15–V21 unchanged, and `git diff --check`
  passing. The planner did not rerun these commands under the user's closure
  direction.
- Gate C0 (14A §6 + Slice 13 historical VIDEO panel) passed on 2026-07-21
  against the local stack on Deal `DL-0000000017`. Evidence:
  `docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`.
- Slice 7 Railway staging passed on 2026-07-21 at
  `main@832cccab8e6f4e2c32bed8230520bdc76ec9df82`: core/web immutable deploys,
  Flyway V22 pre-deploy, disposable failure gate, compatible rollback,
  release identity, HTTPS/security topology, and the real two-context browser
  flow passed. Evidence: `docs/plan/done/review/slice-07-acceptance-2026-07-21.md`.
- Gates G2/G3 were accepted on 2026-07-21. G2's former test-merchant-pool route
  was superseded on 2026-07-22 by the accepted simulation-only decision; no
  Moka account, credential or provider call exists in scope. Production legal/
  KYC/custody/fee/split/payout questions remain explicitly unresolved.
  Ratification compatibility
  uses additive schema v2 with immutable `disputeWindowDays` and a new
  server-owned fulfillment `completedAt`; schema v1 remains readable and
  permanently release-ineligible. Evidence:
  `docs/plan/planning/gates/g2-g3-founder-decision-2026-07-21.md`. Current payment/
  release authority: `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`.

## Not yet stable or accepted

- FastAPI AI service skeleton

## Active work

Slice 11B-A is accepted and merged into `main`. The real-provider Slice 11B-B/
G1 route is superseded by the founder's simulation-only decision. G1-S and
C3–C4/G4c are accepted for simulation scope. C6 ADR-014 and C7 Slice 14B ready
remain blocked, so Slice 14B has no implementation authority yet.

## Known blockers

No external credential/provider blocker remains. ADR-014 must still close
simulation state, race, authorization, cardinality, idempotency, lock and
transaction decisions. No open 14A/Slice 13 browser acceptance debt remains.

## Next likely capability

The next work is ADR-014 for simulation-only settlement/release. After ADR-014
is accepted, the eight-section Slice 14B plan must receive separate ready
approval before any implementation task packet is issued.

## Update rule

Update this file only when accepted project state materially changes. Keep it
short and factual. Do not use it as a backlog, architecture document, or
conversation transcript.
