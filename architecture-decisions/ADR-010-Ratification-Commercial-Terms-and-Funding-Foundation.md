# ADR-010: Ratification Commercial Terms and Funding Foundation

- Durum: Accepted
- Tarih: 19 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Ratification package commercial terms, provider-bağımsız funding
  foundation, payment state/recovery/idempotency sınırları
- Bağlı kararlar:
  - ADR-003: Core Domain Model and Deal Lifecycle
  - ADR-004: Vertical Slice Delivery and Acceptance Testing
  - ADR-006: Public API and Error Conventions
  - ADR-009: Deal Commitment and Cancellation Consent

## 1. Bağlam

ADR-003 §20 ratification package'a funding açısından gerekli contractual
bilgileri dahil eder; ancak ilk modelde bu bilgilerin exact alanları tanımlı
değildir. Slice 11 funding tutarını serbest metinden veya AI sonucundan
türetemez: tarafların aynı structured commercial terms değerini ratify etmiş
olması gerekir.

ADR-003 §21 payment intent'i, provider idempotency'sini, unknown outcome
reconciliation'ını ve external çağrı sırasında transaction açmama kuralını
tanımlar; exact state machine ve crash recovery politikasını ayrı karara
bırakır. Bu karar V1 RatificationPackage ve provider-bağımsız Slice 11
foundation için o boşlukları kapatır. Gerçek provider entegrasyonu Slice 11B
olarak ayrıdır.

## 2. Karar

### 2.1 RatificationPackage V1 commercial terms

V1 package aşağıdaki structured commercial terms alanlarını canonical snapshot
içinde taşır:

- `amountMinor`: `1..9007199254740991` aralığında pozitif integer minor unit
- `currency`: açık ISO 4217 currency

Kabul edilmiş RuleSetVersion içindeki uygun MONEY kuralları yalnız suggestion
kaynağıdır. Hiçbir MONEY kuralı yoksa veya birden fazla aday varsa sistem
sessizce seçim yapmaz. Initiator package-create request'inde exact
`amountMinor` ve `currency` değerini açıkça gönderip teyit eder. Bu değerler
semantic validation'dan geçer ve package içeriği/hash'inin parçasıdır.

Package oluşturulduktan sonra commercial terms değişmez. DRAFT aşamasında
farklı bir değer gerekiyorsa mevcut PENDING package SUPERSEDED edilir ve
yeni package oluşturulup yeniden ratify edilir. RATIFIED/ACTIVE sonrasında
commercial terms mutation'ı yoktur.

Package `contentHash` girdisi yalnız dedicated, closed ve immutable
`RatificationPackageSnapshot` JSON'ıdır. Package id/version/status,
`contentHash`, approvals, approver/actor-specific görünürlük, available actions,
audit/timestamp ve detail-wrapper metadata'sı snapshot'a veya hash girdisine
dahil değildir. Bütün integer alanlar I-JSON safe integer aralığındadır. JSON
RFC 8785 (JCS) ile canonical edilir; UTF-8 byte dizisinin SHA-256 özeti lowercase
64-char hex olarak kullanılır. UUID/currency casing'i deterministiktir; V1
`rules` dizisi unique `ruleReference` değerinin UTF-8 bytewise ascending
sırasındadır. Gelecekte snapshot'a eklenen her array'in ordering kuralı contract
ile sabitlenir. Aynı snapshot bütün runtime'larda aynı hash'i üretir.

Tracking policy ile funding/fulfillment operasyon kayıtları V1 package
kapsamında değildir. Gelecekte yeni contractual alan gerekiyorsa yeni package
schema version ve gerektiğinde yeniden ratification tasarlanır.

### 2.2 Slice 11 V1 kapsamı

Slice 11 provider-bağımsız sandbox funding foundation'dır:

- Deal yalnız ACTIVE olduktan sonra buyer ADMIN'in explicit, idempotent action'ı
  ile funding plan oluşturulabilir; activation otomatik plan oluşturmaz.
