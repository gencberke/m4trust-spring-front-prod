# ADR-006: Public API and Error Conventions

- Durum: Accepted
- Tarih: 14 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: React frontend ile Spring Boot Core Platform arasındaki public HTTP API standartları
- Bağlı kararlar:
- ADR-001: System Boundaries and Data Ownership
- ADR-003: Core Domain Model and Deal Lifecycle
- ADR-004: Vertical Slice Delivery and Acceptance Testing
- ADR-005: Authentication and Security Baseline

## 1. Bağlam

M4Trust frontend’i yalnız Spring Boot Core Platform public API’siyle iletişim kuracaktır.

Frontend:

- FastAPI’yi doğrudan çağırmaz.
- Spring’in internal domain modellerini bilmez.
- JPA entity’lerini kullanmaz.
- Backend response yapılarını tahmin ederek paralel modeller oluşturmaz.
- Business kararlarını response metinlerinden çıkarmaya çalışmaz.

Public API, frontend ile Spring arasındaki bağımsız ve versioned sistem sınırıdır.

Bu ADR aşağıdaki konuları standartlaştırır:

- URL ve resource adlandırma
- Request ve response formatları
- Hata modeli
- HTTP status kullanımı
- Validation hataları
- Pagination, filtering ve sorting
- Optimistic concurrency
- Idempotency
- Tarih, para, yüzde, UUID ve enum gösterimleri
- Correlation ID
- Asenkron işlem response’ları
- OpenAPI geliştirme yaklaşımı

## 2. API versioning

Public API path tabanlı versioning kullanacaktır.

Başlangıç prefix’i:

```text
/api/v1
```

Örnekler:

```text
/api/v1/auth/login
/api/v1/deals
/api/v1/deals/{dealId}
/api/v1/deals/{dealId}/documents
```

v1, tek tek endpoint implementasyonunun değil, public contract ailesinin major version’ıdır.

Aynı major version içinde geriye uyumlu, additive değişiklikler yapılabilir.

Breaking değişiklik gerektiğinde yeni major version değerlendirilir:

```text
/api/v2
```

Her internal refactor yeni API version’ı gerektirmez.

## 3. URL adlandırma kuralları

Resource path’leri çoğul isim kullanacaktır.

Doğru:

```text
/deals
/documents
/legal-entities
/invitations
/payment-operations
```

Path segmentleri:

kebab-case

kullanacaktır.

JSON field isimleri:

lowerCamelCase

kullanacaktır.

Örnek:

```json
{
"legalEntityId": "uuid",
"createdAt": "2026-07-14T12:30:00Z"
}
```

## 4. Resource ve action endpoint’leri

Standart CRUD davranışlarında resource odaklı endpoint’ler tercih edilir.

Örnek:

| GET | /api/v1/deals |
| --- | --- |
| POST | /api/v1/deals |
| GET | /api/v1/deals/{dealId} |
| PATCH | /api/v1/deals/{dealId} |

Ancak business operation’lar yapay biçimde CRUD modeline zorlanmaz.

Açık business action endpoint’leri kullanılabilir:

```text
POST /api/v1/invitations/{invitationId}/accept
POST /api/v1/invitations/{invitationId}/reject
POST /api/v1/deals/{dealId}/cancel
POST /api/v1/ratification-packages/{packageId}/ratify
POST /api/v1/payment-operations/{operationId}/reconcile
```

Action isimleri fiil olarak ve açık business anlamıyla tanımlanır.

Generic endpoint’lerden kaçınılır:

```text
/action
/execute
/process
/update-status
```

## 5. HTTP metodunun anlamı

Başlangıç kuralları:

| GET | → resource veya projection okuma |
| --- | --- |
| POST | → resource oluşturma veya business action başlatma |
| PUT | → resource’ın tam replacement işlemi gerektiğinde |
| PATCH | → kısmi güncelleme |
| DELETE | → gerçekten silinebilir technical resource için |

Business aggregate’lerinde generic DELETE kullanılması varsayılan değildir.

ADR-003’e uygun olarak business lifecycle action’ları tercih edilir:

cancel archive withdraw supersede

Safe ve idempotent HTTP metodunun anlamı bozulmaz.

GET isteği business mutation üretmez.

## 6. Başarılı response formatı

Global success envelope kullanılmayacaktır.

Aşağıdaki gibi bir yapı standart değildir:

```json
{
"success": true,
"data": {},
"message": "Success"
}
```

Tek resource doğrudan response body olarak döner:

