# ADR-022: Railway-Only Controlled Demo Runtime

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Main repository Core/Web için mevcut Railway projesinde kontrollü demo
  çalıştırma, dağıtım ve rollback sınırı
- Geçici olarak değiştirdiği kararlar:
  - ADR-005 §22.2'deki yeni login throttling implementasyonu kontrollü demo için
    ertelenir; aynı ADR'nin session, CSRF, cookie, password, generic login error ve
    server-side authorization kuralları aynen korunur.
  - ADR-007 §§3–4 ve 13–14'teki RabbitMQ zorunluluğu ile server-side encryption,
    lifecycle ve broad-production storage recovery beklentileri kontrollü demo için
    ertelenir. Private PostgreSQL, private bucket, versioning, secrets, migration,
    health, manual production approval ve no-`latest` kuralları korunur.
  - ADR-016'nın EU West, AWS S3, Postmark, Grafana Cloud, iki replica,
    build-once registry promotion, release manifest, PITR/RPO/RTO ve yedi günlük
    production pilot zorunlulukları kontrollü demo için ertelenir.
  - ADR-017'nin invite-only registration, account/member invitation, password
    recovery, login throttling, Postmark ve operator bootstrap kapsamı kontrollü
    demo için ertelenir; ADR-005'teki mevcut register/login/session akışı korunur.
  - ADR-018'in AWS/GuardDuty quarantine ve clean-scan gate'i kontrollü demo için
    ertelenir; mevcut size, media type, SHA-256 ve immutable object-version
    doğrulamaları korunur.
  - ADR-020'nin GHCR/release-manifest promotion hattı kontrollü demo için
    ertelenir; exact source revision ve Railway deployment/image kimliği kanıtı
    korunur.
- Değiştirmediği kararlar:
  - ADR-001/002/019 AI ownership ve shared-contract sınırları.
  - Yukarıdaki dar demo istisnaları dışında ADR-003/005/006/007 domain, session,
    CSRF, authorization, migration, secret, private database ve no-`latest`
    güvenlik kuralları.
  - Gerçek para yasağı ve `DEMO_SIMULATED` sınırı.

## 1. Bağlam

Mevcut `m4trust-staging` Railway projesinde staging ve production environment'ları
zaten vardır. Staging'de web-edge, Core ve PostgreSQL çalışır; production'da
PostgreSQL ile başarısız/kapalı bir MinIO denemesi ve kalıcı volume bulunur.
Staging Core ile web farklı source revision'lardan deploy edilmiştir. Current Core
Dockerfile monorepo root build context'i isterken live Railway Core service root'u
`/services/core-api` olarak kalmıştır.

ADR-016–018 ve ADR-020 geniş production açılışı için doğru fakat mevcut hedef olan
küçük, kontrollü Railway demosu için gereksiz dış provider ve operasyon yükü
oluşturur. Bu karar geniş production güvenliği iddiası üretmeden mevcut projeyi
basit ve geri alınabilir biçimde çalıştırmayı hedefler.

## 2. Karar

### 2.1 Teslimat sınıfı

Bu fazın çıktısı `RAILWAY_DEMO_READY` durumudur; `PROD_READY`, geniş production veya
malware-safe iddiası değildir. Demo URL'si internetten erişilebilir olsa da kullanım
sınırlı ve kontrollüdür. Geniş kullanıcı açılışı ADR-016–018 kapsamının yeniden
kabulüne veya onları değiştiren ayrı bir production-hardening ADR'sine bağlıdır.

### 2.2 Mevcut authentication korunur

Mevcut `register`, `login`, `logout`, `auth/me`, CSRF ve PostgreSQL-backed session
davranışı değiştirilmez. Invite-only onboarding, password reset, transactional
e-mail, Postmark, yeni login throttle, member-invitation ve operator bootstrap bu
fazda yapılmaz. Demo owner'ı açık registration'ın internet-facing riskini kabul
eder; uygulama bunu production-grade onboarding olarak sunmaz.

ADR-017/018 için P1'de eklenmiş fakat hiçbir endpoint/runtime davranışı tarafından
kullanılmayan unreleased error enum değerleri, bu karar kabul edildikten sonra
OpenAPI, Java ve generated frontend kataloglarından birlikte kaldırılır. Existing
Deal invitation kodları bu temizlik kapsamında değildir.

### 2.3 Railway topolojisi

Ayrı bir project kurulmaz; mevcut `m4trust-staging` project'inin `staging` ve
`production` environment'ları kullanılır. Mevcut US West yerleşimi demo boyunca
korunur; region migration yapılmaz. Başlangıçta her main service tek replica'dır.

```text
public:  m4trust-web-edge
private: m4trust-core-api
private: PostgreSQL
public authenticated S3 endpoint: Railway-hosted MinIO API
private: MinIO console/management
```

Browser yalnız web-edge ve kısa ömürlü presigned MinIO URL'lerini görür. MinIO
bucket private kalır; anonymous listing/read/write kapalıdır. Yalnız S3 API portu
presigned browser upload/download için public olabilir. Console public olmaz,
exact web origin CORS uygulanır ve bucket versioning açık olmak zorundadır.

Staging ve production ayrı PostgreSQL, MinIO volume/bucket ve secret kullanır.
Mevcut production MinIO volume'u ile staging orphan volume'u silinmez veya resetlenmez.
Production MinIO ancak mevcut veri/credential/versioning durumu non-destructive
doğrulanabiliyorsa reuse edilir; aksi halde yeni service+volume oluşturulur ve eski
resource dokunulmadan bırakılır.

