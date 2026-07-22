# Slice 15 — Railway Demo Reconciliation and Deployment

- Durum: Ready — founder/user approved on 22 Temmuz 2026
- Önceki plan: `docs/plan/ready/15-production-reconciliation-and-readiness.md`
  kapsam değişikliği nedeniyle ready'den çıkarıldı; P1–P3 kabul geçmişi korunur.
- Hedef: `RAILWAY_DEMO_READY` (broad production readiness değildir)

## 1. Amaç ve kullanıcı sonucu

Mevcut M4Trust backend/frontend uygulaması, mevcut `m4trust-staging` Railway
projesinin staging ve production environment'larında, aynı source revision ile
çalışan ve gerçek browser'dan kullanılabilen kontrollü bir demo olur.

Kullanıcı mevcut register/login/session akışıyla giriş yapar; legal entity ve Deal
akışlarını kullanır; versioned MinIO storage üzerinden belge/evidence yükleyip
indirebilir. Core ve PostgreSQL private kalır. Sonuç `PROD_READY` değil,
`RAILWAY_DEMO_READY` olarak raporlanır.

## 2. Kapsam ve sınırlar

### In scope

- P1–P3'te kabul edilmiş error authority, contract bundle ve honest runtime
  inventory kontrollerini korumak.
- Kullanılmayan ADR-017/018-only unreleased error catalog değerlerini temizlemek.
- Core Railway monorepo build context/watch/config uyumsuzluğunu düzeltmek.
- Mevcut auth davranışını değiştirmeden Railway-safe runtime configuration sağlamak.
- Railway-hosted, private-bucket ve versioning açık MinIO ile mevcut object-storage
  contract'ını çalıştırmak.
- Mevcut Railway staging ve production environment'larını non-destructive biçimde
  uzlaştırmak; aynı main SHA'dan web/Core deploy etmek.
- Targeted testler; en sonda bir kez full suite; staging browser ve production
  smoke/rollback kanıtı.

### Out of scope

- Invite-only account creation, password reset, e-mail verification, Postmark,
  member onboarding, yeni login throttling ve operator bootstrap.
- AWS S3/KMS/GuardDuty, malware scan/quarantine ve production retention.
- Grafana Cloud, OTel/Faro, GHCR promotion, release manifest, SBOM/provenance ve
  RPO/RTO/PITR iddiası.
- AI provider/model/worker/image/repository/deployment değişikliği.
- RabbitMQ/AI-enabled demo kabulü; AI owner uyumluluk kanıtı gelirse ayrı gate olur.
- Domain slice, public endpoint veya migration eklemek.
- Kullanıcıya ait UI/UX revizyon kapsamını tasarlamak veya önceden uygulamak.
- Existing Railway volume/resource silmek, resetlemek veya secret değerlerini repo'ya
  taşımak.

## 3. Kararlar ve ilgili ADR'ler

- ADR-022 Accepted'tır ve bu planın kontrollü demo istisnaları için otoritedir.
- ADR-022'nin dar demo istisnaları dışında ADR-003/005/006/007 ve ADR-019
  bağlayıcıdır: session+CSRF, server-side authorization, private DB, forward-only
  migration, secret dışsallığı ve AI owner sınırı korunur.
- Existing open registration demo için korunur. Account/member invitation veya
  notification aggregate'i eklenmez.
- Railway native bucket yerine versioned Railway-hosted MinIO seçilir; public olan
  bucket değil yalnız presigned S3 API endpoint'idir. Console public olmaz.
- Mevcut US West region ve birer replica demo boyunca korunur.
- Release unit exact main commit SHA'dır; environment deployment ID/image digest
  kanıtı ayrıca kaydedilir.
- Minimum-test politikası P1–P5 reviewlerinde geçerlidir; full suite yalnız P6'da
  bir kez çalışır.
- P4 kabul edilince planner deployment promptu vermeden kullanıcıya UI/UX insertion
  gate'ini bildirir.

## 4. Public interface, state ve data etkisi

### Public contract

