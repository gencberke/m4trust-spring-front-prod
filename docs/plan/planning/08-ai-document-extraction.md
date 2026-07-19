# Slice 8 — AI Document Extraction (Mock AI Worker ile)

- Durum: planning
- Slice sırası: ADR-004 §24 → "AI Document Extraction with Mock AI Worker"
  (bölünmüş yol haritasında 08)
- Öncül: 06-document-upload
- Ardıl: 09-manual-review-and-ruleset

## 1. Amaç ve kullanıcı sonucu

Initiator, Deal'in current document'i için gerçek tarayıcıdan analiz talep eder
→ job asenkron olarak RabbitMQ üzerinden Mock AI Worker'a gider → Deal detayında
AnalysisStatus ilerleyişi görünür (QUEUED → PROCESSING → REVIEW_REQUIRED /
FAILED) → tamamlanan extraction sonucu (parties, rules — advisory `legalBasis`
dahil —, delivery requirements, summary) okunur biçimde görüntülenir.

Completed event teknik başarıdır; business kabul DEĞİLDİR (ADR-002 §11,
ADR-003 §17). Bu slice sonucu yalnız REVIEW_REQUIRED durumuna taşır; ACCEPTED
ve RuleSetVersion Slice 9'un işidir. Diğer participant'lar analiz durumunu ve
sonucu okur; analiz talebi yalnız initiator'a açıktır.

Bu slice projenin asenkron omurgasını ilk kez gerçek kılar: outbox, RabbitMQ
topolojisi, consumer inbox ve Mock AI Worker. Sonraki tüm AI slice'ları
(video dahil) bu mekanizmaları aynen kullanır.

## 2. Kapsam / kapsam dışı

Kapsam:

- `contractintelligence` modülü (ADR-003 §4.5): AI job business kaydı,
  ExtractionResultVersion, canonical sonucun Spring-side kopyası
- AnalysisStatus alt kümesi: NOT_REQUESTED → QUEUED → PROCESSING →
  REVIEW_REQUIRED / FAILED; yeni doküman versiyonu → SUPERSEDED (ADR-003 §10)
