# ADR-007: Deployment and Runtime Environments

- **Durum:** Accepted
- **Tarih:** 14 Temmuz 2026
- **Karar sahipleri:** M4Trust mimari ekibi
- **Kapsam:** Uygulamaların paketlenmesi, Railway üzerinde çalıştırılması, ortamların ayrılması, runtime ağ sınırları, configuration, secret, migration, health, backup ve rollback yaklaşımı
- **Bağlı kararlar:**
  - ADR-001: System Boundaries and Data Ownership
  - ADR-002: Spring–AI Contract and Compatibility Policy
  - ADR-004: Vertical Slice Delivery and Acceptance Testing
  - ADR-005: Authentication and Security Baseline
  - ADR-006: Public API and Error Conventions

## 1. Bağlam

M4Trust sistemi aşağıdaki çalıştırılabilir parçalardan oluşacaktır:

- React frontend
- Spring Boot Core API
- FastAPI AI API
- FastAPI AI Worker
- Mock AI Worker
- PostgreSQL
- RabbitMQ
- S3-compatible object storage

Sistem başlangıçta küçük bir ekip tarafından geliştirilecek ve işletilecektir.

Bu nedenle ilk production deployment yaklaşımı:

- Düşük operasyon yükü,
- Kolay staging ortamı,
- Container tabanlı paketleme,
- Private servis iletişimi,
- Kolay rollback,
- Sağlayıcıya aşırı bağımlı olmama

hedefleriyle tasarlanacaktır.

İlk aşamada Kubernetes, service mesh veya çok bölgeli deployment gibi operasyonel olarak ağır yapılar kullanılmayacaktır.

## 2. İlk deployment platformu

M4Trust’ın ilk staging ve production deployment platformu:

`Railway`

olacaktır.

Önceki tek Linux sunucu ve Docker Compose tabanlı production yaklaşımı kullanılmayacaktır.

Docker Compose yalnız local development ve gerektiğinde local integration ortamı için kullanılacaktır.

Railway platform tercihi:

- Uygulama mimarisinin kalıcı bir cloud lock-in’e girmesi,
- Railway’e özel business logic yazılması,
- Standart protokollerin terk edilmesi

anlamına gelmez.

Uygulamalar OCI-compatible container image ve standart network protokolleriyle taşınabilir tutulacaktır.

## 3. Ortamlar

Kalıcı ortamlar:

`local`

`staging`

`production`

olacaktır.

CI işlemlerinde ayrıca kısa ömürlü ortamlar kullanılabilir:

`ci`

`ephemeral`

### 3.1 Local

Local ortam:

- Geliştirici bilgisayarında çalışır.
- Docker Compose kullanabilir.
- Reset ve seed işlemlerine izin verir.
- Mock AI Worker kullanabilir.
- Gerçek production secret’ları içermez.
- Production dış servislerine varsayılan olarak bağlanmaz.

### 3.2 Staging

Staging:

- Railway üzerinde çalışır.
- Production mimarisine mümkün olduğunca benzer.
- Ayrı PostgreSQL kullanır.
- Ayrı RabbitMQ kullanır.
- Ayrı object storage bucket veya namespace kullanır.
- Test verisi içerir.
- Production credential’larını kullanmaz.
- Frontend kabul testleri için ana paylaşımlı ortamdır.

### 3.3 Production

Production:

- Railway üzerinde çalışır.
- Gerçek kullanıcı ve business verisi içerir.
- Local reset veya seed komutlarına izin vermez.
- Mock AI Worker çalıştırmaz.
- Manual approval olmadan otomatik deploy edilmez.
Staging ve production aşağıdaki kaynakları paylaşmaz:

- PostgreSQL database
- RabbitMQ virtual host veya broker state
- Object storage bucket
- Session kayıtları
- Secret’lar
- Public domain
- AI provider credential’ları
## 4. Deployment topolojisi

Başlangıç Railway topolojisi:

```text
Internet
→ Railway public edge
→ Web Edge Service
├── /        → React frontend
└── /api/* → Spring Core API
```

```text
Railway private network
├── Spring Core API
```

```text
├── PostgreSQL
├── RabbitMQ
├── FastAPI AI API
└── FastAPI AI Worker
```

