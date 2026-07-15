# ADR-001: System Boundaries and Data Ownership

- Durum: Accepted
- Tarih: 13 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Spring Boot Core Platform, FastAPI AI Service, frontend ve ortak altyapı
- İlgili kararlar: ADR-002 Spring–AI Contract and Compatibility Policy

## 1. Bağlam
M4Trust sistemi sıfırdan geliştirilecektir. Mevcut FastAPI, frontend, veri modeli veya API davranışları yeni
sistem için teknik bağımlılık oluşturmaz. Mevcut repository yalnızca domain bilgisinin, iş akışlarının ve
geçmiş tasarım deneyimlerinin referans kaynağıdır.

Yeni sistem üç temel uygulama katmanından oluşacaktır:

1. Spring Boot Core Platform
2. FastAPI AI Service
3. Web frontend

Spring Boot ve FastAPI farklı geliştiriciler tarafından geliştirilecek ve birbirlerinden bağımsız olarak
değiştirilebilmelidir.

FastAPI AI Service içindeki:

- LLM modeli,
- model sağlayıcısı,
- prompt yapısı,
- embedding modeli,
- RAG yöntemi,
- OCR veya document parser,
- video analiz modeli,
- pipeline implementasyonu

zaman içinde değişebilir.

Bu değişiklikler Spring Boot uygulamasının yeniden yazılmasını veya AI servisinin iç detaylarını bilmesini
gerektirmemelidir.

Bu nedenle sistem sınırları, veri sahipliği ve servisler arası iletişim contract-first olarak tanımlanmalıdır.

## 2. Karar

### 2.1 Spring Boot Core Platform sistemin iş otoritesidir

Spring Boot Core Platform:

- Kullanıcıya açık tek backend olacaktır.
- Sistemin business state’i için tek otorite olacaktır.
- PostgreSQL verilerinin sahibi olacaktır.
- İşlem yaşam döngüsünü yönetecektir.
- İş kurallarını ve deterministik validasyonları çalıştıracaktır.
- AI çıktılarının kabul edilip edilmeyeceğine karar verecektir.
- Ödeme ve mutabakat kararlarını yönetecektir.
- Audit ve idempotency kayıtlarını tutacaktır.
- Frontend tarafından kullanılan bütün public API’leri sağlayacaktır.

FastAPI AI Service hiçbir business entity üzerinde doğrudan mutation yetkisine sahip olmayacaktır.

### 2.2 FastAPI AI Service bir internal capability service olacaktır

FastAPI AI Service aşağıdaki teknik yeteneklerden sorumludur:

- Doküman dönüştürme
- PDF ve DOCX metin çıkarımı
- Gerektiğinde OCR
- Metin normalizasyonu
- Hassas veri tespiti ve maskeleme
- RAG ve retrieval işlemleri
- LLM tabanlı yapılandırılmış veri çıkarımı
- Model çıktısının kendi schema seviyesinde kontrolü
- Video veya görsel analiz
- AI pipeline teknik metrikleri
- Model, prompt ve pipeline versiyon bilgilerinin üretilmesi

FastAPI’nin ürettiği sonuçlar business kararı değil, değerlendirilmek üzere üretilmiş yapılandırılmış
önerilerdir.

FastAPI aşağıdaki kararları veremez:

- İşlem onaylandı
- İşlem reddedildi
- Taraf doğrulandı
- Kural kesinleşti
- Ödeme serbest bırakılmalı
- Dispute çözüldü
- Teslimat tamamlandı
- Ratification geçerli
- Transaction state değiştirilmeli

Bu kararlar yalnızca Spring Boot Core Platform tarafından verilebilir.

## 3. Public API sınırı
Frontend yalnızca Spring Boot Core Platform ile iletişim kuracaktır.

  Frontend → Spring Boot Core Platform

Aşağıdaki iletişim yasaktır:

  Frontend → FastAPI AI Service

Frontend:

- AI servisinin adresini bilmeyecek,
- AI job oluşturmayacak,
- AI sonucunu doğrudan okumayacak,
- model veya provider bilgisine göre davranış değiştirmeyecek,
- AI servisinin authentication mekanizmasını kullanmayacaktır.

