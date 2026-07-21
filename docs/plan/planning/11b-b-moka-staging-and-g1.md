# Slice 11B-B — Real Moka Staging Funding and G1 Capability Evidence

- Status: `SUPERSEDED` on 22 July 2026; historical planning artifact only
- Superseded by:
  `docs/agent/gates/simulation-only-payment-decision-2026-07-22.md`
- Execution prohibition: no phase in this file may receive a task packet,
  credential, provider probe, staging activation or browser run
- Sequence: accepted 11B-A → provider probe gate B-G0 → human-approved 11B-B
  implementation phases → planner closure
- Predecessors: accepted Slice 7, accepted G2 decision, accepted 11B-A, access
  to explicitly authorized non-production Moka test credentials
- Successors: ADR-014 decision drafting, then final Slice 14B ready approval
- Execution guide: `docs/agent/settlement-release-roadmap-runbook.md`
- Authority boundary: Moka test environment and staging only; no production
  credential, production deploy, real customer money or 14B release endpoint

## 1. Goal and user outcome

Run the accepted Slice 11 funding flow through the real Moka test environment,
prove duplicate/timeout/query recovery with redacted evidence, and separately
collect the G1 pool-release capability facts needed by ADR-014.

At completion, a buyer ADMIN can fund a staging Deal through the accepted real
provider mode and participants see the same safe funding projection. G1 has an
independent human-reviewed capability matrix for pool approve, status query and
finality. No application release/settlement behavior exists yet.

## 2. Scope and boundaries

In scope:

- Non-production Moka credential provisioning through Railway variables.
- Real funding initiate/query, duplicate identity, decline, timeout/unknown,
  restart and reconciliation evidence.
- Exact 3D requirement/redirect/hash/query facts for the assigned test merchant.
- Staging activation of the accepted Moka adapter after contract review.
- Probe-only pool approve/query calls for G1: request identity, duplicate,
  timeout/crash, query visibility, pending duration/cutoff and finality.
- Redacted provider call counts and planner-owned final staging browser funding
  acceptance.

Out of scope:

- Production credentials, production deployment or real customer money.
- Settlement/release aggregate, eligibility, public release endpoint, Deal
  `COMPLETED`, refund, reversal, payout or operator override.
- Treating redirect/callback, synchronous approve response or local ledger as
  settlement finality without authoritative provider proof.
- Marketplace/sub-dealer onboarding, fee split, KYC or production custody.
- Raw provider payload, card data, credential or unnecessary PII in repository,
  logs, screenshots, audit or public API.

## 3. Decisions and relevant ADRs

Binding inputs:

- ADR-003 §21, §23–§25; ADR-005 session/CSRF rules; ADR-006 async/idempotency
  and compatibility rules; ADR-007 staging/secret/logging rules.
- ADR-010 complete funding state, query-first and real-provider boundary.
- Accepted G2 non-production standard-merchant-pool decision.
- Accepted 11B-A transport/emulator boundary.
- FORBIDDEN payment, secret, external-call and production rules.

Fixed decisions:

- B-G0 evidence is planner/human readiness work, not application
  implementation and not proof supplied by the emulator.
- The real adapter may map only outcomes proved by test-environment evidence.
- 3D redirect is never authoritative for FUNDED. If the merchant requires 3D,
  its public UX/contract must be designed and human-approved before the
  implementation phases move to `ready/`.
- Unknown or timeout remains the same PaymentOperation/key in
  `UNCONFIRMED`; no new charge/key is created.
- G1 is a separate evidence decision. Passing real funding does not prove pool
  release/finality; passing an approve call does not by itself prove SETTLED.
- If authoritative query cannot distinguish the state required by 14B,
  G1 returns `NO-GO` and ADR-014/14B remain blocked.

## 4. Public interface, state and data impact

Before B-G0:

- No public contract decision is inferred.
- Probe tools use internal/redacted artifacts and never expose a Core release
  endpoint.

After B-G0 and before `ready/`:

- If non-3D funding fits the existing Slice 11 contract, record an explicit
  zero-public-contract delta.
- If 3D is mandatory, design the minimum additive Core API/frontend surface
  for browser redirection and query-based return recovery. Redirect data is a
  UX signal only. Committed OpenAPI, validator, changelog and generated types
  are one approval unit.
- Existing FundingPlan/FundingUnit/PaymentOperation states remain unchanged.
  New provider status enums are not added merely to mirror Moka strings.
