# Slice 0–3 Implementation Review Handoff

Bu doküman, kabul edilmiş Slice 0–3 implementasyonunu inceleyecek ajana hızlı
başlangıç sağlamak için hazırlanmıştır. Planların yerine geçmez; plan kapsamını
güncel repository karşılıkları, doğrulama kanıtları ve özellikle incelenmesi
gereken sınırlarla eşler.

## 1. İnceleme başlangıç noktası

- İncelenen dal: `codex/slice3-deal-creation-listing`
- Dal tabanı: `main` / `2fc239a`
- Slice 0–2 `main` dalına merge edilmiştir.
- Slice 3 bu dalda tamamlanmış ve kabul edilmiştir; henüz `main` dalına merge
  edilmemiştir.
- Kabul planları:
  - [`00-platform-foundation.md`](../00-platform-foundation.md)
  - [`01-authentication.md`](../01-authentication.md)
  - [`02-organization-and-membership.md`](../02-organization-and-membership.md)
  - [`03-deal-creation-and-listing.md`](../03-deal-creation-and-listing.md)
- Kabul durumu özeti: [`docs/agent/CURRENT.md`](../../../agent/CURRENT.md)
- Yetkili kararlar: `architecture-decisions/ADR-INDEX.md` üzerinden ilgili
  ADR bölümlerine gidilmelidir. Plan ile ADR çelişirse ADR geçerlidir.

Çalışma ağacında bu handoff hazırlanırken kullanıcıya ait, commitlenmemiş bir
`docs/DEVELOPMENT.md` değişikliği bulunmaktadır. Bu değişiklik uygulama
implementasyonunun parçası olarak yorumlanmamalı ve review sırasında yanlışlıkla
geri alınmamalıdır.

## 2. Slice'lar arası oluşan omurga

Slice 0–3 sonunda sistem şu zinciri gerçek uygulama koduyla kurmuştur:

1. Local PostgreSQL, RabbitMQ ve MinIO altyapısı başlatılır.
2. Spring Boot Core API Flyway migration zincirini çalıştırır.
3. Kullanıcı kayıt olur; PostgreSQL-backed server session açılır.
4. Kayıtla aynı transaction kapsamında teknik tenant provision edilir.
5. Kullanıcı legal entity oluşturur ve otomatik `ADMIN` membership kazanır.
6. Frontend aktif legal entity seçimini istek header'ına taşır; backend her
   scoped istekte membership'i yeniden doğrular.
7. Aktif legal entity bir Deal oluşturur; initiator aynı zamanda ilk
   participant olur.
8. Deal listeleme, detay, update ve cancel işlemleri participant authorization,
   state machine, optimistic locking ve aynı-transaction audit üzerinden
   yürür.

Modül zinciri:

```text
identity -> organization ports
deal -> organization authorization/context
organization + deal -> audit append port
frontend -> committed OpenAPI types -> same-origin Core API
```

Review sırasında doğrudan repository paylaşımı veya controller seviyesinde
kopyalanmış authorization kontrolü görülürse bu beklenen mimari değildir.

## 3. Slice 0 — Platform Foundation

### Gerçekleşen kullanıcı/geliştirici sonucu

- Local Compose ile PostgreSQL, RabbitMQ ve MinIO altyapısı kuruldu.
- Java 21 / Spring Boot 4.1 Core API ve Vite/React/TypeScript frontend
  iskeletleri çalışır hale getirildi.
- Flyway migration zinciri, health/readiness görünümü, RFC 9457 Problem Details,
  correlation ID ve structured logging temeli kuruldu.
- Committed OpenAPI'den frontend tipi üretme hattı ve uygulama/contract CI
  workflow'ları eklendi.
- PowerShell reset/seed giriş noktaları oluşturuldu.

### Başlıca dosyalar

- Altyapı: `infra/compose.yaml`, `infra/README.md`
- Yerel işlemler: `scripts/dev-reset.ps1`, `scripts/dev-seed.ps1`
- Backend iskeleti/config:
  - `services/core-api/pom.xml`
  - `services/core-api/src/main/resources/application.yml`
  - `services/core-api/src/main/resources/application-local.yml`
  - `services/core-api/src/main/resources/db/migration/V1__baseline.sql`
- API altyapısı:
  - `api/CorrelationIdFilter.java`
  - `api/ProblemDetailsWriter.java`
  - `api/ApiExceptionHandler.java`
- Modül sınırı kontrolü:
  - `architecture/ModuleArchitectureTest.java`
  - modül `package-info.java` dosyaları
- Frontend readiness:
  - `pages/PlatformStatusPage.tsx`
  - `features/readiness/`
  - `vite.config.ts`
