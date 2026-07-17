# Slice 6 — Document Upload

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 5 (yol haritasında 06)
- Öncül: 03-deal-creation-and-listing; 04–05'ten teknik olarak bağımsız,
  sırada onlardan sonra
- Ardıl: AI Document Extraction (yol haritası 08) — extraction bu slice'ın
  doküman metadata + hash zeminine dayanır

## 1. Amaç ve kullanıcı sonucu

Kullanıcı gerçek tarayıcıda: deal detayında sözleşme dosyasını (PDF/DOCX)
seçer → dosya browser'dan DOĞRUDAN object storage'a yüklenir (Spring binary
içeriği proxy'lemez) → yükleme finalize edilir ve doküman metadata'sı
(SHA-256, boyut, tür) kaydolur → deal detayında current doküman görünür →
yeni bir versiyon yüklendiğinde önceki doküman SUPERSEDED olarak işaretlenir
ve geçmişte görünmeye devam eder.

Bu slice, AI extraction'ın (Slice 8) tüketeceği doküman zeminini ve
`integration` modülündeki ilk gerçek adapter'ı (object storage) kurar.

## 2. Kapsam / kapsam dışı

Kapsam:

- `document` modülü (ADR-003 §4.4): Document aggregate — metadata, hash,
  upload durumu, versiyon/supersede zinciri
- `integration` modülünde S3-compatible object storage adapter'ı + port
  (ADR-003 §4.11; local hedef compose'daki MinIO)
- Presigned upload akışı: intent → browser PUT → finalize (ADR-006 §49–50,
  ADR-001 §6)
- Finalize'da **Idempotency-Key** (ADR-006 §24 aday listesinde; Slice 4'te
  kurulan mekanizma yeniden kullanılır)
- `deal.current_document_id` pointer'ı ve supersede davranışı (ADR-003 §7,
  §7.2 — generic delete yok)
- Local MinIO bucket bootstrap'ı ve browser PUT için CORS konfigürasyonu
- Frontend: doküman kartı — yükleme, progress, current + geçmiş

Kapsam dışı:

- AI extraction job'ı ve RabbitMQ (Slice 8)
- Doküman silme (yasak — supersede var), indirme yetki matrisinin
  incelmesi (basit participant-download bu slice'ta yeter)
- Virus/malware tarama
- Staging object storage kararı (insan kararıyla ertelendi — Slice 6
  staging'e taşınırken verilecek; kabul LOCAL MinIO ile yapılır)
- Video/evidence upload'ları (yol haritası 13) — akış desenini bu slice
  kurar ama kapsamına almaz

## 3. Okunacak ADR bölümleri

- ADR-001 §6 (dosya/storage sahipliği: Spring metadata sahibi, object key
  üretir, hash saklar, süreli erişim üretir), §16 (presigned URL süreleri)
- ADR-003 §4.4 (document modülü), §4.11 (integration adapter kuralları),
  §7–7.2 (current pointer, supersede, generic delete yasağı), §24 (external
  çağrı sırasında transaction açık tutulmaz — presigned üretimi ve storage
  kontrolü buna tabidir)
- ADR-006 §24–25 (idempotency), §35 (202 değil — upload intent senkron
  metadata işlemidir; asenkron job DEĞİLDİR), §49–50 (content type, presigned
  akış, Spring'e giden alanlar)
- ADR-007 §14 (object storage yetenek beklentileri — local MinIO'da da
  private bucket ilkesi)
- ADR-002 §7.1 (indirilen içeriğin SHA-256 doğrulamasını FastAPI'nin de
  yapacağı — bu slice'taki hash disiplinin gerekçesi)

## 4. Public API yüzeyi

Yüzey implementasyondan ÖNCE `core-api-v1.yaml`'a tasarlanır. Taslak:

- `POST /api/v1/deals/{dealId}/documents` — upload intent: fileName,
  mediaType, sizeBytes, beklenen sha256 alır; 201 + doküman kaydı
  (PENDING_UPLOAD) + kısa ömürlü presigned PUT URL döner
- `POST /api/v1/documents/{documentId}/finalize` — Idempotency-Key zorunlu;
  yükleme tamamlandı bildirimi; başarıda doküman AVAILABLE olur, önceki
  current SUPERSEDED işaretlenir, `deal.currentDocumentId` güncellenir
- `GET /api/v1/deals/{dealId}/documents` — versiyon geçmişi (stabil liste
  DTO'su; pagination bu boyutta zorunlu değil)
- Current doküman bilgisi `DealDetail`'e additive alanla girer (OpenAPI
  fazında netleşir)
- İndirme: kısa ömürlü presigned GET URL üreten bir endpoint (örn.
  `POST /documents/{id}/download-link`); binary Spring'den geçmez

Sabitlenen contract kararları:

- İzinli media type başlangıçta **PDF + DOCX** (ADR-002 extraction
  pipeline'ıyla uyumlu — karar); aksi 422
- `sha256` intent'te zorunludur ve hex biçimi şemada kısıtlanır
- Terminal durumdaki deal'e (CANCELLED/ARCHIVED) upload intent → 409
- Finalize'da aynı Idempotency-Key + aynı request → ilk sonuç; farklı
  request → 409 `IDEMPOTENCY_KEY_REUSED`
- Presigned URL response alanları download-reference desenini izler
  (url + expiresAt; contracts/common şemasındaki kavramla tutarlı adlandırma)

## 5. Backend yönlendirmesi

- **Document aggregate:** dealId, tenant bağlamı, object key (Spring üretir —
  tahmin edilemez, deal/doküman kimliği taşıyan bir düzen; implementer
  belirler), fileName (sanitize edilerek saklanır), mediaType, sizeBytes,
  sha256, durum, versiyon zinciri (önceki dokümana referans veya sıra
  numarası — implementer seçer), audit alanları, optimistic lock.
- **Durum modeli (öneri):** PENDING_UPLOAD → AVAILABLE → SUPERSEDED.
  Finalize edilmemiş PENDING_UPLOAD kayıtları current olamaz ve listede
  "tamamlanmamış" olarak ayrışır; terk edilmiş PENDING'lerin temizlik/expiry
  politikası açık sorudur (başlangıçta zorunlu değil).
- **Storage adapter:** `integration` modülünde port arkasında (ADR-003 §23 —
  document modülü SDK'yı görmez). Presigned PUT/GET üretimi, object varlık ve
  boyut sorgusu. Bucket private; public listing kapalı (ADR-007 §14 ilkesi
  local'de de). Presigned süreleri kısa ve config'ten (ADR-001 §16,
  ADR-007 §18).
- **Finalize doğrulama derinliği (planner kararı):** minimum — object'in
  storage'da var olduğu ve boyutunun bildirilenle eştiği doğrulanır; beyan
  edilen sha256 metadata olarak kaydedilir. Tam içerik hash'inin server-side
  yeniden hesabı maliyet/fayda kararıdır — planner boyut sınırına göre karar
  verir; her durumda FastAPI indirme sonrası hash'i ayrıca doğrular
  (ADR-002 §7.1) ve uyuşmazlık orada terminal hatadır. Bu ikili savunma plana
  not düşülür.
- **Transaction sınırı (ADR-003 §24):** storage çağrıları (presigned üretim,
  varlık kontrolü) DB transaction'ı DIŞINDA yapılır; finalize'ın DB kısmı
  (doküman AVAILABLE + önceki SUPERSEDED + deal pointer + audit) tek
  transaction'dır.
- **Yetki:** upload intent/finalize participant yetkisi ister ve
  `OperationContext`'ten geçer (yeni `RequestedOperation` değerleri). Upload
  hakkının taraf rolüne (buyer/seller) daraltılması bu slice'ta yapılmaz —
  açık soruya bakınız.
- Migration: documents tablosu + `deal.current_document_id` (nullable, FK).
- Local geliştirme: compose MinIO'suna bucket bootstrap (dev-seed/başlangıç
  provisioning — implementer seçer) ve browser PUT için CORS izinleri;
  dokümantasyona (DEVELOPMENT.md) kurulum notu.

## 6. Frontend yönlendirmesi

- Deal detayında doküman kartı: dosya seçici (accept=PDF/DOCX), client-side
  sha256 hesabı (Web Crypto) → intent → PUT (progress göstergesi) → finalize.
  Adımlardan herhangi biri başarısızsa anlaşılır hata + yeniden deneme;
  yarım kalan yükleme current'ı ETKİLEMEZ.
- Current doküman + geçmiş listesi (SUPERSEDED işaretli); indirme linki
  kısa ömürlü URL ile.
- Finalize çağrısı Idempotency-Key üretir; yeniden denemede aynı key.
- Yetkisiz/terminal-durum durumlarında kart salt-okunur (availableActions
  benzeri sunucu sinyaliyle; frontend türetmez).
- Tipler committed OpenAPI'den.

## 7. Kabul testi (tarayıcı akışı)

1. Participant kullanıcı deal detayında PDF seçer → progress → başarı;
   current doküman adı/boyutu/zamanı görünür
2. Sayfa yenilenir → doküman bilgisi kalıcı
3. İkinci bir dosya yüklenir → yeni doküman current olur; ilki geçmişte
   SUPERSEDED görünür
4. İzinsiz tür (örn. PNG) seçilir → anlamlı hata, kayıt oluşmaz
5. İndirme linki tarayıcıda dosyayı indirir; süresi dolmuş link reddedilir
   (geliştirici araçlarıyla gözlenebilir)
6. İkinci browser'daki diğer participant dokümanı görür ve indirebilir;
   participant OLMAYAN kullanıcı doküman yüzeyine erişemez
7. CANCELLED deal'de yükleme kapalıdır; zorlanırsa 409
8. Finalize çift tıklaması ikinci kayıt/ikinci supersede ÜRETMEZ

## 8. Minimum invariant testleri

- Finalize edilmemiş (PENDING_UPLOAD) doküman current olamaz
- Supersede zinciri: yeni AVAILABLE doküman öncekini SUPERSEDED yapar;
  `deal.currentDocumentId` her an en fazla bir AVAILABLE dokümanı gösterir
- Boyut uyuşmazlığında finalize reddedilir
- İzinli olmayan media type intent'te 422
- Participant olmayan entity doküman metadata'sına/linklerine erişemez (404)
- Finalize idempotency: aynı key aynı request → tek etki; farklı → 409
- Finalize DB işlemleri + audit aynı transaction'da (rollback testi)
- Terminal deal'e intent → 409

## 9. Açık sorular / karar noktaları

- Max dosya boyutu (öneri: 50MB başlangıç; config'ten)
- Upload yetkisi: tüm participant'lar mı, yalnız taraflar mı? (öneri:
  başlangıçta tüm participant'lar; ratification öncesi daraltma ayrıca
  değerlendirilir)
- Terk edilmiş PENDING_UPLOAD kayıtlarının temizliği (öneri: başlangıçta
  yalnız görmezden gel; zamanlanmış temizlik ihtiyacı doğarsa ayrı iş)
- Server-side tam hash doğrulaması yapılacak mı (bkz. §5 finalize derinliği)
- Client-side sha256 hesabının büyük dosyalarda UX'i (streaming hash) —
  implementer değerlendirir

## 10. Done tanımı

- [ ] OpenAPI yüzeyi implementasyondan önce tasarlandı (intent/finalize/
      list/download-link, idempotency işaretleri, hata kodları)
- [ ] Migration uygulandı (documents + current pointer)
- [ ] Storage adapter port arkasında; presigned PUT/GET local MinIO ile
      çalışıyor; bucket bootstrap + CORS belgelendi
- [ ] Intent → browser PUT → finalize zinciri gerçek tarayıcıda çalışıyor;
      binary Spring'den geçmiyor
- [ ] Supersede ve current pointer davranışı doğru; geçmiş görünür
- [ ] §8 invariant testleri geçiyor; finalize idempotent; audit aynı
      transaction'da
- [ ] §7 akışı iki browser'la baştan sona çalıştırıldı
- [ ] Contract validator + `mvn verify` + frontend typecheck/build yeşil
