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
- [Tamamlanmış Slice 2 planı](plan/done/02-organization-and-membership.md)
- [Kabul bekleyen Slice 3 planı](plan/ready/03-deal-creation-and-listing.md)

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
`codex/slice2-organization-membership` dalında tamamlanmış ve `main` dalına
birleştirilmiştir. Contract
validation, Testcontainers PostgreSQL kullanan Core API `mvn verify` içindeki 21
test, frontend `npm run typecheck` ve production `npm run build` başarılıdır.
Aşağıdaki iki-profile gerçek tarayıcı akışı da insan tarafından başarıyla
tamamlanmış, kabul kanıtı
`docs/plan/done/02-organization-and-membership.md` içine işlenmiştir. Slice 2
kabul edilmiş durumdadır.

Slice 3'ün contract, Flyway V5, Core API ve frontend implementasyonu
`codex/slice3-deal-creation-listing` dalında tamamlanmıştır. Contract
validation, Testcontainers PostgreSQL kullanan Core API `mvn verify` içindeki 32
test, frontend `npm run typecheck` ve production `npm run build` başarılıdır.
Ancak aşağıdaki gerçek tarayıcı turu henüz kullanıcı tarafından raporlanmadığı
için Slice 3 kabul edilmiş değildir ve planı `ready/` altında kalır.

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

Bu akış 2026-07-16 tarihinde başarıyla tamamlanmıştır. Sonuç
`docs/plan/done/02-organization-and-membership.md` içindeki kabul kanıtı ve
işaretli Done checklist'iyle kayıtlıdır; bölüm regresyon kabul turunda yeniden
kullanılabilir.

## Slice 3 manuel kabul testi

Bu tur için Profil A ve Profil B adında iki ayrı browser profili kullanın.
Profiller ayrı cookie, session ve `sessionStorage` alanlarına sahip olmalıdır.
Profil A içindeki stale-version adımı ise aynı profile ait iki tab ile yapılır.
Postman veya Swagger gerekmez; zorlanan mutation kontrollerinde frontend'in
açık olduğu origin üzerindeki DevTools Console kullanılır.

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

Vite'ın terminalde yazdığı adresi açın; varsayılan adres
`http://localhost:5173` olur. Core API varsayılan olarak
`http://127.0.0.1:8080` üzerinde çalışır ve Vite `/api/*` isteklerini buraya
proxy'ler.

### 2. Profil A: aktif entity, create/list/detail

1. Profil A'da kayıt olun veya giriş yapın.
2. `Organizasyon` ekranında bu tur için yeni bir geçerli legal entity oluşturun.
   Üstteki `Aktif legal entity` alanında bu entity'yi seçin.
3. `Deals` menüsünü açın; route `/app/deals` olmalıdır.
4. İlk kullanımda `Henüz bir Deal yok.` boş durumunu görün.
5. `Yeni Deal` alanında örneğin `Slice 3 Kabul Deal` başlığını ve isteğe bağlı
   bir açıklamayı girip `Taslak Deal oluştur` düğmesine basın.
6. Başarı bildirimini ve Deal'i listede `Taslak` durumuyla görün.
7. Deal satırını açın. Route `/app/deals/{dealId}` olmalı; referans, `DRAFT`
   status, `DRAFT` lifecycle, sürüm `0` ve düzenleme/iptal aksiyonları
   görünmelidir.
8. Daha sonraki kontroller için adres çubuğundaki `dealId` UUID'sini güvenli bir
   yere kopyalayın. Bu değer secret veya session ID değildir.

### 3. Güncelleme ve version artışı

1. Detay ekranında başlığı veya açıklamayı değiştirin.
2. `Değişiklikleri kaydet` düğmesine basın.
3. `Değişiklikler sürüm 1 olarak kaydedildi.` bildirimini ve `Sürüm` alanının
   `0` değerinden `1` değerine yükseldiğini doğrulayın.
4. Listeye dönün; güncel başlık ve sürüm listede de görünmelidir.

### 4. İki tab ile stale-version 409 akışı

1. Profil A'da aynı Deal detayını iki tab'da açın. Her iki tab'ın üstündeki
   `Aktif legal entity` seçiminin aynı entity olduğunu açıkça doğrulayın.
2. İki tab'ın Console'unda aşağıdaki değerin aynı UUID olduğunu kontrol
   edebilirsiniz:

   ```javascript
   sessionStorage.getItem("m4trust:selected-legal-entity-id:v1")
   ```

