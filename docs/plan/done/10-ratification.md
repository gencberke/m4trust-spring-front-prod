# Slice 10 — Ratification ve Deal Activation

- Durum: done
- Tamamlanma tarihi: 20 Temmuz 2026
- Kapsam sapması: Yok. Kabul hardening'i mevcut ADR ve plan kararlarını
  sıkılaştırdı; yeni public yüzey veya business kapsamı eklenmedi.
- Slice sırası: ADR-004 §24 → "Ratification" (bölünmüş yol haritasında 10)
- Öncül: 05-deal-parties-and-activation, 09-manual-review-and-ruleset
- Ardıl: funding/payment slice'ı (bu planın kapsamı dışında)
- **Eskalasyon sonucu:** Ratification package ve structured commercial terms
  kapsamı 19 Temmuz 2026 tarihli insan onayıyla ADR-010'da bağlayıcı olarak
  kapatıldı. Implementasyon ADR-010'u uygular. Onay planda açıkça tarif edilen
  public OpenAPI yüzeyi ile onu kilitleyen `contracts/scripts/validate_contracts.py`,
  `contracts/README.md` ve `contracts/CHANGELOG.md` additive güncellemelerini
  kapsar. AI JSON Schema/fixture, AsyncAPI veya AI-internal OpenAPI değişikliği
  gerekmez; bunlardan biri gerekirse eskalasyondur.

## 1. Amaç ve kullanıcı sonucu

Taraflar atanmış (Slice 5) ve rule-set kabul edilmiş (Slice 9) bir Deal'de:
initiator ratification package oluşturur → buyer ve seller entity'lerinin
ADMIN kullanıcıları AYNI immutable package versiyonunu ayrı ayrı onaylar →
ikinci gerekli onayla birlikte package RATIFIED ve Deal ACTIVE olur — tek
business transaction'da, atomik (ADR-009 §2.3).

RATIFIED öncesi buyer veya seller ADMIN'i package'ı reject edebilir; devam
etmek yeni package gerektirir (ADR-009 §2.4). ACTIVE Deal tek taraflı iptal
edilemez; mevcut cancel action'ı DRAFT-withdrawal olarak kalır (ADR-009 §2.5).

Bu slice projenin ticari commitment kapısıdır: davet kabulü ve review'dan
farklı olarak buradaki onay şirketi bağlayan rızadır.

## 2. Kapsam / kapsam dışı

Kapsam:

- `ratification` modülü (ADR-003 §4.6): RatificationPackage, party approvals
- Package = canonical immutable snapshot + canonical content hash (ADR-003 §20;
  içerik kapsamı §9'daki karara tabi)
- **Structured commercial terms:** package, en az sözleşme bedelini
  (`amountMinor` + ISO 4217 `currency`) yapılandırılmış alan olarak taşır.
  Değer, kabul edilmiş RuleSetVersion'daki MONEY kurallarından ÖNERİLİR ve
  initiator package oluştururken exact değeri açıkça göndererek teyit
  eder/düzeltir. Sıfır, bir veya birden fazla MONEY önerisi bulunması exact
  değeri sessizce seçmez. `amountMinor`, `1..9007199254740991` aralığında
  pozitif integer'dır; onaylanan şey bu
  yapılandırılmış değerdir (ADR-010 §2.1). Funding slice'ı (11) tutarı serbest
  metinden veya rule listesinden DEĞİL bu alandan okur.
- Ratification readiness projection: package yokken `NOT_READY | READY`.
  RatificationPackage durumu: `PENDING → RATIFIED | REJECTED | SUPERSEDED`.
  `EXPIRED` bu slice'ta kullanılmaz; READY persist edilmiş package durumu değildir.
- Package oluşturma (initiator), approve/reject (taraf ADMIN'leri), entity
  başına tek etkili onay
- İkinci gerekli onayla atomik RATIFIED + Deal ACTIVE geçişi
- Package'ı geçersizleştiren değişikliklerde (parties, current rule-set,
  current document veya initiator'ın farklı exact commercial terms ile yeni
  package oluşturması) mevcut PENDING package'ın otomatik SUPERSEDED olması
- DRAFT withdrawal ↔ son onay yarışının serialize edilmesi (ADR-009 §2.4)
- Lifecycle projection RATIFICATION girdisi ve ACTIVE görünümü
- Audit aynı transaction'da; approve/reject `Idempotency-Key`'li

Kapsam dışı:

- Funding, payment, fulfillment
- Mutual cancellation aggregate'i ve ACTIVE cancel yüzeyi (kapalı kalır)
- Package expiry (EXPIRED) politikası
- İnce imza yetkisi modeli (ADMIN ötesi; ADR-009 §2.6 additive gelecek işi)
- ACTIVE sonrası herhangi bir package/parti/doküman mutation'ı (yeni
  version/re-ratification süreci sonraki ihtiyaçta ayrı planlanır)

