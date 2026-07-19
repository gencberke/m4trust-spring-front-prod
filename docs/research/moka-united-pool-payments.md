# Moka United — Pool Payment (Havuz Ödeme) Research Report

Date: 2026-07-19. Research agent output (read-only web research on
developer.mokaunited.com). Input for the Slice 11 payment design ADR and the
Slice 11B provider integration plan. Treat as point-in-time findings; verify
the gaps with Moka directly before integration.

Sources: index.php, ?page=java, ?page=havuzonay, ?page=havuzonayiptal, plus
3dsiz-odeme, 3dli-odeme, iptal-islemi, iade-talebi, capture,
odeme-detay-listesi, MP-havuzonay, hata-kodlari, sss.

## 1. Pool payment model

Pool payment is a flag on the initial charge, not a separate charge operation.
`DoDirectPayment` / `DoDirectPaymentThreeD` accept `IsPoolPayment` (tinyint):

- `0` — normal payment; funds go to merchant next business day.
- `1` — funds captured immediately but held in an escrow-like "pool" state,
  excluded from the merchant settlement statement until explicitly approved.

Two operations act on a pooled transaction:

- `POST /PaymentDealer/DoApprovePoolPayment` — releases the held payment into
  the normal settlement flow. Identified by `VirtualPosOrderId` or
  `OtherTrxCode` (one required). Fails if not found, already approved, or not
  a pool payment.
- `POST /PaymentDealer/UndoApprovePoolPayment` — reverses a pool approval.
  Fails if not yet approved, not a pool payment, or IDs mismatch. Explicit
  rule: executable before end-of-day settlement / before merchant statement
  generation (settlement-batch cutoff, not a fixed multi-day window).

Marketplace variant (MP-havuzonay, same endpoint) adds a required `SubDealer`
array (sub-merchant DealerIds) — but the docs do not explain how amount splits
across sub-dealers are configured.

No documented way to cancel/reject the pool without first approving; refunding
a pooled-but-unapproved payment presumably requires standard `DoVoid` /
`DoCreateRefundRequest`, but the docs never state whether those apply to pool
state (see Gaps).

## 2. State machine

`GetDealerPaymentTrxDetailList` documents `PaymentStatus`: 0 Beklemede,
1 Ön Provizyon, 2 Ödeme, 3 İptal, 4 Tam İade. There is NO distinct enumerated
status for "in pool" vs "approved" — pool state is not visible as a
PaymentStatus value. `TrxType`: 1 Pre-auth, 2 Payment, 3 Cancellation,
4 Refund. `TrxStatus`: 1 Success, 2 Fail.

No partial pool approval documented — approve/undo act on the whole
transaction, no Amount parameter (unlike `DoCreateRefundRequest`, which
supports partial amounts). Only timing rule found: the settlement cutoff on
UndoApprovePoolPayment. Note `IsPreAuth=1` + `DoCapture` is a separate
pre-authorization mechanism (capture can vary ±15%), not the pool mechanism.

## 3. Idempotency and duplicates

- `OtherTrxCode` is the merchant-supplied unique transaction reference and
  lookup/idempotency key. Approve/undo accept `VirtualPosOrderId` OR
  `OtherTrxCode`; errors: `...MustGiven` when neither, `...NotMatch` when
  inconsistent.
- Duplicate guards: `PaymentAlreadyApproved` blocks double approval;
  `PaymentNotApprovedYet` blocks undoing an unapproved payment.
- Status inquiry / reconciliation: `GetDealerPaymentTrxDetailList` keyed by
  `PaymentId` or `OtherTrxCode`, returns full transaction history.
- Global bank error `021 Zaman Aşımı` (timeout) exists — timeouts must be
  resolved by status query, never assumed failures.
- NOT documented: idempotency behavior of `DoDirectPayment` itself for a
  duplicate `OtherTrxCode` submitted as a new charge. Ask Moka.

## 4. Result delivery

- Non-3D charge: purely synchronous JSON response (`IsSuccessful`,
  `ResultCode`, `VirtualPosOrderId`).
- 3D Secure charge: browser-redirect based. The bank redirects the customer
  browser to the merchant `RedirectUrl` via HTTP POST with `hashValue`,
  `resultCode`, `resultMessage`, `trxCode`, `OtherTrxCode`. `ReturnHash=1`
  requests `hashValue`; the verification-algorithm page was referenced but not
  retrieved. `RedirectType=1` selects iframe redirect.
- This is a browser-mediated callback, NOT a server-to-server webhook. No
  evidence of any server-side webhook/notification system anywhere in the
  fetched pages. Pool approve/undo are plain synchronous calls.
- Reconciliation for missed redirects: poll `GetDealerPaymentTrxDetailList`.

## 5. Auth and security

- `PaymentDealerAuthentication`: DealerCode, Username, Password, CheckKey.
- `CheckKey = SHA-256(DealerCode + "MK" + Username + "PD" + Password)` —
  a static derived shared secret recomputed per request; not a
  nonce/timestamp signature. Same formula for all endpoints.
- Environments: Test `https://service.refmokaunited.com`; Production
  `https://service.mokaunited.com`. (The Java page uses
  `service.testmokaunited.com` — inconsistent; clarify.)
- TLS 1.2+, PCI-DSS stated. No IP-restriction mechanism described on the
  portal. Sandbox exists but the credential-provisioning process is not
  detailed.

## 6. Amounts and currency

- `Amount` is DECIMAL with dot separator in major units (e.g. "27.50") — NOT
  integer minor units. Our adapter must convert from internal amountMinor.
- `Currency` optional, defaults TL; USD, EUR, GBP supported. Foreign currency
  cannot use installments.
