# ADR-016: Main Application Production Runtime, Release, and Recovery

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kısmen değiştirildi: ADR-020, release manifest ile image kimliği arasındaki
  döngüsel label şartını tek yönlü post-build manifest bağıyla değiştirmiştir.
- Kapsam: Main repository'deki Core backend, web frontend ve bunların production
  deployment/operasyon sınırı
- Değiştirdiği kararlar:
  - ADR-007 §§3, 12, 14, 16, 18, 26-40 ve 45'te yalnız main-owned servisler için
    deferred bırakılan production seçimlerini bu ADR'deki başlangıç modeliyle
    kapatır; aynı bölümlerin AI-specific kısımları ADR-019 gereği kapsam dışıdır.
  - ADR-001 §16'daki secure internal transport için main-owned Railway
    servislerinde private-network mode tanımlar; service authentication ve least
    privilege zorunluluğu korunur.
- Değiştirmediği kararlar:
  - ADR-001/ADR-002'deki Spring–FastAPI ownership ve shared-contract sınırları.
  - AI repository'sinin provider, model, runtime, worker, image, replica, scaling,
    readiness, CI/CD veya deployment kararları bu ADR'nin yetkisinde değildir.
- Bağlı kararlar: ADR-001, ADR-002, ADR-005, ADR-006, ADR-007, ADR-015,
  ADR-017, ADR-018, ADR-019
- Platform referansları:
  - https://docs.railway.com/deployments/regions
  - https://docs.railway.com/networking/private-networking
  - https://docs.railway.com/volumes/point-in-time-recovery

## 1. Bağlam

Slice 7 yalnız web, Core ve PostgreSQL staging kabulü sağlamıştır. Sonraki
slice'larda Core'un RabbitMQ ve object-storage entegrasyonu büyümüş; production
image promotion, restore, main release identity ve gözlemlenebilirlik exact olarak
sabitlenmemiştir.

AI servisi ayrı repository ve ayrı ekip ownership'indedir. Main ekip AI tarafında
yapısal karar alamaz veya değişiklik görevi veremez. Main ekibin yetkisi, committed
shared contract, Spring producer/consumer davranışı, frontend projection'ları ve
main deployment'ın entegrasyon güvenliğiyle sınırlıdır. AI tarafında görülen bir
risk öneri/uyumsuzluk raporu olarak AI sahibine iletilir.

## 2. Karar

### 2.1 Environment ve region izolasyonu

Persistent ortamlar `local`, `staging`, `production` olarak kalır. Staging ve
production main deployment'ları hiçbir database, broker vhost/user, S3 bucket/KMS
key veya notification stream paylaşmaz.

Başlangıç region'ları:

- Railway main services: `EU West Metal` (`europe-west4-drams3a`);
- AWS S3/KMS/GuardDuty: `eu-central-1`;
- başka region ancak data-residency/latency review ve accepted config change ile.

Production reset/seed/mock profile'ları fail-fast reddeder.

### 2.2 Main production topology

Bu ADR'nin yönettiği servis ve altyapı:

```text
public:  web-edge (Caddy + static React)
private: core-api
private: PostgreSQL
private: RabbitMQ
external private-data service: AWS S3/KMS/GuardDuty
external main providers: Postmark, Grafana Cloud
```

Browser yalnız web-edge'i bilir. Caddy same-origin `/api/v1/**` ve açıkça
allowlisted Postmark webhook path'ini Core private domain'ine proxy eder. Core ve
PostgreSQL için public Railway domain/TCP proxy açılmaz. RabbitMQ public ve
şifresiz biçimde açılmaz; AI ekibinin shared broker'a bağlanması gerekiyorsa
transport, credential/ACL ve contract compatibility iki owner tarafından ayrıca
kabul edilir. Bu kabul AI deployment topolojisi belirlemez.

Başlangıç replica policy:

- web-edge: 2;
- core-api: 2;
- PostgreSQL/RabbitMQ: accepted Railway managed/single-service başlangıç modeli
  ve aşağıdaki recovery kontrolleri.

