# Slice 08–11 Implementation Review Handoff

Bu doküman, kabul edilmiş Slice 8–11 implementasyonunu inceleyecek ajana hızlı
başlangıç sağlar. [00–03](00-03-implementation-review-handoff.md) ve
[04–07](04-07-implementation-review-handoff.md) handoff'larının devamıdır;
planların veya ADR'lerin yerine geçmez. Plan ile ADR çelişirse ADR kazanır;
ilgili karar bölümleri `architecture-decisions/ADR-INDEX.md` üzerinden
seçilmelidir.

## 1. İnceleme başlangıç noktası

- Slice 8 kabul tabanı: `main@84d09ed`.
- Slice 9–11 reviewed implementation: `codex/slice-9-11@8833d56`.
- Slice 8, 9, 10 ve 11 kabul edilmiştir. Bu kayıt kabul anındaki branch
  kimliklerini korur; güncel merge/state için `CURRENT.md` esas alınır.
- Kabul planları:
  - [`08-ai-document-extraction.md`](../08-ai-document-extraction.md)
  - [`09-manual-review-and-ruleset.md`](../09-manual-review-and-ruleset.md)
  - [`10-ratification.md`](../10-ratification.md)
  - [`11-funding-and-payment.md`](../11-funding-and-payment.md)
- Güncel kabul durumu: [`docs/plan/CURRENT.md`](../../CURRENT.md)
- Eski ayrı Slice 10–11 browser/invariant kabul kaydı bu handoff'un §5–§6 ve
  §10 bölümlerine birleştirilmiştir.
- Yetkili kararlar: ADR-001–ADR-010. Özellikle ADR-002, ADR-003,
  ADR-004, ADR-006, ADR-009 ve ADR-010 bu incelemenin ana mimari kaynaklarıdır.

## 2. Slice'lar arası oluşan omurga

Slice 8–11, Slice 6'nın immutable current-document temelinden başlayıp fonlama
sonucuna kadar aşağıdaki business ve teknik zinciri kurar:

1. Initiator, AVAILABLE current document için analiz talep eder.
2. Analysis job, audit, HTTP idempotency sonucu ve transactional outbox aynı
   PostgreSQL transaction'ında yazılır.
3. Outbox relay transaction dışında RabbitMQ'ya contract-valid requested event
   publish eder.
4. Local-only Mock AI Worker süreli object reference üzerinden dokümanı indirir,
   boyut ve SHA-256 doğrular ve completed/failed event üretir.
5. Spring consumer, inbox idempotency ve committed schema doğrulamasından sonra
   sonucu immutable ExtractionResultVersion olarak saklar; başarılı teknik sonuç
   yalnız `REVIEW_REQUIRED` olur.
6. Initiator extraction kurallarını düzeltir, hariç tutar veya manuel kural
   ekler; tek kabul action'ı immutable RuleSetVersion üretir, analizi `ACCEPTED`
   yapar ve Deal current-rule-set pointer'ını atomik günceller.
7. Initiator, current document + accepted rule-set + buyer/seller + exact
   commercial terms kopyalarından immutable ratification package oluşturur.
8. Buyer ve seller entity'lerinin ADMIN kullanıcıları aynı canonical snapshot
   hash'ini onaylar; ikinci gerekli entity onayı package'ı `RATIFIED`, Deal'i
   `ACTIVE` yapar.
9. Buyer ADMIN explicit funding plan oluşturur. Plan, ratified package id,
   amount ve currency provenance'ını immutable snapshot olarak taşır.
10. Payment operation durable dispatch ile oluşturulur; provider çağrısı HTTP
    request'i ve DB transaction'ı dışında yapılır.
11. Definitive success unit'i `FUNDED`, Deal lifecycle projection'ını
    `FULFILLMENT` yapar. Timeout `UNCONFIRMED` kalır ve aynı operation/provider
    key ile query-first reconciliation gerekir.

Modül bağımlılığı ve ownership yönü:

```text
document ──ports──> contractintelligence ──ports──> deal
                         │
                         └── transactional outbox/inbox ──> integration ──> RabbitMQ

deal/document/contractintelligence ──narrow ports──> ratification
deal/ratification ──narrow ports──> payment ──provider port──> integration adapter

frontend ──generated OpenAPI types──> Spring Core API
Mock AI Worker ──committed AsyncAPI/JSON Schema──> RabbitMQ
```

