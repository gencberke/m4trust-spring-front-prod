# Slice 9 — Manual Review ve RuleSetVersion

- Durum: planning
- Slice sırası: ADR-004 §24 → "Manual Review and RuleSetVersion"
  (bölünmüş yol haritasında 09)
- Öncül: 08-ai-document-extraction
- Ardıl: 10-ratification (ratification bu slice'ın kabul edilmiş rule-set
  versiyonuna dayanır)

## 1. Amaç ve kullanıcı sonucu

Initiator tarafı, REVIEW_REQUIRED durumundaki extraction sonucunu gerçek
tarayıcıda inceler: kural değerlerini düzeltir, yanlış çıkarılmış kuralları
hariç tutar, sonucu onaylar → onayla birlikte immutable bir **RuleSetVersion**
oluşur, AnalysisStatus ACCEPTED olur ve Deal'in current rule-set pointer'ı bu
versiyona işaret eder. Geçmiş hiçbir zaman silinmez veya değiştirilmez
(ADR-003 §19).

Yeni bir doküman versiyonu yüklendiğinde önceki analiz/kabul zinciri SUPERSEDED
olur; yeni extraction → yeni review → yeni RuleSetVersion zinciri kurulur ve
önceki versiyonlar tarihçede kalır.

Bu kabul, AI çıktısının business veriye dönüştüğü TEK kapıdır; ratification'a
bağlanan hiçbir ticari onay anlamı taşımaz (ADR-009 §2.1 ayrımı).

## 2. Kapsam / kapsam dışı

Kapsam:

- Review çalışma yüzeyi: extraction sonucu kural bazında görüntüleme,
  düzeltme (değer/kategori/başlık/açıklama), hariç tutma, elle kural ekleme
- Review kararlarının tek onay action'ıyla immutable RuleSetVersion'a
  dönüştürülmesi; ExtractionResultVersion referansı, oluşturan kullanıcı,
  zaman, önceki versiyon referansı (ADR-003 §19)
- AnalysisStatus REVIEW_REQUIRED → ACCEPTED geçişi + Deal current rule-set
  pointer güncellemesi + audit — tek transaction
- Rule-set tarihçesinin read yüzeyi (versiyon listesi + içerik)
- Superseded extraction'ın kabul edilememesi
- `legalBasis` provenance kuralı (bkz. §5)

Kapsam dışı:

- Ratification/package oluşturma → Slice 10
- Versiyonlar arası diff/karşılaştırma UI'ı
- Çok taraflı (buyer/seller ortak) review workflow'u — review initiator taslak
  koordinasyonudur; karşı taraf sonucu Slice 10 package'ında değerlendirir
- Review SLA/hatırlatma, kısmi kaydetme (draft autosave) mekanizmaları
- Extraction'ın yeniden çalıştırılması (Slice 8 action'ı zaten karşılar)

## 3. Okunacak ADR bölümleri

