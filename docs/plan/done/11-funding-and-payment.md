# Slice 11 — Funding Foundation (Provider-Bağımsız Sandbox)

- Durum: done
- Tamamlanma tarihi: 20 Temmuz 2026
- Kapsam sapması: Yok. Kabul hardening'i UNCONFIRMED-only reconcile,
  ratification-package provenance snapshot'ı ve FUNDED sonrası FULFILLMENT
  lifecycle projection'ını plan/ADR sınırları içinde netleştirdi.
- Slice sırası: ADR-004 §24 → "Funding and Payment" (bölünmüş yol haritasında 11)
- Öncül: 10-ratification (funding yalnız ACTIVE Deal'de anlam taşır; funding
  tutarı Slice 10 package'ının structured commercial terms alanından gelir)
- Ardıl: **Slice 11B — gerçek provider entegrasyonu** (ayrı plan; aday: Moka
  United havuz ödeme modeli) ve fulfillment/evidence slice'ı. Payment
  release/settlement bu planın kapsamı DIŞINDADIR.
- **Eskalasyon sonucu:** Funding foundation kararları 19 Temmuz 2026 tarihli
  insan onayıyla ADR-010'da bağlayıcı olarak kapatıldı. Bu slice yalnız
  provider-bağımsız sandbox foundation'ı uygular; gerçek provider seçimi,
  callback/3D Secure ve staging kabulü Slice 11B'dir. Onay planda açıkça tarif
  edilen public OpenAPI yüzeyi ile onu kilitleyen
  `contracts/scripts/validate_contracts.py`, `contracts/README.md` ve
  `contracts/CHANGELOG.md` additive güncellemelerini kapsar. AI JSON
  Schema/fixture, AsyncAPI veya AI-internal OpenAPI değişikliği gerekmez;
  bunlardan biri gerekirse eskalasyondur.

## 1. Amaç ve kullanıcı sonucu

ACTIVE (ratified) bir Deal'de buyer tarafı gerçek tarayıcıdan funding sürecini
başlatır → Deal'in funding planı (v1'de TEK funding unit; tutar ratified
package'ın structured commercial terms alanından) görünür → buyer ödeme
başlatır → sandbox provider sonucu döner → FundingStatus ilerleyişi Deal
detayında izlenir: NOT_CONFIGURED → PLANNED → PENDING → FUNDED
(PARTIALLY_FUNDED v1'de erişilemezdir; çoklu unit ileriki iş). Başarısız
ödeme FAILED unit durumu üretir ve yeniden denenebilir; provider timeout'u
asla otomatik failure sayılmaz (ADR-003 §21).

Bu slice provider-BAĞIMSIZ foundation'dır: amaç port, durum makinesi,
idempotency, reconciliation ve UI iskeletinin sandbox adapter'la uçtan uca
kanıtlanmasıdır. Gerçek provider (Slice 11B) yalnız yeni bir adapter olarak
eklenmeli, bu slice'ın hiçbir davranışını değiştirmemelidir.

Seller ve diğer participant'lar funding durumunu okur; mutation yalnız buyer
entity ADMIN'ine aittir. Sandbox, provider tarafından tutulan fon girişini
simüle eder; gerçek para hareketi ve "M4Trust parayı tutuyor" iddiası yoktur.
Release, payout, refund ve settlement sonraki slice'ların işidir (ADR-004 §19,
ADR-010 §2.2).

## 2. Kapsam / kapsam dışı

Kapsam:

- `payment` modülü (ADR-003 §4.7): FundingPlan/FundingUnit aggregate'leri,
  payment operation kayıtları
- FundingStatus ekseni (ADR-003 §12) ve DealLifecycleProjection'a FUNDING
  girdisi
- Funding planının buyer ADMIN tarafından explicit, idempotent action ile
  oluşturulması — tutar ratified package'ın structured commercial terms
  alanından; v1'de Deal başına TEK plan ve plan içinde TEK funding unit
- Payment provider PORT'u + local sandbox adapter (integration modülü,
  ADR-003 §4.11); gerçek provider adapter'ı Slice 11B
- Provider idempotency key zorunluluğu; duplicate isteğin duplicate para
  hareketi üretmemesi (ADR-003 §21)
- Timeout/bilinmeyen sonuç → reconciliation-gerekli durumu ve manuel/basit
  yeniden-sorgulama action'ı