```json
{
"id": "7b9b1c4f-6577-4f45-9383-2ce082f72d97",
"title": "Equipment Purchase",
"status": "DRAFT",
"version": 3
}
```

Başarı bilgisi HTTP status üzerinden anlaşılır.

Frontend, success boolean alanına bağımlı olmaz.

## 7. Oluşturma response’u

Yeni resource başarıyla oluşturulduğunda varsayılan response:

## 201. Created

Location: /api/v1/deals/{dealId}

Response body oluşturulan public resource veya ilgili projection olabilir:

```json
{
"id": "uuid",
"title": "Equipment Purchase",
"status": "DRAFT",
"version": 0
}
```

Her create endpoint’inin response’u OpenAPI’de açıkça tanımlanır.

## 8. Body bulunmayan başarılı response

Response body’ye ihtiyaç yoksa:

## 204. No Content

kullanılabilir.

## 204. response body içermez.

Business action sonucunda güncel resource’ın frontend tarafından hemen gösterilmesi gerekiyorsa 200 ile güncel projection döndürmek tercih edilebilir.

Bu seçim endpoint contract’ında sabitlenir.

## 9. Liste response formatı

Spring Data framework tipleri public API’den doğrudan dışarı verilmeyecektir.

Özellikle aşağıdakiler public response olmayacaktır:

- PageImpl
- JPA entity
- Spring internal pagination metadata
- Repository sonucu

Page-based liste response’u stabil bir public DTO kullanır:

```json
{
"items": [
{
"id": "uuid",
"title": "Deal A",
"status": "ACTIVE"
}
],
"page": 0,
"size": 20,
"totalElements": 145,
"totalPages": 8
}
```

Alanlar:

items page size totalElements totalPages

olarak standartlaştırılmıştır.

Liste boş olduğunda:

```json
{
"items": [],
"page": 0,
"size": 20,
"totalElements": 0,
"totalPages": 0
}
```

döner.

items hiçbir zaman null olmaz.

## 10. Pagination

Başlangıç pagination modeli page-based olacaktır.

Query parametreleri:

```text
?page=0&size=20
```

Kurallar:

Page indexing: 0 tabanlı Default size: 20 Maximum size: 100

Geçersiz pagination parametreleri validation hatası üretir.

Örnek:

page < 0 size < 1 size > 100

Büyük audit/event listeleri gibi use case’lerde ileride cursor pagination kullanılabilir.

Cursor pagination genel public API standardı olarak başlangıçta zorunlu değildir.

## 11. Sorting

Sorting formatı:

```text
?sort=createdAt,desc
```

Birden fazla sort gerekirse parametre tekrar edebilir:

```text
?sort=status,asc&sort=createdAt,desc
```

Kurallar:

- Her endpoint yalnız allowlist içindeki alanlarda sort destekler.
- Frontend database column adı göndermez.
- JPA property adı public contract olmak zorunda değildir.
- Bilinmeyen sort alanı validation hatası üretir.
- Varsayılan sort endpoint contract’ında belirtilir.
- Deterministik pagination için gerekirse ikinci stabil sort alanı backend tarafından eklenebilir.

## 12. Filtering

Global ve sınırsız bir filter query language kullanılmayacaktır.

Her endpoint yalnız desteklediği filtreleri açıkça tanımlar.

Örnek:

GET /api/v1/deals?status=ACTIVE&legalEntityId={id}

Serbest metin araması gerekiyorsa:

```text
?query=equipment
```

kullanılabilir.

Aşağıdaki gibi database veya expression odaklı filtreler public API’ye açılmaz:

filter=status:eq:ACTIVE where=... sql=...

Filter alanlarının semantiği OpenAPI’de açıklanır.

## 13. Problem Details hata modeli

Public API hataları RFC 9457 Problem Details yaklaşımına uygun biçimde döndürülecektir.

Content type:

application/problem+json

Temel response:

```json
{
"type": "https://problems.m4trust.internal/deal-state-conflict",
"title": "Request could not be completed",
"status": 409,
"detail": "The requested operation conflicts with the current resource
state.",
"instance": "/api/v1/deals/7b9b1c4f/cancel",
"code": "DEAL_STATE_CONFLICT",
"correlationId": "4fa1204c-1c4d-40ff-b583-17152c4c5319"
}
```

Standart alanlar:

type title status detail instance

M4Trust extension alanları:

code correlationId errors

## 14. Hata kodu

Her anlamlı public hata stabil bir machine-readable code alanı taşımalıdır.

Örnekler:

```text
AUTH_INVALID_CREDENTIALS
AUTH_EMAIL_ALREADY_EXISTS
AUTH_SESSION_EXPIRED
DEAL_NOT_FOUND
DEAL_STATE_CONFLICT
DEAL_STALE_VERSION
LEGAL_ENTITY_ACCESS_DENIED
VALIDATION_FAILED
IDEMPOTENCY_KEY_REUSED
RATE_LIMIT_EXCEEDED
INTERNAL_ERROR
```

Frontend business logic’ini:

- detail
- title
- İnsan tarafından okunabilir mesaj

üzerinden kurmaz.

Frontend davranışını HTTP status ve code üzerinden belirler.

Error code’lar public contract’ın parçasıdır.

## 15. Hata metni

title ve detail alanları kullanıcıya doğrudan gösterilmek zorunda değildir.

Frontend:

- Machine-readable code ile uygun kullanıcı mesajını seçebilir.
- Dil seçimini frontend localization sistemiyle yapabilir.
- Beklenmeyen hatalarda correlation ID gösterebilir.

Backend hata response’ları:

- Stack trace içermez.
- SQL bilgisi içermez.
- Internal class adı içermez.
- Secret içermez.
- Credential içermez.
- Hassas authorization ayrıntısı sızdırmaz.

## 16. Field validation hataları

Field veya request validation hatalarında errors array’i kullanılacaktır.

Örnek:

```json
{
"type": "https://problems.m4trust.internal/validation-failed",
"title": "Validation failed",
"status": 422,
"detail": "One or more fields are invalid.",
"instance": "/api/v1/auth/register",
"code": "VALIDATION_FAILED",
"correlationId": "uuid",
"errors": [
{
"field": "email",
"code": "INVALID_FORMAT",
"message": "Email format is invalid."
},
{
"field": "password",
"code": "TOO_SHORT",
"message": "Password does not meet the minimum length."
}
]
}
```

Field error alanları:

field code message

Nested field path gerektiğinde dot notation kullanılabilir:

```text
buyer.address.countryCode
milestones[0].dueDate
```

Frontend yalnız message metnine bağımlı olmaz.

## 17. HTTP status politikası

Başlangıç status kullanımı:

Durum                                                                     HTTP status

Geçersiz JSON veya request formatı                                  400 Bad Request

Authentication bulunmuyor veya session geçersiz                    401 Unauthorized

Kullanıcı authenticated fakat işlemi yapamaz                          403 Forbidden

Resource bulunamadı veya güvenlik nedeniyle gizleniyor                404 Not Found

Field veya semantic request validation hatası            422 Unprocessable Content

Duplicate veya mevcut business state ile çatışma                       409 Conflict

Login throttling veya rate limit                              429 Too Many Requests

Yeni resource oluşturuldu                                               201 Created

Async işlem kabul edildi                                               202 Accepted

Başarılı ve body yok                                                 204 No Content

Beklenmeyen server hatası                                500 Internal Server Error

Geçici dependency problemi                                  503 Service Unavailable

## 18. 400 ve 422 ayrımı

400 Bad Request request teknik olarak parse edilemiyorsa kullanılır.

Örnek:

- Bozuk JSON
- Geçersiz UUID path formatı

- Geçersiz query parameter veri tipi
- Desteklenmeyen serialization formatı

## 422. Unprocessable Content request parse edilebiliyor fakat alanları veya semantiği geçersizse

kullanılır.

Örnek:

- E-posta formatı geçersiz
- Parola minimum uzunluğun altında
- Tarih aralığı geçersiz
- Zorunlu business alanı boş
- amountMinor negatif olamaz
- Buyer ve seller aynı legal entity olarak gönderilmiş

## 19. 409 Conflict kullanımı

409 Conflict request’in mevcut resource veya business state ile çatıştığı durumlarda kullanılır.

Örnek:

- Aynı e-posta zaten kayıtlı
- Deal mevcut durumda iptal edilemez
- Davet zaten kabul edilmiş
- Kullanıcı aynı role zaten sahip
- Eski aggregate version ile update denenmiş
- Aynı idempotency key farklı request ile kullanılmış
- Ratified package değiştirilmek istenmiş

## 409., generic validation status olarak kullanılmaz.

## 20. 401, 403 ve 404 ayrımı

401 Unauthorized :

- Session yok
- Session süresi dolmuş
- Authentication geçersiz

## 403. Forbidden :

- Kullanıcı authenticated
- Resource biliniyor
- Kullanıcının operation yetkisi yok

## 404. Not Found :

- Resource gerçekten yok

