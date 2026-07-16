# Yerel geliştirme rehberi

Bu rehber mevcut monorepo yerleşimini ve Windows/PowerShell başlangıç sırasını
özetler. Ayrıntılı yapılandırma için ilgili bileşenin kendi rehberini kullanın.

## Monorepo yerleşimi

```text
frontend/                  Vite + React istemcisi
services/core-api/         Spring Boot Core API
tools/mock-ai-worker/      Yalnız gelecekteki geliştirme/test worker'ı için yer tutucu
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
- [Mock AI Worker yer tutucusu](../tools/mock-ai-worker/README.md)
- [Tamamlanmış Slice 0 planı](plan/done/00-platform-foundation.md)
- [Tamamlanmış Slice 1 planı](plan/done/01-authentication.md)
- [Kabul bekleyen Slice 2 planı](plan/ready/02-organization-and-membership.md)

## Windows/PowerShell geliştirme sırası

Komutları repository kökünden, ayrı PowerShell pencerelerinde ve aşağıdaki
sırayla çalıştırın. Gereken yerel placeholder ayarları ve ayrıntılar bağlantılı
rehberlerdedir.

1. [Yerel altyapıyı](../infra/README.md) başlatın ve PostgreSQL, RabbitMQ ile
   MinIO'nun healthy olmasını bekleyin.

   ```powershell
   docker compose --project-name m4trust-local --file .\infra\compose.yaml up --detach --wait
   ```

2. Core API'yi açıkça `local` profiliyle başlatın.

   ```powershell
   Set-Location .\services\core-api
   $env:SPRING_PROFILES_ACTIVE = "local"
   .\mvnw.cmd spring-boot:run
   ```

3. Yeni bir pencerede frontend'in yerel environment dosyasını hazırlayın,
   bağımlılıkları ve contract tiplerini üretin, ardından geliştirme sunucusunu
   başlatın.

   ```powershell
   Set-Location .\frontend
   Copy-Item .env.example .env
   npm install
   npm run generate:api
   npm run dev
   ```

Gerçek secret'lar repository'ye, image'a, loglara veya frontend bundle'ına
eklenmez. Yalnız açıkça yerel placeholder değerler örnek dosyalarda tutulabilir;
gerçek `.env` dosyaları Git dışında kalır.

## Reset ve seed

Yerel `m4trust-local` Compose kapsamını silmeden önce hedefi incelemek için:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-reset.ps1 -WhatIf
```

Yerel reset ve seed giriş noktaları:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-reset.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-seed.ps1
```

Reset yalnız yerel geliştirme içindir; production ortamında destructive reset
çalıştırılmaz. Seed verileri production migration zincirine eklenmez.

## Mevcut doğrulama durumu

Slice 0 kabul edilmiş ve planı `done/` altına taşınmıştır. Slice 1'in OpenAPI,
Spring/PostgreSQL ve frontend implementasyonu `main` dalına birleştirilmiştir.
Slice 1'in `docs/plan/done/01-authentication.md` §7 tarayıcı akışı ve parola,
session ID ile CSRF token log spot check'i gerçek frontend → Spring → PostgreSQL
zincirinde başarıyla tamamlanmıştır. Slice 1 kabul edilmiş durumdadır. Public
launch öncesi login throttling işi GitHub issue #7 altında ayrıca izlenmektedir.

Slice 2'nin public contract, Core API ve frontend implementasyonu
`codex/slice2-organization-membership` dalında tamamlanmıştır. Contract
validation, Testcontainers PostgreSQL kullanan Core API `mvn verify` içindeki 21
test, frontend `npm run typecheck` ve production `npm run build` başarılıdır.
Slice 2 henüz kabul edilmemiştir; aşağıdaki iki-profile gerçek tarayıcı akışı
insan tarafından tamamlanıp kanıtı plana işlenmelidir.

## Slice 2 manuel kabul testi

Bu test için iki ayrı browser profili kullanın. Profil A ve Profil B ayrı cookie,
session ve `sessionStorage` alanlarına sahip olmalıdır; Postman veya Swagger
gerekmez. Tüm API kontrolleri frontend'in açık olduğu origin üzerinde browser
DevTools Console'dan güvenli `GET` istekleriyle yapılır.

### 1. Yerel sistemi başlatın

Repository kökünde altyapıyı başlatın:

```powershell
docker compose --project-name m4trust-local --file .\infra\compose.yaml up --detach --wait
```

İkinci PowerShell penceresinde Core API'yi başlatın:

```powershell
Set-Location .\services\core-api
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

Üçüncü PowerShell penceresinde frontend'i başlatın:

```powershell
Set-Location .\frontend
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
npm ci
npm run dev
```

Vite'ın terminalde yazdığı yerel adresi açın; varsayılan adres
`http://localhost:5173` olur. Core API varsayılan olarak
`http://127.0.0.1:8080` üzerinde çalışır ve Vite `/api/*` isteklerini buraya
proxy'ler.

### 2. Profil A: kayıt, boş durum ve ilk legal entity

1. Profil A'da `/register` sayfasını açın ve yeni, benzersiz bir e-posta ile
   kayıt olun.