- No settlement/release persistence or migration is allowed. A payment
  persistence change must be justified by accepted provider evidence and use a
  new forward-only migration; otherwise replan.

## 5. Execution and implementation phases

### B-G0 — Real-provider capability and contract-freeze gate

Outcome:
Redacted real-Moka evidence fixes funding/3D/query facts and determines whether
later implementation phases are safe and contract-complete.

Direction:

- User explicitly authorizes only test credentials and test-environment calls.
- Probe funding create/query, duplicate `OtherTrxCode`, definitive decline,
  timeout/unknown, not-found consistency and 3D requirement.
- Probe pool approve/query separately for G1, including duplicate approve,
  timeout-after-call, query visibility, maximum/pending timing and whether any
  result proves settlement finality.
- Store sanitized request identity, timestamps, result categories and call
  counts; never store credentials/raw card data/raw payload.

Depends on:
Accepted 11B-A and explicit credential authorization.

Exit checks:

- Funding outcome/query matrix is reproducible and redacted.
- Exact 3D contract need is known.
- G1 matrix distinguishes accepted response, pool approval, statement/
  settlement finality and unknown state.
- Any inconclusive duplicate/query/finality behavior produces `NO-GO` for the
  dependent phase rather than a workaround.

Planner checkpoint:
Revise this plan with the evidence-derived exact contract/mappings, obtain
explicit human approval and move it to `ready/` before B-P1.

### B-P1 — Contract-first real-provider activation design

Outcome:
The accepted B-G0 facts are represented in a minimal provider-neutral contract
and configuration design.

Direction:

- Preserve existing public contract when possible.
- If 3D is required, add only the approved redirect-initiation/return UX fields
  and backend-derived actions; query remains authoritative.
- Define safe provider references and error codes without raw Moka detail.
- Freeze staging profile/provider selection and fail-fast secret requirements.

Depends on:
B-G0 accepted and plan moved to `ready/`.

Exit checks:

- Contract validator/generated-type drift passes or zero-delta is proven.
- No provider-specific domain status or release behavior leaks into Slice 11.
- Security review confirms redirect/query and secret boundaries.

### B-P2 — Harden the real funding adapter from probe evidence

Outcome:
The 11B-A client maps the accepted real-provider funding outcomes exactly and
stays fail-closed for every unknown.

Direction:

- Apply only B-G0-proved auth, endpoint, amount, duplicate and query behavior.
- Keep initiate/query external to DB transactions and lifetime-fixed key use.
- Sanitize errors/references before domain or log boundaries.
- Do not add pool release calls to payment business services.

Depends on:
B-P1.

Exit checks:

- Focused adapter contract tests match redacted real examples.
- Timeout/crash/duplicate paths prove one operation/key and query-first
  recovery.
- Missing/invalid secret fails startup without disclosure.

### B-P3 — Deploy and verify real-provider funding in staging

Outcome:
The accepted `main` release uses the Moka test adapter in staging without
changing public network boundaries.

Direction:

- Store credentials only in Railway variables; do not expose Moka directly to
  browser or web edge.
- Deploy from the Slice 7 accepted `main` release path with exact SHA identity.
- Re-run only focused health, config and funding probes after deployment.

Depends on:
B-P2 and accepted Slice 7.

Exit checks:

- Core API is private; browser uses same-origin Core endpoints only.
- Test credential is absent from repo/image/frontend/log evidence.
- Real initiate/query reaches the test environment and keeps accepted funding
  state semantics.

### B-P4 — Funding duplicate, timeout and recovery acceptance

Outcome:
Real-provider staging funding proves money-safety recovery invariants.

Direction:

- Exercise one definitive success, one definitive decline if supported, same
  `OtherTrxCode` duplicate behavior and one controlled unknown/timeout path.
- Recover unknown only by query on the same operation/key.
- Record exact provider call counts and local state transitions.

Depends on:
B-P3.

Exit checks:

- No duplicate charge/operation is produced.
- Unknown is never displayed/persisted as decline or success.
- Authoritative funding query alone produces FUNDED.

### B-P5 — G1 pool-release evidence decision

Outcome:
The readiness charter has an independent `ACCEPTED` or `NO-GO` G1 decision,
without adding release product behavior.

Direction:

- Review the B-G0 pool probe evidence against request identity, duplicate,
  timeout/crash, query, finality, pending duration/cutoff and safe reference
  requirements.
