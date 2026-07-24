# Plan 17 — Living Fulfillment Experience and Demo-Scoped Simulated Settlement

- Status: done — accepted on main 2026-07-24. Implementation merged at
  `main@693add7` (Plan 17 A–B4: living fulfillment and simulated settlement).
  Founder approval recorded 2026-07-23; implementation is on `main`. Plan body
  preserved for history; no further implementation authority.
- Baseline: `main@d3a820d` (staging-simulated funding enablement merged; Railway
  staging runs this SHA with profiles `staging,staging-simulated`, payment
  dispatch relay enabled; Railway production remains on `23a4428` with payment
  fully inert).
- Decision authority: ADR-014 (Accepted) fixes settlement/release semantics,
  states, authorization, dispute races, locks, transactions, and query-first
  recovery. ADR-011, ADR-013, ADR-022, G2/G3, and the 2026-07-22
  simulation-only payment decision remain binding. ADR-019 AI ownership is
  untouched by this plan.
- Relationship to `docs/plan/planning/14b-settlement-and-release.md`: that
  draft's technical content remains reference input, but its pre-ADR-022
  preconditions (`MAIN-PROD-READY`, seven-day production pilot exit) were
  written for the withdrawn broad-production route. The founder's 2026-07-23
  approval re-gates the settlement/release work to the ADR-022 controlled-demo
  scope defined here. This plan is the authorizing document; the planning draft
  is superseded for scope and gating.

## 0. Founder decisions recorded 2026-07-23

1. **Dispute window range 0..365.** ADR-014 §2.2's `disputeWindowDays`
   `1..365` range is extended by founder decision to `0..365`. `0` means the
   release becomes eligible immediately at fulfillment `completedAt`
   (`deadline = completedAt + 0 * 24h`). The `1..365` behavior is unchanged.
   Phase B1 adds a dated amendment note to ADR-014 §2.2 recording this.
2. **Environment scope: staging only.** The existing `staging-simulated`
   in-process sandbox provider is the only simulator used. No separate demo
   simulator service is built; Railway production is not enabled and not
   modified by this plan. Production enablement remains a separate future
   founder decision under ADR-014's private-demo-simulator rule.
3. **Closure separation is explicit.** Buyer evidence acceptance completes the
   FULFILLMENT only; the Deal remains `ACTIVE`. The Deal closes
   (`ACTIVE -> COMPLETED`) exclusively when the simulated release reaches
   query-verified `SIMULATED_SETTLED`. UI, API, and audit must keep these two
   moments visibly distinct at all times.

## 1. Goal

Make the fulfillment tab feel alive for both parties (live updates, clear
turn-taking, explicit post-action feedback), then implement the demo-scoped
settlement/release flow so that, after buyer evidence acceptance and an
elapsed (possibly zero) dispute window, the buyer ADMIN explicitly releases
the simulated funds and the Deal visibly closes as `COMPLETED` — all on
Railway staging, with mandatory `SIMULATED` labeling and zero real-money
claims, without breaking the currently deployed system.

## 2. Hard constraints (do not break the running demo)

- All API changes are additive to `/api/v1`; no existing field, status value,
  or endpoint changes meaning. Existing consumers keep working unchanged.
- New Flyway migrations are `V23+`, forward-only, additive-only (new tables /
  nullable columns). `V15–V22` stay frozen. No destructive migration, no
  `flyway clean`.
- Railway production stays on its current deployment until the separately
  approved production transition; nothing in this plan requires touching it.
  All new behavior is inert without the `staging-simulated` profile because it
  is chain-gated: no simulator → no `FUNDED` → no fulfillment → no settlement.
- Existing fail-closed guards (`SandboxPaymentProviderBootstrapGuard`,
  messaging guard) are not weakened. Production + sandbox still fails startup.
- AI repository, shared AsyncAPI/JSON-Schema contracts, and RabbitMQ topology
  are untouched (ADR-019).
- Real provider, real money, refund/reversal/chargeback, custody/KYC/fee
  claims stay out of scope (ADR-014 §2.9, simulation-only decision §6).

---

## Phase A — Living fulfillment experience (frontend-only)

No backend, contract, or migration change. Zero deployment risk beyond a
web-edge rebuild.

- **A1 Live updates.** `DealFulfillmentPanel` (and the deal-detail fulfillment
  summary) poll while the fulfillment is in a non-terminal, counterparty-
  actionable state (`IN_PROGRESS`, `EVIDENCE_REQUIRED`, `REVIEW_REQUIRED`),
  following the existing analysis-panel `refetchInterval` idiom (~5 s), so a
  counterparty action appears without manual reload. Polling stops in terminal
  states.