Repository veya persistence entity paylaşımı bu mimarinin parçası değildir.
`integration` teknik teslimat ve provider adapter'larını taşır; business state
kararı vermez. AI sonucu RuleSetVersion, ratification veya payment operation'ı
otomatik oluşturamaz.

## 3. Slice 8 — AI Document Extraction

### Gerçekleşen kullanıcı sonucu

- Initiator current document için explicit analiz talep edebilir.
- Kullanıcı `NOT_REQUESTED → QUEUED → PROCESSING →
  REVIEW_REQUIRED | FAILED` ilerleyişini Deal detayında görür.
- Başarılı extraction; parties, structured rules, advisory `legalBasis`,
  delivery requirements ve review summary alanlarıyla okunabilir.
- Participant sonucu okuyabilir fakat analiz talep edemez.
- Failure sonrasında initiator yeni job ile tekrar deneyebilir.
- Yeni current document önceki analiz zincirini `SUPERSEDED` yapar.
- Worker kapalıyken job kuyrukta kalır; worker tekrar başladığında gerçek
  RabbitMQ sınırından devam eder.

Completed event yalnız teknik başarıdır. Implementasyonun sonucu doğrudan
`ACCEPTED` yapmaması ve Deal business alanlarına uygulamaması kritik sınırdır.

### Public API

- `GET /api/v1/deals/{dealId}/document-analysis`
- `POST /api/v1/deals/{dealId}/document-analysis`

Request action'ı `Idempotency-Key` ister ve durable job/outbox commit'inden sonra
`202 Accepted` + `Location` döner. Read yüzeyi participant-scoped, mutation
initiator-scoped'dur.

### Persistence ve başlıca dosyalar

- Migration'lar:
  - `V12__integration_outbox_inbox_foundation.sql`
  - `V13__contract_intelligence_analysis_jobs.sql`
  - `V14__contract_intelligence_extraction_results.sql`
- Messaging:
  - `integration/messaging/JdbcTransactionalOutbox.java`
  - `integration/messaging/JdbcTransactionalInbox.java`
  - `integration/messaging/OutboxRelay.java`
  - `integration/messaging/AsyncApiTopology.java`
  - `integration/messaging/AiResultsListenerConfiguration.java`
- Contract intelligence:
  - `contractintelligence/AnalysisController.java`
  - `contractintelligence/AnalysisService.java`
  - `contractintelligence/AnalysisRepository.java`
  - `contractintelligence/AnalysisResultRabbitListener.java`
  - `contractintelligence/AnalysisResultConsumer.java`
  - `contractintelligence/CommittedEventSchemaValidator.java`
  - `contractintelligence/DocumentExtractionRequestedEventFactory.java`
- Document sınırı:
  - `document/DocumentAnalysisInputPort.java`
  - `document/DocumentAnalysisInputService.java`
  - `document/DocumentAnalysisSupersedePort.java`
- Mock worker:
  - `tools/mock-ai-worker/src/m4trust_mock_worker/`
  - `tools/mock-ai-worker/tests/`
  - `tools/mock-ai-worker/Dockerfile`
- Contract:
  - `contracts/asyncapi/m4trust-ai-v1.yaml`
  - `contracts/schemas/document-extraction/`
- Frontend:
  - `frontend/src/features/analysis/DealContractAnalysis.tsx`
  - `frontend/src/features/analysis/analysisApi.ts`
  - `frontend/src/features/analysis/analysisQueries.ts`

### Test ve kabul kanıtı

- `AnalysisRequestIntegrationTest`: job/outbox/audit/idempotency atomicity,
  authorization, active-job ve Deal-state kapıları.
- `AnalysisResultConsumerIntegrationTest`: inbox duplicate safety, contract
  validation, late/superseded result ve result persistence.
- `CommittedEventSchemaValidatorTest`: committed envelope/schema ve closed
  structured-value doğrulaması.
- `MessagingPersistenceIntegrationTest`: outbox/inbox persistence davranışı.
- `AsyncApiTopologyTest`: durable queue/exchange/binding adlarının AsyncAPI ile
  eşleşmesi.
- `AiResultsListenerConfigurationTest`: bounded retry ve exhausted-message
  davranışı.
- Mock AI Worker testleri 14/14 geçmiştir.
- Kabul anında backend 105/105, contract validator 21 schema/13 fixture ve
  frontend typecheck/build yeşildi.
