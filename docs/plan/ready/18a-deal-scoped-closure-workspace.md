# Plan 18A — Deal-Scoped Closure Workspace

- Status: ready — approved as a child of Plan 18 on 2026-07-23.
- Baseline: `main@693add7`.
- Parent: `18-fulfillment-and-closure-simplification.md`.
- Scope type: narrow UI/UX reorganization plus authoritative lifecycle
  projection correction; no settlement contract or persistence change.

## 1. Purpose and user outcome

Give `Kapanış` its own Deal-scoped stage so users do not confuse accepted
delivery with a closed Deal. After fulfillment completion, the current stage
must move from `Teslimat` to `Kapanış`; the existing settlement summary,
simulated release action and operation progress remain the only closure UI.

## 2. Scope and boundaries

In scope:

- Add a sixth Deal workspace area: `closure`, rendered as `Kapanış`.
- Render `DealSettlementPanel` only in that area.
- Keep fulfillment and casework in `Teslimat`.
- Make the post-accept fulfillment notice navigate to `Kapanış`.
- Extend the central Spring lifecycle calculation to emit `SETTLEMENT` for an
  ACTIVE Deal whose fulfillment is `COMPLETED`.
- Preserve actor-visible dispute priority over settlement and terminal Deal
  status priority over all active stages.
- Add focused backend lifecycle and frontend stage/navigation tests.

Out of scope:

- A global navbar link, nested route migration, new page, or deal picker.
- New settlement endpoint/schema/state/action.
- Renaming `canRequestRelease` to `canCloseDeal`.
- Enabling closure from proof existence, upload finalize or frontend time.
- Changing simulated wording or production/payment behavior.

## 3. Decisions and related ADRs

- ADR-003 §16: lifecycle is a backend-derived projection with `SETTLEMENT`
  above `FULFILLMENT`; the frontend does not combine statuses.
- ADR-006 §41: frontend actions come from backend projections.
- ADR-011 §2.5: fulfillment completion leaves Deal ACTIVE.
- ADR-014 §§2.4, 2.8–2.9: explicit buyer-ADMIN simulated release and
  query-verified terminality remain unchanged.
- Plan 17 B4’s existing settlement panel is reused, not redesigned.

Fixed stage mapping:

- `FULFILLMENT` and actor-visible `DISPUTE` → `Teslimat`
- `SETTLEMENT` and `COMPLETED` → `Kapanış`
- `CANCELLED` and `ARCHIVED` → `Anlaşma`; this is an intentional terminal
  summary placement change from the current delivery branch.

ADR-003 §16 lists settlement status among lifecycle inputs and gives an example
priority order. For this plan, the founder-approved product boundary starts the
closure workspace when fulfillment becomes `COMPLETED`, even while settlement
is `NOT_READY` during the contractual window. Therefore Spring intentionally
projects `SETTLEMENT` from authoritative fulfillment completion rather than
waiting for a release operation. This narrows UI stage meaning only; it does not
create settlement state, eligibility or an action.

## 4. Public interface, state and data impact

- No OpenAPI, generated type, endpoint or migration change.
- `DealLifecycleProjection.SETTLEMENT` already exists; this plan makes the
  existing value reachable from authoritative inputs.
- The calculator/Deal projection receives fulfillment status in addition to
  current Deal/funding/dispute inputs.
- Priority for ACTIVE Deals:
  1. actor-visible active dispute → `DISPUTE`
  2. fulfillment `COMPLETED` → `SETTLEMENT`
  3. funding `FUNDED` → `FULFILLMENT`
  4. otherwise → `FUNDING`
- Deal `COMPLETED`, `CANCELLED`, and `ARCHIVED` stay terminal and win before
  ACTIVE-stage logic.

## 5. Implementation phases

### 18A-P1 — Correct the central lifecycle projection

Outcome:
Spring emits `SETTLEMENT` after fulfillment completion without persisting a
new lifecycle field.

Direction:
- Extend the existing calculator and DealService projection call with the
  fulfillment summary.
- Keep the accepted priority order and fail on unknown status input.
- Do not use settlement action availability as lifecycle state.
- Update existing funded-Deal lifecycle assertions that currently expect
  `FULFILLMENT` after fulfillment completion; retain `FULFILLMENT` for
  not-started/in-progress/review-required fulfillment.

Depends on:
None.

Exit checks:
- Focused tests cover funded/not-started, fulfillment active, fulfillment
  completed, active dispute, simulated terminal Deal and other terminal Deals.

### 18A-P2 — Separate the Deal workspace

Outcome:
`Teslimat` and `Kapanış` are separate stage buttons with one clear owner each.

Direction:
- Add `closure` to the existing local workspace-area model; do not create a
  global route or duplicate the settlement panel.
- Move only `DealSettlementPanel` to `closure`.
- Relocate terminal summary messaging out of the current delivery-only branch,
  keep completed/settlement fulfillment history readable, and preserve the
  existing actor-aware casework visibility when `SETTLEMENT`, `COMPLETED`,
  `CANCELLED` or `ARCHIVED` maps to a different area.
- Keep existing server-derived buttons, loading/error/read-only states and
  mandatory simulated labeling.
- Let the fulfillment-success affordance switch the current area to closure;
  do not trigger a backend mutation while navigating.

Depends on:
18A-P1.

Exit checks:
- Refreshing a Deal selects the area matching backend lifecycle.
- Keyboard/button navigation exposes both stages and no panel is duplicated.

### 18A-P3 — Focused validation

Outcome:
The UX split ships without altering settlement behavior.

Direction:
- Run lifecycle unit/integration tests, frontend typecheck/build and focused
  component/browser checks.
- Confirm zero contract/migration diff.

Depends on:
18A-P1–P2.

Exit checks:
- Existing release/window/dispute action tests remain green.

## 6. Real-browser acceptance

1. Before fulfillment completion, both parties land on `Teslimat`; `Kapanış`
   remains separately accessible with a prerequisite/empty state.
2. Seller uploads and buyer accepts evidence. Deal stays ACTIVE, lifecycle
   becomes `SETTLEMENT`, and the UI moves/offers direct navigation to
   `Kapanış`.
3. `Kapanış` shows existing settlement status, deadline, simulation notice and
   only backend-authorized actions.
4. An active dispute presents `DISPUTE`/Teslimat for the entitled parties and
   does not expose casework to unrelated participants.
5. Query-verified simulated settlement changes Deal to COMPLETED and refresh
   selects `Kapanış`.
6. Global application navbar remains unchanged.

## 7. Minimum invariants and validation

- No frontend status combination derives lifecycle or action availability.
- Proof upload/finalize does not move the workspace to closure.
- Only fulfillment completion makes an ACTIVE non-disputed Deal project
  `SETTLEMENT`.
- Active dispute priority and non-disclosure remain actor-aware.
- `DealSettlementPanel` has one render location.
- Existing `canRequestRelease`/`canReconcileRelease` and no-real-money wording
  remain unchanged.
- Focused Core tests, frontend typecheck/build and `git diff --check` pass.

## 8. Done definition

- [ ] Central lifecycle emits tested `SETTLEMENT`
- [ ] Deal workspace contains a distinct `Kapanış` area
- [ ] Settlement panel is removed from `Teslimat` and not duplicated
- [ ] Fulfillment completion copy navigates clearly to closure
- [ ] Loading/error/read-only/responsive/keyboard states pass
- [ ] Existing settlement authorization and simulated labels regressions pass
- [ ] Real-browser acceptance passes for seller, buyer ADMIN and read-only
      participant
