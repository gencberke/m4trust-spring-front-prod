# ADR-003: Core Domain Model and Deal Lifecycle

**Durum:** Accepted  
**Tarih:** 14 Temmuz 2026  
**Karar sahipleri:** M4Trust mimari ekibi  
**Kapsam:** Spring Boot Core Platform domain modeli, modül sınırları, aggregate yapısı, durum makineleri ve business validation  
**Bağlı kararlar:**

- ADR-001: System Boundaries and Data Ownership
- ADR-002: Spring–AI Contract and Compatibility Policy

**Sonraki karar:** ADR-004: Vertical Slice Delivery and Acceptance Testing

## 1. Bağlam

M4Trust, B2B sözleşmelerinin oluşturulması, analiz edilmesi, taraflarca onaylanması, fonlanması, yerine getirilmesi, kanıtlanması ve sonuçlandırılması süreçlerini yöneten bir platformdur.

Sistem sıfırdan geliştirilmektedir. Mevcut sistemin veri modeli, API yapısı veya teknik mimarisi yeni sistem için compatibility zorunluluğu oluşturmaz.

ADR-001 ile:

- Spring Boot Core Platform business authority olarak,
- PostgreSQL business source of truth olarak,
- FastAPI ise internal AI capability service olarak

belirlenmiştir.

ADR-002 ile Spring ve FastAPI arasındaki versioned contract’lar kesinleştirilmiştir.

Spring Boot geliştirmesine başlanmadan önce aşağıdaki konuların kesinleştirilmesi gerekir:

- Ana business aggregate’ın adı
- Spring modüllerinin sınırları
- Aggregate root’lar
- Deal yaşam döngüsü
- Business state’in nasıl temsil edileceği
- AI sonucunun business verisine nasıl dönüşeceği
- Validation sorumlulukları
- Transaction ve event sınırları
- Modüller arası iletişim kuralları

## 2. Ana business kavramı

Sistemin ana business kavramının adı:

**Deal**

olacaktır.

Transaction adı ana aggregate için kullanılmayacaktır.

Bunun nedenleri:

- Database transaction kavramıyla karışması
- Payment transaction kavramıyla karışması
- Ticari ilişkinin yalnız finansal hareketten ibaret olmaması
- Deal’in sözleşme, taraflar, kurallar, onay, fonlama, teslimat ve settlement süreçlerinin tamamını temsil etmesi

Transaction kelimesi yalnız teknik database transaction’ları veya açıkça adlandırılmış ödeme

kayıtlarında kullanılabilir.

Örnek:

- `Deal`
- `PaymentOperation`
- `ProviderTransactionReference`
- `DatabaseTransaction`

Ana aggregate root:

**Deal**

olacaktır.

## 3. Mimari yaklaşım

Spring Boot Core Platform, başlangıçta tek deploy edilen bir:

**Modular Monolith**

olarak geliştirilecektir.

İlk aşamada business modülleri ayrı mikroservislere bölünmeyecektir.

Her modül:

- Açık bir domain sorumluluğuna sahip olur.
- Kendi application servislerine sahip olur.
- Kendi repository interface’lerini yönetir.
- Kendi database tablolarının mantıksal sahibidir.
- Diğer modüllerin repository’lerine doğrudan erişmez.
- Diğer modüllerin internal entity sınıflarını kullanmaz.
- İletişimi application port’ları veya internal domain event’ler üzerinden gerçekleştirir.

Modular monolith sınırları, ileride gerekli olduğunda servis ayrıştırmasına uygun olacak şekilde korunacaktır.

Servis ayrıştırması yalnız aşağıdaki ihtiyaçlardan biri kanıtlandığında değerlendirilir:

- Bağımsız ölçekleme
- Farklı güvenlik sınırı
- Farklı veri sahipliği
- Bağımsız deployment gereksinimi
- Farklı operasyonel yaşam döngüsü

## 4. Spring modülleri

İlk domain modülleri aşağıdaki gibidir:

- `identity`
- `organization`
- `deal`
- `document`
- `contractintelligence`
- `ratification`
- `fulfillment`
- `payment`
- `casework`
- `audit`
- `integration`
- `sharedkernel`

### 4.1 identity

Sorumlulukları:

- Kullanıcı hesabı
- Kimlik doğrulama
- Session veya access credential yönetimi
- Password hashing
- Aktif kullanıcı bağlamı
- Kullanıcı güvenlik durumu

identity, legal entity üyeliğinin sahibi değildir.

### 4.2 organization

Sorumlulukları:

- Tenant
- Legal entity
- Legal entity membership
- Kullanıcının legal entity içindeki rolü
- Organizasyon yetkileri
- Kullanıcının aktif tenant ve legal entity bağlamı

### 4.3 deal

Sorumlulukları:

- Deal oluşturma
- Deal temel bilgileri
- Buyer ve seller atamaları
- Deal participant ilişkileri
- Davetler
- Ana deal yaşam döngüsü
- Deal-level erişim kuralları
- Deal lifecycle projection

### 4.4 document

Sorumlulukları:

- Doküman metadata
- Object storage key
- Dosya hash’i
- Media type
- Dosya versiyonu
- Upload durumu
- Deal ile doküman ilişkisi
- Dokümanın değiştirilmesi veya supersede edilmesi

Binary içerik PostgreSQL’de tutulmayacaktır.

### 4.5 contractintelligence

Sorumlulukları:

- AI job business kaydı
- AI sonucunun Spring tarafındaki canonical kopyası
- Contract validation
- Business acceptance validation
- Extraction result version
- Manual review
- Rule-set draft
- Immutable rule-set version
- AI sonucu ile business state arasındaki dönüşüm

### 4.6 ratification

Sorumlulukları:

- Ratification hazırlığı
- Canonical ratification package
- Package hash
- Taraf onayları
- Ratification durumu
- Rejection ve expiry
- Yeni versiyon için yeniden ratification

### 4.7 fulfillment

Sorumlulukları:

- Milestone
- Teslimat gereksinimleri
- Evidence submission
- Evidence değerlendirmesi
- Fulfillment durumu
- Video analiz sonuçlarının advisory değerlendirmesi

### 4.8 payment

Sorumlulukları:

- Funding plan
- Funding unit
- Provider payment operation
- Provider idempotency
- Reconciliation
- Settlement eligibility
- Release instruction
- Refund veya reversal süreçleri

AI servisi bu modül üzerinde hiçbir mutation yetkisine sahip değildir.

### 4.9 casework

Sorumlulukları:

- Manual case
- Dispute
- Review assignment
- Case comment ve karar kayıtları
- Dispute resolution
- Operasyonel insan müdahaleleri

### 4.10 audit

Sorumlulukları:

- Append-only business audit
- Kim, ne zaman, hangi değişikliği yaptı
- Önceki ve sonraki business referansları
- Correlation ve causation kimlikleri

Audit kaydı yalnız loglama olarak değerlendirilmez. Business işlemle aynı database transaction’ında yazılır.

### 4.11 integration

Sorumlulukları:

- RabbitMQ adapter’ları
- Transactional outbox
- Consumer inbox
- Object storage adapter’ları
- FastAPI mesajlaşması
- Payment provider adapter’ları
- External retry ve reconciliation mekanizmaları

integration modülü business karar vermez.

### 4.12 sharedkernel

Yalnız gerçekten ortak ve stabil kavramları içerir:

- Aggregate ID tipleri
- Money
- Currency
- Basis points
- Domain event interface
- Clock abstraction
- Correlation context
- Temel domain exception’ları

sharedkernel, genel yardımcı sınıfların atıldığı kontrolsüz bir klasöre dönüşmeyecektir.

Business entity veya modüle özel domain kuralları sharedkernel içine taşınmayacaktır.

## 5. Multi-tenancy ve business actor ayrımı

Tenant teknik veri izolasyonu ve platform hesabı sınırıdır.

LegalEntity sözleşmeye taraf olabilen gerçek business aktördür.

Bu iki kavram birbirinin yerine kullanılmayacaktır.

Her mutable business aggregate en az aşağıdaki bilgiyi taşır:

**tenantId**

Deal, bir hosting veya initiating tenant altında oluşturulur.

Ancak bir Deal farklı tenant’lara bağlı legal entity’leri içerebilir.

Bu nedenle:

- `tenantId ≠ buyer legal entity`
- `tenantId ≠ seller legal entity`

Deal erişimi yalnız tenantId filtresine göre belirlenmez.

Yetkilendirme aşağıdaki ilişkiler üzerinden yapılır:

- User membership
- Legal entity membership
- Deal party assignment
- Deal participant assignment
- Kullanıcının ilgili operation için rolü

Bir tenant, yalnız başka bir tenant’a ait olduğu için Deal verisine erişemez. Erişim açık Deal participation ilişkisi gerektirir.

## 6. Aggregate root’lar

İlk aggregate root adayları aşağıdaki gibidir:

- `User`
- `Tenant`
- `LegalEntity`
- `Deal`
- `DealInvitation`
- `Document`
- `AiJob`
- `ExtractionResultVersion`
- `RuleSetVersion`
- `RatificationPackage`
- `FundingPlan`
- `PaymentOperation`
- `Milestone`
- `EvidenceSubmission`
- `Dispute`

Bu liste fiziksel tablo listesi değildir.

Aggregate root sınırları business tutarlılık ve mutation sınırlarını ifade eder.

## 7. Deal aggregate

Deal sistemin ana aggregate root’udur.

Deal en az aşağıdaki bilgileri yönetir:

**dealId**

- `tenantId`
- `reference`
- `title`
- `description`
- `dealStatus`
- `buyerLegalEntityId`
- `sellerLegalEntityId`
- `currentDocumentId`
- `currentAcceptedExtractionId`
- `currentRuleSetVersionId`
- `currentRatificationPackageId`
- `createdBy`
- `createdAt`
- `updatedAt`
- `version`

Deal içindeki bütün alanların ilk günden aynı tabloda tutulması zorunlu değildir.

### 7.1 Deal temel invariant’ları

- Bir Deal’in tek bir kimliği vardır.
- Aynı legal entity hem buyer hem seller olamaz.
- Buyer ve seller atamaları açık role sahiptir.
- Deal iptal edildikten sonra yeni business işlem başlatılamaz.
- Deal tamamlandıktan sonra mutable business state doğrudan değiştirilemez.
- Arşivleme business geçmişini silmez.
- Current document, extraction, rule-set ve ratification referansları yalnız geçerli versiyonlara işaret eder.
- AI sonucu doğrudan Deal state’ini değiştiremez.
- Deal aggregate ödeme provider’ına doğrudan çağrı yapmaz.

### 7.2 Generic soft delete kullanılmayacaktır

Business kayıtlarında genel amaçlı:

**deleted = true**

yaklaşımı varsayılan çözüm olmayacaktır.

Bunun yerine açık domain durumları kullanılacaktır:

- `CANCELLED`
- `ARCHIVED`
- `SUPERSEDED`
- `WITHDRAWN`

Yasal, finansal ve audit geçmişi fiziksel olarak silinmeyecektir.

## 8. Deal state modeli

Sistem bütün süreci tek bir devasa status alanında temsil etmeyecektir.

Aşağıdaki bağımsız durum makineleri kullanılacaktır:

- `DealStatus`
- `AnalysisStatus`
- `RatificationStatus`
- `FundingStatus`
- `FulfillmentStatus`
- `SettlementStatus`
- `DisputeStatus`

Her durum kendi domain sahibi tarafından yönetilir.

## 9. DealStatus

DealStatus, Deal container’ının genel yaşam durumunu ifade eder.

İlk değerler:

- `DRAFT`
- `ACTIVE`
- `CANCELLED`
- `COMPLETED`
- `ARCHIVED`

### Geçişler

**DRAFT → ACTIVE**

**DRAFT → CANCELLED**

**ACTIVE → CANCELLED**

**ACTIVE → COMPLETED**

**COMPLETED → ARCHIVED**

**CANCELLED → ARCHIVED**

Aşağıdaki geçişler yasaktır:

**CANCELLED → ACTIVE**

**COMPLETED → ACTIVE**

ARCHIVED → herhangi bir mutable durum

ACTIVE, bütün alt süreçlerin tamamlandığı anlamına gelmez. Deal üzerinde aktif business süreç

bulunduğunu belirtir.

## 10. AnalysisStatus

İlk değerler:

- `NOT_REQUESTED`
- `QUEUED`
- `PROCESSING`
- `REVIEW_REQUIRED`
- `ACCEPTED`
- `FAILED`
- `SUPERSEDED`

Temel akış:

**NOT_REQUESTED**

**→ QUEUED**

**→ PROCESSING**