- 19 Temmuz 2026 gerçek tarayıcı kabulünde RabbitMQ, MinIO ve Compose Mock AI
  Worker kullanılarak plan §7.1–§7.8 tamamlandı.

### Review odağı

- Job + audit + idempotency + outbox yazımının tek transaction olduğunu,
  RabbitMQ publish ve presigned-object erişiminin transaction dışında kaldığını
  doğrulayın.
- Inbox'un `eventId` ile at-least-once teslimatı duplicate-safe yaptığını;
  exactly-once varsayımı bulunmadığını kontrol edin.
- Consumer sırasının schema/envelope validation → job/input eşleşmesi →
  lifecycle kontrolü → result persistence olduğunu doğrulayın.
- Contract-invalid veya stale event'in business rejection/FAILED sonucuna
  çevrilmediğini kontrol edin.
- Mock worker'ın raw document, URL, credential veya payload loglamadığını ve
  production profile'da başlayamadığını doğrulayın.
- Frontend'in FastAPI/worker adresini bilmediğini, yalnız Core API projection'ı
  ve backend `canRequestAnalysis` action'ını kullandığını kontrol edin.

## 4. Slice 9 — Manual Review ve RuleSetVersion

### Gerçekleşen kullanıcı sonucu

- Initiator `REVIEW_REQUIRED` extraction kurallarını görüntüler.
- Existing rule için `KEPT`, `MODIFIED`, `EXCLUDED`; yeni kural için `ADDED`
  kararı verebilir.
- Money ve percentage değerleri wire/persistence katmanında integer minor unit
  ve basis point olarak kalır.
- Tek accept action'ı immutable RuleSetVersion üretir, analizi `ACCEPTED` yapar,
  Deal current-rule-set pointer'ını günceller ve audit yazar.
- Participant current rule-set ve history'yi okuyabilir, review mutation'ı
  yapamaz.
- Yeni current document eski analysis/rule-set zincirini supersede eder, current
  pointer'ı temizler; geçmiş okunur kalır.

Review kabulü initiator'ın DRAFT koordinasyonudur; buyer/seller ticari rızası
veya ratification değildir.

### Public API

- `GET /api/v1/deals/{dealId}/extraction-review`
- `POST /api/v1/deals/{dealId}/extraction-review/accept`
- `GET /api/v1/deals/{dealId}/rule-set-versions`
- `GET /api/v1/deals/{dealId}/rule-set-versions/{versionId}`

Accept action'ı target analysis, review kararları, Deal `expectedVersion` ve
`Idempotency-Key` taşır. Semantic validation 422, stale/state conflict 409,
visible-but-unauthorized actor 403 ve non-disclosing access 404 üretir.

### Persistence ve başlıca dosyalar

- Migration'lar:
  - `V15__contract_intelligence_rule_set_versions.sql`
  - `V16__rule_set_previous_same_deal_integrity.sql`
  - `V17__accepted_analysis_status.sql`
- Backend:
  - `contractintelligence/ReviewController.java`
  - `contractintelligence/ReviewService.java`
  - `contractintelligence/ReviewAcceptanceRequestDecoder.java`
  - `contractintelligence/RuleSetRepository.java`
  - `contractintelligence/ReviewDtos.java`
  - `contractintelligence/RatificationAcceptedRuleSetAdapter.java`
- Deal projection:
  - `deal/DealRuleSetProjectionPort.java`
  - `deal/DealService.java`
  - `deal/DealLifecycleProjectionCalculator.java`
- Frontend:
  - `frontend/src/features/review/DealReviewWorkspace.tsx`
  - `frontend/src/features/review/reviewApi.ts`
  - `frontend/src/features/review/reviewErrors.ts`

### Test ve kabul kanıtı

- `ReviewAcceptanceIntegrationTest`: tüm karar türleri, exact persisted
  RuleSetVersion, idempotency, authorization, rollback atomicity, immutable DB
  guards, same-Deal pointers ve concurrent accept.
- `ReviewAcceptanceRequestDecoderTest`: closed decision shapes, canonical
  fingerprint, float rejection ve typed structured values.
- `ReviewDecisionAssemblerTest`: final rule/provenance üretimi.
- `RatificationAcceptedRuleSetAdapterTest`: sonraki modüle dar, kopyalanmış
  accepted-rule projection'ı.
- `RuleSetVersion` predecessor same-Deal bütünlüğü ve document-finalize
  supersession davranışı integration testleriyle kapatıldı.