Frontend’de gösterilecek AI durumu ve AI sonuçları Spring API’leri üzerinden sunulacaktır.

## 4. Veri sahipliği

### 4.1 Spring Boot tarafından sahip olunan veriler

Aşağıdaki verilerin authoritative sahibi Spring Boot Core Platform’dur:

                               Veri alanı                                 Sahip

                               Kullanıcı hesapları                        Spring

                               Authentication ve session                  Spring

                               Tüzel kişiler                              Spring

                               Üyelikler ve yetkiler                      Spring

                               Deal veya transaction kayıtları            Spring

                               Katılımcılar ve taraflar                   Spring

                               Davetler                                   Spring

                               Doküman metadata kayıtları                 Spring

                               Doküman hash ve provenance bilgileri       Spring

                               AI job business durumu                     Spring

                               AI sonuçlarının kabul edilmiş kopyası      Spring

                               Rule-set ve rule version kayıtları         Spring

                             Veri alanı                                   Sahip

                             Deterministik validator sonuçları            Spring

                             Manual review kayıtları                      Spring

                             Ratification package                         Spring

                             Ratification kayıtları                       Spring

                             Funding plan ve funding unit                 Spring

                             Payment operation kayıtları                  Spring

                             Evidence metadata                            Spring

                             Milestone ve fulfillment durumu              Spring

                             Dispute kayıtları                            Spring

                             Settlement kararları                         Spring

                             Audit kayıtları                              Spring

                             Outbox ve inbox idempotency kayıtları        Spring

Bu veriler PostgreSQL içinde tutulacaktır.

### 4.2 FastAPI tarafından sahip olunan veriler

FastAPI AI Service yalnızca kendi teknik çalışma verilerinin sahibi olabilir:

                                  Veri alanı                        Sahip

                                  Model konfigürasyonları           FastAPI

                                  Prompt template’leri              FastAPI

                                  Pipeline implementasyonu          FastAPI

                                  Model cache                       FastAPI

                                  Embedding cache                   FastAPI

                                  Vector index                      FastAPI

                                  Geçici job çalışma durumu         FastAPI

                                  Teknik retry kayıtları            FastAPI

                                  Model performans metrikleri       FastAPI

                                  AI teknik logları                 FastAPI

FastAPI’de saklanan teknik veriler business source of truth olarak kullanılamaz.

FastAPI kendi özel veritabanına veya vector store’una sahip olabilir. Ancak bu veri kaynakları Spring
tarafından doğrudan okunmaz ve Spring PostgreSQL verilerinin yerine geçmez.

## 5. Veritabanı izolasyonu
Spring Boot ve FastAPI aynı veritabanı şemasını paylaşmayacaktır.

Aşağıdaki davranışlar yasaktır:

- FastAPI’nin Spring PostgreSQL veritabanına doğrudan bağlanması
- FastAPI’nin Spring tablolarından veri okuması
- FastAPI’nin Spring tablolarına veri yazması
- Spring’in FastAPI private veritabanından veri okuması
- Servislerin birbirlerinin tablolarını repository seviyesinde kullanması
- Servisler arası veri aktarımı için database trigger kullanılması

Servisler yalnızca tanımlanmış iletişim contract’ları üzerinden veri alışverişi yapacaktır.

## 6. Dosya ve object storage sahipliği
Raw sözleşme, görsel, video ve diğer kanıt dosyalarının business metadata sahibi Spring Boot olacaktır.

Dosyaların binary içeriği S3 uyumlu object storage üzerinde tutulabilir.

Başlangıç tercihi:

  MinIO → local ve development
  S3 compatible storage → production

Spring:

- Dosya yükleme sürecini başlatır.
- Dosya metadata kaydını oluşturur.
- Object key üretir.
- İçerik hash’ini saklar.
- Erişim politikasını belirler.
- AI job için süreli erişim yetkisi üretir.

FastAPI:

- Dosyayı kısa ömürlü presigned URL ile okur.
- Gerekli hash kontrolünü gerçekleştirir.
- Dosya üzerinde business sahipliği kazanmaz.
- Raw dosyanın kalıcı ana kopyasını saklamaz.
- Job tamamlandıktan sonra local geçici kopyaları siler.

