# ADR-004: Vertical Slice Delivery and Acceptance Testing

- Durum: Accepted
- Tarih: 14 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Spring Boot, frontend, PostgreSQL, RabbitMQ ve AI entegrasyonlarının geliştirme, test ve kabul yöntemi
- Bağlı kararlar:
- ADR-001: System Boundaries and Data Ownership
- ADR-002: Spring–AI Contract and Compatibility Policy
- ADR-003: Core Domain Model and Deal Lifecycle

## 1. Bağlam

M4Trust sistemi aşağıdaki temel uygulama parçalarından oluşacaktır:

Frontend

Spring Boot Core Platform

PostgreSQL

RabbitMQ

Object Storage

FastAPI AI Service Spring Boot ve frontend geliştirmesi M4Trust ana geliştirme ekibi tarafından yürütülecektir.

FastAPI AI Service ayrı geliştirilebilir ve kendi geliştirme takvimine sahip olabilir.

Sistemin backend modüllerinin tamamlanıp frontend entegrasyonunun sona bırakılması aşağıdaki riskleri oluşturur:

- Public API ile gerçek frontend ihtiyacının geç uyuşması
- Yanlış request ve response tasarımlarının geç fark edilmesi
- Kullanıcı akışlarının business modelle uyuşmaması
- Authentication ve authorization sorunlarının geç görülmesi
- Loading ve hata durumlarının unutulması
- Backend’in teknik olarak çalışmasına rağmen ürünün kullanılamaması
- Entegrasyon problemlerinin proje sonunda birikmesi
Bu nedenle sistem, teknik katmanlar halinde değil, kullanıcıya anlamlı business capability’ler halinde geliştirilecektir.

## 2. Temel karar

M4Trust:

`Vertical Slice Delivery`

yaklaşımıyla geliştirilecektir.

Bir capability yalnız Spring tarafında implement edildiğinde tamamlanmış sayılmaz.

Her capability mümkün olduğunca aşağıdaki zincirin tamamını kapsar:

```text
Frontend
→ Spring public API
→ Spring application/domain
→ PostgreSQL
→ response
→ frontend state ve kullanıcı sonucu
```

AI içeren capability’lerde zincir şu şekilde genişler:

```text
Frontend
→ Spring
→ PostgreSQL ve transactional outbox
→ RabbitMQ
→ Mock AI Worker veya gerçek FastAPI
→ RabbitMQ
→ Spring inbox ve business validation
→ PostgreSQL
→ Frontend
```

Bir slice’ın belirleyici kabul testi, gerçek frontend üzerinden çalışan kullanıcı akışıdır.

## 3. Tamamlanma ölçütü

Bir capability aşağıdaki koşul sağlanmadan tamamlanmış sayılmaz:

Kullanıcı, gerçek frontend’i kullanarak gerçek Spring API ve gerçek PostgreSQL üzerinde capability’nin ana akışını başarıyla tamamlayabilmelidir.

AI içeren akışlarda gerçek AI modeli zorunlu değildir.

Ancak aşağıdaki parçalar gerçek çalışmalıdır:

- Frontend
- Spring public API
- Spring business logic
- PostgreSQL
- Outbox ve inbox
- RabbitMQ mesajlaşması
- Contract validation
- Business acceptance validation
- Frontend sonucunun güncellenmesi
AI modeli veya FastAPI servisi yerine contract uyumlu bir Mock AI Worker kullanılabilir.

## 4. Slice geliştirme sırası

Her slice genel olarak aşağıdaki sırayla ilerler:

1. Kullanıcı senaryosunu tanımla
2. Business kurallarını netleştir
3. Public API yüzeyini tasarla
4. Gerekli PostgreSQL migration’ını belirle
5. Spring implementasyonunu yap
6. Kritik kod düzeyi kontrolleri ekle
7. Frontend ekranını ve state yönetimini yap
8. Frontend’i gerçek Spring API’ye bağla
9. Tarayıcıdan ana ve kritik hata akışlarını test et
10. Varsa geçici mockları kaldır veya açıkça sınırla
11. Slice kabul checklist’ini tamamla Backend’in bütün modülleri tamamlanıp frontend’in daha sonra geliştirilmesi kabul edilen yöntem değildir.
## 5. Public API yaklaşımı

