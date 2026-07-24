# M4Trust

M4Trust, güvenilir ticari işlem (Deal) yaşam döngüsünü uçtan uca yöneten bir
monorepo'dur: Spring Boot Core API, Vite/React istemcisi, paylaşılan API
contract'ları ve yerel geliştirme altyapısı aynı depoda bulunur.

## Ön koşullar

- Docker (Compose ile yerel PostgreSQL, RabbitMQ, MinIO)
- Java 21 (Core API)
- Node.js >= 22.12 (frontend)

## Hızlı başlangıç

Yerel kurulum, başlatma sırası ve ortam değişkenleri:
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) (bash ve PowerShell komutları).

## Doğrulama

Hafif doğrulama haritası (test ve contract kontrolleri):
[`docs/VALIDATION.md`](docs/VALIDATION.md).

## Agent girişi

Planner/Implementer yönlendirmesi ve keşif bağlantıları:
[`AGENTS.md`](AGENTS.md).

## Bileşenler

- [`frontend/`](frontend/) — Vite + React + TypeScript istemci
- [`services/core-api/`](services/core-api/) — Spring Boot modular-monolith API
- [`contracts/`](contracts/) — OpenAPI, AsyncAPI, JSON Schema ve örnekler
- [`infra/`](infra/) — Yerel Docker Compose altyapısı
