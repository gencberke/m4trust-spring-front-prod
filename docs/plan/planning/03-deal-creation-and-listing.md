# Slice 3 — Deal Creation ve Listing

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 3
- Öncül: 02-organization-and-membership
- Ardıl: Slice 4 (Deal Parties and Participants — bu planda kapsam dışı)

## 1. Amaç ve kullanıcı sonucu

Kullanıcı, aktif legal entity bağlamıyla gerçek tarayıcıda: yeni bir Deal (DRAFT) oluşturur → deal listesinde görür (pagination/filtre ile) → detayını açar → temel bilgilerini günceller → gerekirse iptal eder. Başka bir entity'nin kullanıcısı bu deal'i göremez.

Bu slice, projenin ana aggregate'ini (Deal) ve üç kalıcı deseni ilk kez gerçek business üzerinde kurar: **state machine + optimistic locking + aynı-transaction audit**. Sonraki tüm business slice'ları bu desenleri kopyalar.

## 2. Kapsam / kapsam dışı

Kapsam:

- Deal aggregate (ADR-003 §7'nin bu slice'a düşen alt kümesi)
- `DealStatus` state machine: DRAFT → ACTIVE → CANCELLED / COMPLETED → ARCHIVED; yasak geçişler engellenir (ADR-003 §9). Bu slice'ta kullanıcıya açılan geçişler: oluşturma (DRAFT) ve cancel; diğer geçişler domain'de tanımlı ama tetikleyicileri sonraki slice'larda gelir.
- Deal oluşturma, listeleme (pagination + status filtresi + sıralama), detay, temel alan güncelleme (`expectedVersion` ile), cancel action'ı
- Deal erişim kuralı: yalnız participant ilişkisi olan legal entity'ler — bu slice'ta tek participant, oluşturan (initiator) entity'dir
- Basit `DealLifecycleProjection` alanı (ADR-003 §16) — bu aşamada çoğunlukla DRAFT/CANCELLED üretir ama mekanizma ve merkezi algoritma yeri kurulur
- Append-only audit: create/update/cancel mutation'ları audit kaydıyla aynı transaction'da

Kapsam dışı:

- Buyer/seller ataması, davetler, participant yönetimi → **Slice 4**. Bu slice'ta deal'in buyer/seller alanları boş/atanmamış kalabilir; "aynı entity hem buyer hem seller olamaz" invariant'ı domain'e Slice 4'te girer.
- Doküman upload (Slice 5), AI (Slice 6+), ratification/funding/fulfillment
- Deal silme (yasak — CANCELLED/ARCHIVED açık durumlardır, generic soft delete yok; ADR-003 §7.2)
- ARCHIVED geçişinin UI'ı (domain'de kural olarak var, ekran sonraya kalabilir)
- Gerçek zamanlı liste güncellemesi (yenileme/polling yeterli, ADR-005 §9)

## 3. Okunacak ADR bölümleri

