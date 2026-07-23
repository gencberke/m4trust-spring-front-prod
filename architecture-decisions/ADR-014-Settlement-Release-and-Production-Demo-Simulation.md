# ADR-014: Settlement, Release, and Production Demo Simulation

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Demo settlement/release, production simulation boundary, ratification
  compatibility, dispute race, operation recovery, and Deal completion
- Değiştirdiği kararlar:
  - ADR-003 §14 yalnız `SIMULATED_SETTLED` demo terminal durumuyla genişletilir.
  - ADR-010 §§2.2, 2.6 ve 2.7 yalnız bu ADR'deki ayrı
    `DEMO_SIMULATED` adapter için genişletilir; test sandbox adapter'ı production'da
    yasak kalır.
  - ADR-011 §2.5'teki automatic-completion yasağı korunur; yalnız explicit release
    ve query-verified terminal sonuç sonraki Slice 14B'de Deal'i tamamlayabilir.
  - ADR-013 §2.8'de öngörülen active-dispute settlement gate'i etkinleştirilir;
    casework payment repository'sine erişmez.
- Bağlı kararlar: ADR-003, ADR-006, ADR-009, ADR-010, ADR-011, ADR-013, ADR-015
- Karar girdileri:
  - `docs/plan/planning/gates/g2-g3-founder-decision-2026-07-21.md`
  - `docs/plan/planning/gates/simulation-only-payment-decision-2026-07-22.md`

## 1. Bağlam

Accepted funding foundation gerçek para hareketi olmadan provider-benzeri bir
durable operation ve query-first recovery akışı kurmuştur. Fulfillment ve
casework foundation'ları ise completion, active-dispute visibility ve
no-side-effect sınırlarını sabitlemiştir. Settlement/release için gerekli exact
state, authorization, cardinality, idempotency, lock ve transaction kararları
henüz alınmamıştır.

Önceki simulation kararı yalnız local/CI/staging yetkisi vermiş ve production'ı
fail-closed bırakmıştır. Founder artık gerçek ödeme iddiası taşımayan production
demo akışını istemektedir. Test scenario seçimini production'a açmak yasaktır;
bu nedenle production demo modu sandbox adapter'dan ayrı tanımlanır.

## 2. Karar

### 2.1 İşletim modu ve değişmez non-claim sınırı

Settlement/funding projection'ları exact mode taşır:

- `DEMO_SIMULATED`
- gelecekte ayrı ADR olmadan başka production mode yoktur.

`DEMO_SIMULATED`:

- gerçek banka, kart, merchant, provider veya müşteri parası kullanmaz;
- custody, KYC, fee, split, payout, chargeback veya finansal finality iddiası
  üretmez;
- public scenario endpoint/header/body/query alanı içermez;
- production'da test sandbox adapter'ını veya Moka emulator'ını çalıştırmaz;
- yalnız private, integration-owned demo simulator'a bağlanır;
- API, frontend, e-posta, audit ve operasyon ekranlarında açıkça
  `SIMULATED`/`DEMO` olarak gösterilir.

Production demo simulator aynı operation identity için kalıcı ve tekrar
sorgulanabilir sonuç üretir. Production config'inde outcome scenario seçimi
yoktur; initiated operation bounded delay sonrasında simulated success üretir.
Decline/timeout/crash senaryoları yalnız local/CI/staging config'inde test edilir.

### 2.2 Ratification schema v2 ve contractual window

G3 kararı bağlayıcıdır:

- mevcut snapshot `schemaVersion=1` olarak okunmaya devam eder;
- existing `/api/v1` create request'ine optional `disputeWindowDays` eklenir;
- alan varsa server closed `schemaVersion=2` snapshot oluşturur;
- v2 içinde alan required integer ve inclusive `0..365` aralığındadır;
- bir gün exact 24 saatlik UTC aralığıdır;
- alan package create sonrasında immutable'dır; değişiklik yeni package version ve
  iki tarafın yeniden ratification'ını gerektirir;
- v1 veya bozuk/missing v2 package release-ineligible kalır; migration default veya
  consent icat etmez;
- v2 canonical snapshot RFC 8785/JCS + SHA-256 kurallarını korur.

Buyer evidence acceptance transaction'ı immutable `fulfillment.completedAt`
değerini aynı server `Instant` ile bir kez yazar. Historical completed rows yalnız
tek authoritative accepted evidence `acceptedAt` değeriyle backfill edilir;
cardinality tutarsızlığı migration'ı durdurur. Release deadline:

