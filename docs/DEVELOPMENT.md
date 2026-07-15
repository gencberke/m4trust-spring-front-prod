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
- [Onaylanmış Slice 0 planı](plan/ready/00-platform-foundation.md)

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

## Mevcut doğrulama sınırı

Bu belge yalnız yerleşimi ve planlanan çalışma sırasını kaydeder. Spring/frontend
build CI workflow'u yapılandırılmıştır; ancak henüz çalıştırılmamış ve yeşil
olduğu doğrulanmamıştır. Paket kurulumu, üretilmiş frontend tipleri,
Spring/frontend build'leri ve tam runtime/tarayıcı kabul akışı bir sonraki
execution aşamasıdır. Bunlar tamamlanıp doğrulanmadan Slice 0 tamamlanmış veya
kabul edilmiş sayılmaz.