- ADR-003 §2 (Deal adlandırması — Transaction adı YASAK), §7–7.2 (Deal aggregate ve invariant'lar), §8–9 (state modeli, DealStatus), §16 (lifecycle projection), §23 (repository/modül erişimi), §24–25 (transaction sınırları, concurrency), §27 (veri tipleri)
- ADR-006 §2–5 (URL/metot kuralları), §9–12 (liste, pagination, sorting, filtering), §19 (409), §21–23 (expectedVersion/version), §41 (read projection)
- ADR-004 §22–23 (checklist, Done tanımı)

## 4. Public API yüzeyi

`core-api-v1.yaml`'a eklenecek yüzey (implementasyondan önce tasarlanır):

- `POST /api/v1/deals` — DRAFT deal oluşturma; aktif legal entity initiator olarak kaydedilir; 201 + Location
- `GET /api/v1/deals` — ADR-006 §9 liste DTO'su (`items/page/size/totalElements/totalPages`); `?status=` filtresi; `?sort=createdAt,desc` (allowlist: createdAt, title); default sort belirtilir
- `GET /api/v1/deals/{dealId}` — detay; erişimi olmayan için 404
- `PATCH /api/v1/deals/{dealId}` — title/description güncelleme; body'de `expectedVersion` zorunlu; stale ise 409 `DEAL_STALE_VERSION`
- `POST /api/v1/deals/{dealId}/cancel` — business action endpoint'i (ADR-006 §4); geçersiz durumdan cancel 409 `DEAL_STATE_CONFLICT`

Response'larda `version` (optimistic lock token) ve `lifecycle` (projection) alanları döner. Detay response'u ileride ADR-006 §41 tarzı `availableActions` projection'ına genişleyecek şekilde tasarlanır — bu slice'ta en az `canCancel` benzeri tek bir action bilgisi ile başlanması önerilir ki frontend buton görünürlüğünü status kombinasyonundan TÜRETMESİN.

## 5. Backend yönlendirmesi

- Modül: `deal` (ADR-003 §4.3). Organization modülüne erişim yalnız port/ID üzerinden (JPA entity paylaşımı yasak, ADR-003 §23).
- Flyway: deals tablosu — UUID id, tenantId, reference (üretilen kısa insan-okur referans; üretim stratejisi implementer'a), title, description, dealStatus, initiatorLegalEntityId, createdBy, timestamps, bigint version. Buyer/seller kolonları şimdi eklenebilir (nullable) veya Slice 4'te expand–contract ile eklenir — öneri: şimdi nullable ekle, Slice 4 migration yükünü azaltır.
- Participant erişim modeli: ayrı bir deal_participants tablosu ŞİMDİ kurulmalı (initiator tek satır) — Slice 4 davet/participant eklerken erişim sorguları değişmez. Erişim sorguları "kullanıcının aktif entity'si deal'in participant'ı mı" üzerinden yazılır; tenantId filtresi tek başına yetki DEĞİLDİR (ADR-003 §5).
- State machine: geçiş kuralları aggregate içinde tek yerde (örn. `Deal.cancel()` gibi davranış metotları); geçersiz geçiş domain exception → 409. Status alanına dışarıdan serbest set yasak.
- Optimistic locking: JPA `@Version`; `expectedVersion` application katmanında current version ile karşılaştırılır ve uyuşmazlık `DEAL_STALE_VERSION` üretir — sessiz last-write-wins yasak (ADR-003 §25).
- Audit: create/update/cancel için append-only kayıt, business mutation ile aynı transaction (ADR-003 §24). Slice 2'de kurulan audit port'u kullanılır.
- Lifecycle projection: ADR-003 §16 öncelik algoritmasının iskeleti merkezi tek bir sınıfta kurulur; bu aşamada girdisi yalnız DealStatus olsa da imzası diğer status'ları alacak şekilde tasarlanır. Frontend'in kendi lifecycle hesabı yapması yasaktır (ADR-003 §29).
- Idempotency-Key bu slice'ta zorunlu değil (deal create duplicate'i para riski taşımaz); ADR-006 §24 aday listesinde de yok. Eklenmez — YAGNI.

## 6. Frontend yönlendirmesi

- Ekranlar: deal listesi (`/app/deals`), oluşturma formu, detay (`/app/deals/:dealId`).
- Liste: pagination kontrolü, status filtresi, boş state ("henüz deal yok" + oluşturmaya yönlendirme), loading/error durumları.
- Detay: temel alanlar + lifecycle/status gösterimi; düzenleme formu `version`'ı saklar ve `expectedVersion` olarak gönderir.
- **Stale version akışı (bu slice'ın frontend'e öğrettiği ana desen):** 409 `DEAL_STALE_VERSION` alındığında kullanıcıya "kayıt başka bir işlemle değişti" bildirimi + güncel veriyi yeniden yükleme seçeneği sunulur; form sessizce üzerine yazmaz (ADR-006 §21).
- Cancel: onay diyaloğu + başarı sonrası güncel durum ekranda görünür. Buton görünürlüğü backend'in verdiği action bilgisine göre (`canCancel`), status kombinasyonu türetmesiyle değil.
- TanStack Query: liste ve detay sorguları; mutation sonrası ilgili query invalidation.

## 7. Kabul testi (tarayıcı akışı)

1. Kullanıcı A aktif entity'siyle deal oluşturur → listede görür → detayını açar
2. Title günceller → değişiklik ve artan version görünür
3. İki tab'da aynı deal açılır; birinde güncelleme yapılır, diğerindeki eski formla güncelleme denenir → 409 stale version akışı tarayıcıda gözlenir
4. Deal cancel edilir → durum CANCELLED görünür; cancel edilmiş deal'de düzenleme/cancel aksiyonları kapalıdır; zorlanırsa 409
5. İkinci browser'da kullanıcı B (farklı entity) kullanıcı A'nın deal ID'sine erişmeyi dener → 404; B'nin listesinde A'nın deal'i yoktur
6. Liste filtresi (status=CANCELLED vb.) ve pagination çalışır; boş filtre sonucu boş state gösterir

## 8. Minimum invariant testleri

ADR-004 §7 listesinden bu slice'a düşenler:

- Yasak state geçişleri (CANCELLED→ACTIVE, COMPLETED→ACTIVE, CANCELLED durumda update) reddedilir
- Stale `expectedVersion` → conflict; başarılı update version'ı artırır
- Participant olmayan entity deal'i okuyamaz/değiştiremez (authorization sınırı)
- Audit kaydının mutation ile aynı transaction'da yazıldığı (mutation rollback → audit de yok)
- Liste response'unun `items: []` (null değil) döndürdüğü

## 9. Açık sorular / karar noktaları

- Deal `reference` üretim biçimi (insan-okur kısa kod; format önerisini implementer getirir)
- Create formunun minimum alan seti (öneri: yalnız title zorunlu, description opsiyonel — form şişirilmez)
- DRAFT→ACTIVE geçişinin tetikleyicisi bu slice'ta kullanıcıya açılacak mı? (öneri: hayır; ACTIVE'e geçiş, taraflar atandıktan sonra anlam kazanır → Slice 4+)
- Buyer/seller kolonlarının şimdi nullable eklenmesi önerisi kabul mü? (expand–contract maliyeti vs erken şema)

## 10. Done tanımı

- [ ] OpenAPI yüzeyi implementasyondan önce tasarlandı; liste DTO'su ADR-006 §9 formatında
- [ ] Deal create/list/detail/update/cancel gerçek Spring + PostgreSQL üzerinde çalışıyor
- [ ] State machine geçiş kuralları domain'de tek yerde; yasak geçişler 409 üretiyor
- [ ] Optimistic locking uçtan uca çalışıyor; stale version akışı TARAYICIDA test edildi
- [ ] Participant tabanlı erişim izolasyonu iki-browser testiyle doğrulandı
- [ ] Audit kayıtları aynı transaction'da; rollback testi geçiyor
- [ ] Lifecycle projection merkezi algoritma iskeleti kuruldu; frontend lifecycle hesaplamıyor
- [ ] §8 invariant testleri geçiyor; aşırı test yazılmadı
- [ ] Frontend loading/error/empty durumları ve action-availability tabanlı buton görünürlüğü çalışıyor