Yeni endpoint veya schema yoktur. `/api/v1/auth/register`, `/login`, `/logout`,
`/auth/me` ve mevcut business API davranışları korunur.

P1'de yalnız gelecekteki ADR-017/018 için eklenmiş ve runtime'da kullanılmayan şu
global catalog değerleri OpenAPI ownership, Java enum ve generated frontend
tiplerinden exact-set olarak kaldırılır:

```text
AUTH_INVITATION_NOT_FOUND_OR_INVALID
AUTH_INVITATION_STATE_CONFLICT
AUTH_PASSWORD_RESET_NOT_FOUND_OR_INVALID
AUTH_REGISTRATION_CLOSED
MEMBER_INVITATION_ACTIVE_EXISTS
MEMBER_INVITATION_NOT_FOUND_OR_HIDDEN
MEMBER_INVITATION_STATE_CONFLICT
UPLOAD_REJECTED_MALWARE
UPLOAD_SCAN_PENDING
UPLOAD_SCAN_UNAVAILABLE
```

Existing `DEAL_INVITATION_*`, `ACCESS_DENIED` ve diğer documented/runtime error
kodları kalır. Bu değerler hiçbir released endpoint tarafından kullanılmadığı ve
staging runtime'a deploy edilmediği için temizlik unreleased reconciliation'dır.

### State ve persistence

Domain state transition veya business persistence değişmez. V1–V22 frozen kalır;
yeni migration beklenmez. Production persistent resources resetlenmez. MinIO
versioning ve environment başına ayrı bucket/volume mevcut immutable object-version
semantiğini taşır.

### Compatibility

Core–frontend contract aynı committe birlikte güncellenir. AI schema/AsyncAPI ve AI
repository değişmez. Shared-contract digest P2 kurallarına göre aynı kalır veya
yalnız yukarıdaki public OpenAPI cleanup'ı nedeniyle deterministik olarak değişir;
değişen digest Core artifact ve probe çıktısında eşleşir.

## 5. Implementation phases

### Kabul edilmiş temel

- P1 — Closed public error authority: accepted at `d69d7e00`.
- P2 — Packaged shared contract bundle/digest: accepted within `4845a03`.
- P3 — Observable runtime inventory and consumer drift gates: accepted within
  `4845a03`; planner workflow policy head `50518607`.

Bu phase'ler yeniden uygulanmaz. Durdurulan `15-T03` tekrar kullanılmaz; plan ready
olursa ilk yeni packet `15-T04` Revision 1 olarak yalnız P4'ü taşır.

### P4 — Reconcile repository for the Railway demo

Outcome:
Contract, Core image/runtime config ve frontend mevcut Railway projesinde deploy
edilmeye hazırdır; auth veya business davranışı genişlemez.

Direction:

- Contract-first olarak yalnız §4'teki dead error değerlerini OpenAPI ownership,
  Java enum, generated frontend type, validator expectation ve changelog'dan birlikte
  çıkar.
- Core Railway build'i monorepo root context'inden
  `services/core-api/Dockerfile` kullanır; watch scope en az `services/core-api/**`
  ve bundle'a giren `contracts/**` değişikliklerini kapsar.
- Staging/production Railway runtime aynı production-safe forwarded-header,
  pre-deploy Flyway ve secure-cookie davranışını kullanır. DB/storage/release
  identity eksikliği fail-fast'tır; messaging yalnız enabled ise broker config'i
  ister.
- MinIO-compatible endpoint/path-style/presign davranışı mevcut storage adapter ve
  public contract'ı değiştirmeden korunur.
- Frontend aynı-origin `/api/v1` proxy, actuator/internal deny ve relative API
  kullanımını korur. Bu phase UI/UX revizyonu yapmaz.

Depends on:
Accepted ADR-022 and accepted P1–P3.

Exit checks:

- Contract validator ve error exact-set negative check geçer.
- Değişen Core config/catalog için smallest focused tests geçer.
- Frontend generated API clean-diff/typecheck'in yalnız etkili en küçük kontrolü
  geçer.