- 19 Temmuz 2026 gerçek iki-session kabulünde düzeltme, hariç tutma, manuel
  kural, 422 field recovery, immutable history, document supersession ve
  eşzamanlı accept yarışı geçti.
- Slice 10–11 sonundaki tam `mvn verify` bu akışı regresyon olarak da kapsar.

### Review odağı

- RuleSetVersion tablosunun trigger ile insert-only olduğunu; update/delete
  application yolunun bulunmadığını doğrulayın.
- Deal current-rule-set composite FK'sinin yalnız aynı Deal'e ait versiyona
  işaret edebildiğini ve predecessor zincirinin de same-Deal olduğunu kontrol
  edin.
- Accept transaction'ında RuleSetVersion + AnalysisStatus + Deal pointer +
  audit + idempotency sonucunun birlikte commit/rollback olduğunu doğrulayın.
- Document finalize ve review accept'in aynı Deal → current analysis lock
  sırasını kullandığını; iki current veya eski document + yeni rules görünümü
  üretemediğini kontrol edin.
- AI completed event'in tek başına RuleSetVersion/current pointer üretmediğini
  doğrulayın.
- `legalBasis` alanının advisory provenance olarak kaldığını ve business
  kararında kullanılmadığını kontrol edin.
- Frontend action'ının `availableActions.canReviewExtraction` üzerinden
  açıldığını, status'tan bağımsız yetki tahmini yapılmadığını doğrulayın.

## 5. Slice 10 — Ratification ve Deal Activation

### Gerçekleşen kullanıcı sonucu

- Initiator, buyer/seller ve accepted rule-set hazır olduğunda exact
  `amountMinor` + `currency` ile package oluşturur.
- MONEY kuralları yalnız öneri olarak gösterilir; sıfır/bir/çok öneriden otomatik
  ticari değer seçilmez.
- Buyer ve seller aynı immutable snapshot ve aynı `contentHash` değerini görür.
- Buyer/seller entity ADMIN'leri entity adına birer etkili approval verir.
- İkinci farklı entity onayıyla package `RATIFIED`, Deal `ACTIVE` olur.
- Reject yeni package gerektirir; parties/document/rule-set veya exact terms
  değişikliği pending package'ı `SUPERSEDED` yapar.
- ACTIVE sonrasında DRAFT mutation yüzeyleri server ve UI seviyesinde kapanır.

### Public API

- `POST /api/v1/deals/{dealId}/ratification-packages`
- `GET /api/v1/deals/{dealId}/ratification-packages`
- `GET /api/v1/deals/{dealId}/ratification-packages/{ratificationPackageId}`
- `POST /api/v1/deals/{dealId}/ratification-packages/{ratificationPackageId}/approve`
- `POST /api/v1/deals/{dealId}/ratification-packages/{ratificationPackageId}/reject`

Create exact commercial terms + Deal version; approve/reject target package
version taşır. Riskli mutation'lar `Idempotency-Key` ile korunur. Dedicated
immutable `RatificationPackageSnapshot`, mutable/actor-aware wrapper'dan
contract seviyesinde ayrıdır.

### Persistence ve başlıca dosyalar

- Migration: `V18__ratification_package_foundation.sql`
- Backend:
  - `ratification/RatificationController.java`
  - `ratification/RatificationPackage.java`
  - `ratification/RatificationRepository.java`
  - `ratification/RatificationSnapshotAssembler.java`
  - `ratification/CanonicalSnapshotHasher.java`
  - `ratification/RatificationPackageCreateService.java`
  - `ratification/RatificationPackageActionService.java`
  - `ratification/RatificationPackageReadService.java`
  - `ratification/RatificationSupersessionService.java`
  - `ratification/RatificationSourcePorts.java`
  - `ratification/RatificationPackageProjectionPort.java`
  - `ratification/RatificationSupersessionPort.java`
- Frontend:
  - `frontend/src/features/ratification/DealRatificationPanel.tsx`
  - `frontend/src/features/ratification/ratificationApi.ts`
  - `frontend/src/features/ratification/ratificationQueries.ts`

### Test ve kabul kanıtı

- `CanonicalSnapshotHasherTest`: RFC 8785 serialization ve stable lowercase
  SHA-256.
- `RatificationSnapshotAssemblerTest`: exact snapshot field set, defensive copy,
  UTF-8 rule ordering, duplicate reference ve source consistency.