- Transactional outbox (yeni migration) + tx-dışı publisher relay (ADR-003 §24)
- Consumer inbox idempotency tablosu ve duplicate-safe tüketim (ADR-002 §17.2)
- spring-amqp bağımlılığı; AsyncAPI'deki topolojinin local bootstrap'i
  (`m4trust.ai.commands`/`events`/`dead-letter` exchange'leri,
  `m4trust.ai.document-extraction.v1` ve `m4trust.core.ai-results.v1` queue'ları)
- ADR-003 §18.2 contract validation ve minimal §18.3 business-acceptance
  (sonuç her zaman REVIEW_REQUIRED'a düşer; otomatik ACCEPTED yolu yok)
- `tools/mock-ai-worker/` gerçek implementasyonu
- DealLifecycleProjection'a CONTRACT_ANALYSIS / MANUAL_REVIEW girdileri
- Analysis request action + sonuç/durum read yüzeyleri; audit aynı transaction

Kapsam dışı:

- Manual review, düzeltme, RuleSetVersion, ACCEPTED geçişi → Slice 9
- Video analysis; job cancel akışı (contract'ı mevcut, tetikleyicisi yok)
- Gerçek FastAPI servisi; AI'ın staging'e eklenmesi
- FAILED sonrası otomatik yeni job (yeniden talep kullanıcı action'ıdır;
  ADR-002 §18.2 gereği yeni jobId + yeni idempotencyKey ile)
- Extraction sonucunun Deal alanlarına herhangi bir yazımı (FORBIDDEN)

## 3. Okunacak ADR bölümleri

- ADR-002 §11–§13, §15, §17–§18, §25, §29 (event semantiği, idempotency, retry)
- ADR-003 §4.5, §4.11, §10, §16–§18, §23–§25
- ADR-001 §6–§7 (async sınır, presigned erişim), §20
- ADR-004 §11–§14 (Mock AI Worker kuralları, messaging sınırı atlanamaz)
- ADR-006 §4, §18–§19, §33, §39 (action endpoint, uzun işlem HTTP'de bekletilmez)

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Analysis request action endpoint'i (Deal'in current document'i için;
  `Idempotency-Key` önerilir — duplicate job üretimi maliyetli)
- Analysis durum/sonuç read projection'ı: status, talep/tamamlanma zamanları,
  sonuç içeriği (rules[] `legalBasis` dahil), FAILED'de stable hata özeti
- Deal detail'e analysis özeti + actor-aware `canRequestAnalysis` action'ı

Sabit davranışlar:

- Current document yokken veya AVAILABLE değilken talep → 409
- Terminal Deal'de talep → 409 `DEAL_STATE_CONFLICT`
- Aktif (QUEUED/PROCESSING) job varken ikinci talep → 409
- FAILED teknik hata detayı raw provider mesajı içermez (ADR-002 §12.3);
  frontend `code` ile dallanır
- Read yüzeyleri tüm participant'lara açıktır; request yalnız initiator

## 5. Backend yönlendirmesi

### Messaging altyapısı (integration modülü, ADR-003 §4.11)

- **Outbox:** yeni migration; business mutation (job kaydı + AnalysisStatus)
  ile outbox satırı AYNI transaction'da yazılır. Relay ayrı bir scheduler/poller
  olarak tx dışında publish eder; publish onayı sonrası outbox satırı
  işaretlenir. Broker'a publish sırasında DB transaction'ı açık tutulmaz.
- **Inbox:** `eventId` unique kaydıyla duplicate event tek kez uygulanır;
  ikinci teslim sessizce consumed sayılır (ADR-002 §17.2).
- **Topoloji:** AsyncAPI'deki exchange/queue/binding adları birebir kullanılır;
  local profilde integration modülü declare eder (öneri). Production declare
  stratejisi staging-AI işinde kesinleşir. Dead-letter bağlanır; zehirli mesaj
  sınırlı redelivery sonrası DLQ'ya düşer ve loglanır.
- **Requested event:** envelope alanları (jobId, jobType, tenantId,
  transactionId=dealId, subjectId=documentId, idempotencyKey) + payload'da
  immutable object version'a pinli, süreli presigned download-reference
  (ADR-002 §7.1). Presigned URL üretimi tx dışında, outbox yazımından önce
  yapılamayacağı için download-reference'ın outbox relay ANINDA üretilmesi
  önerilir (TTL, kuyruk beklemesini tolere edecek şekilde config'ten).

### contractintelligence modülü

- AI job kaydı: jobId, dealId, documentId + object version, input sha256,
  durum, zaman damgaları, optimistic `version`. Document/Deal verisine yalnız
  port üzerinden erişir (ADR-003 §23).
- Consumer akışı (ADR-003 §17 sırası): inbox → §18.2 contract validation
  (şema, exact schemaVersion, jobType, envelope tutarlılığı) → job + input-hash
  eşleşmesi → job lifecycle kontrolü (geç/superseded/cancelled sonuç
  uygulanmaz, audit'e kaydedilir) → ExtractionResultVersion olarak canonical
  sonucun saklanması → REVIEW_REQUIRED. Contract-invalid payload integration
  hatasıdır, business rejection ÜRETMEZ.
- `legalBasis` advisory olarak saklanır ve projection'da geçer; hiçbir Spring
  kuralı ona göre karar vermez.
- Failed event: FAILED durumu + stable hata kodu; yeniden talep initiator
  action'ı olarak yeni job üretir (ADR-002 §18.2).
- Yeni doküman finalize'ı önceki analiz zincirini SUPERSEDED yapar; bu bağ
  document modülüne dar bir port/event ile kurulur, repository paylaşımı yok.

### Mock AI Worker (`tools/mock-ai-worker/`)

- Contract fixture'larından türetilmiş completed/failed event'leri üretir;
  ürettiği her mesaj `validate_contracts.py` şemalarına uygundur.
- Senaryo seçimi production contract'ına alan EKLEMEDEN yapılır (ADR-004 §13):
  öneri — doküman dosya adı deseni (örn. `fail-retryable*.pdf`) ve/veya worker
  config'i. Varsayılan senaryo başarılı sonuçtur; en az bir kural `legalBasis`
  içerir.
- Requested event'teki presigned reference ile objeyi GERÇEKTEN indirir
  (E2E'nin storage bacağını da doğrular), içeriği yorumlamaz.
- Yalnız local compose RabbitMQ'suna bağlanır; production'da asla çalışmaz
  (ADR-007 §10). Teknoloji seçimi implementer'a bırakılmıştır (öneri: bağımsız
  küçük Python worker — FastAPI tarafıyla dil sürekliliği).

## 6. Frontend yönlendirmesi

- Deal detail'e "Contract analysis" bölümü: durum rozeti, talep butonu
  (yalnız backend `canRequestAnalysis` true iken), FAILED'de hata + yeniden
  talep.
- Durum tazelemesi polling/refetch ile (ADR-005 §9; websocket kapsam dışı).
- Sonuç görünümü: parties, rules (kategori, değer, confidence, kaynak sayfa,
  varsa `legalBasis` rozeti — salt bilgi, "yasal onay" gibi sunulmaz),
  delivery requirements, summary/reviewReasons.
- "Sonuç geldi" ile "kabul edildi" ayrımı görsel olarak açık: REVIEW_REQUIRED
  "inceleme bekliyor" dilinde sunulur.
- Tipler committed OpenAPI'den; hata dallanması `code` ile.

## 7. Kabul testi (tarayıcı akışı)

Compose (RabbitMQ dahil) + Mock AI Worker ayakta:

1. Initiator doküman yükler, analiz talep eder → durum QUEUED/PROCESSING
   olarak görünür.
2. Mock worker sonucu üretir → durum REVIEW_REQUIRED; rules listesi ve en az
   bir `legalBasis` rozeti ekranda.
3. İkinci browser'daki participant durumu ve sonucu görür; talep butonu görmez,
   zorlanan istek reddedilir.
4. Failure senaryolu dosya ile talep → FAILED + anlamlı hata; yeniden talep
   yeni job olarak çalışır ve başarılı sonuçlanır.
5. Aktif job varken ikinci talep 409 üretir.
6. Yeni doküman versiyonu yüklenir → önceki analiz SUPERSEDED; yeni talep yeni
   dokümana karşı çalışır.
7. Worker kapalıyken talep edilir → durum QUEUED kalır; worker açılınca sonuç
   gelir (asenkronluk kanıtı). Duplicate teslim (worker'ı aynı event'i iki kez
   publish edecek senaryoyla) tek sonuç üretir.
8. Slice 6 upload akışı regresyonsuz çalışır.

## 8. Minimum invariant testleri

- Outbox atomicity: business mutation rollback → outbox satırı da yok
- Inbox idempotency: aynı eventId iki kez → tek uygulama
- Superseded/geç sonuç uygulanmaz, audit kaydı düşer
- Contract-invalid payload → integration hatası; AnalysisStatus business
  rejection'a düşmez
- AnalysisStatus geçiş kuralları (FAILED→PROCESSING gibi yasak geçişler)
- Non-initiator request reddi; nonparticipant read reddi

Broker'ın kendisini test eden geniş matris kurulmaz; messaging E2E'si §7
akışıyla doğrulanır (ADR-004 §14 — Spring içinde fake sonuç üretmek ana E2E
kabulü sayılmaz).

## 9. Açık sorular / karar noktaları

- Analiz talebi explicit action mı, finalize'a otomatik mi? (öneri: explicit
  action — yetki ve maliyet kontrolü net kalır; otomatik tetik ileride additive
  eklenebilir)
- Outbox relay mekanizması: basit polling scheduler mı, Postgres LISTEN/NOTIFY
  mi? (öneri: polling — taşınabilir ve yeterli)
- Presigned download-reference TTL değeri (config'ten; kuyruk beklemesi payı
  ile)
- Request endpoint'inde `Idempotency-Key` zorunlu mu önerilir mi? (öneri:
  zorunlu — mevcut idempotency altyapısı hazır)
- Mock worker dili/paketlemesi (öneri: Python + compose servisi)

## 10. Done tanımı

- [ ] OpenAPI analysis yüzeyi implementasyondan önce tasarlandı
- [ ] Outbox + inbox migration'ları ve mekanizmaları kuruldu; atomicity testli
- [ ] RabbitMQ topolojisi AsyncAPI ile birebir; DLQ bağlı
- [ ] contractintelligence modülü §18.2 validation ve REVIEW_REQUIRED akışıyla
      çalışıyor; ExtractionResultVersion saklanıyor
- [ ] Mock AI Worker contract-valid event üretiyor; senaryolar production
      contract'ını kirletmiyor
- [ ] Lifecycle projection CONTRACT_ANALYSIS/MANUAL_REVIEW üretiyor; frontend
      hesaplamıyor
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] §7 gerçek tarayıcı + gerçek broker + mock worker akışı tamamlandı
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