## 3. Okunacak ADR bölümleri

- ADR-009 tamamı (bu slice'ın ana kaynağı)
- ADR-010 tamamı (package kapsamı, commercial terms ve Slice 11 bağı)
- ADR-003 §4.6, §8–§9, §11, §16, §20, §23–§25
- ADR-005 §21; ADR-008 §2.3–§2.4
- ADR-006 §4, §19, §21–§25, §33

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Package oluşturma action'ı (initiator; exact `amountMinor` + `currency` +
  Deal `expectedVersion` + `Idempotency-Key`; MONEY kuralları yalnız öneridir).
  DRAFT'ta mevcut PENDING package varken yeni exact terms ile create,
  eskisini SUPERSEDED edip yeni immutable package üretir.
- Package detail: immutable `RatificationPackageSnapshot` + `contentHash` ile
  bunları saran mutable/actor-aware durum ve taraf onay projection'ı. Hangi
  entity'nin onayladığı/beklediği ve zaman görünür; kullanıcı kimliği yalnız
  aynı entity'nin yetkili kullanıcılarına görünür — §9.
- Approve action'ı ve reject action'ı (taraf ADMIN'i; target package id +
  `expectedPackageVersion` + `Idempotency-Key`)
- Deal detail'e ratification projection + actor-aware action'lar
  (`canCreateRatificationPackage`, `canApproveRatification`,
  `canRejectRatification`)

Additive compatibility kuralları:

- `RatificationPackageSnapshot` yeni, dedicated, closed ve immutable bir schema
  olur. Public detail projection bu schema'yı aynen taşır; mutable status,
  package/version metadata'sı, approvals, approver görünürlüğü, available
  actions ve audit alanları snapshot'ın dışında ayrı wrapper alanlarıdır.
- Mevcut closed `DealDetail` ve `DealAvailableActions` şemalarına eklenen
  ratification projection/action member'ları optional olur; mevcut `required`
  listeleri ve alan anlamları değiştirilmez. Eksik/bilinmeyen action frontend'de
  `false` ve read-only kabul edilir.
- Yeni path/operation/schema/enum beklentileri validator exact allowlist ve
  field-set kontrollerine aynı contract commit'inde eklenir; README/CHANGELOG
  güncellenir.

Sabit davranışlar:

- Taraflar veya kabul edilmiş rule-set eksikken package oluşturma → 409
- Nonparticipant veya gizli Deal/package → non-disclosing 404; Deal'i görebilen
  fakat buyer/seller olmayan participant'ın ya da yanlış entity bağlamının
  approve/reject denemesi → 403
- `MEMBER` rolüyle approve/reject → 403 (ADR-009 §2.6)
- Aynı entity'nin ikinci onayı idempotent sonuç döner; farklı package
  versiyonuna karşı onay → 409 `RATIFICATION_STALE_PACKAGE` sınıfı
