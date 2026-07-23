# ADR-011: Fulfillment and Evidence V1

- Durum: Accepted
- Tarih: 20 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Fulfillment V1 actor modeli, milestone/evidence cardinality,
  state machine, object-storage bütünlüğü ve completion sınırı
- Bağlı kararlar:
  - ADR-001: System Boundaries and Data Ownership
  - ADR-003: Core Domain Model and Deal Lifecycle
  - ADR-004: Vertical Slice Delivery and Acceptance Testing
  - ADR-005: Authentication and Security Baseline
  - ADR-006: Public API and Error Conventions
  - ADR-008: Cross-Tenant Deal Participation
  - ADR-009: Deal Commitment and Cancellation Consent
  - ADR-010: Ratification Commercial Terms and Funding Foundation

## 1. Bağlam

ADR-003 §4.7, §13 ve §22 fulfillment, milestone ve evidence sahipliğini
Spring'deki `fulfillment` modülüne verir; evidence'ın Deal ve milestone'a
bağlanmasını, binary'nin object storage'da tutulmasını ve AI/video sonucunun
otomatik fulfillment completion üretememesini zorunlu kılar. Ancak ilk V1
actor modeli, milestone cardinality'si, exact status geçişleri ve fulfillment
acceptance'ın Deal/payment üzerindeki etkisi açık değildir.

Slice 11'in kabulüyle `ACTIVE + FUNDED` Deal artık gerçek bir başlangıç
kapısıdır. Slice 12 bu kapının ardında provider ve deployment'tan bağımsız,
gerçek browser'da tamamlanabilen fulfillment/evidence omurgasını kuracaktır.

Document extraction contract'ındaki `deliveryRequirements` advisory AI
çıktısıdır. Slice 9 manual review yalnız typed rule kararlarını immutable
RuleSetVersion'a taşımış, Slice 10 ratification package yalnız bu accepted
rule-set'i onaylamıştır. Bu nedenle extraction `deliveryRequirements`
değerlerini contractual checklist, milestone veya completion kuralı saymak
tarafların ratify etmediği bir yükümlülük üretir.

Fulfillment acceptance ileride settlement/release eligibility'ye girdi
olabilir. V1 bu business kararını açık bir insan action'ı yapacak, fakat henüz
payment release, settlement veya Deal completion açmayacaktır.

## 2. Karar

### 2.1 V1 kapsamı, cardinality ve provenance

- Fulfillment yalnız `ACTIVE + FUNDED` Deal'de başlatılabilir.
- V1'de Deal başına tam olarak bir fulfillment kaydı ve onun altında tam olarak
  bir primary milestone vardır. Yarış altında ikinci kayıt DB unique
  invariant'ıyla engellenir.
- Seller'ın explicit start action'ı fulfillment ve primary milestone'ı atomik
  oluşturur. Funding sonucu otomatik fulfillment aggregate'i oluşturmaz.
- Fulfillment immutable current ratification package kimliğine bağlanır.
  Milestone, package'taki `DELIVERY` ve `QUALITY` rule reference'larını
  traceability amacıyla gösterebilir; rule metnini yeniden yorumlayarak yeni
  contractual obligation üretmez.
- AI extraction `deliveryRequirements` listesi V1 required checklist,
  contractual requirement veya otomatik completion girdisi değildir.
- Çoklu milestone, partial acceptance ve funding/payment allocation sonraki
  additive kararlardır.

### 2.2 Actor ve authorization modeli

- Seller legal entity'nin `ADMIN` ve `MEMBER` kullanıcıları fulfillment'ı
  başlatabilir ve evidence sunabilir.
- Current evidence'ı kabul veya reddetmek yalnız buyer legal entity'nin
  `ADMIN` kullanıcılarına aittir.
- Buyer `MEMBER`, buyer/seller olmayan participant ve yalnız initiator olması
  nedeniyle yetki bekleyen entity'ler fulfillment mutation yapamaz.
- Bütün Deal participant'ları fulfillment/evidence metadata'sını, immutable
  history'yi ve kısa ömürlü download link'ini okuyabilir.
- Nonparticipant veya başka Deal'e ait nested resource için non-disclosing 404
  korunur.
- Frontend action availability backend projection'ından gelir; application
  katmanı her mutation'da aynı operation-specific policy'yi tekrar doğrular.

### 2.3 Fulfillment ve evidence state machine