Spring ile frontend arasında hibrit API sözleşmesi yaklaşımı kullanılacaktır.

### 5.1 Slice başlamadan önce

Geliştirilecek slice’ın public API yüzeyi tasarlanır.

En az aşağıdaki bilgiler netleştirilir:

- Endpoint
- HTTP metodu
- Authentication gereksinimi
- Request alanları
- Response alanları
- Hata response formatı
- Pagination veya filtering ihtiyacı
- Optimistic concurrency ihtiyacı
- İlgili business action’lar
Bu tasarım merkezi public OpenAPI dokümanına eklenir:

`contracts/openapi/core-api-v1.yaml`

Bütün sistemin public API’si baştan tasarlanmaz.

Yalnız aktif olarak geliştirilecek slice’ın API yüzeyi eklenir.

### 5.2 Spring implementasyonu

Spring endpoint ve DTO’ları tasarlanan OpenAPI yüzeyiyle uyumlu olur.

Spring runtime OpenAPI çıktısı üretebilir.

Tasarlanan contract ile Spring’in gerçek runtime çıktısı arasında sessiz sapma oluşmamalıdır.

### 5.3 Frontend kullanımı

Frontend request ve response modellerini tahmin ederek bağımsız biçimde oluşturmaz.

Mümkün olduğunda OpenAPI üzerinden:

- TypeScript tipleri
- Request modelleri
- Response modelleri
- API client kodu üretilir.
Generated client kullanımı uygun değilse frontend modelleri yine public OpenAPI ile birebir uyumlu tutulur.

Frontend, Spring’in internal domain veya JPA modellerini bilmez.

## 6. Kod düzeyindeki test politikası

M4Trust’ın geliştirme hedefi yüksek test sayısı veya yüksek coverage yüzdesi değildir.

Temel prensip:

Test yazmak yerine sistemi test etmek önceliklidir.

Kod düzeyindeki otomatik testler minimum tutulacaktır.

Aşağıdaki alanlar için varsayılan olarak test yazılmayacaktır:

- Basit getter ve setter’lar
- Framework tarafından sağlanan standart CRUD davranışları
- Controller’ın service çağırdığını doğrulayan yüzeysel testler
- Basit DTO mapping
- Her küçük React component’i
- Her butonun render olması
- Her HTTP status code’un ayrı testi
- Framework’ün kendi davranışını tekrar doğrulayan testler
Coverage yüzdesi başarı metriği olarak kullanılmayacaktır.

## 7. Minimum kritik otomatik testler

Bazı invariant’ların yalnız tarayıcı testiyle güvenli biçimde korunması zor veya maliyetlidir.

Bu nedenle aşağıdaki gibi kritik konularda az sayıda ve doğrudan otomatik test tutulabilir:

- Aynı legal entity’nin hem buyer hem seller olamaması
- Yasak state transition’ları
- Money minor-unit hesapları
- Basis-point hesapları
- Immutable ratification package davranışı
- Rule-set version immutability
- Inbox duplicate event kontrolü
- Outbox idempotency
- Payment provider idempotency
- Unknown payment outcome ve reconciliation
- Authorization sınırları
- Contract schema validation
- AI sonucunun business state’i doğrudan değiştirememesi
- Superseded AI sonucunun current state’e uygulanmaması
Bu testler küçük, açık ve iş açısından kritik olmalıdır.

Bütün implementation detaylarını test eden geniş unit-test katmanı oluşturulmayacaktır.

## 8. Belirleyici test: frontend kabul testi

Her slice’ın belirleyici testi gerçek kullanıcı akışıdır.

Örnek authentication kabul akışı:

1. Kullanıcı kayıt sayfasını açar.
2. Geçerli bilgilerle kayıt olur.
3. Uygun authenticated ekrana yönlendirilir.
4. Sayfayı yeniler ve oturumun korunduğunu görür.
5. Çıkış yapar.
6. Korumalı sayfaya erişemez.
7. Aynı hesapla tekrar giriş yapar.
8. Korumalı sayfaya ulaşır.
9. Yanlış şifreyle giriş yapamaz.
10. Aynı e-posta ile tekrar kayıt olamaz.
Bu akış:

```text
Frontend
→ Spring
→ PostgreSQL
```

üzerinden gerçek çalışmalıdır.

Sadece Postman, Swagger UI veya backend testi ile authentication slice tamamlanmış sayılmaz.

## 9. Manuel tarayıcı testi

Her slice sonunda manuel tarayıcı kabul checklist’i hazırlanır.

Checklist en az şunları içerir:

- Ana happy path
- En kritik business hata durumu
- Yetkisiz erişim
- Loading durumu
- Backend hata durumu
- Boş state
- Form validation
- Sayfa yenileme davranışı
- Gerekliyse responsive temel kontrol
- Kullanıcının yaptığı işlemin sonuç ekranında görünmesi
Manuel kabul testi, geliştirme sırasında temel ve zorunlu test yöntemidir.

## 10. Playwright otomasyonu

Playwright, gerçek bir tarayıcıyı otomatik olarak kullanan end-to-end test aracıdır.

Playwright testi:

- Frontend sayfasını açar.
- Formları doldurur.
- Butonlara basar.
- Yönlendirmeleri takip eder.
- Ekranda görünen sonucu kontrol eder.
- Gerçek Spring API ile konuşabilir.
- Gerçek PostgreSQL üzerinde veri oluşturabilir.
- Gerekli olduğunda RabbitMQ ve Mock AI Worker içeren akışı çalıştırabilir.
Örnek:

```text
Playwright
→ Frontend
→ Spring
→ PostgreSQL
```

AI içeren akışlarda:

```text
Playwright
→ Frontend
→ Spring
→ RabbitMQ
→ Mock AI Worker
→ Spring
→ Frontend
```

### 10.1 Otomasyon zorunluluğu

Her küçük ekran veya hata durumu için Playwright testi yazılmayacaktır.

İlk geliştirme sırasında manuel kabul testi yeterlidir.

Bir akış:

- Stabil hâle geldiğinde,
- Tekrar tekrar manuel test edilmesi maliyet oluşturduğunda,
- Release açısından kritik olduğunda,
- Başka slice’lar tarafından sık kullanılmaya başlandığında
Playwright ile otomatikleştirilebilir.

Özellikle aşağıdaki ana akışlar otomasyon için adaydır:

- Register, logout, login ve protected route
- Legal entity oluşturma ve aktif entity seçimi
- Deal oluşturma ve detayını açma
- Document upload
- AI extraction sonucu görüntüleme
- Manual review ve rule-set kabulü
- Ratification
- Funding
- Evidence submission
- Settlement öncesi kritik akış
Playwright otomasyonu tamamlanma ölçütünün yerini almaz; manuel ürün kontrolünü destekler.

## 11. FastAPI bağımlılığı

Günlük Spring ve frontend geliştirmesi gerçek FastAPI servisine bağımlı olmayacaktır.

FastAPI’nin hazır olmaması:

- Authentication
- Organization
- Deal
- Document
- Ratification
- Funding
- Payment
- Evidence
- Dispute
- Frontend geliştirmesini engellememelidir.
AI içeren akışlarda da gerçek FastAPI zorunlu değildir.

## 12. Mock AI Worker

AI içeren vertical slice’larda contract uyumlu bağımsız bir Mock AI Worker kullanılacaktır.

Önerilen konum:

tools/mock-ai-worker/

veya:

`services/mock-ai-worker/`

Kesin repository konumu platform foundation sırasında belirlenebilir.

Mock AI Worker:

- Gerçek LLM çağırmaz.
- Gerçek OCR çalıştırmaz.
- Gerçek RAG kullanmaz.
- Embedding üretmez.
- Video modeli çalıştırmaz.
- Contract fixture veya deterministik fixture üretir.
- RabbitMQ’dan gerçek command mesajı tüketir.
- Contract uyumlu gerçek completed veya failed event yayınlar.
Mock worker’ın FastAPI ile yazılması zorunlu değildir.

Küçük ve bağımsız bir script veya servis olabilir.

## 13. Mock AI Worker senaryoları

Mock AI Worker en az aşağıdaki senaryoları destekleyebilir:

SUCCESS

SUCCESS_WITH_WARNINGS

LOW_CONFIDENCE

REVIEW_REQUIRED

RETRYABLE_FAILURE

NON_RETRYABLE_FAILURE

DELAYED_RESULT

DUPLICATE_RESULT

LATE_RESULT Gerekirse input metadata veya development-only test configuration üzerinden scenario seçilebilir.

Scenario seçim mekanizması production contract’ına business alanı olarak eklenmeyecektir.

Development/test davranışı production event contract’ını kirletmemelidir.

## 14. AI modelini mocklamak ile servis sınırını mocklamak arasındaki ayrım

Aşağıdaki yaklaşım kabul edilir:

```text
Spring
→ gerçek RabbitMQ
→ Mock AI Worker
→ gerçek RabbitMQ
→ Spring
```

Bu yapıda model mocklanmıştır fakat servis sınırı gerçektir.

Aşağıdaki yaklaşım, ana entegrasyon testi olarak yeterli değildir:

```text
Spring
→ kendi içinde sahte AI result oluşturur
→ kendi consumer’ını doğrudan çağırır
```

Bu yaklaşım yalnız dar kapsamlı development kolaylığı olarak kullanılabilir.

Ana AI slice kabulünde aşağıdakiler gerçek çalışmalıdır:

- Outbox kaydı
- RabbitMQ publish
- Routing key
- Queue
- Event envelope
- Mock worker consumption
- Completed veya failed event
- Spring inbox
- Duplicate kontrolü
- Contract validation
- Business acceptance validation
- Frontend sonucu
Temel prensip:

AI modeli mocklanabilir; servisler arası contract ve messaging sınırı mocklanmamalıdır.

## 15. Gerçek FastAPI entegrasyonu

Her günlük geliştirme veya her slice kapanışı için gerçek FastAPI gerekli değildir.

Gerçek FastAPI entegrasyonu aşağıdaki zamanlarda yapılır:

- Yusuf’un ilgili capability’si hazır olduğunda
- Contract implementation kontrolü gerektiğinde
- Milestone öncesinde
- Release öncesinde
- Contract major/minor değişikliğinde
- RabbitMQ topology veya serialization değişikliğinde
Gerçek FastAPI entegrasyon testinde AI provider yine mocklanabilir.

Bu testin amacı model kalitesini değil, FastAPI servisinin aşağıdaki konulardaki uyumunu doğrulamaktır:

- Command tüketimi
- Contract validation
- Dosya referansı işleme
- Canonical output üretimi
- Completed ve failed event üretimi
- Schema version
- Correlation ve causation bilgileri
- Idempotency
- Retry davranışı
- Cancellation davranışı
## 16. Yusuf’un geliştirme özgürlüğü

FastAPI contract’ları Yusuf’un AI servisi içindeki implementation kararlarını kısıtlamaz.

Yusuf aşağıdakileri serbestçe değiştirebilir:

- LLM provider
- LLM modeli
- Local model
- Prompt
- Agent yapısı
- OCR motoru
- Document parser
- Embedding modeli
- Vector store
- RAG yaklaşımı
- Retrieval algoritması
- Video analiz modeli
- Pipeline adımları
- Internal retry
- Internal storage
- Internal cache
- Worker sayısı
FastAPI’nin uyması gereken sınır:

- Spring’den gelen command formatı
- Spring’e dönen canonical event formatı
- Accepted contract version’ları
- ADR-001’deki ownership sınırları
FastAPI’nin internal geliştirmesi Spring ve frontend geliştirmesini bloklamaz.

## 17. FastAPI’nin kendi test yaklaşımı

FastAPI tarafında model ve provider entegrasyonlarında varsayılan yaklaşım mock-first olacaktır.

Yusuf aşağıdaki parçaları mocklayabilir:

- LLM response
- OCR response
- Retrieval response
- Embedding response
- Video model response
- Provider timeout
- Provider unavailable durumu
- Corrupted provider response
FastAPI içinde minimum otomatik kontroller:

- Canonical output validation
- Contract schema validation
- Event serialization
- Failure mapping
- Idempotent duplicate job davranışı
- Retry sınırı
- Safe error üretimi ile sınırlandırılabilir.
Model kalitesi, backend slice kabul testinin parçası değildir.

Model quality evaluation ayrı bir AI değerlendirme sürecidir.

## 18. Frontend mock politikası

Frontend geliştirmenin ilk aşamasında geçici mock response kullanılabilir.

Ancak:

- Mock ile çalışan ekran tamamlanmış sayılmaz.
- Slice kapanmadan gerçek Spring API’ye bağlanmalıdır.
- Frontend içinde production’a kalan gizli mock fallback bulunmamalıdır.
- Mock response, public OpenAPI’den sapmamalıdır.
- Mock state gerçek authorization veya business validation yerine geçmemelidir.
Mock frontend server veya fixture kullanımı yalnız geliştirme hızlandırıcısıdır.

## 19. Payment provider ve diğer dış servisler

FastAPI dışında kalan dış servisler de günlük geliştirmede mock veya fake adapter ile değiştirilebilir.

Örnek:

- Payment provider
- Email provider
- SMS provider
- Object storage’ın production versiyonu
- External identity provider
- Webhook recipient
Ancak servis boundary mümkün olduğunca gerçek adapter interface’i üzerinden çalıştırılır.

Payment testlerinde gerçek para hareketi yapılmaz.

Payment provider fake’i aşağıdaki senaryoları desteklemelidir:

SUCCESS

DECLINED

TIMEOUT

UNKNOWN_OUTCOME

DUPLICATE_REQUEST

LATE_WEBHOOK Payment business logic’i fake provider ile gerçek Spring ve PostgreSQL üzerinde test edilir.

## 20. Local development ortamı

Geliştirici, sistemi tekrar üretilebilir şekilde ayağa kaldırabilmelidir.

Hedef deneyim:

```text
Docker Compose başlat
→ PostgreSQL hazır
→ RabbitMQ hazır
→ MinIO hazır
→ gerekirse Mock AI Worker hazır
→ Spring hazır
→ Frontend hazır
```

Geliştirme ortamı manuel database kurulumu veya elle SQL çalıştırmaya bağımlı olmamalıdır.

Local/test için açık reset ve seed mekanizması bulunmalıdır.

Örnek:

`scripts/dev-reset.sh`

`scripts/dev-seed.sh`

veya eşdeğer proje komutları.

Production ortamında destructive reset komutu bulunmayacaktır.

## 21. Test verisi

Test ve development verileri deterministik olmalıdır.

Gerektiğinde aşağıdaki fixture türleri sağlanabilir:

- Default tenant
- Test user
- Test legal entity
- Buyer ve seller
- Draft Deal
- Active Deal
- Uploaded document metadata
- Mock AI success result
- Mock AI warning result
- Mock AI failure result
- Ratification-ready Deal
- Funding-ready Deal
- Evidence-required milestone
Seed verileri production business migration’larının içine gömülmez.

Development ve test seed mekanizması ayrıdır.

## 22. Slice kabul checklist’i

Her slice için aşağıdaki checklist uyarlanır:

Business

- Kullanıcı senaryosu tanımlandı.
- Ana business kuralları uygulandı.
- Yasak geçişler engellendi.
- Authorization kontrol edildi.
Backend