- `RatificationMigrationIntegrationTest`: immutable snapshot/approval, party ve
  current-pointer DB invariant'ları.
- `RatificationPackageCreateServiceTest`: readiness, exact commercial terms,
  idempotency ve supersede davranışı.
- `RatificationPackageActionServiceTest`: Deal→package lock sırası,
  entity-scoped approval, actor role, stale/terminal state, atomic activation
  ve action fingerprint.
- `RatificationIntegrationTest`: HTTP contract, two-party happy path,
  authorization, parties supersede ve withdrawal↔approval yarışının iki sırası.
- 20 Temmuz 2026 gerçek tarayıcı kabulünde buyer ADMIN, ikinci buyer ADMIN,
  buyer MEMBER ve seller ADMIN ile plan §7'nin 14 adımlı akışı tamamlandı.
- Kaydedilen browser matrisi; MONEY suggestions varken alanların boş kalmasını,
  exact snapshot/hash görünürlüğünü, two-party activation'ı, ACTIVE mutation
  kapanışını, reject→new package ve party-change supersession'ı, MEMBER/second
  ADMIN/double-click davranışını, yeni terms için yeni hash+boş approvals'ı ve
  withdrawal/approval ile approve/reject/party-supersede terminal yarışlarını
  kapsadı. Yarışlarda yalnız tek terminal sonuç kaldı, kaybeden 409 aldı.

### Review odağı

- Hash girdisinin yalnız dedicated immutable snapshot olduğunu; package
  id/version/status, approvals, actor visibility, action ve audit metadata'sının
  hash dışında kaldığını doğrulayın.
- Snapshot'ın JCS/RFC 8785 + UTF-8 + SHA-256 lowercase hex kullandığını; integer
  alanların I-JSON safe aralıkta olduğunu kontrol edin.
- Snapshot, approvals ve package history DB guard'larının update/delete'i
  engellediğini; mutable wrapper'da yalnız status/version değişebildiğini
  doğrulayın.
- Approval unique invariant'ının package + legal entity olduğunu; aynı entity
  içindeki iki ADMIN'in iki taraf sayılmadığını kontrol edin.
- İkinci required approval'ın package RATIFIED + Deal ACTIVE + audit'i tek
  transaction'da yaptığını doğrulayın.
- Create/approve/reject/supersede/withdrawal akışlarının Deal → current package
  lock sırasını koruduğunu kontrol edin.
- ACTIVE Deal'de basic patch, withdrawal, party, document ve review mutation
  kapılarının yalnız UI'da değil backend'de de kapalı olduğunu doğrulayın.

## 6. Slice 11 — Funding Foundation

### Gerçekleşen kullanıcı sonucu

- Buyer entity ADMIN'i ACTIVE Deal için explicit, idempotent funding plan
  oluşturabilir.
- V1'de Deal başına tek FundingPlan ve plan başına sequence 1 olan tek
  FundingUnit vardır.
- Plan amount/currency ve `ratificationPackageId` değerini RATIFIED package'tan
  server-side immutable snapshot olarak alır; request serbest tutar veya sandbox
  scenario taşımaz.
- Payment initiate hemen durable operation/dispatch oluşturup `202 Accepted`
  döner; provider sonucu polling ile görünür.
- Definitive decline unit'i `FAILED` yapar ve yeni operation ile retry'a izin
  verir.
- Timeout operation'ı `UNCONFIRMED` bırakır, yeni charge'ı engeller ve aynı
  provider key ile reconciliation gerektirir.
- Definitive success unit'i `FUNDED`; ACTIVE Deal lifecycle projection'ını
  `FULFILLMENT` yapar.
- Seller, buyer MEMBER ve diğer participant'lar salt-okunurdur.

### Public API

- `POST /api/v1/deals/{dealId}/funding-plan`
- `GET /api/v1/deals/{dealId}/funding-plan`
- `POST /api/v1/funding-units/{fundingUnitId}/payment-operations`
- `GET /api/v1/payment-operations/{paymentOperationId}`
- `POST /api/v1/payment-operations/{paymentOperationId}/reconcile`

Plan create `201 Created`; initiate/reconcile durable command commit'inden sonra
provider sonucunu beklemeden `202 Accepted` + operation `Location` döner.
Reconcile yalnız `UNCONFIRMED` operation için açıktır; `CREATED` ve terminal
operation'lar conflict üretir.

### Persistence ve başlıca dosyalar

