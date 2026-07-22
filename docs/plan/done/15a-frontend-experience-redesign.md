# Slice 15A — Frontend Experience Redesign

- Status: Done — accepted on 22 July 2026 at implementation commit `fbcbb7f`
- Placement: Slice 15 `G-UI` insertion gate, before Railway P5/P6
- Visual authority: the accepted open agreement-workspace concept; the secondary
  concept is a state-pattern reference only and does not authorize extra navigation

## 1. Purpose and user result

The existing React application becomes a clear, presentation-ready agreement
workspace without changing its business capabilities. A user should understand the
current stage, the next permitted task, the primary action, and the supporting
business information without seeing implementation vocabulary.

The main journey is:

`Agreement → Review → Approval → Payment → Delivery`

The redesign keeps the existing register, login, session, company, invitation and
deal flows. It changes their information architecture and presentation, not their
authority or public contract.

## 2. Scope and boundaries

### In scope

- A quiet authenticated shell with `M4Trust`, `Anlaşmalar`, `Davetler`, company
  switcher and account menu.
- A coherent visual system for typography, spacing, colour, elevation, controls,
  statuses, empty states, loading states and responsive layouts.
- Clearer auth, company selection, invitation and deal-list experiences.
- A stage-led deal workspace with one dominant primary action and progressively
  disclosed secondary and technical information.
- Existing setup, document, contract-analysis, manual-review, ratification,
  funding, fulfillment, video-evidence, dispute and terminal-state capabilities.
- Desktop, tablet and compact mobile layouts; keyboard, focus, status semantics and
  reduced-motion support.

### Out of scope

- Backend, OpenAPI, AsyncAPI, shared-contract, database or migration changes.
- New product areas such as Tasks, Notifications, Archive or Settings.
- A new activity endpoint, calculated frontend lifecycle, new authorization rules or
  invented counterparty data.
- AI provider/model/worker/image/deployment changes or invented AI progress.
- Slice 14B dispute resolution, settlement actions, automated payment-release claims,
  broad-production hardening or new onboarding/authentication behaviour.
- A new UI framework or speculative component abstraction. No new runtime dependency
  is expected; small repo-local primitives are preferred.

## 3. Decisions and authoritative references

- Accepted ADRs and `architecture-decisions/FORBIDDEN.md` remain authoritative.
- Backend `lifecycle` and `availableActions` are the only lifecycle and action
  authority. The frontend renders them and never reconstructs their rules.
- Existing generated API types and React Query hooks remain the contract boundary.
- The accepted primary visual concept is the fidelity reference:
  `C:/Users/gencberke/.codex/generated_images/019f8aea-93d5-72c1-81c3-7f0fc1869066/exec-7a7a16e9-8448-4d13-a2c5-9cc019b27bd3.png`.
- The secondary concept may inform living-state presentation only:
  `C:/Users/gencberke/.codex/generated_images/019f8aea-93d5-72c1-81c3-7f0fc1869066/exec-9d93d41a-112d-4397-86ed-b3dccb4dc1ba.png`.
  Its left navigation and invented product areas are explicitly rejected.
- The experience has three information layers: stage/task/action, business detail,
  then technical trust detail.
- “Son gelişmeler” is stage-focused and may use only timestamps/history already
  present in authoritative projections. It is not a synthetic audit log.
- Funding-plan creation and payment initiation form one user intent,
  `Ödemeyi güvenceye al`, implemented through the existing ordered APIs with an
  honest recoverable partial-failure state.

## 4. Public interface, state and data impact

No public interface, persisted state or migration changes are permitted. V1–V22
remain frozen.

Routes remain `/login`, `/register`, `/app`, `/app/deals`,
`/app/deals/:dealId`, `/app/invitations` and the existing development status route.
Session, CSRF, tenant/legal-entity selection, idempotency and optimistic concurrency
behaviour remain unchanged.

Raw implementation terms such as `RuleSet`, `RatificationPackage`, `FundingUnit`,
`expectedVersion`, `Idempotency-Key`, UUIDs and raw enums are hidden from the main
experience. They may appear only in a clearly secondary technical disclosure when
that information is genuinely useful.

The frontend must represent external states honestly:

- Contract AI may show `QUEUED` or `PROCESSING` only when returned by the contract.
- Video AI has no `PROCESSING` state and the UI must not invent one.
- `UNCONFIRMED` is recoverable uncertainty, not failure; only the same operation is
  reconciled.
- `DISPUTE` is a delivery-exception workspace without resolution controls.
- `SETTLEMENT` is a safe read-only fallback with no actions.

## 5. Implementation phases

### P1 — Foundation, shell and entry experiences

Establish the minimal visual tokens and reusable primitives needed by this slice.
Redesign login/register, company selection, the authenticated shell, invitations and
the deal list. Preserve routes, session and company-context behaviour. Deal-list rows
use only available title/reference/status/update data and do not introduce N+1
counterparty reads.