- RATIFIED/REJECTED/SUPERSEDED package'a approve/reject → 409
- Deal ACTIVE olduktan sonra `PATCH /deals/{dealId}` temel alan güncellemesi,
  DRAFT withdrawal/cancel, parties, document ve rule-set mutation action'ları
  kapalıdır (availableActions false + backend reddi)

## 5. Backend yönlendirmesi

- **Package snapshot:** package, oluşturulduğu anda kaynak verilerin
  KOPYASINI taşır (dealId, reference, title, buyer/seller entity kimlikleri +
  legal name'leri, RuleSetVersion id + kural içerik özeti, structured
  commercial terms — sözleşme bedeli amountMinor + currency —, current
  document id + object version + sha256, package schema version). Commercial
  terms alanı integer minor unit'tir; float yasak (ADR-003 §21, ADR-006 §28). Kaynak kayıtlar sonradan
  değişse bile package içeriği değişmez; `contentHash` canonical JSON
  serileştirmesinin SHA-256'sıdır ve onayların neye verildiğini kanıtlar
  (ADR-003 §20).
- **Canonicalization:** hash girdisi yalnız OpenAPI'deki dedicated immutable
  `RatificationPackageSnapshot` JSON'ıdır. `contentHash`, package id/version/
  status, approvals, approver/actor-specific visibility, available actions,
  timestamps/audit ve wrapper metadata'sı hash girdisine dahil değildir. Bütün
  integer alanlar I-JSON safe
  integer aralığıyla, özellikle `amountMinor <= 9007199254740991` ile sınırlıdır.
  JSON RFC 8785 (JCS) ile canonical
  edilir, UTF-8 byte dizisinin SHA-256 özeti lowercase 64-char hex olarak
  saklanır/sunulur. UUID, currency ve hash casing'i contract'ta sabittir;
  snapshot `rules` dizisi unique `ruleReference` değerinin UTF-8 bytewise
  ascending sırasındadır. V1 snapshot'a başka array eklenirse ordering kuralı
  contract'ta sabitlenmeden eklenemez. Aynı semantic snapshot her ortamda aynı
  byte/hash'i üretmelidir.
- **Approvals:** append-only tablo (package id, entity id, onaylayan kullanıcı,
  zaman). "Entity başına en fazla bir etkili onay" DB unique kısıtıyla
  desteklenir; aynı entity'nin farklı ADMIN'lerinin onayları toplanmaz
  (ADR-009 §2.3).
- **Atomik aktivasyon:** ikinci gerekli onay geldiğinde package RATIFIED +
  Deal ACTIVE + audit tek transaction'dır; kısmi durum kalamaz. Deal'e geçiş
  dar port üzerinden yapılır (`Deal.activate()` davranış metodu; serbest status
  set yok). Rollback ikisini de geri alır.
- **Yarış serileştirmesi:** create/approve/reject/supersede ve DRAFT-withdrawal
  önce Deal, sonra current RatificationPackage satırını lock eder. Approve/reject
  request'i target package'ın `expectedPackageVersion` değerini lock altında
  doğrular; package primary action target'ıdır, Deal lock'u lifecycle ve
  cross-module yarış kapısıdır. Aynı sıra bütün dar port çağrılarında korunur:
  onay önce biterse Deal ACTIVE olur ve withdrawal 409; withdrawal önce
  biterse package SUPERSEDED olur ve onay 409 (ADR-009 §2.4).
- **Supersede tetikleri:** parties değişikliği, yeni RuleSetVersion kabulü,
  yeni current document — PENDING package'ı aynı transaction'da
  SUPERSEDED yapar. Bu bağ ilgili modüllere dar port/event ile kurulur;
  ratification modülü deal/document/contractintelligence repository'lerine
  erişmez (ADR-003 §23).
- **Commercial terms replacement:** initiator DRAFT aşamasında farklı exact
  terms gönderirse create action aynı Deal lock'u altında mevcut PENDING
  package'ı SUPERSEDED eder ve yeni package üretir. Eski approval'lar taşınmaz;
  RATIFIED/ACTIVE package için bu yol kapalıdır.
