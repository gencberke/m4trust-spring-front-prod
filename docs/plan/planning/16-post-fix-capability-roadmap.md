# Slice 16+ — Post-Fix Capability Roadmap

- Status: `planning` — gated portfolio plan; not implementation-authorizing
- Draft/approval input: founder/user, 22 July 2026
- Entry gate: Slice 15 archived `done`, seven-day production pilot accepted, and
  no open Contract/ADR/security/recovery finding
- Decision authority: each package requires the named Accepted ADR and a separate
  eight-section `ready` plan before any implementer task packet
- Payment boundary: every financial-looking capability remains
  `DEMO_SIMULATED`; real payment/custody/payout is not authorized

## 1. Amaç ve kullanıcı sonucu

Production reconciliation sonrasında kalan product/deferred capabilities'i
birbirinin authority ve state sınırlarını bozmadan paketlemek. Roadmap bittiğinde:

- demo Deal settlement/release ile tamamlanabilir;
- privileged identity ve operator casework güvenli biçimde çalışır;
- retained data lifecycle açık policy ile yönetilir;
- mutual/casework cancellation ve simulated recovery query-first yürür;
- multi-milestone/partial fulfillment mümkün olur;
- AI work durable human-review workflow kazanır;
- enterprise/public-client authentication ayrı trust modeline sahip olur.

## 2. Kapsam, sıra ve global sınırlar

Zorunlu sıra:

```text
R1 Slice 14B Demo Settlement/Release
-> R2 Identity Hardening
-> R3 Casework Completion
-> R4 Data Lifecycle/Retention
-> R5 Simulated Recovery and ACTIVE Cancellation
-> R6 Multi-Milestone/Partial Fulfillment
-> R7 AI Work Management
-> R8 Enterprise/Public-Client Authentication
```

R7, R4 sonrasında R5/R6 ile ancak ilgili ready plan açıkça paralel yetki verirse
paralel olabilir. R2 tamamlanmadan platform operator açılmaz. R3 tamamlanmadan
casework-driven cancellation yoktur. R5 tamamlanmadan partial release/refund yoktur.

Global yasaklar:

- real provider/payment/custody/payout veya financial `SETTLED`;
- AI'nın business acceptance/case/payment mutation yapması;
- accepted artifact/history overwrite;
- participant visibility'yi mutation authority saymak;
- frontend-derived action/lifecycle;
- provider timeout'u success/failure saymak;
- future package behavior'ını önceki pakete gizlice eklemek.

## 3. Karar ve ADR programı

- R1: Accepted ADR-014; mevcut `planning/14b-settlement-and-release.md` ADR-014
  ile reconcile edilip ayrı human approval ile `ready` olur.
- R2: ADR-020 — Privileged MFA, Session and Recovery Security.
- R3: ADR-021 — Platform Operator and Casework Resolution.
- R4: ADR-022 — Retention, Legal Hold, Export and Erasure.
- R5: ADR-023 — Demo Reversal/Refund and ACTIVE Cancellation Consent.
- R6: ADR-024 — Multi-Milestone Commercial Allocation and Partial Release.
- R7: ADR-025 — AI Work Management and Human Review Queue.
- R8: ADR-026 — Enterprise OIDC and Public-Client Authentication.

Future ADR numbers are reserved by this roadmap but remain `Proposed`/absent until
their package decision work starts. Implementer may not author or infer them.

## 4. Target public interface, state ve data etkisi

### R1 target

ADR-014 exact surface:

```text
GET  /api/v1/deals/{dealId}/settlement
POST /api/v1/deals/{dealId}/settlement/release
GET  /api/v1/release-operations/{operationId}
POST /api/v1/release-operations/{operationId}/reconcile
```

Ratification schema v2 optional-create transition, immutable
`disputeWindowDays=1..365`, immutable fulfillment `completedAt`, one settlement
and one lifetime release operation per Deal/funding unit. Only query-verified
`SIMULATED_SETTLED` completes the demo Deal.

### R2 target

Target capabilities:

- WebAuthn primary and TOTP fallback enrollment/challenge;
- hashed single-use recovery codes;
- active-session list, single/all-session revoke;
- recent-auth/MFA requirement for ADMIN/operator-sensitive mutation.

