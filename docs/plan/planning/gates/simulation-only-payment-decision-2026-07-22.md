> **Not project state.** Founder decision input for ADR-014/14B planning; accepted Plan 17 in `docs/plan/done/` is authoritative for settlement implementation. Current project state: `docs/plan/CURRENT.md` and `docs/plan/ready|done/`.

# Simulation-Only Payment and Release Founder Decision — 22 July 2026

- Status: `ACCEPTED`
- Decision owner: founder/user
- Accepted: 22 July 2026 through explicit user statements that Moka will only
  be simulated and that successful simulated release may complete the demo Deal
- Scope: local, CI and staging demonstration behavior only
- Authority: roadmap/G1-S/G4c decision input for ADR-014 and Slice 14B planning
- Non-authority: no production payment, real provider integration, credential,
  money movement, custody, payout, refund or legal-compliance claim

## 1. Decision

M4Trust will not obtain or use a Moka account, test merchant, credential, SDK,
public provider endpoint, 3D redirect or callback. The repository's Moka-shaped
HTTP emulator is the only payment/release transport used by this roadmap.
“Moka” is an internal fixture/transport label, not a claim that Moka processed,
authorized, captured, held, released or settled funds.

The former real-provider Slice 11B-B and G1 evidence route is superseded. It
must not receive a task packet, credential, staging probe or browser run.

## 2. Simulation authority and environments

- The simulator is deterministic, separately started and integration-owned.
- It is allowed only in explicit local, CI and staging simulation profiles.
- Production profile selection fails closed; there is no simulator fallback.
- Scenario choice remains startup configuration. No public API field, request
  header, amount, currency or browser control selects a scenario.
- No runtime stores or logs a provider credential, card data, raw provider
  payload or claim of real financial execution.
- Public/frontend projections must visibly identify the mode as `SIMULATED`.

The existing ADR-010 §2.6/local profile and 11B-A bootstrap guards remain in
force until ADR-014 is accepted. ADR-014 must explicitly extend that rule with
a separately named `staging-simulated` profile and preserve production
rejection; this founder record alone does not authorize a config/code change.

## 3. Funding and settlement semantics

Existing accepted sandbox funding behavior remains valid: a simulator query
may verify the local PaymentOperation outcome and project the FundingUnit as
`FUNDED`. This means simulated funding only.

ADR-014 must extend settlement semantics with an explicit terminal
`SIMULATED_SETTLED` state. It must not map simulator success to financial
`SETTLED` or call the result provider-verified finality.

A Deal may transition `ACTIVE -> COMPLETED` after all accepted 14B eligibility,
authorization, dispute, idempotency and concurrency rules pass and the
simulator's authoritative status query verifies `SIMULATED_SETTLED`. Deal
completion means the non-production demo workflow completed; it does not mean
real payment settlement, payout or legal discharge.

Initiate/dispatch acceptance, a synchronous success-like body, timeout,
connection loss, callback-like input or browser state never establishes
`SIMULATED_SETTLED`. The authoritative local terminal source is the simulator
status query for the release operation's lifetime-fixed key.

## 4. Release and race rules carried into ADR-014

- Release is an explicit buyer legal-entity `ADMIN` action. Fulfillment
  completion or window expiry never releases automatically.
- Schema-v2 `disputeWindowDays`, immutable fulfillment `completedAt`, exact UTC
  deadline and schema-v1 release ineligibility from accepted G3 remain binding.
- A dispute that wins the required lock before release intent prevents intent
  and dispatch creation.
- A release intent that wins first is not silently cancelled by a later
  dispute. The same operation/key remains fail-closed and query-first; no new
  operation is created.
- Timeout, crash or inconclusive query remains unknown/reconcilable. It is not
  success or failure and blocks another release.
- Durable intent, HTTP idempotency result, audit and dispatch are atomic.
  Simulator HTTP happens after commit and outside database transactions;
  result application is a separate short transaction.
- Refund, reversal, chargeback, void and manual force-success remain out of
  scope.

ADR-014 must still fix exact operation states, deterministic lock order,
cardinality, authorization/read disclosure, idempotency reuse, late-dispute
projection and final completion transaction before Slice 14B can become ready.

## 5. Gate effect

- Historical G1 real-provider capability is `SUPERSEDED` for the accepted
  simulation-only product scope; it is not falsely marked as provider evidence.
- Replacement gate G1-S is `ACCEPTED`: simulator authority, environment
  exclusion, query-only terminal proof, visible simulated semantics and
  fail-closed recovery are binding.
- C3–C4/G4c is `ACCEPTED` for simulation-only provider foundation because
  Slice 11B-A is accepted and no real-provider Slice 11B-B exists in scope.
- G2's former standard-merchant-pool/test-credential selection is superseded.
  Its production/legal/custody/fee/split/payout non-claims remain conservative
  boundaries. G3 remains accepted and unchanged.
- C6 ADR-014 becomes the next blocked gate. C7 Slice 14B ready remains blocked
  until ADR-014 is accepted and the eight-section plan is revised/approved.

## 6. Required labels and prohibited claims

The product, API, audit and acceptance evidence must not say or imply:

- Moka/provider authorized, captured, released, paid or settled money;
- funds are held, escrowed or available for payout;
- merchant pool, sub-dealer, custody, KYC, fee split or Law 6493 compliance;
- callback/redirect/provider finality; or
- production readiness for payment or settlement.

Allowed wording is explicit: simulated funding, simulated release,
`SIMULATED` mode, `SIMULATED_SETTLED`, and no real money movement.

## 7. Superseded route

`docs/plan/planning/11b-b-moka-staging-and-g1.md` is retained only as a
superseded planning artifact. No part of it authorizes work. Historical
research and accepted 11B-A evidence remain reviewable and are not rewritten as
real-provider proof.
