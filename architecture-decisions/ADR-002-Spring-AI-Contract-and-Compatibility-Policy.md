# ADR-002: Spring–AI Contract and Compatibility Policy

- Durum: Accepted
- Tarih: 13 Temmuz 2026
- Bağlı karar: ADR-001 System Boundaries and Data Ownership
- Kapsam: Spring Boot Core Platform ile FastAPI AI Service arasındaki bütün iletişim
- Contract sahipleri: Spring geliştiricisi, FastAPI geliştiricisi ve sistem mimarisi sorumlusu

## 1. Amaç
Bu ADR, Spring Boot Core Platform ile FastAPI AI Service arasındaki entegrasyon yüzeyini tanımlar.

Temel hedefler:

- FastAPI içinde kullanılan modelin bağımsız değiştirilebilmesi
- Prompt, provider, RAG, OCR ve pipeline implementasyonunun Spring’i etkilememesi
- Spring business davranışının model detaylarından bağımsız kalması
- Spring ve FastAPI’nin ayrı geliştirilebilmesi ve deploy edilebilmesi
- Contract değişikliklerinin kontrollü ve geriye uyumlu yapılması
- Duplicate delivery, retry, timeout ve servis kesintilerinin güvenli yönetilmesi

Spring ve FastAPI arasında kod paylaşımı değil, contract paylaşımı yapılacaktır.

## 2. Temel iletişim modeli
AI işlemleri asenkron olarak yürütülecektir.

  Spring Boot
     → command event
     → RabbitMQ
     → FastAPI AI Worker
     → result event
     → RabbitMQ
     → Spring Boot

Spring:

- AI job oluşturur.
- Command event yayınlar.
- Job durumunu saklar.
- Result event’i tüketir.
- AI sonucunu doğrular.
- Business kararını verir.

FastAPI:

- Command event’i tüketir.
- AI pipeline’ını çalıştırır.
- Canonical sonucu üretir.
- Result veya failure event’i yayınlar.
- Business state değiştirmez.

## 3. İlk desteklenen AI job türleri
İlk sürümde iki public AI capability bulunacaktır:

  DOCUMENT_EXTRACTION
  VIDEO_ANALYSIS

### 3.1 DOCUMENT_EXTRACTION

Aşağıdaki işlemler FastAPI içinde tek pipeline olarak yürütülür:

- Dosya indirme
- Hash doğrulama
- Dosya türü tespiti
- PDF veya DOCX metin çıkarımı
- Gerektiğinde OCR
- Metin normalizasyonu
- Hassas veri analizi
- Maskeleme
- RAG
- LLM extraction
- Canonical schema dönüşümü
- Teknik schema validation

Spring bu iç adımları ayrı job olarak görmez.

### 3.2 VIDEO_ANALYSIS

Aşağıdaki işlemleri kapsar:

- Video indirme
- Hash doğrulama
- Format kontrolü
- Frame veya segment analizi
- Nesne veya olay tespiti
- Confidence üretimi
- Advisory anomaly üretimi
- Canonical schema dönüşümü

Video sonucu yalnız advisory niteliktedir. Ödeme, teslimat veya dispute kararı vermez.

## 4. Event isimleri
İlk major version için event isimleri:

  ai.job.requested.v1
  ai.job.completed.v1
  ai.job.failed.v1
  ai.job.cancel.requested.v1

Job türü event isminden değil, envelope içindeki jobType alanından belirlenir.

Bu sayede ortak operasyonel mekanizmalar job türünden bağımsız kalır.

Progress event ilk sürümde bulunmayacaktır.

İleride gerekirse backward-compatible yeni event olarak eklenebilir:

  ai.job.progressed.v1

## 5. RabbitMQ topolojisi

### 5.1 Exchange’ler

  m4trust.ai.commands
  m4trust.ai.events
  m4trust.ai.dead-letter

Exchange türü:

  topic

### 5.2 Command routing key’leri

  ai.document-extraction.requested.v1
  ai.video-analysis.requested.v1
  ai.job.cancel.requested.v1

### 5.3 Result routing key’leri

  ai.document-extraction.completed.v1
  ai.document-extraction.failed.v1

  ai.video-analysis.completed.v1
  ai.video-analysis.failed.v1

### 5.4 Queue isimleri