**→ ACCEPTED**

Alternatif akışlar:

**PROCESSING → REVIEW_REQUIRED**

**PROCESSING → FAILED**

**REVIEW_REQUIRED → ACCEPTED**

**REVIEW_REQUIRED → SUPERSEDED**

**FAILED → QUEUED**

**ACCEPTED → SUPERSEDED**

Yeni bir doküman versiyonu yüklendiğinde önceki geçerli extraction sonucu:

**SUPERSEDED**

olarak işaretlenebilir.

FAILED, Deal’in business olarak reddedildiği anlamına gelmez. Yalnız ilgili AI job veya analiz

denemesinin tamamlanamadığını belirtir.

## 11. RatificationStatus

İlk değerler:

- `NOT_READY`
- `READY`
- `PENDING`
- `RATIFIED`
- `REJECTED`
- `EXPIRED`
- `SUPERSEDED`

Temel akış:

**NOT_READY**

**→ READY**

**→ PENDING**

**→ RATIFIED**

Alternatif akışlar:

**PENDING → REJECTED**

**PENDING → EXPIRED**

**READY → SUPERSEDED**

**PENDING → SUPERSEDED**

**RATIFIED → SUPERSEDED**

Ratified package doğrudan değiştirilemez.

Ratified içerikte değişiklik yapılması gerektiğinde:

- `1.`
- `2.`
- `3.`
- `4.`

Yeni rule-set version oluşturulur. Yeni ratification package oluşturulur. Eski package superseded olarak işaretlenir. Yeni ratification süreci başlatılır.

## 12. FundingStatus

İlk değerler:

- `NOT_CONFIGURED`
- `PLANNED`
- `PENDING`
- `PARTIALLY_FUNDED`
- `FUNDED`
- `FAILED`
- `CANCELLED`

Temel akış:

**NOT_CONFIGURED**

**→ PLANNED**

**→ PENDING**

**→ FUNDED**

Birden fazla funding unit bulunuyorsa:

PENDING → PARTIALLY_FUNDED → FUNDED

Provider timeout veya belirsiz sonuç doğrudan:

**FAILED**

olarak yorumlanmayacaktır.

Provider sonucu bilinmiyorsa operasyon:

**UNKNOWN**

provider outcome ile tutulur ve reconciliation yapılır.

FundingStatus, provider operasyon durumlarının business projection’ıdır.

## 13. FulfillmentStatus

İlk değerler:

- `NOT_STARTED`
- `IN_PROGRESS`
- `EVIDENCE_REQUIRED`
- `REVIEW_REQUIRED`
- `COMPLETED`
- `CANCELLED`

Temel akış:

**NOT_STARTED**

**→ IN_PROGRESS**

**→ EVIDENCE_REQUIRED**

**→ COMPLETED**

Alternatif akış:

**EVIDENCE_REQUIRED → REVIEW_REQUIRED**

**REVIEW_REQUIRED → COMPLETED**

AI veya video analizi tek başına COMPLETED durumuna geçiş yaptıramaz.

## 14. SettlementStatus

İlk değerler:

- `NOT_READY`
- `READY`
- `PROCESSING`
- `ON_HOLD`
- `SETTLED`
- `FAILED`
- `CANCELLED`

Temel akış:

**NOT_READY**

**→ READY**

**→ PROCESSING**

**→ SETTLED**

Alternatif akışlar:

**READY → ON_HOLD**

**PROCESSING → ON_HOLD**

**PROCESSING → FAILED**

**ON_HOLD → READY**

Settlement eligibility yalnız Spring business kuralları tarafından hesaplanır.

FastAPI:

- Settlement başlatamaz.
- Payment release yapamaz.
- Settlement status değiştiremez.

## 15. DisputeStatus

İlk değerler:

- `NONE`
- `OPEN`
- `UNDER_REVIEW`
- `RESOLVED`
- `WITHDRAWN`

Temel akış:

NONE → OPEN → UNDER_REVIEW → RESOLVED

Alternatif akış:

**OPEN → WITHDRAWN**

**UNDER_REVIEW → WITHDRAWN**

Aktif dispute, settlement veya release süreçlerini hold durumuna geçirebilir.

Bu karar Spring tarafından verilir.

Video veya AI sonucu otomatik dispute açamaz. Yalnız review sinyali üretebilir.

