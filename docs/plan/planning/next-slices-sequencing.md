# Slice 11 Sonrası İçerik Sıralaması

- Durum: accepted sequencing decision — 20 Temmuz 2026
- Hazırlanma tarihi: 20 Temmuz 2026
- Repo başlangıcı: `main@0b663d0`
- Kabul başlangıcı: Slice 10 ve Slice 11 kabul borcu kapanmıştır; ilgili
  planlar `done/` altındadır, gerçek browser kabulü geçmiştir ve V15–V19
  migration'ları dondurulmuş geçmiş kabul edilir.

Bu dokümanın onayı yalnız yol haritası sırasını kabul eder. Bir slice planını
`ready/` durumuna taşımaz ve implementer task paketi üretme yetkisi vermez.

> **Superseded route notice — 22 July 2026:** the founder selected
> simulation-only payment/release. The real-provider Slice 11B route below is
> preserved as historical sequencing context but is no longer actionable.
> Current authority is
> `docs/agent/gates/simulation-only-payment-decision-2026-07-22.md`.

## 1. Karar ölçütleri

Sıralama şu ölçütlerle değerlendirilmiştir:

1. Kabul edilmiş bir state veya capability üzerine doğrudan kurulabilmesi
2. Sonraki slice için first-class business kaydı ve public kullanıcı akışı
   açması
3. Hukuki, provider veya production credential kararına bağımlı olmadan gerçek
   browser'da kabul edilebilmesi
4. FORBIDDEN sınırına girmemesi ve yeni ADR sayısını düşük tutması
5. Sonradan atılacak entegrasyon kodu yerine kalıcı domain omurgası üretmesi

## 2. Adayların bağımlılık ve risk analizi

### Aktif içerik kapsamı

| Aday | Hazır bağımlılık | Eksik kapı | Risk | Sıralama sonucu |
| --- | --- | --- | --- | --- |
| Slice 12 — Fulfillment and Evidence | Slice 11'in `FUNDED` sonucu, private object storage ve direct-upload deseni hazır | Fulfillment V1 actor/consent, milestone kaynağı ve completion semantiği kararı | Orta; provider ve AI'dan bağımsız | **İlk içerik slice'ı** |
| Slice 13 — Video Analysis | RabbitMQ outbox/inbox, Mock AI Worker ve video contract'ları mevcut | First-class video evidence kaydı ve milestone bağı Slice 12'de kurulmalı | Orta; AI sonucu advisory kaldığı sürece sınırlı | Slice 12'nin ardılı |
| Slice 14 — Dispute and Settlement | Fulfillment/dispute/settlement eksenleri ADR-003'te isimlendirilmiş | Casework yetkisi, dispute hold/finality ve release/refund hukuki-provider kararları | Çok yüksek; FORBIDDEN ve ADR-010 §2.7 sınırı | Dispute foundation içerikte kalabilir; provider-bağımlı settlement sonra |

### Sonraya ayrılan deployment ve dış entegrasyon kapsamı

| Aday | Neden ayrıldı | Yeniden açılma kapısı |
| --- | --- | --- |
| Slice 7 — Railway staging | 21 Temmuz 2026'da tamamlandı; C2/G4b kabul edildi | `done/07-staging-deployment.md` ve `docs/agent/slice-07-acceptance-2026-07-21.md` |
| Slice 11B — Gerçek provider | Domain adapter işi içerse de staging, dış provider test ortamı, credential, 3DS ve operasyonel kabul gerektirir | Slice 7/staging ve provider-hukuk kapıları ayrı çalışma olarak açıldığında |

Bu sequencing kararı hazırlandığında iki aday da ertelenmişti. Slice 7 daha
sonra ayrı deployment hattında tamamlanıp kabul edildi; Slice 11B ertelenmiş
durumunu korur.

## 3. Önerilen yürütme sırası

### Şimdi planlanacak içerik hattı

1. **Slice 12 — Fulfillment and Evidence**
2. **Slice 13 — Video Analysis**
3. **Slice 14A — Dispute and Casework Foundation** — yalnız insan birleşik
   Slice 14'ü bölmeyi onaylarsa; payment release/settlement mutation'ı içermez

Slice 12'nin ilk seçilme nedeni, `FUNDED` state'inin bugün kullanıcıya yalnız bir
lifecycle aşaması sunmasıdır. Fulfillment/evidence bu state'in doğal ardılıdır,
mevcut storage desenini tekrar kullanır ve Video Analysis'in bağlanacağı
first-class evidence kaydını üretir. Gerçek provider seçimini beklemez.

Slice 13 ikinci olmalıdır: mevcut video event contract'ları ve messaging
omurgası teknik başlangıç maliyetini düşürür, fakat video job'unun subject'i ve
business bağlamı Slice 12'deki immutable evidence object version'ına
bağlanmadan başlanırsa geçici bir video aggregate'i üretilir.

Birleşik Dispute and Settlement slice'ının **14A/14B olarak bölünmesi önerilir**.
Dispute/casework, provider'dan bağımsız bir hold ve insan kararı temeli olarak
ilerleyebilir. Release, payout, refund ve settlement ise ADR-010 §2.7 gereği
fail-closed kalmalı ve gerçek provider/hukuk kapısından önce açılamamalıdır.