- Resource varlığını açıklamak güvenlik riski oluşturuyor

Hangi endpoint’in unauthorized resource için 403 veya 404 döndüreceği authorization politikasıyla tutarlı olmalıdır.

Frontend bu ayrımı tahmin ederek üretmez.

## 21. Optimistic concurrency

Mutable aggregate update işlemlerinde optimistic concurrency kullanılacaktır.

Public request içinde:

```json
{
"title": "Updated Deal Title",
"expectedVersion": 3
}
```

gönderilir.

Spring, aggregate’ın current version’ı ile expectedVersion değerini karşılaştırır.

Eşleşmezse:

## 409. Conflict

```json
{
"type": "https://problems.m4trust.internal/stale-resource-version",
"title": "Resource has changed",
"status": 409,
"detail": "The resource was modified by another operation.",
"instance": "/api/v1/deals/{dealId}",
"code": "DEAL_STALE_VERSION",
"correlationId": "uuid"
}
```

döner.

Frontend kullanıcıya:

- Güncel veriyi yeniden yükleme
- Değişiklikleri tekrar değerlendirme
- İşlemi yeniden uygulama

seçenekleri sunabilir.

## 22. expectedVersion tercihi

Başlangıçta optimistic concurrency için:

expectedVersion

kullanılacaktır.

ETag ve If-Match başlangıç zorunluluğu değildir.

Bu tercih:

- Generated TypeScript client kullanımını kolaylaştırır.
- Business action request’lerinde açık davranış sağlar.
- Frontend acceptance testlerini sadeleştirir.
- Aggregate version’ın request contract’ında görünür olmasını sağlar.

Caching veya public HTTP conditional request ihtiyacı oluşursa ETag ayrıca değerlendirilebilir.

## 23. Version alanı

Mutable public resource veya projection gerektiğinde:

```json
{
"id": "uuid",
"version": 3
}
```

alanı döndürür.

version :

- Optimistic concurrency token’ıdır.
- Business lifecycle status değildir.
- Timestamp değildir.
- Frontend tarafından artırılmaz.
- Spring tarafından yönetilir.

Immutable versioned resource’lar kendi domain version numarasına ayrıca sahip olabilir:

aggregate version ruleSetVersionNumber extractionResultVersionNumber

Bu kavramlar birbirine karıştırılmaz.

## 24. Idempotency

Duplicate side-effect riski bulunan endpoint’lerde idempotency desteklenecektir.

Header:

Idempotency-Key: <uuid>

Idempotency gerektiren endpoint’ler OpenAPI’de açıkça işaretlenir.

Başlangıç adayları:

- Davet gönderme
- Document upload finalize
- AI extraction job başlatma
- Funding operation başlatma
- Payment operation
- Settlement veya release operation
- Harici provider side-effect’i başlatan işlemler

Her basit POST için idempotency zorunlu değildir.

## 25. Idempotency davranışı

Aynı idempotency key ve aynı canonical request tekrar gönderilirse:

- İşlem ikinci kez uygulanmaz.
- İlk işlemin sonucu veya eşdeğer sonucu döndürülür.

Aynı key farklı request ile kullanılırsa:

## 409. Conflict

```json
{
"code": "IDEMPOTENCY_KEY_REUSED"
}
```

döner.

Idempotency kaydı en az şu bilgilerle ilişkilendirilebilir:

authenticated actor tenant operation type

idempotency key canonical request hash result reference createdAt expiry

Idempotency yalnız frontend double-click önlemi değildir.

Server-side uygulanır.

## 26. Timestamp formatı

Public API timestamp alanları:

RFC 3339 UTC

formatında ve Z suffix’i ile döner.

Örnek:

```json
{
"createdAt": "2026-07-14T12:30:00Z"
}
```

Offset içeren alternatif timestamp formatları public API’den rastgele döndürülmez.

Spring internal time representation’ı:

Instant

veya eşdeğer UTC-safe tip kullanmalıdır.

Frontend görüntüleme sırasında kullanıcının timezone’una çevirebilir.

## 27. Date formatı

Saat bilgisi taşımayan business date:

YYYY-MM-DD

formatında döner.

Örnek:

```json
{
"dueDate": "2026-08-01"
}
```

Date alanına sahte midnight timestamp eklenmez.

Timestamp ve date semantiği birbirine karıştırılmaz.

## 28. Money formatı

Para değerleri floating-point olarak taşınmayacaktır.

Format:

```json
{
"amountMinor": 125050,
"currency": "TRY"
}
```

Anlamı:

### 1250.50. TRY