- Build hatları:
  - `.github/workflows/application-build.yml`
  - `.github/workflows/contracts-validation.yml`

### Test ve kabul kanıtı

- `ApiInfrastructureTest` Problem Details ve correlation ID altyapısını kapsar.
- `ModuleArchitectureTest` modüller arası bağımlılık sınırlarını korur.
- Contract validation, Maven verify ve frontend production build CI'a
  bağlanmıştır.
- Temiz ortamda Compose → Spring/Flyway → frontend readiness kabul akışı
  tamamlanmıştır.

### Review odağı

- Secret/config değerlerinin frontend bundle'ına veya repository'ye
  taşınmadığını kontrol edin.
- `/livez` ve `/readyz` management yüzeylerinin public OpenAPI business
  contract'ına karışmadığını doğrulayın.
- Reset scriptinin yalnız açıkça local Compose kapsamını hedeflediğini kontrol
  edin.
- RabbitMQ ve MinIO'nun bu aşamada hazır altyapı olduğunu, business
  topolojisinin henüz kurulmadığını göz önünde bulundurun.

## 4. Slice 1 — Authentication

### Gerçekleşen kullanıcı sonucu

- Kullanıcı register/login/logout yapabilir ve `/auth/me` ile session restore
  edilir.
- Session opaque ve server-side'dır; Spring Session JDBC tablolarında tutulur.
- Parolalar Argon2id ile hashlenir; normalize edilmiş e-posta unique'dir.
- Unsafe istekler fresh CSRF token ile gönderilir.
- Login sonrası session fixation koruması, idle timeout ve server-side absolute
  timeout uygulanır.
- Frontend `/register`, `/login` ve protected `/app/*` route'larını gerçek API
  üzerinden kullanır; 401/session-expiry merkezi ele alınır.

