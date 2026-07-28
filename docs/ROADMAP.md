# M4Trust yol haritası

Bu dosya **bundan sonra ne yapacağımızı** tutar.
Kabul edilmiş mevcut durum için [`docs/plan/CURRENT.md`](plan/CURRENT.md),
bağlayıcı mimari kurallar için
[`architecture-decisions/`](../architecture-decisions/ADR-INDEX.md) kullanılır.

Son güncelleme: 2026-07-28

## Nerede duruyoruz

Hackathon Temmuz 2026'da tamamlandı. `main@47f3d2a` üzerinde kabul edilmiş
kapsam: deal yaşam döngüsü uçtan uca (oluşturma → davet → ratification →
funding → fulfillment/evidence → dispute/casework → simulated settlement),
Railway üzerinde `RAILWAY_DEMO_READY` etiketli kontrollü demo runtime'ı.

Ürün ve fikir devam ediyor. Hackathon temposunda biriken **yapısal borç**
(repo toparlama faz 0–4 + closeout) kabul edilerek kapatıldı; ürün yönü
kararları ayrı oturumda alınacak.

## Repo toparlama

| Faz | Kapsam | Durum |
|---|---|---|
| 0 | Kırık referanslar, gitignore, script platform paritesi, CI'da beyan edilmemiş python bağımlılığı | ✅ |
| 1 | Hackathon docs arşivi, bu yol haritası, ADR status alanlarının gerçekten kullanılması | ✅ |
| 2 | Frontend zemini: ESLint/Prettier/Vitest, `src/shared/`, path alias, Vitest suite | ✅ |
| 3 | Frontend yapı: `pages`/`features` sınırı, büyük panel/component bölünmesi, CSS Modules, barrel import'lar | ✅ |
| 4 | Backend modül içi `api/domain/infra` katmanlaması, Spotless + JaCoCo (%85 satır regresyon tabanı) | ✅ |
| Audit düzeltme (A–E) | CI gate'leri, ESLint/ArchUnit, doküman senkronu | ✅ |
| Closeout | Spotless, test lifecycle, domain sınırı, coverage ADR, repo-hygiene CI | ✅ ACCEPT |
| 5 | Compact ADR authority and critical validation spine | 🟡 implementation/review pending |

Faz 0–4, audit remediation ve closeout kabul edildi. Phase 5 implementasyonu
başladı; bağımsız review kabulü gelmeden mevcut durum veya accepted authority
değişmiş sayılmaz.

Gerekçe ve ayrıntı: aşağıdaki "Bilinen boşluklar" bölümü ertelenmiş Phase 5
işlerini ve üretimleşme boşluklarını tutar.

Faz 4 notu: `sharedkernel` boş iskelet olarak **korunacak**; Money/Ids/Clock
primitifleri şimdilik `api`, `audit`, `idempotency` ve `organization` modüllerinde.

## Bilinen boşluklar

Dört paralel audit'ten çıkan, henüz kapatılmamış maddeler:

**Frontend yapı borcu**
- `frontend/src/features/fulfillment/components/EvidenceUploadSection.tsx` —
  **518 satır.** Faz 3'te `DealFulfillmentPanel` 905 → 316 satıra inerken
  çıkan yeni dosya. Panel küçüldü ama upload akışı tek parça kaldı; iş
  ayrıştırılmaktan çok taşındı. Bilinçli olarak Faz 3 kapsamı dışında
  bırakıldı; upload formu, doğrulama ve mutation zinciri hâlâ tek component'te.
- `frontend/src/features/casework/DealCaseworkPanel.tsx` — **733 satır.**
  Faz 3'te hiç dokunulmadı ve şu an frontend ağacının en büyük dosyası.
  Kardeş paneller bölündü (`review` 932 → 247, `ratification` 892 → 173);
  sıra bu panele gelmeden Faz 3 kapatıldı. Dispute açma, yorum ve durum
  akışları tek dosyada kaldı.

**Test ve doğrulama**
- Phase 5 test/ADR konsolidasyonu implementation/review pending durumunda:
  JaCoCo yüzde tabanı ve test sayısı gate değildir; kritik invariant, contract,
  migration, architecture ve public-boundary kanıtları tutulur.
- Audit transaction/append-only kanıtı ve pruned local-tool suite'lerinin CI
  entegrasyonu Phase 5 branch'inde uygulanmıştır; bağımsız kabul bekler.
- Secret, bağımlılık, lisans, zafiyet, SBOM ve provenance taraması yok; bu boşluk
  geniş bir production-readiness iddiasını engeller ve sonraki ürünleşme turunda
  ele alınır.
- Frontend presentation tests ve browser automation bu Phase 5'in dışında kalır;
  yalnız session/API boundary ve pure helper kanıtları tutulur.
- Framework-free domain dönüşümü yapılmadı; compact ArchUnit omurgası yalnız
  top-level cycle, first-party `domain ↛ api|infra` ve repository ownership
  sınırlarını zorlar.

**Ürünleşme kararları (açık)**
- Kabul edilmiş CURRENT'deki demo carve-out'ları (backup/PITR, invite-only ve
  malware quarantine ertelemeleri) ürünleşme kararı verilirse yeni karar turu
  ister.
- Gerçek ödeme sağlayıcısı: 2026-07-22'de simulation-only'ye pivot edildi.
  Production legal/KYC/custody/fee/split/payout soruları **açık**.
- AI capability ayrı bir repo/ekip tarafından sahiplenir; bu repo yalnız
  shared-contract uyumunu ve Spring sınırını yönetir.

**Taşınabilirlik**
- Railway deploy config'i derinden yük taşıyor (`railway.json` şeması,
  `RAILWAY_PRIVATE_DOMAIN` referans söz dizimi, iki ayrı dashboard ayarı).
  Başka bir platforma taşınma senaryosu için soyutlama katmanı yok.

## Karar bekleyenler

| Konu | Seçenekler | Ne zaman |
|---|---|---|
| Ürün yönü | Repo toparlama bittikten sonra ayrı bir oturumda | Closeout sonrası |

## Güncelleme kuralı

Bu dosya niyeti tutar, kabul edilmiş durumu değil. Bir madde gerçekten
kabul edildiğinde `docs/plan/CURRENT.md` güncellenir ve buradaki satır
kapatılır.
