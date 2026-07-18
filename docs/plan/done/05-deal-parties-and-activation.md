# Slice 5 — Deal Parties (Buyer/Seller) ve Activation Readiness

- Durum: done
- Slice sırası: ADR-004 §24 → Deal parties capability
- **Tamamlanma notu (2026-07-18):** Gerçek local Spring + PostgreSQL + Vite
  ortamında iki bağımsız tab bağlamıyla kabul tamamlandı. Alpha=BUYER ve
  Beta=SELLER görünürlüğü, eşit/nonparticipant taraf redleri, non-initiator UI
  ve zorlanmış istek reddi, CANCELLED mutation reddi, DRAFT/no-activate sınırı
  ve stale-version recovery kanıtlandı. Slice 4 invitation/participation akışı
  da regresyonsuz tekrar çalıştı. Contract validator, backend verify ve frontend
  typecheck/build yeşildir; Deal DRAFT kalır ve activate endpoint/action yoktur.
- Öncül: 04-deal-invitations-and-participation
- Ardıl: 06-document-upload; ratification bu slice'ın taraf modeline dayanır

## 1. Amaç ve kullanıcı sonucu

Initiator, Deal participant'ları arasından buyer ve seller atar. Aynı entity'nin
iki role atanması engellenir. Diğer participant kendi rolünü görür.

Bu slice Deal'i ACTIVE yapmaz. Taraf modeli ratification'a hazırlanır; ticari
commitment ancak buyer ve seller aynı immutable package sürümünü onayladığında
oluşur (ADR-009).

## 2. Kapsam / kapsam dışı

Kapsam:

- Buyer ve seller atama/kaldırma (yalnız DRAFT)
- Yalnız participant entity'lerin taraf olabilmesi
- Buyer ≠ seller invariant'ı
- Actor-aware party management availability
- Taraf rollerinin participant/detail projection'ında gösterilmesi
- Audit'in aynı transaction'da yazılması

Kapsam dışı:

- Doğrudan `DRAFT → ACTIVE` action'ı
- RatificationPackage ve taraf onayları
- ACTIVE cancellation workflow
- Participant çıkarma/ayrılma
- ACTIVE durumda party değiştirme

## 3. Okunacak ADR bölümleri

- ADR-003 §7–9, §11, §20, §25
- ADR-008 §2.3–2.4
- ADR-009 tamamı
- ADR-005 §20–21
- ADR-006 §18–23, §33

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Buyer ve seller'ı birlikte güncelleyen atomik parties endpoint'i
- Request'te zorunlu `expectedVersion`
- Deal detail içinde buyer/seller ve participant role projection'ı
- Actor-aware party management action availability

Sabit davranışlar:

- Buyer = seller → 422 `VALIDATION_FAILED`
- Participant olmayan entity → 422
- DRAFT dışında party değişikliği → 409 `DEAL_STATE_CONFLICT`
- Stale version → 409 `DEAL_STALE_VERSION`
- Bu slice'ta activate endpoint'i yoktur.

## 5. Backend yönlendirmesi

- Deal party referansları nullable başlar.
- DB, buyer/seller değerinin aynı Deal'in participant satırına bağlı olduğunu
  composite referential integrity ile garanti eder; yalnız legal entity FK'sı
  yeterli değildir.
- Buyer ≠ seller hem domain hem DB seviyesinde korunur.
- Party assignment yalnız Deal'in immutable `initiatorLegalEntityId` değeri adına
  çalışan kullanıcıya açıktır; initiator dolaylı veriden çıkarılmaz. Diğer
  participant'lar read/list ile sınırlıdır.
- İlk rol modelinde initiator entity'nin `ADMIN` ve `MEMBER` kullanıcıları DRAFT
  koordinasyonu yapabilir. Ratification ve ACTIVE cancellation onayı yalnız
  `ADMIN` içindir (ADR-009).
- Yetki merkezi operation policy'sinde uygulanır; controller'a kopya kontrol eklenmez.
- Party update optimistic concurrency kullanır ve audit aynı transaction'da yazılır.
- Basic Deal update ve DRAFT cancel çok-participant dünyada initiator ile
  sınırlandırılır. Participant görünürlüğü mutation yetkisi değildir.
- Deal DRAFT kalır. `DRAFT → ACTIVE`, ratification slice'ında ikinci gerekli
  taraf onayıyla package `RATIFIED` olurken atomik yapılır.
- ACTIVE olduktan sonra party veya contractual alan değişikliği mevcut package'ı
  mutation ile değiştiremez; yeni package/version süreci gerekir.

## 6. Frontend yönlendirmesi

- Deal detail'de participant'lardan buyer/seller seçimi ve rol rozetleri bulunur.
- Düzenleme yalnız backend'in action projection'ı izin veriyorsa gösterilir.
- `Activate` butonu bu slice'ta eklenmez.
- Taraflar tamamlandığında kullanıcıya yalnız “ratification için taraflar hazır”
  bilgisi verilebilir; ACTIVE veya onaylanmış gibi gösterilmez.
- 422 field hataları ve stale-version yenileme akışı mevcut desenle ele alınır.

## 7. Kabul testi

1. A, Alpha=BUYER ve Beta=SELLER atar; iki browser da rolleri görür.
2. Aynı entity iki role seçildiğinde kayıt oluşmaz.
3. Participant olmayan entity atanamaz.
4. B tarafları değiştiremez; butonu görmez ve zorlanan istek reddedilir.
5. CANCELLED Deal'de party mutation reddedilir.
6. Taraflar tamamlansa da Deal DRAFT kalır ve activate action görünmez.
7. İki tab'daki eşzamanlı party update stale-version akışını üretir.

## 8. Minimum invariant testleri

- Buyer ≠ seller (domain + DB)
- Participant olmayan entity party olamaz
- Non-initiator party/basic-update/cancel mutation reddi
- Stale parties update ve başarılı version artışı
- Taraflar tam olduğunda Deal'in kendiliğinden ACTIVE olmaması

Aşırı state/HTTP kombinasyonu testi yazılmaz.

## 9. Açık sorular / karar noktaları

- Initiator'ın buyer/seller dışında aracı kalabilmesi kabul edilmiştir.
- Ratification'ı şirket adına yalnız `ADMIN` onaylar (ADR-009); daha ayrıntılı
  imza yetkisi ratification planında additive permission olarak değerlendirilebilir.
- ACTIVE cancellation request aggregate/API biçimi bu slice'ın konusu değildir.

## 10. Done tanımı

- [x] OpenAPI parties yüzeyi implementasyondan önce tasarlandı
- [x] Party migration ve composite participant bütünlüğü uygulandı
- [x] Buyer/seller assignment DRAFT'ta explicit initiator authorization ile çalışıyor
- [x] Deal activation bu slice'ta açılmadı
- [x] Actor-aware action projection frontend tarafından kullanılıyor
- [x] §8 minimum testleri geçiyor ve audit aynı transaction'da
- [x] §7 iki-browser kabul akışı tamamlandı
- [x] Önceki slice kabul akışları regresyonsuz
- [x] Contract validator, backend verify ve frontend typecheck/build yeşil