- Core Docker image monorepo-root smoke'u contract bundle ve non-root runtime'ı
  doğrular.
- Repository-wide Maven/frontend suite çalıştırılmaz.

### G-UI — User-owned UI/UX insertion gate

Outcome:
P4 kabulünden sonra yalnız deployment işi kalmıştır; kullanıcı kendi UI/UX revizyon
planını araya yerleştirir.

Direction:

- Planner P4 acceptance sonunda kullanıcıya açıkça haber verir.
- Yeni deployment task packet'i, kullanıcı UI/UX planını uygulayıp review ettirene
  kadar çıkarılmaz.
- Bu Slice 15 planı UI metni, görsel tasarım veya frontend ürün kararı üretmez.

Depends on:
P4 accepted.

Exit checks:

- Kullanıcı deployment'a devam edilmesini açıkça onaylar.
- Deployment base'i kabul edilmiş P4 + UI/UX revision head olarak sabitlenir.

### P5 — Reconcile and prove the existing Railway staging environment

Outcome:
Mevcut staging environment aynı source SHA'lı web/Core ve versioned MinIO ile gerçek
browser demo akışını çalıştırır.

Direction:

- Yeni Railway project/environment oluşturma; mevcut project içindeki staging'i
  kullan. Mevcut Core/Postgres private, web public sınırını koru.
- Staging için ayrı MinIO service+volume+private bucket kur; versioning'i aç, yalnız
  S3 API endpoint'ini presigned erişim için public yap, console'u kapalı tut ve CORS'u
  exact staging web origin/method/header ile sınırla; browser'ın ihtiyaç duyduğu
  `ETag` ve `x-amz-version-id` response header'larını expose et. MinIO image'i
  immutable version/digest ile pinle; `latest` kullanma.
- Core/Web'i G-UI'da sabitlenen aynı exact main SHA'dan deploy et. Core service root,
  Dockerfile ve watch settings P4 repository contract'ıyla eşleşsin.
- Secret değerlerini okumaya/rapora yazmaya gerek olmadan required variable
  presence ve service references doğrulansın. Production resource'larına dokunma.
- RabbitMQ/AI capability açma ve AI readiness iddiası üretme.

Depends on:
G-UI complete.

Exit checks:

- Railway service/deployment IDs, source SHA ve image digest'leri kaydedilir; secret
  değerleri kaydedilmez.
- Web public; Core/Postgres private; MinIO console private; bucket anonymous access
  kapalı ve versioning açıktır.
- Health/readiness ve pre-deploy migration başarılıdır.
- Gerçek browser register → login → refresh/logout ve iki kullanıcı/legal-entity
  ayrımı geçer.
- Gerçek browser document/evidence upload → finalize → download exact version ile
  geçer.
- AI aksiyonları acceptance kapsamına alınmaz ve mock production queue'ya bağlanmaz.

### P6 — Run the one final gate and deploy the production demo

Outcome:
Final repository gate bir kez geçtikten sonra aynı accepted source SHA mevcut
production environment'a non-destructive ve manual olarak deploy edilir.

Direction:

- Önce final full contract/Core/frontend/image gate'ini bir kez çalıştır; sonraki
  review yalnız başarısız alanı tekrarlar.
- Production PostgreSQL ve mevcut MinIO service/volume için önce inventory ve
  backup/snapshot evidence al. Hiçbir resource'u silme, detach/reset/purge etme.
- Existing production MinIO non-destructive doğrulanırsa versioned private bucket ile
  reuse et; doğrulanamıyorsa yeni service+volume oluştur ve eskisini untouched bırak.
- Web/Core'i staging'de kabul edilen exact source SHA'dan birer replica deploy et;
  production-specific secret, DB, storage ve origin kullan.
- Manual approval, pre-deploy migration, smoke ve Railway rollback/redeploy kanıtını
  kaydet. Broad-production, malware-safe, AI-ready veya RPO/RTO iddiası üretme.

Depends on:
P5 accepted.

Exit checks:

- Final contract validator, Core full verify, frontend clean install/typecheck/build,
  Core/Web image smoke ve `git diff --check` bir kez geçer.
- Production web public; Core/Postgres private; MinIO bucket private/versioned,
  console public değildir.
- Register/login/session ve document/evidence upload/download production smoke'u
  geçer; tenant/participant non-disclosure spot-check edilir.
- Exact source SHA, deployment IDs/image digest'leri, backup evidence ve rollback
  target kaydedilir.
- Sonuç `RAILWAY_DEMO_READY` olarak planner tarafından kabul edilir.

## 6. Gerçek browser kabulü

### Staging

1. Yeni kullanıcı mevcut register ekranından hesap açar, session kurulur ve refresh
   sonrası korunur.
2. İkinci browser context'i farklı kullanıcıyla giriş yapar; tenant/legal-entity
   sınırları birbirine sızmaz.
3. Kullanıcı legal entity ve Deal oluşturur; diğer kullanıcı existing Deal invitation
   akışıyla participant olur.
4. PDF/DOCX document upload intent → direct MinIO PUT → finalize → download çalışır;
   exact object version korunur.
5. Evidence upload/finalize/download aynı storage sınırında çalışır.
6. Logout server session'ını invalid eder; actuator/internal route public edge'de
   404 kalır.

AI extraction/video result, settlement/release ve gerçek provider davranışı bu
acceptance matrisinin parçası değildir.

### Production demo smoke

Staging'deki akışın kısa smoke versiyonu yeni demo verisiyle tekrarlanır. Existing
persistent data silinmez. Hata halinde deploy durur ve recorded Railway rollback veya
same-commit redeploy yolu uygulanır.

## 7. Minimum invariant ve validation

### Incremental P4–P5

- Sadece değişen contract/config/storage/deployment davranışının focused testleri.
- Review agent ana diff ve en küçük reproducer'ı inceler; full suite koşmaz.
- Secret değerleri, token, presigned URL ve object content loglanmaz.

### Final P6 — once

```text
python contracts/scripts/validate_contracts.py
cd services/core-api && ./mvnw verify
cd frontend && npm ci && npm run typecheck && npm run build
docker build -f services/core-api/Dockerfile .
docker build -f frontend/Dockerfile .
git diff --check
```

Windows eşdeğerleri kullanılabilir. Başarısız komut düzeltildikten sonra yalnız
başarısız alan tekrar edilir; tüm final gate baştan döndürülmez.

### Invariants

- V1–V22 migration dosyaları değişmez; production'da reset/seed/`flyway clean` yoktur.
- Existing auth endpoint/state/session davranışı korunur; invite/reset/e-mail yüzeyi
  eklenmez.
- Core/DB public olmaz; bucket public olmaz; MinIO console public olmaz.
- Staging ve production DB/storage/secret paylaşmaz.
- `latest`, mock production worker veya AI-owned değişiklik kullanılmaz.
- No malware scan, broad-production, real-money veya AI readiness claim üretilmez.
- Durdurulan 15-T03 `req-review.md` değişikliği planner tarafından implementation
  evidence olarak kabul edilmez ve üzerine yazılmaz.

## 8. Done tanımı

- [x] P1 error authority accepted (`d69d7e00`)
- [x] P2 contract bundle/digest accepted (`4845a03`)
- [x] P3 observable runtime drift gates accepted (`4845a03`)
- [x] ADR-022 founder/user tarafından Accepted
- [ ] P4 repository reconciliation accepted
- [ ] G-UI user-owned revision completed and user authorizes deployment
- [ ] P5 existing Railway staging reconciliation accepted
- [ ] P6 one final gate and production demo deployment accepted
- [ ] Existing Railway persistent resources remained non-destructive
- [ ] Planner independently reviewed final evidence and browser smoke
- [ ] `CURRENT.md`, ADR index ve FORBIDDEN yalnız ADR/plan acceptance sonrası updated
- [ ] Plan `done/` altında `RAILWAY_DEMO_READY` sonucu ve açık deferred risks ile archived