Target `/api/v1/auth/mfa/**` and `/api/v1/auth/sessions/**` surface remains closed
until ADR-020 threat/recovery/device-binding decisions are accepted.

### R3 target

Casework extends `OPEN|UNDER_REVIEW|WITHDRAWN` with assignment and `RESOLVED`.
Immutable decision records, SLA/escalation, operator/party projections,
notifications and scanned case attachments are introduced. Resolution alone does
not mutate payment/cancellation; R5 consumes a narrow immutable decision projection.

### R4 target

- immutable correction creates a new object/business version, never overwrite;
- accepted legal retention schedule and legal hold gate deletion;
- object version deletion leaves audit tombstone, no raw content;
- tenant export and personal-profile erasure distinguish retained business record;
- retention worker external delete is transaction-separated/idempotent.

Exact periods and jurisdictional grounds are founder/legal inputs; without them
ADR-022 cannot be Accepted or plan moved to ready.

### R5 target

- buyer/seller ADMIN mutual ACTIVE cancellation proposal + explicit acceptance;
- casework-driven cancellation only from immutable ADR-021 decision;
- simulated refund/reversal ledger entries and lifetime operation identity;
- query-first unknown outcome, no replacement key;
- deterministic Deal/dispute/release/cancellation lock order.

No unilateral MEMBER/participant cancellation or real refund exists.

### R6 target

Ratification schema v3 caller-supplied milestone allocations use integer minor
units; all currency values match and exact sum equals contract amount. No ratio,
float or implicit remainder allocation. Each milestone owns independent
fulfillment/evidence/funding/release state; Deal completes only when every required
milestone reaches the ADR-024 terminal condition.

### R7 target

- durable human manual-review queue and assignment;
- immutable reviewer decision history;
- cooperative job cancellation;
- backend progress projection and SSE reconnect;
- bounded batch submission with per-item result;
- AI remains advisory and cannot produce R3/R5/R6 decisions.

### R8 target

- tenant/domain-bound OIDC/SSO;
- invite-linked JIT default; open JIT/self-registration disabled;
- IdP claims pass backend mapping and never directly grant ADMIN/operator;
- mobile/public API only through registered OAuth2 clients, scoped credentials,
  rotation/revocation and separate rate/audit policy;
- CAPTCHA/device/risk signals only evidence-triggered supplemental controls.

## 5. Package implementation phases

### R1 — Slice 14B Demo Settlement and Release

#### R1-P0 — Reconcile and approve ready plan

Outcome:
Existing 14B planning document exactly matches ADR-014, removes stale production
exclusion/G3 contradictions and receives separate human ready approval.

Depends on: Slice 15/pilot accepted.

Exit checks: no open state/lock/auth/idempotency/compatibility decision.

#### R1-P1 — Contract and ratification v2

Outcome:
OpenAPI, generated client, canonical hash fixtures and compatibility validator own
schema v1/v2, dispute window and settlement/release surface.

Depends on: R1-P0.

Exit checks: v1 remains readable/release-ineligible; v2 range/hash/absence tests pass.

#### R1-P2 — Persistence and module ports

Outcome:
Forward-only `completedAt`, settlement, release operation/history and durable
dispatch schema with ADR-014 constraints and narrow Deal/funding/fulfillment/
casework ports.

Depends on: R1-P1.

Exit checks: migration clean/upgrade/cardinality/immutability/architecture tests.

#### R1-P3 — Eligibility and release intent

Outcome:
Buyer ADMIN explicit 202 release writes one intent/dispatch only when every
contractual/funding/fulfillment/window/dispute gate passes under exact lock order.

Depends on: R1-P2.

Exit checks: actor, boundary-time, stale, idempotency and dispute-first matrix.

#### R1-P4 — Simulator dispatch and reconciliation

Outcome:
Production-safe demo simulator and relay recover crash/timeout/duplicate with the
same key; terminal proof is query-only.

Depends on: R1-P3.

Exit checks: call-count and unknown-outcome recovery matrix; no replacement op.

#### R1-P5 — Terminal completion and frontend

Outcome:
Only query-verified `SIMULATED_SETTLED` with no active dispute atomically completes
Deal; UI exposes safe simulated states/actions/non-claim wording.