- Separate “approve accepted”, “pool released”, and “finally settled”; do not
  collapse provider vocabulary.
- Record any provider limitation ADR-014 must carry.

Depends on:
B-G0 evidence and B-P4 operational confidence.

Exit checks:

- `ACCEPTED`: authoritative query/finality and recovery are reproducible; or
- `NO-GO`: exact missing capability is recorded and ADR-014/14B remain blocked.
- No local state or emulator result substitutes for provider truth.

### B-P6 — Single high-value real staging browser acceptance

Outcome:
The real browser proves the changed funding/provider boundary once, after all
adapter and recovery work is stable.

Direction:

- Use buyer ADMIN plus one read-only participant context.
- Create/activate a normal test Deal only as needed, initiate real test funding,
  follow any accepted 3D UX, refresh/poll and verify query-confirmed FUNDED.
- Include one recoverable unknown case only if the test environment can produce
  it safely and reproducibly.
- Do not replay document/video/dispute suites or test release UI (none exists).

Depends on:
B-P3–B-P5; G1 may be `NO-GO`, but funding browser acceptance can still close
11B if its own scope passes. A G1 `NO-GO` keeps ADR-014 blocked.

Exit checks:

- Buyer ADMIN flow and participant-safe read pass over HTTPS same-origin.
- Redirect alone never marks FUNDED; query-confirmed result does.
- No credential/raw provider data is visible.

### B-P7 — Slice 11B closure and gate synchronization

Outcome:
11B-A/B accepted work is archived, C3–C4/G4c is updated, and G1 status is
recorded independently.

Direction:

- Planner reviews complete A/B diffs, exact deployed SHAs, redacted provider
  evidence and the one browser acceptance run.
- Update `CURRENT.md` only after accepted closure.
- Do not mark G1 accepted unless B-P5 independently passed.

Depends on:
B-P1–B-P6 accepted.

Exit checks:

- 11B plans are `done/` with completion evidence and deviations.
- C3–C4/G4c is `ACCEPTED`.
- G1 is explicitly `ACCEPTED` or `NO-GO/BLOCKED`; no ambiguous status.

## 6. Real-browser acceptance

Only B-P6 is a browser E2E checkpoint. It is intentionally late because the
valuable UI behavior exists only after real-provider activation and recovery
mapping are stable.

Minimum flow:

1. Buyer ADMIN opens an ACTIVE Deal with one funding unit.
2. Initiate uses the Moka test environment; any 3D redirect follows the
   approved UX but does not mutate state by itself.
3. Refresh/poll reaches FUNDED only after authoritative query.
4. Same action/retry yields one payment operation/provider identity.
5. Read-only participant sees safe state and no mutation/provider secret.
6. If a reproducible unknown path exists, UI remains reconciling until query.

No release, settlement, refund, dispute or AI browser scenario belongs here.

## 7. Minimum invariants and validation

Per phase, run only the changed boundary's focused checks. At final review run:

- Contract validator/generated types only if public contract changed.
- Focused real-adapter auth/amount/response/redaction tests.
- Payment duplicate/timeout/query-first and external-call transaction tests.
- Production-profile/secret and module-architecture checks.
- One final full Core API verify because this is the only real-money-provider
  boundary change; do not repeat it after each phase.
- Frontend typecheck/build only if 3D/public UI changed.
- B-P6 one real staging browser E2E.
- `git diff --check`, complete base-to-HEAD diff and exact deployed SHA review.

Do not run all accepted browser suites, per-phase full builds, load tests or
release/refund scenarios.

## 8. Done definition

- [ ] B-G0 real-provider funding/3D/query evidence is redacted and accepted
- [ ] Evidence-derived plan/contract receives explicit human approval before B-P1
- [ ] Real Moka funding adapter uses only accepted provider behavior
- [ ] Credentials exist only in authorized non-production runtime variables
- [ ] Duplicate identity and timeout/crash recovery are query-first and safe
- [ ] FUNDED requires authoritative provider confirmation
- [ ] One high-value staging browser funding flow passes
- [ ] No release/settlement/refund/Deal-completion behavior was added
- [ ] 11B-A/B independent planner review and closure pass
- [ ] C3–C4/G4c is accepted
- [ ] G1 has an independent explicit `ACCEPTED` or `NO-GO/BLOCKED` decision