### Public API

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/security/csrf`

### Başlıca dosyalar

- Migration:
  - `V2__identity_user.sql`
  - `V3__spring_session_jdbc.sql`
- Backend:
  - `identity/AuthController.java`
  - `identity/IdentityService.java`
  - `identity/IdentityRepository.java`
  - `identity/EmailAddress.java`
  - `identity/security/SecurityConfiguration.java`
  - `identity/security/AuthenticatedSessionManager.java`
  - `identity/security/AbsoluteSessionTimeoutFilter.java`
  - `identity/security/SessionSecurityProperties.java`
- Frontend:
  - `features/auth/`
  - `pages/RegisterPage.tsx`
  - `pages/LoginPage.tsx`
  - `app/coreApi.ts`

### Test ve kabul kanıtı

- `AuthenticationIntegrationTest`
- `RegistrationTransactionIntegrationTest`
- `AbsoluteSessionTimeoutFilterTest`
- `SessionCookiePolicyTest`
- Gerçek frontend → Spring → PostgreSQL tarayıcı turu tamamlandı.
- Parola, session ID ve CSRF token için log spot check temiz geçti.

### Review odağı ve ertelenen işler

- Generic invalid-credentials davranışının hesap varlığını sızdırmadığını
  kontrol edin.
- Register ve tenant provisioning'in tek transaction davranışını Slice 2
  eklemeleriyle birlikte inceleyin.
- Production cookie ile local cookie profil ayrımını kontrol edin.
- Aynı browser profilinde ikinci login yeni kullanıcı bağlamına geçer; tek aktif
  session politikası uygulanmamıştır. ADR-005 §9 çoklu session'a izin verir.
- Login throttling bilinçli olarak kapsam dışıdır ve public launch öncesi
  [GitHub issue #7](https://github.com/gencberke/m4trust-spring-front-prod/issues/7)
  ile takip edilmektedir.

## 5. Slice 2 — Tenant, Legal Entity ve Membership

### Gerçekleşen kullanıcı sonucu

- Register sırasında kullanıcı için teknik tenant otomatik provision edilir.
- Kullanıcı legal entity oluşturabilir; oluşturan kullanıcı aynı transaction'da
  `ADMIN` membership kazanır.
- Kullanıcı yalnız üye olduğu legal entity'leri listeler, detayını ve üyelerini
  görüntüler.
- Scoped isteklerde `X-M4Trust-Legal-Entity-Id` kullanılır.
- Eksik/geçersiz context 403, kullanıcıdan gizlenen entity 404 ile sonuçlanır.
- Frontend entity empty/create/list/switch/detail/member durumlarını yönetir.

### Public API

- `POST /api/v1/legal-entities`
- `GET /api/v1/legal-entities`
- `GET /api/v1/legal-entities/{legalEntityId}`
- `GET /api/v1/legal-entities/{legalEntityId}/members`
- `GET /api/v1/auth/me` response'una required membership bootstrap listesi
  eklenmiştir.

### Başlıca dosyalar

- Migration: `V4__organization_and_audit_foundation.sql`
- Backend organization:
  - `organization/TenantProvisioningService.java`
  - `organization/LegalEntityService.java`
  - `organization/OrganizationRepository.java`
  - `organization/OperationContextArgumentResolver.java`
  - `organization/OperationContextService.java`
  - `organization/OperationContext.java`
  - `organization/CurrentMembershipQueryPort.java`
- Identity/organization bağlantısı:
  - `organization/TenantProvisioningPort.java`
  - `identity/IdentityMemberProjectionAdapter.java`
- Audit:
  - `audit/AuditAppendPort.java`
  - `audit/JdbcAuditAppender.java`
  - `audit/AuditRepository.java`
- Frontend:
  - `features/organization/`
  - `pages/AuthenticatedAppPage.tsx`
  - `pages/AuthenticatedLayout.tsx`

### Test ve kabul kanıtı

- `LegalEntityIntegrationTest`
- `OrganizationMigrationIntegrationTest`
- `LegalEntityAuditAtomicityIntegrationTest`
- İki ayrı browser profiliyle tenant/entity izolasyonu, creator `ADMIN`,
  refresh sonrası seçim, cross-user 404 ve invalid/missing-header 403 akışları
  tamamlandı.

### Review odağı

- `tenantId` tek başına business authorization değildir; membership doğrulaması
  zorunludur.
- Legal entity existence bilgisinin yetkisiz kullanıcıya sızmadığını kontrol
  edin.
- Audit kaydının entity mutation'ıyla aynı transaction'da olduğunu doğrulayın.
- Plan metninde storage seçimi için `localStorage` örneği verilmiş olsa da
  implementasyon bilinçli olarak versioned `sessionStorage` kullanır:
  `features/organization/legalEntitySelection.ts`. Bu seçim tab/profile
  izolasyonu sağlar ve server authority değildir.
- Aynı browser profilinde kullanıcı değişiminden sonra önceki seçimin
  membership bootstrap sonucu temizlenmesi kullanıcıya entity kaybolmuş gibi
  görünebilir. Veri silme endpoint'i yoktur; konu
  [GitHub issue #10](https://github.com/gencberke/m4trust-spring-front-prod/issues/10)
  altında izlenmektedir.

## 6. Slice 3 — Deal Creation ve Listing

### Gerçekleşen kullanıcı sonucu

- Aktif legal entity DRAFT Deal oluşturabilir.
- Participant olduğu Deal'leri status filtresi, allowlist sıralama ve
  pagination ile listeleyebilir.
- Deal detayını görebilir, editable temel alanları `expectedVersion` ile
  güncelleyebilir ve Deal'i cancel edebilir.
- Stale update 409 `DEAL_STALE_VERSION`, geçersiz state mutation'ı 409
  `DEAL_STATE_CONFLICT` üretir.
- Participant olmayan entity Deal'i okuyamaz veya listesinde göremez.
- Frontend action görünürlüğünü status'tan türetmez; backend
  `availableActions` projection'ını kullanır.

### Public API

- `POST /api/v1/deals`
- `GET /api/v1/deals`
- `GET /api/v1/deals/{dealId}`
- `PATCH /api/v1/deals/{dealId}`
- `POST /api/v1/deals/{dealId}/cancel`

### Başlıca dosyalar

- Contract başlangıç commit'i: `8abb370`
- Migration: `V5__deal_foundation.sql`
- Backend:
  - `deal/Deal.java`
  - `deal/DealStatus.java`
  - `deal/DealService.java`
  - `deal/DealRepository.java`
  - `deal/DealController.java`
  - `deal/DealLifecycleProjectionCalculator.java`
  - `deal/DealAvailableActions.java`
  - `deal/DealExceptionHandler.java`
- Frontend:
  - `features/deals/`
  - `pages/DealListPage.tsx`
  - `pages/DealDetailPage.tsx`
  - `pages/DealMembershipBootstrapState.tsx`
- Route'lar:
  - `/app/deals`
  - `/app/deals/:dealId`

### Test ve kabul kanıtı

- Contract validator: 21 schema ve 13 fixture ile expected-invalid kontrolleri
  geçti.
- Core API `mvn verify`: Testcontainers PostgreSQL üzerinde 32 test geçti.
- Deal odaklı testler:
  - `DealStatusTest`
  - `DealRepositoryIntegrationTest`
  - `DealIntegrationTest`
  - `DealAuditAtomicityIntegrationTest`
- Frontend `npm run typecheck` ve `npm run build` geçti.
- İnsan kabulünde create/list/detail/update, version artışı, iki-tab stale
  recovery, cancel/conflict, iki-profile participant non-disclosure,
  filtre/empty/sort/pagination akışları tamamlandı.

### Review odağı

- Planın backend yönlendirmesinde JPA `@Version` örneği vardır; repository
  standardı JDBC olduğu için kabul edilen implementasyon JPA eklememiştir.
  Optimistic locking, mutation sorgularındaki atomik `WHERE version = ?`
  predicate'i ve etkilenen satır sayısı üzerinden sağlanır.
- State değişikliklerinin serbest status setter'larıyla değil `Deal` davranış
  metotlarıyla yürüdüğünü kontrol edin.
- Participant erişiminin tenant filtresine indirgenmediğini doğrulayın.
- `deal_participant` tablosunun Slice 4 genişlemesine uygun şekilde erişim
  modelinin şimdiden kaynağı olduğunu kontrol edin.
- Audit mutation'larının business mutation'la aynı transaction'da kaldığını
  doğrulayın.
- Lifecycle ve `availableActions` hesabının backend'de merkezi kaldığını,
  frontend'in bağımsız status matrisi üretmediğini kontrol edin.
- Buyer/seller ataması, davetler, participant yönetimi, DRAFT→ACTIVE
  tetikleyicisi ve archive UI Slice 3 kapsamına dahil değildir.

## 7. Contract, persistence ve frontend çapraz kontrol tablosu

| Slice | OpenAPI alanı | Migration | Backend modülü | Frontend yüzeyi |
|---|---|---|---|---|
| 0 | Problem Details ortak temeli | V1 | `api`, `sharedkernel`, `integration` | `/status`, readiness |
| 1 | auth + CSRF | V2, V3 | `identity` | `/register`, `/login`, route guards |
| 2 | legal entities + memberships | V4 | `organization`, `audit` | `/app` organization workspace |
| 3 | Deal CRUD-benzeri actions/list | V5 | `deal` | `/app/deals`, Deal detail |

`contracts/openapi/core-api-v1.yaml` public Core API için source of truth'tür.
`frontend/src/generated/core-api.d.ts` bu dosyadan üretilir; paralel elle
yazılmış wire model review bulgusu sayılmalıdır.

## 8. Önerilen review sırası

1. Planların Done checklist'lerini ve ilgili ADR sınırlarını okuyun.
2. `V1`–`V5` migration zincirini veri sahipliği, FK ve tenant izolasyonu için
   inceleyin.
3. OpenAPI endpoint/error kodlarını controller/exception handler davranışıyla
   karşılaştırın.
4. `identity -> organization -> audit` transaction ve modül sınırını inceleyin.
5. `OperationContext` çözümlemesinden Deal participant sorgularına kadar
   authorization zincirini takip edin.
6. Deal state, optimistic locking ve audit atomicity testlerini uygulamayla
   karşılaştırın.
7. Frontend `coreApi.ts` içinden auth, selected legal entity header'ı, CSRF ve
   session-expiry akışlarını takip edin.
8. UI'ın server projection'larını kullandığını ve error `code` değerleriyle
   dallandığını doğrulayın.
9. Son olarak contract validation, Core API verify ve frontend build çalıştırın.

## 9. Review için doğrulama komutları

Repository kökünden:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm ci
npm run typecheck
npm run build
```

Manuel E2E kabul turları daha önce kullanıcı tarafından başarıyla
tamamlanmıştır. Review ajanı regresyon şüphesi veya değişen kod olmadıkça bunları
baştan çalıştırmak yerine kabul planlarındaki kanıtı ve ilgili otomatik testleri
esas alabilir.

## 10. Açık takipler ve kapsam sınırı

- Issue #7: public launch öncesi login throttling.
- Issue #10: aynı browser profilinde kullanıcı değişiminden sonra legal entity
  seçiminin kaybolmuş görünmesi.
- FastAPI AI service ve Mock AI Worker henüz stabil/kabul edilmiş değildir.
- Railway staging/production kurulumu henüz stabil/kabul edilmiş değildir.
- Slice 4 ve sonrası capability'leri bu review kapsamında implement edilmiş
  kabul edilmemelidir.

Bu handoff'ta belirtilen açık takipler, Slice 0–3'ün mevcut kabul durumunu geri
almaz; reviewer bunları yanlışlıkla eksik Slice 0–3 implementasyonu olarak
sınıflandırmamalıdır.