FastAPI tarafı:

  m4trust.ai.document-extraction.v1
  m4trust.ai.video-analysis.v1
  m4trust.ai.cancellation.v1

Spring tarafı:

  m4trust.core.ai-results.v1

Dead-letter queue:

  m4trust.ai.dead-letter.v1

Queue ve exchange isimleri environment’a göre prefix alabilir:

  dev.m4trust.ai.commands
  staging.m4trust.ai.commands
  prod.m4trust.ai.commands

Payload contract’ı environment’a göre değişmez.

## 6. Ortak event envelope
Bütün mesajlar aşağıdaki ortak yapıyı kullanacaktır:

```json
{
"eventId": "0190f75b-f7dc-7000-8000-000000000001",
"eventType": "ai.job.requested.v1",
"schemaVersion": "1.0.0",
"occurredAt": "2026-07-13T12:30:00.000Z",
"correlationId": "0190f75b-f7dc-7000-8000-000000000002",
"causationId": null,
"jobId": "0190f75b-f7dc-7000-8000-000000000003",
"jobType": "DOCUMENT_EXTRACTION",
"tenantId": "0190f75b-f7dc-7000-8000-000000000004",
"transactionId": "0190f75b-f7dc-7000-8000-000000000005",

"subjectId": "0190f75b-f7dc-7000-8000-000000000006",
"idempotencyKey": "ai-job:0190f75b-f7dc-7000-8000-000000000003",
"producer": {
"service": "core-api",
"version": "1.0.0"
},
"payload": {}
}
```

### 6.1 Alanların anlamları

                 Alan                    Açıklama

                  eventId                Her publish işlemi için benzersiz event kimliği

                  eventType              Event adı ve major version

                  schemaVersion          Payload şemasının semantic version değeri

                  occurredAt             UTC RFC-3339 zamanı

                  correlationId          Uçtan uca işlem takibi

                  causationId            Bu event’i doğuran önceki event

                  jobId                  AI job kimliği

                  jobType                AI capability türü

                  tenantId               Tenant izolasyonu

                  transactionId          İlgili business transaction

                  subjectId              İşlenen document, video veya diğer subject

                  idempotencyKey         Duplicate-safe processing anahtarı

                  producer               Event’i üreten servis bilgisi

                  payload                Event türüne özel veri

Bütün UUID değerleri string biçiminde taşınacaktır.

Bütün zamanlar UTC olacaktır.

## 7. Document extraction request contract
jobType :

  DOCUMENT_EXTRACTION

Örnek payload:

```json
{
"input": {
"documentId": "0190f75b-f7dc-7000-8000-000000000006",
"fileName": "contract.pdf",
"mediaType": "application/pdf",
"sizeBytes": 481920,
"sha256":
"f0fba7a70f30d7fa3b285c7bce96e58ab6b93ef41445b9fc19f6a610387402d6",
"download": {
"url": "https://object-storage.example/presigned-resource",
"expiresAt": "2026-07-13T12:45:00.000Z"
}
},
"processing": {
"languageHints": [
"tr",
"en"
],
"documentCategory": "B2B_CONTRACT",
"requestedOutputSchema": "m4trust.document-extraction-result",
"requestedOutputSchemaVersion": "1.0.0",
"privacyProfile": "DEFAULT",
"retrievalProfile": "M4TRUST_LEGAL_DEFAULT"
},
"deadlineAt": "2026-07-13T13:00:00.000Z"
}
```

### 7.1 Request kuralları

- Raw dosya içeriği broker mesajında taşınmaz.
- download.url kısa ömürlü olmalıdır.
- FastAPI indirdiği içeriğin SHA-256 değerini doğrular.
- Hash uyuşmazlığı retry edilmez.
- Süresi dolmuş URL teknik olarak retryable kabul edilebilir.
- requestedOutputSchemaVersion FastAPI tarafından desteklenmiyorsa job işlenmez.
- FastAPI request’te belirtilmeyen ek business varsayımlar üretmez.

## 8. Document extraction result contract
Başarılı sonuç payload’ı:

```json
{
"result": {
"document": {
"detectedMediaType": "application/pdf",
"detectedLanguage": "tr",
"pageCount": 12,
"textExtractionMethod": "DIGITAL_PDF",

"contentSha256":
"f0fba7a70f30d7fa3b285c7bce96e58ab6b93ef41445b9fc19f6a610387402d6"
},
"parties": [
{
"partyReference": "party-1",
"role": "BUYER",
"legalName": {
"value": "ABC Anonim Şirketi",
"confidence": 0.97
},
"taxIdentifier": {
"value": null,
"masked": true,
"confidence": 0.94
},
"sourceReferences": [
{
"page": 1,
"startOffset": 120,
"endOffset": 178
}
]
}
],
"rules": [
{
"ruleReference": "rule-1",
"category": "PAYMENT",
"title": "Payment term",
"description": "Invoice date plus 30 days",
"structuredValue": {
"type": "DURATION",
"unit": "DAY",
"value": 30
},
"confidence": 0.92,
"sourceReferences": [
{
"page": 4,
"startOffset": 845,
"endOffset": 921
}
]
}
],
"deliveryRequirements": [
{
"requirementReference": "delivery-1",
"evidenceType": "DELIVERY_NOTE",
"required": true,

"confidence": 0.89,
"sourceReferences": [
{
"page": 7,
"startOffset": 160,
"endOffset": 245
}
]
}
],
"summary": {
"requiresManualReview": false,
"reviewReasons": []
}
},
"technicalMetadata": {
"pipelineVersion": "document-extraction-1.4.0",
"modelProvider": "INTERNAL",
"modelFamily": "generic-contract-model",
"modelVersion": "2026-07",
"promptVersion": "contract-extraction-3.2.0",
"retrievalVersion": "legal-rag-2.1.0",
"parserVersion": "document-parser-1.3.0",
"privacyVersion": "privacy-pipeline-1.1.0",
"durationMs": 48320
},
"warnings": []
}
```

### 8.1 Canonical sonuç ilkeleri

- Spring yalnızca canonical sonucu görür.
- Modelin native cevabı broker üzerinden gönderilmez.
- Provider-specific response alanları contract’a eklenmez.
- Confidence değerleri 0.0 ile 1.0 arasında decimal number olur.
- Confidence business kabul kararı değildir.
- Spring deterministic validator çalıştırır.
- Spring gerekli görürse manual review oluşturur.
- FastAPI’nin requiresManualReview değeri advisory niteliktedir.
- Spring bu alanı doğrudan business state kararı olarak kullanmaz.

## 9. Video analysis request contract
jobType :

  VIDEO_ANALYSIS

Örnek payload:

```json
{
"input": {
"videoId": "0190f75b-f7dc-7000-8000-000000000020",
"fileName": "delivery-video.mp4",
"mediaType": "video/mp4",
"sizeBytes": 19481920,
"sha256":
"f50f4a8b963d6d95cd09166fd5f9a4b5515d97c377e021532b33daae139bb531",
"download": {
"url": "https://object-storage.example/presigned-resource",
"expiresAt": "2026-07-13T12:45:00.000Z"
}
},
"processing": {
"analysisProfile": "DELIVERY_EVIDENCE_DEFAULT",
"expectedObjects": [
{
"label": "PACKAGE",
"expectedCount": 10
}
],
"requestedOutputSchema": "m4trust.video-analysis-result",
"requestedOutputSchemaVersion": "1.0.0"
},
"deadlineAt": "2026-07-13T13:15:00.000Z"
}
```

## 10. Video analysis result contract

```json
{
"result": {
"durationMs": 84200,
"observations": [
{
"observationReference": "observation-1",
"type": "OBJECT_COUNT",
"label": "PACKAGE",
"observedValue": 10,
"confidence": 0.91,
"timeRange": {
"startMs": 14200,
"endMs": 38100
}
}
],
"anomalies": [
{

"anomalyReference": "anomaly-1",
"type": "VISIBILITY_GAP",
"severity": "MEDIUM",
"description": "Objects are temporarily outside the visible frame.",
"confidence": 0.76,
"timeRange": {
"startMs": 39000,
"endMs": 42100
}
}
],
"summary": {
"advisoryOutcome": "REVIEW_SUGGESTED",
"reviewReasons": [
"VISIBILITY_GAP"
]
}
},
"technicalMetadata": {
"pipelineVersion": "video-analysis-1.2.0",
"modelProvider": "INTERNAL",
"modelFamily": "delivery-video-model",
"modelVersion": "2026-07",
"durationMs": 96720
},
"warnings": []
}
```

