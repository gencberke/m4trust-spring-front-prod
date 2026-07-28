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
| 5 | Test kapsamı ve ADR revizyonu turu | ⏳ (kapsam tanımlanmadı) |

Faz 0–4, audit remediation ve closeout kabul edildi. Repo toparlama bu
noktada kapanır; kalan boşluklar Phase 5 ve ürünleşme kararlarına bırakılır.

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
- Core `mvn verify` JaCoCo %85 satır tabanı regresyon bariyeridir; slice kabul kanıtı
  değildir (ADR-004 §30). Birincil kanıt: kritik invariant, contract, mimari kural ve
  tarayıcı kabul akışları.
- `audit` modülünün kendi davranışsal test sınıfı yok; append-only audit trail yalnız
  başka modüllerin atomicity testlerinden dolaylı geçiyor (Phase 5 adayı).
- `tools/mock-ai-worker` ve `tools/moka-emulator` altındaki birim suite'leri CI'da
  koşmuyor (`smoke_rabbitmq.py` pytest varsayılan toplama desenine uymuyor).
- Secret, bağımlılık, lisans, zafiyet, SBOM ve provenance taraması yok; bu boşluk
  geniş bir production-readiness iddiasını engeller (ADR-016/022 daraltılmış
  demo kapsamı dışında sessizce genişletilmez).
- Frontend feature testleri, browser automation ve coverage eşiği Phase 5 kapsamına
  bırakılmıştır.
- Framework-free domain dönüşümü yapılmadı; ArchUnit yalnız first-party
  `domain ↛ api|infra` ve request-body konumunu zorlar.

**Ürünleşme kararları (açık)**
- ADR-022 demo carve-out'ları: backup/PITR waiver (§2.7), invite-only kayıt
  (ADR-017) ve malware quarantine (ADR-018) ertelemeleri. Ürünleşme kararı
  verilirse bunlar yeni bir ADR turu ister.
- Gerçek ödeme sağlayıcısı: 2026-07-22'de simulation-only'ye pivot edildi.
  Production legal/KYC/custody/fee/split/payout soruları **açık**.
- AI capability ayrı bir repo/ekip tarafından sahipleniyor (ADR-019); bu repo
  yalnız shared-contract uyumunu yönetir.

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
