# ADR-009: Deal Commitment and Cancellation Consent

- Durum: Accepted
- Tarih: 17 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Deal taslak yönetimi, ratification ile ACTIVE geçişi ve ACTIVE iptal yetkisi
- Bağlı kararlar:
  - ADR-003: Core Domain Model and Deal Lifecycle
  - ADR-005: Authentication and Security Baseline
  - ADR-008: Cross-Tenant Deal Participation

## 1. Bağlam

ADR-003 immutable `RatificationPackage`, package hash'i ve append-only taraf
onaylarını tanımlar. Ancak `DRAFT → ACTIVE` geçişinin ratification ile bağı ve
`ACTIVE → CANCELLED` geçişinin kim tarafından tetiklenebileceği açık değildir.

Cross-tenant participant görünürlüğü de mutation yetkisi anlamına gelmez. Bir
Deal'i başlatan legal entity taslağı koordine edebilir; diğer taraf adına ticari
rıza veremez ve aktif bir ticari ilişkiyi tek başına sona erdiremez.

## 2. Karar

### 2.1 Davet kabulü ticari rıza değildir

Deal davetini kabul etmek yalnız ilgili legal entity'nin Deal participant'ı
olmasını sağlar. Buyer/seller rolü, sözleşme içeriği, funding, fulfillment veya
Deal'in ACTIVE olması kabul edilmiş sayılmaz.

### 2.2 Initiator taslak koordinatörüdür

Deal, oluşturma anındaki aktif legal entity'yi immutable
`initiatorLegalEntityId` olarak taşır. Initiator; hosting tenant, creator user,
en eski participant veya başka dolaylı veriden çıkarılmaz. Oluşturma sırasında
initiator participant kaydıyla aynı transaction'da yazılır.

DRAFT aşamasında initiator legal entity:

- temel Deal alanlarını ve taraf taslağını yönetebilir,
- davet oluşturabilir ve pending daveti revoke edebilir,
- Deal'i ratification'a hazırlanacak hale getirebilir,
- DRAFT Deal'i geri çekerek CANCELLED yapabilir.

İlk rol modelinde initiator legal entity'nin `ADMIN` ve `MEMBER` kullanıcıları bu
DRAFT koordinasyon işlemlerini yapabilir. Bu yetki şirketi bağlayan ratification
veya ACTIVE cancellation onayı anlamına gelmez.

Diğer participant'lar başlangıçta read/list yetkisine sahiptir. Participant
ilişkisi tek başına update, cancel, invite, party assignment veya activation
yetkisi vermez.

### 2.3 ACTIVE yalnız başarılı ratification ile oluşur

`DRAFT → ACTIVE` doğrudan initiator action'ı değildir. Geçiş yalnız:

1. current canonical `RatificationPackage` mevcutsa,
2. buyer ve seller aynı immutable package sürümünü onayladıysa,
3. package `RATIFIED` durumuna atomik olarak geçtiyse

gerçekleşir.

Her gerekli legal entity package başına en fazla bir etkili onay sağlar; aynı
entity'deki birden fazla ADMIN onay sayısını artırmaz.

İkinci gerekli taraf onayıyla package'ın `RATIFIED` olması ve Deal'in `ACTIVE`
olması aynı business transaction içinde tamamlanır. Initiator buyer veya seller
değilse bu onayların yerine geçemez.

### 2.4 Değişiklik ve ratification'dan vazgeçme

Ratification package immutable'dır. Buyer/seller, accepted rule-set, current
contractual document veya package içeriğindeki başka bir contractual bilgi
değişirse yeni package version oluşturulur; eski onaylar yeni sürümde geçerli
sayılmaz.

Package henüz RATIFIED değilken buyer veya seller'ın yetkili ADMIN'i package'ı
reddedebilir. Package `REJECTED` olur; mevcut approval kayıtları audit geçmişi
olarak korunur ve devam etmek için yeni package gerekir. RATIFIED olduktan sonra
tek taraflı approval geri çekme yoktur; cancellation/dispute akışı kullanılır.