AI API/worker replica, concurrency, network placement ve scaling bu ADR'nin
dışındadır.

### 2.3 Secure internal transport mode

`INTERNAL_TRANSPORT_MODE` main production'da closed enum'dır:

- `PRIVATE_WIREGUARD`: yalnız aynı Railway project/environment private DNS ve
  private networking üzerinde kullanılabilir;
- `TLS`: platform private-network guarantee'i yoksa zorunludur.

`PRIVATE_WIREGUARD` network encryption sağlar fakat authentication yerine
geçmez. RabbitMQ ayrı non-guest users/vhost/ACL, PostgreSQL ayrı
application/migration credentials kullanır. Public TCP proxy veya private domain
dışındaki cleartext broker URL main production startup'ını durdurur.

### 2.4 Main artifacts ve release identity

Main ekibin yönettiği iki first-party OCI artifact vardır:

- `web`;
- `core`.

Base images digest'e pinlenir; runtime non-root'tur. Image bir kez build edilir,
GHCR'a immutable digest ile publish edilir ve aynı digest staging'den production'a
promote edilir. `latest`, mutable environment tag, production platform rebuild ve
auto-update yasaktır.

Main release manifesti şunları taşır:

- main repository commit SHA;
- `web` ve `core` image digest'leri;
- deterministic main contract bundle digest;
- highest Flyway migration;
- build time/version.

Manifest, image label'larındaki build-time kimlikler, safe health/info projection
ve deployment evidence ADR-020'nin tek yönlü bağıyla doğrulanır. AI repository
SHA'sı, model/provider revision'ı veya AI image digest'i main release manifestinin
owned artifact alanı değildir. AI-enabled
acceptance gerekiyorsa AI sahibinin ayrıca verdiği opaque evidence reference main
release kaydına dış bağımlılık olarak eklenebilir; main ekip bu kanıtın iç yapısını
veya AI implementation'ını belirlemez.

### 2.5 Build context ve main contract bundle

Core image monorepo root context'inden build edilir; reviewed
`contracts/schemas/**` build artifact içine `contracts/schemas/**` classpath'iyle
girer. Image smoke testi bütün main-runtime schema'larını load/parse eder ve
digest'i doğrular.

Bundle semantic inclusion seti yalnız şunlardır:

```text
contracts/asyncapi/**/*.{json,yaml,yml}
contracts/openapi/**/*.{json,yaml,yml}
contracts/schemas/**/*.json
contracts/examples/**/*.json
```

README/CHANGELOG/scripts/dependency files digest'e girmez. Her file SHA-256 exact
committed bytes üzerinden alınır. Path `contracts/` kökünden POSIX `/` ile,
ordinal artan sıralanır. UTF-8/LF manifest satırı exact:

```text
<lowercase-file-sha256><two spaces><relative-path>\n
```

`contractBundleDigest`, bu manifest bytes'ının `sha256:<lowercase-hex>` değeridir;
platform default encoding veya line ending kullanılamaz.

Core private, service-authenticated `GET /internal/v1/contracts` endpoint'inde
exact şu projection'ı döndürür:

```json
{
  "service": "core",
  "releaseRevision": "40-hex commit",
  "contractBundleDigest": "sha256:<64-hex>",
  "files": [{"path": "schemas/...", "sha256": "<64-hex>"}]
}
```

`files` aynı ordinal sıradadır; path bundle-relative'dir. Caddy `/internal/**`
public deny uygular ve private network tek başına auth sayılmaz. Endpoint
`Authorization: Bearer` ile environment-specific 32-byte CSPRNG
`CONTRACT_PROBE_TOKEN` ister; token yalnız secret store'dadır, constant-time
karşılaştırılır, loglanmaz ve rotation sırasında active + one previous token
bounded overlap destekler. Main image'ların release kimliği ve contract label'ları
ADR-020'ye tabidir; release-manifest digest'i image config'e gömülmez.