- 3D Secure is enforced at the merchant-account level; a non-3D call is
  rejected with `ThreeDRequired` if the account demands 3D. Tokenized-card
  bypass is a parametric permission. Nothing pool-specific about 3D/currency.

## 7. Java integration

`?page=java` is a sample-code walkthrough (OkHttp3 + Jackson), not an SDK:
`sendDirectRequest()`, `send3DRequest()`, the CheckKey SHA-256 computation,
request field construction, and a sample JSP result page for the 3D redirect.
No pool-specific Java sample; the same JSON POST pattern presumably applies.

## 8. Fees / settlement notes

- Normal settlement: next business day. Pool payments are excluded from the
  statement until approved; approval enrolls funds into settlement.
- `DoVoid` (cancel): same-day only, until 22:00.
- `DoCreateRefundRequest`: only the day AFTER payment or later (no same-day
  refund); partial amounts supported; `RefundRequestAlreadyExist` blocks
  concurrent pending refunds.
- Sub-merchant onboarding endpoints exist (MP-bayi-olusturma / -gorme /
  -guncelleme) but were not fetched; KYC requirements unknown.
- Commission/fees are not in the API docs (commercial/contractual).

## 9. Gaps and risks — ask Moka directly

1. How to detect "in pool, unapproved" vs "approved" vs "settled" via the
   status endpoint (no pool status enum documented).
2. Can a pooled-but-unapproved payment be refunded/voided directly, or must
   it be approved first?
3. Exact maximum pool holding time; any auto-expiry/auto-cancel behavior.
4. hashValue computation/verification algorithm for the 3D redirect.
5. Sub-merchant amount-split mechanism for marketplace pool approval.
6. Charge-endpoint idempotency for a duplicate `OtherTrxCode`.
7. Sandbox credential provisioning; whether the test environment supports the
   full pool approve/undo flow; refmokaunited vs testmokaunited hostname
   discrepancy.
8. Confirm there is truly no server-to-server webhook for any event.
9. Sub-merchant KYC/onboarding requirements for pool/marketplace mode.
10. Whether an approved pool payment can ever be refunded same-day
    (dispute-reaction speed).

## Implications for M4Trust (payment design ADR inputs)

- Charge into pool = a single `DoDirectPayment(ThreeD)` with `IsPoolPayment=1`
  at funding time — fits the single-charge-per-deal / single FundingUnit v1.
- Release on fulfillment = `DoApprovePoolPayment` keyed by our
  `OtherTrxCode` (set to our internal operation id for idempotent lookups).
- Cancellation is TWO distinct paths and the ADR must model both:
  pre-approval cancel (`UndoApprovePoolPayment`, settlement-cutoff bound) vs
  post-approval refund (`DoCreateRefundRequest`, next-day-or-later only).
  There is no single uniform refund call.
- Our system is the source of truth for pool/release state: Moka exposes no
  pool status enum, so our own ledger (a recorded successful approve call)
  defines the state; Moka's transaction-detail endpoint is reconciliation
  only.
- Result channel: POLLING-first design. Only the initial 3D charge has a
  browser-redirect push, which is a UX signal, not a reliable state trigger.
  A reconciliation job polls `GetDealerPaymentTrxDetailList` by `OtherTrxCode`
  for any operation whose response was not cleanly received (bank timeout 021
  is a real outcome class).
- Amount conversion: internal integer minor units → Moka decimal major units
  happens ONLY inside the provider adapter; the core keeps integers.
- Biggest open items before Slice 11B: pool-state detection (#1), pre-approval
  refund path (#2), pool holding limit (#3), charge idempotency (#6).

## Gap handling — self-reliant strategy (no direct Moka contact available)

Decision (2026-07-19): the gaps above cannot be resolved by asking Moka; the
payment design ADR adopts defensive assumptions and empirical verification
instead. Binding principles:

1. Never depend on undocumented behavior; assume the worst case; verify
   empirically against the Moka test environment.
2. **Probe suite is the first Slice 11B deliverable:** an automated test pack
   that exercises the full pool flow (charge → status → approve → undo →
   refund attempts) against the test environment. Probe results are recorded
   as evidence in the ADR; assumptions are upgraded only with probe proof.
3. Gap #1 (no pool status enum): our ledger is the sole source of truth for
   pool/release state; the Moka status endpoint is reconciliation-only.
4. Gap #2 (pre-approval refund unknown): ASSUME not possible. Money-return
   before release is modeled as approve → next-day refund (funds transit the
   statement for one day; accounting note required). If probes show direct
   void/refund works on pooled state, relax the ADR.
5. Gap #3 (pool holding limit unknown): aged-pool monitoring with alerting on
   operations pooled longer than a configured threshold; release-timing policy
   approves promptly after fulfillment acceptance; the unknown maximum stays
   an explicit open risk in the ADR until probed.
6. Gap #4 (redirect hash unverified): 3D redirect is UX-only; NO state
   transition is driven by redirect data; every outcome is confirmed via
   status polling.
7. Gaps #5/#9 (sub-dealer split/KYC): v1 target is the marketplace
   (sub-dealer) pool model — platform-held funds with manual EFT payout risks
   unlicensed payment-institution exposure under Law 6493 and is rejected as
   default. Requires explicit legal review; sub-dealer onboarding is probed
   empirically in 11B.
8. Gap #6 (charge idempotency unknown): charges are never blindly retried.
   Intent record → on unclear outcome, status query by OtherTrxCode → only a
   confirmed-absent charge may be retried, and only with a NEW OtherTrxCode.
9. Gap #10 (no same-day refund after approve): release timing waits for the
   fulfillment-acceptance gate; approval is not granted while a dispute
   window is open.
