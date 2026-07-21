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
| C0 — 14A/Slice 13 browser debt | `BLOCKED` | Planner | Complete archived Slice 14A §6 against the real local stack, visibly retire the Slice 13 historical VIDEO/MP4 debt, record evidence, and remove the debt from current-state documents. Baseline closure commit `e8fd60b` and the approved local membership fixture prelude exist; the browser matrix has not run. |
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

## Execution checkpoint — safe stop on 21 July 2026

Completed:

- Archived the accepted Slice 14A plan and project-state closure in commit
  `e8fd60b` without adding the unrelated `.claude/launch.json` file.
- Added this readiness charter in commit `41524fd`.
- Verified the local PostgreSQL, RabbitMQ, MinIO, Mock AI Worker, Core API
  `local,local-sandbox` profile, V22 schema, and Vite acceptance topology.
- Created six isolated acceptance accounts through the real registration UI.
- Created Buyer, Seller, Other Participant, and Nonparticipant legal entities
  through the real organization UI.
- With explicit user authorization, added only the two missing local
  `MEMBER` relationships through a disposable PostgreSQL transaction. No
  existing data was reset, no migration/application code changed, and both
  membership rows were verified tenant-aligned.

Prepared local fixture map:

| Context | Account | Legal entity | Role |
| --- | --- | --- | --- |
| Buyer ADMIN | `s14b-c0-buyer-admin@example.test` | `C0 Buyer Ltd` (`3f4a2051-f7a8-485b-880e-64a55ed5c967`) | `ADMIN` |
| Seller ADMIN | `s14b-c0-seller-admin@example.test` | `C0 Seller Ltd` (`51a6f83b-50f6-4b23-b97f-df710070aff4`) | `ADMIN` |
| Buyer MEMBER | `s14b-c0-buyer-member@example.test` | `C0 Buyer Ltd` | `MEMBER` |
| Seller MEMBER | `s14b-c0-seller-member@example.test` | `C0 Seller Ltd` | `MEMBER` |
| Other Participant ADMIN | `s14b-c0-other-admin@example.test` | `C0 Other Ltd` (`d178f8af-6a7e-4079-8c14-ec8f4eec5c6c`) | `ADMIN` |
| Nonparticipant ADMIN | `s14b-c0-nonparticipant-admin@example.test` | `C0 Nonparticipant Ltd` (`1f6ef8a1-b22f-4e92-a5da-e7071af89439`) | `ADMIN` |

No Deal, invitation, party assignment, document, analysis, ratification,
funding, fulfillment, evidence, video result, or dispute was created for this
fixture at this checkpoint. No C0 browser acceptance item is claimed as passed.

### Exact continuation plan

1. Reconfirm the Compose services, start Core API with
   `local,local-sandbox`, start Vite on port 5173, and log the two MEMBER
   accounts out/in so their new memberships are loaded through `/auth/me`.
2. Through the visible UI, have Buyer ADMIN create one Deal and invite Seller
   ADMIN plus Other Participant ADMIN; accept both invitations and assign only
   Buyer/Seller legal entities as contractual parties.
3. Build the accepted prerequisite chain through the real UI/API: finalize a
   contract document, complete Mock Worker extraction, accept manual review,
   create and approve one immutable ratification package from both party ADMIN
   contexts, fund through the local sandbox relay, and have Seller ADMIN start
   fulfillment.
4. Upload/finalize a VIDEO/MP4, request successful advisory analysis, reject it,
   and submit a replacement. Before opening a dispute, visibly prove the
   retained historical advisory panel, absence of historical mutation
   controls, and lack of duplicate current-evidence rendering.
5. Run archived Slice 14A §6 completely: UI open/refresh, same-key replay,
   distinct-key concurrent open, Buyer/Seller MEMBER read/comment and forced
   403 matrix, counterparty acknowledgement, stale recovery, 21-comment
   pagination/order, party-only lifecycle, unrelated-participant and
   nonparticipant 404 non-disclosure, late video/evidence snapshot immutability,
   fulfillment independence, withdrawal/history restoration, loading/error,
   and desktop/mobile presentation.
6. Capture redacted role/URL/status/code evidence, immutable before/after
   dispute snapshots, Mock Worker timing, and per-Deal database before/after
   state proving that casework changed no Deal, fulfillment, evidence, funding,
   payment, provider, outbox, or AI state.
7. If every item passes, add a dedicated C0 debt-retirement acceptance record,
   update this gate to `ACCEPTED`, remove the debt only from current/future
   state documents, and preserve historical acceptance records unchanged. Any
   UI, disclosure, snapshot, race, or side-effect defect produces `FIX` or
   `REPLAN`; it must not be waived as a pass.