V1 `FulfillmentStatus` akışı:

- Fulfillment kaydı yok: `NOT_STARTED`
- Seller start: `NOT_STARTED -> IN_PROGRESS`
- Current milestone için upload intent: `IN_PROGRESS -> EVIDENCE_REQUIRED`
- Storage-verified finalize: `EVIDENCE_REQUIRED -> REVIEW_REQUIRED`
- Buyer reject: `REVIEW_REQUIRED -> EVIDENCE_REQUIRED`
- Buyer accept: `REVIEW_REQUIRED -> COMPLETED`

`CANCELLED` closed state setinde forward compatibility için bulunabilir, ancak
Slice 12'de bu duruma götüren action yoktur. Serbest status set veya sessiz
geriye geçiş yoktur.

Her upload ayrı bir `EvidenceSubmission` business kaydıdır:

- `PENDING_UPLOAD -> SUBMITTED -> ACCEPTED | REJECTED`

Rejected veya accepted submission mutation ile yeni dosyaya çevrilmez,
silinmez ve overwrite edilmez. Reject sonrasında replacement yeni submission
ID'si ve yeni immutable object version ile gelir. Bir milestone üzerinde aynı
anda yalnız bir current `SUBMITTED` evidence review bekleyebilir.

### 2.4 Evidence object ve history bütünlüğü

- Başlangıç evidence türleri `DELIVERY_NOTE`, `INVOICE`, `VIDEO`, `PHOTO`,
  `SIGNED_DOCUMENT`, `OTHER`; `UNKNOWN` kullanıcı submission türü değildir.
- Başlangıç media türleri PDF, DOCX, JPEG, PNG ve MP4'tür. Boyut limitleri
  media sınıfına göre config'te tutulur ve public contract'ta davranış olarak
  belgelenir.
- Upload akışı intent → browser direct PUT → storage-verified finalize
  şeklindedir. Core API binary upload proxy'si değildir.
- Client SHA-256 beyanı tek başına authoritative değildir. Finalize,
  storage-adapter tarafından doğrulanan size, checksum ve immutable object
  version'a dayanır.
- Bucket private, object key tahmin edilemez ve download link kısa ömürlüdür.
- Pending/expired intent current evidence olamaz. Physical orphan cleanup V1
  acceptance koşulu değildir.
- Evidence metadata'sı Spring/PostgreSQL'in, binary içerik object storage'ın
  sorumluluğundadır. Document aggregate'i evidence için yeniden kullanılmaz.

### 2.5 Manual acceptance ve completion sınırı

- Buyer `ADMIN` acceptance açık business action'ıdır; AI/video sonucu,
  seller submission'ı veya dosyanın teknik olarak finalize edilmesi acceptance
  sayılmaz.
- Accept yalnız current `SUBMITTED` evidence'a uygulanır ve primary milestone
  ile FulfillmentStatus'u `COMPLETED` yapar.
- Reject bounded, kullanıcıya gösterilebilir bir reason taşır; evidence
  history'de `REJECTED` kalır ve status `EVIDENCE_REQUIRED` olur.
- Fulfillment `COMPLETED` olduğunda Deal `ACTIVE` kalır ve lifecycle V1 boyunca
  `FULFILLMENT` gösterir.
- Fulfillment acceptance; Deal `COMPLETED`, SettlementStatus mutation,
  payment release, payout, refund, reversal, provider çağrısı veya AI job
  üretmez.
- Deal `COMPLETE` mutation'ını açan sonraki plan, payment initiate/reconcile
  akışlarının Deal-status okumasını aynı Deal lock sırasına almalı ve completion
  yarışını test etmelidir.

**Founder amendment (2026-07-23) — Ratified evidence policy:**

- Her ratified package'ın etkili bir evidence policy'si vardır:
  `REQUIRED | NOT_REQUIRED`.
- Schema v1 ve v2 package'lar kalıcı olarak `REQUIRED` yorumlanır; bu
  compatibility'tir, yeni rıza icadı değildir. Orijinal seller upload /
  buyer-ADMIN accept-reject actor modeli (§2.2) ve immutable evidence history
  (§2.3) `REQUIRED` için değişmez.
- Schema v3 package'lar immutable `evidencePolicy` taşır; policy değişikliği
  yeni package/version ve yeniden ratification gerektirir.