### 10.1 Video sonucu ilkeleri

- advisoryOutcome ödeme kararı değildir.
- FastAPI teslimat tamamlandı kararı vermez.
- FastAPI dispute açmaz veya kapatmaz.
- FastAPI payment release tetiklemez.
- Spring kendi deterministic kurallarını çalıştırır.
- Spring gerekli görürse review oluşturur.

## 11. Completed event contract
ai.job.completed.v1 yalnız canonical ve schema-valid sonuç için yayınlanır.

Completed event:

- Job’ın teknik olarak başarıyla tamamlandığını belirtir.
- Sonucun business olarak kabul edildiğini belirtmez.
- Spring’in sonucu doğrulama veya reddetme hakkını ortadan kaldırmaz.

Spring aşağıdaki durumlarda completed sonucu kabul etmeyebilir:

- Job iptal edilmişse
- Daha yeni bir job aynı subject için tamamlanmışsa
- Input hash artık geçerli değilse
- Schema version desteklenmiyorsa
- Transaction terminal durumda ise
- Result payload semantic validation’dan geçmiyorsa

Bu durumda event tüketilmiş olarak kaydedilir fakat business state’e uygulanmaz.

## 12. Failure event contract
ai.job.failed.v1 payload’ı:

```json
{
"error": {
"category": "RETRYABLE_TECHNICAL",
"code": "MODEL_PROVIDER_TIMEOUT",
"message": "AI provider did not respond before the configured timeout.",
"retryRecommended": true,
"details": {
"stage": "STRUCTURED_EXTRACTION"
}
},
"attempt": {
"attemptNumber": 3,
"maxAttempts": 3
},
"technicalMetadata": {
"pipelineVersion": "document-extraction-1.4.0",
"durationMs": 91500
}
}
```

### 12.1 Error kategorileri

İzin verilen kategoriler:

  RETRYABLE_TECHNICAL
  NON_RETRYABLE_TECHNICAL
  INVALID_INPUT

### 12.2 Örnek error code’lar

Retryable:

  MODEL_PROVIDER_TIMEOUT
  MODEL_PROVIDER_UNAVAILABLE
  OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE
  RETRIEVAL_SERVICE_UNAVAILABLE
  INTERNAL_DEPENDENCY_TIMEOUT

Non-retryable:

  UNSUPPORTED_MEDIA_TYPE
  UNSUPPORTED_SCHEMA_VERSION
  FILE_TOO_LARGE
  ENCRYPTED_DOCUMENT_UNSUPPORTED
  CORRUPTED_FILE
  CONTENT_HASH_MISMATCH

Invalid input:

  MISSING_REQUIRED_FIELD
  INVALID_DEADLINE
  INVALID_DOWNLOAD_REFERENCE
  INVALID_PROCESSING_PROFILE
  INVALID_EXPECTED_OBJECT

### 12.3 Hata kuralları

- Error code’lar stable contract parçasıdır.
- Raw stack trace broker mesajına yazılmaz.
- Provider hata mesajı doğrudan taşınmaz.
- Hassas veri message veya details içinde bulunmaz.
- FastAPI business rejection error code’u üretmez.
- Spring AI teknik hatasını transaction reddi olarak yorumlamaz.

## 13. Warning contract
Warning yapısı:

```json
{
"code": "LOW_TEXT_QUALITY",
"message": "Some pages have low extraction quality.",
"severity": "WARNING",
"path": "$.result.document",
"details": {
"affectedPages": [
3,

]
}
}
```

Warning severity değerleri:

  INFO
  WARNING

ERROR warning seviyesi kullanılmaz. İşlem tamamlanamayacak durumlar failure event üretir.

Warning code’lar zaman içinde backward-compatible olarak eklenebilir.

Spring bilinmeyen warning code’larını ignore edebilir ancak loglamalıdır.

## 14. Enum politikası
Canonical contract içindeki enum alanları closed set olarak değerlendirilir.

Consumer:

- Bilmediği optional alanları ignore eder.
- Bilmediği enum değerini sessizce kabul etmez.
- Bilinmeyen enum değerini contract violation olarak kaydeder.

İleriye uyumluluk gereken enum’larda açıkça:

  UNKNOWN

değeri tanımlanabilir.