FastAPI’nin object storage’a genel veya sınırsız bucket erişimi olmamalıdır.

## 7. Spring–AI iletişim modeli
Uzun süren AI işlemleri için iletişim modeli asynchronous olacaktır.

  Spring
     → AI job request event
     → Message Broker
     → FastAPI worker
     → AI result event
     → Message Broker
     → Spring consumer

Başlangıç broker tercihi RabbitMQ’dur.

Broker tercihi ileride değişebilir. Ancak event contract’ları broker implementasyonuna özel
tasarlanmayacaktır.

Senkron HTTP iletişimi yalnızca aşağıdaki operasyonel amaçlarla kullanılabilir:

- Health
- Readiness
- Service metadata
- Desteklenen contract version’ları
- Desteklenen capability’ler

AI processing işlemleri varsayılan olarak request-response HTTP çağrısı içinde çalıştırılmayacaktır.

İleride gerçekten düşük gecikmeli senkron bir AI ihtiyacı oluşursa bu kullanım ayrı bir ADR ile
onaylanacaktır.

## 8. Contract-first geliştirme politikası
Spring ve FastAPI implementasyonları yazılmadan önce servisler arası contract’lar tanımlanacaktır.

Contract’lar ayrı ve merkezi bir dizinde tutulacaktır:

  contracts/
    asyncapi/
    openapi/
    schemas/
    examples/

Kullanılacak standartlar:

- Asenkron mesajlar için AsyncAPI
- Senkron internal HTTP API’leri için OpenAPI

- Payload tanımları için JSON Schema
- Örnek request ve response’lar için canonical fixture dosyaları

Contract dosyaları iki servisten birinin private kodu içinde tutulmayacaktır.

Spring ve FastAPI bu contract’lardan kendi dillerine uygun modeller üretebilir. Ancak iki servis arasında
ortak runtime library paylaşılması zorunlu olmayacaktır.

Amaç kaynak kod paylaşmak değil, contract paylaşmaktır.

## 9. Contract değişiklik sahipliği
Spring–AI contract’ları ortak sorumluluktur.

Bir contract değişikliğinin kabul edilmesi için en az:

- Spring tarafı sahibi,
- FastAPI tarafı sahibi,
- mimari karar sahibi

tarafından değerlendirilmesi gerekir.

FastAPI geliştiricisi model veya pipeline değişikliği nedeniyle Spring contract’ını tek taraflı değiştiremez.

Spring geliştiricisi de AI servisinin iç implementasyonunu varsayan yeni alanları tek taraflı zorunlu hale
getiremez.

Contract değişiklikleri normal feature branch ve pull request süreci üzerinden yapılacaktır.

Contract değişikliği uygulama kodundan önce veya aynı pull request serisinin ilk adımında
hazırlanmalıdır.

## 10. Model bağımsızlığı
Spring hiçbir zaman aşağıdaki iç detaylara bağımlı olmayacaktır:

- Model provider adı
- Model SDK’sı
- Prompt metni
- Model context window değeri
- Modelin native response formatı
- Provider-specific tool calling formatı
- Embedding storage yapısı
- Vector database sorgu formatı
- OCR motoru
- Video analiz sağlayıcısı

FastAPI bu iç detayları stable M4Trust contract’ına dönüştürmekle sorumludur.

Örnek olarak farklı modeller aşağıdaki gibi farklı sonuçlar üretebilir:

  OpenAI native result
  Anthropic native result
  Local LLM native result
  Rule-based fallback result

Spring bu formatların hiçbirini görmez.

Spring yalnızca M4Trust tarafından tanımlanmış canonical result payload’ını görür.

## 11. Minimum event envelope
Bütün Spring–AI mesajları ortak bir envelope kullanacaktır.

Minimum alanlar:

```json
{
"eventId": "uuid",
"eventType": "string",
"schemaVersion": "major.minor",
"occurredAt": "RFC-3339 timestamp",
"correlationId": "uuid",
"causationId": "uuid or null",
"jobId": "uuid",
"tenantId": "uuid",
"transactionId": "uuid",
"documentId": "uuid or null",
"idempotencyKey": "string",
"payload": {}
}
```