Main contract change'i için AI consumer compatibility read-only kontrol edilebilir.
Kontrol AI repository'sini değiştirmez; uyumsuzluk varsa merge/release durur ve
iki owner'a exact contract diff raporlanır. AI CI, image label, digest endpoint veya
consumer implementation'ı bu ADR tarafından emredilmez.

### 2.6 Configuration ve fail-fast guards

Production config environment/secret store'dan gelir. En az şu main-owned gruplar
startup validation'a tabidir:

- database ve migration connection;
- RabbitMQ host/port/vhost/user/password/transport;
- S3 region/bucket/KMS/credentials/CORS origins;
- session cookie, trusted host/origin ve forwarded-header policy;
- notification provider/domain/stream;
- release identity ve environment.

Local convenience default'u production'a taşınmaz. Main capability'nin
dependency/config'i eksikse capability sessizce mock/fallback açmaz; required
capability ise Core unready/startup failure olur.

AI provider/model configuration main Core'a eklenmez. Main deployment yalnız
shared-contract uyumluluğu kanıtlanmış dış AI capability'nin kullanılabilirlik
durumunu güvenli teknik failure olarak yansıtabilir; AI internals'ına göre business
branch yapmaz.

### 2.7 Edge security

Caddy yalnız Railway edge/private proxy kaynaklarını trusted proxy kabul eder.
Client-supplied forwarding header'lar güven otoritesi değildir. HTTPS ve secure
session cookie zorunludur. Response policy en az:

- HSTS;
- explicit CSP;
- `Permissions-Policy`;
- `X-Content-Type-Options: nosniff`;
- frame embedding denial;
- exact request-body limits;
- actuator/internal path public deny;
- credentialed CORS için exact origin allowlist.

### 2.8 Health, lifecycle ve stateful recovery

Liveness yalnız process health'tir. Core readiness PostgreSQL ile production'da
required olan RabbitMQ/S3/notification configuration ve dependency durumlarını
kapsar. Bu readiness AI worker/model/provider readiness iddiası taşımaz.

Scheduler/relay/listener bean'leri explicit feature/profile koşulludur. SIGTERM
yeni work claim'ini durdurur, bounded drain uygular ve durable
outbox/inbox/requeue recovery bırakır. Test lifecycle kapanışında background
scheduler kalamaz.

PostgreSQL:

- automated backup + Railway/PostgreSQL PITR;
- hedef RPO `<=15 dakika`, RTO `<=4 saat`;
- restore in-place değil sibling database + verified cutover;
- production öncesi ve en az üç ayda bir restore drill.

RabbitMQ:

- durable exchange/queue, persistent message, DLQ ve queue-age/depth alarms;
- Core business recovery PostgreSQL outbox/inbox/reconciliation'dır;
- AI consumer availability veya recovery implementation'ı main ekip tarafından
  tanımlanmaz; shared queue/contract gözlemi uyumsuzluğu görünür kılar.

Object storage recovery ADR-018'e tabidir.

### 2.9 Migration, rollout ve rollback

V1-V22 frozen kalır. Yeni migration'lar next available version ve forward-only
olur. Staging/production migration tek pre-deploy job ile çalışır; web/Core
replica'ları migration authority değildir. Breaking değişiklik en az iki
compatible release'lik expand-contract rollout gerektirir.

Promotion sırası:

```text
CI/attestation -> exact main digests staging deploy -> automated + browser acceptance
-> manual production approval -> pre-deploy migration -> exact main digest deploy
-> smoke/canary -> observation window
```

Application rollback previous exact main digest'tir. Schema/data recovery
gerektiğinde rollback migration veya current DB üstüne destructive write yapılmaz;
sibling PITR restore + cutover runbook'u izlenir.

### 2.10 Supply-chain ve CI gate

Main repository için required gate'ler:

- contract validator ve invalid fixtures;
- runtime/committed OpenAPI structural drift;
- generated TypeScript clean-diff;
- Core verify ve frontend typecheck/production build;
- migration clean/upgrade validation;
- `web`/`core` container build, smoke ve non-root check;
- secret, dependency, license ve vulnerability scan;
- SBOM ve provenance attestation.

