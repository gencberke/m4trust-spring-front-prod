> **Not project state.** Historical gate decision record only; authoritative accepted state is `docs/plan/CURRENT.md` and `docs/plan/ready|done/`.

# G2/G3 Founder Decision Record — 21 July 2026

- Status: `ACCEPTED`
- Charter: `docs/plan/planning/gates/settlement-release-readiness-charter-2026-07-21.md`
- Execution guide: `docs/plan/planning/settlement-release-roadmap-runbook.md`
- Decision owner: founder/user
- Accepted: 21 July 2026 by explicit founder/user delegation of ready approval to the planner
- Authority of this record: non-production readiness decisions for Slice 14B gate closure only
- Explicit non-authority: no production credentials, no real-money movement, no
  Law 6493 legal opinion, no ADR-014 draft, no Slice 14B ready approval, no
  application/migration/public-contract change
- Supersession note: G2's standard-merchant-pool/test-credential route was
  superseded on 22 July 2026 by
  `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`. This file
  remains historical evidence; G3 remains authoritative and unchanged.

## Purpose

Record decision-complete founder choices for charter gates **G2**
(non-production risk and operations) and **G3** (ratification compatibility)
so later ADR-014 work can consume fixed operating-model and package-contract
inputs.

## Parallel-work boundary

This record is documentation-only under `docs/plan/planning/gates/` plus accepted-state and
gated-planning synchronization. It does not touch Slice 7 staging
deployment work, Railway config, application code, migrations, OpenAPI, or
`docs/plan/ready/`.

---

## G2 — Non-production provider operating model

### G2.1 Selected model

**Selected for all non-production Moka sandbox authority (local, CI, staging):
standard merchant pool.**

Meaning for readiness work:

- One Moka test/sandbox merchant (dealer) account holds pooled funds for the
  funded Deal unit under the accepted single-plan/single-unit model.
- Release/capture/status-query probes and any later non-production settlement
  behavior assume that single-merchant pool path.
- **Marketplace / sub-dealer split is not selected** for non-production 14B
  readiness. Seller onboarding as sub-merchant, amount-split at pool approve,
  and multi-dealer settlement remain unresolved and out of this gate’s
  authority.

Rationale (engineering, not legal): marketplace/sub-dealer KYC and split
behavior are unproved; selecting them now would invent operational consent.
Standard merchant pool is sufficient to exercise G1 release capability and
14B settlement design without pretending those gaps are closed. Research note
`docs/research/moka-united-pool-payments.md` remains research, not acceptance.

### G2.2 Authority limit

Authority granted by this decision is limited to:

- local Compose / developer machines;
- CI using non-production secrets only;
- staging sandbox / test-environment provider credentials already governed by
  Slice 7 / 11B plans.

This decision **does not** authorize:

- production Moka credentials or production merchant configuration;
- real customer money movement;
- production deployment of release/settlement behavior;
- platform-held custody or operator-initiated manual payout as a product
  default.

### G2.3 Incident and unknown-outcome ownership

| Role | Ownership |
| --- | --- |
| Founder/user | Final non-production incident owner; decides whether to pause sandbox traffic, rotate test credentials, or escalate to provider support. |
| Planner | Records gate evidence; stops on fail-closed contradictions; does not invent success from unknown outcomes. |
| Implementer (when later authorized) | Keeps unknown/timeout/crash states reconcilable and fail-closed; never maps redirect/callback alone to final settlement; never retries an unknown operation under a new provider identity. |
| Provider support (Moka test env) | External status clarification only; does not authorize local state inventing success. |

Until an authoritative provider status query proves a terminal outcome, local
state remains unknown/reconcilable. Approve-then-refund, optimistic settlement,
and “assume void” are forbidden under this decision.

### G2.4 Explicitly unresolved risks (recorded, not closed)

The following remain **open** and must not be treated as accepted by G2:

1. **KYC / onboarding** for marketplace sub-dealers or any seller-as-merchant
   path.
2. **Custody** claims about who legally holds funds beyond the provider’s
   documented pool mechanics in the test environment.
