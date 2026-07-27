# Slice 6 — Document Upload

- Durum: done (kabul: 2026-07-18)
- Slice sırası: yol haritası 06
- Öncül: Deal creation; sırada Slice 4–5'ten sonra
- Ardıl: AI Document Extraction

## 1. Amaç ve kullanıcı sonucu

Initiator Deal detayından PDF/DOCX yükler. Dosya browser'dan doğrudan private
object storage'a gider; Spring binary upload proxy'si olmaz. Finalize sonrası
doğrulanmış metadata current document olarak görünür. Yeni versiyon eskisini
SUPERSEDED yapar; geçmiş korunur.

Diğer participant'lar başlangıçta dokümanı görür ve indirir; contractual document
mutation yetkisi visibility'den otomatik türemez.

## 2. Kapsam / kapsam dışı

Kapsam:

- Document aggregate ve PENDING_UPLOAD → AVAILABLE → SUPERSEDED akışı
- Presigned PUT/GET için object storage port ve adapter
- Upload intent, finalize, list ve download-link yüzeyleri
- Doğrulanmış size/checksum metadata'sı
- Immutable object version referansı
- Deal current document pointer'ı
- Finalize idempotency ve atomik supersede
- Local MinIO private bucket + CORS/bootstrap

Kapsam dışı:

- AI extraction ve RabbitMQ
- Virus/malware tarama
- Doküman silme
- Participant belge teklif/karşı-teklif workflow'u
- Staging object storage provider kararı

## 3. Okunacak ADR bölümleri

- ADR-001 §6, §16
- ADR-003 §4.4, §4.11, §7–7.2, §23–25
- ADR-006 §24–25, §49–50
- ADR-007 §14, §18
- ADR-009 §2.2, §2.4

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Deal document upload intent
- Document finalize (`Idempotency-Key` zorunlu)
- Deal document history
- Kısa ömürlü download link
- Deal detail current document ve actor-aware document actions

Sabit davranışlar:

- Başlangıç media type: PDF ve DOCX
- Client SHA-256 gönderir fakat tek başına authoritative kabul edilmez.
- Terminal Deal'e upload intent → 409
- Size/checksum uyuşmazlığı finalize'ı reddeder.
- Aynı idempotency key + aynı request tek etki; farklı request → 409.

## 5. Backend yönlendirmesi

### Storage bütünlüğü

- Spring tahmin edilemez object key üretir; bucket private kalır.
- Finalize, client beyanına değil storage adapter'ın doğruladığı size ve checksum'a
  dayanır. Provider-native verified checksum kullanılır; yoksa doğrulama storage
  üzerinden transaction dışında yapılır.
- Finalize edilen doküman overwrite edilemez. Object versioning kullanılır ve
  kayıtta object version/ref tutulur; download ve AI erişimi aynı immutable
  version'a pinlenir.
- Pending upload için expiry zamanı tutulur. Expired kayıt current olamaz; fiziksel
  cleanup scheduler'ı başlangıçta zorunlu değildir.

### Transaction ve concurrency

- Presigned URL ve storage doğrulaması DB transaction'ı dışında yapılır.
- DB finalize adımı tek transaction'dır: document AVAILABLE, önceki current
  SUPERSEDED, Deal pointer güncellemesi, audit ve idempotency sonucu.
- Aynı Deal için eşzamanlı farklı finalize işlemleri serialize edilir; transaction
  sonunda yalnız bir current AVAILABLE document kalır.
- Deal pointer'ı DB seviyesinde aynı Deal'e ait document'e bağlanır; başka Deal'in
  document ID'si pointer olamaz.
- Document ve Deal modülleri birbirinin repository'sine erişmez; dar port üzerinden
  tek application workflow'u ve açık lock sırası kullanılır.

### Yetki

- Upload intent ve finalize yalnız initiator legal entity adına çalışan kullanıcıya
  açıktır.
- Participant'lar metadata/download read yetkisine sahiptir.
- Frontend action availability yalnız backend projection'ından gelir.
- Current contractual document değişikliği ileride mevcut ratification package'ı
  mutation ile değiştirmez; yeni package version gerektirir (ADR-009).

## 6. Frontend yönlendirmesi

- Dosya seçimi → client hash → intent → direct PUT → finalize akışı
- Progress, retry ve yarım upload hatası; başarısız upload current'ı etkilemez
- Current document ve SUPERSEDED geçmişi
- Finalize retry'sında aynı Idempotency-Key
- Mutation yetkisi olmayan participant için salt-okunur kart
- Süresi dolmuş upload intent yeniden oluşturulur; eski object current olmaz

## 7. Kabul testi

1. Initiator PDF yükler; current document görünür ve refresh sonrası kalır.
2. İkinci doküman yüklenir; önceki SUPERSEDED olarak geçmişte kalır.
3. Size/checksum uyuşmazlığı finalize edilmez.
4. Diğer participant görür/indirir fakat upload/finalize yapamaz.
5. Participant olmayan kullanıcı metadata/link yüzeyine erişemez.
6. CANCELLED Deal'de upload kapalıdır.
7. Eşzamanlı finalize denemeleri sonunda tek current document vardır.
8. Aynı finalize retry ikinci supersede etkisi üretmez.

## 8. Minimum invariant testleri

- Verified size/checksum mismatch reddi
- Eşzamanlı finalize → tek current document
- Başka Deal document pointer'ının DB tarafından reddi
- Immutable object version referansı
- Finalize idempotency + DB rollback atomicity
- Non-initiator mutation ve nonparticipant read reddi

Bunların dışında adapter detaylarını test eden geniş matris kurulmaz.

## 9. Açık sorular / karar noktaları

- Başlangıç max dosya boyutu config'ten 50 MB önerisidir.
- Presigned TTL config'ten yönetilir; exact süre deployment config'inde belirlenir.
- Staging storage provider'ı Slice 6 staging kabulü öncesinde seçilir.
- Participant document proposal ihtiyacı bu temel upload akışına eklenmez; gerçek
  ürün ihtiyacı doğarsa ayrı workflow olur.

## 10. Done tanımı

- [x] OpenAPI intent/finalize/list/download yüzeyi önce tasarlandı
- [x] Document migration ve same-Deal current pointer bütünlüğü uygulandı (V11 composite FK)
- [x] Storage adapter private, immutable ve verified metadata üretiyor (MinIO versioning)
- [x] Direct browser upload + finalize gerçek MinIO ile çalışıyor (CORS'lu direct PUT doğrulandı)
- [x] Concurrent finalize tek current sonucu veriyor (§8 testi + DB tek-current invariant)
- [x] Yetki visibility'den bağımsız uygulanıyor (participant read; non-initiator mutation 403)
- [x] §8 minimum testleri geçiyor ve audit aynı transaction'da
- [x] §7 gerçek tarayıcı akışı tamamlandı (initiator + participant, iki oturum; §7.1–7.8)
- [x] Contract validator, backend verify ve frontend typecheck/build yeşil
