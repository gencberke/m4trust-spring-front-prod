# Slice 14B Settlement and Release — Readiness Charter

- Status: active gate register — decision/evidence work only
- Opened: 21 July 2026
- Baseline: `planner/slice-14a-closure@e8fd60b`
- Owner of approvals and planner–implementer handoffs: user
- Planner: maintains this register, reviews evidence, and stops on failed gates
- Implementation authority: none; this charter is not a ready plan or task packet

## Purpose and boundary

This register prevents Slice 14B implementation from starting with inherited
acceptance debt or unproved payment assumptions. A gate is `ACCEPTED` only when
its named evidence exists and the named human owner explicitly accepts it.
Missing, draft, reported-only, or contradictory evidence means `BLOCKED`.

This charter authorizes planning, redacted research, local acceptance, and
evidence collection only. It does not authorize provider credentials, provider
calls, application code, migrations, public-contract changes, deployment,
production access, real money movement, or an implementer task packet.

## Gate register

| Gate | Status | Decision owner | Required evidence and exit condition |
| --- | --- | --- | --- |
| C0 — 14A/Slice 13 browser debt | `BLOCKED` | Planner; local fixture prelude requires user authorization | Complete archived Slice 14A §6 against the real local stack, visibly retire the Slice 13 historical VIDEO/MP4 debt, record evidence, and remove the debt from current-state documents. Baseline closure commit `e8fd60b` exists; the browser matrix has not run. |
| G4a — Slice 14A prerequisite | `ACCEPTED` | User/planner | Slice 14A implementation is merged at `main@0282c0e`; acceptance record is `docs/agent/slice-14a-acceptance-2026-07-21.md`; V22 is frozen accepted history. This does not claim the C0 browser debt passed. |
| C2/G4b — Slice 7 staging | `BLOCKED` | User/planner | Every Done item in `docs/plan/ready/07-staging-deployment.md`, including Railway HTTPS smoke, controlled migration, failure gate, release identity, secret/network checks, and schema-compatible rollback, is independently accepted; plan moves to `done/`. |
| C3–C4/G4c — Slice 11B provider foundation | `BLOCKED` | User/planner | A separate eight-section 11B plan is human-approved before implementation. Real-provider funding, Moka transport/security/query behavior, staging sandbox validation, and full acceptance pass; 11B moves to `done/`. Release aggregate/API behavior remains 14B scope. |
| G1 — Moka pool-release capability | `BLOCKED` | User/planner | Redacted, reproducible Moka test-environment evidence proves request identity, duplicate behavior, authoritative status query, settlement finality, timeout/crash recovery, callback/redirect authority, pending duration/cutoffs, and safe reference handling. Local emulation is not provider evidence. |
| G2 — Non-production risk and operations | `BLOCKED` | Founder/user | A signed non-production decision selects standard merchant pool or marketplace/sub-dealer, limits authority to local/CI/staging sandbox, names incident/unknown-outcome ownership, and records unresolved KYC/custody/fee/split/payout risks. It is not a legal opinion and grants no production or real-money authority. |
| G3 — Ratification compatibility | `BLOCKED` | Founder/user | A signed decision fixes package schema/version, `disputeWindowDays` unit/range/absence behavior, deadline source/boundary, canonical ordering/hash input, frontend/client transition, and permanent release-ineligibility of existing packages without invented consent. |
| C6 — ADR-014 | `BLOCKED` | User | After G1–G4 close, ADR-014 has no open provider, legal/operational, state, race, authorization, idempotency, transaction, compatibility, or finality decision; it is human-accepted and ADR index/README/FORBIDDEN are synchronized. |
| C7 — Slice 14B ready approval | `BLOCKED` | User | The provisional/circular parts of the planning draft are replaced with ADR-014 decisions, implementation phases start after the ready gate, the eight-section format passes review, and explicit human approval moves the plan from `planning/` to `ready/`. |

## Sequencing rules

1. C0 is retired independently before 14B acceptance work.
2. Slice 7 and 11B use their own approved plans and acceptance records.
3. Slice 11B may establish real-provider funding plus shared Moka
   transport/query primitives. It must not implement settlement eligibility,
   release aggregates, or 14B public endpoints.
4. Provider release probes may collect G1 evidence without creating 14B
   application behavior.
5. ADR-014 is drafted only after G1–G4 evidence is accepted.
6. Public contract design and 14B implementation phases follow accepted
   ADR-014 and explicit 14B ready approval; they are not a pre-ready
   implementer phase.
7. `docs/agent/CURRENT.md` changes only after accepted project state materially
   changes, not for drafts, raw evidence, or this register.

## Stop rules

Return `NO-GO` and do not issue a 14B task packet if any gate is `BLOCKED`, if
Moka query finality is not proved, or if the proposed behavior needs optimistic
settlement, a new provider key for an unknown operation, approve-then-refund,
invented contractual consent, production credentials, or real money movement.

## GO definition

Slice 14B receives `GO` only when C0, G1, G2, G3, G4a–G4c, C6, and C7 are all
`ACCEPTED`, the repository baseline is clean, and every evidence link remains
reviewable and redacted. Until then, only the specific readiness work needed to
close the next blocked gate may proceed.