3. **Fee / split / commission** automation between platform, buyer, and seller.
4. **Payout ownership** after release (seller settlement timing, withholding,
   chargebacks).
5. **Law 6493** and related licensing/regulatory review — **not performed** by
   this record; this is not a legal opinion.
6. **Production** merchant-pool versus marketplace model selection — deferred
   until a separate production gate exists.
7. **Manual intervention** beyond non-production incident ownership above —
   no standing operator authority to force SETTLED, invent refunds, or override
   ratification/dispute gates.

Platform-controlled manual payout is rejected as an **engineering default** for
product design (consistent with existing research posture). That rejection is
not a legal conclusion.

### G2.5 Product rules acknowledged (not legal enforceability opinions)

For packages that ratify a dispute window under G3:

- Buyer legal-entity `ADMIN` remains the sole release requester once eligibility
  exists.
- The ratified dispute window is a contractual product term enforced by Spring
  eligibility checks.
- Enforceability in a court or under payment-services law is **not** asserted
  here.

### G2 exit

G2 is decision-complete and accepted: model selected, authority limited,
ownership named, unresolved risks listed, and authority is non-production-only.

---

## G3 — Ratification compatibility contract

### G3.1 Package schema version

| Item | Decision |
| --- | --- |
| Current accepted schema | `schemaVersion = 1` (ADR-010): `commercialTerms` carries `amountMinor` and `currency` only. |
| Additive create transition | Existing `/api/v1` create request gains optional `disputeWindowDays`; presence creates schema v2, absence continues to create schema v1 during the compatibility window. |
| V2 commercial terms | V1 fields plus required `disputeWindowDays` |
| Supersession | Existing v1 packages are never rewritten. Any change requires SUPERSEDE + new schema v2 package + re-ratification. |
| V1 create retirement | Blocking schema-v1 create is not part of the additive 14B rollout. It requires a separately accepted explicit breaking migration or a new public API major version. |

`schemaVersion` remains part of the canonical snapshot (already true for v1).
The server selects the snapshot version from the request shape; clients do not
send or override a free `schemaVersion`. The bump is a new closed snapshot
shape, not an in-place mutation of ratified rows.

### G3.2 `disputeWindowDays` field

| Property | Decision |
| --- | --- |
| Type | JSON integer (I-JSON safe); no string durations. |
| Unit | Whole UTC days; each day is an exact 24-hour interval from `completedAt`. |
| Allowed range | Inclusive `1..365`. |
| Required on schema v2 create | Yes. Absence, null, non-integer, out-of-range, or zero → validation failure; package is not created. |
| Mutability | Immutable after create; change requires SUPERSEDE + new package + re-ratification (existing ADR-010 rule). |

### G3.3 Absence and legacy behavior

| Case | Behavior |
| --- | --- |
| `/api/v1` create with valid `disputeWindowDays` | Create schema v2; the field is required inside the schema-v2 snapshot. |
| `/api/v1` create omitting `disputeWindowDays` during compatibility window | Preserve existing behavior and create schema v1; return a backend-derived missing-contractual-window/release-ineligible projection. Do not default a window. |
| Frontend create after the v2 UI rollout | Require explicit `disputeWindowDays`; the first-party UI creates schema v2 and does not expose a hidden/default value. |
| Already-ratified / historical schema v1 packages | Remain readable and permanently **release-ineligible**. No dispute-window backfill, default or invented consent. |
| Schema v2 package missing the field in stored snapshot | Treat as corrupt/ineligible; do not invent a window. |

Allowing a compatibility-window schema-v1 create preserves ADR-006 §47. It
does not make that package release-eligible. Retiring v1 create later is a
separate migration/version decision and is not silently folded into 14B.

### G3.4 Deadline source and boundary