- Spring endpoint’i gerçek çalışıyor.
- PostgreSQL migration mevcut.
- Public OpenAPI güncel.
- Error response anlamlı.
- Audit gerekiyorsa yazılıyor.
- Outbox gerekiyorsa aynı transaction’da yazılıyor.
Frontend

- Ekran gerçek API’ye bağlı.
- Loading durumu var.
- Error durumu var.
- Empty state var.
- Yetkisiz durum doğru gösteriliyor.
- Başarılı işlem kullanıcıya görünür sonuç üretiyor.
Test

- Ana akış tarayıcıda çalıştırıldı.
- Kritik hata senaryosu çalıştırıldı.
- Gerekli minimum invariant testleri geçiyor.
- AI akışıysa RabbitMQ ve Mock AI Worker üzerinden çalıştı.
- Slice için zorunlu olmayan aşırı test yazılmadı.
Temizlik

- Geçici frontend mock kaldırıldı veya açıkça development-only bırakıldı.
- Debug kodu kalmadı.
- Secret veya gerçek credential eklenmedi.
- Dokümantasyon güncellendi.
## 23. Slice Done tanımı

Bir slice ancak aşağıdaki şartların tamamı sağlandığında Done kabul edilir:

Spring implementasyonu tamam

PostgreSQL migration tamam

Public OpenAPI güncel

Frontend gerçek Spring API’ye bağlı

Ana kullanıcı akışı tarayıcıda çalışıyor

Kritik hata durumu test edildi

Loading ve error durumları mevcut

Gerekli minimum invariant testleri geçiyor

Mock production davranışına sızmıyor

AI akışıysa messaging boundary gerçek çalışıyor

Aşağıdakiler tek başına Done anlamına gelmez:

- Backend endpoint’inin Swagger üzerinden çalışması
- Unit testlerin geçmesi
- Frontend’in mock API ile çalışması
- Postman collection’ın başarılı olması
- Database’e doğru kayıt atılması
- OpenAPI dosyasının yazılmış olması
- FastAPI’nin tek başına canonical JSON üretmesi
## 24. İlk vertical slice sırası

İlk geliştirme sırası:

Slice 0

Slice 1

— Platform Foundation

— Authentication

Slice 2

Slice 3

Slice 4

Slice 5

Slice 6

— Tenant, Legal Entity and Membership

— Deal Creation and Listing

— Deal Parties and Participants

— Document Upload

— AI Document Extraction with Mock AI Worker

Slice 7 — Manual Review and RuleSetVersion

Slice 8 — Ratification

Slice 9 — Funding and Payment

Slice 10 — Fulfillment and Evidence

Slice 11 — Video Analysis

Slice 12 — Dispute and Settlement Her slice, kendinden sonraki slice başlamadan tamamen bitmek zorunda değildir.

Ancak tamamlanmamış temel kullanıcı akışları biriktirilerek bütün frontend entegrasyonunun proje sonuna bırakılmasına izin verilmez.

## 25. Slice 0: Platform Foundation

Platform foundation en az aşağıdakileri hazırlar:

`services/core-api`

frontend application

PostgreSQL

RabbitMQ

MinIO

local Docker Compose

shared environment configuration

health endpoints

correlation ID

public error format

public OpenAPI foundation

local reset/seed mechanism

Slice 0 kullanıcıya business capability sunmaz.

Sonraki slice’ların tekrar üretilebilir şekilde geliştirilebilmesini sağlar.

## 26. Slice 1: Authentication kabul sınırı

Authentication slice en az şu akışı kapsar:

```text
Register
→ authenticated state
→ current user
```

```text
→ page refresh
→ logout
→ login
→ protected route
```

Spring tarafında en az:

POST /api/v1/auth/register

POST /api/v1/auth/login

POST /api/v1/auth/logout

GET /api/v1/auth/me veya aynı capability’yi sağlayan kesinleşmiş public API bulunur.

Frontend tarafında en az:

- Register ekranı
- Login ekranı
- Auth state
- Protected route
- Logout
- Loading
- Invalid credentials
- Duplicate email
- Session restore bulunur.
Bu akış tarayıcı üzerinden çalışmadan authentication tamamlanmış sayılmaz.

## 27. Yasaklanan yaklaşımlar

Bu ADR ile aşağıdaki yaklaşımlar yasaklanmıştır:

- Önce bütün backend’i bitirip frontend entegrasyonunu sona bırakmak
- Frontend mock ile çalışırken slice’ı tamamlanmış saymak
- Test coverage yüzdesini ana başarı metriği yapmak
- Her sınıf ve metot için otomatik test zorunluluğu koymak
- AI geliştirmesini beklediği için Spring ve frontend geliştirmesini durdurmak
- AI modelini mocklamak yerine Spring–RabbitMQ sınırını tamamen atlamak
- Spring’in kendi içinde fake AI sonucu üretmesini ana E2E test kabul etmek
- Gerçek FastAPI’yi her günlük test için zorunlu kılmak
- Frontend’in backend response modellerini tahmin ederek oluşturması
- Public OpenAPI’den habersiz paralel frontend API modelleri üretmek
- Yalnız Swagger veya Postman testiyle capability’yi tamamlanmış kabul etmek
- Gerçek dış servislerle development ortamında para veya production operasyonu yapmak
## 28. Sonuçlar

Olumlu sonuçlar:

- Her capability kısa sürede kullanıcı tarafından görülebilir hâle gelir.
- Spring ve frontend entegrasyon sorunları erken görülür.
- API tasarımı gerçek kullanıcı ihtiyacıyla birlikte gelişir.
- FastAPI takvimi ana platform geliştirmesini bloklamaz.
- RabbitMQ ve contract boundary gerçek olarak test edilir.
- Gereksiz test kodu üretimi azaltılır.
- Kritik invariant’lar minimum testlerle korunur.
- Manuel test yükü arttığında Playwright ile seçici otomasyon yapılabilir.
- Modüller tamamlandıkça ürün gerçekten kullanılabilir hâle gelir.
Maliyetler:

- Her slice için frontend ve backend koordinasyonu gerekir.
- Local ortamda birden fazla servis çalıştırmak gerekir.
- Mock AI Worker bakımı gerekir.
- OpenAPI’nin slice bazında sürekli güncellenmesi gerekir.
- Manuel kabul checklist’lerinin disiplinle uygulanması gerekir.
- Bazı akışlarda frontend geliştirmesi backend ilerleme hızını belirleyebilir.
Bu maliyetler entegrasyon riskini azaltmak ve gerçek çalışan ürün üretmek için kabul edilmiştir.

## 29. Nihai karar

M4Trust:

- Modül modül vertical slice yaklaşımıyla geliştirilecektir.
- Bir backend capability’sini frontend gerçek API’ye bağlanmadan tamamlanmış saymayacaktır.
- Belirleyici test olarak gerçek frontend kullanıcı akışını kullanacaktır.
- Kod düzeyindeki otomatik testleri kritik invariant’larla sınırlı ve minimum tutacaktır.
- Coverage hedefi kullanmayacaktır.
- Spring–frontend arasında slice bazlı hibrit OpenAPI yaklaşımı kullanacaktır.
- AI modellerini ve provider’ları varsayılan olarak mocklayabilecektir.
- Günlük platform geliştirmesini gerçek FastAPI’ye bağımlı kılmayacaktır.
- AI akışlarında bağımsız ve contract uyumlu Mock AI Worker kullanacaktır.
- AI modeli mocklansa da RabbitMQ ve servisler arası messaging sınırını gerçek çalıştıracaktır.
- Playwright’ı stabil ve kritik kullanıcı akışlarında seçici otomasyon için kullanacaktır.
- Her slice sonunda manuel tarayıcı kabul testi uygulayacaktır.
- Spring, PostgreSQL ve frontend’in gerçek entegrasyonunu slice tamamlanma koşulu olarak kabul edecektir.