```text
completedAt + disputeWindowDays * 24 hours
```

olur ve ilk eligible an `now >= deadline` sınırıdır.

**Founder amendment (2026-07-23):** `disputeWindowDays` inclusive aralığı `1..365`'ten
`0..365`'e genişletildi. `0` anında eligibility sağlar (`completedAt + 0`); v1
packages ve missing window release-ineligible kalır.

### 2.3 Aggregate, cardinality ve state

`payment` modülü aşağıdakilerin sahibidir:

- Deal/funding unit başına tam bir `Settlement`;
- Settlement başına en fazla bir lifetime release operation;
- immutable operation attempt/result history;
- durable dispatch/reconciliation kayıtları;
- participant-safe settlement projection.

Financial `SETTLED` 14B'de unreachable kalır. Settlement transition'ları:

```text
NOT_READY -> READY -> PROCESSING
PROCESSING -> ON_HOLD | SIMULATED_SETTLED | FAILED
ON_HOLD -> PROCESSING | SIMULATED_SETTLED | FAILED
```

`SIMULATED_SETTLED` yalnız authoritative simulator query ile doğrulanır.
`FAILED` yalnız query-verified `SIMULATED_DECLINED` veya external çağrıdan önceki
kesin permanent local dispatch/config failure için kullanılabilir. Timeout,
disconnect, crash, ambiguous response veya missing commit `FAILED` değildir.

Release operation public state'i:

- `QUEUED`: intent ve durable dispatch commit edildi;
- `PROCESSING`: aynı lifetime key ile initiate/query çalışıyor;
- `RECONCILIATION_REQUIRED`: external outcome veya late-dispute etkisi bilinmiyor;
- `SIMULATED_SETTLED`: query-verified terminal simulated success;
- `SIMULATED_DECLINED`: query-verified terminal simulated decline;
- `FAILED_BEFORE_DISPATCH`: external call yapılmadığı kanıtlı permanent failure.

Terminal operation sonrasında yeni release operation açılamaz. Unknown/hold state
aynı operation ve aynı lifetime key ile reconcile edilir.

### 2.4 Authorization, visibility ve eligibility

Deal participant'ları safe settlement summary ve operation projection'ını
okuyabilir. Release/reconcile mutation'ı yalnız buyer legal entity `ADMIN`
kullanıcısına açıktır. MEMBER, seller, initiator-only participant ve diğer
participant'lar read-only'dir. Nonparticipant ve nested-resource mismatch
non-disclosing 404 döndürür.

Release intent ancak bütün koşullar lock altında tekrar doğrulanırsa oluşur:

- Deal `ACTIVE`;
- current ratification package v2 ve `RATIFIED`;
- funding aynı unit için `DEMO_SIMULATED` mode'da query-verified `FUNDED`;
- fulfillment `COMPLETED` ve immutable `completedAt` mevcut;
- contractual dispute window geçmiş;
- active (`OPEN|UNDER_REVIEW`) dispute yok;
- settlement `READY` ve release operation yok.

Fulfillment completion veya window expiry automatic release oluşturmaz.
Frontend deadline/action hesaplamaz; `canRequestRelease` ve
`canReconcileRelease` backend-derived'dır.

### 2.5 Public API ve stable errors

Slice 14B contract-first yüzeyi:

```text
GET  /api/v1/deals/{dealId}/settlement
POST /api/v1/deals/{dealId}/settlement/release
GET  /api/v1/release-operations/{operationId}
POST /api/v1/release-operations/{operationId}/reconcile
```

Mutation'lar session, CSRF, active legal-entity context, `Idempotency-Key` ve
contract'ta belirtilen exact `expectedVersion` alanlarını ister. Release ve
reconcile provider sonucunu HTTP request içinde beklemez; `202 Accepted`,
`Location` ve operation projection döndürür.

Closed business error family en az şu semantic ayrımları taşır:

- missing contractual window;
- window not elapsed;
- active dispute;
- forbidden actor;
- hidden settlement/operation;
- stale Deal/funding/fulfillment/settlement/operation version;
- operation already exists;
- reconciliation not available;
- unknown simulated outcome;
- terminal simulated settlement;
- idempotency-key reuse.

Exact code isimleri B-P1 committed OpenAPI review biriminde sabitlenir; aynı
semantic için free-form veya provider-specific code kullanılamaz.

### 2.6 Lock, transaction ve idempotency

Release eligibility/intent ve terminal result application aynı global sırayı
kullanır:

```text
Deal
-> FundingPlan/FundingUnit
-> fulfillment/milestone/current accepted evidence
-> Settlement/ReleaseOperation
-> active Dispute rows in deterministic ID order
```

Ratification snapshot immutable projection olarak okunur. Hiçbir modül yabancı
repository veya JPA entity kullanmaz; lock/read işlemleri consumer-owned narrow
port'larla yapılır.

Release HTTP transaction'ı atomik olarak şunları yazar:

```text
Settlement transition + ReleaseOperation + audit
+ HTTP idempotency result + durable simulator dispatch
```

Simulator çağrısı transaction dışında yapılır. Result application yeni kısa
transaction'da aynı lock order ile operation, settlement, audit ve gerektiğinde
Deal transition'ını yazar. Aynı HTTP key/same canonical request aynı operation'ı
döndürür; farklı request `IDEMPOTENCY_KEY_REUSED` alır. Distinct concurrent keys
DB cardinality constraint ile tek operation'a düşer.

### 2.7 Query-first dispatch ve recovery

Relay claim transaction'ını kapattıktan sonra önce aynı lifetime operation key
ile `queryStatus` çağırır:

- terminal sonuç varsa uygular;
- `NOT_FOUND` ve active dispute yoksa aynı key ile initiate eder;
- response/transport belirsizse `RECONCILIATION_REQUIRED` bırakır;
- yeni key veya replacement operation üretmez.

Initiate response terminal proof değildir. Yalnız sonraki authoritative query
`SIMULATED_SETTLED` veya `SIMULATED_DECLINED` kanıtı sağlar. Duplicate dispatch,
process restart ve late result aynı operation'a idempotent uygulanır.

### 2.8 Dispute yarışları ve Deal completion

- Dispute-first Deal lock'ını kazanır; release intent/dispatch oluşmaz.
- Release intent first olup dispute external initiate öncesi açılırsa relay yalnız
  query yapar, `NOT_FOUND` ise initiate etmez ve settlement `ON_HOLD` kalır.
- Initiate sonrasında dispute açılırsa aynı operation
  `RECONCILIATION_REQUIRED`/`ON_HOLD` olur; cancellation veya replacement
  operation varsayılmaz.
- Active dispute varken terminal simulator sonucu immutable history'ye alınabilir
  fakat Deal completion uygulanmaz.
- Dispute withdrawal sonrasında buyer ADMIN aynı operation'ı reconcile eder.
- Query-verified `SIMULATED_SETTLED` result transaction'ı active dispute olmadığını
  yeniden doğrular ve atomik olarak Settlement'i `SIMULATED_SETTLED`, Deal'i
  `ACTIVE -> COMPLETED` yapıp audit yazar.
- Deal tamamlandıktan sonra ADR-013 opening eligibility gereği yeni dispute
  açılamaz.

Casework payment state'ini doğrudan değiştirmez ve payment repository'sine
erişmez. Payment kendi transaction'ında casework narrow gate'ini okur.

### 2.9 UI, audit ve telemetry non-claim

Generic "paid", "settled", "funds transferred" veya provider logosu kullanılmaz.
UI en az "Demo simulated — no real money moved" ifadesini görünür tutar.
Public DTO, audit ve telemetry raw simulator payload, credential veya internal
transport detail taşımaz. `mode=DEMO_SIMULATED` hiçbir layer'da gizlenemez.

## 3. Sonuçlar

- ADR-014 decision-complete'tir; implementation yalnız human-approved Slice 14B
  ready plan/task packet ile başlar.
- Existing schema v1 package'lar uyumlu fakat release-ineligible kalır.
- Production demo simulator test sandbox'ından ayrıdır; mevcut production sandbox
  yasağı korunur.
- Refund, reversal, chargeback, mutual ACTIVE cancellation, real provider ve real
  money ayrı gelecek ADR/slice kapsamıdır.

## 4. Kabul kapıları

- State/transition, actor, cardinality, lock, transaction ve idempotency matrisi
  contract + DB + concurrency testleriyle kanıtlanır.
- V1/v2 ratification compatibility ve immutable `completedAt` migration'ı testlidir.
- Dispute-first, release-first, crash, duplicate, timeout ve restart senaryoları
  query-first ve no-duplicate-operation kanıtı üretir.
- Financial `SETTLED`, automatic release, refund/reversal ve production test
  scenario control unreachable'dır.
- Browser acceptance bütün simulated/non-claim etiketlerini doğrular.
