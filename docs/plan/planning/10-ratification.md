# Slice 10 — Ratification ve Deal Activation

- Durum: planning
- Slice sırası: ADR-004 §24 → "Ratification" (bölünmüş yol haritasında 10)
- Öncül: 05-deal-parties-and-activation, 09-manual-review-and-ruleset
- Ardıl: funding/payment slice'ı (bu planın kapsamı dışında)
- **Eskalasyon notu:** Bu slice ADR-INDEX Katman 3 kapsamındadır (ratification
  mutation'ı). Plan `ready/`ye insan onayı olmadan geçmez; §9'daki package
  içerik kararı çözülmeden implementasyon başlamaz.

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
  initiator package oluştururken açıkça teyit eder/düzeltir; onaylanan şey bu
  yapılandırılmış değerdir. Funding slice'ı (11) tutarı serbest metinden veya
  rule listesinden DEĞİL bu alandan okur.
- RatificationStatus: NOT_READY → READY → PENDING → RATIFIED / REJECTED /
  SUPERSEDED (ADR-003 §11; EXPIRED bu slice'ta kullanılmaz)
- Package oluşturma (initiator), approve/reject (taraf ADMIN'leri), entity
  başına tek etkili onay
- İkinci gerekli onayla atomik RATIFIED + Deal ACTIVE geçişi
- Package'ı geçersizleştiren değişikliklerde (parties, current rule-set,
  current document) otomatik SUPERSEDED
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
- ADR-003 §4.6, §8–§9, §11, §16, §20, §23–§25
- ADR-005 §21; ADR-008 §2.3–§2.4
- ADR-006 §4, §19, §21–§25, §33

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Package oluşturma action'ı (initiator; `expectedVersion` + `Idempotency-Key`)
- Package detail: canonical içerik, contentHash, durum, taraf onay durumu
  (hangi entity onayladı/bekliyor — onaylayan kullanıcı kimliği participant'lara
  açık mı: §9)
- Approve action'ı ve reject action'ı (taraf ADMIN'i; `Idempotency-Key`)
- Deal detail'e ratification projection + actor-aware action'lar
  (`canCreateRatificationPackage`, `canApproveRatification`,
  `canRejectRatification`)

Sabit davranışlar:

- Taraflar veya kabul edilmiş rule-set eksikken package oluşturma → 409
- Buyer/seller tarafı olmayan entity'nin approve/reject'i → 403/404
  (non-disclosure kuralı OpenAPI'de netleşir)
- `MEMBER` rolüyle approve/reject → 403 (ADR-009 §2.6)
- Aynı entity'nin ikinci onayı idempotent sonuç döner; farklı package
  versiyonuna karşı onay → 409 `RATIFICATION_STALE_PACKAGE` sınıfı
- RATIFIED/REJECTED/SUPERSEDED package'a approve/reject → 409
- Deal ACTIVE olduktan sonra parties/document/rule-set mutation action'ları
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
- **Approvals:** append-only tablo (package id, entity id, onaylayan kullanıcı,
  zaman). "Entity başına en fazla bir etkili onay" DB unique kısıtıyla
  desteklenir; aynı entity'nin farklı ADMIN'lerinin onayları toplanmaz
  (ADR-009 §2.3).
- **Atomik aktivasyon:** ikinci gerekli onay geldiğinde package RATIFIED +
  Deal ACTIVE + audit tek transaction'dır; kısmi durum kalamaz. Deal'e geçiş
  dar port üzerinden yapılır (`Deal.activate()` davranış metodu; serbest status
  set yok). Rollback ikisini de geri alır.
- **Yarış serileştirmesi:** approve ve DRAFT-withdrawal aynı Deal satırı
  lock'u üzerinden serialize edilir (Slice 6 `lockForDocumentMutation` deseni):
  onay önce biterse Deal ACTIVE olur ve withdrawal 409; withdrawal önce
  biterse package SUPERSEDED olur ve onay 409 (ADR-009 §2.4).
- **Supersede tetikleri:** parties değişikliği, yeni RuleSetVersion kabulü,
  yeni current document — READY/PENDING package'ı aynı transaction'da
  SUPERSEDED yapar. Bu bağ ilgili modüllere dar port/event ile kurulur;
  ratification modülü deal/document/contractintelligence repository'lerine
  erişmez (ADR-003 §23).
- **Yetki:** merkezi operation policy genişletilir: approve/reject için
  "aktif entity = package'ın buyer veya seller'ı" + "membership rolü ADMIN"
  application katmanında doğrulanır; `OperationContext` rol bilgisini taşımıyorsa
  bu slice'ta eklenir (tek noktadan). Controller'a kopya kontrol yazılmaz.
- **NOT_READY/READY hesabı:** taraflar + kabul edilmiş rule-set + current
  document varlığından türetilir; package oluşturma READY önkoşulunu
  application katmanında doğrular.

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

1. Taraflar ve kabul edilmiş rule-set hazırken initiator package oluşturur →
   iki taraf da package içeriğini ve PENDING durumunu görür.
2. Buyer ADMIN onaylar → durum "1/2 onay"; Deal hâlâ DRAFT.
3. Seller ADMIN onaylar → package RATIFIED + Deal ACTIVE aynı anda görünür;
   lifecycle ACTIVE/FUNDING-öncesi görünümü alır.
4. ACTIVE Deal'de parties/doküman/review/cancel aksiyonları kapalıdır;
   zorlanan istekler reddedilir.
5. (Yeni Deal) Seller ADMIN reject eder → REJECTED; devam için yeni package
   gerekir; eski onaylar taşınmaz.
6. (Yeni Deal) Package PENDING iken initiator parties'i değiştirir → package
   SUPERSEDED; eski package'a onay denemesi 409.
7. (Yeni Deal) Bir onay varken initiator withdrawal ile son onay yarıştırılır
   (iki tab) → yalnız bir sonuç oluşur; diğeri 409 alır.
8. MEMBER kullanıcı approve göremez; doğrudan istek 403 alır.
9. Aynı entity'nin ikinci ADMIN'i onay verdiğinde onay sayısı artmaz.
10. Approve çift tıklaması (aynı Idempotency-Key) tek onay üretir.

## 8. Minimum invariant testleri

- İki FARKLI entity'nin aynı package versiyonu onayı olmadan ACTIVE olamaz
- Aynı entity çoklu ADMIN onayı tek etkili onay sayılır (DB kısıtı dahil)
- MEMBER approve/reject reddi
- Atomik RATIFIED+ACTIVE; rollback'te ikisi de geri alınır
- REJECTED/SUPERSEDED package'a onay reddi; onaylar yeni package'a taşınmaz
- Withdrawal ↔ son onay yarışı (her iki sıralama)
- Package immutability + contentHash'in içerikle eşleştiği
- Supersede tetiklerinin (parties/rule-set/document değişimi) çalıştığı

## 9. Açık sorular / karar noktaları

- **Package içerik kapsamı (ESKALASYON — implementasyon öncesi zorunlu
  karar):** ADR-003 §20 package'a "tracking policy" ve funding/fulfillment
  contractual data dahil eder; bunlar henüz tanımsız/mevcut değil. Öneri: v1
  package içeriği funding-öncesi mevcut verilerle sınırlanır (deal çekirdeği,
  taraflar, RuleSetVersion, current document) ve bu daraltma kısa bir karar
  notu / ADR-003 amendment'ı olarak kayda geçirilir. İnsan onayı gerekir.
- Structured commercial terms önerisinin RuleSetVersion'dan türetilme kuralı
  (hangi kategori/valueType eşleşmesi öneri sayılır; birden fazla MONEY kuralı
  varsa seçim davranışı) ve initiator teyidinin package create isteğindeki
  temsili — OpenAPI tasarımında kesinleşir
- Onaylayan kullanıcı kimliğinin karşı tarafa gösterilip gösterilmeyeceği
  (öneri: yalnız entity + zaman gösterilir; kullanıcı adı kendi entity'sine
  görünür)
- `RATIFICATION_STALE_PACKAGE` vb. Problem Details kodlarının kesin adları
  OpenAPI tasarımında sabitlenir
- Canonical JSON serileştirme kuralı (hash tekrarlanabilirliği için alan
  sırası/format sözleşmesi) — implementer önerir, planner onaylar
- READY durumunun ayrı persist mi yoksa hesaplanan projection mı olacağı
  (öneri: hesaplanan; yalnız package'lı durumlar persist edilir)

## 10. Done tanımı

- [ ] §9 package içerik kararı insan onayıyla kayda geçti (ADR notu)
- [ ] OpenAPI ratification yüzeyi implementasyondan önce tasarlandı
- [ ] Package snapshot + contentHash + append-only approvals migration'ları
      DB kısıtlarıyla kuruldu
- [ ] Structured commercial terms package'ta yapılandırılmış ve initiator
      teyitli olarak taşınıyor; Slice 11'in tutar kaynağı hazır
- [ ] Atomik RATIFIED+ACTIVE geçişi ve yarış serileştirmesi testli çalışıyor
- [ ] Supersede tetikleri ilgili modüllerden dar port'larla bağlandı
- [ ] ADMIN-only onay ve entity-başına-tek-onay uygulanıyor
- [ ] ACTIVE sonrası mutation yüzeyleri kapalı; lifecycle projection doğru
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] §7 çok-kullanıcılı gerçek tarayıcı akışı tamamlandı; önceki slice
      akışları regresyonsuz
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
