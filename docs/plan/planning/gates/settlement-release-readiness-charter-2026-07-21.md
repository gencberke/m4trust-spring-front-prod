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
| C0 — 14A/Slice 13 browser debt | `ACCEPTED` | Planner | Complete archived Slice 14A §6 against the real local stack, visibly retire the Slice 13 historical VIDEO/MP4 debt, record evidence, and remove the debt from current-state documents. Accepted 21 July 2026; evidence `docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md` on Deal `DL-0000000017`. |
| G4a — Slice 14A prerequisite | `ACCEPTED` | User/planner | Slice 14A implementation is merged at `main@0282c0e`; acceptance record is `docs/plan/done/review/slice-14a-acceptance-2026-07-21.md`; V22 is frozen accepted history. This does not claim the C0 browser debt passed. |
| C2/G4b — Slice 7 staging | `ACCEPTED` | User/planner | Accepted 21 July 2026. Every Done item in `docs/plan/done/07-staging-deployment.md` passed, including Railway HTTPS smoke, controlled migration, disposable failure gate, exact release identity, secret/network checks, and schema-compatible immutable rollback. Evidence: `docs/plan/done/review/slice-07-acceptance-2026-07-21.md`. |
| C3–C4/G4c — simulation transport foundation | `ACCEPTED` | Founder/user/planner | Accepted 22 July 2026 for simulation scope only. Slice 11B-A and its external emulator evidence are accepted; real-provider Slice 11B-B is superseded and cannot receive work. Evidence: `docs/plan/done/review/slice-11b-a-acceptance-2026-07-22.md` and `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`. |
| G1-S — simulation safety | `ACCEPTED` | Founder/user | Replaces historical real-provider G1 for this product scope. Only a profile-gated deterministic simulator may act; terminal release is query-verified `SIMULATED_SETTLED`, visibly `SIMULATED`, and never a real Moka/money/finality claim. Unknown remains fail-closed. Evidence: `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`. |
| G2 — non-production risk and operations | `ACCEPTED / SUPERSEDED IN PART` | Founder/user | The 21 July merchant-pool/test-credential route is historical and superseded. Simulation-only authority and prohibited claims are fixed by `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`; G2's production/legal/custody/fee/split/payout non-claims remain binding. |
| G3 — Ratification compatibility | `ACCEPTED` | Founder/user | Accepted 21 July 2026. Schema-v2 `disputeWindowDays`, additive v1 compatibility, immutable server-owned `completedAt`, accepted-evidence-only historical backfill, exact UTC boundary, canonical hash input and client transition are fixed by `docs/plan/planning/gates/g2-g3-founder-decision-2026-07-21.md`. |
| C6 — ADR-014 | `BLOCKED` | User | Convert accepted G1-S/G2/G3/G4 inputs into exact simulation state, race, authorization, idempotency, transaction, compatibility and completion decisions; human-accept ADR-014 and synchronize ADR index/README/FORBIDDEN. |
| C7 — Slice 14B ready approval | `BLOCKED` | User | The provisional/circular parts of the planning draft are replaced with ADR-014 decisions, implementation phases start after the ready gate, the eight-section format passes review, and explicit human approval moves the plan from `planning/` to `ready/`. |

## Sequencing rules

1. C0 is retired independently before 14B acceptance work.
2. Slice 7 and accepted 11B-A keep their approval/evidence records.
3. Real-provider Slice 11B-B and historical G1 are superseded; no credential,
   provider probe, 3D/callback, real-provider staging or provider browser run is
   allowed.
4. Simulation transport evidence is not real-provider evidence. G1-S permits
   only the explicitly simulated semantics in its founder decision.
5. ADR-014 is the next work after accepted G1-S/G2/G3/G4 inputs.
6. Public contract design and 14B implementation phases follow accepted
   ADR-014 and explicit 14B ready approval; they are not a pre-ready
   implementer phase.
7. `docs/plan/CURRENT.md` changes only after accepted project state materially
   changes, not for drafts, raw evidence, or this register.

## Stop rules

Return `NO-GO` and do not issue a 14B task packet if any current gate is
`BLOCKED`, if simulated mode is not explicit, or if behavior needs a new key for
an unknown operation, optimistic success, invented contractual consent,
production enablement, provider credentials or real money movement.

## GO definition

Slice 14B receives `GO` only when C0, G1-S, G2, G3, G4a–G4c, C6, and C7 are all
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

## Execution checkpoint — C0 continuation safe stop on 21 July 2026

Baseline: `planner/slice-14a-closure@f26df72`. Gate C0 remains `BLOCKED`.
No browser-matrix item and no acceptance debt was retired.

Environment reconfirmed:

- Compose `m4trust-local` with PostgreSQL, RabbitMQ, MinIO, and Mock AI Worker
  healthy.
- Core API running with `SPRING_PROFILES_ACTIVE=local,local-sandbox`;
  `GET /actuator/health` → `UP`; Flyway head remains V22
  (`dispute casework foundation`).
- Vite accepting on `http://127.0.0.1:5173/` (HTTP 200); login UI reachable at
  `/login`.

Fixture reconfirmed (read-only):

- All six `s14b-c0-*@example.test` accounts remain enabled.
- Buyer/Seller MEMBER rows remain tenant-aligned on `C0 Buyer Ltd` /
  `C0 Seller Ltd`.
- Leftover Spring Session for `s14b-c0-seller-member@example.test` produced
  authenticated `GET /api/v1/auth/me` with
  `memberships[0].role=MEMBER` on `C0 Seller Ltd`
  (`51a6f83b-50f6-4b23-b97f-df710070aff4`). This proves the MEMBER SQL fixture
  is visible to `/auth/me` for that account; it is not a logout/login refresh
  and does not pass any C0 matrix item.
- No live Spring Session exists for the other five C0 accounts.
- No C0 Deal/invitation/party/document/analysis/ratification/funding/
  fulfillment/evidence/video/dispute work was started in this continuation.

Hard stop — missing shared UI credentials:

- The six accounts were created through the real registration UI in the prior
  checkpoint, but the shared password was not recorded in this charter or in
  recoverable planner evidence.
- Without that password, MEMBER logout/login refresh, Buyer ADMIN Deal
  creation, and the remaining Exact continuation plan steps cannot proceed
  through the real UI.
- No password hash extraction, credential guessing campaign, application code
  change, migration, reset, or debt closure was performed.

Resume only after the user supplies the shared C0 password (or explicitly
authorizes a disposable local password-reset transaction of the same class as
the prior MEMBER fixture). Then continue Exact continuation plan step 1
logout/login for both MEMBER accounts and proceed through Deal creation.

## Execution checkpoint — C0 Deal-chain mid-stop on 21 July 2026

Baseline remains `planner/slice-14a-closure@f26df72`. Gate C0 remains
`BLOCKED`. No Section 6 matrix item and no acceptance debt was retired.

Completed after explicit user authorization for a disposable local
password-reset (same class as the prior MEMBER fixture):

- Generated a local-only Argon2id hash with
  `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` and updated only the
  six `s14b-c0-*` `identity_user.password_hash` rows in one PostgreSQL
  transaction; invalidated leftover C0 Spring Session rows. No migration,
  application code, or unrelated account was changed.
- Shared local acceptance password for these six accounts was set by that
  disposable reset and is **not** recorded in this charter (held out of band).
- Verified API login + `/auth/me` for both MEMBER accounts:
  Buyer MEMBER → `C0 Buyer Ltd` / `MEMBER`; Seller MEMBER → `C0 Seller Ltd` /
  `MEMBER`.
- Real UI logout/login refresh: Buyer MEMBER showed `C0 Buyer Ltd … Üye`;
  Seller MEMBER showed `C0 Seller Ltd … Üye`.
- Buyer ADMIN created Deal `DL-0000000017`
  (`4342aa36-8e32-4a24-ad4f-09d0b5fbe94f`, title
  `C0 Settlement Readiness Prerequisite`) under `C0 Buyer Ltd`.
- Sent PENDING invitations to `s14b-c0-seller-admin@example.test` and
  `s14b-c0-other-admin@example.test`.
- Seller ADMIN accepted with `C0 Seller Ltd`; participants are now
  `C0 Buyer Ltd` + `C0 Seller Ltd`.
- Other ADMIN is logged in at `/app/invitations` with the C0 Deal invitation
  visible (`Daveti kabul et` ready). Acceptance of that invitation was not
  completed in this turn.

Database confirmation at stop:

- invitations: seller-admin `ACCEPTED` → `C0 Seller Ltd`; other-admin
  `PENDING`.
- `buyer_legal_entity_id` / `seller_legal_entity_id` still null (party
  assignment not done).

Hard stop:

- Safe mid-chain stop before Other ADMIN invitation acceptance and initiator
  party assignment. Browser automation was unlocked; Core API
  (`local,local-sandbox`) and Vite `:5173` remain running.
- C0 gate stays `BLOCKED`. Do not claim any 14A §6 PASS.

### Exact resume plan

1. As `s14b-c0-other-admin@example.test`, accept the pending invitation with
   `C0 Other Ltd`.
2. As Buyer ADMIN, assign Buyer=`C0 Buyer Ltd` and Seller=`C0 Seller Ltd` only.
3. Continue Exact continuation plan steps 3–7 (document → analysis → review →
   ratification → funding → fulfillment start → historical VIDEO panel → full
   14A §6 matrix → evidence/debt retirement only if every item PASSes).