- Public API funding yüzeyi + `Idempotency-Key` zorunlu payment action'ları
- Audit aynı transaction'da; provider çağrıları DB transaction'ı DIŞINDA

Kapsam dışı:

- **Gerçek provider entegrasyonu → Slice 11B** (adapter, credential/secret
  kurulumu, gerçek callback doğrulaması, staging kabulü)
- Çoklu funding unit / kısmi fonlama yüzeyi (PARTIALLY_FUNDED v1'de
  erişilemez)
- Payment release, payout, settlement (SettlementStatus akışı) → sonraki
  slice'lar
- Refund/reversal (açık business operation olarak ayrıca modellenecek)
- Fulfillment/evidence; dispute etkileri
- Gerçek provider entegrasyonunun staging/production kurulumu (provider
  kararı sonrası ayrı operasyonel iş)
- Webhook/callback tasarımı ve imza doğrulama altyapısı (gerçek provider işiyle
  birlikte Slice 11B'de tasarlanır ve kabul edilir)
- Faiz/komisyon/ücret hesaplamaları

## 3. Okunacak ADR bölümleri

- ADR-003 §4.7, §4.11, §12, §16, §21, §23–§25
- ADR-010 tamamı (V1 scope, state machine, crash recovery ve yetki)
- ADR-001 §2.2, §20 (FastAPI'nin payment'a dokunamaması; sınırlar)
- ADR-006 §24–§25 (Idempotency-Key), §18–§19, §33, §39
- ADR-004 §19 (dev'de gerçek para yasağı), §22–§23
- ADR-007 §19–§20 (provider secret yönetimi)
- FORBIDDEN §1–§2 payment satırları

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Funding planı oluşturma action'ı (buyer ADMIN; Deal `expectedVersion` +
  `Idempotency-Key`) ve participant-readable görüntüleme. Create body serbest
  tutar/currency veya sandbox scenario taşımaz; değerler ratified package'tan
  server-side kopyalanır. Synchronous resource create `201 Created`, FundingPlan
  body ve `Location: /api/v1/deals/{dealId}/funding-plan` döner; Deal başına
  ikinci plan create edilmez.
- `POST /funding-units/{fundingUnitId}/payment-operations`: ödeme başlatma
  command'ı (`Idempotency-Key` ZORUNLU; FundingUnit `expectedVersion` ile).
  Durable operation/outbox/audit/idempotency commit edildikten sonra provider
  sonucu beklenmeden `202 Accepted`, `Location:
  /api/v1/payment-operations/{paymentOperationId}` ve `CREATED`
  PaymentOperation projection'ı döner.
- `GET /payment-operations/{paymentOperationId}` ve funding-plan read
  projection'ı polling için operation/unit/funding durumunu sunar.
- Ödeme sonucu/durum read projection'ı; reconciliation-gerekli durumun açık
  gösterimi
- Polling-first read/reconciliation yüzeyi. Slice 11 public provider callback
  endpoint'i açmaz. `POST /payment-operations/{paymentOperationId}/reconcile`
  target operation `expectedVersion` + `Idempotency-Key` ister; reconciliation
  dispatch/outbox kaydını commit edip aynı operation `Location`'ı ve `202
  Accepted` döner. Provider query response içinde beklenmez. Callback/3D Secure
  gerçek provider ile Slice 11B'dedir.
- Deal detail'e funding özeti + actor-aware action'lar (`canInitiateFunding`
  vb.)

Additive compatibility kuralları:

- Mevcut closed `DealDetail` ve `DealAvailableActions` şemalarına eklenen
  funding summary/action member'ları optional olur; mevcut `required` listeleri
  ve alan anlamları değiştirilmez. Eksik/bilinmeyen action frontend'de `false`
  ve read-only kabul edilir.
- Yeni path/operation/schema/status beklentileri, `202` response body ve
  `Location` header'ları validator exact allowlist/response kontrollerine aynı
  contract commit'inde eklenir; README/CHANGELOG güncellenir.

Sabit davranışlar:

- ACTIVE olmayan Deal'de funding action'ları → 409 `DEAL_STATE_CONFLICT`
- Nonparticipant veya gizli Deal/funding kaynağı → non-disclosing 404. Deal'i
  görebilen seller/diğer participant ile buyer `MEMBER` create/initiate/
  reconcile denemesi → 403
- Aynı Idempotency-Key + aynı request → tek etki; farklı request → 409
- Tutarlar integer minor unit + ISO 4217 currency; float ASLA (ADR-006 §28)
- Timeout/bilinmeyen sonuç FAILED ÜRETMEZ; ayrı "UNCONFIRMED/reconciliation"
  gösterimi olur
- CREATED/PENDING dispatch veya UNCONFIRMED operation varken aynı unit için
  farklı Idempotency-Key ile yeni payment operation açılamaz → 409
- FUNDED'a ulaşmış unit üzerinde yeni ödeme başlatılamaz → 409
- Eşzamanlı funding-plan create denemeleri aynı DB unique invariant'ı altında
  tek plan/unit üretir; idempotent replay aynı sonucu döner

## 5. Backend yönlendirmesi

- **Aggregate'ler:** FundingPlan (Deal başına, ratified tutar + currency),
  FundingUnit (tutar, durum, sıra), PaymentOperation (unit başına girişimler:
  provider referansı, idempotency key, durum, ham olmayan sonuç özeti).
  Payment intent + sabit provider key + durable dispatch/outbox + audit + HTTP
  idempotency sonucu tek transaction'da persist edilir. Provider çağrısı TX
  DIŞINDA yapılır; sonuç ayrı transaction'da uygulanır (ADR-010 §2.4).
- **FundingPlan create:** kullanıcı action'ıdır; otomatik activation side-effect'i
  değildir. Deal satırı lock edilir, ACTIVE + RATIFIED package doğrulanır ve
  package id/amount/currency snapshot'ı ile tam bir FundingPlan + tek
  FundingUnit atomik oluşturulur. `UNIQUE(deal_id)` ve
  `UNIQUE(funding_plan_id, sequence_no)` DB kısıtları yarışta ikinci plan/unit'i
  engeller. Plan oluşturulduktan sonra amount/currency değişmez.
- **Exact durum makinesi:** FundingUnit `PLANNED → PENDING → FUNDED | FAILED`;
  definitive decline sonrası `FAILED → PENDING` ile yeni operation denenebilir.
  PaymentOperation `CREATED → SUCCEEDED | DECLINED | UNCONFIRMED`; yalnız
  definitive provider sonuçları SUCCEEDED/DECLINED üretir. UNCONFIRMED'dan
  query-first reconciliation ile terminal sonuca geçilir (ADR-010 §2.3).
- **Idempotency iki katmanlıdır:** (1) HTTP katmanı mevcut `idempotency`
  modülüyle; (2) provider katmanında her PaymentOperation kendi provider
  idempotency key'ini taşır ve retry AYNI provider key ile gider — duplicate
  para hareketi imkânsızlaşır (ADR-003 §21).
- **Timeout/reconciliation:** timeout veya belirsiz cevapta operation
  UNCONFIRMED kalır; unit FAILED'a düşmez, yeni ödeme başlatılması engellenir
  (çifte tahsilat riski). Basit reconciliation action'ı provider'dan durumu
  AYNI operation ve AYNI provider key ile yeniden sorgular. Kör initiate retry,
  yeni key veya yeni charge yasaktır. Otomatik scheduler bu slice'ta zorunlu
  değildir; polling/manual reconciliation yeterlidir.
- **Reconciliation dispatch:** public reconcile request provider'ı HTTP request
  içinde çağırmaz. Operation version kontrolü + durable reconciliation
  dispatch/outbox + audit + HTTP idempotency sonucu tek kısa transaction'da
  yazılır ve `202` döner. Relay kaydı claim edip transaction'ı kapattıktan sonra
  aynı operation/provider key ile `queryStatus` çağırır; kesin sonucu ayrı
  transaction'da uygular. Aynı reconcile key replay'i aynı sonucu/Location'ı
  döner, farklı payload aynı key ile 409 alır.
- **Tek in-flight operation:** FundingUnit PENDING iken CREATED veya UNCONFIRMED
  operation onun tek in-flight operation'ıdır. Aynı HTTP key replay edilir;
  farklı key ile initiate 409 alır. Yalnız kesin DECLINED ile unit FAILED
  olduktan sonra yeni operation/provider key oluşturulabilir.
- **Provider port:** dar interface (initiate, queryStatus); adapter integration
  modülünde. Her dispatch önce aynı provider key ile `queryStatus` çağırır;
  `SUCCEEDED/DECLINED` ise sonucu uygular, bilinmiyorsa UNCONFIRMED bırakır,
  provider açıkça `NOT_FOUND` döndürürse aynı key ile `initiate` çağırır.
- **Sandbox scenario mekanizması:** yalnız `local-sandbox` Spring profile'ında
  startup config'i olarak verilen deterministik sıra (`SUCCESS`, `DECLINE`,
  `TIMEOUT_THEN_SUCCESS`) her yeni operation tarafından sırayla tüketilir.
  Runtime test-control endpoint/header/body alanı yoktur; amount/currency gibi
  business verisi senaryo seçmez. Sandbox adapter production profile'da bean
  olamaz ve production bootstrap sandbox provider seçimiyle fail eder.
- **Crash recovery:** durable dispatch kaydı provider çağrısından önce vardır.
  Çağrıdan önce crash olursa worker aynı operation/key ile güvenle dispatch
  eder. Provider sonucu ile local sonuç uygulaması arasında crash olursa
  initiate tekrarlanmaz; aynı key ile query-first reconciliation yapılır.
  Provider authoritative sonuç veremiyorsa UNCONFIRMED korunur ve yeni charge
  bloklanır (ADR-010 §2.4–§2.5).
- **Yetki:** funding mutation yalnız buyer entity ADMIN'ine açıktır. Buyer
  MEMBER, seller ve diğer participant'lar salt-okunurdur; frontend butonu
  gizlese de backend policy zorunludur.
- **Sınırlar:** FastAPI/AI hiçbir payment yüzeyine dokunmaz (FORBIDDEN §1);
  AI sonucu payment operation ÜRETEMEZ (ADR-003 §21). Payment modülü Deal'e
  dar port üzerinden bağlanır; FUNDED bilgisi Deal'e event/port ile yansır,
  repository paylaşımı yoktur (ADR-003 §23).
- **Para alanları:** amountMinor bigint + currency char(3); DB CHECK'leriyle
  negatif tutar engellenir. Query edilen para alanları JSONB'ye gömülmez
  (ADR-003 §27).

## 6. Frontend yönlendirmesi

- Deal detail'e "Funding" bölümü: plan tutarı, unit listesi, durum rozetleri,
  buyer için ödeme başlat butonu (yalnız backend projection'ı izin veriyorsa).
- Ödeme başlatma akışı local sandbox adapter'da yürür; frontend yalnız core API
  read projection'ını polling/refetch ile tazeler. Browser redirect'i veya
  provider callback'i funding state için authoritative değildir.
- UNCONFIRMED durumu "başarısız" DEĞİL "doğrulanıyor" dilinde gösterilir;
  reconciliation action'ı kullanıcıya sunulur.
- Tutar gösterimi minor-unit → ondalık dönüşümüyle; giriş alanı yoktur (tutar
  package'tan gelir).
- Seller/participant salt-okunur funding görünümü alır.
- Çift tıklama koruması yalnız UI'da değildir; aynı Idempotency-Key retry
  deseni korunur (ADR-006 §25).

## 7. Kabul testi (tarayıcı akışı)

Üç browser context (buyer ADMIN + buyer MEMBER + seller tarafı), local sandbox
provider:

1. ACTIVE Deal'de funding planı görünür (tutar ratified package ile eşleşir);
   DRAFT/CANCELLED Deal'de funding yüzeyi kapalıdır. Buyer ADMIN explicit create
   yapar; çift create/replay tek plan ve tek unit üretir.
2. Buyer ödeme başlatır → PENDING → sandbox başarı → FUNDED; her iki browser
   da durumu görür; lifecycle FUNDING→(fulfillment-öncesi) görünümüne ilerler.
3. Decline senaryosu → operation DECLINED, unit FAILED ve yeniden denenebilir;
   ikinci
   deneme başarıyla FUNDED olur; tek para hareketi kaydı oluşur.
4. Timeout senaryosu → UNCONFIRMED; yeni ödeme başlatma engellenir;
   reconciliation action'ı geç onayı çeker ve FUNDED'a taşır.
5. Ödeme başlat butonuna çift tıklama / aynı Idempotency-Key retry tek
   operation üretir; farklı request aynı key ile 409 alır.
6. Seller ödeme başlatamaz; butonu görmez, zorlanan istek reddedilir.
7. Buyer MEMBER ödeme başlatamaz; butonu görmez ve doğrudan istek 403 alır.
8. FUNDED unit'te yeni ödeme denemesi 409 üretir.
9. Ratification ve önceki slice akışları regresyonsuz.

## 8. Minimum invariant testleri

- Provider çağrısı sırasında DB transaction'ının açık olmadığı
- Aynı provider idempotency key ile retry'ın duplicate operation/para hareketi
  üretmediği; HTTP idempotency katmanının farklı-request 409'u
- Timeout'un FAILED üretmediği; CREATED/PENDING ve UNCONFIRMED'da yeni ödeme engeli
- Intent/outbox commit'inden provider çağrısı öncesine kadar crash recovery
- Provider çağrısından local sonuç commit'ine kadar crash recovery; aynı
  operation/key ile query-first çözüm ve initiate'in tekrarlanmaması
- FUNDED'a yalnız doğrulanmış provider sonucuyla geçilebildiği
- ACTIVE-olmayan Deal'de funding mutation reddi; buyer MEMBER/seller reddi
- Eşzamanlı/idempotent FundingPlan create'in DB unique ile tek plan + tek unit
  üretmesi; package amount/currency snapshot'ının immutable olması
- Tutarların integer persist edildiği; negatif tutar DB reddi
- Intent kaydı + sonuç uygulama transaction'larının audit ile atomikliği
- Initiate ve reconcile command'larının provider sonucunu beklemeden `202 +
  Location + operation projection` döndüğü; provider çağrısının request/TX
  dışında kaldığı

Provider adapter'ının kendisini test eden geniş matris kurulmaz; sandbox
senaryoları §7 akışıyla doğrulanır.

## 9. V1 bağlayıcı kararları

- ADR-010'daki tek FundingUnit, exact state machine, polling-first sonuç kanalı,
  durable dispatch ve query-first crash recovery uygulanır.
- FundingPlan otomatik değil explicit buyer ADMIN action'ıyla, Deal başına tek
  ve idempotent oluşturulur.
- Moka United araştırması yalnız Slice 11B provider/legal değerlendirmesine
  girdidir; Slice 11 provider port'unu veya sandbox davranışını Moka'ya bağlamaz.
- Currency ratified package'ın tek currency değeridir; currency conversion yoktur.
- PaymentOperation güvenli sonuç özeti alanları OpenAPI/port tasarımında
  contract-first sabitlenir. Raw provider payload, kart verisi, credential veya
  PII domain/audit/public API'ye taşınmaz (ADR-007 §33, ADR-010 §2.6).
- Slice 11 gerçek para kabul etmez ve M4Trust'ın fon tuttuğunu iddia etmez.

## 10. Done tanımı

- [x] ADR-010 state machine, crash recovery, polling-first ve yetki kararları
      eksiksiz uygulandı
- [x] OpenAPI funding yüzeyi implementasyondan önce tasarlandı
- [x] OpenAPI path/schema değişiklikleriyle birlikte contract validator exact
      beklentileri ve contracts README/CHANGELOG güncellendi; AI contract'ları
      değişmedi
- [x] Initiate/reconcile `202 + Location + CREATED/current operation` contract'ı
      ve optional Deal projection/action rollout'u validator ile kilitlendi
- [x] V1 tek FundingUnit sınırı korunuyor; PARTIALLY_FUNDED yüzeyde yok
- [x] Sandbox senaryo seçimi business tutarından bağımsız; production API'ye
      senaryo alanı/test-control yüzeyi sızmıyor; yalnız startup config sırası
      kullanılıyor ve production sandbox seçimiyle açılmıyor
- [x] FundingPlan/FundingUnit/PaymentOperation migration'ları para
      invariant'larını DB seviyesinde taşıyor
- [x] Provider port + sandbox adapter çalışıyor; provider çağrıları TX dışında
- [x] İki katmanlı idempotency (HTTP + provider key) testli
- [x] Timeout/UNCONFIRMED/reconciliation akışı çalışıyor; timeout hiçbir yerde
      otomatik failure değil
- [x] Crash pencereleri durable dispatch ve aynı-key query-first recovery ile
      testli; UNCONFIRMED'da yeni charge bloklu
- [x] FundingStatus + lifecycle projection doğru; frontend hesaplamıyor
- [x] `ModuleArchitectureTest` payment ownership'ini kapsıyor
- [x] §8 invariant testleri geçiyor; audit aynı transaction'da
- [x] §7 üç-context kabul akışı sandbox provider ile tamamlandı; önceki
      slice akışları regresyonsuz
- [x] Contract validator, backend verify ve frontend typecheck/build yeşil