- Plan tutarı ve currency yalnız RATIFIED package commercial terms'inden gelir.
- V1'de Deal başına tam olarak bir FundingPlan ve onun içinde tam olarak bir
  FundingUnit vardır; DB unique kısıtları bunu yarış altında da korur ve
  `PARTIALLY_FUNDED` erişilemez.
- Funding plan/payment mutation'ı yalnız buyer legal entity'nin `ADMIN`
  kullanıcılarına açıktır. Buyer MEMBER, seller ve diğer participant'lar
  salt-okunurdur.
- Sandbox gerçek para hareketi veya M4Trust'ın para tuttuğu iddiası üretmez;
  provider tarafından tutulduğu simüle edilen fonu modeller.
- Gerçek provider adapter'ı, credential, 3D redirect/callback ve staging kabulü
  Slice 11B kapsamıdır.

### 2.3 Ayrı state eksenleri

V1 FundingUnit durumları:

- `PLANNED`
- `PENDING`
- `FUNDED`
- `FAILED`

İzinli geçişler:

- `PLANNED -> PENDING`: ilk PaymentOperation intent'i oluşturuldu.
- `PENDING -> FUNDED`: provider sonucu status query/adapter tarafından
  doğrulanmış `SUCCEEDED`.
- `PENDING -> FAILED`: yalnız kesin `DECLINED` sonucu.
- `FAILED -> PENDING`: kullanıcı yeni bir PaymentOperation başlattı.

V1 PaymentOperation durumları:

- `CREATED`: durable intent yazıldı, external sonuç henüz uygulanmadı.
- `SUCCEEDED`: provider başarı sonucu doğrulandı; terminal.
- `DECLINED`: provider kesin ret sonucu doğrulandı; terminal.
- `UNCONFIRMED`: çağrı timeout, process crash veya belirsiz cevap nedeniyle dış
  sonuç bilinmiyor; reconciliation gerekir.

`CREATED -> SUCCEEDED | DECLINED | UNCONFIRMED` ve
`UNCONFIRMED -> SUCCEEDED | DECLINED` geçişleri dışında serbest status set
yoktur. UNCONFIRMED bir operation varken aynı unit için yeni payment operation
başlatılamaz. FundingUnit PENDING iken CREATED veya UNCONFIRMED operation tek
in-flight operation'dır; farklı bir HTTP key ile yeni operation 409 alır.
FundingStatus tek unit'in business projection'ıdır; frontend ayrı
status alanlarından kendi lifecycle'ını hesaplamaz.

### 2.4 Durable dispatch ve crash recovery

Payment başlatma transaction'ı aşağıdakileri atomik yazar:

- PaymentOperation `CREATED` intent'i,
- operation'a ömür boyu sabit provider request key,
- durable dispatch/outbox kaydı,
- audit ve HTTP idempotency sonucu.

Relay/scheduler işi claim eder, database transaction'ını kapatır ve provider
port'unu transaction DIŞINDA çağırır. Sonuç ayrı transaction'da operation,
FundingUnit, FundingStatus projection ve audit'e uygulanır.

Public initiate command'ı durable intent/dispatch commit'inden sonra provider
sonucunu beklemeden `202 Accepted`, operation `Location` ve `CREATED` operation
projection'ı döner. Reconciliation command'ı da provider query'yi HTTP request
içinde çalıştırmaz: durable reconciliation dispatch/audit/idempotency kaydını
commit eder, `202` ve aynı operation `Location`'ını döner; relay `queryStatus`
çağrısını transaction dışında yapıp sonucu ayrı transaction'da uygular.

Recovery kuralları:

- Intent yazılıp çağrıdan önce process ölürse dispatch önce aynı operation/key
  ile status query yapar; kesin sonuç varsa uygular, provider açıkça `NOT_FOUND`
  döndürürse aynı key ile initiate eder, sonuç bilinmiyorsa UNCONFIRMED bırakır.
- Provider çağrısından sonra sonuç kaydedilmeden process ölürse yeni charge
  açılmaz; aynı key ile status query yapılır.
