# C0 — 14A/Slice 13 Browser Debt Acceptance — 21 July 2026

## Result

Gate C0 is `ACCEPTED` after the second-control FIX completed the missing §6
evidence. The archived Slice 14A Section 6 matrix and the inherited Slice 13
historical VIDEO/MP4 advisory-panel observation were exercised against the
local Compose stack (PostgreSQL, RabbitMQ, MinIO, Mock AI Worker), Core API
`local,local-sandbox` (Flyway V22), and Vite `:5173`.

Baseline branch for planning: `planner/slice-14a-closure@f26df72`.
Primary Deal: `DL-0000000017` / `4342aa36-8e32-4a24-ad4f-09d0b5fbe94f`.

Dispute cases used for evidence:

- First matrix case `55c8248a-94fe-43a0-a70f-895f35624643` (`OPEN` →
  `UNDER_REVIEW` → `WITHDRAWN`).
- FIX concurrent-race case `27352a6f-244b-4e09-8ea8-4a843b94b704` (buyer
  `201` winner / seller `409` loser on empty active-case slate, then
  comment → acknowledge → withdraw).

This record retires the transferred browser debt from Slice 14A closure and the
Slice 13 historical-panel waiver. It does not rewrite those historical
acceptance records.

## Fixture

Six local `s14b-c0-*@example.test` accounts with Buyer/Seller/Other/
Nonparticipant legal entities (ADMIN + MEMBER where required). Disposable
local passwords were reset for this gate only; the current shared fixture
password is held out of band and is **not** recorded in repository documents.
After the FIX, hashes were rotated again and Spring Sessions for these
accounts were invalidated.

## Matrix outcomes

| Item | Result | Notes |
| --- | --- | --- |
| Prerequisite ACTIVE+FUNDED+fulfillment | PASS | Dual ratification, sandbox funding `SUCCEEDED`, Seller start |
| Slice 13 historical VIDEO panel | PASS | Rejected `c0-delivery-success.mp4` retained `RESULT_AVAILABLE` / `NO_ISSUE_DETECTED`; no history request/retry; current analysis not duplicated onto history row |
| Open + refresh | PASS | Buyer ADMIN open → `OPEN`; Deal lifecycle `DISPUTE`; snapshot retained both evidence rows + prior successful VA |
| Same-key replay | PASS | Same Idempotency-Key → same case id `55c8248a-…` |
| Concurrent distinct-key open | PASS | **True race on empty active-case slate:** Buyer `201` / `OPEN` `27352a6f-…`; Seller `409 DISPUTE_ACTIVE_CASE_EXISTS`; exactly one active OPEN row |
| MEMBER read/comment | PASS | Buyer/Seller MEMBER deal+dispute `200`; comments `201` |
| MEMBER forced mutations | PASS | open `403 DISPUTE_OPEN_FORBIDDEN`; acknowledge `403 DISPUTE_ACKNOWLEDGE_FORBIDDEN`; withdraw `403 DISPUTE_WITHDRAW_FORBIDDEN` |
| Seller ADMIN acknowledge | PASS | `UNDER_REVIEW`; comments unchanged at ack; fulfillment unchanged |
| Party lifecycle / other / nonparticipant | PASS | Buyer/Seller `DISPUTE`; Other `FULFILLMENT` + `casework=null` + dispute `404`; Nonparticipant deal+dispute `404` |
| Fulfillment independence | PASS | While active dispute, Buyer could request VA on replacement (`202 QUEUED` → `RESULT_AVAILABLE`) and later reject evidence |
| Late change vs snapshot | PASS | After late reject + late VA, openingSnapshot SHA-256 unchanged; snap still showed replacement `SUBMITTED` at open |
| 21-comment pagination | PASS | `totalElements=21`, page0 size20, page1 size1; createdAt ascending |
| Withdraw + history restore | PASS | `WITHDRAWN`; Deal lifecycle back to `FULFILLMENT` / `casework=null`; detail+comments+snapshot retained; UI “Geri çekilmiş geçmiş” |
| Stale recovery | PASS | Post-withdraw stale `expectedVersion` → `409 DISPUTE_STALE_VERSION` |
| Empty / loading / backend-error | PASS | Empty active casework: open form + withdrawn history only (`casework=null`, `canOpen=true`). Loading: `Oturum doğrulanıyor` and `Deal yükleniyor`. Backend-error: API stopped → UI `Bağlantı kurulamadı` + `Yeniden dene` |
| No casework side effects (full lifecycle) | PASS | FIX case open→comment→ack→withdraw: deal/fulfillment/evidence/payment/VA/outbox business rows unchanged vs pre-open baseline (empty diffs). Earlier matrix withdraw likewise left fulfillment/evidence/payment/VA unchanged |
| Desktop/mobile presentation | PASS | Desktop + 390×844 mobile: withdrawn history + open form visible; no horizontal overflow |

## Explicit non-claims

- No production, staging, or real-provider payment evidence.
- No Slice 14B implementation authorization.
- Historical Slice 13 / 14A acceptance records remain as written; debt retirement
  is recorded here and in current/future planner-state documents only.
- Fixture passwords are not stored in git.

## Evidence pointers (local, redacted)

- Deal reference `DL-0000000017`.
- Concurrent winner dispute `27352a6f-…`; first matrix dispute `55c8248a-…`.
- Rejected historical VIDEO `48b291eb-…` / job `fefb3312-…`.
- Late replacement VIDEO `53131cfb-…` / job `83ddfb48-…`.
- Charter execution checkpoints in
  `docs/plan/planning/gates/settlement-release-readiness-charter-2026-07-21.md`.