Producer normal çalışma sırasında UNKNOWN üretmemelidir. Bu değer yalnız güvenli fallback olarak
kullanılmalıdır.

## 15. Contract versioning

### 15.1 Event major version

Event türünün sonunda major version bulunur:

  ai.job.completed.v1

Breaking change durumunda:

  ai.job.completed.v2

oluşturulur.

### 15.2 Schema version

Payload içinde semantic version bulunur:

  1.0.0
  1.1.0
  1.1.1

### 15.3 Minor version değişiklikleri

Minor version yalnız backward-compatible değişiklikler içerir:

- Yeni optional alan
- Yeni optional metadata
- Yeni warning code
- Yeni optional nested object
- Yeni event türü
- Yeni job capability

### 15.4 Patch version değişiklikleri

Patch version:

- Açıklama düzeltmesi
- Örnek düzeltmesi
- Semantic değiştirmeyen validation düzeltmesi
- Documentation iyileştirmesi

için kullanılır.

### 15.5 Breaking değişiklikler

Aşağıdakiler major version gerektirir:

- Required alan eklemek
- Required alan kaldırmak
- Alan tipini değiştirmek
- Alanın semantic anlamını değiştirmek
- Enum değerini kaldırmak
- Event adını değiştirmek
- JSON hiyerarşisini değiştirmek
- Nullability değiştirmek
- Para, tarih veya confidence formatını değiştirmek

## 16. Major version geçiş politikası
Yeni major version tek adımda eskisinin yerini almaz.

Geçiş sırası:

1. Yeni contract schema oluşturulur.
2. FastAPI yeni major version’ı destekler.
3. FastAPI capabilities endpoint’inde desteği ilan eder.
4. Spring yeni version için consumer desteği ekler.
5. Spring yeni job’ları yeni version ile göndermeye başlar.
6. Eski işler tamamlanır.
7. Gözlem süresi tamamlanır.
8. Eski major version deprecated ilan edilir.
9. Kullanım kalmadığı doğrulanır.
10. Eski version kaldırılır.

Bir major version’ın kaldırılması ayrı pull request ve release notu gerektirir.

## 17. Idempotency
Sistem at-least-once delivery varsayımıyla çalışır.

Exactly-once delivery varsayılmaz.

### 17.1 FastAPI consumer idempotency

FastAPI aşağıdaki bileşimi job identity olarak kullanır:

  jobId + jobType + input.sha256

Aynı kombinasyon tekrar gelirse FastAPI:

- Devam eden işi yeniden başlatmamalı,
- tamamlanan işi tekrar çalıştırmak zorunda olmamalı,
- önceki terminal sonucu yeniden yayınlayabilmeli,
- payload farklıysa conflict olarak işaretlemelidir.

Aynı jobId ile farklı input hash gelmesi contract violation’dır.

### 17.2 Spring consumer idempotency

Spring:

- eventId değerini inbox kaydında tutar.
- Aynı event’i ikinci kez business mutation’a uygulamaz.
- Aynı job için birden fazla terminal event gelirse state machine kurallarını uygular.
- Terminal job sonucunu ikinci kez işlemeye çalışmaz.

## 18. Retry politikası

### 18.1 FastAPI internal retry

FastAPI retryable teknik hatalarda en fazla üç deneme yapabilir.

Varsayılan politika:

  Attempt 1 → initial processing
  Attempt 2 → exponential backoff
  Attempt 3 → exponential backoff

Jitter uygulanmalıdır.

Exact süreler FastAPI operasyonel konfigürasyonudur ve contract’ın parçası değildir.

### 18.2 Spring job retry

FastAPI terminal failure gönderdikten sonra yeniden job başlatma kararı Spring’e aittir.

Spring yeniden çalıştırmak isterse:

- Yeni jobId üretir.
- Yeni idempotencyKey üretir.
- Önceki job ile ilişkiyi kendi veritabanında saklar.
- Aynı input hash kullanılabilir.

FastAPI terminal failure sonrası aynı job kimliğiyle sınırsız retry yapmaz.

## 19. Deadline ve timeout
Request payload içinde mutlak UTC zamanı bulunur:

```json
{
"deadlineAt": "2026-07-13T13:00:00.000Z"
}
```

FastAPI:

- Deadline geçmişse job’ı başlatmaz.
- Çalışma sırasında deadline aşılırsa güvenli şekilde durdurmaya çalışır.
- Timeout sonucu uygun failure event üretir.
- Kendi operasyonel üst limitini ayrıca uygulayabilir.

Spring:

- Business timeout’un sahibidir.
- Job bekleme süresini izler.
- Gerekirse job’ı timeout olarak işaretler.
- Cancel event yayınlayabilir.
- Yeni job başlatabilir.

## 20. Cancellation
Cancel event:

```json
{
"eventId": "0190f75b-f7dc-7000-8000-000000000030",
"eventType": "ai.job.cancel.requested.v1",
"schemaVersion": "1.0.0",
"occurredAt": "2026-07-13T12:35:00.000Z",
"correlationId": "0190f75b-f7dc-7000-8000-000000000002",
"causationId": "0190f75b-f7dc-7000-8000-000000000001",
"jobId": "0190f75b-f7dc-7000-8000-000000000003",
"jobType": "DOCUMENT_EXTRACTION",
"tenantId": "0190f75b-f7dc-7000-8000-000000000004",
"transactionId": "0190f75b-f7dc-7000-8000-000000000005",
"subjectId": "0190f75b-f7dc-7000-8000-000000000006",
"idempotencyKey": "cancel:0190f75b-f7dc-7000-8000-000000000003",
"producer": {
"service": "core-api",
"version": "1.0.0"
},
"payload": {
"reason": "DOCUMENT_REPLACED"
}
}
```

Cancellation best-effort’tür.

FastAPI:

- Henüz başlamamış job’ı iptal eder.
- Çalışan job’ı güvenli noktada durdurmaya çalışır.
- İş zaten tamamlandıysa completed event gönderebilir.

Spring:

- Cancel sonrası gelen geç sonucu job state’e göre kabul veya ignore eder.
- FastAPI’nin cancel işlemini kesin olarak gerçekleştirdiğini varsaymaz.

İlk sürümde ayrı cancelled result event zorunlu değildir.

## 21. FastAPI internal HTTP API’leri
FastAPI yalnız aşağıdaki internal HTTP endpoint’lerini sunacaktır:

  GET /health/live
  GET /health/ready
  GET /internal/v1/capabilities
  GET /internal/v1/contracts

### 21.1 Liveness

  GET /health/live

Process’in çalıştığını belirtir.

External provider veya RabbitMQ erişimi liveness sonucunu etkilememelidir.

### 21.2 Readiness

  GET /health/ready

Aşağıdakileri kontrol edebilir:

- RabbitMQ bağlantısı
- Gerekli model veya provider konfigürasyonu
- Object storage erişim yeteneği
- Gerekli schema registry veya local contract dosyaları
- Worker’ın yeni iş kabul edebilme durumu

### 21.3 Capabilities

Örnek cevap:

```json
{
"service": "ai-service",
"serviceVersion": "1.4.0",
"capabilities": [
{
"jobType": "DOCUMENT_EXTRACTION",
"requestSchemaVersions": [
"1.0.0"
],
"resultSchemaVersions": [
"1.0.0"
]

},
{
"jobType": "VIDEO_ANALYSIS",
"requestSchemaVersions": [
"1.0.0"
],
"resultSchemaVersions": [
"1.0.0"
]
}
]
}
```

### 21.4 Contracts endpoint

Contract metadata ve checksum bilgisi döndürür:

```json
{
"contracts": [
{
"name": "m4trust.document-extraction-request",
"version": "1.0.0",
"sha256": "..."
}
]
}
```

Bu endpoint runtime contract discovery için değil, operasyonel doğrulama için kullanılır.

Spring her job öncesi bu endpoint’i çağırmaz.

### 21.5 Yasak senkron endpoint’ler

Aşağıdaki türde endpoint’ler oluşturulmayacaktır:

  POST /extract
  POST /analyze
  POST /chat
  POST /generate
  POST /video/analyze

AI processing broker üzerinden yürütülür.