- Migration: `V19__payment_funding_foundation.sql`
- Payment domain/application:
  - `payment/FundingPlan.java`
  - `payment/FundingUnit.java`
  - `payment/PaymentOperation.java`
  - `payment/FundingController.java`
  - `payment/FundingPlanCreateService.java`
  - `payment/PaymentOperationInitiateService.java`
  - `payment/PaymentOperationReconcileService.java`
  - `payment/FundingRepository.java`
  - `payment/PaymentDispatchStore.java`
  - `payment/PaymentDispatchRelay.java`
  - `payment/PaymentProviderPort.java`
  - `payment/FundingProjectionPort.java`
  - `payment/FundingSourcePorts.java`
- Sandbox integration:
  - `integration/payment/SandboxPaymentProviderAdapter.java`
  - `integration/payment/SandboxPaymentConfiguration.java`
  - `integration/payment/SandboxPaymentProviderBootstrapGuard.java`
  - `integration/payment/SandboxPaymentProviderProperties.java`
- Deal/lifecycle:
  - `deal/DealLifecycleProjectionCalculator.java`
  - `deal/DealService.java`
- Frontend:
  - `frontend/src/features/funding/DealFundingPanel.tsx`
  - `frontend/src/features/funding/fundingApi.ts`
  - `frontend/src/features/funding/fundingQueries.ts`

### Test ve kabul kanıtı

- `FundingUnitTest`: exact unit state machine, retry ve optimistic version.
- `PaymentOperationTest`: CREATED/UNCONFIRMED/terminal geçişleri ve immutable
  provider key.
- `PaymentFundingIntegrationTest`: plan provenance, tek plan/unit,
  SUCCESS/DECLINE/TIMEOUT_THEN_SUCCESS, actor/state gates, HTTP/provider
  idempotency, UNCONFIRMED-only reconcile, DB money invariant'ı, provider
  transaction sınırı ve iki crash penceresi.
- `SandboxPaymentProviderBootstrapGuardTest`: sandbox'ın production-like
  profile ile başlamaması.
- `DealStatusTest`: ACTIVE funding projection'ının FUNDING/FULFILLMENT ayrımı.
- 20 Temmuz 2026 gerçek browser kabulünde explicit plan double-click, SUCCESS,
  DECLINE→retry→SUCCESS, TIMEOUT→UNCONFIRMED→reconcile, seller/MEMBER
  visibility ve terminal operation kapıları tamamlandı.
- Aynı kabulde same-key replay tek operation bıraktı; aynı key/farklı payload
  409 üretti. FUNDED unit yeni payment'ı, seller ve buyer MEMBER mutation'ı
  açmadı. Buyer ve seller aynı terminal projection'ı gördü.

### Review odağı

- `funding_plan.ratification_package_id` composite FK'sinin aynı Deal'in
  package'ına bağlandığını; package id/amount/currency alanlarının immutable
  olduğunu doğrulayın.
- V1 tek plan/tek unit sınırının DB unique/check invariant'larıyla korunduğunu ve
  `PARTIALLY_FUNDED` durumunun public/state yüzeyine sızmadığını kontrol edin.
- Payment intent + fixed provider key + durable dispatch + audit + HTTP
  idempotency sonucunun provider çağrısından önce commit edildiğini doğrulayın.
- Provider `initiate/queryStatus` çağrılarının request ve DB transaction dışında
  olduğunu kontrol edin.
- Relay'in önce aynı provider key ile query yaptığını, yalnız açık `NOT_FOUND`
  durumunda aynı key ile initiate ettiğini doğrulayın.
- Timeout'un `FAILED` üretmediğini, UNCONFIRMED varken yeni provider key/charge
  açılamadığını ve reconcile'ın yalnız aynı operation/key üzerinden çalıştığını
  kontrol edin.
- Sandbox scenario seçiminin yalnız `local-sandbox` startup config'inde
  bulunduğunu; public request/header/body veya business amount üzerinden
  seçilemediğini doğrulayın.
- FastAPI/AI tarafının payment modülüne hiçbir çağrı veya state etkisi
  taşımadığını kontrol edin.

## 7. Contract, persistence ve frontend çapraz kontrol tablosu