## 16. DealLifecycleProjection

Frontend ve operasyonel ekranlar için genel bir Deal aşaması gereklidir.

Bu amaçla ayrı bir projection üretilecektir:

**DealLifecycleProjection**

İlk projection değerleri:

**DRAFT**

- `CONTRACT_ANALYSIS`
- `MANUAL_REVIEW`
- `RATIFICATION`
- `FUNDING`
- `FULFILLMENT`
- `SETTLEMENT`
- `DISPUTE`
- `COMPLETED`
- `CANCELLED`
- `ARCHIVED`

Bu alan authoritative mutable state değildir.

Aşağıdaki status’lardan türetilir:

- `DealStatus`
- `AnalysisStatus`
- `RatificationStatus`
- `FundingStatus`
- `FulfillmentStatus`
- `SettlementStatus`
- `DisputeStatus`

Projection’ın kendi başına business mutation yetkisi yoktur.

Örnek öncelik:

**1.**

ARCHIVED

**2.**

CANCELLED

**3.**

Aktif dispute varsa DISPUTE

**4.**

Settlement tamamlandıysa COMPLETED

**5.**

Settlement aktifse SETTLEMENT

**6.**

Fulfillment aktifse FULFILLMENT

**7.**

Funding tamamlanmadıysa FUNDING

**8.**

Ratification tamamlanmadıysa RATIFICATION

**9.**

Manual review gerekiyorsa MANUAL_REVIEW

**10.**

Analysis tamamlanmadıysa CONTRACT_ANALYSIS

**11.**

Aksi hâlde DRAFT

Exact projection algoritması application kodunda merkezi olarak tutulacaktır.

Frontend farklı status alanlarından kendi yaşam döngüsünü uydurmayacaktır.

## 17. AI sonucu işleme modeli

FastAPI’den gelen completed event yalnız teknik olarak başarılı AI işlemini ifade eder.

Bu event:

- Business kabul anlamına gelmez.
- Rule-set’in kesinleştiği anlamına gelmez.
- Deal state’inin değişmesi gerektiği anlamına gelmez.
- Ratification başlatma yetkisi vermez.
- Payment veya settlement kararı vermez.

Spring AI sonucu işleme sırası:

**RabbitMQ event**

**→ Inbox idempotency kontrolü**

**→ Contract validation**

→ Job ve input hash doğrulaması

**→ Job lifecycle kontrolü**

**→ Canonical result kaydı**

**→ Business acceptance validation**

→ Manual review veya accepted extraction

**→ Rule-set draft/version üretimi**

Geç, iptal edilmiş veya superseded sonuçlar audit amacıyla kaydedilebilir fakat current business state’e uygulanmaz.

## 18. Validation sahipliği

Validation üç ayrı seviyede uygulanacaktır.

### 18.1 FastAPI AI Output Validation

FastAPI’nin sorumluluğu:

- Model-native cevabı parse etmek
- Canonical M4Trust sonucuna dönüştürmek
- JSON Schema doğrulaması
- Confidence sınırları
- Source reference yapısı
- AI çıktısının iç tutarlılığı
- Düşük kalite ve eksik alan warning’leri
- Model veya pipeline teknik hataları

FastAPI’nin “valid” sonucu business kabul anlamına gelmez.

### 18.2 Spring Contract Validation

Spring’in integration boundary sorumluluğu:

- Event envelope validation
- Exact schema version
- Event type ve job type
- UUID ve UTC timestamp formatları
- Canonical payload şekli
- Error contract
- Duplicate event
- Input hash ve subject eşleşmesi

Contract validation başarısızlığı business rejection değildir.

Bu durum integration veya contract violation olarak kaydedilir.

### 18.3 Spring Business Acceptance Validation

Spring’in business sorumluluğu:

- Deal mevcut mu?
- Deal ilgili tenant ve taraflarla uyumlu mu?
- Job hâlâ current mı?
- Doküman superseded olmuş mu?
- Buyer ve seller geçerli mi?
- Aynı legal entity iki taraf olmuş mu?
- Kullanıcı veya taraf yetkili mi?
- Para ve oran invariant’ları sağlanıyor mu?
- Rule-set business açısından uygulanabilir mi?
- Manual review gerekiyor mu?
- Deal state bu sonucu kabul etmeye uygun mu?
- Ratification veya settlement açısından engel var mı?