Main runtime image'da unresolved Critical veya High vulnerability ve
unsigned/unpinned artifact production blocker'dır. Medium risk ancak owner + due
date + explicit waiver ile ilerler.

AI repository test, dependency lock, image, SBOM, provenance ve CI tasarımı bu
gate'in kapsamında değildir. Shared-contract read-only compatibility sonucu ayrı
entegrasyon kanıtıdır; başarısızsa exact diff AI sahibine raporlanır.

### 2.11 Observability, SLO ve incident operations

Core Micrometer/OTel, frontend privacy-filtered Faro ve synthetic checks Grafana
Cloud'a gönderilir. Main telemetry raw document/video/prompt, e-mail, token,
presigned URL, cookie, external payload veya credential içermez.

Main alert/runbook seti:

- public availability, 5xx ve latency;
- DB saturation/storage/backup;
- outbox age, Core-owned queue publish/result age ve DLQ;
- certificate and secret rotation;
- email bounce;
- rollback, DB/S3 restore ve external dependency degradation.

Pilot main SLO'ları:

- non-async public API p95 `<750 ms`;
- API 5xx `<0.5%`;
- contract drift, authorization disclosure ve malware bypass: sıfır.

AI end-to-end latency/provider/worker SLO'ları bu ADR tarafından seçilmez. Ürün
pilotunda AI-enabled akış zorunluysa user ve AI owner ayrı bir service-level
acceptance verir; main gözlem yalnız submitted/queued/result/timeout
projection'larını ve contract uyumluluğunu raporlar.

### 2.12 Main production pilot gate

İlk main production açılışı 7 gün, en fazla 3 legal entity ve 15 kullanıcıyla
sınırlı pilottur. Promotion için Postmark domain, S3/KMS/GuardDuty, backup/restore
ve main alert/runbook kanıtı gerekir. Pilot boyunca data loss, authorization
disclosure, contract drift, malware bypass veya real-money claim kabul edilmez.

AI-enabled ürün akışları pilot kapsamındaysa AI owner'ın sağladığı uyumlu staging
capability/evidence olmadan o akışlar production-ready sayılamaz. Main ekip bu
eksikliği mock ile kapatamaz, AI implementation kararı alamaz veya bütün sistem
AI-ready iddiası taşıyamaz.

Threshold ihlalinde pilot durdurulur ve exact main-digest rollback uygulanır.
Geniş açılış planner acceptance ve retention/legal sign-off olmadan yapılamaz.

## 3. Sonuçlar

- Local/CI başarı main production readiness yerine geçmez.
- Railway provider-specific business logic oluşmaz; standard OCI/PostgreSQL/
  RabbitMQ/S3/OpenAPI/AsyncAPI sınırları korunur.
- Main release yalnız `web` ve `core` artifacts'ını yönetir.
- AI repository'sinde karar/değişiklik görevi üretilmez; yalnız öneri veya exact
  shared-contract incompatibility raporu paylaşılır.
- Main production acceptance, AI service'in internal production readiness'i
  hakkında iddia oluşturmaz.

## 4. Kabul kapıları

- Web/Core/PostgreSQL/RabbitMQ/S3 main topology staging'de çalışır.
- Main release manifest, exact image digest'leri ve contract label'ları ADR-020'ye
  göre tek yönlü doğrulanır.
- Core broker disconnect/reconnect, duplicate result, scheduler SIGTERM, DB
  restore ve S3 restore drill'leri geçer.
- AI team capability kullanılıyorsa shared-contract uyumluluğu read-only olarak
  doğrulanır; mismatch exact diff ile AI owner'a bildirilir.
- Staging aynı main artifact digest'leriyle browser acceptance ve rollback kanıtı
  üretir.
- Pilot exit checklist planner tarafından ayrıca kabul edilir; implementer code
  completion pilot acceptance değildir.