## Execution checkpoint — C0 prerequisite + Slice 13 history observation on 21 July 2026

Baseline remains `planner/slice-14a-closure@f26df72`. Gate C0 remains
`BLOCKED`. No Section 6 matrix item was claimed PASS and no acceptance debt was
retired from `CURRENT.md`.

Stack recovery this turn:

- Compose Postgres/RabbitMQ/MinIO/Mock AI Worker remained healthy.
- Core API and Vite had died; restarted. Core API now runs with
  `SPRING_PROFILES_ACTIVE=local,local-sandbox` and
  `PAYMENT_DISPATCH_RELAY_FIXED_DELAY=5s` (runtime only; no config file change)
  so local sandbox payment dispatch does not wait the default 5m.
- Health: API `UP`, Vite `:5173` HTTP 200.

Deal `DL-0000000017` / `4342aa36-8e32-4a24-ad4f-09d0b5fbe94f` — Exact plan
steps 3–4 progress:

| Step | Result | Evidence |
| --- | --- | --- |
| Dual package approve | PASS | UI status `Package RATIFIED oldu; Deal ACTIVE`; Seller approved 15:58 |
| Funding plan + sandbox | PASS | Buyer created plan; initiate → payment op `SUCCEEDED`; UI `FONLANDI` / `BAŞARILI`; provider ref `sandbox-f54e0c19-…` |
| Seller start fulfillment | PASS | UI `Durum: Devam ediyor` then `İnceleme bekleniyor`; DB fulfillment `74e81b1a-…` `IN_PROGRESS` then review path |
| VIDEO/MP4 upload (API) | PASS | Seller intent→MinIO PUT→finalize `48b291eb-…` `SUBMITTED` (`c0-delivery-success.mp4`) |
| Advisory analysis success | PASS | Buyer request 202 → job `fefb3312-…` `RESULT_AVAILABLE`; `advisoryOutcome=NO_ISSUE_DETECTED` |
| Reject + replacement | PASS | Buyer reject → `REJECTED`; Seller replacement `53131cfb-…` `SUBMITTED` (`c0-delivery-replacement.mp4`) |
| Slice 13 historical panel | OBSERVED PASS (debt not retired) | Seller Deal UI: historical rejected row shows retained advisory (`Belirgin sorun tespit edilmedi`, observations); historical/current analysis panels have **no** request/retry buttons; current evidence analysis is `NOT_REQUESTED` only in current section (not duplicated onto history row for same id) |

IDs at stop:

- Fulfillment: `74e81b1a-5305-4f0d-8f60-dfcc84d998b3`
- Milestone: `40e677bc-35c5-4edf-a4ab-d37ff416c396`
- Rejected VIDEO + retained analysis: evidence `48b291eb-0099-4ef3-8fdf-59e83a83e0e5`, job `fefb3312-1a86-4208-9264-98ef7086b011`
- Current replacement VIDEO: `53131cfb-5d02-4781-b619-e2ac9b052f78` (analysis not requested)

Hard stop:

- Exact continuation plan step 5 (full archived Slice 14A §6 matrix) not
  started. Dispute open and remaining matrix items remain.
- C0 stays `BLOCKED` until the full §6 matrix PASSes and debt retirement is
  recorded. Do not update `CURRENT.md` debt language yet.

### Exact resume plan

1. As Buyer ADMIN on the same Deal, optionally request analysis on the
   replacement VIDEO if a later matrix item needs a current advisory result.
2. Run Exact continuation plan steps 5–7: full 14A §6 matrix → redacted
   evidence capture → C0 debt-retirement record + gate `ACCEPTED` only if
   every item PASSes.

## Execution checkpoint — C0 ACCEPTED on 21 July 2026

Gate C0 is `ACCEPTED`. Acceptance record:
`docs/plan/done/review/c0-14a-browser-debt-acceptance-2026-07-21.md`.
`docs/plan/CURRENT.md` browser-debt language for 14A §6 / Slice 13 historical
VIDEO was retired. Historical Slice 13 / 14A acceptance records were not
rewritten.

Completed Exact continuation plan steps 5–7 on Deal `DL-0000000017` /
dispute `55c8248a-94fe-43a0-a70f-895f35624643`:

- Open / same-key replay / concurrent distinct-key `409` / MEMBER
  read+comment / MEMBER forced `403` matrix / Seller acknowledge
  `UNDER_REVIEW` / Other+nonparticipant non-disclosure / fulfillment
  independence / late evidence+VA with immutable openingSnapshot /
  21-comment pagination / withdraw to `FULFILLMENT` with retained history /
  stale `409` / withdraw DB side-effect empty-diff / desktop+mobile UI.

