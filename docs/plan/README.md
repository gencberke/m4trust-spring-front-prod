# M4Trust Slice Plan Workflow

Bu dizin, vertical slice geliştirme planlarını (ADR-004) yönetir. Her plan dokümanı bir planner agent'ın veya geliştiricinin ilgili slice'ı ayrıntılı implementasyon görevlerine bölmesi için **yönerge** niteliğindedir; exact kod şeması veya tam DTO tanımı içermez.

## Dizin akışı

```text
planning/  → taslak; içerik tartışılıyor veya gözden geçiriliyor
ready/     → onaylandı; implementasyona alınabilir
done/      → slice'ın Done tanımı sağlandı; doküman arşivlendi
```

Kurallar:

- Bir plan `ready/` dizinine insan onayı olmadan taşınmaz.
- `ready/` içindeki plan üzerinde kapsam değişikliği gerekirse plan `planning/` dizinine geri alınır ve değişiklik açıkça yazılır.
- Slice tamamlandığında doküman `done/` dizinine taşınır; içine tamamlanma tarihi ve varsa kapsam sapmaları not edilir.
- Dosya adlandırma: `NN-kebab-case-baslik.md`. Numara ADR-004 §24'teki slice sırasını takip eder.

## Plan dokümanı formatı

Her plan aynı bölümleri içerir:

1. **Amaç ve kullanıcı sonucu** — slice bittiğinde kullanıcının gerçek tarayıcıda yapabildiği şey
2. **Kapsam / kapsam dışı** — açık in/out listesi
3. **Okunacak ADR bölümleri** — yalnız ilgili başlıklar; `architecture-decisions/ADR-INDEX.md` yönlendirme mantığı geçerlidir
4. **Public API yüzeyi** — endpoint listesi ve `contracts/openapi/core-api-v1.yaml`'a eklenecek yüzeyin tarifi
5. **Backend yönlendirmesi** — modüller, aggregate'ler, migration ihtiyacı, invariant'lar
6. **Frontend yönlendirmesi** — ekranlar, route'lar, state yaklaşımı
7. **Kabul testi (tarayıcı akışı)** — adım adım manuel akış (ADR-004 §8–9)
8. **Minimum invariant testleri** — ADR-004 §7 kapsamında, aşırı test yazılmaz
9. **Açık sorular / karar noktaları** — implementer'ın planner'a geri getirmesi gerekenler
10. **Done tanımı** — ADR-004 §23 checklist'inin slice'a uyarlanmış hali

## Planner agent'ın kullanımı

- Önce bu README'yi ve ilgili slice planını oku.
- Mikro-kararlar (format, status code, adlandırma vb.) için önce `architecture-decisions/ADR-INDEX.md` Katman 0/1'e bak; yasak kontrolü için `architecture-decisions/FORBIDDEN.md` kullan.
- Plandaki "Okunacak ADR bölümleri" listesindeki başlıkları oku; ADR'nin tamamını yalnız core policy değiştiriliyorsa oku.
- Plan ile ADR çelişirse **ADR kazanır**; çelişkiyi implementasyona gömmek yerine plana not düş ve insana bildir.
- Plandaki "öneri" ifadeleri serbestlik derecesidir; "karar" ifadeleri bağlayıcıdır.
- Public API yüzeyi implementasyondan **önce** `core-api-v1.yaml` dosyasına tasarlanır (ADR-006 §42–43 hibrit akış).

## Sabitlenmiş teknoloji kararları (tüm slice'lar için)

- Backend: Spring Boot modular monolith (ADR-003), PostgreSQL + Flyway, Spring Session JDBC
- Frontend: **Vite + React + TypeScript**, server state için **TanStack Query**, tip/client üretimi committed OpenAPI'den (öneri: `openapi-typescript` + ince fetch wrapper)
- Local orkestrasyon: Docker Compose (PostgreSQL, RabbitMQ, MinIO)
- Frontend dev server `/api` isteklerini Spring'e proxy'ler; production'daki same-origin davranışı (ADR-007 §6) local'de böyle simüle edilir