Initiator bir DRAFT Deal'i geri çektiğinde current READY/PENDING package aynı
transaction'da SUPERSEDED olur. Son gerekli approval ile DRAFT withdrawal yarışı
serialize edilir: approval önce tamamlanırsa Deal ACTIVE olur ve doğrudan cancel
reddedilir; withdrawal önce tamamlanırsa sonraki approval reddedilir.

### 2.5 ACTIVE Deal tek taraflı doğrudan iptal edilemez

Mevcut doğrudan Deal cancel action'ı yalnız DRAFT geri çekme davranışıdır.
`ACTIVE → CANCELLED` geçişi korunur ancak yalnız:

- buyer ve seller legal entity'lerinin aynı cancellation talebine karşılıklı
  onayıyla veya
- yetkili casework/dispute resolution kararıyla

gerçekleşebilir.

Bir participant, initiator veya tek bir taraf ACTIVE Deal'i doğrudan cancel
edemez. Mutual cancellation akışının aggregate/API ayrıntıları ilgili sonraki
slice'ta tasarlanır; bu karar uygulanmadan ACTIVE cancel yüzeyi açılmaz.

### 2.6 Şirket adına yüksek riskli onay

İlk rol modelinde ratification ve mutual cancellation onayı yalnız ilgili legal
entity'de `ADMIN` membership'i bulunan kullanıcı tarafından verilebilir.
`MEMBER` katılım ve görünürlük kazanır fakat şirketi bağlayan bu onayları
veremez. Daha ayrıntılı imza yetkisi ileride ayrı permission modeliyle additive
olarak genişletilebilir.

## 3. Slice etkisi

- Slice 4, immutable initiator referansını ve cross-tenant invitation/visibility
  zeminini kurar; invitation acceptance ticari rıza üretmez.
- Slice 5 buyer/seller ataması ve ratification readiness'i kurar; kullanıcıya
  doğrudan activate action'ı sunmaz.
- Ratification slice'ı package approval/rejection işlemlerini ve
  `RATIFIED → Deal ACTIVE` atomik bağını uygular.
- ACTIVE cancellation workflow casework/dispute veya ayrı cancellation slice'ında
  uygulanır.

Mevcut kodda ACTIVE basic edit/cancel izinleri bulunabilir; kullanıcı akışında
ACTIVE üretimi açılmadan önce bu davranışlar bu ADR'ye hizalanmalıdır. Bu
hizalamanın sahibi ratification slice'ıdır: `DRAFT → ACTIVE` bağını uygulayan
slice, ACTIVE edit/cancel izinlerinin bu ADR'ye çekilmesini kendi Done tanımına
açık madde olarak alır; hizalama yapılmadan ACTIVE üretimi açılamaz.

## 4. Yasaklanan yaklaşımlar

- Initiator legal entity'yi hosting tenant, creator user veya participant sırasından çıkarmak
- Davet kabulünü buyer/seller veya contract onayı saymak
- Participant görünürlüğünü bütün Deal mutation'ları için yetki saymak
- Initiator'a tek başına `DRAFT → ACTIVE` yetkisi vermek
- Farklı ratification package sürümlerindeki onayları birleştirmek
- Aynı legal entity'nin birden fazla ADMIN onayını birden fazla taraf onayı saymak
- Buyer ve seller rızası veya casework kararı olmadan ACTIVE Deal'i cancel etmek
- `MEMBER` rolünü şirketi bağlayan ratification/cancellation onayı için yeterli saymak

## 5. Sonuçlar

- Initiator kimliği kalıcı ve denetlenebilir bir business alanıdır.
- Initiator taslağı hızlı biçimde yönetebilir; taraflar yalnız immutable aynı
  içeriğe onay verdiklerinde ticari commitment oluşur.
- ACTIVE sonrası tek taraflı kötüye kullanım engellenir.
- Her alan değişikliğinde ayrı çift onay yerine canonical package sürümü bir kez
  onaylanır.
- Mutual cancellation için ileride küçük bir workflow gerekir; bu maliyet aktif
  ticari ilişkinin tek taraflı silinmesini önlemek için bilinçli olarak kabul edilir.
