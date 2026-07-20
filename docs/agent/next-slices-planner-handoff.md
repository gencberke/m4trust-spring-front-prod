# Planner Handoff — Slice 12 Sonrası

Hazırlanma tarihi: 20 Temmuz 2026
Kabul branch'i: `codex/slice-12-fulfillment`

Bu doküman fresh-context planner için kısa durum aktarımıdır. Başlangıçta
`docs/agent/WORKFLOW.md`, `docs/agent/CURRENT.md`, `docs/plan/README.md`,
`architecture-decisions/ADR-INDEX.md` ve
`architecture-decisions/FORBIDDEN.md` okunmalıdır.

## Kabul edilmiş durum

- Slice 0–12 kabul edilmiştir.
- Slice 12 planı `docs/plan/done/12-fulfillment-and-evidence.md` altındadır.
- Kabul kanıtı `docs/agent/slice-12-acceptance-2026-07-20.md` içindedir.
- V15–V20 kabul edilmiş ve donmuş migration history'dir; yeni DB değişiklikleri
  yeni forward-only migration kullanır.
- Slice 12; seller start, tek fulfillment/primary milestone, immutable evidence
  object version, direct MinIO upload/finalize, participant read/history,
  buyer accept/reject/replacement ve fail-closed `COMPLETED` sınırını kurdu.
- Fulfillment completion Deal'i tamamlamaz ve ödeme release/settlement/refund,
  provider çağrısı, dispute veya AI işi üretmez.
- Browser kabulünde kullanıcı minimum kapsam istedi. Kritik iki taraflı akış
  gerçek PostgreSQL ve MinIO ile geçti; tam rol/yarış matrisi browser'da tekrar
  edilmedi ve bu sapma done planı ile kabul kaydında açıkça yazıldı.

## Sonraki içerik hattı

Önerilen sıra mevcut `docs/plan/planning/next-slices-sequencing.md` kararıyla
uyumludur:

1. Slice 13 — Video Analysis
2. Dispute/Settlement için önce gerekli ADR ve insan onayı
3. Gerçek payment provider entegrasyonu ayrı bir hat

Railway deployment/staging ile gerçek provider işleri kullanıcı tarafından
özellikle ertelenmiştir. Yeni içerik planına karıştırılmamalıdır.

## Slice 13 planlama bağları

- Girdi, Slice 12'nin immutable ve finalize edilmiş video evidence kaydıdır.
- Slice 8'in RabbitMQ outbox/inbox ve Mock AI Worker desenleri yeniden
  kullanılmalıdır.
- AI sonucu advisory kalır; fulfillment, Deal, payment veya dispute state'ini
  otomatik değiştiremez.
- Public Core API değişikliği contract-first tasarlanır. AI JSON
  Schema/fixture, AsyncAPI veya AI-internal OpenAPI değişikliği gerekiyorsa
  mevcut insan onayının kapsamına varsayılmaz; açık eskalasyon gerekir.
- Modül erişimi dar port + adapter yönünde kalmalı ve
  `ModuleArchitectureTest` ile korunmalıdır.
- Deployment, production object storage, secret yönetimi ve provider release
  bu planın dışındadır.

## Planner'ın sıradaki işi

Kullanıcı yeni planlama istediğinde önce accepted ADR'lerde Video Analysis
kararlarının yeterli olup olmadığını denetle. Eksik yüksek-etkili kararları
eskalasyon listesine çıkar; ardından sekiz bölümlü Slice 13 taslağını
`docs/plan/planning/` altında hazırla. İnsan onayı olmadan `ready/` altına taşıma
ve implementer task paketi üretme.
