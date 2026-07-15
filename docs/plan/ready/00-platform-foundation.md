# Slice 0 — Platform Foundation

- Durum: ready
- Slice sırası: ADR-004 §24 → Slice 0
- Öncül: yok (ilk slice)
- Ardıl: 01-authentication

## 1. Amaç ve kullanıcı sonucu

Bu slice kullanıcıya business capability sunmaz (ADR-004 §25). Hedef, sonraki bütün slice'ların tekrar üretilebilir biçimde geliştirilebileceği zemini kurmaktır:

Geliştirici tek compose komutuyla ortamı ayağa kaldırır → PostgreSQL, RabbitMQ ve MinIO hazır olur → Spring Core API başlar, Flyway migration'ları çalışır, health endpoint'leri yeşildir → frontend dev server açılır ve Spring'in health durumunu ekranda gösterir.

Bu zincir uçtan uca çalışmadan Slice 0 bitmiş sayılmaz.

## 2. Kapsam / kapsam dışı

Kapsam:

- Monorepo dizin yerleşimi
- Docker Compose ile local altyapı (PostgreSQL, RabbitMQ, MinIO)
- `services/core-api` Spring Boot iskeleti
- `frontend` Vite + React + TS iskeleti
- Flyway migration zinciri (ilk boş/temel migration)
- Health endpoint'leri, correlation ID, Problem Details global handler
- Committed `contracts/openapi/core-api-v1.yaml` iskeletinin başlatılması
- Local reset/seed mekanizması
- CI'a Spring build + frontend build adımlarının eklenmesi

Kapsam dışı:

- Her türlü business endpoint (auth dahil — Slice 1)
- Mock AI Worker implementasyonu (yalnız dizin yeri ayrılır; Slice 6'da yazılır)
- RabbitMQ topolojisinin (exchange/queue) kurulumu — AI slice'ına kadar broker yalnız compose'da çalışır durumda olur
- Railway staging/production kurulumu (ayrı operasyonel iş; ADR-007 kararları geçerli ama bu slice local'i hedefler)
- Observability platformu (yalnız structured log + correlation ID; ADR-007 §34)

## 3. Okunacak ADR bölümleri

- ADR-004 §25 (Slice 0 tanımı), §20 (local development ortamı), §21 (test verisi)
- ADR-003 §4 (Spring modülleri — paket konvansiyonu için), §27 (veri tipi kararları)
- ADR-006 §13–16 (Problem Details), §34 (correlation ID), §42–43 (OpenAPI hibrit işleyiş)
- ADR-007 §7 (frontend deployment davranışları), §17 (monorepo), §18–19 (config/secret), §21–22 (Flyway/local migration), §29–32 (health, logging)

## 4. Public API yüzeyi

Business endpoint yok. `core-api-v1.yaml` bu slice'ta oluşturulur ve yalnız şunları içerir:

- OpenAPI 3.1 iskeleti (info, servers, ortak component'ler)
- Reusable component şemaları: `ProblemDetail`, `FieldError` (ADR-006 §45) — sonraki slice'lar bunları referanslar
- Henüz endpoint tanımı yok; her slice kendi yüzeyini ekler

Spring management endpoint'leri (`/livez`, `/readyz` veya actuator eşdeğerleri) public contract'ın parçası değildir; OpenAPI'ye eklenmez.

## 5. Backend yönlendirmesi

**Dizin yerleşimi** (ADR-007 §17 örneğiyle uyumlu):

```text
frontend/
services/core-api/
tools/mock-ai-worker/        (yalnız README'li yer tutucu)
contracts/                   (mevcut)
architecture-decisions/      (mevcut)
docs/plan/                   (mevcut)
infra/                       (docker-compose ve local config)
scripts/                     (dev-reset, dev-seed)
```

**Spring iskeleti:**

- Paket konvansiyonu ADR-003 §4 modül adlarını taşır: `...m4trust.<modül>` (identity, organization, deal, ...). Bu slice'ta yalnız `sharedkernel` ve `integration` için iskelet paket + package-info açıklaması oluşturmak yeterli; boş modül paketleri önceden açılmaz, her slice kendi modülünü açar. Modül sınırlarını derleme zamanında koruma aracı (öneri: Spring Modulith veya ArchUnit kuralı) burada kurulursa sonraki slice'lar bedava korunur — araç seçimi implementer'a bırakılmıştır, ancak bir mekanizma kurulmalıdır.
- Flyway: `V1__baseline.sql` benzeri ilk migration; Spring Session JDBC tabloları Slice 1'de gelir, burada zorunlu değil. Migration adlandırma standardı bu slice'ta yazılı hale getirilir (kısa bir `services/core-api/README` bölümü yeterli).
- Correlation ID: `X-Correlation-ID` header'ını okuyan/üreten ve response'a geri yazan bir filter + MDC entegrasyonu (ADR-006 §34).
- Problem Details: RFC 9457 üreten global exception handler; `code` ve `correlationId` extension alanları ilk günden var (ADR-006 §13). Spring'in `ProblemDetail` desteği kullanılabilir.
- Config: environment tabanlı (ADR-007 §18); zorunlu config eksikse fail-fast. Port hard-code edilmez.
- Logging: stdout'a structured JSON; en az timestamp, level, service, correlationId alanları (ADR-007 §32).

**Docker Compose:**

- PostgreSQL, RabbitMQ (management UI local'de açık olabilir), MinIO. Spring ve frontend'in compose içinde mi yoksa host'ta mı çalışacağı implementer tercihidir; hedef deneyim ADR-004 §20'deki zincirdir.
- `scripts/dev-reset` ve `scripts/dev-seed`: reset volume'ları temizler; seed bu slice'ta boş kalabilir ama mekanizma (çalıştırılabilir script + dokümantasyon) kurulur. Windows'ta çalışması gerektiği unutulmamalı (PowerShell eşdeğeri veya cross-platform yaklaşım).

## 6. Frontend yönlendirmesi

- Vite + React + TypeScript. Router (öneri: React Router; TanStack Router da kabul) + TanStack Query provider iskeleti.
- Vite dev proxy: `/api/*` → Spring (adres environment/config'ten; ADR-005 §25 gereği hard-code port yok).
- OpenAPI tip üretim boru hattı: committed `core-api-v1.yaml`'dan tip üreten script (`openapi-typescript` önerilir) + üretilen dosyanın elle düzenlenmeyeceği kuralı. Bu slice'ta üretilecek anlamlı tip azdır; amaç boru hattının çalışır olmasıdır.
- Tek sayfa: uygulama kabuğu + Spring health durumunu gösteren basit bir gösterge (readiness endpoint'ine istek). Loading ve error durumu burada bile gösterilir — sonraki slice'ların standardını kurar.
- `PUBLIC_*` olmayan hiçbir değer frontend env'ine girmez (ADR-007 §20).

## 7. Kabul testi (tarayıcı akışı)

1. Temiz makinede (veya `dev-reset` sonrası) compose başlatılır.
2. PostgreSQL, RabbitMQ, MinIO healthy olur.
3. Spring başlar; Flyway migration loglarda görünür; `/readyz` 200 döner.
4. Frontend dev server başlar.
5. Tarayıcıda uygulama açılır; Spring bağlantı durumu "sağlıklı" görünür.
6. Spring durdurulur; frontend anlamlı bir hata/bağlantı-yok durumu gösterir (beyaz ekran değil).
7. `dev-reset` çalıştırılıp ortam yeniden kurulduğunda aynı sonuç tekrar elde edilir.

## 8. Minimum invariant testleri

Bu slice'ta business invariant yok. Yalnız:

- Problem Details serialization'ının beklenen alanları (`type`, `status`, `code`, `correlationId`) ürettiğine dair tek bir test
- Correlation ID filter'ının header yokken ürettiği, varken koruduğuna dair tek bir test

CI: mevcut contract validation workflow'una ek olarak Spring build ve frontend production build adımları.

## 9. Açık sorular / karar noktaları

- Modül sınırı koruma aracı: Spring Modulith mi, ArchUnit mi? (implementer önerir, tek PR'da karara bağlanır)
- Compose'da Spring/frontend container olarak mı koşacak, host'ta mı? (geliştirme ergonomisine göre implementer seçer)
- Actuator path'leri `/livez`/`/readyz` olarak mı map'lenir, actuator default'ları mı kullanılır? (ADR-007 §29 ikisine de izin verir)

## 10. Done tanımı

- [ ] Monorepo dizin yapısı kuruldu ve `docs/` altında kısaca belgelendi
- [ ] Tek komutla local altyapı ayağa kalkıyor; reset/seed scriptleri çalışıyor
- [ ] Spring başlıyor, Flyway çalışıyor, health endpoint'leri doğru davranıyor
- [ ] Problem Details + correlation ID altyapısı çalışıyor ve testli
- [ ] Frontend gerçek Spring'e proxy üzerinden bağlanıyor; health durumu ve hata durumu ekranda
- [ ] `core-api-v1.yaml` iskeleti commit'lendi; tip üretim boru hattı çalışıyor
- [ ] CI: contract validation + Spring build + frontend build yeşil
- [ ] Kabul akışı (§7) temiz ortamda baştan sona çalıştırıldı