Kurallar:

- amountMinor integer’dır.
- Para hesabında float/double kullanılmaz.
- currency açıkça taşınır.
- Currency ISO 4217 kodu olarak UPPER_SNAKE_CASE benzeri uppercase string kullanır.
- Para alanının scale’i currency semantiğine göre yorumlanır.
- Frontend yalnız formatlama yapar; authoritative hesap yapmaz.

## 29. Percentage formatı

Yüzde değerleri basis point olarak taşınacaktır.

Örnek:

```json
{
"penaltyBasisPoints": 250
}
```

Anlamı:

2.50%

Kurallar:

- Integer kullanılır.
- Float kullanılmaz.
- Alan adı BasisPoints suffix’i ile semantiği açıkça belirtir.
- Frontend gösterim için yüzdeye dönüştürebilir.
- Business hesaplamanın sahibi Spring’dir.

## 30. UUID formatı

Public identifier’lar string olarak taşınan UUID kullanacaktır.

Örnek:

```json
{
"dealId": "7b9b1c4f-6577-4f45-9383-2ce082f72d97"
}
```

Frontend UUID’nin iç yapısından business anlam çıkarmaz.

Sequential integer ID’ler public business identifier olarak başlangıç standardı değildir.

## 31. Enum formatı

Public enum değerleri:

UPPER_SNAKE_CASE

formatında taşınır.

Örnek:

```json
{
"status": "REVIEW_REQUIRED"
}
```

Enum değerleri public contract’tır.

Frontend bilinmeyen enum değerinde sessizce yanlış business davranışı üretmez.

Geriye uyumlu enum genişletme yapılacaksa frontend fallback davranışı ayrıca düşünülmelidir.

Closed enum’lar OpenAPI’de açıkça tanımlanır.

## 32. Null ve optional alanlar

Collection alanları boş olduğunda:

```json
[]
```

döner; null dönmez.

Optional scalar veya object alanlarında endpoint contract’ı açıkça şu kararlardan birini seçer:

- Alan mevcut ve null olabilir.
- Alan mevcut değilse response’tan çıkarılır.

Aynı alanın rastgele biçimde bazen null, bazen absent dönmesine izin verilmez.

null semantik olarak:

değer şu anda mevcut değil

anlamına gelir.

Boş string, bilinmeyen değer yerine kullanılmaz.

## 33. Boolean alanlar

Boolean alanlar açık olumlu anlamla adlandırılır.

Tercih edilen:

canCancel requiresReview isCurrent

Belirsiz veya çift negatif alanlardan kaçınılır:

notDisabled isNotUnverified

Business action availability mümkünse Spring projection’ından gelir.

Frontend status kombinasyonlarından kendisi türetmez.

## 34. Correlation ID

Her public request bir correlation ID’ye sahip olacaktır.

Request header:

X-Correlation-ID

Frontend geçerli bir UUID gönderebilir.

Header yoksa veya geçersizse Spring yeni correlation ID üretir.

Response header:

X-Correlation-ID: <uuid>

olarak döner.

Problem response içinde de bulunur:

```json
{
"correlationId": "uuid"
}
```

Correlation ID:

- Loglarda taşınır.
- Outbox event’lerine aktarılabilir.
- FastAPI mesajlaşmasındaki correlation bağlamına aktarılabilir.
- Kullanıcı destek süreçlerinde hata ile log eşleştirmeye yardımcı olur.

## 35. Async operation response’u

Uzun sürecek operasyonlar synchronous HTTP request içinde bekletilmez.

Örnek:

POST /api/v1/deals/{dealId}/document-extractions

Response:

## 202. Accepted

Location: /api/v1/ai-jobs/{jobId}

```json
{
"jobId": "uuid",
"status": "QUEUED"
}
```

## 202., işlemin tamamlandığı anlamına gelmez.

Yalnız command’ın kabul edildiğini ifade eder.

Frontend sonucu:

- Polling
- Manual refresh
- İleride SSE/WebSocket
- İlgili Deal projection’ının tekrar okunması

ile takip edebilir.

Takip yöntemi ilgili slice’ta belirlenir.

## 36. Async operation failure

Async job daha sonra başarısız olursa başlangıçtaki 202 response’u geriye dönük değiştirilmez.

Job veya ilgili projection güncel durumu gösterir:

```json
{
"jobId": "uuid",
"status": "FAILED",
```

```json
"failureCategory": "RETRYABLE_TECHNICAL"
}
```

AI technical failure doğrudan Deal business rejection anlamına gelmez.

