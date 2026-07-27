# Plan dizini

Bu dizin **kabul edilmiş proje durumunu** ve **üzerinde çalışılan planları**
tutar. Hackathon dönemine ait süreç kayıtları
[`docs/history/hackathon-2026-07/`](../history/hackathon-2026-07/) altına
arşivlenmiştir.

## Dizin akışı

```text
CURRENT.md → yalnız kabul edilmiş güncel proje durumu
ready/     → üzerinde çalışılan veya sıradaki plan dokümanları
```

- `CURRENT.md` backlog değildir; yalnız kabul edilmiş proje durumu maddi olarak
  değiştiğinde güncellenir.
- Sıradaki iş ve öncelikler [`docs/ROADMAP.md`](../ROADMAP.md) içindedir.
- Bir plan bütün Done koşulları kanıtlandığında `CURRENT.md` güncellenir; plan
  dosyası isterse arşive taşınır.

## Plan dokümanı ne içerir

Plan bir kod reçetesi değildir; implementasyonu davranış ve mimari düzeyde
karar-tam hale getirir. Riskle orantılı tutun — küçük bir değişiklik için plan
dokümanı gerekmez, doğrudan issue açıp uygulayın.

Plan yazılıyorsa şunlar kapalı olmalıdır:

1. **Amaç ve kullanıcı sonucu** — gerçek tarayıcıda elde edilen observable sonuç
2. **Kapsam ve sınırlar** — açık in/out kapsamı
3. **Kararlar ve ilgili ADR'ler** — bağlayıcı davranış/mimari kararlar
4. **Public interface, state ve data etkisi** — API/contract, state transition,
   persistence/migration ve compatibility etkisi
5. **Done tanımı** — gözlemlenebilir checklist

Plan exact sınıf/metot gövdeleri, tam SQL DDL veya dosya-dosya reçete içermez.
İsim veya wire alanı bir public contract, kabul edilmiş ADR ya da cross-module
sınır için bağlayıcıysa exact yazılabilir.

## Contract ve ADR kuralları

- Mikro kararlar için önce
  [`architecture-decisions/ADR-INDEX.md`](../../architecture-decisions/ADR-INDEX.md),
  yasaklar için
  [`architecture-decisions/FORBIDDEN.md`](../../architecture-decisions/FORBIDDEN.md).
- Plan ile ADR çelişirse **ADR kazanır**; çelişki implementasyona gömülmez.
- Public API yüzeyi implementasyondan önce
  `contracts/openapi/core-api-v1.yaml` içinde tasarlanır.
- OpenAPI değişikliği; validator beklentileri, `contracts/README.md` ve
  `contracts/CHANGELOG.md` ile tek review birimidir.
- İlgili plan aksini açıkça söylemedikçe AI JSON Schema/fixture, AsyncAPI ve
  AI-internal OpenAPI bu public API deltasına dahil değildir.

## Ajan kullanımı

Planner ve implementer rolleri [`docs/agent/`](../agent/) altında tanımlıdır.
Hackathon dönemindeki zorunlu task-packet ve `req-review.md` protokolü artık
uygulanmıyor; kayıtları
[arşivde](../history/hackathon-2026-07/) bulabilirsiniz.

## Sabit teknoloji kararları

- Backend: Spring Boot modular monolith, PostgreSQL + Flyway, Spring Session JDBC
- Frontend: Vite + React + TypeScript, TanStack Query, committed OpenAPI'den tip
  üretimi
- Local orkestrasyon: Docker Compose ile PostgreSQL, RabbitMQ ve MinIO
- Local frontend `/api` isteklerini Spring'e proxy'ler; production same-origin
  davranışı bu şekilde simüle edilir