Depends on: R1-P4.

Exit checks: release/dispute races, participant reads, no financial `SETTLED`, browser acceptance.

### R2 — Identity Hardening

#### R2-P0 — ADR-020 and contract

Fix WebAuthn origins/RP ID, TOTP secret encryption, recovery-code lifecycle,
session/device disclosure and recent-auth window; approve API contract first.

#### R2-P1 — Credential/session persistence

Add encrypted authenticators/TOTP, hashed recovery codes, challenge TTL/replay
constraints and principal session projection through forward-only migrations.

#### R2-P2 — Enrollment and challenge enforcement

Implement WebAuthn primary/TOTP fallback, recovery and MFA-required policy for
ADMIN; no role bypass through frontend/IdP claim.

#### R2-P3 — Session management frontend

Implement active-session list/revoke/all-revoke and MFA enrollment/recovery UX.

#### R2-P4 — Security acceptance

Prove replay/origin/sign-counter/recovery/session theft/role escalation and two-browser matrix.

### R3 — Casework Completion

#### R3-P0 — ADR-021 and contract

Fix operator identity/authorization, assignment cardinality, SLA clocks,
resolution reasons/decision shape, party disclosure and notification semantics.

#### R3-P1 — Operator and case persistence

Add operator grant projection, assignment/history, SLA/escalation and immutable
decision records without foreign payment/fulfillment repositories.

#### R3-P2 — Assignment/review/resolution application

Implement operation-specific policies and deterministic locks; party read and
operator read/mutation projections remain distinct.

#### R3-P3 — Scanned attachments and notifications

Use ADR-018 upload gate and ADR-015 notification outbox; no unscanned read.

#### R3-P4 — Frontend and acceptance

Operator queue/detail/assignment/resolution plus party-safe status; prove SLA,
concurrency, non-disclosure and no payment/cancellation mutation.

### R4 — Data Lifecycle and Retention

#### R4-P0 — Legal/product decision and ADR-022

Accept exact artifact/profile/audit retention periods, hold authority, export
scope, erasure exceptions and incident ownership. Missing legal decision blocks.

#### R4-P1 — Contract and retention persistence

Model legal hold, retention deadline, deletion work, tombstone/export/erasure job
without generic soft delete or history overwrite.

#### R4-P2 — Retention and object recovery worker

External deletes/exports outside transaction, lifetime work identity, retry and
version-specific restore/verification.

#### R4-P3 — User/operator interface

Expose only authorized export/retention/hold actions; backend derives eligibility.

#### R4-P4 — Acceptance

Prove hold vs expiry, concurrent download/delete, version restore, tenant isolation,
erasure audit and no accepted-history mutation.

### R5 — Simulated Recovery and ACTIVE Cancellation

#### R5-P0 — ADR-023 and contract

Fix bilateral proposal/approval identity, case decision mapping, refund/reversal
state, lock order and late settlement/cancellation outcomes.

#### R5-P1 — Consent and ledger persistence

Add immutable cancellation consent/version and simulated recovery operation/
ledger history; money remains integer minor units.

#### R5-P2 — Mutual and casework-driven cancellation

Implement buyer/seller ADMIN consent and narrow R3 decision port; no MEMBER or
unilateral action.

#### R5-P3 — Query-first simulated recovery

Dispatch/reconcile same lifetime key, preserve unknown outcome, block conflicting
release/recovery operation.

#### R5-P4 — Frontend/race acceptance

Prove bilateral/case authority, stale/concurrent/dispute/release/cancel races and
all no-real-money labels.

### R6 — Multi-Milestone and Partial Fulfillment

#### R6-P0 — ADR-024 and schema v3 contract

Fix milestone identity/order, exact amount allocation, required/optional completion,
partial funding/release and compatibility with v1/v2 Deal packages.

#### R6-P1 — Expand persistence

Add milestone/allocation/funding/release cardinality while old single-unit rows and
images remain compatible.

#### R6-P2 — Milestone fulfillment/evidence

Generalize existing primary milestone through owned ports; no accepted evidence
history rewrite.

#### R6-P3 — Partial demo funding/release

Per-milestone operations use ADR-014/023 query-first rules and exact allocated amount.