Bu ayrım ADR-002 ve ADR-003 kurallarına tabidir.

## 37. Rate limit response’u

Rate limit veya login throttling durumunda:

## 429. Too Many Requests

döner.

Mümkünse:

Retry-After: 900

header’ı kullanılır.

Problem response:

```json
{
"code": "RATE_LIMIT_EXCEEDED",
"status": 429,
"correlationId": "uuid"
}
```

Hesap enumeration riski bulunan authentication endpoint’lerinde response ayrıntıları kontrollü tutulur.

## 38. Beklenmeyen server hatası

Beklenmeyen hata:

## 500. Internal Server Error

ile döner.

Örnek:

```json
{
"type": "https://problems.m4trust.internal/internal-error",
"title": "Unexpected error",
"status": 500,
"detail": "The request could not be completed.",
"instance": "/api/v1/deals",
"code": "INTERNAL_ERROR",
"correlationId": "uuid"
}
```

Response internal exception ayrıntısı taşımaz.

Ayrıntılı hata yalnız güvenli teknik loglarda bulunur.

## 39. Dependency unavailable

PostgreSQL, RabbitMQ, object storage veya başka zorunlu dependency nedeniyle işlem geçici olarak gerçekleştirilemiyorsa:

## 503. Service Unavailable

kullanılabilir.

Ancak async tasarlanması gereken işlemlerde dependency çağrısı HTTP request içinde tutulmamalıdır.

Örneğin AI modelinin geçici olarak unavailable olması frontend request’ini uzun süre açık tutan 503 akışı yerine async job failure olarak ele alınmalıdır.

## 40. Public DTO sınırı

Public API DTO’ları:

- JPA entity değildir.
- Domain aggregate değildir.
- Repository projection’ı doğrudan değildir.
- Internal event payload’ı değildir.
- FastAPI AI contract’ı değildir.

Public DTO’lar frontend use case’ine göre tasarlanır.

Bir aggregate’ın bütün internal alanlarını frontend’e açmak zorunlu değildir.

Sensitive alanlar public DTO’ya eklenmez.

## 41. Read projection yaklaşımı

Frontend ekranları için gereken veriler tek tek internal aggregate graph’ı olarak dönmek zorunda değildir.

Spring use-case odaklı projection döndürebilir.

Örnek:

```json
{
"deal": {
"id": "uuid",
"title": "Deal A",
"lifecycle": "MANUAL_REVIEW"
},
"currentLegalEntity": {
"id": "uuid",
"role": "BUYER"
},
"availableActions": [
"UPLOAD_DOCUMENT",
"INVITE_PARTICIPANT"
]
}
```

Frontend:

- Lifecycle’ı status kombinasyonlarından hesaplamaz.
- Available action’ları kendisi türetmez.
- Yetkiyi yalnız projection’a bırakmaz; Spring her mutation’da tekrar doğrular.

## 42. OpenAPI ana sözleşmesi

Spring–frontend public contract dosyası:

contracts/openapi/core-api-v1.yaml

olacaktır.

Başlangıç formatı:

OpenAPI 3.1

Her vertical slice başlamadan önce ilgili public API yüzeyi bu dosyada tasarlanır.

Bütün sistemin endpoint’leri baştan tanımlanmaz.

Yalnız aktif slice için gereken endpoint, schema ve error response’lar eklenir.

## 43. Hibrit OpenAPI işleyişi

Geliştirme sırası:

## 1. Slice kullanıcı akışı belirlenir.

2. Public endpoint ve schema OpenAPI’de tasarlanır.
3. Spring implementasyonu contract’a göre geliştirilir.
4. Spring runtime OpenAPI çıktısı üretir.
5. Tasarlanan spec ile runtime yüzeyi karşılaştırılır.
6. Frontend type/client generation committed spec üzerinden yapılır.
7. Gerçek frontend–Spring kabul testi çalıştırılır.

core-api-v1.yaml review edilen design contract’tır.

Spring runtime çıktısı implementation gerçeğini gösterir.

İki kaynak sessizce birbirinden uzaklaşamaz.

## 44. Frontend client üretimi

Frontend mümkün olduğunda OpenAPI’den aşağıdakileri üretir:

- TypeScript type’ları
- Request modelleri
- Response modelleri
- API client
- Error type foundation

Generated code manuel olarak rastgele değiştirilmez.

Custom frontend wrapper gerekiyorsa generated client’ın üzerinde ince bir katman olarak yazılır.

Frontend aynı public modeli ikinci kez elle tanımlamaz.