Object storage ayrı bir S3-compatible servis olabilir.

Mock AI Worker yalnız local ve açıkça seçilmiş staging testlerinde bulunabilir.

## 5. Public edge kararı

Public olarak internete açık ana servis:

Web Edge Service

olacaktır.

Web Edge Service:

- React static frontend dosyalarını sunar.
- /api/* isteklerini Spring Core API’ye proxy eder.
- HTTPS termination arkasında çalışır.
- Gerekli forwarded header’ları Spring’e iletir.
- FastAPI’ye public route tanımlamaz.
- RabbitMQ veya PostgreSQL’e public erişim sağlamaz.
Production public URL modeli:

https://<application-domain>/ https://<application-domain>/api/v1/...

olacaktır.

Kesin domain adı bu ADR ile belirlenmez.

## 6. Same-origin yaklaşımı

Frontend ve Spring public API production’da aynı origin üzerinden sunulacaktır.

Bu karar ADR-005’teki session ve CSRF yaklaşımıyla uyumludur.

Avantajları:

- CORS karmaşıklığını azaltır.
- Session cookie yönetimini sadeleştirir.
- CSRF davranışını sadeleştirir.
- Frontend’in Spring’e doğrudan public servis adresi bilmesini gerektirmez.
- Spring private network içinde tutulabilir.
Frontend Railway dışındaki başka bir platforma taşınırsa same-origin davranışı edge proxy veya custom domain yönlendirmesiyle korunmaya çalışılır.

## 7. Frontend deployment

İlk aşamada frontend Railway üzerinde çalıştırılacaktır.

Frontend için ayrı bir Railway service kullanılabilir:

`m4trust-web-edge`

Bu servis:

- React production build’ini oluşturur.
- Static dosyaları sunar.
- Spring API’ye reverse proxy yapar.
Static serving ve proxy için:

- Caddy,
- Nginx,
- veya eşdeğer küçük bir web server
kullanılabilir.

Kesin web server ürünü mimari contract değildir.

Gerekli davranışlar:

- SPA route fallback
- Static asset caching
- /api/* proxy
- Forwarded header aktarımı
- Request body size sınırları
- Security header’ları
- FastAPI’ye route bulunmaması
olacaktır.

## 8. Spring deployment

Spring Core API Railway üzerinde ayrı bir private service olarak çalışacaktır.

Önerilen service adı:

`m4trust-core-api`

Spring:

- Public domain’e doğrudan açılmayabilir.
- Web Edge Service üzerinden çağrılır.
- PostgreSQL’e private network üzerinden bağlanır.
- RabbitMQ’ya private network üzerinden bağlanır.
- Object storage’a S3-compatible API üzerinden erişir.
- FastAPI’ye synchronous inference çağrısı yapmaz.
- AI command ve result akışını RabbitMQ üzerinden yürütür.
Spring process’i stateless tasarlanacaktır.

Business state ve session state container filesystem’inde tutulmaz.

## 9. FastAPI deployment

FastAPI kod tabanı ayrı process rollerinde çalışacaktır.

Önerilen servisler:

`m4trust-ai-api`

`m4trust-ai-worker`

Aynı container image farklı start command’lerle kullanılabilir.

### 9.1 AI API

AI API:

- Operasyonel health endpoint’leri sağlar.
- Capability ve contract bilgisi sağlayabilir.
- Public frontend inference endpoint’i sağlamaz.
- Railway private network içinde kalır.
- Spring PostgreSQL’e erişmez.

### 9.2 AI Worker

AI Worker:

- RabbitMQ command queue’larını tüketir.
- AI pipeline’ını çalıştırır.
- Contract uyumlu completed veya failed event yayınlar.
- Business ve payment state’ini değiştirmez.
- Spring PostgreSQL’e erişmez.
- Gerekirse private teknik storage veya vector store kullanabilir.
FastAPI API ve worker bağımsız olarak ölçeklenebilir.

## 10. Mock AI Worker deployment

Mock AI Worker:

- Local development’ta kullanılabilir.
- Staging’de açık test amacıyla çalıştırılabilir.
- Production’da çalıştırılamaz.
- Production message queue’larına bağlanamaz.
- Gerçek AI Worker ile aynı anda aynı queue üzerinde kontrolsüz biçimde bulunmaz.
Staging’de mock ve gerçek AI worker arasında seçim:

- Ayrı queue,
- Ayrı environment,
- veya açık configuration
ile yapılmalıdır.

Production contract’ına mock scenario alanı eklenmez.

## 11. PostgreSQL

İlk deployment’ta Railway PostgreSQL kullanılacaktır.

PostgreSQL:

- Spring business source of truth’tür.
- Spring Session kayıtlarını tutar.
- Outbox ve inbox kayıtlarını tutar.
- Business audit kayıtlarını tutar.
- FastAPI tarafından doğrudan kullanılmaz.
- Public internete açık credential ile kullanılmaz.
- Railway private connection bilgileri üzerinden erişilir.
Staging ve production database’leri ayrıdır.

Local ortamda Railway PostgreSQL’e bağlanmak zorunlu değildir; local PostgreSQL container kullanılabilir.

## 12. PostgreSQL yüksek erişilebilirlik sınırı

İlk production aşamasında özel active-active veya multi-region PostgreSQL mimarisi kurulmayacaktır.

Başlangıç gereksinimleri:

- Kalıcı storage
- Otomatik backup
- Restore imkânı
- İzlenebilir database boyutu
- Connection limit takibi
- Migration kontrolü
olacaktır.

Kullanıcı, veri hacmi veya iş kritikliği arttığında managed PostgreSQL sağlayıcısı yeniden değerlendirilebilir.

Business kodu Railway PostgreSQL’e özel SQL veya extension’lara gereksiz yere bağımlı hâle getirilmeyecektir.

## 13. RabbitMQ

RabbitMQ Railway üzerinde private service olarak çalıştırılabilir.

RabbitMQ:

- Public internete açılmaz.
- Persistent volume kullanır.
- Durable exchange ve queue kullanır.
- Persistent message kullanır.
- Staging ve production’da ayrı tutulur.
- Management UI production’da public açılmaz.
RabbitMQ business source of truth değildir.

Broker state kaybı durumunda recovery yaklaşımı:

- PostgreSQL business state,
- transactional outbox,
- inbox,
- reconciliation
üzerinden kurulur.

RabbitMQ tek başına kalıcı business kayıt sistemi olarak görülmez.

## 14. Object storage

Production belge ve video storage’ı S3-compatible object storage olacaktır.

Object storage sağlayıcısı bu ADR ile kesin olarak seçilmez.

Gerekli production yetenekleri:

- Presigned upload
- Presigned download
- Server-side encryption
- Object versioning veya eşdeğer recovery
- Lifecycle/retention policy
- Private bucket
- Public bucket listing’in kapalı olması
- Object metadata desteği
- SHA-256 doğrulama akışı
Railway object storage ancak bu gereksinimleri yeterli şekilde karşıladığı doğrulanırsa production için kullanılabilir.

Aksi hâlde harici bir S3-compatible sağlayıcı kullanılacaktır.

Başlangıç mimarisi harici object storage kullanımını doğal olarak destekler.

## 15. Container image stratejisi

Ayrı deploy edilebilir image’lar:

`m4trust-web-edge`

`m4trust-core-api`

`m4trust-ai-service`

`m4trust-mock-ai-worker`

olabilir.

FastAPI API ve worker aynı image’i kullanabilir.

Image kuralları:

- Multi-stage Dockerfile
- Non-root runtime user
- Minimum runtime dependency
- Immutable image
- Source code’un build sırasında doğrulanması
- Secret’ın image içine gömülmemesi
- Environment-specific config’in image içine yazılmaması
- Git commit SHA ile trace edilebilir tag
- Production’da latest tag’ine güvenilmemesi
Aynı image mümkün olduğunda staging’den production’a promote edilir.

Production için source yeniden farklı şekilde build edilmez.

## 16. Railway build yaklaşımı

Railway native build mekanizmaları kullanılabilir.

Ancak repository içinde açık Dockerfile bulunması tercih edilir.

Amaç:

- Local ve Railway build davranışının yakın olması
- Başka sağlayıcıya geçişin kolay olması
- Runtime sürümünün açık olması
- Buildpack davranışına gizli bağımlılığın azalması
Spring, frontend ve FastAPI için bağımsız Dockerfile bulunabilir.

Railway’e özel configuration yalnız deployment katmanında tutulur.

## 17. Monorepo deployment

Sistem aynı monorepo içinde geliştirilecektir.

Railway service’leri ilgili root directory üzerinden build edilebilir.

Örnek repository yapısı:

frontend/ services/core-api/ services/ai-service/ tools/mock-ai-worker/ contracts/ infra/

Kesin klasör yapısı Platform Foundation planında kilitlenebilir.

Her Railway service yalnız ihtiyaç duyduğu klasör ve build context ile çalıştırılmalıdır.

Frontend değişikliği gereksiz yere AI worker build’i üretmemelidir.

## 18. Configuration yönetimi

Non-secret configuration environment variable üzerinden sağlanabilir.

Örnekler:

`SPRING_PROFILES_ACTIVE`

`DATABASE_HOST`

`DATABASE_PORT`

`DATABASE_NAME`

`RABBITMQ_HOST`

`RABBITMQ_PORT`

`OBJECT_STORAGE_ENDPOINT`

`OBJECT_STORAGE_BUCKET`

`CORS_ALLOWED_ORIGINS`

`SESSION_IDLE_TIMEOUT`

`SESSION_ABSOLUTE_TIMEOUT`

Configuration:

- Kod içine hard-code edilmez.
- Environment’a göre değişebilir.
- Eksik zorunlu config durumunda uygulama fail-fast davranır.
- Production default’u güvenli olmalıdır.
- Local kolaylığı production güvenliğini gevşetmez.
Railway’in runtime port değeri environment üzerinden alınır.

Uygulamalar belirli local veya production portuna mimari olarak sabitlenmez.

## 19. Secret yönetimi

Secret örnekleri:

`DATABASE_PASSWORD`

`RABBITMQ_PASSWORD`

`OBJECT_STORAGE_SECRET_KEY`

`AI_PROVIDER_API_KEY`

`EMAIL_PROVIDER_API_KEY`

Secret’lar:

- Git repository’ye eklenmez.
- Dockerfile içine yazılmaz.
- Image layer içine gömülmez.
- Frontend environment’ına verilmez.
- Client bundle’a eklenmez.
- Loglanmaz.
- Error response’a eklenmez.
- Staging ve production arasında paylaşılmaz.
Repository içinde yalnız placeholder içeren örnek dosya bulunabilir:

.env.example

Gerçek .env dosyaları Git tarafından ignore edilir.

Railway environment variable ve secret yönetimi başlangıçta kullanılabilir.

Daha sonra harici secret manager’a geçiş mümkündür.

## 20. Frontend environment kuralları

Frontend build’ine yalnız public olarak bilinmesinde sakınca olmayan değerler eklenebilir.

Örnek:

`PUBLIC_APP_NAME`

`PUBLIC_ENVIRONMENT`

`PUBLIC_RELEASE_VERSION`

Aşağıdakiler frontend bundle’a kesinlikle eklenmez:

- Database credential
- RabbitMQ credential
- Object storage secret key
- AI provider key
- Session secret
- Internal FastAPI URL
- Internal service credential
Frontend Spring API’yi production’da relative path ile çağıracaktır:

/api/v1

Bu sayede internal Spring service adresi browser’a açılmaz.

## 21. Database migration aracı

Spring business database migration’ları için:

`Flyway`

kullanılacaktır.

Kurallar:

- Her schema değişikliği versioned migration ile yapılır.
- Production schema manuel SQL ile sessizce değiştirilmez.
- Uygulanmış migration değiştirilmez.
- Yeni değişiklik için yeni migration eklenir.
- Migration isimlendirmesi repository standardına uyar.
- Development seed migration’ları production migration zincirine karıştırılmaz.
## 22. Local migration

Local ortamda Spring startup sırasında Flyway migration çalıştırabilir.

Akış:

PostgreSQL hazır

```text
→ Spring başlar
→ Flyway migrate
→ Spring ready olur
```

Local developer’ın her başlangıçta manuel migration komutu çalıştırması zorunlu değildir.

Local reset mekanizması yalnız local profile’da bulunur.

## 23. Staging ve production migration

Staging ve production migration’ları application rollout’tan önce kontrollü çalıştırılacaktır.

Tercih edilen Railway yaklaşımı:

Yeni deployment hazırlanır

```text
→ pre-deploy migration çalışır
→ migration başarılıysa yeni uygulama başlatılır
→ migration başarısızsa rollout durur
```

Migration komutu uygulamanın normal web process’i değildir.

Birden fazla Spring instance’ın aynı anda migration çalıştırmasına güvenilmez.

Production’da:

flyway clean

yasaktır.

## 24. Forward-only migration

Database migration yaklaşımı varsayılan olarak:

`forward-only`

olacaktır.

Database rollback migration’ı ana rollback yöntemi değildir.

Application rollback önceki immutable image’e dönmekle yapılır.

Bu nedenle migration’lar backward-compatible tasarlanmalıdır.

## 25. Expand–contract yaklaşımı

Breaking database değişiklikleri birden fazla release’e bölünür.

Örnek:

Release A

```text
→ yeni nullable column eklenir
→ eski ve yeni yapı birlikte çalışabilir
```

Release B

```text
→ uygulama yeni column’ı kullanır
→ gerekiyorsa backfill yapılır
```

Release C

```text
→ eski column kaldırılır
```

Tek release içinde aşağıdaki kırıcı işlemler varsayılan olarak yapılmaz:

- Kullanılan column’ı doğrudan silmek
- Kullanılan column adını doğrudan değiştirmek
- Type’ı backward-incompatible biçimde değiştirmek
- Eski application image’ını çalışamaz hâle getirmek
Bu yaklaşım Railway rollback davranışını güvenli hâle getirir.

## 26. Deployment pipeline

Hedef deployment akışı:

Feature geliştirme

```text
→ main branch
→ CI validation
→ immutable image build
→ staging deployment
→ frontend kabul testi
→ production için manual approval
→ production migration
→ production deployment
→ smoke test
```

Production her main push’unda otomatik olarak deploy edilmeyecektir.

Production deploy açık manual approval gerektirir.

Staging otomatik veya kontrollü şekilde güncellenebilir.

## 27. CI kapsamı

ADR-004’e uygun olarak CI aşırı test yükü oluşturmaz.

Başlangıç CI kontrolleri:

- Build
- Compile/type-check
- Contract validation
- Minimum kritik testler
- Container image build kontrolü
- Secret scanning
- Migration dosyası doğrulaması
- Frontend production build
- OpenAPI validation
olabilir.

Her deployment öncesi uzun ve geniş unit test paketi zorunlu değildir.

Belirleyici kabul testi staging üzerinde gerçek frontend akışıdır.

## 28. Release kimliği

Her deploy edilen sürüm en az şu bilgilerle tanımlanmalıdır:

gitCommitSha buildVersion deploymentEnvironment buildTime

Bu bilgi:

- Health veya info endpoint’inde,
- Log metadata’sında,
- Frontend diagnostic bilgisinde
görülebilir.

Kullanıcıya gereksiz internal bilgi açmadan destek ekibinin deploy edilen commit’i tespit etmesi sağlanır.

## 29. Health endpoint’leri

Spring:

/livez /readyz

veya eşdeğer management path’leri sağlar.

FastAPI:

/health/live /health/ready

sağlar.

Web Edge Service de temel health endpoint’i sağlayabilir.

## 30. Liveness

Liveness yalnız process’in sağlıklı çalışıp çalışmadığını gösterir.

Liveness kontrolü aşağıdaki dış dependency’lere bağlanmaz:

- PostgreSQL
- RabbitMQ
- Object storage
- AI provider
- Email provider
Dış dependency problemi nedeniyle bütün process’in sürekli restart edilmesi engellenir.

Liveness başarısızlığı process’in kendi içinde kilitlenmesi veya çalışamaz hâle gelmesi anlamına gelmelidir.

## 31. Readiness

Readiness servisin trafik kabul edip edemeyeceğini gösterir.

Spring readiness için PostgreSQL bağlantısı zorunludur.

RabbitMQ veya object storage problemi:

- Bütün Spring API’yi otomatik olarak unready yapmak zorunda değildir.
- İlgili capability’nin kontrollü hata üretmesine neden olabilir.
- Kritik işlevlere etkisi ayrıca health component olarak gösterilebilir.
AI Worker readiness için RabbitMQ bağlantısı gerekli olabilir.

AI API readiness, operasyonel endpoint’lerin çalışabilirliğini göstermelidir.

## 32. Logging

Bütün servisler loglarını:

`stdout`

`stderr`

üzerinden üretir.

Container filesystem’ine kalıcı application log dosyası yazılmaz.

Structured JSON logging tercih edilir.

Her teknik log mümkün olduğunda şu alanları içerir:

timestamp level service environment version correlationId message

Message flow loglarında gerektiğinde:

eventId jobId dealId tenantId

bulunabilir.

Hassas business veri gereksiz yere loglanmaz.

## 33. Loglarda yasaklı veriler

Aşağıdakiler loglanmaz:

- Parola
- Parola hash’i
- Session ID
- CSRF token
- Cookie içeriği
- Database credential
- RabbitMQ credential
- Object storage secret
- AI provider key
- Raw document içeriği
- Raw video içeriği
- Tam payment credential
- Gereksiz kişisel veri
Log masking güvenlik için destekleyici olabilir; secret’ın hiç loglanmaması ana yöntemdir.

## 34. Metrics ve tracing

İlk Platform Foundation aşamasında tam observability platformu zorunlu değildir.

Ancak servisler ileride:

- Micrometer
- OpenTelemetry
- Prometheus
- Loki
- ELK
- Railway veya cloud logging
ile entegre olabilecek şekilde tasarlanır.

Başlangıçta en az:

- Health
- Structured logs
- Correlation ID
- Release version
- Kritik queue/database bağlantı görünürlüğü
sağlanmalıdır.

Distributed tracing daha sonraki bir operasyonel geliştirme olabilir.

## 35. PostgreSQL backup

Production PostgreSQL için:

- Otomatik backup
- Belirli retention süresi
- Mümkünse point-in-time recovery
- Backup durumunun izlenmesi
- Periyodik restore testi
zorunludur.

Sadece backup alındığının görünmesi yeterli değildir.

Backup’ın gerçekten restore edilebildiği belirli aralıklarla doğrulanmalıdır.

Kesin RPO ve RTO değerleri production provider planı seçildiğinde belirlenecektir.

## 36. Object storage recovery

Production object storage için:

- Encryption
- Versioning veya eşdeğer recovery
- Retention policy
- Lifecycle policy
- Accidental deletion recovery
- Private access
beklenir.

Object storage yalnız raw file deposu değildir; sözleşme ve evidence kayıtları açısından kritik olabilir.

Bu nedenle production sağlayıcısı seçilirken yalnız maliyet dikkate alınmaz.

## 37. RabbitMQ recovery

RabbitMQ için:

- Persistent volume
- Durable queue
- Durable exchange
- Persistent message
- Dead-letter queue
- Queue depth gözlemi
uygulanacaktır.

Ancak RabbitMQ backup’ı business recovery’nin ana yöntemi değildir.

Asıl güvence:

PostgreSQL state + outbox + inbox + idempotent consumer + reconciliation

yaklaşımıdır.

## 38. Rollback

Application rollback:

`current image`

```text
→ previous immutable image
```

şeklinde yapılır.

Rollback sırasında:

- Önceki image tag’i korunur.
- Deploy edilen commit bilinir.
- Database schema önceki application ile uyumlu kalmalıdır.
- Destructive migration rollout ile aynı anda yapılmaz.
Rollback gerektiğinde database’i otomatik olarak eski migration’a döndürmek varsayılan yöntem değildir.

## 39. Production deployment approval

Production deployment:

- Otomatik ve kontrolsüz değildir.
- Manual approval ister.
- Staging kabul kontrolünden sonra yapılır.
- Migration sonucu doğrulanır.
- Deployment sonrasında smoke test yapılır.
Smoke test en az:

- Frontend açılıyor
- Spring health başarılı
- Login endpoint’i erişilebilir
- Database bağlantısı sağlıklı
- Gerekli internal servisler beklenen durumda
kontrollerini içerebilir.

## 40. Scaling yaklaşımı

Başlangıçta servisler düşük replica sayısıyla çalışabilir.

Stateless tasarım sayesinde ileride:

- Spring replica artırılabilir.
- FastAPI worker replica artırılabilir.
- Frontend edge replica artırılabilir.
Session PostgreSQL’de olduğu için Spring horizontal scaling sırasında kullanıcı session’ları tek instance’a bağlı değildir.

AI Worker scaling yapılırken RabbitMQ consumer concurrency ve AI provider limitleri dikkate alınır.

## 41. Railway port politikası

Servisler Railway tarafından verilen runtime port değerini kullanır.

Port:

- Kod içine hard-code edilmez.
- Production mimari kararı olarak sabitlenmez.
- Environment/config üzerinden alınır.
- Container içinde gerektiğinde default local değer bulunabilir.
Local örnek portlar dokümantasyonda bulunabilir ancak mimari contract değildir.

## 42. Domain ve TLS

Production public domain Railway custom domain veya eşdeğer edge mekanizması üzerinden sunulabilir.

HTTPS zorunludur.

TLS termination public edge’de yapılır.

Spring doğru forwarded header’ları işleyerek:

- Secure cookie
- Scheme
- Host
- Redirect
davranışlarını doğru üretmelidir.

FastAPI için public custom domain oluşturulmaz.

## 43. Platform bağımsızlığı

Railway ilk deployment sağlayıcısıdır ancak aşağıdaki standartlar korunur:

- OCI container
- PostgreSQL
- RabbitMQ
- S3-compatible object storage
- HTTP
- OpenAPI
- AsyncAPI
- Environment-based config
- Standard health endpoints
Railway’e özel API veya SDK business domain katmanına eklenmez.

Gelecekte başka bir platforma geçiş deployment configuration değişikliği olmalı; business logic rewrite gerektirmemelidir.

## 44. Başlangıçta yapılmayacaklar

İlk aşamada aşağıdakiler yapılmayacaktır:

- Kubernetes
- Helm
- Service mesh
- Multi-region active-active
- Active-active PostgreSQL
- Blue/green zorunluluğu
- Canary deployment zorunluluğu
- Full distributed tracing platformu
- Her serviste bağımsız public domain
- Public FastAPI ingress
- Production Mock AI Worker
- Frontend’i zorunlu olarak Vercel’e taşıma
- Railway’e özel business model geliştirme
Bu kararlar gelecekte ihtiyaç oluşursa yeniden değerlendirilebilir.

## 45. Deferred konular

Aşağıdaki kararlar production yaklaşımı ilerledikçe kesinleştirilebilir:

- Kesin public domain
- Frontend web server olarak Caddy veya Nginx seçimi
- Harici object storage sağlayıcısı
- Railway RabbitMQ yerine managed broker kullanımı
- Kesin backup retention süresi
- RPO ve RTO hedefleri
- Merkezi logging sağlayıcısı
- Error monitoring sağlayıcısı
- Metrics dashboard
- Otomatik staging deployment tetikleyicisi
- Blue/green veya canary deployment
- Frontend’in gelecekte farklı platforma taşınması
Bu konular Platform Foundation geliştirmesini bloklamaz.

## 46. Yasaklanan yaklaşımlar

Bu ADR ile aşağıdaki yaklaşımlar yasaklanmıştır:

- Production’ı geliştirici bilgisayarından manuel Docker Compose ile yönetmek
- FastAPI’yi public internete açmak
- PostgreSQL ve RabbitMQ’yu public credential ile erişilebilir yapmak
- Mock AI Worker’ı production’da çalıştırmak
- Production’da latest image tag’ine güvenmek
- Secret’ı repository’ye veya Docker image’a koymak
- Backend secret’ını frontend build’ine eklemek
- Her Spring replica’nın kontrolsüz biçimde Flyway migration çalıştırmasına güvenmek
- Production’da flyway clean kullanmak
- Uygulanmış migration dosyasını değiştirmek
- Breaking database değişikliğini tek rollout içinde yapmak
- Container filesystem’ini business storage olarak kullanmak
- Session state’ini yalnız application memory’de tutmak
- Raw belge ve credential bilgilerini loglamak
- Production deploy’u staging kontrolü olmadan her main push’unda otomatik yapmak
- Railway’e özel business logic yazmak
- Object storage’ı public bucket olarak açmak
## 47. Kabul edilen ana kararlar

```text
Initial deployment platform → Railway
Local orchestration                → Docker Compose kullanılabilir
Persistent environments            → local, staging, production
Public edge                        → Railway Web Edge Service
Frontend                           → Başlangıçta Railway
Public origin                      → Frontend ve Spring same-origin
Spring                             → Railway private service
FastAPI API                        → Railway private service
FastAPI Worker                     → Railway private service
Mock AI Worker                     → Local/staging only
PostgreSQL                         → Railway PostgreSQL
RabbitMQ                           → Railway private service + persistent storage
Object storage                     → S3-compatible, provider sonra seçilecek
Container strategy                 → Explicit Dockerfile + immutable image
Image identity                     → Git commit SHA ile izlenebilir
Config                             → Environment tabanlı
Secrets                            → Repository ve image dışında
Migration                          → Flyway
Production migration               → Pre-deploy kontrollü migration
Rollback                           → Previous image + expand–contract schema
Health                             → Ayrı liveness ve readiness
Logs                               → stdout/stderr structured logs
Production deploy                  → Manual approval
Kubernetes                         → Başlangıçta yok
Platform portability               → Standart protokoller ve OCI image korunacak
```

## 48. Sonuçlar

Olumlu sonuçlar:

- İlk production ortamı düşük operasyon yüküyle kurulabilir.
- Frontend ve Spring same-origin kalır.
- Cookie, CSRF ve CORS yapısı sadeleşir.
- Spring ve FastAPI private network içinde tutulur.
- Railway üzerinde staging ve production ayrılabilir.
- Container image’lar başka platformlara taşınabilir.
- Migration ve rollback davranışı kontrollü olur.
- Production Mock AI Worker riski engellenir.
- Frontend için ayrıca platform seçmek başlangıcı geciktirmez.
- FastAPI worker Spring’den bağımsız ölçeklenebilir.
Maliyetler:

- Railway maliyet ve limitleri takip edilmelidir.
- RabbitMQ persistent storage ve recovery ayrıca yönetilmelidir.
- Railway PostgreSQL backup özellikleri doğrulanmalıdır.
- Production object storage için ayrı sağlayıcı gerekebilir.
- Web Edge proxy configuration’ı yönetilmelidir.
- Platform büyüdüğünde daha gelişmiş observability ve orchestration gerekebilir.
Bu maliyetler hızlı ve kontrollü production başlangıcı için kabul edilmiştir.

## 49. Nihai karar

M4Trust:

- İlk staging ve production deployment platformu olarak Railway kullanacaktır.
- Local geliştirmede Docker Compose kullanabilecektir.
- Frontend’i başlangıçta Railway Web Edge Service üzerinden sunacaktır.
- Frontend ve Spring public API’yi aynı origin altında yayınlayacaktır.
- Spring’i Railway private service olarak çalıştıracaktır.
- FastAPI API ve worker process’lerini ayrı private servisler olarak çalıştıracaktır.
- FastAPI’yi frontend veya internet için public olarak açmayacaktır.
- Railway PostgreSQL’i ilk business database ve Spring Session store’u olarak kullanacaktır.
- RabbitMQ’yu private ve persistent service olarak çalıştıracaktır.
- Production raw file storage’ı için gerekli güvenlik ve recovery özelliklerine sahip S3-compatible object storage kullanacaktır.
- Mock AI Worker’ı yalnız local veya kontrollü staging testlerinde kullanacaktır.
- Uygulamaları immutable OCI-compatible container image’lar olarak paketleyecektir.
- Secret’ları repository, image ve frontend bundle dışında tutacaktır.
- Production database migration’larını kontrollü Flyway pre-deploy adımıyla çalıştıracaktır.
- Database değişikliklerinde forward-only ve expand–contract yaklaşımını uygulayacaktır.
- Production rollback’ını önceki immutable image üzerinden yapacaktır.
- Liveness ve readiness kontrollerini birbirinden ayıracaktır.
- Structured logları stdout/stderr üzerinden üretecektir.
- Production deployment için staging kabulü ve manual approval isteyecektir.
- Başlangıçta Kubernetes veya multi-region deployment kullanmayacaktır.
- Railway platformuna rağmen container, PostgreSQL, RabbitMQ, S3 ve HTTP standartları üzerinden taşınabilir kalacaktır.