3. İki tab da aynı sürümü gösterirken Tab 1'de başlığı değiştirip kaydedin;
   sürümün bir arttığını görün.
4. Tab 2'yi yenilemeden, formdaki başlık ve açıklamayı farklı değerlerle
   değiştirip `Değişiklikleri kaydet` düğmesine basın.
5. Beklenen sonuç:

   - HTTP `409` karşılığında kullanıcıya `Bu kayıt başka bir işlemde
     değiştirildi...` bildirimi gösterilir.
   - Tab 2'de kaydetmeyi denediğiniz başlık/açıklama değerleri formda korunur;
     sessiz last-write-wins olmaz.
   - `Güncel veriyi yükle` düğmesi görünür.

6. Korunan değerleri gözle kontrol ettikten sonra `Güncel veriyi yükle`
   düğmesine basın. Tab 1'de kaydedilen güncel server değerlerinin ve artmış
   sürümün açıkça yüklendiğini doğrulayın. Bu explicit reload, yerel form
   değerlerinin güncel server projection'ıyla değiştirilmesini bilinçli hale
   getirir.

### 5. Cancel onayı, action projection ve zorlanan ikinci cancel

1. Güncel Deal detayında `Deal’i iptal et` düğmesine basın.
2. `Deal iptal edilsin mi?` onay penceresini görün; önce `Vazgeç` ile kapatıp
   status'un değişmediğini doğrulayın.
3. Düğmeye yeniden basıp `İptali onayla` seçeneğini kullanın.
4. Status ve lifecycle'ın `CANCELLED` olduğunu doğrulayın.
5. Düzenleme formunun `Düzenleme kapalı` paneline dönüştüğünü ve iptal
   düğmesinin kaybolarak `Bu Deal için kullanılabilir iptal aksiyonu yok.`
   metninin gösterildiğini doğrulayın. Bu görünürlük frontend'in status
   türetmesinden değil backend `availableActions` projection'ından gelir.
6. UI artık ikinci cancel'a izin vermediği için, aynı iptal edilmiş Deal detay
   sayfasında DevTools Console'u açıp aşağıdaki güvenli kontrolü çalıştırın.
   Kod seçili entity UUID'sini `sessionStorage` içinden alır, yeni bir CSRF
   token fetch eder ve token/session değerlerini loglamaz:

   ```javascript
   (async () => {
     const storageKey = "m4trust:selected-legal-entity-id:v1";
     const legalEntityId = sessionStorage.getItem(storageKey);
     const dealId = location.pathname.split("/").filter(Boolean).at(-1);
     if (!legalEntityId || !dealId) {
       throw new Error("Aktif legal entity veya Deal ID bulunamadı.");
     }

     const csrfResponse = await fetch("/api/v1/security/csrf", {
       method: "GET",
       credentials: "same-origin",
       cache: "no-store",
       headers: { Accept: "application/json" },
     });
     if (!csrfResponse.ok) {
       throw new Error(`CSRF alınamadı: HTTP ${csrfResponse.status}`);
     }
     const csrf = await csrfResponse.json();

     const response = await fetch(`/api/v1/deals/${dealId}/cancel`, {
       method: "POST",
       credentials: "same-origin",
       headers: {
         Accept: "application/json, application/problem+json",
         "X-M4Trust-Legal-Entity-Id": legalEntityId,
         [csrf.headerName]: csrf.token,
       },
     });
     const problem = await response.json();
     console.log({ status: response.status, code: problem.code });
   })();
   ```

Beklenen sonuç yalnız `{ status: 409, code: "DEAL_STATE_CONFLICT" }`
özetidir. Response içinde secret, cookie veya session ID bulunmamalıdır.

### 6. Profil B: participant izolasyonu ve non-disclosure

1. Profil B'da Profil A'dan farklı bir kullanıcıyla kayıt olun veya giriş
   yapın.
2. Profil B'nin kendisine ait geçerli bir legal entity oluşturun ve üstteki
   `Aktif legal entity` alanında onu seçin.
3. `/app/deals` listesini açın. Profil A'nın Deal başlığı, referansı veya başka
   bir verisi listede bulunmamalıdır.
4. Adres çubuğuna Profil A'da kopyaladığınız Deal UUID'siyle
   `/app/deals/PROFILE_A_DEAL_UUID` yazın. UI'da `Deal bulunamadı` ve
   non-disclosure açıklamasını görün.