## 45. OpenAPI schema reuse

Tekrarlanan public tipler component schema olarak tanımlanır.

Örnek:

ProblemDetail FieldError Money PageMetadata DealSummary CorrelationId

Ancak aşırı generic schema üretilmez.

Her şeyi tek bir:

BaseResponse GenericEntity DynamicPayload

modeline sıkıştırmak yasaktır.

Business anlamı public schema isimlerinde görünür olmalıdır.

## 46. Error response dokümantasyonu

Her endpoint OpenAPI’de en az anlamlı hata response’larını tanımlar.

Örnek:

Her endpoint’in bütün olası global hataları tekrar tekrar aşırı ayrıntıyla yazılması zorunlu değildir.

Reusable Problem Details schema’ları kullanılabilir.

Business-specific code değerleri endpoint açıklamasında belirtilir.

## 47. Backward compatibility

Aynı /api/v1 içinde genel olarak kabul edilen additive değişiklikler:

- Optional response alanı ekleme
- Yeni endpoint ekleme
- Yeni optional request alanı ekleme
- Mevcut davranışı bozmayan metadata ekleme

Breaking değişiklik örnekleri:

- Alanı kaldırma
- Alan tipini değiştirme
- Required alan ekleme
- Enum anlamını değiştirme
- Aynı error code’un semantiğini değiştirme
- Pagination yapısını değiştirme
- Endpoint’in authorization anlamını değiştirme
- Money formatını değiştirme

Breaking değişiklikler açık migration veya yeni major version gerektirir.

## 48. Deprecation

Endpoint veya field kaldırılacaksa mümkün olduğunda önce deprecated olarak işaretlenir.

OpenAPI:

deprecated: true

kullanabilir.

Deprecation:

- Replacement yolunu açıklar.
- Sessiz kaldırma yapılmaz.
- Frontend aynı monorepo içinde olsa bile contract disiplini korunur.

Greenfield geliştirme sırasında henüz release edilmemiş slice’larda daha esnek değişiklik yapılabilir.

## 49. Content type

Public JSON API için varsayılan content type:

application/json

Problem response:

application/problem+json

Dosya binary içeriği Spring üzerinden zorunlu olarak proxy edilmez.

Dosya yükleme akışı presigned object storage yaklaşımına tabidir.

Upload metadata ve finalize endpoint’leri JSON kullanır.

## 50. Request boyutu ve validation

Her endpoint ihtiyaç duyduğu request boyutu sınırını belirleyebilir.

Büyük raw document veya video içeriği JSON body içinde taşınmaz.

Raw file:

```text
Frontend
→ presigned object storage upload
```

ile gönderilir.

Spring’e:

- Object reference
- File metadata
- SHA-256
- Upload completion bilgisi

gönderilir.

## 51. Security response prensipleri

Public API:

- Password hash döndürmez.
- Session ID döndürmez.
- CSRF token’ı yalnız ilgili security endpoint’i üzerinden verir.
- Internal role veya permission modelinin gereksiz ayrıntısını açmaz.
- Resource existence bilgisini authorization riskine göre korur.
- Stack trace ve SQL mesajı döndürmez.
- FastAPI provider/model internal metadata’sını business API’ye zorunlu olarak taşımaz.

## 52. Testing yaklaşımı

ADR-004’e uygun olarak public API standardının belirleyici testi frontend üzerinden yapılır.

Örnek:

```text
Frontend form
→ generated client
→ Spring API
→ PostgreSQL
→ public response
→ frontend state
```

Kod düzeyinde yalnız minimum ve kritik contract testleri tutulabilir:

- Problem Details serialization
- Money formatı
- Timestamp UTC formatı
- Pagination DTO stabilitesi
- Stale version conflict
- Idempotency key reuse
- Critical OpenAPI/runtime drift kontrolü

Her endpoint için geniş controller test paketi zorunlu değildir.

## 53. Yasaklanan yaklaşımlar

Bu ADR ile aşağıdaki yaklaşımlar yasaklanmıştır:

- Global success/data/message response envelope kullanmak
- JPA entity’yi public API’den döndürmek
- Spring PageImpl nesnesini doğrudan döndürmek
- Hata davranışını yalnız serbest metin mesajla tanımlamak
- Frontend’in detail metnine göre business logic yazması
- Para değerlerini float/double ile taşımak
- Yüzdeleri floating-point olarak taşımak
- Timestamp’leri local timezone ile rastgele döndürmek
- Collection alanını null döndürmek
- Frontend’in lifecycle veya available action hesaplaması
- Sınırsız generic filter dili açmak
- Database column isimlerini public sort/filter contract’ı yapmak
- Duplicate side-effect riski olan endpoint’lerde yalnız frontend button disable’a güvenmek
- Optimistic concurrency kontrolünü yalnız last-write-wins davranışına bırakmak
- Generated frontend modeli yerine paralel ve tahmini TypeScript modelleri oluşturmak
- Runtime Spring OpenAPI ile committed contract’ın sessizce ayrışmasına izin vermek
- Internal exception, SQL veya stack trace bilgisini public hata response’una koymak

## 54. Kabul edilen ana kararlar

| Success envelope | → Kullanılmayacak |
| --- | --- |
| Error format | → RFC 9457 Problem Details |
| Validation status | → 422 |
| Pagination | → 0-indexed page/size |
| Default page size | → 20 |
| Maximum page size | → 100 |
| Sorting | → Allowlist tabanlı |
| Filtering | → Endpoint-specific |
| Optimistic concurrency | → expectedVersion |
| Stale version status | → 409 |
| Idempotency | → Riskli side-effect endpoint’lerinde header tabanlı |
| Timestamp | → RFC 3339 UTC, Z suffix |
| Money | → amountMinor + currency |
| Percentage | → basis points |
| UUID | → String UUID |
| Enum | → UPPER_SNAKE_CASE |
| Correlation | → X-Correlation-ID |
| Async operations | → 202 Accepted + Location |
| OpenAPI | → Slice bazlı hibrit OpenAPI 3.1 |

## 55. Sonuçlar

Olumlu sonuçlar:

- Frontend ve Spring arasında stabil bir public sınır oluşur.
- Generated TypeScript client güvenilir hâle gelir.
- Hata yönetimi endpoint’ler arasında tutarlı olur.
- Business conflict ve validation hataları ayrılır.
- Para ve percentage hesaplarında precision riski azalır.
- Concurrent update kayıpları önlenir.
- Duplicate payment veya job işlemleri idempotency ile korunur.
- Runtime ve design contract sapmaları erken görülür.
- Frontend business state’i tahmin etmek yerine Spring projection’larını kullanır.

Maliyetler:

- OpenAPI’nin her slice’ta güncel tutulması gerekir.
- Problem Details ve error-code katalog disiplini gerekir.
- Idempotency storage ve request hashing mekanizması gerekir.
- expectedVersion frontend request’lerine taşınmalıdır.
- Runtime OpenAPI ile committed spec karşılaştırma mekanizması gerekir.
- Public DTO ve internal domain modeli ayrı tutulmalıdır.

Bu maliyetler API stabilitesi, frontend entegrasyonu ve güvenli business operation’lar için kabul edilmiştir.

## 56. Nihai karar

M4Trust public Spring API:

- /api/v1 path versioning kullanacaktır.
- Çoğul ve kebab-case resource path’leri kullanacaktır.
- JSON alanlarında lowerCamelCase kullanacaktır.
- Global success envelope kullanmayacaktır.
- Stabil public DTO’lar döndürecektir.
- RFC 9457 Problem Details tabanlı hata modeli kullanacaktır.
- Machine-readable stabil error code’lar sağlayacaktır.
- Field validation hatalarında errors array’i kullanacaktır.
- Validation için 422, business conflict için 409 kullanacaktır.
- Page-based pagination’ı sıfır tabanlı olarak uygulayacaktır.
- Maximum page size’ı 100 ile sınırlandıracaktır.
- Allowlist tabanlı sorting ve endpoint-specific filtering kullanacaktır.
- Optimistic concurrency için expectedVersion kullanacaktır.
- Duplicate side-effect riski olan endpoint’lerde Idempotency-Key destekleyecektir.
- Tarihleri RFC 3339 UTC formatında döndürecektir.
- Parayı minor unit ve currency ile taşıyacaktır.
- Yüzdeleri basis point olarak taşıyacaktır.
- UUID identifier’lar ve UPPER_SNAKE_CASE enum değerleri kullanacaktır.
- Her request ve response’ta correlation ID yönetimini destekleyecektir.
- Uzun işlemleri 202 Accepted ile asenkron başlatacaktır.
- Public OpenAPI contract’ını contracts/openapi/core-api-v1.yaml altında OpenAPI 3.1
olarak yönetecektir.

- Spring runtime API’si ile committed OpenAPI contract’ının uyumunu koruyacaktır.
- Frontend client ve type’larını mümkün olduğunda committed OpenAPI contract’ından üretecektir.