## 22. Contract repository yapısı
Ortak contract’lar monorepo içinde tutulacaktır:

  contracts/
    asyncapi/
      m4trust-ai-v1.yaml

    schemas/
      common/
        event-envelope.schema.json
        error.schema.json
          warning.schema.json
          source-reference.schema.json

       document-extraction/
         request-1.0.0.schema.json
         result-1.0.0.schema.json

       video-analysis/
         request-1.0.0.schema.json
         result-1.0.0.schema.json

    examples/
      document-extraction/
        minimum-request.json
        full-request.json
        success-result.json
        warning-result.json
        retryable-failure.json
        non-retryable-failure.json

       video-analysis/
         minimum-request.json
         full-request.json
         success-result.json
         warning-result.json
         retryable-failure.json
         non-retryable-failure.json

    openapi/
      ai-internal-v1.yaml

    CHANGELOG.md
    README.md

Contract dosyaları Spring veya FastAPI private klasörleri altında tutulmaz.

## 23. Kod üretimi politikası
Spring ve FastAPI JSON Schema veya OpenAPI’den kendi modellerini üretebilir.

Ancak:

- Generated modeller doğrudan domain entity olarak kullanılmamalıdır.
- Transport modelleri application boundary’de domain modeline çevrilmelidir.
- Servisler ortak runtime package paylaşmak zorunda değildir.
- Contract değişikliği generated code üzerinden değil schema üzerinden yapılır.
- Generated dosyalar elle düzenlenmez.

Spring tarafında generated DTO’lar integration adapter katmanında tutulur.

FastAPI tarafında generated veya schema-backed modeller transport katmanında tutulur.

## 24. Contract testleri
Minimum contract test seti zorunludur.

### 24.1 Ortak contract repository testleri

- Bütün JSON örneklerinin schema validation’dan geçmesi
- Invalid fixture’ların reddedilmesi
- Schema reference’larının çözülebilmesi
- AsyncAPI dokümanının doğrulanması
- OpenAPI dokümanının doğrulanması

### 24.2 FastAPI testleri

- Minimum request kabulü
- Full request kabulü
- Bilinmeyen optional alan toleransı
- Bilinmeyen enum reddi
- Desteklenmeyen major version reddi
- Canonical success result üretimi
- Canonical failure result üretimi
- Duplicate job davranışı

### 24.3 Spring testleri

- Completed event consumer testi
- Failed event consumer testi
- Duplicate event testi
- Cancel sonrası geç result testi
- Input hash uyuşmazlığı testi
- Desteklenmeyen schema version testi
- Bilinmeyen optional alan toleransı
- Bilinmeyen enum contract violation testi

Test sayısı minimum tutulabilir ancak bu sınır testleri kaldırılmaz.

## 25. Merge politikası
contracts/ altında yapılan değişiklikler:

- Ayrı branch üzerinde geliştirilir.
- Spring geliştiricisinin review’undan geçer.
- FastAPI geliştiricisinin review’undan geçer.
- Mimari karar sahibi tarafından değerlendirilir.
- Contract testlerini geçer.
- Gerekli changelog kaydını içerir.

Contract değişikliği uygulama kodundan sonra yapılmaz.

Doğru sıra:

  1. Contract değişikliği
  2. Fixture değişikliği
  3. Contract testleri
  4. FastAPI implementasyonu
  5. Spring implementasyonu
  6. Entegrasyon testi

Spring ve FastAPI kodları paralel geliştirilebilir; ancak ortak contract merge edilmeden entegrasyon
kodu merge edilmez.

## 26. Model ve pipeline değişiklikleri
Aşağıdaki değişiklikler contract değişikliği gerektirmez:

- LLM provider değiştirmek
- LLM modelini değiştirmek
- Prompt güncellemek
- RAG stratejisini değiştirmek
- Embedding modelini değiştirmek
- Vector store değiştirmek
- OCR motorunu değiştirmek
- PDF parser değiştirmek
- Video modelini değiştirmek
- Pipeline iç adımlarının sırasını değiştirmek
- Retry sürelerini değiştirmek
- Worker concurrency değerini değiştirmek

Şu koşullar korunmalıdır:

- Canonical payload değişmemeli.
- Alanların semantic anlamı değişmemeli.
- Mevcut schema validation geçmeli.
- Confidence ve source reference kuralları korunmalı.

- Spring’in business davranışı model metadata’ya bağlanmamalı.

## 27. Spring tarafında yasaklanan bağımlılıklar
Spring aşağıdaki detayları kullanmayacaktır:

- Provider-specific model adıyla business branching
- Prompt version’a göre business branching
- RAG sonucu ham formatı
- Native model JSON’u
- Provider SDK tipi
- Model token sayısı
- Temperature
- Context window
- OCR motorunun özel alanları
- Video provider özel detection formatı

Model metadata yalnız audit, gözlemlenebilirlik ve kalite analizi için saklanabilir.

## 28. FastAPI tarafında yasaklanan davranışlar
FastAPI:

- Spring PostgreSQL’e bağlanamaz.
- Transaction state değiştiremez.
- Ratification oluşturamaz.
- Payment provider çağıramaz.
- Payment release kararı veremez.
- Dispute kapatamaz.
- Kullanıcı yetkisi değerlendiremez.
- Frontend’e public AI API sunamaz.
- Model native sonucunu canonical sonuç yerine gönderemez.
- Contract’ta olmayan required alanı Spring’den bekleyemez.
- Contract değişikliğini tek taraflı yapamaz.

## 29. PII ve veri taşıma politikası
Broker mesajlarında:

- Raw doküman taşınmaz.
- Raw video taşınmaz.
- Gereksiz kişisel veri taşınmaz.
- Secret taşınmaz.
- Provider credential taşınmaz.
- Session veya access token taşınmaz.

Raw içerik object storage üzerinden süreli erişimle alınır.

FastAPI:

- Local geçici dosyaları job sonunda temizler.
- Raw prompt veya PII loglamaz.
- Native provider response saklayacaksa retention ve encryption politikasına uyar.
- Spring’e yalnız canonical ve gerekli sonucu gönderir.

## 30. Observability
Her log, trace ve metric mümkün olduğunda aşağıdaki alanları içermelidir:

  correlationId
  jobId
  jobType
  tenantId
  transactionId
  subjectId
  schemaVersion
  pipelineVersion

Spring ve FastAPI aynı correlation ID’yi korur.

Önerilen temel metrikler:

  ai_jobs_requested_total
  ai_jobs_completed_total
  ai_jobs_failed_total
  ai_job_duration_seconds
  ai_job_retry_total
  ai_job_duplicate_total
  ai_contract_violation_total
  ai_late_result_total

Model performans metrikleri FastAPI’ye aittir.

Business sonuç metrikleri Spring’e aittir.

## 31. İlk uygulama sırası
ADR-002 sonrasında uygulama sırası:

1. contracts/ dizininin oluşturulması
2. Ortak event envelope şemasının yazılması
3. Error ve warning şemalarının yazılması
4. Document extraction request/result şemalarının yazılması

5. Video analysis request/result şemalarının yazılması
6. Canonical fixture’ların hazırlanması
7. AsyncAPI dokümanının hazırlanması
8. Internal OpenAPI dokümanının hazırlanması
9. Contract validation CI job’ının eklenmesi
10. Spring AI job producer skeleton’ı
11. FastAPI consumer ve producer skeleton’ı
12. Duplicate handling
13. İlk sahte end-to-end job
14. Gerçek document extraction pipeline entegrasyonu
15. Video analysis entegrasyonu

Gerçek model entegrasyonu contract ve fake end-to-end akış tamamlanmadan yapılmamalıdır.

## 32. Nihai karar
Spring Boot Core Platform ile FastAPI AI Service arasında:

- Asenkron RabbitMQ iletişimi kullanılacaktır.
- Tek ortak event envelope kullanılacaktır.
- İlk public capability’ler DOCUMENT_EXTRACTION ve VIDEO_ANALYSIS olacaktır.
- OCR, masking, RAG ve LLM ayrı public job türleri olmayacaktır.
- FastAPI yalnız canonical M4Trust payload’ı üretecektir.
- Model ve provider detayları Spring’den gizlenecektir.
- Contract’lar JSON Schema, AsyncAPI ve OpenAPI ile tanımlanacaktır.
- Aynı major version içinde yalnız backward-compatible değişiklik yapılacaktır.
- Duplicate-safe ve at-least-once processing uygulanacaktır.
- FastAPI teknik retry, Spring ise business retry sahibi olacaktır.
- AI işlemleri için senkron inference API sunulmayacaktır.
- Contract değişiklikleri Spring ve FastAPI sahiplerinin ortak onayına tabi olacaktır.
- ADR-002 tamamlanmadan gerçek AI entegrasyon kodu geliştirilmeyecektir.