- ADR-003 §4.5, §10, §17–§19, §23–§25
- ADR-009 §2.1–§2.2 (review'un ticari rıza olmadığı; DRAFT koordinasyonu)
- ADR-006 §4, §18–§23, §33 (action endpoint, 422/409, expectedVersion,
  action availability)
- ADR-005 §20–§21

## 4. Public API yüzeyi

Implementasyondan önce OpenAPI tasarlanır:

- Review projection: extraction kuralları + mevcut düzeltme durumu
- Review kabul action'ı: kural kararları (kept/modified/excluded/added) +
  `expectedVersion`; başarıda oluşan RuleSetVersion referansını döner
- Rule-set tarihçesi: versiyon listesi ve tekil versiyon içeriği
- Deal detail'e current rule-set özeti + actor-aware `canReviewExtraction`

Sabit davranışlar:

- REVIEW_REQUIRED dışındaki analize kabul → 409 `DEAL_STATE_CONFLICT` sınıfı
- Superseded extraction'a kabul → 409
- Değer doğrulamaları (money integer, basis points 0–10000 vb.) 422 field
  hatası; para/yüzde floating-point ASLA (ADR-003 §21)
- Stale `expectedVersion` → 409; kabul action'ı `Idempotency-Key` ister
  (çift tıklama iki versiyon üretmemeli)
- Read yüzeyleri tüm participant'lara açık; kabul yalnız initiator tarafına

## 5. Backend yönlendirmesi

- Modül: `contractintelligence` devamı. RuleSetVersion ve review kayıtları
  burada; Deal'e yalnız pointer güncellemesi dar port üzerinden gider
  (Slice 6'daki `DealDocumentMutationPort` deseni örnek alınır).
- **Immutability:** RuleSetVersion satırları insert-only; UPDATE/DELETE yolu
  yoktur. Migration'da tablo yorumu ve uygulamada repository tasarımıyla
  korunur; ArchUnit/test ile desteklenir. Kabul edilmiş geçmişi değiştirme
  girişimi tasarım gereği imkânsız olmalıdır (ADR-003 §19, FORBIDDEN).
- **Kural içeriği:** canonical extraction kuralı + review kararı (KEPT /
  MODIFIED / EXCLUDED / ADDED) + nihai değer. MODIFIED kuralda orijinal
  extraction değeri ExtractionResultVersion üzerinden zaten tarihçededir;
  RuleSetVersion nihai hali taşır.
- **legalBasis provenance:** advisory alan RuleSetVersion kuralına kopyalanır;
  MODIFIED/ADDED kurallarda "reviewerModified/manuallyAdded" işareti ile
  sunulur, hukuki doğrulama iddiası taşımaz. Hiçbir business kural legalBasis
  değerine göre karar vermez.
- **Pointer bütünlüğü:** Deal current rule-set pointer'ı DB seviyesinde aynı
  Deal'in RuleSetVersion'ına bağlanır (Slice 6 current-document deseninin
  aynısı: composite FK).
- **Transaction:** kabul tek transaction'dır — RuleSetVersion insert +
  AnalysisStatus ACCEPTED + Deal pointer + audit + idempotency sonucu.
  Eşzamanlı kabul denemeleri serialize edilir; tek versiyon oluşur.
- **Yetki:** kabul, initiator legal entity adına çalışan kullanıcıya açıktır
  (DRAFT koordinasyonu: ADMIN + MEMBER — ADR-009 §2.6 yalnız
  ratification/cancellation onayını ADMIN'e kilitler, review'u değil).
  Merkezi `DealOperationPolicy`/operation-context zinciri kullanılır.

## 6. Frontend yönlendirmesi

- Review ekranı: kural listesi (kategori, değer, confidence, kaynak, legalBasis
  rozeti), satır bazında düzelt/hariç tut, elle kural ekleme, onay özeti.
- Para girişi minor-unit/ondalık dönüşümünü UI'da yapar, wire'a integer gider;
  yüzde girişi basis points'e çevrilir.
- Kabul onay diyaloğu "bu içerik ratification'a temel olacak" dilinde; ama
  "sözleşme onaylandı" izlenimi VERMEZ.
- Kabul sonrası current rule-set özeti ve tarihçe görünür; SUPERSEDED zincir
  tarihçede kalır.
- Buton görünürlüğü yalnız backend action projection'ından; stale-version ve
  409 akışları mevcut desenle.

## 7. Kabul testi (tarayıcı akışı)

1. Slice 8 akışıyla REVIEW_REQUIRED sonuç elde edilir.
2. Initiator bir kuralın değerini düzeltir, bir kuralı hariç tutar, elle bir
   kural ekler → onaylar → ACCEPTED durumu ve RuleSetVersion v1 görünür.
3. İkinci browser'daki participant rule-set'i okur; review/kabul aksiyonlarını
   görmez, zorlanan istek reddedilir.
4. Kabul action'ının aynı Idempotency-Key ile tekrarı ikinci versiyon üretmez;
   iki tab'dan eşzamanlı kabul tek versiyonla sonuçlanır (stale akışı görünür).
5. Yeni doküman versiyonu yüklenir → analiz zinciri SUPERSEDED; eski
   RuleSetVersion tarihçede okunur kalır; yeni zincir v2 üretir ve pointer
   v2'ye geçer.
6. Superseded extraction üzerinde kabul denemesi 409 üretir.
7. Geçersiz değer (negatif tutar, 10000 üstü basis point) 422 field hatası
   üretir ve ekranda alan bazında görünür.

## 8. Minimum invariant testleri

- RuleSetVersion immutability (update/delete yolunun yokluğu) ve insert-only
  tarihçe
- Pointer'ın yalnız aynı Deal'in versiyonuna bağlanabilmesi (DB seviyesi)
- Kabul transaction atomicity: rollback'te status/pointer/versiyon/audit
  hiçbiri kalmaz
- Eşzamanlı kabul → tek RuleSetVersion
- Superseded extraction kabul reddi
- Non-initiator kabul reddi; nonparticipant read reddi
- Para/yüzde alanlarının integer olarak persist edildiği

## 9. Açık sorular / karar noktaları

- Elle eklenen kural için `ruleReference` üretim biçimi (öneri: `manual-N`
  önekli, versiyon içinde unique)
- EXCLUDED kuralların RuleSetVersion içeriğinde işaretli mi taşınacağı yoksa
  yalnız review kaydında mı kalacağı (öneri: review kaydında; RuleSetVersion
  yalnız geçerli kuralları taşır — ratification package'ı sadeleşir)
- Review kabulünde toplu tek action mı (önerilen ve §4'te varsayılan) yoksa
  kural bazlı kaydet + ayrı finalize mi
- `canonicalHash` alanının bu slice'ta mı yoksa Slice 10 package hash'iyle
  birlikte mi ekleneceği (öneri: Slice 10 — YAGNI)

## 10. Done tanımı

- [ ] OpenAPI review/rule-set yüzeyi implementasyondan önce tasarlandı
- [ ] RuleSetVersion migration'ı immutable/insert-only ve pointer bütünlüğü
      DB seviyesinde
- [ ] Kabul akışı tek transaction; idempotent; eşzamanlılık testli
- [ ] AnalysisStatus ACCEPTED geçişi ve SUPERSEDED zinciri çalışıyor
- [ ] legalBasis provenance kuralları uygulanıyor; hiçbir business karar ona
      bağlı değil
- [ ] Yetki merkezi policy'den; review ticari onay olarak sunulmuyor
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] §7 iki-browser kabul akışı tamamlandı; Slice 8 akışı regresyonsuz
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