- **Yetki:** merkezi operation policy genişletilir: approve/reject için
  "aktif entity = package'ın buyer veya seller'ı" + "membership rolü ADMIN"
  application katmanında doğrulanır; `OperationContext` rol bilgisini taşımıyorsa
  bu slice'ta eklenir (tek noktadan). Controller'a kopya kontrol yazılmaz.
- **NOT_READY/READY hesabı:** taraflar + kabul edilmiş rule-set + current
  document varlığından türetilir; package oluşturma READY önkoşulunu ve exact
  commercial terms'i application katmanında doğrular. Projection bir MONEY
  kuralını sessizce seçmez.

## 6. Frontend yönlendirmesi

- Deal detail'e "Ratification" bölümü: package özeti (içerik + hash kısaltması),
  taraf onay durumu, aktör bazlı approve/reject/oluştur butonları (yalnız
  backend projection'ı izin veriyorsa).
- Approve onay diyaloğu açık rıza dilindedir: "Şirketiniz adına bu package
  içeriğini bağlayıcı olarak onaylıyorsunuz" + package hash gösterimi.
- SUPERSEDED/REJECTED durumları tarihçeyle görünür; kullanıcı neyin neden
  geçersizleştiğini görür.
- ACTIVE sonrası Deal detail salt-okunur ticari içerik sunar; parties/doküman/
  review aksiyonları kaybolur (projection'dan).
- MEMBER kullanıcı approve butonunu hiç görmez; zorlanan istek reddi test edilir.

## 7. Kabul testi (tarayıcı akışı)

İki browser + üç kullanıcı (initiator/buyer ADMIN, seller ADMIN, bir MEMBER):

1. Review'daki MONEY önerileri gösterilir; birden fazla adayda otomatik seçim
   yapılmaz. Initiator exact tutar/currency değerini teyit eder veya düzeltir.
2. Package oluşturulduğunda iki taraf aynı structured commercial terms,
   canonical içerik ve contentHash'i görür; package sonradan değiştirilemez.
3. Taraflar ve kabul edilmiş rule-set hazırken initiator package oluşturur →
   iki taraf da package içeriğini ve PENDING durumunu görür.
4. Buyer ADMIN onaylar → durum "1/2 onay"; Deal hâlâ DRAFT.
5. Seller ADMIN onaylar → package RATIFIED + Deal ACTIVE aynı anda görünür;
   lifecycle ACTIVE/FUNDING-öncesi görünümü alır.
6. ACTIVE Deal'de temel alan PATCH'i, parties/doküman/review/cancel aksiyonları
   kapalıdır; zorlanan istekler reddedilir.
7. (Yeni Deal) Seller ADMIN reject eder → REJECTED; devam için yeni package
   gerekir; eski onaylar taşınmaz.
8. (Yeni Deal) Package PENDING iken initiator parties'i değiştirir → package
   SUPERSEDED; eski package'a onay denemesi 409.
9. (Yeni Deal) Bir onay varken initiator withdrawal ile son onay yarıştırılır
   (iki tab) → yalnız bir sonuç oluşur; diğeri 409 alır.
10. MEMBER kullanıcı approve göremez; doğrudan istek 403 alır.
11. Aynı entity'nin ikinci ADMIN'i onay verdiğinde onay sayısı artmaz.
12. Approve çift tıklaması (aynı Idempotency-Key) tek onay üretir.
13. Bir onaydan sonra initiator farklı exact terms ile yeni package oluşturur →
    eski package SUPERSEDED, eski onay etkisiz ve yeni hash farklıdır.
14. Approve ↔ reject ve approve ↔ document/rule-set supersede yarışlarında tek
    terminal sonuç oluşur; kaybeden stale package 409 alır.

## 8. Minimum invariant testleri

- İki FARKLI entity'nin aynı package versiyonu onayı olmadan ACTIVE olamaz
- Aynı entity çoklu ADMIN onayı tek etkili onay sayılır (DB kısıtı dahil)
- MEMBER approve/reject reddi
- Atomik RATIFIED+ACTIVE; rollback'te ikisi de geri alınır
- REJECTED/SUPERSEDED package'a onay reddi; onaylar yeni package'a taşınmaz
- Withdrawal ↔ son onay yarışı (her iki sıralama)
- Package immutability + contentHash'in içerikle eşleştiği
- Detail wrapper'daki status/approval/actor-specific alan değişikliklerinin
  immutable snapshot byte'ını veya contentHash'i değiştirmediği
- Structured amount/currency doğrulaması, canonical hash'e dahil edilmesi ve
  package oluşturulduktan sonra değiştirilememesi
- Supersede tetiklerinin (parties/rule-set/document değişimi) çalıştığı
- Yeni exact commercial terms package'ının önceki PENDING package ve
  approvals'ı supersede ettiği
- Approve ↔ reject ve approve ↔ supersede yarışları; Deal → package lock sırası
- ACTIVE durumda basic PATCH dahil bütün mevcut DRAFT mutation endpoint'lerinin
  server-side reddi

## 9. V1 bağlayıcı kararları

- Package; Deal çekirdeği, buyer/seller party snapshot'ı, RuleSetVersion,
  current document, structured `amountMinor` + `currency`, `schemaVersion` ve
  `contentHash` içerir. Tracking policy ile operasyonel funding/fulfillment
  kayıtları package dışındadır (ADR-010 §2.1).
- MONEY rule değerleri yalnız öneridir. Sıfır/bir/çok aday exact değer seçmez;
  initiator create isteğinde exact tutar ve currency gönderir.
- Karşı tarafa onaylayan entity + zaman gösterilir; kullanıcı adı yalnız aynı
  entity içindeki yetkili kullanıcılara gösterilir.
- Problem Details kodlarının exact adları OpenAPI'de implementasyondan önce
  sabitlenir.
- Canonical JSON/hash, §5'teki RFC 8785 + UTF-8 + SHA-256 lowercase hex kuralını
  ve dedicated snapshot/exclusion sınırını kullanır; implementerin seçimine
  bırakılmaz.
- READY hesaplanan projection'dır; yalnız package'lı durumlar persist edilir.

## 10. Done tanımı

- [x] §9 package içeriği ve commercial terms ADR-010'a göre uygulandı
- [x] OpenAPI ratification yüzeyi implementasyondan önce tasarlandı
- [x] OpenAPI path/schema değişiklikleriyle birlikte contract validator exact
      beklentileri ve contracts README/CHANGELOG güncellendi; AI contract'ları
      değişmedi
- [x] Dedicated immutable `RatificationPackageSnapshot` hash sınırı wrapper'ın
      mutable/actor-specific alanlarından ayrıldı; yeni Deal alanları optional
- [x] Package snapshot + contentHash + append-only approvals migration'ları
      DB kısıtlarıyla kuruldu
- [x] Structured commercial terms package'ta yapılandırılmış ve initiator
      teyitli olarak taşınıyor; Slice 11'in tutar kaynağı hazır
- [x] Atomik RATIFIED+ACTIVE geçişi ve yarış serileştirmesi testli çalışıyor
- [x] Supersede tetikleri ilgili modüllerden dar port'larla bağlandı
- [x] ADMIN-only onay ve entity-başına-tek-onay uygulanıyor
- [x] ACTIVE sonrası mutation yüzeyleri kapalı; lifecycle projection doğru
- [x] `ModuleArchitectureTest` ratification ownership'ini kapsıyor
- [x] §8 invariant testleri geçiyor; audit aynı transaction'da
- [x] §7 çok-kullanıcılı gerçek tarayıcı akışı tamamlandı; önceki slice
      akışları regresyonsuz
- [x] Contract validator, backend verify ve frontend typecheck/build yeşil