2. `/app` açıldığında legal entity listesinin boş olduğunu ve oluşturma
   yönlendirmesini görün.
3. Örnek olarak `Profil A Şirketi` ve `A-REG-001` değerleriyle legal entity
   oluşturun.
4. Oluşturulan entity'nin listede ve aktif entity switcher'ında seçili olduğunu
   doğrulayın.
5. Detay panelinde resmî ad/kayıt numarasını; üye listesinde kendi hesabınızı ve
   `Yönetici` (`ADMIN`) rolünü görün.
6. DevTools Console'da aşağıdaki komutu çalıştırın ve dönen UUID'yi güvenli bir
   yere kopyalayın:

   ```javascript
   sessionStorage.getItem("m4trust:selected-legal-entity-id:v1")
   ```

7. Sayfayı yenileyin. Aynı UUID'nin storage'da kaldığını, switcher seçiminin
   korunduğunu ve detay/üye listesinin tekrar yüklendiğini doğrulayın.

### 3. Profil B: ayrı kullanıcı ve ayrı legal entity

1. Profil B'da aynı frontend origin'inin `/register` sayfasını açın ve Profil
   A'dan farklı, benzersiz bir e-posta ile kayıt olun.
2. İlk açılışta boş legal entity durumunu görün.
3. Örnek olarak `Profil B Şirketi` ve `B-REG-001` değerleriyle kendi entity'nizi
   oluşturun; listede seçildiğini ve üye rolünüzün `Yönetici` olduğunu görün.
4. Profil B DevTools Console'da aşağıdaki komutla kendi entity UUID'nizi alın:

   ```javascript
   sessionStorage.getItem("m4trust:selected-legal-entity-id:v1")
   ```

### 4. Cross-user non-disclosure kontrolü

Profil B DevTools Console'da `PROFILE_A_ENTITY_UUID` yerini Profil A'dan
kopyaladığınız UUID ile değiştirip aşağıdaki güvenli `GET` isteğini çalıştırın:

```javascript
(async () => {
  const profileAEntityId = "PROFILE_A_ENTITY_UUID";
  const response = await fetch(`/api/v1/legal-entities/${profileAEntityId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/problem+json",
      "X-M4Trust-Legal-Entity-Id": profileAEntityId,
    },
  });
  console.log(response.status, await response.json());
})();
```

Beklenen sonuç:

- HTTP status `404`
- Problem Details `code` alanı `LEGAL_ENTITY_NOT_FOUND`
- Profil A entity adı, kayıt numarası veya üyeleri response içinde bulunmaz

Bu sonuç, var olmayan entity ile başka kullanıcıdan gizlenen entity'nin aynı
response kimliğini kullandığını ve varlık bilgisinin sızmadığını doğrular.

### 5. Eksik ve geçersiz header kontrolleri

Profil B DevTools Console'da `PROFILE_B_ENTITY_UUID` yerini Profil B'nin kendi
UUID'siyle değiştirin. İlk istek header'ı tamamen atlar:

```javascript
(async () => {
  const profileBEntityId = "PROFILE_B_ENTITY_UUID";
  const response = await fetch(`/api/v1/legal-entities/${profileBEntityId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/problem+json" },
  });
  console.log(response.status, await response.json());
})();
```

İkinci istek geçersiz bir header değeri gönderir:

```javascript
(async () => {
  const profileBEntityId = "PROFILE_B_ENTITY_UUID";
  const response = await fetch(`/api/v1/legal-entities/${profileBEntityId}`, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/problem+json",
      "X-M4Trust-Legal-Entity-Id": "not-a-uuid",
    },
  });
  console.log(response.status, await response.json());
})();
```

Her iki istekte beklenen sonuç:

- HTTP status `403`
- Problem Details `code` alanı `LEGAL_ENTITY_ACCESS_DENIED`
- Entity detayı response içinde bulunmaz

Bu Console istekleri uygulamanın merkezi fetch wrapper'ını bilinçli olarak
atlayarak header doğrulamasını doğrudan test eder; normal workspace istekleri
seçili UUID'yi otomatik gönderir.

### 6. Seçim izolasyonu ve temizleme

- `sessionStorage` seçimi tab/profile kapsamındadır. Ayrı tab'lar kendi aktif
  legal entity seçimini değiştirebilir; seçim authoritative server session
  state değildir ve her scoped istekte yeniden doğrulanır.
- Profil B'da çıkış yapın ve login sayfasında Console'dan aşağıdaki komutun
  `null` döndürdüğünü doğrulayın:

  ```javascript
  sessionStorage.getItem("m4trust:selected-legal-entity-id:v1")
  ```

- Merkezi session-expiry akışı da aynı seçimi temizleyip korumalı sayfalardan
  `/login` sayfasına yönlendirir.

Tüm adımlar geçerse
`docs/plan/ready/02-organization-and-membership.md` içindeki iki-browser kabul
checkbox'ı kanıt notuyla işaretlenebilir. Bundan önce plan `done/` altına
taşınmaz ve Slice 2 kabul edilmiş sayılmaz.