5. Aynı Profile B tab'ında DevTools Console'da aşağıdaki güvenli `GET`
   kontrolünü çalıştırın:

   ```javascript
   (async () => {
     const dealId = "PROFILE_A_DEAL_UUID";
     const legalEntityId = sessionStorage.getItem(
       "m4trust:selected-legal-entity-id:v1",
     );
     if (!legalEntityId) {
       throw new Error("Profil B için aktif legal entity seçin.");
     }

     const response = await fetch(`/api/v1/deals/${dealId}`, {
       method: "GET",
       credentials: "same-origin",
       headers: {
         Accept: "application/problem+json",
         "X-M4Trust-Legal-Entity-Id": legalEntityId,
       },
     });
     const problem = await response.json();
     console.log({ status: response.status, code: problem.code });
   })();
   ```

Beklenen sonuç `{ status: 404, code: "DEAL_NOT_FOUND" }` olur. Profil A'nın
başlığı, açıklaması, referansı veya participant bilgisi response içinde
bulunmaz.

### 7. Filtre, boş sonuç, sıralama ve pagination

Bu adımları Deal verisinin sahibi olan Profil A'da ve doğru aktif entity
seçiliyken yapın:

1. `Durum` filtresinde `İptal edildi` seçeneğini seçin; iptal ettiğiniz Deal'in
   görünmesini doğrulayın.
2. Henüz bu status'ta kayıt üretmediğiniz `Arşivlendi` filtresini seçin;
   `Bu filtreyle eşleşen Deal yok.` boş durumunu ve `Filtreyi temizle`
   düğmesini görün.
3. Filtreyi temizleyin. En az iki Deal varken `Sıralama` alanındaki
   `Başlık A–Z`, `Başlık Z–A`, `En yeni oluşturulan` ve `En eski oluşturulan`
   seçeneklerinin liste sırasını beklenen yönde değiştirdiğini doğrulayın.
4. UI page size değeri 10'dur. Elle çok sayıda kayıt girmek istemiyorsanız,
   `/app/deals` sayfasında aşağıdaki yardımcıyı bir kez çalıştırabilirsiniz.
   Yardımcı yalnız public API'yi kullanır; her create öncesi yeni CSRF token
   alır, seçili entity'yi gönderir ve token/session değerlerini loglamaz:

   ```javascript
   (async () => {
     const legalEntityId = sessionStorage.getItem(
       "m4trust:selected-legal-entity-id:v1",
     );
     if (!legalEntityId) {
       throw new Error("Önce aktif legal entity seçin.");
     }

     const createCount = 12;
     for (let index = 1; index <= createCount; index += 1) {
       const csrfResponse = await fetch("/api/v1/security/csrf", {
         method: "GET",
         credentials: "same-origin",
         cache: "no-store",
         headers: { Accept: "application/json" },
       });
       if (!csrfResponse.ok) {
         throw new Error(`CSRF alınamadı: HTTP ${csrfResponse.status}`);
       }
       const csrf = await csrfResponse.json();

       const response = await fetch("/api/v1/deals", {
         method: "POST",
         credentials: "same-origin",
         headers: {
           Accept: "application/json, application/problem+json",
           "Content-Type": "application/json",
           "X-M4Trust-Legal-Entity-Id": legalEntityId,
           [csrf.headerName]: csrf.token,
         },
         body: JSON.stringify({
           title: `Pagination Taslak ${String(index).padStart(2, "0")}`,
           description: "Slice 3 manuel pagination kabul verisi",
         }),
       });
       if (!response.ok) {
         const problem = await response.json().catch(() => ({}));
         throw new Error(
           `Deal ${index} oluşturulamadı: HTTP ${response.status} ${problem.code ?? ""}`,
         );
       }
     }
     console.log(`${createCount} DRAFT Deal oluşturuldu; listeyi yenileyin.`);
   })();
   ```

5. Sayfayı yenileyip `Tüm durumlar` ve uygun bir sıralama seçin. Listenin en
   altında `Sayfa 1 / ...` bilgisini ve etkin `Sonraki` düğmesini görün.
6. `Sonraki` ile ikinci sayfaya gidin; farklı kayıtların geldiğini ve `Önceki`
   düğmesinin etkinleştiğini doğrulayın. `Önceki` ile ilk sayfaya dönün.

Tüm adımlar başarılıysa sonucu kullanıcı konuşmasında başarı olarak bildirin;
bir hata varsa adımı, görülen UI mesajını ve mümkünse HTTP status/Problem
Details `code` değerini bildirin. Bu geri bildirim gelmeden
`docs/plan/ready/03-deal-creation-and-listing.md` `done/` altına taşınmayacak ve
Slice 3 kabul edilmiş sayılmayacaktır.