Business acceptance kararının sahibi yalnız Spring’dir.

## 19. Extraction ve rule-set versioning

AI sonuçları mutable tek bir kayıt olarak tutulmayacaktır.

Her kabul edilebilir sonuç versioned olarak saklanır:

**ExtractionResultVersion**

Bir extraction üzerinde manual review düzeltmeleri yapılabilir.

Ancak kabul edilmiş ve rule-set’e dönüştürülmüş sonuç geçmişten silinmez.

Business kuralları versioned olarak saklanır:

**RuleSetVersion**

Her RuleSetVersion:

- Immutable olur.
- Kaynak extraction version’ına referans verir.
- Oluşturan kullanıcı veya süreç bilgisini taşır.
- Oluşturulma zamanını taşır.
- Önceki version’a referans verebilir.
- Canonical hash taşıyabilir.

Current rule-set, Deal üzerinde yalnız bir pointer olarak tutulur.

Geçmiş rule-set version’ları değiştirilmez.

## 20. Ratification package invariant’ları

RatificationPackage aşağıdakilerin canonical snapshot’ını içerir:

- Deal kimliği
- Buyer ve seller bilgileri
- Kabul edilmiş rule-set version
- Tracking policy
- Funding veya fulfillment açısından gerekli contractual bilgiler
- Package schema version
- Canonical content hash

Not: "Tracking policy" kavramının sahibi, modülü ve veri yapısı henüz tanımlanmamıştır.
Ratification slice'ına başlamadan önce ayrı bir tasarım dokümanı veya ADR ile
kesinleştirilmelidir; bu tanım yapılmadan tracking policy alanı package snapshot'ına
implemente edilmez.

Package oluşturulduktan sonra immutable olur.

Package üzerinde değişiklik yapılmaz.

Değişiklik gerektiğinde yeni package version oluşturulur.

Party approvals append-only tutulur.

Ratification package hash’i, tarafların onayladığı içeriğin sonradan değişmediğini kanıtlamak için kullanılır.

## 21. Funding ve payment temel invariant’ları

- Para değerleri integer minor unit olarak tutulur.
- Currency açık şekilde saklanır.
- Percentage değerleri basis points olarak tutulur.
- Floating-point para hesabı kullanılmaz.
- Bir funding unit, kendi payment operasyonlarıyla izlenir.
- Provider idempotency key zorunludur.
- Aynı provider request’in yeniden gönderilmesi duplicate para hareketi oluşturmamalıdır.
- Provider timeout failure olarak kabul edilmez.
- Bilinmeyen provider sonucu reconciliation gerektirir.
- External provider çağrısı sırasında database transaction açık tutulmaz.
- Refund, reversal veya payment undo açık business operation olarak modellenir.
- AI sonucu doğrudan payment operation oluşturamaz.

Exact payment workflow ayrı bir ADR veya payment design dokümanında detaylandırılacaktır.

## 22. Evidence ve video analysis temel invariant’ları

- Evidence first-class business kaydıdır.
- Evidence ilgili Deal ve milestone’a bağlanır.
- Evidence binary içeriği object storage’da tutulur.
- Spring evidence metadata ve business durumunun sahibidir.
- Video analysis sonucu advisory niteliktedir.
- Video sonucu otomatik fulfillment completion oluşturamaz.
- Video sonucu otomatik dispute açamaz.
- Video sonucu otomatik payment release oluşturamaz.
- Düşük confidence veya anomaly human review oluşturabilir.
- Contractual evidence gereksinimi platform tercihlerinden üstündür.

## 23. Repository ve modül erişim kuralları

Her aggregate root için owning module içinde repository bulunur.

Örnek:

- `DealRepository`
- `DocumentRepository`
- `RuleSetVersionRepository`
- `RatificationPackageRepository`
- `PaymentOperationRepository`

Aşağıdaki davranış yasaktır:

payment → deal JPA repository doğrudan kullanımı

contractintelligence → organization tablolarına raw SQL

fulfillment → payment entity sınıfları

deal → integration provider implementation

Modüller arası erişim:

- Application service port’u
- Stable identifier
- Internal domain event
- Read-only projection

üzerinden yapılır.

JPA entity’leri modüller arası public contract olarak kullanılmaz.