- Query kesin sonuç vermezse operation UNCONFIRMED kalır ve yeni charge
  engellenir.
- Definitive DECLINED sonrasında kullanıcı retry'sı yeni PaymentOperation ve
  yeni provider key üretir.
- Aynı HTTP Idempotency-Key retry'sı aynı operation/provider key sonucunu döner.

Provider'ın create idempotency veya status-query consistency garantisi
kanıtlanmadan gerçek charge yeni bir provider key ile otomatik retry edilmez.

### 2.5 Result channel

Slice 11 polling-first'tür:

- Core yalnız `initiate` ve `queryStatus` provider portlarını bilir.
- Sandbox adapter synchronous sonuç veya query ile çözülen late-result üretir.
- Frontend Core API read projection'ını polling/refetch ile yeniler.
- Slice 11'de public provider callback endpoint'i yoktur.

Slice 11B'deki 3D browser redirect yalnız UX sinyali olabilir; tek başına
payment state transition üretemez. Provider sonucu status query veya doğrulanmış
provider-to-server mekanizmasıyla teyit edilmeden FUNDED oluşmaz.

### 2.6 Provider port ve veri güvenliği

Provider port domain-owned request/outcome tipleri kullanır. Raw provider
payload'ı, kart verisi, credential veya provider hata metni domain/audit/public
API'ye taşınmaz. Secret'lar yalnız adapter runtime environment'ında bulunur.
Internal para alanları integer minor unit olarak kalır; provider major-unit
dönüşümü yalnız gerçek adapter içinde yapılır.

Sandbox scenario seçimi yalnız `local-sandbox` profile startup config'inde
verilen deterministik sonuç sırasıdır. Runtime test-control endpoint/header/body
alanı yoktur. Business amount, currency veya production API alanı senaryo
seçmek için kullanılmaz. Sandbox adapter production profile'da açılamaz.

### 2.7 Release, refund ve hukuki sınır

Slice 11 yalnız funding entry foundation'dır. Pool release, payout, refund,
reversal ve settlement mutation'ı içermez. Bilinmeyen bir provider davranışını
çözmek için otomatik pool approval veya approve-then-refund uygulanmaz; sistem
fail-closed kalır.

Moka United Slice 11B için aday ve araştırma girdisidir; bu ADR gerçek provider
seçimi veya hukuki uygunluk kararı değildir. Platform-held/manual payout modeli
hukuk görüşü olmadan uygulanmaz.

## 3. Sonuçlar

- Slice 10'un iş yüküne structured commercial terms confirmation ve package
  hash/immutability testleri eklenir.
- Slice 11 gerçek provider bilinmeden uçtan uca geliştirilebilir; adapter portu
  Moka araştırmasıyla uyumluluk açısından review edilir.
- Unknown payment outcome kullanıcıya failure olarak sunulmaz ve duplicate
  charge riski pahasına otomatik retry yapılmaz.
- Gerçek provider entegrasyonu, probe kanıtı, provider security/callback ve
  hukuki/operasyonel kabul ayrı Slice 11B kapısıdır.

## 4. Kabul kapıları

- Commercial terms iki tarafın gördüğü canonical package/hash içinde testli.
- Mutable/actor-specific package detail alanları dedicated snapshot/hash dışında.
- State transition matrisi ve yasak geçişler testli.
- Provider çağrısı sırasında DB transaction'ı açık değil.
- Crash-before-call ve crash-after-provider-before-result senaryoları duplicate
  operation/charge üretmiyor.
- UNCONFIRMED yeni ödeme başlatmayı engelliyor; reconciliation kesin sonucu
  aynı operation'a uyguluyor.
- Buyer MEMBER/seller mutation reddi hem projection hem server enforcement ile
  kanıtlanıyor.
- Sandbox scenario production contract veya business amount'a sızmıyor.
- Eşzamanlı/idempotent funding-plan create tek plan ve tek unit üretiyor.
- Initiate/reconcile public command'ları `202 + Location` dönerken external
  provider çağrısı request transaction'ı dışında kalıyor.