| Slice | Public/async contract | Migration | Backend ownership | Frontend yüzeyi |
|---|---|---|---|---|
| 8 | analysis OpenAPI + document-extraction AsyncAPI/JSON Schema | V12–V14 | `contractintelligence`, `integration/messaging` | Contract analysis panel |
| 9 | review + rule-set history OpenAPI | V15–V17 | `contractintelligence`, narrow Deal ports | Review workspace + history |
| 10 | ratification package/action OpenAPI | V18 | `ratification`, narrow source/projection/supersession ports | Ratification panel |
| 11 | funding/payment/reconcile OpenAPI | V19 | `payment`, `integration/payment` adapter | Funding/payment panel |

`contracts/openapi/core-api-v1.yaml` public Core API source of truth'tür.
`frontend/src/generated/core-api.d.ts` bu contract'tan üretilir. Feature
klasörlerinde elle tahmin edilmiş paralel wire model, kapalı schema'ya fazladan
alan veya status/action'dan türetilmiş frontend yetkisi review bulgusudur.

Slice 8'in AI event contract'ı `contracts/asyncapi/m4trust-ai-v1.yaml` ve
`contracts/schemas/document-extraction/` altındaki JSON Schema'lardır. Slice
9–11 bu AI contract'larını değiştirmez.

## 8. Mimari çapraz review odağı

### Ownership ve port sınırları

- `ModuleArchitectureTest` top-level modül cycle'larını ve repository
  ownership'ini kontrol eder.
- `contractintelligence`, `ratification` ve `payment` birbirlerinin
  repository/JDBC record'larını kullanmamalıdır.
- Cross-module işbirliği stable ID, projection ve dar mutation/supersession
  port'ları üzerinden yürümelidir.
- `integration` business state machine veya authorization kararı
  vermemelidir.

### Transaction, external call ve lock sırası

- Audit, idempotency sonucu ve gerekiyorsa outbox/dispatch, business mutation'la
  aynı transaction'da olmalıdır.
- Broker, object storage ve payment provider çağrısı sırasında DB transaction
  açık kalmamalıdır.
- Review/document yarışı Deal → analysis; ratification yarışları Deal →
  current package sırasını korumalıdır.
- Sessiz last-write-wins veya conflict'i başarı gibi döndürme kabul edilmez.

### Authorization ve non-disclosure

- Deal visibility participant ilişkisidir; tenant eşleşmesi değildir.
- Analysis/review/package create initiator operation'larıdır.
- Ratification approve/reject ve funding mutation buyer/seller rolüne ek olarak
  entity-scoped `ADMIN` gerektirir.
- Read visibility mutation yetkisi sağlamaz.
- Frontend buton gizleme tek koruma değildir; application katmanı aynı policy'yi
  zorunlu uygular.

### Lifecycle ve actor-aware projection

- Lifecycle hesabı backend'de `DealLifecycleProjectionCalculator` üzerinden
  merkezidir:
  - analysis active/failed/superseded → `CONTRACT_ANALYSIS`
  - review required → `MANUAL_REVIEW`
  - accepted rule-set → `RATIFICATION`
  - ACTIVE ve henüz funded değil → `FUNDING`
  - ACTIVE + FUNDED → `FULFILLMENT`
- Frontend status'lardan yeni lifecycle veya authorization matrisi
  üretmemelidir.
- Eksik/bilinmeyen optional action güvenli şekilde `false`/read-only olmalıdır.

### Immutability ve provenance

- Extraction result, RuleSetVersion, ratification snapshot/approval ve funding
  plan provenance zinciri geçmişi silmeden ilerler.
- Current pointer'lar same-Deal composite FK ile korunur.
- Applied migration'lar forward-only history'dir; özellikle `CURRENT.md`
  tarafından freeze edilen V15–V19 değiştirilmemeli, yeni ihtiyaç yeni migration
  kullanmalıdır.
- Ratified package mutation ile güncellenmez; yeni terms/content yeni package
  ve yeni approval seti gerektirir.

## 9. Önerilen review sırası

1. Dört done planının kapsam/kapsam dışı ve Done checklist'lerini okuyun.
2. ADR-002'nin event semantiği/idempotency/compatibility, ADR-003'ün lifecycle,
   module/transaction/concurrency ve ADR-010'un ratification/funding kararlarını
   açın.
3. OpenAPI, AsyncAPI, JSON Schema, validator allowlist'leri ve generated
   frontend tiplerini karşılaştırın.
4. V12–V19 migration zincirini FK, unique/check, immutable trigger ve
   current-pointer bütünlüğü açısından inceleyin.