- **A2 Whose-turn banner.** A single status strip at the top of the panel
  states, per role and status, whose action is awaited — derived ONLY from the
  backend `availableActions` and status projections (frontend invents no
  rule). Examples: seller sees "Sıra karşı tarafta: alıcı kanıtı inceliyor",
  buyer sees "Sıra sizde: kanıtı inceleyin".
- **A3 Post-action feedback.** After evidence finalize: explicit notice
  "Kanıt gönderildi — alıcının incelemesi bekleniyor". After reject: guidance
  that a replacement upload is expected. After accept: notice that delivery is
  complete and closure continues in the settlement step (links Phase B).
- **A4 Evidence timeline.** The existing evidence history renders as a
  chronological timeline (submitted / rejected / accepted entries with
  timestamps and status badges), reusing current markup idioms.
- **A-validation.** `npm run typecheck` + production build; a two-session
  (seller + buyer) manual browser pass on staging verifying live update,
  turn banner correctness for every non-terminal status, and the three
  feedback notices. No Core redeploy required.

Acceptance gate A: both sessions observe each other's actions within one poll
interval without reload; every banner statement matches backend-derived
actions; build artifacts deploy to staging web-edge only.

---

## Phase B — Demo-scoped simulated settlement and release

Implements the ADR-014 flow on staging with the existing sandbox provider.
Sub-phases are ordered and each ends in a reviewable state.

### B1 Contract-first surface

- Add to `core-api-v1.yaml` (additive):
  - `GET  /deals/{dealId}/settlement`
  - `POST /deals/{dealId}/settlement/release`
  - `GET  /release-operations/{operationId}`
  - `POST /release-operations/{operationId}/reconcile`
  - Schemas per ADR-014 §2.3/§2.5: `SettlementDetail` (status
    `NOT_READY|READY|PROCESSING|ON_HOLD|SIMULATED_SETTLED|FAILED`),
    `ReleaseOperation` (status `QUEUED|PROCESSING|RECONCILIATION_REQUIRED|
    SIMULATED_SETTLED|SIMULATED_DECLINED|FAILED_BEFORE_DISPATCH`),
    backend-derived `canRequestRelease`/`canReconcileRelease`, and the closed
    error family (missing window, window not elapsed, active dispute,
    forbidden actor, hidden resource, stale versions, operation exists,
    reconciliation unavailable, unknown outcome, terminal settlement,
    idempotency reuse) with exact code names fixed in this review unit.
  - Every settlement/release response carries `mode` (`DEMO_SIMULATED`,
    reusing the Slice-16 `PaymentSimulationMode` schema) — never null when a
    settlement exists, because settlement is unreachable without the
    simulator.
  - `DealStatus` documents `COMPLETED` as reachable; `DealDetail` gains an
    optional null-tolerant `settlement` summary.
  - Ratification create request: optional `disputeWindowDays` integer
    `0..365` producing a `schemaVersion=2` snapshot (G3 semantics, RFC
    8785/JCS preserved); v1 packages stay readable and release-ineligible
    with a distinct error code.
- Update `validate_contracts.py` closed-set checks for the new schemas;
  record everything in `contracts/CHANGELOG.md`.
- Add the dated founder amendment note (0..365) to ADR-014 §2.2 in the same
  change.

Gate B1: contract validation passes; generated frontend types compile; no AI
contract file touched.

### B2 Settlement domain and simulated release transport

- Migrations `V23+`: `settlement`, `release_operation` (immutable attempt
  history), durable release dispatch rows; one settlement per Deal/funding
  unit, at most one lifetime release operation per settlement (DB
  cardinality constraints per ADR-014 §2.3/§2.6).
- `payment` module owns the aggregate. Eligibility re-verified under the
  ADR-014 §2.6 lock order (Deal → FundingPlan/Unit → fulfillment/current
  accepted evidence → Settlement/ReleaseOperation → active disputes in ID
  order): Deal `ACTIVE`, current package v2 `RATIFIED`, funding
  `FUNDED` in `DEMO_SIMULATED` mode, fulfillment `COMPLETED` with immutable
  `completedAt`, `now >= completedAt + disputeWindowDays*24h`, no
  `OPEN|UNDER_REVIEW` dispute, settlement `READY`, no existing operation.
- Release HTTP transaction atomically writes settlement transition +
  operation + audit + HTTP idempotency result + durable dispatch, returns
  `202 + Location` (ADR-006). Simulator calls happen outside DB
  transactions; result application is a separate short transaction.
- Extend `PaymentProviderPort` (default-null pattern as with `mode()`) with
  release `initiate`/`queryStatus`; implement ONLY in
  `SandboxPaymentProviderAdapter` (profiles `local-sandbox`,
  `staging-simulated` — guard matrix unchanged): deterministic, persistent,
  re-queryable simulated success after a bounded delay; decline/timeout
  scenarios remain startup-config-only for local/CI tests (no public
  scenario control, ADR-014 §2.1).
