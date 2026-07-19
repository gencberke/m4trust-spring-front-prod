# Slice 11 — Funding ve Payment (Buyer Funding)

- Durum: planning (insan talimatıyla; bkz. eskalasyon önkoşulu)
- Slice sırası: ADR-004 §24 → "Funding and Payment" (bölünmüş yol haritasında 11)
- Öncül: 10-ratification (funding yalnız ACTIVE Deal'de anlam taşır)
- Ardıl: fulfillment/evidence slice'ı; payment release/settlement bu planın
  kapsamı DIŞINDADIR
- **Eskalasyon önkoşulu (bağlayıcı):** ADR-003 §21 exact payment workflow'un
  ayrı bir ADR/payment design dokümanında detaylandırılmasını şart koşar ve bu
  slice ADR-INDEX Katman 3 kapsamındadır (payment/funding mutation'ı).
  Implementasyona başlamadan önce: (a) payment design karar notu/ADR'si insan
  onayıyla kabul edilir, (b) staging/production provider seçimi karara
  bağlanır. Bu iki karar kapanmadan yalnız OpenAPI/port tasarımına kadar
  ilerlenebilir.

## 1. Amaç ve kullanıcı sonucu

ACTIVE (ratified) bir Deal'de buyer tarafı gerçek tarayıcıdan funding sürecini
başlatır → Deal'in funding planı (bir veya birden fazla funding unit) görünür →
buyer bir funding unit için ödeme başlatır → provider (local'de sandbox/mock)
sonucu döner → FundingStatus ilerleyişi Deal detayında izlenir:
NOT_CONFIGURED → PLANNED → PENDING → (PARTIALLY_FUNDED) → FUNDED. Başarısız
ödeme FAILED unit durumu üretir ve yeniden denenebilir; provider timeout'u
asla otomatik failure sayılmaz (ADR-003 §21).

Seller ve diğer participant'lar funding durumunu okur; para hareketi başlatma
yalnız buyer tarafına aittir. Bu slice'ta para YALNIZ platforma girer; release,
payout, refund ve settlement sonraki slice'ların işidir. Development ortamında
gerçek para hareketi yoktur (ADR-004 §19).

## 2. Kapsam / kapsam dışı

Kapsam:

- `payment` modülü (ADR-003 §4.7): FundingPlan/FundingUnit aggregate'leri,
  payment operation kayıtları
- FundingStatus ekseni (ADR-003 §12) ve DealLifecycleProjection'a FUNDING
  girdisi
- Funding planının oluşturulması — ratified package içeriğindeki tutara bağlı
  (tek unit varsayılan; çoklu unit desteği veri modelinde)
- Payment provider PORT'u + local sandbox/mock adapter (integration modülü,
  ADR-003 §4.11); gerçek provider adapter'ı ayrı iş
- Provider idempotency key zorunluluğu; duplicate isteğin duplicate para
  hareketi üretmemesi (ADR-003 §21)
- Timeout/bilinmeyen sonuç → reconciliation-gerekli durumu ve manuel/basit
  yeniden-sorgulama action'ı
- Public API funding yüzeyi + `Idempotency-Key` zorunlu payment action'ları
- Audit aynı transaction'da; provider çağrıları DB transaction'ı DIŞINDA

Kapsam dışı:

- Payment release, payout, settlement (SettlementStatus akışı) → sonraki
  slice'lar
- Refund/reversal (açık business operation olarak ayrıca modellenecek)
- Fulfillment/evidence; dispute etkileri
- Gerçek provider entegrasyonunun staging/production kurulumu (provider
  kararı sonrası ayrı operasyonel iş)
- Webhook imza doğrulama altyapısının production sertleştirmesi (tasarımı §5'te
  yer alır; gerçek provider işiyle birlikte kabul edilir)
- Faiz/komisyon/ücret hesaplamaları

## 3. Okunacak ADR bölümleri

- ADR-003 §4.7, §4.11, §12, §16, §21, §23–§25
- ADR-001 §2.2, §20 (FastAPI'nin payment'a dokunamaması; sınırlar)
- ADR-006 §24–§25 (Idempotency-Key), §18–§19, §33, §39
- ADR-004 §19 (dev'de gerçek para yasağı), §22–§23
- ADR-007 §19–§20 (provider secret yönetimi)
- FORBIDDEN §1–§2 payment satırları

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Funding planı oluşturma/görüntüleme (plan tutarı ratified package'tan;
  elle serbest tutar girilmez)
- Funding unit için ödeme başlatma action'ı (`Idempotency-Key` ZORUNLU;
  `expectedVersion` ile)
- Ödeme sonucu/durum read projection'ı; reconciliation-gerekli durumun açık
  gösterimi
- Provider callback/confirmation yüzeyi (mock provider'ın sonucu ilettiği
  internal endpoint veya polling — §9 kararına göre)
- Deal detail'e funding özeti + actor-aware action'lar (`canInitiateFunding`
  vb.)

Sabit davranışlar:

- ACTIVE olmayan Deal'de funding action'ları → 409 `DEAL_STATE_CONFLICT`
- Buyer olmayan entity'nin ödeme başlatması → reddedilir (403/404 politikası
  OpenAPI'de netleşir)
- Aynı Idempotency-Key + aynı request → tek etki; farklı request → 409
- Tutarlar integer minor unit + ISO 4217 currency; float ASLA (ADR-006 §28)
- Timeout/bilinmeyen sonuç FAILED ÜRETMEZ; ayrı "UNCONFIRMED/reconciliation"
  gösterimi olur
- FUNDED'a ulaşmış unit üzerinde yeni ödeme başlatılamaz → 409

## 5. Backend yönlendirmesi

- **Aggregate'ler:** FundingPlan (Deal başına, ratified tutar + currency),
  FundingUnit (tutar, durum, sıra), PaymentOperation (unit başına girişimler:
  provider referansı, idempotency key, durum, ham olmayan sonuç özeti).
  Provider'a giden her istek önce PaymentOperation olarak persist edilir
  (intent kaydı) → provider çağrısı TX DIŞINDA yapılır → sonuç ayrı
  transaction'da uygulanır (ADR-003 §24, §21).
- **Durum makinesi:** FundingStatus geçişleri aggregate davranış metotlarında;
  PENDING'e geçiş ödeme başlatmayla, FUNDED'a geçiş yalnız provider'ın
  DOĞRULANMIŞ başarı sonucuyla olur. PARTIALLY_FUNDED çoklu unit'te ara
  durumdur. FAILED unit yeniden denemeye açıktır (yeni PaymentOperation +
  yeni provider idempotency key).
- **Idempotency iki katmanlıdır:** (1) HTTP katmanı mevcut `idempotency`
  modülüyle; (2) provider katmanında her PaymentOperation kendi provider
  idempotency key'ini taşır ve retry AYNI provider key ile gider — duplicate
  para hareketi imkânsızlaşır (ADR-003 §21).
- **Timeout/reconciliation:** timeout veya belirsiz cevapta operation
  UNCONFIRMED kalır; unit FAILED'a düşmez, yeni ödeme başlatılması engellenir
  (çifte tahsilat riski). Basit reconciliation action'ı provider'dan durumu
  yeniden sorgular. Otomatik scheduler bu slice'ta zorunlu değildir; tasarım
  buna açık bırakılır.
- **Provider port:** dar interface (initiate, queryStatus); adapter integration
  modülünde. Local sandbox/mock adapter deterministik senaryolar sunar
  (başarı, decline, timeout, geç onay) — senaryo seçimi production API'sine
  alan sızdırmadan (tutar deseni veya mock config'i ile; ADR-004 §13 ruhu).
  Provider secret'ları yalnız environment'tan (ADR-007 §19–§20).
- **Sınırlar:** FastAPI/AI hiçbir payment yüzeyine dokunmaz (FORBIDDEN §1);
  AI sonucu payment operation ÜRETEMEZ (ADR-003 §21). Payment modülü Deal'e
  dar port üzerinden bağlanır; FUNDED bilgisi Deal'e event/port ile yansır,
  repository paylaşımı yoktur (ADR-003 §23).
- **Para alanları:** amountMinor bigint + currency char(3); DB CHECK'leriyle
  negatif tutar engellenir. Query edilen para alanları JSONB'ye gömülmez
  (ADR-003 §27).

## 6. Frontend yönlendirmesi

- Deal detail'e "Funding" bölümü: plan tutarı, unit listesi, durum rozetleri,
  buyer için ödeme başlat butonu (yalnız backend projection'ı izin veriyorsa).
- Ödeme başlatma akışı sandbox provider'a yönlenir (local'de mock ekranı veya
  otomatik sonuç); sonuç dönüşünde durum tazelenir (polling/refetch).
- UNCONFIRMED durumu "başarısız" DEĞİL "doğrulanıyor" dilinde gösterilir;
  reconciliation action'ı kullanıcıya sunulur.
- Tutar gösterimi minor-unit → ondalık dönüşümüyle; giriş alanı yoktur (tutar
  package'tan gelir).
- Seller/participant salt-okunur funding görünümü alır.
- Çift tıklama koruması yalnız UI'da değildir; aynı Idempotency-Key retry
  deseni korunur (ADR-006 §25).

## 7. Kabul testi (tarayıcı akışı)

İki browser (buyer ADMIN + seller tarafı), local sandbox provider:

1. ACTIVE Deal'de funding planı görünür (tutar ratified package ile eşleşir);
   DRAFT/CANCELLED Deal'de funding yüzeyi kapalıdır.
2. Buyer ödeme başlatır → PENDING → sandbox başarı → FUNDED; her iki browser
   da durumu görür; lifecycle FUNDING→(fulfillment-öncesi) görünümüne ilerler.
3. Decline senaryosu → operation FAILED, unit yeniden denenebilir; ikinci
   deneme başarıyla FUNDED olur; tek para hareketi kaydı oluşur.
4. Timeout senaryosu → UNCONFIRMED; yeni ödeme başlatma engellenir;
   reconciliation action'ı geç onayı çeker ve FUNDED'a taşır.
5. Ödeme başlat butonuna çift tıklama / aynı Idempotency-Key retry tek
   operation üretir; farklı request aynı key ile 409 alır.
6. Seller ödeme başlatamaz; butonu görmez, zorlanan istek reddedilir.
7. FUNDED unit'te yeni ödeme denemesi 409 üretir.
8. Ratification ve önceki slice akışları regresyonsuz.

## 8. Minimum invariant testleri

- Provider çağrısı sırasında DB transaction'ının açık olmadığı
- Aynı provider idempotency key ile retry'ın duplicate operation/para hareketi
  üretmediği; HTTP idempotency katmanının farklı-request 409'u
- Timeout'un FAILED üretmediği; UNCONFIRMED'da yeni ödeme engeli
- FUNDED'a yalnız doğrulanmış provider sonucuyla geçilebildiği
- ACTIVE-olmayan Deal'de funding mutation reddi; buyer-olmayan reddi
- Tutarların integer persist edildiği; negatif tutar DB reddi
- Intent kaydı + sonuç uygulama transaction'larının audit ile atomikliği

Provider adapter'ının kendisini test eden geniş matris kurulmaz; sandbox
senaryoları §7 akışıyla doğrulanır.

## 9. Açık sorular / karar noktaları

- **Payment design ADR'si (önkoşul):** funding unit granülaritesi (tek ödeme
  mi, taksit/milestone mu), platform hesabı modeli (escrow benzeri tutma
  biçimi), UNCONFIRMED→otomatik reconciliation politikası ve refund modelinin
  iskeleti bu dokümanda karara bağlanır — insan onayı şart.
- **Provider seçimi (önkoşul):** staging/production için gerçek provider
  (örn. Stripe/iyzico/craftgate sınıfı) kararı; sandbox port tasarımı bu
  karardan bağımsız ilerleyebilir ama kabulden önce karar kapanmalıdır.
- Provider sonucunun platforma dönüş kanalı: webhook mu polling mi (öneri:
  port her ikisine açık; local mock'ta senkron sonuç + geç-onay senaryosu)
- Çoklu currency desteği: v1'de tek currency (package currency'si) önerilir
- PaymentOperation'da saklanacak provider yanıt özetinin alan seti (raw
  yanıt/PII saklanmaz — ADR-007 §33)

## 10. Done tanımı

- [ ] §9'daki payment design ADR'si ve provider kararı insan onayıyla kapandı
- [ ] OpenAPI funding yüzeyi implementasyondan önce tasarlandı
- [ ] FundingPlan/FundingUnit/PaymentOperation migration'ları para
      invariant'larını DB seviyesinde taşıyor
- [ ] Provider port + sandbox adapter çalışıyor; provider çağrıları TX dışında
- [ ] İki katmanlı idempotency (HTTP + provider key) testli
- [ ] Timeout/UNCONFIRMED/reconciliation akışı çalışıyor; timeout hiçbir yerde
      otomatik failure değil
- [ ] FundingStatus + lifecycle projection doğru; frontend hesaplamıyor
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] §7 iki-browser kabul akışı sandbox provider ile tamamlandı; önceki
      slice akışları regresyonsuz
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
