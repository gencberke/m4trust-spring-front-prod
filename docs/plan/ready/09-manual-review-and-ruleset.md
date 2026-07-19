# Slice 9 — Manual Review ve RuleSetVersion

- Durum: ready
- Slice sırası: ADR-004 §24 → "Manual Review and RuleSetVersion"
  (bölünmüş yol haritasında 09)
- Öncül: 08-ai-document-extraction
- Ardıl: 10-ratification (ratification bu slice'ın kabul edilmiş rule-set
  versiyonuna dayanır)
- **İnsan onayı:** 19 Temmuz 2026. Bu onay planda açıkça tarif edilen public
  OpenAPI yüzeyi ile onu kilitleyen `contracts/scripts/validate_contracts.py`,
  `contracts/README.md` ve `contracts/CHANGELOG.md` additive güncellemelerini
  kapsar. AI JSON Schema/fixture, AsyncAPI veya AI-internal OpenAPI değişikliği
  gerekmez; bunlardan biri gerekirse eskalasyondur.

## 1. Amaç ve kullanıcı sonucu

Initiator tarafı, REVIEW_REQUIRED durumundaki extraction sonucunu gerçek
tarayıcıda inceler: kural değerlerini düzeltir, yanlış çıkarılmış kuralları
hariç tutar, sonucu onaylar → onayla birlikte immutable bir **RuleSetVersion**
oluşur, AnalysisStatus ACCEPTED olur ve Deal'in current rule-set pointer'ı bu
versiyona işaret eder. Geçmiş hiçbir zaman silinmez veya değiştirilmez
(ADR-003 §19).

Yeni bir doküman versiyonu current olduğunda önceki analiz/kabul zinciri
SUPERSEDED olur ve Deal current rule-set pointer'ı aynı finalize transaction'ında
temizlenir. Pointer, yeni extraction → yeni review → yeni RuleSetVersion kabulü
tamamlanana kadar `null` kalır; önceki versiyonlar yalnız tarihçede okunur.

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
- Yeni current document finalize edildiğinde eski analysis/rule-set zincirinin
  SUPERSEDED edilmesi ve Deal current rule-set pointer'ının aynı transaction'da
  temizlenmesi
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
- Review kabul action'ı: target analysis id + kural kararları
  (kept/modified/excluded/added) + Deal `expectedVersion`; başarıda oluşan
  RuleSetVersion referansını döner
- Rule-set tarihçesi: versiyon listesi ve tekil versiyon içeriği
- Deal detail'e current rule-set özeti + actor-aware `canReviewExtraction`

Additive compatibility kuralları:

- Mevcut closed `DealDetail` ve `DealAvailableActions` şemalarına eklenecek
  current rule-set özeti ve review action alanları **optional response member**
  olur; mevcut `required` listeleri veya alan anlamları değiştirilmez.
- Closed `DocumentAnalysisStatus` enum'una `ACCEPTED` eklenmesi aynı slice'ta
  generated frontend type/switch güncellemesi ve bilinmeyen-değer read-only
  fallback'i ile birlikte rollout edilir. Eski/eksik action alanı frontend
  tarafından `false` kabul edilir; mutation tahmin edilmez.
- Yeni path/operation/schema ve enum beklentileri
  `contracts/scripts/validate_contracts.py` exact allowlist/field kontrollerine
  aynı contract commit'inde eklenir; contracts README/CHANGELOG güncellenir.

Sabit davranışlar:

- REVIEW_REQUIRED dışındaki analize kabul → 409 `DEAL_STATE_CONFLICT` sınıfı
- Superseded extraction'a kabul → 409
- Değer doğrulamaları (money integer, basis points 0–10000 vb.) 422 field
  hatası; para/yüzde floating-point ASLA (ADR-003 §21)
- Stale `expectedVersion` → 409; kabul action'ı `Idempotency-Key` ister
  (çift tıklama iki versiyon üretmemeli)
