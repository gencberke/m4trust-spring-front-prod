# Yerel geliştirme rehberi

Bu rehber mevcut monorepo yerleşimini ve yerel başlangıç sırasını özetler.
Komut örnekleri **bash/zsh** (macOS/Linux) ve **PowerShell** (Windows) için
verilir; rehber yalnız Windows'a özel değildir. Ayrıntılı yapılandırma için
ilgili bileşenin kendi rehberini kullanın.

## Monorepo yerleşimi

```text
frontend/                  Vite + React istemcisi
services/core-api/         Spring Boot Core API
tools/mock-ai-worker/      Yerel contract-valid document extraction test worker'ı
tools/moka-emulator/       Yerel/CI dış-process Moka HTTP transport emulatorü
contracts/                 OpenAPI, AsyncAPI, JSON Schema ve örnekler
infra/                     Yerel PostgreSQL, RabbitMQ ve MinIO Compose tanımı
scripts/                   Yerel reset ve seed giriş noktaları
architecture-decisions/    Kabul edilmiş mimari kararlar ve yasaklar
docs/plan/                 Slice planları
```

Bileşen rehberleri:

- [Yerel altyapı](../infra/README.md)
- [Core API](../services/core-api/README.md)
- [Frontend](../frontend/README.md)
- [Contract'lar](../contracts/README.md)
- [Mock AI Worker](../tools/mock-ai-worker/README.md)
- [Moka HTTP Emulator](../tools/moka-emulator/README.md)
- [Tamamlanmış slice planları](plan/done/) ve [planlama notları](plan/planning/)

## Hızlı başlangıç

Repository kökünden bash/zsh:

```bash
./scripts/dev-up.sh
```

PowerShell kullanıcıları aynı Compose komutunu doğrudan çalıştırabilir; ayrıntılar
[Yerel altyapı](../infra/README.md) rehberindedir.

## Slice 8 yerel analiz akışı

PostgreSQL, RabbitMQ, MinIO ve local-only Mock AI Worker'ı repository kökünden
başlatın:

```bash
docker compose --project-name m4trust-local --file infra/compose.yaml --profile mock-ai up --detach --build
```

```powershell
docker compose --project-name m4trust-local --file .\infra\compose.yaml --profile mock-ai up --detach --build
```

`docker compose ... --wait` kullanmayın veya çıkış koduna güvenmeyin:
`minio-bootstrap` tek seferlik bir container olduğu için `--wait` sıfır dışı
çıkabilir; asıl doğrulama `docker compose ps` ile postgres, rabbitmq ve minio
servislerinin **healthy** olduğunu görmektir:

```bash
docker compose --project-name m4trust-local --file infra/compose.yaml ps
```

Core API'yi ayrı bir terminalde çalıştırın. Yerel geliştirmede `./mvnw clean
spring-boot:run` tercih edin; eski `target/` derlemesi `ClassNotFoundException`
riski taşır.

Temel yerel profil (`local`):

```bash
cd services/core-api
SPRING_PROFILES_ACTIVE=local ./mvnw clean spring-boot:run
```

```powershell
Set-Location .\services\core-api
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd clean spring-boot:run
```

Funding/settlement demo veya simulated payment davranışı gerektiğinde
`local-sandbox` profilini ekleyin:

```bash
SPRING_PROFILES_ACTIVE=local,local-sandbox ./mvnw clean spring-boot:run
```

```powershell
$env:SPRING_PROFILES_ACTIVE = "local,local-sandbox"
.\mvnw.cmd clean spring-boot:run
```

Frontend'i ayrı bir terminalde çalıştırın. Önce `frontend/.env.example`
dosyasını `frontend/.env` olarak kopyalayın ve [Frontend rehberindeki](../frontend/README.md)
`CORE_API_PROXY_TARGET` değerini ayarlayın (varsayılan: `http://127.0.0.1:8080`).

```bash
cp frontend/.env.example frontend/.env
cd frontend
npm ci
npm run generate:api
npm run dev
```

```powershell
Copy-Item .\frontend\.env.example .\frontend\.env
Set-Location .\frontend
npm ci
npm run generate:api
npm run dev
```

Mock worker yalnız `--profile mock-ai` ile açılır. Profil kapalıyken gönderilen
analiz talebi `QUEUED` kalır ve worker yeniden açıldığında işlenir. Deterministik
başarı, retryable failure ve duplicate senaryoları ile yerel presigned-download
köprüsünün ayrıntıları [Mock AI Worker rehberindedir](../tools/mock-ai-worker/README.md).

Slice 8 doğrulama komutları:

```bash
python contracts/scripts/validate_contracts.py

cd services/core-api
./mvnw verify

cd ../../frontend
npm run typecheck
npm run build

cd ..
PYTHONPATH=tools/mock-ai-worker/src python -m pytest tools/mock-ai-worker/tests
```

```powershell
python contracts/scripts/validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd verify

Set-Location ..\..\frontend
npm run typecheck
npm run build

Set-Location ..
$env:PYTHONPATH = "tools/mock-ai-worker/src"
python -m pytest tools/mock-ai-worker/tests
```

## Kabul edilmiş Slice 7 staging

Railway staging Slice 7 kapsamında 21 Temmuz 2026'da kabul edilmiştir. Aşağıdaki
bölüm kabul edilen config-as-code ve operatör akışını belgeler. Güncel kabul
kanıtı `plan/done/review/04-07-implementation-review-handoff.md` içindedir.

## Railway staging hazırlığı

Repository iki deploy edilebilir OCI image tanımlar:

- `m4trust-core-api`: Spring web süreci ve aynı image içindeki tek seferlik
  Flyway komutu.
- `m4trust-web-edge`: React production bundle'ını sunan ve yalnız `/api/*`
  isteklerini private Core API'ye ileten Caddy edge'i.

Bu bölüm repository/config-as-code ve operatör sözleşmesidir. Gerçek değerler
Railway Variables/Secrets içinde tutulur; dokümantasyon credential içermez.

### Service kaynak ve config yolları

Railway dashboard'da iki GitHub service aynı `main` branch'ine bağlanır:

| Service | Root Directory | Config File | Docker build context |
| --- | --- | --- | --- |
| `m4trust-core-api` | `/` | `/services/core-api/railway.json` | repository root (`services/core-api/Dockerfile`) |
| `m4trust-web-edge` | `/` | `/frontend/railway.json` | repository root |

Web edge'in root'u bilerek repository köküdür. `frontend/Dockerfile`, frontend
type generation sırasında commit edilmiş
`contracts/openapi/core-api-v1.yaml` dosyasını okur. Core service de monorepo
root context'inden `services/core-api/Dockerfile` ile build edilir ve
`contracts/**` watch kapsamına dahildir (ADR-022). Config File yolu Root
Directory'den bağımsız olarak Railway service ayarında açıkça seçilmelidir.

Yalnız `m4trust-web-edge` için public domain üretilir. `m4trust-core-api` ve
Railway PostgreSQL public domain/TCP proxy almaz; aralarındaki trafik aynı
environment'ın private network'ünde kalır. Browser yalnız şu yüzeyi görür:

```text
https://<staging-edge>/         -> React/Caddy
https://<staging-edge>/api/*   -> private Core API
```

`/actuator/*` edge üzerinden her zaman `404` döner. Core liveness, readiness ve
info endpoint'leri yalnız private servis yüzeyinde kullanılır.

### Staging variable sözleşmesi

Gerçek değerler Railway Variables/Secrets içinde tutulur. Aşağıdaki isimler
application'ın provider-bağımsız sözleşmesidir; repository'ye gerçek credential
yazılmaz.

Core API non-secret variable'ları:

```text
PORT=<service için açıkça seçilen runtime port>
SPRING_PROFILES_ACTIVE=staging
APP_ENVIRONMENT=staging
APP_VERSION=<immutable release version>
GIT_COMMIT_SHA=<deploy edilen tam commit SHA>
BUILD_TIME=<RFC 3339 UTC build zamanı>
DATABASE_HOST=${{Postgres.PGHOST}}
DATABASE_PORT=${{Postgres.PGPORT}}
DATABASE_NAME=${{Postgres.PGDATABASE}}
DATABASE_USER=${{Postgres.PGUSER}}
```

`Postgres` örnek service adıdır; reference namespace gerçek Railway service
adıyla eşleşmelidir. `DATABASE_PASSWORD=${{Postgres.PGPASSWORD}}` secret
reference olarak tanımlanır. Değer kopyalanmaz, loglanmaz veya repository'ye
yazılmaz. `SESSION_IDLE_TIMEOUT` ve `SESSION_ABSOLUTE_TIMEOUT` yalnız default
süreler değiştirilecekse eklenir.

Staging'de `SESSION_COOKIE_NAME` ve `SESSION_COOKIE_SECURE` override edilmez.
Böylece production-default `__Host-M4TRUST_SESSION`, `Secure`, `HttpOnly`,
`SameSite=Lax`, `Path=/` ve Domain attribute'suz profil korunur.

Web edge variable'ları:

```text
PORT=<service için açıkça seçilen runtime port>
CORE_API_ORIGIN=http://${{m4trust-core-api.RAILWAY_PRIVATE_DOMAIN}}:${{m4trust-core-api.PORT}}
```

Railway'de diğer service'in `PORT` değeri reference edilebilmesi için Core API
service variable'ı olarak açıkça tanımlanmalıdır. Private network trafiği HTTP
kullanır; browser tarafı yine Railway public edge TLS'i üzerinden HTTPS'tir.
Core API internal URL'si hiçbir `VITE_*` variable'a veya frontend bundle'a
verilmez.

`APP_VERSION`, `GIT_COMMIT_SHA`, `APP_ENVIRONMENT` ve `BUILD_TIME` hem Docker
build arg olarak iki image'ın OCI label'larına hem Core API runtime environment
olarak info/structured-log metadata'sına verilmelidir. Application doğrudan
Railway'e özel variable adı okumaz. CI image'ları tam commit SHA ile tag'ler;
`latest` deploy veya rollback hedefi değildir.

### Migration ve health gate

`services/core-api/railway.json` normal web sürecini
`m4trust-core-api run`, pre-deploy adımını ise
`m4trust-core-api migrate` olarak sabitler. Staging profile ve base config'de
Flyway kapalıdır; yalnız migration komutu Flyway'i açar, bütün versioned
migration'ları uygular ve context'i kapatarak başarıda `0` ile çıkar. Startup
veya migration hatası exception'ı process'e taşır ve non-zero exit ile rollout'u
durdurur. Her replica migration çalıştırmaz.

Health yolları:

- Web edge deployment health: `GET /healthz`
- Core private liveness: `GET /actuator/health/liveness`
- Core deployment readiness: `GET /actuator/health/readiness` (PostgreSQL dahil)
- Core private release info: `GET /actuator/info`

Railway deploy ayrıntısında önce pre-deploy migration'ın başarılı olduğu, sonra
readiness'in `UP` olduğu doğrulanır. Migration tamamlanmadan yeni web process'i
başlamamalı; readiness başarısızken eski deployment trafik almaya devam
etmelidir.

### Disposable failure gate

Migration failure gate ortak staging Flyway history'sini kirletmeden şu şekilde
kanıtlanır:

1. Ayrı, disposable Railway environment ve disposable PostgreSQL oluşturulur.
2. Aynı commit-SHA image/config deploy edilir ve başarılı migration baseline'ı
   kaydedilir.
3. Yalnız disposable Core service'te database host/credential reference'ı geçici
   olarak erişilemez bir test değerine yöneltilir ve yeniden deploy tetiklenir.
4. `m4trust-core-api migrate` non-zero çıktığı, yeni web process'in başlamadığı
   ve önceki deployment'ın değişmediği deploy logundan doğrulanır.
5. Disposable environment tamamen silinir.

Ortak staging zincirine kasıtlı bozuk/sahte migration eklenmez, uygulanmış
migration değiştirilmez ve hiçbir ortamda `flyway clean` çalıştırılmaz.

### Staging smoke ve rollback operatör akışı

Başarılı deploy sonrasında public HTTPS origin'de uygulama shell'i, deep-link
refresh, register/login/logout/refresh, legal entity ve Deal smoke akışları
çalıştırılır. Browser Network kaydında istekler yalnız relative `/api/v1`
yüzeyine gitmeli; private Core/PostgreSQL adresi veya `/actuator/*` cevabı
görünmemelidir. Core private info/log metadata'sındaki tam SHA deploy edilen
image ile eşleşmelidir.

Rollback gerektiğinde:

1. Mevcut schema ile uyumlu, daha önce başarılı olmuş deployment tam commit SHA
   ile belirlenir; `latest` seçilmez.
2. Migration'ın forward-only/expand-contract uyumluluğu doğrulanır. Destructive
   schema rollback veya down migration çalıştırılmaz.
3. Railway'de o immutable deployment/image yeniden seçilir ve private readiness
   doğrulanır.
4. Public edge smoke tekrar çalıştırılır; info/log SHA'sının rollback hedefine
   döndüğü kaydedilir.

Breaking schema switch sonrasında doğrudan eski ve uyumsuz image'a dönülmez;
rollback tabanı mevcut schema ile birlikte çalışabilen expand/dual-write image
olmalıdır.
