> **Proje durumu değildir.** Bu dosya 14A/14B sonrası planlama aktarım taslağıdır; kabul edilmiş Plan 17 (simulated settlement) ile yol haritası çerçevesi güncellenmiştir. Otoritatif kabul edilmiş durum `docs/plan/CURRENT.md` ve `docs/plan/ready|done/` altındadır. Bu belgeyi uygulama otoritesi olarak kullanmayın.

# Planner Handoff — Settlement/Release Roadmap

Hazırlanma tarihi: 21 Temmuz 2026

Son güncelleme: 22 Temmuz 2026

Kabul branch'i: `feature/14a-dispute-casework-foundation`

Kabul edilen implementation HEAD:
`e30c185733a601014d8ebbc8413b3cc6a1b2c85d`

Main merge commit'i: `0282c0e103a2fd3c0cacd32b11cb639c098b803c`

Bu doküman fresh-context planner için kısa durum aktarımıdır. Başlangıçta
`docs/agent/planner-agent.md`, `docs/plan/CURRENT.md`, `docs/plan/README.md`,
`architecture-decisions/ADR-INDEX.md` ve
`architecture-decisions/FORBIDDEN.md` okunmalıdır.

## Kabul edilmiş durum

- Slice 0–14A kabul edilmiştir.
- Slice 14A planı
  `docs/plan/done/14a-dispute-and-casework-foundation.md` altındadır.
- Kabul kanıtı `docs/plan/done/review/14a-15p4-implementation-review-handoff.md` içindedir.
- ADR-013 Accepted durumundadır ve Dispute/Casework V1 için otoritatiftir.
- V15–V22 kabul edilmiş, donmuş migration history'dir. Yeni DB ihtiyacı yeni
  forward-only migration kullanmalıdır.
- Slice 13, Slice 12'nin immutable finalized VIDEO/MP4 evidence kaydından
  yalnız buyer ADMIN tarafından açıkça başlatılan ve retry edilen video analizi
  kurdu. RabbitMQ outbox/inbox ile Mock AI Worker deseni yeniden kullanılır.
- Sonuç advisory-only'dir. Deal, fulfillment, evidence review, payment,
  dispute/casework veya settlement state'ini otomatik değiştiremez.
- Katılımcılar durum ve güvenli advisory sonucu okuyabilir; talep/retry yetkisi
  backend `canRequest` kararıyla sınırlıdır. Teknik model/provider/prompt/storage
  ayrıntıları public response'a sızmaz.

## Slice 14A kabul özeti

- Buyer/seller ADMIN open, party ADMIN/MEMBER read-comment, counterparty ADMIN
  acknowledge ve opener ADMIN withdraw akışları kabul edilmiştir.
- Opening snapshot fulfillment/milestone, finalized evidence ve mevcut başarılı
  video sonucu provenance'ını immutable saklar.
- Other participant casework varlığını veya `DISPUTE` lifecycle'ını göremez.
- Casework Deal, fulfillment, evidence, funding, payment, settlement, provider,
  messaging veya AI state'i değiştirmez.
- Implementer contract validator, 331-test full backend verify, focused
  migration/authorization/concurrency/regression matrix ve frontend
  typecheck/build sonuçlarını PASS raporladı. Kapanışta kullanıcı yönlendirmesi
  gereği planner bu komutları yeniden koşmadı.

## Kabul edilmiş browser borcu

Slice 14A kapanışında planner-owned §6 matrisi ve Slice 13 historical VIDEO/MP4
gözlemi 14B'ye aktarılmıştı. Bu borç **gate C0** ile 21 Temmuz 2026'da kapatıldı.

Kanıt: `docs/plan/done/review/14a-15p4-implementation-review-handoff.md` (Deal
`DL-0000000017`). Historical Slice 13 / 14A acceptance kayıtları yeniden
yazılmadı; borç yalnızca current/future planner state'ten kaldırıldı.

14B acceptance artık bu matrisi açık borç olarak taşımaz; kendi settlement/
release kabul kanıtını üretmelidir.

## Sonraki içerik hattı

Sıradaki içerik Slice 14B Settlement and Release planlamasıdır.
`docs/plan/planning/14b-settlement-and-release.md` henüz implementation-authorizing
değildir. Provider capability, legal/operational model, ratification contract,
Slice 7/C2-G4b ile G2/G3 21 Temmuz 2026'da, Slice 11B-A ise 22 Temmuz
2026'da kabul edilmiştir. Founder 22 Temmuz'da payment/release için
simulation-only kapsamı seçti; gerçek-provider Slice 11B-B/G1 rotası
superseded oldu. G1-S ve G4c simulation scope için kabul edildi. Sıradaki iş
ADR-014'tür. Slice 14B; ADR-014 ve final human approval kapanmadan `ready/`
durumuna geçemez veya task packet üretemez.

Kullanıcı tüm planner/implementer handoff'larının sahibidir.