Not: Bu liste ilk taslaktır; kesinleşen envelope ADR-002 §6'da tanımlıdır. ADR-002'de
`documentId` yerine genel amaçlı `subjectId` alanı kullanılmış, `jobType` ve `producer`
alanları eklenmiştir. İki doküman arasında fark olduğunda ADR-002 ve `contracts/` altındaki
şemalar geçerlidir.

AI job request’lerinde ayrıca input hash bulunacaktır.

AI sonuçlarında en az aşağıdaki teknik metadata bulunacaktır:

```json
{
"pipelineVersion": "string",
"modelFamily": "string",
"modelVersion": "string",
"promptVersion": "string or null",
"durationMs": 0,
"warnings": []
}
```

Şema seviyesinde yalnız `pipelineVersion` ve `durationMs` zorunludur; model ve prompt
alanları nullable/optional'dır (örneğin failure event'lerinde model hiç çalışmamış olabilir).

Spring’in business davranışı modelFamily , modelVersion veya promptVersion değerlerine göre
değişmeyecektir.

Bu alanlar yalnızca:

- izlenebilirlik,
- audit,
- kalite analizi,
- yeniden üretilebilirlik,
- hata inceleme

amaçlarıyla saklanacaktır.

Exact event adları ve exact payload şemaları ADR-002 kapsamında tanımlanacaktır.

## 12. Compatibility politikası
Aynı major contract version’ı içinde yalnız backward-compatible değişikliklere izin verilecektir.

Backward-compatible değişiklik örnekleri:

- Yeni optional alan eklemek
- Yeni optional metadata eklemek
- Yeni event type eklemek
- Var olan alanların açıklamasını netleştirmek
- Mevcut semantic’i değiştirmeden validation sınırını daraltmamak

Breaking change örnekleri:

- Required alan kaldırmak
- Required alan eklemek
- Alan tipini değiştirmek
- Alanın semantic anlamını değiştirmek
- Event adını değiştirmek
- Mevcut status değerini kaldırmak
- Payload hiyerarşisini değiştirmek
- Para veya tarih formatını değiştirmek
- Null kabul eden alanı null kabul etmez hale getirmek

Breaking change gerektiğinde yeni major version oluşturulacaktır.

Örnek:

  ai.extraction.requested.v1
  ai.extraction.completed.v1

  ai.extraction.requested.v2
  ai.extraction.completed.v2

Yeni major version geçiş döneminde eski version ile birlikte çalıştırılabilir.

Bir major version, onu kullanan bütün consumer’lar migrate edilmeden kaldırılmayacaktır.

## 13. Mesaj teslim ve idempotency politikası
Message broker iletişimi en az bir kez teslim, yani at-least-once delivery varsayımıyla tasarlanacaktır.

Bu nedenle:

- Spring producer idempotency key üretir.
- FastAPI aynı job’ı birden fazla kez alabileceğini varsayar.
- FastAPI aynı sonuç event’ini birden fazla kez gönderebileceğini varsayar.
- Spring aynı result event’ini birden fazla kez alabileceğini varsayar.
- Her iki consumer da duplicate-safe olmak zorundadır.
- İşlenmiş mesaj kimlikleri kalıcı veya yeterince uzun ömürlü biçimde takip edilir.

Exactly-once delivery varsayımı yapılmayacaktır.

## 14. Hata sahipliği

### 14.1 Teknik AI hataları

Aşağıdaki durumlar AI teknik hatasıdır:

- Model provider erişilemiyor
- OCR başarısız
- Model timeout
- RAG servisi erişilemiyor
- Schema üretilemedi
- İşlenemeyen dosya formatı
- Worker crash
- Geçici network hatası

FastAPI bu hataları stable error contract’ına dönüştürür.

Spring bu hataları business rejection olarak yorumlamaz.

AI teknik hatası sonucunda transaction’ın business state’i otomatik olarak reddedilmiş duruma
geçirilmez.

Spring yalnızca ilgili AI job durumunu günceller ve uygun retry veya manual action seçeneğini sunar.

### 14.2 Business validation hataları

Aşağıdaki kararlar Spring’e aittir:

- Çıkarılan kural geçersiz