Modüller birbirine entity graph vermek yerine ID veya açık application DTO kullanır.

## 24. Transaction sınırları

Bir kullanıcı veya sistem command’i, Spring içinde açık bir application transaction sınırına sahip olur.

Temel kural:

- `Business mutation`
- `+ audit record`
- `+ outbox event`
- `= aynı PostgreSQL transaction`

Inbound mesaj işleme kuralı:

- `Inbox duplicate kaydı`
- `+ business mutation`
- `+ audit`
- `+ gerekli outbox`
- `= aynı PostgreSQL transaction`

Aşağıdaki işlemler boyunca database transaction açık tutulmayacaktır:

- FastAPI çağrısı
- RabbitMQ publish sonucu bekleme
- Object storage upload/download
- Payment provider çağrısı
- Email gönderme
- External webhook çağrısı

External işlem sonucu ayrı command veya event ile işlenir.

Bir operation birden fazla aggregate’ı değiştirecekse:

- Bir primary aggregate belirlenir.
- Cross-aggregate invariant açıkça belgelenir.
- Gerekli kayıtlar deterministik sırayla lock edilir.
- Operation mümkün olduğunca küçük tutulur.
- Uzun süren süreçler process manager veya state machine ile yürütülür.

## 25. Concurrency

Mutable aggregate’larda optimistic locking kullanılacaktır.

Her mutable aggregate en az:

**version**

alanına sahip olur.

Concurrent mutation durumunda sessiz last-write-wins uygulanmaz.

Conflict:

- Açık hata olarak döndürülür veya
- Güvenli biçimde yeniden denenir.

Payment, funding, ratification ve settlement gibi kritik alanlarda gerektiğinde pessimistic row locking kullanılabilir.

FOR UPDATE veya SKIP LOCKED kullanımı application ihtiyacına göre açıkça sınırlandırılır.

## 26. Domain event ve outbox politikası

Sistem full event sourcing kullanmayacaktır.

Authoritative business state PostgreSQL relational tablolarında tutulacaktır.

Aşağıdaki yapı kullanılacaktır:

- `Current relational state`
- `+ immutable version/history records`
- `+ domain events`
- `+ transactional outbox`
- `+ append-only audit`

Internal domain event’ler modüller arası gevşek iletişim için kullanılabilir.

External servisleri ilgilendiren event’ler outbox üzerinden yayınlanır.

Database’e yazılıp broker’a yayınlanamayan event kaybolmamalıdır.

Outbox publisher:

- Retry edilebilir.
- Duplicate publish yapabilir.
- Consumer’lar idempotent olmak zorundadır.

Exactly-once delivery varsayılmaz.

## 27. Veri tipi kararları

PostgreSQL tarafında başlangıç prensipleri:

**ID                 → UUID**

**Timestamp          → timestamptz**

Money → bigint minor units + currency

Percentage → integer basis points

Optimistic lock → bigint/integer version

Flexible metadata → JSONB yalnız kontrollü alanlarda

JSONB aşağıdakilerin yerine kullanılmayacaktır:

- Query edilmesi gereken temel domain alanları
- Money
- Status
- Party relationships
- Payment state
- Authorization
- Ratification state

JSONB yalnız:

- Canonical immutable snapshot
- Technical metadata
- Provider-safe metadata
- Versioned transport snapshot

gibi alanlarda kullanılabilir.

## 28. Authorization bağlamı

Her mutating operation en az aşağıdaki bağlamla değerlendirilir:

- `authenticatedUserId`
- `tenantId`
- `activeLegalEntityId`
- `dealId`
- `requestedOperation`

Controller seviyesindeki authentication tek başına yeterli değildir.

Application service, kullanıcının ilgili Deal ve legal entity adına işlem yapma yetkisini doğrular.

Domain object’leri HTTP session veya security framework nesnelerine doğrudan bağımlı olmaz.

## 29. Frontend ilişkisi

Frontend authoritative business state üretmez.

Frontend:

- Deal lifecycle stage’i kendi kurallarıyla hesaplamaz.
- Farklı status alanlarından bağımsız sonuç çıkarmaya çalışmaz.
- AI sonucunu business kabul edilmiş gibi göstermez.
- Payment eligibility hesaplamaz.
- Ratification geçerliliğini kendi başına belirlemez.

Spring gerekli projection ve action availability bilgisini public API üzerinden sağlar.