### P2 — Agreement workspace and review journey

Turn the deal detail into a stage-led workspace. Keep setup, participants, parties,
documents, upload progress, contract analysis and manual review reachable in the
current lifecycle. Mount only the active feature area; load heavy workspaces and
history only when opened. Preserve real upload progress and truthful AI states.

### P3 — Approval and payment journey

Present ratification and funding as user tasks with projection-derived status. Keep
per-company approval truth, idempotency, expected-version and authorization behaviour.
Compose the existing funding-plan and payment-initiation calls behind one user intent
with safe retry/recovery after a partial failure; do not duplicate an already-created
operation.

### P4 — Delivery, evidence and exception states

Present fulfillment, video evidence, delivery confirmation and disputes through the
same stage-led structure. Preserve existing fulfillment history and upload/download
behaviour. Render completed, cancelled and archived states clearly; render settlement
as read-only. Do not add Slice 14B controls or claims about automatic settlement.

### P5 — Browser fidelity and acceptance

Run independent review of the complete diff and fix only demonstrated defects. In a
real browser, compare the latest implementation screenshot with the accepted primary
concept, validate the target journey, responsive layouts, keyboard/focus behaviour,
console cleanliness and truthful state/copy. P5 acceptance completes Slice 15 `G-UI`
and authorizes the already-approved Railway P5/P6 work.

## 6. Real-browser acceptance

Target flow: a signed-in user opens an agreement, immediately understands its current
stage and next permitted task, completes the available action, and sees the workspace
advance without losing session, company or operation state.

Required evidence:

1. Login/register and company-context entry remain usable.
2. Deal list and one representative deal workspace render at desktop and mobile
   widths without clipping, overlay or horizontal page scrolling.
3. The stage indicator uses semantic ordered structure and the current stage is
   announced with `aria-current`; focus is visible and dialogs/disclosures are
   keyboard operable.
4. One representative action in each implemented active stage exposes only actions
   permitted by `availableActions`; an unavailable action is not recreated locally.
5. Browser title/URL are correct, there is no framework error overlay, and the
   relevant interaction produces no new console error.
6. The accepted concept and latest browser screenshot are visually inspected in the
   same QA pass. Record a five-point fidelity ledger covering composition, hierarchy,
   typography, colour/surface and responsive behaviour, plus any intentional copy
   differences.

## 7. Minimum invariants and validation

- Intermediate work runs no repository-wide or full frontend/Core suite.
- Each implementation phase uses only `git diff --check`, TypeScript checking and the
  smallest affected unit/config/browser checks needed for changed behaviour.
- Generated API files must remain clean unless their authoritative contract changes;
  this plan does not authorize such a change.
- No backend, contract, migration, Railway or AI-owned file may change in P1–P5.
- No authorization, lifecycle, idempotency or optimistic-concurrency rule is copied
  into presentation components.
- Only the active feature is mounted; expensive or history-backed panels are
  conditional/lazy where practical without introducing an abstraction layer.
- Automated polling updates do not repeatedly announce themselves to assistive
  technology.
- Browser QA uses real rendered evidence, not only static code inspection.

## 8. Done definition

- [x] P1 foundation, shell and entry experiences accepted.
- [x] P2 agreement workspace and review journey accepted.
- [x] P3 approval and payment journey accepted.
- [x] P4 delivery, evidence and exception states accepted.
- [x] Independent review result is ACCEPT; any required revision is focused.
- [x] P5 desktop/mobile browser and accessibility evidence accepted.
- [x] Accepted visual concept and latest browser screenshot inspected together.
- [x] Contracts, backend, AI ownership, auth/session, V1–V22 and Railway resources
      remain unchanged.
- [x] Review handoff records exact branch/base/HEAD and minimum validation evidence.
- [x] Plan is archived to `docs/plan/done/` and Slice 15 `G-UI` is recorded complete
      before Railway P5 begins.

## 9. Acceptance evidence

- Implementation commit: `fbcbb7f` on `codex/frontend-experience-redesign`,
  based on `origin/main@87adbe41271747d46ed0884a55557a95be5ce75d`.
- Independent review: `ACCEPT` after two focused FIX revisions for primary copy
  and structural visual fidelity.
- Minimum validation: frontend TypeScript no-emit and `git diff --check` passed.
- Real-browser acceptance: register/login, organization and agreement creation,
  semantic stage navigation, refresh/session retention, explicit logout/relogin,
  empty console, visible keyboard focus, desktop and 390 px layouts passed.
- Fidelity ledger: the dark inline shell, connected five-stage composition,
  task/supporting-information hierarchy, navy/green visual system and responsive
  stacking align with the accepted concept. The representative DRAFT copy differs
  intentionally because no AI activity or counterparty data was invented.
