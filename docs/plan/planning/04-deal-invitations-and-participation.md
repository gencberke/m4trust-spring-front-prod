# Slice 4 — Deal Invitations ve Cross-Entity Participation

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 4 (bölünmüş yol haritası)
- Öncül: 03-deal-creation-and-listing, 03.9-hardening-and-decisions
- Ardıl: 05-deal-parties-and-activation

## 1. Amaç ve kullanıcı sonucu

Kullanıcı A Deal'ine e-postayla karşı tarafı davet eder. Kullanıcı B farklı
tenant'taki legal entity'siyle daveti kabul eder, Deal'i listesinde görür ve
participant olarak görünür. Reject ve pending invitation revoke akışları çalışır.

Davet kabulü yalnız participation oluşturur; buyer/seller veya contractual
consent anlamına gelmez (ADR-009).

## 2. Kapsam / kapsam dışı

Kapsam:

- ADR-008 cross-tenant participant modeli
- Deal üzerinde immutable initiator legal entity referansı
- DealInvitation: PENDING → ACCEPTED / REJECTED / REVOKED
- E-posta bazlı incoming invitations
- Invitation create için reusable HTTP idempotency
- Participant ve pending invitation görünümü
- Audit'in business mutation ile aynı transaction'da yazılması

Kapsam dışı:

- Buyer/seller ataması
- Ratification ve Deal activation
- Participant çıkarma/ayrılma
- E-posta gönderim provider'ı
- ADR-008 cleanup release'i

## 3. Okunacak ADR bölümleri

- ADR-008 tamamı
- ADR-009 §2.1–2.3
- ADR-003 §4.3, §5, §23–25
- ADR-005 §20–21
- ADR-006 §19–25, §33
- ADR-007 §21, §24–25

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Deal invitation oluşturma ve listeleme
- Authenticated kullanıcının incoming invitations listesi
- Accept, reject ve revoke action'ları
- Deal detail participant projection'ı
- Actor-aware invitation action availability

Sabit davranışlar:

- Invitation create `Idempotency-Key` ister.
- Incoming invitations aktif legal entity header'ı istemez.
- Accept body seçilen `legalEntityId` değerini taşır; membership doğrulanır.
- Alıcı olmayan kullanıcı invitation varlığını öğrenemez (404).
- Aynı Deal + normalize e-posta için tek PENDING invitation bulunur.

## 5. Backend yönlendirmesi

### Rollout sırası

Tek plan korunur; deployment üç uyumlu aşamaya ayrılır:

1. **Expand release:** participant `legal_entity_tenant_id` nullable eklenir,
   mevcut satırlar bugünkü `tenant_id` değerinden backfill edilir, yeni
   FK/index'ler eklenir ve uygulama yeni kayıtları dual-write eder. Eski image
   bu şemayla çalışır.
2. **Switch release:** visibility participant legal entity + kendi tenant'ı
   eksenine, mutation authorization ise explicit initiator alanına geçer;
   invitation capability açılır. Ardından yeni kolon `NOT NULL` olur ve eski
   participant entity FK kaldırılır. Rollback hedefi expand image'ıdır.
3. **Cleanup release:** yalnız doğrulanmış gereksiz constraint/index temizliği.

`initiator_legal_entity_id` için ŞEMA İŞİ YOKTUR: kolon Slice 3'ten beri
(`V5__deal_foundation.sql`) NOT NULL ve hosting-tenant FK'sıyla mevcuttur;
oluşturma, participant kaydıyla aynı transaction'da zaten yazmaktadır. Bu
slice'ın initiator işi yalnız MUTATION AUTHORIZATION kontrollerinin bu explicit
alandan okunmasıdır. Alan immutable'dır; creator user, hosting tenant veya
participant sırası üzerinden sonradan çıkarılmaz (ADR-009 §2.2).

Switch ve contract adımları expand compatibility release'i production/staging'de
kanıtlanmadan aynı rollout'a sıkıştırılmaz.

### Yetki

- Visibility yalnız participant ilişkisine dayanır.
- Initiator temel Deal mutation'larını ve invitation create/revoke işlemlerini
  yönetir; ilk rol modelinde initiator entity'nin `ADMIN` ve `MEMBER` kullanıcıları
  DRAFT koordinasyonu yapabilir.
- Diğer participant'lar başlangıçta read/list yetkisine sahiptir.
- Accept/reject yalnız davetin normalize e-postasına bağlı authenticated kullanıcıya
  aittir.
- Incoming invitation işlemleri için entity-scoped `OperationContext` zorlanmaz;
  ayrı, kullanıcı-scoped doğrulanmış context kullanılır.

### Tutarlılık

- Accept/reject/revoke optimistic ve atomik durum geçişleridir.
- Accept: invitation ACCEPTED + participant insert + audit tek transaction'dır.
- Aynı kullanıcı/entity ile tekrar accept eşdeğer sonucu döndürebilir; farklı
  entity ile tekrar deneme 409'dur.
- Idempotency kaydı transaction başında unique olarak sahiplenilir; check-then-insert
  yarışı kurulmaz. Aynı key farklı canonical request için 409 döner.
- Deal modülü identity/organization repository'sine erişmez; port kullanır.

## 6. Frontend yönlendirmesi

- Incoming invitations authenticated alanda aktif entity seçimine bağlı olmadan
  görünür.
- Accept sırasında kullanıcının entity'leri arasından seçim yapılır.
- Deal detail participant ve pending invitation bölümlerini gösterir.
- Mutation butonları backend action projection'ından gelir; participant/status
  üzerinden frontend tarafından türetilmez.
- Retry sırasında aynı Idempotency-Key korunur.

## 7. Kabul testi

1. A, B'nin e-postasına invitation oluşturur.
2. B ikinci browser profilinde invitation'ı görür ve farklı tenant'taki Beta ile
   kabul eder.
3. B Deal'i list/detail yüzeyinde görür; A Beta'yı participant olarak görür.
4. Üçüncü kullanıcı Deal ve invitation'a erişemez.
5. Reject ve pending revoke akışları çalışır.
6. Çift gönderim ikinci PENDING invitation oluşturmaz.
7. B participant olduktan sonra Deal mutation butonlarını görmez ve zorlanan
   mutation backend tarafından reddedilir.

## 8. Minimum invariant testleri

- Cross-tenant participant visibility + nonparticipant 404
- Non-initiator Deal/invitation mutation reddi (yetki explicit initiator alanından)
- Accept/revoke concurrency ve terminal transition
- Duplicate PENDING + aynı/farklı idempotency request davranışı
- Expand dual-write ve switch query uyumluluğu

Bunların dışında geniş unit-test matrisi kurulmaz.

## 9. Açık sorular / karar noktaları

- Endpoint adları ve additive DTO biçimi OpenAPI tasarımında kesinleşir.
- Invitation expiry başlangıçta yoktur; ihtiyaç oluşursa ayrı durum/politika eklenir.
- Participant leave/removal bu slice'a eklenmez; gerçek ihtiyaç görülürse ayrı
  lifecycle kararı olarak ele alınır.

## 10. Done tanımı

- [ ] OpenAPI invitation/participant yüzeyi önce tasarlandı
- [ ] Expand release participant tenant backfill'i ve rollback uyumluluğuyla kanıtlandı
- [ ] Switch release cross-tenant visibility ve explicit initiator authorization ile çalışıyor
- [ ] Invitation state machine ve idempotency çalışıyor
- [ ] Yetki visibility'den bağımsız ve backend'de doğrulanıyor
- [ ] §8 minimum testleri geçiyor
- [ ] §7 iki-browser akışı gerçek sistemde tamamlandı
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