Frontend ve backend’in modül modül birlikte geliştirilmesi, gerçek API üzerinden tarayıcı kabul testleri ve vertical slice tamamlanma kriterleri ADR-004 kapsamında kesinleştirilecektir.

## 30. İlk geliştirme sırası

ADR-003 sonrasında önerilen domain geliştirme sırası:

- `1. Platform foundation`
- `2. Identity ve authentication`
- `3. Tenant, legal entity ve membership`
- `4. Deal oluşturma ve listeleme`
- `5. Buyer/seller ve participant yönetimi`
- `6. Document upload`
- `7. AI job ve fake extraction`
- `8. Business acceptance validation`
- `9. Manual review`
- `10. RuleSetVersion`
- `11. RatificationPackage`
- `12. Funding ve payment`
- `13. Fulfillment ve evidence`
- `14. Video analysis`
- `15. Dispute ve settlement`

Her modülün frontend ile birlikte nasıl kabul edileceği ADR-004 ile belirlenecektir.

## 31. Yasaklanan yaklaşımlar

Bu ADR ile aşağıdaki yaklaşımlar yasaklanmıştır:

- Ana aggregate’a Transaction adı verilmesi
- Bütün yaşam döngüsünün tek devasa status alanında tutulması
- Frontend’in lifecycle stage hesaplaması
- AI sonucunun doğrudan Deal state’ine uygulanması
- FastAPI’nin business acceptance kararı vermesi
- FastAPI’nin payment veya settlement mutation’ı yapması
- Modüllerin birbirlerinin repository’lerini doğrudan kullanması
- JPA entity’lerinin modüller arası contract olarak paylaşılması
- Generic soft delete’in her business kayda uygulanması
- Floating-point para kullanılması
- External çağrı boyunca PostgreSQL transaction açık tutulması
- Full event sourcing ile başlanması
- Provider timeout’un otomatik failure kabul edilmesi
- Ratified package’ın mutation ile güncellenmesi
- Audit ve outbox’ın business mutation’dan ayrı transaction’da yazılması

## 32. Sonuçlar

Olumlu sonuçlar:

- Ana business kavramı netleşir.
- Spring modülleri açık sorumluluklara ayrılır.
- Mikroservis maliyeti alınmadan modül sınırları korunur.
- AI ile business validation birbirinden ayrılır.
- Deal yaşam döngüsü tek status karmaşasına dönüşmez.
- Frontend için güvenilir lifecycle projection üretilir.
- Rule-set ve ratification geçmişi değiştirilemez biçimde saklanır.
- Payment ve settlement kararları Spring kontrolünde kalır.
- Outbox ve idempotency güvenilirliği destekler.
- Modüller vertical slice yöntemiyle sırayla geliştirilebilir.

Maliyetler:

- Birden fazla status alanı ve projection gerekir.
- Modül sınırlarının korunması ek disiplin gerektirir.
- Versioned kayıtlar daha fazla tablo ve storage oluşturur.
- Outbox, inbox ve audit ek altyapı gerektirir.
- Cross-module işlemler application orchestration gerektirir.
- Frontend’e action availability ve projection sunulması gerekir.

Bu maliyetler sistemin güvenilirliği, açıklığı ve uzun vadeli değiştirilebilirliği için kabul edilmiştir.

## 33. Nihai karar

M4Trust Core Platform:

- Spring Boot modular monolith olarak başlayacaktır.
- Ana business aggregate için Deal adını kullanacaktır.
- Business yaşam döngüsünü bağımsız durum makineleriyle yönetecektir.
- Frontend için türetilmiş DealLifecycleProjection sunacaktır.
- FastAPI sonucunu önce contract, sonra business acceptance validation’dan geçirecektir.
- AI çıktısını business kararı olarak kabul etmeyecektir.
- Immutable extraction, rule-set ve ratification version’ları kullanacaktır.
- Payment ve settlement otoritesini Spring’de tutacaktır.
- Business mutation, audit ve outbox kaydını aynı PostgreSQL transaction’ında yapacaktır.
- Full event sourcing kullanmayacaktır.
- Modüller arası repository ve entity paylaşımına izin vermeyecektir.
- Frontend ve backend geliştirmesini ADR-004 ile tanımlanacak vertical slice yöntemiyle ilerletecektir.
