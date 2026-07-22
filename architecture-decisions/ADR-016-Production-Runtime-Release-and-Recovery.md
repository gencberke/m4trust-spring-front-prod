# ADR-016: Production Runtime, Release, and Recovery

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Production topology, private transport, OCI artifacts, environment
  guards, promotion, migration, rollback, backup/restore, observability and pilot
- Değiştirdiği kararlar:
  - ADR-007 §§3, 9, 12, 14-18, 26-40 ve 45'te deferred bırakılan production
    seçimlerini bu ADR'deki exact başlangıç modeliyle kapatır.
  - ADR-001 §16'daki secure internal transport için Railway private-network mode
    tanımlar; service authentication/least privilege zorunluluğu korunur.
- Bağlı kararlar: ADR-001, ADR-002, ADR-005, ADR-006, ADR-007, ADR-015,
  ADR-017, ADR-018, ADR-019
- Platform referansları:
  - https://docs.railway.com/deployments/regions
  - https://docs.railway.com/networking/private-networking
  - https://docs.railway.com/volumes/point-in-time-recovery

## 1. Bağlam

Slice 7 yalnız web, Core ve PostgreSQL staging kabulü sağlamıştır. RabbitMQ,
Redis, production object storage ve gerçek AI runtime topolojisi daha sonraki
slice'larda local/CI düzeyinde kalmıştır. Production image promotion, restore,
cross-repo release identity ve gözlemlenebilirlik exact olarak sabit değildir.

## 2. Karar

### 2.1 Environment ve region izolasyonu

Persistent ortamlar `local`, `staging`, `production` olarak kalır. Staging ve
production hiçbir database, broker vhost/user, Redis namespace/credential, S3
bucket/KMS key, provider project/key veya notification stream paylaşmaz.

Başlangıç region'ları:

- Railway services: `EU West Metal` (`europe-west4-drams3a`);
- AWS S3/KMS/GuardDuty: `eu-central-1`;
- başka region ancak data-residency/latency review ve accepted config change ile.

Production reset/seed/mock profile'ları fail-fast reddeder.

### 2.2 Production service topology

First-party services:

```text
public:  web-edge (Caddy + static React)
private: core-api
private: ai-api
private: ai-worker
private: PostgreSQL
private: RabbitMQ
private: Redis
external private-data service: AWS S3/KMS/GuardDuty
external providers: Postmark, OpenAI, private Roboflow, Grafana Cloud
```

Browser yalnız web-edge'i bilir. Caddy same-origin `/api/v1/**` ve açıkça
allowlisted provider webhook path'ini Core private domain'ine proxy eder. Core,
AI API, RabbitMQ, Redis ve PostgreSQL için public Railway domain/TCP proxy
açılmaz. FastAPI public inference endpoint sunmaz.

Başlangıç replica policy:

- web-edge: 2;
- core-api: 2;
- ai-api: 2;
- ai-worker: 1, consumer concurrency/prefetch 1;
- stateful services: accepted Railway single-service model + recovery controls.

AI worker ancak measured queue/latency/provider limits sonucunda scale edilir.

### 2.3 Secure internal transport mode

`INTERNAL_TRANSPORT_MODE` production'da closed enum'dır:

- `PRIVATE_WIREGUARD`: yalnız aynı Railway project/environment private DNS ve
  private networking üzerinde kullanılabilir;
- `TLS`: platform private-network guarantee'i yoksa zorunludur.

`PRIVATE_WIREGUARD` network encryption sağlar fakat authentication yerine
geçmez. RabbitMQ ayrı non-guest users/vhost/ACL, Redis password/ACL, PostgreSQL
ayrı application/migration credentials kullanır. Public TCP proxy veya private
domain dışındaki cleartext broker/Redis URL production startup'ını durdurur.

### 2.4 Artifact ve release identity

Dört first-party OCI artifact vardır:

- `web`;
- `core`;
- `ai-api`;
- `ai-worker`.

Base images digest'e pinlenir; runtime non-root'tur. Image bir kez build edilir,
GHCR'a immutable digest ile publish edilir ve aynı digest staging'den production'a
promote edilir. `latest`, mutable environment tag, production platform rebuild
ve auto-update yasaktır.

Her release manifesti şunları taşır:

- main repository commit SHA;
- AI repository commit SHA;
- dört image digest;
- deterministic contract bundle digest;
- highest Flyway migration;
- model/provider revision manifest;
- build time/version.

Manifest ve digest'ler image label, safe health/info projection ve deployment
evidence'da eşleşir. Cross-repo mismatch promotion'ı durdurur.

### 2.5 Build context ve contract bundle

Core image monorepo root context'inden build edilir; reviewed
`contracts/schemas/**` build artifact içine `contracts/schemas/**` classpath'iyle
girer. Image smoke testi bütün schema'ları load/parse eder ve digest'i doğrular.

AI producer/consumer image'ları aynı contract bundle digest'ini taşır. Main
contract-change PR'ı AI consumer'ı, her AI PR'ı pinned main contract authority'yi
doğrular. Weekly drift job destekleyicidir; per-PR gate'in yerine geçmez.

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

Core ve AI API private, service-authenticated `GET /internal/v1/contracts`
endpoint'inde exact şu projection'ı döndürür:

```json
{
  "service": "core|ai-api",
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
bounded overlap destekler. Dört image
`org.opencontainers.image.revision`, `io.m4trust.contract-bundle-digest` ve
`io.m4trust.release-manifest-digest` OCI label'larını taşır.

### 2.6 Configuration ve fail-fast guards

Production config environment/secret store'dan gelir. En az şu gruplar startup
validation'a tabidir:

- database ve migration connection;
- RabbitMQ host/port/vhost/user/password/transport;
- Redis host/port/ACL/transport;
- S3 region/bucket/KMS/credentials/CORS origins;
- session cookie, trusted host/origin ve forwarded-header policy;
- notification provider/domain/stream;
- AI contract digest, provider projects/keys, model manifest;
- release identity ve environment.

Local convenience default'u production'a taşınmaz. Production capability'nin
dependency/config'i eksikse capability sessizce mock/fallback açmaz; required
capability ise service unready/startup failure olur.

### 2.7 Edge security

Caddy yalnız Railway edge/private proxy kaynaklarını trusted proxy kabul eder.
Client-supplied forwarding header'ları güven otoritesi değildir. HTTPS ve secure
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

Liveness yalnız process health'tir. Readiness:

- Core için PostgreSQL ve production-required capability configuration;
- AI API için contract/model/provider configuration;
- AI worker için RabbitMQ, Redis, contract bundle ve offline models loaded
  durumunu kapsar.

Scheduler/relay/consumer bean'leri explicit feature/profile koşulludur. SIGTERM
yeni work claim'ini durdurur, bounded drain uygular ve lease/requeue recovery
bırakır. Test lifecycle kapanışında background scheduler kalamaz.

PostgreSQL:

- automated backup + pgBackRest/WAL PITR;
- hedef RPO `<=15 dakika`, RTO `<=4 saat`;
- restore in-place değil sibling database + verified cutover;
- production öncesi ve en az üç ayda bir restore drill.

RabbitMQ:

- durable exchange/queue, persistent message, DLQ ve queue-age/depth alarms;
- business recovery PostgreSQL outbox/inbox/reconciliation'dır.

Redis:

- ACL, AOF/persistent volume ve bounded TTL;
- business source of truth değildir; kayıp başarı icat etmez ve broker/outbox
  üzerinden fail-closed recovery uygulanır.

Object storage recovery ADR-018'e tabidir.

### 2.9 Migration, rollout ve rollback

V1-V22 frozen kalır. Yeni migration'lar next available version ve forward-only
olur. Staging/production migration tek pre-deploy job ile çalışır; web replica'lar
migration authority değildir. Breaking değişiklik en az iki compatible release'lik
expand-contract rollout gerektirir.

Promotion sırası:

```text
CI/attestation -> exact digest staging deploy -> automated + browser acceptance
-> manual production approval -> pre-deploy migration -> exact digest deploy
-> smoke/canary -> observation window
```

Application rollback previous exact digest'tir. Schema/data recovery gerektiğinde
rollback migration veya current DB üstüne destructive write yapılmaz; sibling
PITR restore + cutover runbook'u izlenir.

### 2.10 Supply-chain ve CI gate

Required gate'ler:

- contract validator ve invalid fixtures;
- runtime/committed OpenAPI structural drift;
- generated TypeScript clean-diff;
- Core verify ve frontend typecheck/production build;
- AI unit/contract/integration tests;
- migration clean/upgrade validation;
- container build/smoke/non-root check;
- secret, dependency, license ve vulnerability scan;
- SBOM ve provenance attestation.

Runtime image'da unresolved Critical veya High vulnerability ve unsigned/unpinned
artifact production blocker'dır. Medium risk ancak owner + due date + explicit
waiver ile ilerler.

### 2.11 Observability, SLO ve incident operations

Core Micrometer/OTel, AI Python OTel, frontend privacy-filtered Faro ve synthetic
checks Grafana Cloud'a gönderilir. Telemetry raw document/video/prompt, e-mail,
token, presigned URL, cookie, provider payload veya credential içermez.

Alert/runbook seti:

- public availability, 5xx ve latency;
- DB saturation/storage/backup;
- outbox age, queue depth/oldest age/DLQ;
- AI job latency/failure/provider quota;
- Redis and worker readiness;
- certificate and secret rotation;
- email bounce;
- rollback, DB/S3 restore ve provider degradation.

Pilot SLO'ları:

- non-async public API p95 `<750 ms`;
- API 5xx `<0.5%`;
- AI end-to-end p95 `<5 dakika`, p99 `<10 dakika`;
- oldest AI queue age `<5 dakika`;
- hard AI job deadline `30 dakika`.

### 2.12 Production pilot gate

İlk production açılışı 7 gün, en fazla 3 legal entity ve 15 kullanıcıyla sınırlı
pilottur. Promotion için external provider/DPA/domain/model provenance, backup
ve restore kanıtı gerekir. Pilot boyunca data loss, authorization disclosure,
contract drift, malware bypass veya real-money claim kabul edilmez.

Threshold ihlalinde pilot durdurulur ve exact-digest rollback uygulanır. Geniş
açılış planner acceptance ve retention/legal sign-off olmadan yapılamaz.

## 3. Sonuçlar

- Local/CI başarı production readiness yerine geçmez.
- Railway provider-specific business logic oluşmaz; standard OCI/PostgreSQL/
  RabbitMQ/Redis/S3/OpenAPI/AsyncAPI sınırları korunur.
- Single-stateful-service başlangıç riski explicit RPO/RTO ve pilot limitiyle
  kabul edilmiştir; SLO sağlanmazsa broad production açılmaz.

## 4. Kabul kapıları

- Full topology staging'de gerçek service boundaries ile çalışır.
- Release manifest/digest/contract/model identity bütün servislerde eşleşir.
- Broker disconnect, worker SIGTERM, Redis loss, duplicate delivery, provider
  timeout, DB restore ve S3 restore drills geçer.
- Staging aynı artifact digest'leriyle browser acceptance ve rollback kanıtı
  üretir.
- Pilot exit checklist planner tarafından ayrıca kabul edilir; implementer code
  completion pilot acceptance değildir.