- Read yüzeyleri tüm participant'lara açık; kabul yalnız initiator tarafına
- Nonparticipant veya gizli Deal → non-disclosing 404; Deal'i görebilen fakat
  initiator olmayan participant'ın kabul denemesi → 403

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
  Deal bu action'ın primary concurrency aggregate'idir; request'teki
  `expectedVersion` Deal version'ıdır. Bütün document/review mutation'ları önce
  Deal satırını, sonra current analysis satırını lock eder. Target analysis'in
  hâlâ current document'a ait REVIEW_REQUIRED kayıt olduğu lock altında tekrar
  doğrulanır; eşzamanlı kabul/finalize denemelerinde tek geçerli sonuç oluşur.
- **Supersede aralığı:** yeni document finalize transaction'ı Deal lock'u
  altında eski analysis'i SUPERSEDED eder, current rule-set pointer'ını `null`
  yapar ve ratification readiness'i düşürür. Eski RuleSetVersion silinmez veya
  current gibi sunulmaz; yeni AI sonucu tek başına pointer kuramaz.
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
- `DocumentAnalysisStatus` additive olarak `ACCEPTED` alırken generated type ve
  UI switch'leri aynı committe güncellenir; bilinmeyen status güvenli read-only
  fallback gösterir ve mutation açmaz.

## 7. Kabul testi (tarayıcı akışı)

1. Slice 8 akışıyla REVIEW_REQUIRED sonuç elde edilir.
2. Initiator bir kuralın değerini düzeltir, bir kuralı hariç tutar, elle bir
   kural ekler → onaylar → ACCEPTED durumu ve RuleSetVersion v1 görünür.
3. İkinci browser'daki participant rule-set'i okur; review/kabul aksiyonlarını
   görmez, zorlanan istek reddedilir.
4. Kabul action'ının aynı Idempotency-Key ile tekrarı ikinci versiyon üretmez;
   iki tab'dan eşzamanlı kabul tek versiyonla sonuçlanır (stale akışı görünür).
5. Yeni doküman versiyonu yüklenir → analiz zinciri SUPERSEDED ve current
   rule-set pointer'ı boşalır; yeni review kabul edilene kadar ratification
   READY değildir. Eski RuleSetVersion tarihçede okunur kalır; yeni zincir v2
   üretince pointer v2'ye geçer.
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
- AI completed event/result persistence'ının tek başına RuleSetVersion veya
  current rule-set pointer üretmediği
- Yeni current document finalize'i ile analysis supersede + pointer clear +
  audit'in atomik olduğu; arada eski rules + yeni document READY görünmediği
- Deal → current analysis lock sırasının accept ↔ document-finalize yarışında
  deadlock veya çift-current sonuç üretmediği

## 9. V1 kararları

- Elle eklenen kuralın `ruleReference` değeri `manual-N` önekli ve aynı
  RuleSetVersion içinde unique üretilir.
- EXCLUDED kurallar review karar geçmişinde kalır; RuleSetVersion yalnız nihai
  geçerli kuralları taşır.
- Review kabulü bütün kararları taşıyan tek toplu action'dır; ayrı draft-save
  veya kural bazlı finalize yoktur.
- RuleSetVersion için ayrı `canonicalHash` bu slice'ta eklenmez. Canonical
  package hash'i Slice 10'un sorumluluğudur.

## 10. Done tanımı

- [ ] OpenAPI review/rule-set yüzeyi implementasyondan önce tasarlandı
- [ ] OpenAPI path/schema değişiklikleriyle birlikte contract validator exact
      beklentileri ve contracts README/CHANGELOG güncellendi; AI contract'ları
      değişmedi
- [ ] Yeni Deal response/action alanları optional; mevcut required listeler ve
      alan anlamları korunuyor; frontend eksik/bilinmeyen değerde read-only
- [ ] RuleSetVersion migration'ı immutable/insert-only ve pointer bütünlüğü
      DB seviyesinde
- [ ] Kabul akışı tek transaction; idempotent; eşzamanlılık testli
- [ ] AnalysisStatus ACCEPTED geçişi ve SUPERSEDED zinciri çalışıyor
- [ ] legalBasis provenance kuralları uygulanıyor; hiçbir business karar ona
      bağlı değil
- [ ] Yetki merkezi policy'den; review ticari onay olarak sunulmuyor
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] `ModuleArchitectureTest` contractintelligence ownership'ini kapsıyor
- [ ] §7 iki-browser kabul akışı tamamlandı; Slice 8 akışı regresyonsuz
- [ ] Contract validator, backend verify ve frontend typecheck/build yeşil
