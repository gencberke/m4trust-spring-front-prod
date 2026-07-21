# Planner Handoff — Slice 13 Sonrası

Hazırlanma tarihi: 21 Temmuz 2026

Kabul branch'i: `codex/slice-13-video-analysis`

Kabul edilen implementation HEAD: `cdfb97a4dbb65644a42e16a7c26eb120cf8980c5`

Bu doküman fresh-context planner için kısa durum aktarımıdır. Başlangıçta
`docs/agent/WORKFLOW.md`, `docs/agent/CURRENT.md`, `docs/plan/README.md`,
`architecture-decisions/ADR-INDEX.md` ve
`architecture-decisions/FORBIDDEN.md` okunmalıdır.

## Kabul edilmiş durum

- Slice 0–13 kabul edilmiştir.
- Slice 13 planı `docs/plan/done/13-video-analysis.md` altındadır.
- Kabul kanıtı `docs/agent/slice-13-acceptance-2026-07-21.md` içindedir.
- ADR-012 Accepted durumundadır ve Video Analysis V1 için otoritatiftir.
- ADR-013 Accepted durumundadır; human-approved Slice 14A planı
  `docs/plan/ready/14a-dispute-and-casework-foundation.md` altındadır ve henüz
  implementasyon task'ı verilmemiştir.
- V15–V21 kabul edilmiş, donmuş migration history'dir. Yeni DB ihtiyacı yeni
  forward-only migration kullanmalıdır.
- Slice 13, Slice 12'nin immutable finalized VIDEO/MP4 evidence kaydından
  yalnız buyer ADMIN tarafından açıkça başlatılan ve retry edilen video analizi
  kurdu. RabbitMQ outbox/inbox ile Mock AI Worker deseni yeniden kullanılır.
- Sonuç advisory-only'dir. Deal, fulfillment, evidence review, payment,
  dispute/casework veya settlement state'ini otomatik değiştiremez.
- Katılımcılar durum ve güvenli advisory sonucu okuyabilir; talep/retry yetkisi
  backend `canRequest` kararıyla sınırlıdır. Teknik model/provider/prompt/storage
  ayrıntıları public response'a sızmaz.

## Planner review düzeltmeleri ve kabul

- D1: Gerçek cross-tenant Deal akışındaki tenant/FK uyumsuzluğu giderildi.
- D2: Evidence media type response'u enum adı yerine committed MIME değeri
  (`video/mp4`, `application/pdf`) üretir.
- D3: Historical VIDEO/MP4 evidence satırları retained video-analysis durumunu
  ve sonucunu gösterir; current evidence paneli iki kez render edilmez.
- Full backend verify 292 test, focused backend matrix 65 test, Mock AI Worker
  27 test, contract validator, frontend typecheck/build ve Compose config geçti.
- Browser kabulünün cross-tenant request, gerçek worker sonucu, rol sınırları,
  retry/idempotency, manual review ve non-video regresyon kısımları geçti.

## Kabul edilmiş browser borcu

Kullanıcı, D3 frontend düzeltmesinden sonra yalnız historical paneli tekrar
eden son scoped browser koşusunu açıkça erteledi. Bu, Slice 13 kabulünü bloke
etmeyen kayıtlı borçtur; koşulmuş gibi raporlanmamalıdır.

Historical evidence alanına dokunan ilk ilgili browser regresyonunda şunlar
kanıtlanmalıdır:

1. ACCEPTED veya REJECTED historical VIDEO/MP4 satırı retained advisory paneli
   ve sonucu gösterir.
2. Historical panel manual review veya başka business-state mutation kontrolü
   sunmaz.
3. Current SUBMITTED evidence paneli historical listede duplicate render olmaz.

## Sonraki içerik hattı

Mevcut sequencing kararına göre sıradaki içerik Slice 14A'dır. ADR-013 ve
sekiz bölümlü plan 21 Temmuz 2026'da insan-onaylıdır; plan
`docs/plan/ready/14a-dispute-and-casework-foundation.md` altındadır.
Implementasyon henüz başlamamıştır ve yalnız planner-issued task packet ile
başlayabilir. İlk persistence migration'ı V22'dir; V15–V21 donmuş kalır.

Payment release/settlement/refund, gerçek provider entegrasyonu ve Railway
deployment/staging kullanıcı yeniden açmadıkça kapsam dışıdır. Kullanıcı tüm
planner/implementer handoff'larının sahibidir.