| Property | Decision |
| --- | --- |
| Window length source | Exact `disputeWindowDays` from the Deal’s current RATIFIED package snapshot (`schemaVersion = 2`). |
| Window start source | The server-owned `now` captured after the Deal/fulfillment/evidence locks in the **buyer acceptance transaction** that transitions the fulfillment to `COMPLETED`. It is the same instant used for accepted evidence, milestone completion and audit; it is not provider time. |
| Authoritative persistence field | New immutable `fulfillment.completedAt` (wire `completedAt`, persistence `completed_at`). It is written once in the buyer acceptance transaction and cannot move afterward. V20 `fulfillment.updated_at` is not the contractual clock. |
| New-row invariant | A newly completed fulfillment has non-null `completedAt`; a non-completed fulfillment has null `completedAt`. Later mutations cannot rewrite it. |
| Historical completion backfill | Forward migration derives `completedAt` only from the corresponding immutable ACCEPTED evidence `acceptedAt`, after proving exactly one authoritative accepted evidence for each completed fulfillment. Contradictory/missing rows fail migration review; `updated_at` is never a fallback. |
| Legacy release effect | Backfilled completion history does not create contractual consent. Any schema-v1 package remains release-ineligible regardless of `completedAt`. |
| Clock | UTC. |
| End instant | `completedAt + disputeWindowDays × 24 hours`, computed as exact UTC days (for example `Instant.plus(days, ChronoUnit.DAYS)`). |
| Eligibility boundary | Release may be requested only when `now >= endInstant` **and** every other eligibility gate passes. The end instant is the first eligible instant (inclusive lower bound). |
| Not sources | Payment-provider verification, AI output, rule-set free text, browser clock alone, provider redirect, operator override, or mutable `updated_at`. |

### G3.5 Canonical ordering and hash input

Unchanged ADR-010 hashing rules apply:

- Hash input is only the dedicated closed immutable snapshot JSON.
- Canonicalization: RFC 8785 (JCS); SHA-256 of UTF-8 bytes; lowercase 64-char
  hex.
- Package id/version/status, approvals, actions, audit timestamps, and detail
  wrappers remain outside the hash input.

Schema v2 snapshot additions:

- `commercialTerms` object fields in canonical object member order after JCS
  (JCS sorts keys): `amountMinor`, `currency`, `disputeWindowDays`.
- `disputeWindowDays` is a JSON number integer; no array ordering rule needed
  for this field.
- Existing v1 `rules` array ordering rule (unique `ruleReference` UTF-8
  bytewise ascending) is unchanged.

Any future array added to the snapshot must publish its ordering rule in the
implementing ADR/plan before use.

### G3.6 Public contract and frontend / client transition

When a later human-approved implementing slice (not this record) lands:

1. Use an ADR-006 §47 additive transition: optional request field in the
   existing `/api/v1` operation, versioned closed response snapshots, and no
   rejection of a previously valid request during the compatibility window.
2. OpenAPI create/detail/history DTOs model both closed snapshot versions and
   expose `disputeWindowDays` only for schema v2.
3. Generated TypeScript client is regenerated from the accepted contract; hand
   edits of generated payment/ratification clients are forbidden.
4. Frontend package-create UI requires an explicit integer day input in range
   `1..365` before submit; it must not default a hidden window.
5. Settlement/release UI (14B) must surface distinct backend-derived states for:
   missing contractual window (legacy/ineligible), window not elapsed, and
   ready — without client-side invention of the deadline.
6. Older clients may continue creating schema-v1 packages during the
   compatibility window, but those packages remain visibly release-ineligible.
7. A later decision may retire schema-v1 create only through an explicit
   migration or new public API major version with deprecation evidence.

This accepted decision authorizes the **contract choices** above. It does **not** authorize implementing them until ADR-014 and
Slice 14B ready approval (and any prerequisite ratification-contract phase
those plans name).

### G3.7 Compatibility proof obligations (for the future implementing slice)

Acceptance of that future implementation must show:

- A pre-existing schema v1 RATIFIED package remains readable and permanently
  release-ineligible.
- An existing `/api/v1` client omitting `disputeWindowDays` remains contract
  compatible and creates a visibly release-ineligible schema-v1 package.