At that checkpoint, charter gates G1–G3, C2/G4b, C3–C4/G4c, C6 and C7
remained `BLOCKED`.
No Slice 14B implementation authority is granted.

## Execution checkpoint — C0 second-control FIX on 21 July 2026

Second control required `FIX` before C0 could remain accepted. Completed:

1. **True concurrent open** on empty active-case slate: Buyer `201`
   (`27352a6f-…` OPEN) and Seller `409 DISPUTE_ACTIVE_CASE_EXISTS` in one
   parallel distinct-key race (exactly one winner).
2. **empty / loading / backend-error**: empty active casework UI
   (`casework=null`, open form + withdrawn history); loading
   (`Oturum doğrulanıyor`, `Deal yükleniyor`); backend-error with API stopped
   (`Bağlantı kurulamadı` + `Yeniden dene`).
3. **Full-lifecycle no-side-effect**: open→comment→ack→withdraw on the FIX
   case left deal/fulfillment/evidence/payment/VA/outbox business rows
   unchanged vs pre-open baseline.
4. **Credential hygiene**: plaintext fixture password removed from acceptance
   record and charter; six `s14b-c0-*` hashes rotated again; sessions
   invalidated; password value not stored in git.
5. **Planner-state sync**: `CURRENT.md`, this charter, planning `14b` draft,
   `next-slices-planner-handoff.md`, and `req-review.md` point at the C0
   acceptance evidence instead of open transferred debt.

Gate C0 remains `ACCEPTED` with the corrected evidence set.

## Execution checkpoint — C2/G4b ACCEPTED on 21 July 2026

Slice 7 Railway Staging Deployment is independently accepted and archived at
`docs/plan/done/07-staging-deployment.md`. Acceptance evidence:
`docs/plan/done/review/slice-07-acceptance-2026-07-21.md`.

Accepted evidence covers main-bound immutable core/web deployments, public-edge
and private-service topology, one-shot Flyway V22 pre-deploy, isolated migration
failure containment, schema-compatible platform rollback, exact release SHA,
production-cookie/same-origin browser behavior, deep-link fallback,
authorization non-disclosure, actuator blocking, and secret/bundle checks.

Gate C2/G4b is retired. At that checkpoint, gates G1–G3, C3–C4/G4c, C6 and
C7 remained `BLOCKED`; no Slice 14B implementation authority was granted.

## Execution checkpoint — G2/G3 ACCEPTED and Slice 11B-A READY on 21 July 2026

The founder/user explicitly delegated ready approval to the planner. The
planner independently reviewed the decision record against ADR-006 §47,
ADR-010, ADR-011, the accepted fulfillment implementation and FORBIDDEN.

- G2 is `ACCEPTED` for non-production standard-merchant-pool work only.
- G3 is `ACCEPTED` with additive schema-v2 compatibility, immutable server-owned
  fulfillment `completedAt`, accepted-evidence-only historical backfill and no
  invented consent for schema v1.
- Slice 11B-A is approved at
  `docs/plan/done/11b-a-moka-provider-foundation.md` and was later accepted on
  22 July 2026.

G1, C3–C4/G4c, C6 and C7 remain `BLOCKED`. This checkpoint grants no real
provider credential, release/settlement, production or real-money authority.

## Execution checkpoint — Slice 11B-A ACCEPTED on 22 July 2026

The planner accepted A-P1–A-P6 at `main@7e773d9` after complete diff review,
targeted FIX revisions and an independent rerun of the material external HTTP
relay/recovery integration test. Evidence is
`docs/plan/done/review/slice-11b-a-acceptance-2026-07-22.md`.

This retires only the local/CI Moka transport foundation. C3–C4/G4c and G1
remain `BLOCKED` pending Slice 11B-B B-G0 real-provider evidence, revised ready
approval, staging funding acceptance and independent finality decision. C6 and
C7 remain `BLOCKED`; no release, settlement, production or real-money authority
is granted.

## Execution checkpoint — simulation-only route ACCEPTED on 22 July 2026

The founder/user explicitly decided that Moka payment and release exist only as
simulation and approved successful simulated release completing the demo Deal.
`docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md` supersedes the
real-provider 11B-B/G1 route without rewriting the historical checkpoint above.

G1-S and C3–C4/G4c are `ACCEPTED` for simulation scope. The former G1 provider
gate and Slice 11B-B are `SUPERSEDED`, not falsely accepted as provider proof.
G3 remains binding. C6 and C7 remain `BLOCKED`; ADR-014 is now the next allowed
decision work and no Slice 14B task packet exists yet.