- Query-first relay per ADR-014 §2.7 reusing the existing payment dispatch
  relay pattern and the same `PAYMENT_DISPATCH_RELAY_*` configuration family
  (relay off ⇒ dispatch dormant ⇒ safe).
- Dispute races per §2.8: dispute-first blocks intent; release-first is never
  assumed cancelled; active dispute defers Deal completion; withdrawal +
  reconcile resumes. Casework is read through the existing narrow gate only.
- Deal `ACTIVE -> COMPLETED` happens only inside the result-application
  transaction after re-verifying query-proved `SIMULATED_SETTLED` and the
  absence of active disputes; audit records the simulated nature. After
  `COMPLETED`, new dispute opening stays blocked (ADR-013 eligibility).

Gate B2: focused tests prove the state machine, eligibility matrix (including
window 0 immediate eligibility and window 1 blocking), both dispute races,
duplicate dispatch/restart idempotency, cardinality constraints, and that
financial `SETTLED`, automatic release, and production sandbox activation are
unreachable. Migrations tested against Testcontainers PostgreSQL.

### B3 Ratification v2 entry point

- Backend accepts optional `disputeWindowDays` (0..365) on package create,
  producing an immutable v2 snapshot; absence keeps v1 behavior.
- Frontend ratification form adds the field with helper text ("0 = itiraz
  penceresi yok; kabulden hemen sonra kapanış mümkün") and shows the window
  on package detail. Existing v1 packages display "kapanışa uygun değil
  (eski şema)" with the distinct error surfaced when release is attempted.

Gate B3: v1/v2 compatibility tests pass; JCS hash parity for v2 verified.

### B4 Settlement UI ("Kapanış" section)

- New deal-detail section visible to all participants (read), actionable only
  per backend-derived actions: settlement status, dispute-window countdown
  (server-derived deadline rendered, never computed from frontend rules),
  buyer-ADMIN release button, operation progress with polling (reusing the
  funding panel poll idiom), reconcile action when
  `RECONCILIATION_REQUIRED`.
- Mandatory labeling everywhere in this section and on the closed deal:
  "Demo simülasyonu — gerçek para hareketi yok"; terminal state renders
  "SIMULATED_SETTLED" semantics as "Kapanış (simüle) tamamlandı"; the Deal
  header shows `COMPLETED` distinctly from fulfillment completion. No
  "paid/settled/funds transferred" wording, no provider logo (ADR-014 §2.9).
- Fulfillment panel (Phase A copy) links here after acceptance: "Teslimat
  tamamlandı — kapanış için Kapanış bölümüne geçin".

Gate B4: typecheck/build; the closure-separation copy reviewed against §0.3.

### B5 Staging deployment and acceptance

- Deploy the merged SHA to staging through the existing pipeline (variables
  unchanged; the relay flags set for funding already serve release dispatch).
  Update staging's `APP_VERSION`/`GIT_COMMIT_SHA`/`BUILD_TIME` labels to the
  deployed SHA while doing so (fixes the known stale-label observation).
- Two-session real-browser acceptance on staging, same day, one deal:
  register/invite → parties → document + AI analysis (live worker) →
  ratification v2 with window 0 → both approvals → `ACTIVE` → funding
  `FUNDED` (simulated, labeled) → seller start + evidence → buyer accept
  (fulfillment `COMPLETED`, deal still `ACTIVE` — screenshot the
  distinction) → buyer release → operation `SIMULATED_SETTLED` → Deal
  `COMPLETED` with simulated labeling — plus negatives: window 1 blocks
  release with the exact error; open dispute blocks release; non-buyer gets
  read-only.
- Rollback at any point: redeploy the previous staging deployment ID;
  additive migrations remain in place harmlessly; no data deletion.

Gate B5 (final): the full same-day closure flow passes on staging with all
labels; production remains untouched on its existing deployment; evidence is
recorded in a review handoff under `docs/plan/done/review/` when the slice is
accepted.

---

## 3. Explicit non-goals

- No production enablement of any simulated payment/release (separate future
  decision; requires ADR-014's private demo simulator).
- No real provider, refund, reversal, chargeback, payout, custody, KYC, fee
  split, or Law 6493 claim.
- No AI-repo or shared AI-contract change; no broker/topology change.
- No broad-production hardening; ADR-022 deferrals stay as accepted.
- No automatic release: window expiry alone never releases (ADR-014 §2.4).

## 4. Validation policy

Per ADR-022 §2.9: each sub-phase runs only its focused contract/unit/
migration/concurrency tests plus frontend typecheck/build. The repository-wide
full suite runs once before the B5 staging acceptance gate.

## 5. Open items deliberately deferred

- Production transition of AI messaging (existing Task #5) proceeds
  independently of this plan and is unaffected by it.
- Video-analysis live smoke still awaits a real MP4 evidence file.
- Production settlement demo (separate simulator) — future founder decision.