- Taraf bilgileri uyuşmuyor
- Manuel inceleme gerekiyor
- Zorunlu alan eksik
- Ratification oluşturulamaz
- Evidence yetersiz
- Payment release engellenmeli

FastAPI bu durumlar için nihai business kararı üretmez.

## 15. Retry ve timeout politikası
FastAPI teknik retry uygulayabilir.

Ancak retry:

- Aynı jobId altında yürütülmelidir.
- Aynı input hash ile ilişkilendirilmelidir.
- Duplicate business result üretmemelidir.
- Maksimum deneme sınırına sahip olmalıdır.
- Son başarısızlıkta terminal failure event’i üretmelidir.

Business timeout’un sahibi Spring’dir.

Spring bir AI job’ın beklenen süreyi aşması durumunda:

- job’ı timeout olarak işaretleyebilir,
- cancel event’i yayınlayabilir,
- yeni bir job oluşturabilir,
- kullanıcıya retry seçeneği sunabilir.

Cancel işlemi best-effort olacaktır. FastAPI cancel mesajı geldiğinde işlem bitmişse sonuç yine
gönderilebilir. Spring geç veya iptal edilmiş sonuçları kendi job state’ine göre kabul edip etmeyeceğine
karar verir.

## 16. Güvenlik sınırları
Spring–FastAPI iletişimi internal network içinde olsa dahi güvenilir kabul edilmeyecektir.

Aşağıdaki önlemler uygulanacaktır:

- Servis kimlik doğrulaması
- TLS veya güvenli internal transport
- Broker kullanıcılarının ayrı tutulması
- Queue publish/consume yetkilerinin sınırlandırılması
- Presigned URL sürelerinin kısa tutulması
- Secret’ların source code içine yazılmaması
- PII ve secret’ların loglanmaması
- Message payload’larında gereksiz kişisel veri taşınmaması

- Tenant ve transaction bağlamının her mesajda bulunması
- Input hash doğrulaması
- Yetkisiz veya beklenmeyen schema version’larının reddedilmesi

AI servisi üçüncü taraf modele gönderilecek veriler için privacy ve masking politikasını uygulamak
zorundadır.

Spring gerekli veri sınıflandırma bilgisini job request içinde sağlayabilir. FastAPI’nin masking uygulaması
yine de zorunlu bir savunma katmanıdır.

## 17. Audit ve gözlemlenebilirlik
Spring business audit’in sahibidir.

FastAPI teknik telemetry’nin sahibidir.

Her iki sistem de ortak olarak şu değerleri taşımalıdır:

- correlationId
- causationId
- jobId
- transactionId
- tenantId
- schemaVersion

Spring audit kaydı:

- Hangi AI job’ın oluşturulduğunu,
- hangi input hash ile çalıştığını,
- hangi schema version kullanıldığını,
- hangi AI sonucunun kabul edildiğini,
- hangi business kararın verildiğini

göstermelidir.

FastAPI teknik kayıtları:

- Hangi pipeline’ın çalıştığını,
- hangi model family ve model version’ın kullanıldığını,
- kaç deneme yapıldığını,
- işlemin ne kadar sürdüğünü,
- hangi teknik uyarıların oluştuğunu

göstermelidir.

Raw prompt, raw PII veya hassas doküman içeriği varsayılan olarak loglanmayacaktır.

## 18. Contract test politikası
Spring ve FastAPI’nin birbirinden bağımsız geliştirilebilmesi için contract testleri zorunludur.

Minimum test seti:

1. JSON Schema validation testleri
2. Canonical request fixture testleri
3. Canonical success result fixture testleri
4. Canonical failure result fixture testleri
5. Duplicate event testi
6. Bilinmeyen optional alan toleransı testi
7. Desteklenmeyen major version reddetme testi
8. Spring consumer compatibility testi
9. FastAPI producer compatibility testi

Test sayısı minimum tutulabilir; ancak contract boundary testleri atlanamaz.

Bir servis kendi testlerinde başarılı olsa bile ortak contract testlerini geçmeden merge edilemez.

## 19. Deployment bağımsızlığı
Spring Boot ve FastAPI:

- Ayrı container image’larına sahip olacaktır.
- Ayrı versiyonlanacaktır.
- Ayrı deploy edilecektir.
- Ayrı ölçeklenebilecektir.
- Ayrı health ve readiness kontrollerine sahip olacaktır.
- Birbirinden bağımsız rollback edilebilmelidir.

FastAPI worker sayısının artırılması Spring deployment’ını gerektirmemelidir.

Model değişikliği Spring deployment’ını gerektirmemelidir.

Spring business feature deployment’ı model deployment’ını gerektirmemelidir.

Contract major version değişikliği ise koordineli rollout gerektirebilir.

## 20. Yasaklanan mimari yaklaşımlar
Aşağıdaki yaklaşımlar bu ADR ile açıkça yasaklanmıştır:

- Shared business database
- Frontend’in doğrudan FastAPI’ye bağlanması
- FastAPI’nin transaction state değiştirmesi
- FastAPI’nin payment provider çağırması

- Spring’in LLM provider SDK kullanması
- Spring’in model-specific response parse etmesi
- AI sonucu üzerinden deterministik validasyonu atlamak
- Servisler arasında source code seviyesinde domain entity paylaşmak
- Broker mesajlarında provider-specific payload kullanmak
- Model değiştiğinde Spring contract’ını değiştirmek
- Uzun AI işlemlerini kullanıcı HTTP request’i içinde bekletmek
- AI teknik hatasını otomatik business rejection olarak değerlendirmek

## 21. Başlangıç deployment birimleri
İlk aşamada aşağıdaki deployment birimleri yeterlidir:

  web
  core-api
  ai-api
  ai-worker
  postgres
  rabbitmq
  minio

 ai-api ve ai-worker aynı FastAPI codebase içinde bulunabilir fakat farklı process veya container
olarak çalıştırılabilir.

İlk aşamada gereksiz mikroservis ayrıştırması yapılmayacaktır.

Spring tarafı modular monolith olarak başlayacaktır.

FastAPI tarafı tek AI codebase olarak başlayacaktır.

Yeni servis ayrıştırmaları yalnız:

- bağımsız ölçekleme ihtiyacı,
- güvenlik izolasyonu,
- farklı veri sahipliği,
- farklı operasyonel yaşam döngüsü

kanıtlandığında yapılacaktır.

## 22. Sonuçlar
Bu kararın olumlu sonuçları:

- Spring ve FastAPI ekipleri bağımsız çalışabilir.
- Model değişiklikleri business backend’i etkilemez.
- Public API yalnızca Spring tarafından yönetilir.

- Business state tek yerde tutulur.
- AI servisinin yetki alanı netleşir.
- Ölçekleme ve retry daha güvenli hale gelir.
- Contract değişiklikleri kontrollü yapılır.
- Frontend AI implementasyonundan bağımsız olur.
- FastAPI farklı model sağlayıcılarına geçebilir.
- AI çıktıları audit edilebilir ve yeniden üretilebilir olur.

Bu kararın maliyetleri:

- Message broker altyapısı gerekir.
- Eventual consistency oluşur.
- Idempotency mekanizması gerekir.
- Contract governance disiplini gerekir.
- Local development için birden fazla servis çalıştırılır.
- Debug sırasında distributed tracing gerekir.
- Schema versioning başlangıçta ek çalışma oluşturur.

Bu maliyetler sistemin ekipler arası bağımsızlığı ve production güvenilirliği için kabul edilmiştir.

## 23. Sonraki zorunlu karar
Bu ADR’den hemen sonra aşağıdaki ADR hazırlanmalıdır:

  ADR-002: Spring–AI Contract and Compatibility Policy

ADR-002 kapsamında kesinleştirilecek konular:

- Exact event isimleri
- Job type listesi
- Job lifecycle durumları
- Request payload’ları
- Result payload’ları
- Error formatı
- Warning formatı
- Progress event gereksinimi
- Model metadata formatı
- Schema versioning kuralları
- Queue ve routing key isimleri
- Retry ve dead-letter davranışı
- Contract fixture’ları
- Consumer-driven contract test yapısı
- Version deprecation süreci

ADR-002 tamamlanmadan Spring veya FastAPI tarafında gerçek AI entegrasyon kodu yazılmayacaktır.
