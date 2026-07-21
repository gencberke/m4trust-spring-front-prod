# Slice 11B-A — Moka Provider Foundation with External HTTP Emulator

- Status: done — accepted 22 July 2026 after independent planner review
- Sequence: accepted Slice 7 closure → accepted G2/G3 decisions → 11B-A
- Predecessors: accepted Slice 11 funding foundation, accepted ADR-010,
  accepted Slice 7 staging, accepted G2 non-production operating model
- Successor: `../planning/11b-b-moka-staging-and-g1.md`
- Approval record: founder/user delegated ready approval to the planner on 21 July 2026
- Acceptance record: `docs/agent/slice-11b-a-acceptance-2026-07-22.md`
- Execution guide: `docs/agent/settlement-release-roadmap-runbook.md`
- Authority boundary: local/CI non-production implementation only; no real
  Moka credential, staging provider call, release behavior or money movement

## 1. Goal and user outcome

Replace the in-process funding sandbox boundary, when explicitly selected in
non-production, with a real HTTP transport boundary that behaves like the
documented Moka request/response protocol.

The Core API still exposes the accepted Slice 11 funding behavior: buyer ADMIN
creates one durable payment operation, provider work happens outside the HTTP
request and database transaction, timeout stays `UNCONFIRMED`, and query-first
reconciliation resolves only authoritative outcomes. The visible product flow
does not change in 11B-A; the value is proving the external-process boundary
before any real credential is introduced.

## 2. Scope and boundaries

In scope:

- A separate non-production HTTP emulator process; it is not embedded in
  Spring and does not share the business database.
- Documented Moka authentication material construction, JSON transport,
  amount/currency conversion, request identity and safe response mapping.
- Existing provider-neutral funding `initiate` and `queryStatus` ports.
- Probe-only client capability for documented pool approve/query calls needed
  later by G1; no Spring release port, aggregate, endpoint or action.
- Deterministic startup-configured emulator scenarios for success, decline,
  duplicate request identity, not-found, timeout/connection loss and late
  query resolution.
- Focused local/CI validation of out-of-transaction HTTP and recovery behavior.

Out of scope:

- Real Moka credentials, public internet calls or staging provider traffic.
- 3D browser redirect UX, real callback/hash verification or merchant-account
  configuration.
- Provider capability/finality evidence; emulator results never satisfy G1.
- Settlement/release state, pool approval business action, Deal completion,
  refund, reversal, void, payout or production deployment.
- Provider behavior that is absent from accepted documentation/research.
- A production test-control API or scenario field in the Core public contract.

## 3. Decisions and relevant ADRs

Binding inputs:

- ADR-003 §21, §23–§25: payment ownership, module ports, short transactions,
  query-first unknown recovery and optimistic concurrency.
- ADR-004 §19, §22–§23: no real money in development; minimum valuable tests.
- ADR-006 §24–§25, §35, §54: HTTP idempotency and async `202 + Location`.
- ADR-007 §18–§20, §33: environment configuration, secret and logging rules.
- ADR-010 §2.2–§2.7: existing funding state machine and provider boundary.
- FORBIDDEN payment, secret, external-call and sandbox-production rules.

Fixed decisions:

- The emulator is a transport test double, not provider evidence or a source
  of undocumented semantics.
- Emulator scenario selection is process startup configuration. Business
  amount, currency, public header/body or runtime production endpoint cannot
  select a scenario.
- Moka credentials are represented by non-secret placeholders in emulator
  runs. The same client receives real secrets only in 11B-B runtime config.
- Internal money remains integer minor units. Decimal major-unit conversion is
  adapter-owned and exact; no floating point enters domain/public state.
- Raw provider/emulator payload and messages do not enter domain, audit,
  Problem Details or frontend projections.
- A timeout, malformed response or inconclusive query maps to existing
  `UNCONFIRMED`, never `DECLINED` or `SUCCEEDED`.

## 4. Public interface, state and data impact

Core public OpenAPI:

- No change expected. Existing funding-plan/payment-operation/reconcile
  endpoints and generated frontend types remain authoritative.
- If implementation discovers a necessary public field or endpoint, stop and
  return this plan to planning; do not add it as an implementation detail.

Internal transport contract:

- Request identity is the payment operation's lifetime-fixed provider key.
- Authentication material is computed only inside the integration adapter and
  is redacted from logs/errors.
- `initiate` and `queryStatus` return provider-neutral outcomes already
  accepted by ADR-010.
- Pool approve/query client methods are probe-only until G1/ADR-014 authorizes
  a release port; payment business services cannot call them in 11B-A.

State and persistence:

- Existing FundingPlan/FundingUnit/PaymentOperation/durable-dispatch records
  remain unchanged.
- No migration is expected. A new persistence need is a replan trigger.
- External HTTP calls occur only after durable dispatch commit and outside all
  database transactions; result application remains a separate transaction.

## 5. Implementation phases

### A-P1 — Freeze the documented transport and safety matrix

Outcome:
The exact documented request fields, authentication inputs, decimal conversion,
provider-neutral outcomes, timeout classes and redaction rules are reviewable
before code.

Direction:

- Convert the point-in-time Moka research into focused emulator/client fixtures
  without copying secrets or treating gaps as facts.
- Mark every undocumented pool/finality behavior `UNKNOWN`; emulator behavior
  for it is only a local scenario.
