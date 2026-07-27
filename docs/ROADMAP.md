# M4Trust yol haritası

Bu dosya **bundan sonra ne yapacağımızı** tutar.
Kabul edilmiş mevcut durum için [`docs/plan/CURRENT.md`](plan/CURRENT.md),
bağlayıcı mimari kurallar için
[`architecture-decisions/`](../architecture-decisions/ADR-INDEX.md) kullanılır.

Son güncelleme: 2026-07-27

## Nerede duruyoruz

Hackathon Temmuz 2026'da tamamlandı. `main@47f3d2a` üzerinde kabul edilmiş
kapsam: deal yaşam döngüsü uçtan uca (oluşturma → davet → ratification →
funding → fulfillment/evidence → dispute/casework → simulated settlement),
Railway üzerinde `RAILWAY_DEMO_READY` etiketli kontrollü demo runtime'ı.

Ürün ve fikir devam ediyor. Yarış temposunda biriken **yapısal borç** önce
kapatılıyor; ürün yönü kararları ondan sonra alınacak.

## Şu an: repo toparlama (devam ediyor)

| Faz | Kapsam | Durum |
|---|---|---|
| 0 | Kırık referanslar, gitignore, script platform paritesi, CI'da beyan edilmemiş python bağımlılığı | ✅ |
| 1 | Hackathon docs arşivi, bu yol haritası, ADR status alanlarının gerçekten kullanılması | 🔄 |
| 2 | Frontend zemini: ESLint/Prettier/Vitest, `src/shared/`, path alias, 43 test | ✅ |
| 3 | Frontend yapı: `pages`/`features` sınırı, dev component'lerin bölünmesi, CSS Modules | ⏳ |
| 4 | Backend modül içi `api/domain/infra` katmanlaması, spotless + jacoco | ⏳ |
| 5 | Test kapsamı ve ADR revizyonu turu | ⏳ (kapsam tanımlanmadı) |

Gerekçe ve ayrıntı: bu turun audit bulguları aşağıdaki "Bilinen boşluklar"
bölümünde özetlenmiştir.

## Bilinen boşluklar

Dört paralel audit'ten çıkan, henüz kapatılmamış maddeler:

**Faz 2'den devredilen, Faz 3'te kapatılacak lint borcu**
- `react-hooks/set-state-in-effect` — dört panel (`DealFundingPanel`,
  `DealSettlementPanel`, `DealDetailPage`, `DealListPage`) effect içinde
  senkron `setState` çağırıyor; bu cascading render tetikliyor. Kural şu an
  `warn` seviyesinde, çünkü düzeltmek panellerin senkronizasyon mantığını
  yeniden kurgulamayı gerektiriyor — bu da Faz 3'ün component bölme işine ait.
- `no-restricted-imports` — `pages/` katmanı feature iç dosyalarını doğrudan
  import ediyor. Faz 3'te her feature `index.ts` barrel'ı kazanınca kural
  `error` seviyesine çıkarılacak.

**Test ve doğrulama**
- `audit` modülünün kendi test sınıfı yok; append-only audit trail yalnız başka
  modüllerin atomicity testlerinden dolaylı geçiyor.
- `tools/mock-ai-worker` ve `tools/moka-emulator` altındaki 7 test dosyası CI'da
  hiç koşmuyor.
- Frontend'de test yok (Faz 2'de temel atılıyor).
- Hiçbir dilde lint/format gate'i yok; bağımlılık güvenlik taraması yok
  (Dependabot, `npm audit`, `pip-audit` — hiçbiri kurulu değil).

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
| `sharedkernel` paketi | Money/Ids/Clock ile doldur **veya** kaldır (bugün boş, işi `api`/`audit`/`idempotency`/`organization` yapıyor) | Faz 4 başında |
| Ürün yönü | Repo toparlama bittikten sonra ayrı bir oturumda | Faz 4 sonrası |

## Güncelleme kuralı

Bu dosya niyeti tutar, kabul edilmiş durumu değil. Bir madde gerçekten
kabul edildiğinde `docs/plan/CURRENT.md` güncellenir ve buradaki satır
kapatılır.