5. Analysis request'ten outbox relay ve Mock AI Worker'a; oradan inbox/consumer
   ve `REVIEW_REQUIRED` sonucuna kadar gerçek async yolu takip edin.
6. Review accept transaction'ını document finalize supersession yoluyla birlikte
   inceleyin.
7. Ratification snapshot assembler/hash sınırını package mutable wrapper ve
   approval projection'ından ayırarak kontrol edin.
8. Approve/reject/supersede/withdrawal lock sıralarını ve concurrency testlerini
   uygulamayla karşılaştırın.
9. Funding planın ratified package provenance'ını ve buyer ADMIN policy'sini
   takip edin.
10. Payment initiate → durable dispatch → query-first provider adapter → result
    application ve UNCONFIRMED reconcile akışını iki crash penceresiyle
    karşılaştırın.
11. Deal detail'in analysis, review, ratification ve funding panellerinde yalnız
    backend projection/action kullandığını doğrulayın.
12. Son olarak contract validator, Core API verify, Mock AI Worker testleri ve
    frontend typecheck/build çalıştırın.

## 10. Review için doğrulama komutları

Repository kökünden:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm ci
npm run typecheck
npm run build

Set-Location ..
$env:PYTHONPATH='tools/mock-ai-worker/src'
python -m pytest .\tools\mock-ai-worker\tests

docker compose -f .\infra\compose.yaml config
```

Contract ve worker Python bağımlılıkları temiz ortamda önceden kurulu değilse
sırasıyla `contracts/requirements.txt` ve
`tools/mock-ai-worker/requirements-dev.txt` kullanılmalıdır.

Son kabul durumunda:

- Contract validator: 21 schema, 13 valid fixture ve tüm expected-invalid
  kontrolleri geçti.
- Core API `mvn verify`: 230 test, 0 failure, 0 error, 0 skipped.
- Mock AI Worker: 14/14.
- Frontend typecheck ve production build geçti.
- `ModuleArchitectureTest` cycle ve repository ownership kontrollerini geçti.
- Slice 8–11 için gerçek broker/storage/browser ve çok-actor kabul turları
  tamamlandı.

Manuel kabul akışları ilgili plan ve kabul kaydında ayrıntılıdır. Reviewer,
implementation değişmedikçe bütün browser yarış matrisini baştan kurmak yerine
otomatik invariant testleri ve kaydedilmiş kabul kanıtını esas alabilir.

## 11. Açık takipler ve kapsam sınırı

- Slice 7 Railway staging ayrı kabul edilmiştir; kanıt
  [`04–07 handoff`](04-07-implementation-review-handoff.md) içindedir ve bu
  review'un implementation kapsamına dahil değildir.
- Gerçek FastAPI AI service skeleton'ı stabil/kabul edilmiş değildir. Slice 8'in
  kabul edilen runtime karşılığı local-only Mock AI Worker'dır.
- Gerçek payment provider yolu daha sonra simulation-only founder kararıyla
  kapatılmıştır. Slice 11B-A'nın local/CI HTTP boundary kanıtı ve bu scope
  değişikliği [`14A–15 P4 handoff`](14a-15p4-implementation-review-handoff.md)
  içinde kaydedilmiştir.
- Release, payout, refund, settlement, dispute etkisi ve gerçek para hareketi
  Slice 11 kapsamında yoktur. ADR-010 §2.7 çözülmeden otomatik
  approve-then-refund veya benzeri workaround kurulamaz.
- ACTIVE Deal için unilateral cancel hâlâ kapalıdır; mutual consent veya
  casework/dispute kararı ayrıca planlanmalıdır.
- V1 frontend money formatter bütün currency'lerde iki ondalık varsayar.
- Local sandbox provider state'i process-memory içindedir; production tasarımı
  değildir.
- Funding read response'ları için daha geniş closed-shape assertion ucuz bir
  test-hardening adayıdır; mevcut kabulü geri almaz.
- Deal `COMPLETE` mutation'ı açılacak slice'ta payment initiate/reconcile Deal
  status okuması Deal lock'u altında yeniden değerlendirilmelidir.

Bu açık takipler Slice 8–11'in mevcut kabul durumunu geri almaz. Reviewer bunları
yanlışlıkla eksik Slice implementasyonu olarak sınıflandırmamalı; sonraki
kararların tarihsel Slice 8–11 sınırlarını sessizce yeniden yazmasına da izin
vermemelidir.