- Define request/response size and timeout bounds plus stable internal failure
  categories.

Depends on:
Accepted G2/G3 decisions and this ready approval.

Exit checks:

- Fixture matrix covers success, decline, duplicate, not-found, timeout,
  malformed/sanitized error and late query result.
- Amount conversion and CheckKey inputs have exact, non-secret examples.
- No fixture claims G1 evidence or production semantics.

### A-P2 — Build the external non-production HTTP emulator

Outcome:
A separately started HTTP process exposes only the bounded provider-like
transport needed by 11B-A tests.

Direction:

- Keep it outside Spring and outside the business database.
- Select deterministic scenario sequences only at startup.
- Enforce duplicate `OtherTrxCode`/request-identity behavior in the emulator's
  own ephemeral state so query-first recovery can be exercised.
- Never package or enable it in production profiles.

Depends on:
A-P1.

Exit checks:

- Emulator health and bounded payment/query/pool-probe routes work over HTTP.
- Restart and timeout scenarios are deterministic.
- Production bootstrap cannot select the emulator.

### A-P3 — Implement the Moka authentication and transport client

Outcome:
The integration boundary can build authenticated requests, convert money and
parse safe outcomes without depending on Spring business state.

Direction:

- Compute CheckKey/authentication only at call time from runtime config.
- Use exact decimal conversion from minor units and explicit currency.
- Bound connect/read timeouts; sanitize provider messages and references.
- Preserve the lifetime-fixed request identity across initiate and query.

Depends on:
A-P1 and A-P2.

Exit checks:

- Focused vectors prove authentication input order and amount conversion.
- Logs/errors contain no credential, raw body or unsafe provider message.
- Timeout/malformed responses become provider-neutral unknown outcomes.

### A-P4 — Adapt the existing funding port to external HTTP

Outcome:
Slice 11 durable dispatch and reconciliation can use the HTTP client without
changing their public behavior or transaction boundaries.

Direction:

- Keep provider selection profile/config based and fail fast on missing config.
- Do not open a transaction around the HTTP call.
- Query before retrying an uncertain initiate; reuse the exact provider key.
- Keep probe-only pool operations unreachable from payment business services.

Depends on:
A-P3.

Exit checks:

- Success and definitive decline map to accepted terminal states.
- Timeout/crash-like emulator outcomes remain `UNCONFIRMED`; reconciliation
  uses query and produces no second initiate.
- Module architecture still prevents integration from owning business state.

### A-P5 — Valuable local vertical-boundary check

Outcome:
One real Core API → external emulator → query/recovery path proves the process
boundary end to end.

Direction:

- Run one success path and one timeout-then-query path through the real durable
  dispatch relay.
- Assert exact HTTP call counts and unchanged public funding projections.
- Do not repeat the accepted Slice 11 browser matrix; the UI contract did not
  change.

Depends on:
A-P4.

Exit checks:

- Success reaches FUNDED only from a verified emulator outcome.
- Timeout produces one operation/key, blocks new charge and later reconciles
  without duplicate initiate.
- External calls are observed outside DB transactions.

### A-P6 — Focused validation and review handoff

Outcome:
11B-A is submitted for independent planner review without claiming real
provider or G1 acceptance.

Direction:

- Report exact branch/base/HEAD, config profiles, changed internal contracts,
  call counts and known provider gaps.
- Keep validation proportional; do not run browser E2E or unrelated slice
  suites.

Depends on:
A-P1–A-P5.

Exit checks:

- Focused emulator/client/payment-recovery tests pass.
- Module-architecture, production-profile exclusion, frontend production build
  (only if frontend/config changed) and `git diff --check` pass.
- Review report states that G1 and real-provider acceptance remain open.

## 6. Real-browser acceptance

No browser E2E is required for 11B-A because the public API and UI behavior are
unchanged. The valuable end-to-end boundary is A-P5 at the process/HTTP level.

A browser run becomes mandatory only if implementation changes a public
funding projection or frontend behavior; that change first requires replan and
contract approval.

## 7. Minimum invariants and validation

Run only:

- Exact CheckKey-input and minor/major money conversion tests.
- Timeout, malformed response, duplicate identity and query-first mapping.
- Provider call outside transaction assertion.
- Production-profile emulator exclusion and module architecture.
- A-P5 one success + one timeout/reconcile vertical check.
- Contract drift check only if contract files changed; otherwise prove zero
  contract diff.

Do not run full browser E2E, all accepted slice regressions or the complete
backend suite after every phase. A focused payment/integration test set at
A-P6 is sufficient unless a shared module changed.

## 8. Done definition

- [x] Plan is explicitly human-approved and moved to `ready/` before work
- [x] External HTTP emulator is separate, deterministic and non-production only
- [x] Moka authentication, money conversion and safe parsing are bounded
- [x] Existing provider-neutral funding port works over external HTTP
- [x] Timeout/unknown remains fail-closed and query-first
- [x] Duplicate provider identity never creates a second initiate
- [x] Pool approve/query client capability is probe-only and unreachable from business services
- [x] No public API, migration, release, settlement or Deal-completion behavior was added
- [x] A-P5 valuable vertical-boundary check passes
- [x] Focused validation and independent planner review pass
- [x] Acceptance record explicitly says emulator evidence does not close G1