- A request with `disputeWindowDays` absent never silently creates schema v2 or
  receives a default window.
- Null, non-integer and out-of-range `disputeWindowDays` values are rejected.
- A schema v2 create with `disputeWindowDays=14` (example) produces a stable
  contentHash across runtimes under JCS.
- Changing the window requires SUPERSEDE + new package + both-party
  re-approval; the old package is not mutated.
- No migration invents `disputeWindowDays` onto historical snapshots.
- Historical `completedAt` backfill uses only immutable accepted-evidence
  `acceptedAt`, fails on inconsistent cardinality and does not change release
  eligibility of schema-v1 packages.
- Buyer acceptance writes immutable `completedAt` once from the transaction's
  shared server instant; later row updates do not move the window start.

### G3 exit

G3 is decision-complete and accepted: schema version, field unit/range,
absence/legacy behavior, deadline source/boundary (including authoritative
`completedAt`), canonical hash input, and client transition are fixed without
inventing consent for existing packages.

---

## Explicit non-claims

- G1 Moka pool-release capability remains `BLOCKED` until redacted provider
  probe evidence is accepted.
- C2/G4b Slice 7 staging is accepted; C3–C4/G4c Slice 11B remains an independent blocked gate.
- ADR-014 is not drafted by this record.
- `docs/plan/planning/14b-settlement-and-release.md` stays in `planning/` and
  is not moved to `ready/`.
- No implementer task packet is authorized.
- This draft does not close G2 or G3.

## Decision execution phases

These are planner/user decision phases, not implementer task phases.

### D-P1 — Planner FIX verification

Status: `ACCEPTED` by planner on 21 July 2026. This accepts the record's
decision completeness and consistency only; it does not accept G2/G3 on behalf
of the founder/user.

Outcome:
The record is internally consistent with ADR-006, accepted ratification/
fulfillment behavior and the non-production authority boundary.

Exit checks:

- `/api/v1` compatibility window does not reject a previously valid create
  request or silently default a window.
- Schema v1 stays release-ineligible; schema v2 hash/input/range is exact.
- `completedAt` uses the buyer-acceptance transaction's server instant;
  historical backfill uses only immutable accepted-evidence `acceptedAt` and
  never creates consent.
- Production KYC/custody/Law 6493/fee/split/payout risks remain explicitly open.
- `CURRENT.md` contains no proposed/draft state.

Depends on:
None.

### D-P2 — Explicit founder/user acceptance

Status: `ACCEPTED` on 21 July 2026 through explicit founder/user delegation of
ready approval to the planner.

Outcome:
The named human decision owner explicitly accepts G2 and G3 as
non-production readiness decisions.

Exit checks:

- Acceptance is an affirmative user statement, not planner inference.
- Acceptance grants no production credential, production deployment, legal
  opinion or real-money authority.
- Any requested decision change returns D-P1 to review before acceptance.

Depends on:
D-P1 accepted by planner.

### D-P3 — Gate and accepted-state synchronization

Outcome:
The record and readiness charter show G2/G3 `ACCEPTED` with date/evidence; no
implementation task is created.

Exit checks:

- Status/header/acceptance table and charter G2/G3 rows are synchronized.
- `CURRENT.md` is updated only if the accepted decision materially changes the
  concise accepted project state; it is never used for draft tracking.
- G1, Slice 11B, ADR-014 and 14B ready gates remain independently blocked
  until their own evidence passes; accepted Slice 7 remains retired.

Depends on:
D-P2.

## Acceptance

| Gate | Result | Evidence |
| --- | --- | --- |
| Planner FIX review / D-P1 | `ACCEPTED` | ADR-006 §47, ADR-010 §2.1–§2.6, ADR-011 §2.5–§2.6 and FORBIDDEN review on 21 July 2026 |
| G2 | `ACCEPTED` | This record §G2 and explicit founder/user delegation on 21 July 2026 |
| G3 | `ACCEPTED` | This record §G3 and explicit founder/user delegation on 21 July 2026 |

Founder/user acceptance: **recorded on 21 July 2026**.
