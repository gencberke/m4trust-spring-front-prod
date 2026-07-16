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
- [Tamamlanmış Slice 3 planı](plan/done/03-deal-creation-and-listing.md)