RabbitMQ ve AI servisleri main demo deployment'ının zorunlu parçası değildir.
AI-enabled akış iddia edilecekse RabbitMQ/private transport ve AI capability,
ADR-019 uyarınca AI owner ile ayrı uyumluluk kanıtı gerektirir. Main ekip AI image,
worker, provider, model veya deployment'ı değiştirmez.

### 2.4 Storage güvenlik sınırı

Demo, mevcut S3-compatible adapter ve direct-upload contract'ını kullanır:
benzersiz key, private bucket, short-lived presign, exact size/media type/SHA-256 ve
immutable `versionId` doğrulaması zorunludur. Scan veya malware-clean iddiası yoktur.
Kullanıcı dosyaları yalnız demo verisi olmalı; bilinmeyen/gerçek production verisi
bu gate altında kabul edilmez.

Railway native bucket bu fazda kullanılmaz; mevcut public contract non-null
immutable `versionId` ister ve MinIO versioning bu davranışı değişikliksiz sağlar.

### 2.5 Build, release ve rollback

Railway native Git/Dockerfile deployment kullanılır. Core monorepo root context'inden,
web repository root'tan build edilir. Staging ve production aynı exact main commit
SHA'dan deploy edilir; Railway'in her environment için ürettiği deployment ID ve
image digest ayrı ayrı kaydedilir. `latest`, unpinned source veya environment'ta
manuel kod farkı kullanılmaz.

Bu demo için GHCR promotion, SBOM/provenance ve release-manifest hattı kurulmaz.
Rollback Railway'de kayıtlı önceki başarılı deployment kimliğine veya aynı exact
commit'in yeniden deployment'ına yapılır. Flyway yalnız pre-deploy command'da,
forward-only çalışır; applied migration değiştirilmez ve `flyway clean` yoktur.

### 2.6 Configuration ve secret

Repo yalnız variable isimleri ve güvenli örnekleri taşır; değerler Railway secret
store'dadır. Core startup en az database, object storage, secure session/origin,
release revision ve environment kimliğini doğrular. Messaging yalnız açıkça enabled
ise RabbitMQ config'ini zorunlu kılar; disabled capability mock/fallback üretmez.

Core/PostgreSQL public domain veya TCP proxy almaz. Web same-origin `/api/v1` proxy
olur. MinIO credential, database credential, probe token ve internal origin frontend
bundle'a veya loglara girmez.

### 2.7 Operasyon sınırı

Railway health, deployment logları ve resource metrics demo için yeterlidir;
Grafana Cloud/OTel/Faro kurulmaz. Production demo deployment'ından önce mevcut
PostgreSQL ve MinIO persistent resource'ları için non-destructive backup/snapshot
kanıtı alınır. RPO/RTO veya PITR iddiası yapılmaz.

İlk production demo deploy'u manual approval ile yapılır. Resource silme, volume
detach/reset, bucket purge veya credential rotation ayrı açık kullanıcı onayı ister.

### 2.8 UI/UX insertion gate

Main repo code/config reconciliation kabul edilince deployment başlamaz. Planner
önce kullanıcıya “yalnız deployment kaldı” bilgisini verir. Kullanıcının ayrı UI/UX
revizyon planı uygulanıp kabul edildikten sonra staging ve production deployment
paketleri açılır.

### 2.9 Test politikası

Her implementasyon ve review paketi yalnız değişen davranışın en küçük contract,
unit/config/image smoke testlerini çalıştırır. Repository-wide Core/frontend full
suite yalnız final pre-production gate'te bir kez çalıştırılır.

## 3. Sonuçlar

- Mevcut çalışan auth ve domain slice'ları yeniden yazılmaz.
- Ana ekip yalnız backend, frontend, shared contracts ve onların Railway deployment'ını
  yönetir; AI tarafına yapısal karar veya write scope'u çıkmaz.
- AWS, Postmark, GuardDuty, Grafana Cloud, GHCR ve yeni release sistemi demo yolundan
  çıkar.
- Railway-hosted MinIO contract değişikliği olmadan exact-version storage sağlar.
- Demo daha hızlı açılır; buna karşılık invite-only, malware scan, güçlü recovery
  SLO'su, at-rest encryption/lifecycle kanıtı ve broad-production supply-chain
  kanıtı bilinçli olarak yoktur.

## 4. Kabul kapıları

- ADR-022 founder/user tarafından açıkça Accepted yapılmıştır.
- Core Railway config'i monorepo-root Docker build'i ve `contracts/**` watch kapsamını
  doğru kullanır.
- Unreleased ADR-017/018-only error değerleri bütün üç consumer'dan birlikte kaldırılır;
  existing auth ve Deal invitation contract'ı değişmez.
- Staging web/Core aynı source SHA ile çalışır; Core/DB private kalır.
- Staging ve production MinIO bucket'ları private, versioned ve exact-CORS'tur;
  public console yoktur.
- Gerçek browser register/login/session ve document/evidence upload/download akışı
  staging'de geçer.
- Final full gate bir kez geçer; production demo manual deploy, smoke ve rollback
  kanıtı üretir.
- Sonuç yalnız `RAILWAY_DEMO_READY` olarak kaydedilir.