#### R6-P4 — Aggregate completion/UI

Backend derives per-milestone and Deal actions; prove exact-sum/no-rounding,
parallel milestone races and terminal completion.

### R7 — AI Work Management

#### R7-P0 — ADR-025 and contracts

Fix review item subject/cardinality/assignment, cancel/progress/SSE/batch schemas,
retention and advisory/no-side-effect boundary.

#### R7-P1 — Review/job persistence

Add durable review queue, assignment/decision history, cancel intent and progress
snapshots with inbox/outbox identity.

#### R7-P2 — Cooperative cancellation/progress

Spring owns job decision; AI honors best-effort cancel and may return late result;
no terminal result loss or duplicate job.

#### R7-P3 — SSE and batch API/frontend

SSE reconnect uses event identity/cursor; batch returns bounded per-item async
operations; UI never derives business acceptance.

#### R7-P4 — Acceptance

Prove cancel/result race, duplicate/reconnect, partial batch failure, assignment
authority and no AI-created case/payment/fulfillment decision.

### R8 — Enterprise and Public-Client Authentication

#### R8-P0 — Product consumers and ADR-026

Identify exact enterprise IdPs, tenant-domain ownership, mobile/public clients,
token scopes/TTL/rotation and admin mapping. No generic auth platform is built
without named consumers.

#### R8-P1 — OIDC tenant linking

Contract/persistence for domain/issuer/client mapping, invite-linked JIT and safe
account linking; IdP claim never directly grants privileged role.

#### R8-P2 — Public/mobile clients

Only if R8-P0 names consumers: registered confidential/public clients, scoped
OAuth2 tokens, PKCE where applicable, rotation/revocation/rate/audit.

#### R8-P3 — Enterprise frontend/admin

Safe SSO initiation/callback UX and tenant admin mapping without enumeration or
open redirect.

#### R8-P4 — Acceptance

Prove issuer/domain/tenant isolation, account takeover/linking, key rotation,
scope enforcement, logout/revoke and fallback recovery.

## 6. Browser ve operatif kabul modeli

Every package has planner-owned browser acceptance with at least:

- two legal entities and separate browser sessions;
- ADMIN/MEMBER/participant/nonparticipant/operator actor matrix as applicable;
- stale version, idempotent replay and distinct-key concurrency;
- refresh/reconnect/restart persistence;
- hidden-resource non-disclosure;
- explicit simulated/no-real-money wording for R1/R5/R6;
- before/after regression of all predecessor package invariants.

R4 additionally requires real object version restore; R7 real worker reconnect;
R8 real configured staging IdP. Mock-only evidence cannot complete a plan.

## 7. Minimum invariant ve validation

Each ready package must require:

- contract-first validator/generated-client clean diff;
- forward-only clean/upgrade migration proof;
- authorization/idempotency/concurrency/lock transaction tests;
- no external call in DB transaction;
- architecture/module ownership tests;
- backend/frontend full validation;
- affected AI producer/consumer validation;
- exact base-to-HEAD review and `git diff --check`;
- planner browser/operative acceptance.

Payment-like packages additionally prove exact operation call counts, query-first
unknown handling, integer money and unreachable financial `SETTLED`. Identity
packages additionally prove enumeration/replay/session/token secrecy.

## 8. Portfolio Done tanımı

- [ ] Slice 15 and seven-day pilot accepted and archived
- [ ] R1 ADR-014 implementation/14B accepted and archived
- [ ] ADR-020/R2 privileged identity accepted and archived
- [ ] ADR-021/R3 casework completion accepted and archived
- [ ] ADR-022/R4 legal data lifecycle accepted and archived
- [ ] ADR-023/R5 demo recovery/ACTIVE cancellation accepted and archived
- [ ] ADR-024/R6 multi-milestone/partial fulfillment accepted and archived
- [ ] ADR-025/R7 AI work management accepted and archived
- [ ] ADR-026/R8 named enterprise/public clients accepted and archived
- [ ] Every package's browser, operational, migration and invariant evidence accepted
- [ ] No real payment/custody/payout or AI business-authority behavior introduced
- [ ] `CURRENT.md` updated only as each package becomes accepted project state