- `REQUIRED` mevcut evidence state machine'ini ve §2.5'teki file-backed accept
  sınırını korur.
- `NOT_REQUIRED` started fulfillment'da buyer legal entity `ADMIN`'in
  EvidenceSubmission olmadan explicit no-file acceptance ile fulfillment'ı
  `COMPLETED` yapmasına izin verir. Seller start ile no-file acceptance ayrı
  explicit action'lardır; start otomatik completion üretmez.
- No-file acceptance §2.5 completion sınırına tabidir: Deal `ACTIVE` kalır;
  Deal COMPLETED, settlement, release, payout, refund, provider çağrısı veya
  AI job üretmez. Actor modeli genişlemez: yalnız buyer `ADMIN`; seller
  self-accept ve buyer `MEMBER` accept yoktur.

### 2.6 Contract, concurrency, transaction ve modül sınırı

- Public Core API yüzeyi implementasyondan önce committed OpenAPI'de additive
  tasarlanır. Existing closed `DealDetail` ve action projection'larına yeni
  üyeler optional eklenir; absent/unknown action frontend'de false/read-only
  kabul edilir.
- Start, finalize ve accept/reject duplicate side-effect riski nedeniyle
  server-side `Idempotency-Key` kullanır. Mutable target action'ları
  `expectedVersion` taşır; stale veya state conflict 409'dur.
- Upload presign, storage verify ve download sırasında DB transaction açık
  tutulmaz.
- Finalize transaction'ı evidence status/current pointer, fulfillment status,
  audit ve idempotency sonucunu atomik yazar.
- Accept/reject deterministik olarak Deal → fulfillment/milestone → current
  evidence sırasını lock eder. Target version ve current pointer lock altında
  doğrulanır; terminal yarışta yalnız bir karar kazanır.
- `fulfillment`, Deal/payment/ratification/document repository veya JPA
  entity'sini kullanmaz. Consumer-owned port, stable ID ve read projection
  sınırı korunur.
- Yeni persistence forward-only migration kullanır; V15–V19 değiştirilmez.

### 2.7 Deployment ve sonraki-slice sınırı

Slice 12 yalnız local PostgreSQL + MinIO ile kabul edilir. Railway/staging,
production secret'ları, production object-storage provider seçimi, gerçek
payment provider ve provider credential/3DS kurulumu ayrı ve ertelenmiş
çalışmalardır.

Slice 13 Video Analysis, yalnız Slice 12'nin immutable video evidence
version'ını subject olarak kullanabilir. Video sonucu advisory kalır ve §2.5
manual acceptance sınırını aşamaz.

Dispute, casework, mutual ACTIVE cancellation, settlement ve release ayrı
insan-onaylı plan/ADR gerektirir.

## 3. Sonuçlar

- Slice 12 provider ve deployment kararı olmadan uçtan uca geliştirilebilir.
- Video Analysis'in bağlanacağı first-class, immutable evidence identity'si
  oluşur.
- Seller operational work yapabilir; şirketi gelecekte release'e yaklaştıran
  acceptance buyer `ADMIN` ile sınırlıdır.
- Tarafların ratify etmediği AI `deliveryRequirements` değerleri contractual
  obligation'a dönüşmez.
- V1 tek milestone ile dar kalır; çoklu milestone ve settlement semantiği
  sonraki additive kararlara bırakılır.
- Fulfillment completion ile para hareketi arasında otomatik bağ kurulmaz;
  sistem fail-closed kalır.

## 4. Kabul kapıları

- `ACTIVE + FUNDED` önkoşulu server-side ve gerçek browser'da kanıtlanır.
- Seller `ADMIN`/`MEMBER` submit, buyer `ADMIN` review ve read-only actor
  matrisi projection + server enforcement ile testlidir.
- Concurrent/idempotent start tek fulfillment/milestone üretir.
- Direct PUT, verified size/checksum ve immutable object version gerçek private
  storage ile çalışır.
- Concurrent finalize tek current `SUBMITTED` evidence bırakır.
- Reject → replacement history'yi değiştirmeden çalışır.
- Accept/reject yarışı tek terminal sonuç ve stale loser üretir.
- Fulfillment COMPLETED hiçbir Deal COMPLETE, release, settlement, provider
  veya AI side effect'i üretmez.
- Core API contract, validator, backend verify, frontend typecheck/build ve
  çok-actor gerçek-browser kabulü geçer.