### Sonraya bırakılan hat

- **Slice 7 Railway staging** bu içerik planlamasının dışında ayrı hatta
  tamamlanmıştır. **Slice 11B gerçek provider** sıradaki açık entegrasyon
  hattıdır.
- **Slice 14B — Settlement and Release** deployment işi değildir; ancak
  ertelenen Slice 11B ile hukuk/operasyon kararlarına bağımlı olduğu için aktif
  içerik batch'ine alınmaz.
- Bu işler yeniden açıldığında önerilen bağımlılık sırası
  `Slice 7 → Slice 11B → Slice 14B` olur.

Aktif ve ertelenmiş kapsamın sınırı:

```text
Şimdi:   Slice 12 → Slice 13 → (önerilen) Slice 14A

Sonra:   Slice 7 → Slice 11B → Slice 14B
```

İnsan Slice 14'ün bölünmesini kabul etmezse önerilen lineer sıra:

1. Slice 12
2. Slice 13
3. Deployment/provider hattı daha sonra yeniden açılır
4. Slice 7 ve Slice 11B tamamlandıktan sonra birleşik Slice 14

## 4. Eskalasyon listesi

### Slice 12 için kapanması gereken yeni kararlar

- **Yeni ADR önerisi — ADR-011, Fulfillment and Evidence V1:** seller
  `ADMIN`/`MEMBER` başlatma ve evidence submission; buyer `ADMIN` manual
  accept/reject; diğer participant'ların read-only olması.
- V1'in Deal başına tek fulfillment ve tek primary milestone ile başlaması;
  milestone'ın immutable current ratification package'a bağlanması.
- AI'dan çıkarılan `deliveryRequirements` değerlerinin Slice 9'da
  RuleSetVersion/ratification onayına taşınmadığı için otomatik contractual
  checklist veya completion kuralı yapılmaması.
- Exact `FulfillmentStatus` geçişleri ve buyer acceptance'ın yalnız fulfillment
  completion üretmesi; Deal `COMPLETED`, settlement veya payment release
  üretmemesi.
- Evidence media/type sınırı, retention ve rejected history politikası.
- Bu kararları uygulayan additive Core API OpenAPI yüzeyi, validator exact
  beklentileri, `contracts/README.md` ve `contracts/CHANGELOG.md` tek review
  birimi olarak insan onayına tabidir.
- AI JSON Schema/fixture, AsyncAPI ve AI-internal OpenAPI değişikliği Slice 12
  kapsamı dışıdır. İhtiyaç doğarsa ayrı contract eskalasyonu gerekir.

### Slice 13 için kapanması gereken kararlar

- Video Analysis'in yalnız Slice 12'deki immutable video evidence version'ını
  subject kabul etmesi ve advisory sonuçtan otomatik completion/dispute/release
  üretmemesi.
- Mevcut video schema'larının yeterlilik incelemesi. Her schema/fixture veya
  AsyncAPI değişikliği implementasyondan önce ayrı contract onayı gerektirir.
- Düşük confidence/anomaly sonucunun hangi Spring-owned review kaydını açacağı;
  yalnız UI uyarısı mı yoksa casework'e giden durable review item mı olduğu.

### Slice 11B ve Settlement için kapanması gereken kararlar

- Moka test credential'ı ve tam pool probe kanıtı: charge, status, approve,
  undo, void/refund, timeout ve duplicate `OtherTrxCode`.
- Pool'da bekleyen fonun provider tarafından nasıl gözlemlendiği, maksimum
  bekleme süresi ve automatic expiry davranışı.
- 3DS redirect hash doğrulaması; redirect'in yalnız UX sinyali kalması ve
  server-side query ile doğrulanmayan sonucun authoritative olmaması.
- Marketplace/sub-dealer modeli, KYC/onboarding, fee/split ve Law 6493 dahil
  açık hukuk/operasyon onayı. Platform-held/manual payout varsayılanı yoktur.
- Pre-approval cancel/refund, post-approval undo/refund ve settlement cutoff
  semantiği. Bilinmeyen durumda approve-then-refund yasaktır.
- Release eligibility, dispute window, hold/resume, reconciliation, ledger ve
  manual intervention authority için yeni ADR.
- ACTIVE mutual cancellation ile dispute resolution cancellation arasındaki
  permission, finality ve audit modeli.
- Deal `COMPLETE` mutation'ını hangi plan açarsa, payment initiate/reconcile
  akışlarının bugün kilitsiz olan Deal-status okumasını aynı Deal lock sırasına
  almalı ve completion ile yeni payment/reconcile yarışını test etmelidir.

## 5. Kabul edilen insan kararları

20 Temmuz 2026 tarihinde kabul edilen kararlar:

1. İlk içerik slice'ı olarak Slice 12'nin seçilmesi
2. Slice 7 ve Slice 11B'nin aktif içerik hattından çıkarılıp sonraya bırakılması
3. Slice 13'ün Slice 12 sonrasına alınması
4. Birleşik Slice 14'ün 14A Dispute/Casework ve 14B Settlement/Release olarak
   bölünmesi
5. Slice 14B'nin, ertelenen provider/deployment hattı yeniden açılana kadar
   planlanmaması
