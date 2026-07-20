# Slice 12 — Fulfillment and Evidence

- Durum: ready — insan onayı 20 Temmuz 2026
- Slice sırası: ADR-004 §24 "Fulfillment and Evidence" (bölünmüş
  yol haritasında Slice 12)
- Öncül: kabul edilmiş Slice 11 Funding Foundation; Deal `ACTIVE` ve tek
  FundingUnit `FUNDED`
- Ardıl: Slice 13 Video Analysis
- Contract sınırı: Core API OpenAPI'deki additive fulfillment/evidence yüzeyi
  bu planın onayıyla birlikte review edilir. AI JSON Schema/fixture, AsyncAPI
  ve AI-internal OpenAPI değişiklikleri kapsam dışıdır.
- Deployment sınırı: Railway/staging, environment secret'ları, production
  object-storage seçimi ve gerçek provider kurulumu ayrı ve sonraya bırakılmış
  çalışma hattıdır; hiçbir implementation phase'e dahil değildir.

Bu planın V1 actor, cardinality, state, evidence ve completion kararları
ADR-011 ile bağlayıcıdır. Planın kapsamı ve bütün phase'leri 20 Temmuz 2026
tarihinde insan tarafından onaylanmıştır.

## 1. Amaç ve kullanıcı sonucu

`ACTIVE + FUNDED` bir Deal'de seller tarafındaki kullanıcı fulfillment'ı
başlatır, primary milestone için PDF/DOCX/fotoğraf/video kanıtını doğrudan
private object storage'a yükler ve finalize eder. Buyer tarafının `ADMIN`
kullanıcısı aynı immutable evidence version'ını gerçek browser'da inceler,
indirir ve gerekçeli olarak kabul veya reddeder.

Kabul edilen evidence fulfillment'ı `COMPLETED` yapar. Reddedilen evidence
geçmişte kalır, seller yeni bir version sunar ve iki taraf aynı review
durumunu görür. Diğer Deal participant'ları metadata, geçmiş ve indirme
görünümüne sahiptir; mutation yetkisi participant olmaktan türetilmez.

Bu slice para hareketi üretmez. Fulfillment kabulü Deal `COMPLETED`, settlement,
release, payout veya refund tetiklemez. Video dosyası first-class evidence
olarak saklanabilir, fakat Video Analysis ve bütün AI yorumları Slice 13'tedir.

## 2. Kapsam ve sınırlar

### Kapsam

- `fulfillment` owning modülü: Deal başına V1 fulfillment kaydı, tek primary
  milestone, evidence submission/history ve manual review sonucu
- `FulfillmentStatus` V1 akışı ve merkezi Deal lifecycle projection'ı
- FUNDED önkoşulunun `payment` modülünden dar read port ile doğrulanması
- Seller tarafından explicit, idempotent fulfillment start action'ı
- Evidence upload intent → browser direct PUT → storage-verified finalize
- Immutable object version, size, SHA-256 ve media/evidence type metadata'sı
- Participant-readable fulfillment detail, evidence history ve kısa ömürlü
  download link
- Buyer `ADMIN` accept/reject action'ları; reject reason ve stale-version
  recovery
- Audit ve HTTP idempotency'nin ilgili business mutation ile atomik olması
- Deal detail'e optional fulfillment özeti ve actor-aware action'lar
- Yeni forward-only migration'lar; V15–V19'a dokunulmaması

### Kapsam dışı

- Deal `ACTIVE → COMPLETED` mutation'ı
- Payment release, payout, refund, reversal, settlement veya provider çağrısı
- Dispute/casework, ACTIVE cancellation ve mutual cancellation
- Video Analysis, RabbitMQ event'i, AI result/review veya FastAPI değişikliği
- AI extraction `deliveryRequirements` listesinden otomatik contractual
  milestone/checklist üretmek
- Çoklu milestone, partial milestone acceptance, amount allocation veya
  milestone başına payment/funding unit
- Buyer/seller'ın ratified package sonrasında yeni contractual obligation
  eklemesi; fulfillment tracking ratified package'ı değiştirmez
- Evidence silme/overwrite; antivirus/malware scanning ve OCR
- Public bucket, Core API üzerinden binary proxy upload veya production
  object-storage provider seçimi
- Railway/staging config'i, deploy pipeline, production secret/credential
  kurulumu ve gerçek payment provider entegrasyonu

## 3. Kararlar ve ilgili ADR'ler

### Kabul edilmiş kararlar

- `fulfillment` milestone, evidence evaluation ve status'un sahibidir
  (ADR-003 §4.7).
- Evidence first-class business kaydıdır; Deal ve milestone'a bağlanır. Binary
  object storage'da, authoritative metadata ve business state Spring'de kalır
  (ADR-001 §4.1, §6; ADR-003 §22).
- `FulfillmentStatus`, `DealStatus`, funding ve settlement'tan ayrı state
  eksenidir. Frontend lifecycle/action hesaplamaz (ADR-003 §8, §13, §16, §29;
  ADR-006 §33, §41).
- AI/video sonucu otomatik fulfillment completion, dispute veya payment
  release üretemez (ADR-003 §13, §22; FORBIDDEN §1).
- Modüller repository/JPA entity paylaşmaz; port, ID, event veya read
  projection kullanır (ADR-003 §23).
- Business mutation + audit + gerekiyorsa outbox aynı transaction'dadır;
  object storage çağrısında transaction açık tutulmaz (ADR-003 §24).
- Evidence mutation'larında actor + tenant + active legal entity + Deal +
  operation bağlamı application katmanında doğrulanır. Deal visibility yalnız
  participant ilişkisine dayanır (ADR-005 §20–§21; ADR-008 §2.4).
- Public API contract-first ve additive ilerler; riskli mutation'lar
  `expectedVersion` ve `Idempotency-Key` kullanır (ADR-006 §21–§25, §42–§47).

### ADR-011 ile bağlayıcı V1 kararları

Aşağıdaki kararlar **ADR-011 — Fulfillment and Evidence V1** ile bağlayıcıdır:

1. **Actor modeli:** seller legal entity'nin `ADMIN` ve `MEMBER` kullanıcıları
   fulfillment başlatabilir ve evidence sunabilir. Evidence acceptance/rejection
   gelecekte release eligibility'ye girdi olacağı için yalnız buyer legal
   entity `ADMIN` kullanıcısına aittir. Buyer `MEMBER`, initiator-only entity
   ve diğer participant'lar salt-okunurdur.
2. **V1 cardinality:** Deal başına tek fulfillment ve onun altında tek primary
   milestone vardır. Start action bu iki kaydı atomik oluşturur ve immutable
   current ratification package kimliğine bağlar. Çoklu milestone sonraki
   additive slice'tır.
3. **Contractual kaynak:** milestone ratified package'a ve varsa package'taki
   `DELIVERY`/`QUALITY` rule reference'larına izlenebilir biçimde bağlanır;
   kuralları yeniden yorumlamaz. AI extraction'daki `deliveryRequirements`
   Slice 9 review/ratification zincirine taşınmadığından required checklist,
   accepted obligation veya otomatik completion girdisi değildir.
4. **Exact V1 state akışı:** kayıt yokken `NOT_STARTED`; seller start ile
   `IN_PROGRESS`; upload intent ile `EVIDENCE_REQUIRED`; storage-verified
   finalize ile `REVIEW_REQUIRED`; buyer reject ile `EVIDENCE_REQUIRED`;
   buyer accept ile `COMPLETED`. `CANCELLED` V1 public state setinde korunabilir
   fakat bu slice'ta ona götüren action yoktur.
5. **Evidence history:** her upload ayrı immutable object version'lı
   `EvidenceSubmission` olur. V1 submission akışı
   `PENDING_UPLOAD → SUBMITTED → ACCEPTED | REJECTED` şeklindedir. Reject edilen
   kayıt değişmez/silinmez; yeni submission yeni ID/object version ile gelir.
6. **Completion sınırı:** buyer acceptance yalnız fulfillment ve milestone
   completion üretir. Deal `ACTIVE` kalır, lifecycle bu slice boyunca
   `FULFILLMENT` gösterir ve “settlement henüz kullanılamıyor” durumu açıkça
   sunulur. Deal `COMPLETED` ve payment release sonraki insan-onaylı planlara
   aittir. Bu nedenle payment initiate/reconcile Deal-status okumasını Deal
   lock'una alma borcu bu slice'ta tetiklenmez; Deal `COMPLETE` mutation'ını
   açan sonraki plan bu yarışı ve lock sırasını zorunlu olarak üstlenir.
7. **Başlangıç evidence türleri:** `DELIVERY_NOTE`, `INVOICE`, `VIDEO`,
   `PHOTO`, `SIGNED_DOCUMENT`, `OTHER`; desteklenen ilk media türleri PDF,
   DOCX, JPEG, PNG ve MP4'tür. Boyut limitleri media sınıfına göre config'te
   tutulur ve OpenAPI'de davranış olarak belgelenir; `UNKNOWN` kullanıcı
   submission türü değildir.

## 4. Public interface, state ve data etkisi

### Bağlayıcı Core API davranış yüzeyi

OpenAPI implementasyondan önce aşağıdaki use-case yüzeylerini tanımlar; exact
DTO alan listeleri contract phase'inde review edilir:

- Deal fulfillment detail read: status, source ratification package, primary
  milestone, current evidence, immutable history ve actor-aware actions
- Seller fulfillment start action: Deal `expectedVersion` +
  `Idempotency-Key`; synchronous create sonucu ve `Location`
- Milestone evidence upload intent: declared evidence/media metadata, size ve
  client SHA-256; direct PUT için kısa ömürlü URL
- Evidence finalize action: milestone/evidence expected version +
  `Idempotency-Key`; storage verification sonucu current submission
- Evidence history ve kısa ömürlü download-link read yüzeyleri
- Buyer `ADMIN` accept ve reject action'ları: target submission/milestone
  expected version + `Idempotency-Key`; reject için kullanıcıya gösterilebilir
  bounded reason
- Deal detail'e optional fulfillment summary ve optional action üyeleri;
  eksik/bilinmeyen action frontend'de `false`/read-only

Sabit HTTP ve disclosure davranışı:

- Session yok → 401
- Active entity membership'i yok veya görünür Deal'de operation yetkisi yok →
  403
- Nonparticipant, başka Deal'e ait milestone/evidence veya gizli kaynak →
  varlığı açıklamayan (non-disclosing) 404
- Parse edilebilir fakat geçersiz evidence metadata/size/type/reject reason →
  field-level 422
- ACTIVE olmayan, FUNDED olmayan, stale version, terminal fulfillment veya
  uygun olmayan current evidence → stable code'lu 409
- Aynı idempotency key + aynı canonical request → ilk/eşdeğer sonuç; aynı key +
  farklı request → 409 `IDEMPOTENCY_KEY_REUSED`

Compatibility:

- Yeni path/schema'lar additive olur.
- Closed `DealDetail` ve `DealAvailableActions` içindeki yeni fulfillment
  üyeleri optional olur; mevcut required listeleri ve alan semantiği değişmez.
- OpenAPI değişikliği, validator exact allowlist/response/closed-shape
  beklentileri, `contracts/README.md` ve `contracts/CHANGELOG.md` ile tek review
  birimidir.
- AI JSON Schema/fixture, AsyncAPI ve AI-internal OpenAPI değişmez.

### Persistence ve transaction etkisi

- V15–V19 frozen history'dir; fulfillment tabloları ve bütün constraint'ler
  yeni versioned migration ile, mevcut zincirde V20 veya sonrasından eklenir.
- DB seviyesinde en az Deal başına tek fulfillment, V1 fulfillment başına tek
  primary milestone, evidence'ın aynı Deal/milestone'a ait olması, immutable
  object key/version ve geçerli status/type invariant'ları korunur.
- Bir milestone üzerinde aynı anda yalnız bir current `SUBMITTED` evidence
  review bekleyebilir. Concurrent finalize yarışında tek current submission
  kazanır; diğer işlem stale/state conflict alır.
- Upload intent/presign ve storage size/checksum/version verification DB
  transaction'ı dışında çalışır. Finalize transaction'ı evidence'ı SUBMITTED
  yapar, milestone/current pointer'ı ve FulfillmentStatus'u günceller, audit ve
  idempotency sonucunu birlikte yazar.
- Accept/reject transaction'ı deterministik olarak Deal → fulfillment/
  milestone → current evidence sırasını lock eder; target expected version ve
  current pointer lock altında doğrulanır. Terminal yarışın yalnız bir sonucu
  olur.
- `fulfillment`, Deal/ratification/payment/document repository veya entity'sini
  kullanmaz. FUNDED ve immutable ratification provenance consumer-owned dar
  source port'larla okunur. Storage adapter, fulfillment-owned bir portu
  uygular; document aggregate'i evidence için yeniden kullanılmaz.
- `ModuleArchitectureTest` fulfillment ownership'ini ve yeni dependency
  yönlerini cycle/repository paylaşımı açısından kilitler.

## 5. Implementation phases

### P1 — Reviewed Core API contract

Outcome:
Fulfillment/evidence kullanıcı akışını, actor-aware action'ları, state ve error
semantiğini taşıyan additive committed OpenAPI review edilebilir ve validator
ile kilitlidir.

Direction:

- Yalnız Core API public contract değişir.
- Path/schema/response tasarımıyla exact validator beklentileri,
  `contracts/README.md` ve `contracts/CHANGELOG.md` aynı review birimidir.
- AI contract dosyalarında değişiklik yoktur.
- Farklı Deal'e ait nested resource disclosure ve stale/idempotency davranışı
  contract açıklamalarında açık olur.

Depends on:
None — ADR-011 ve plan onayı tamamlanmış önkoşuldur

Exit checks:

- Contract validator yeni yüzeyi ve expected-invalid kontrolleriyle geçer.
- Generated frontend tipleri contract'tan üretilebilir; paralel elle yazılmış
  model yoktur.

### P2 — Fulfillment ownership and persistence foundation

Outcome:
Yeni fulfillment modülü, forward-only migration ve dar source/storage port
sınırlarıyla tek plan/tek milestone state'ini PostgreSQL'de korur.

Direction:

- V15–V19 değiştirilmez.
- Unique, same-Deal/milestone, status, immutable object reference ve optimistic
  version invariant'ları DB/application seviyesinde bölüştürülür.
- Payment/Deal/ratification verisi repository paylaşmadan consumer-owned port
  ile okunur.
- Module architecture listesi fulfillment ile genişletilir.

Depends on:
P1

Exit checks:

- Migration temiz veritabanında ve mevcut migration zinciri üstünde uygulanır.
- Persistence invariant ve module-cycle testleri geçer.

### P3 — Start and participant-readable fulfillment vertical

Outcome:
Seller kullanıcı gerçek API'den idempotent start yapar; bütün participant'lar
aynı fulfillment/milestone projection'ını görür.

Direction:

- Start yalnız `ACTIVE + FUNDED` Deal'de ve seller entity adına çalışır.
- Deal başına tek fulfillment/milestone yarışta DB unique invariant'ıyla
  korunur; replay aynı sonucu verir.
- Source ratification package ve ilgili accepted rule reference'ları
  traceability olarak tutulur, contractual içerik yeniden yorumlanmaz.
- Frontend ilk loading/error/empty/read-only ve start action durumlarını gerçek
  API üzerinden sunar.

Depends on:
P2

Exit checks:

- Seller `ADMIN`/`MEMBER`, buyer ve diğer participant authorization matrisi
  server-side testlidir.
- Concurrent/replayed start tek fulfillment ve tek milestone üretir.

### P4 — Direct evidence upload, history and download vertical

Outcome:
Seller browser'dan private object storage'a evidence yükler, verified finalize
eder; participant'lar immutable history'yi görür ve indirir.

Direction:

- Slice 6'nın intent → direct PUT → verify → finalize deseni davranış emsalidir;
  evidence ayrı aggregate ve ayrı object key namespace'inde kalır.
- Object version, size ve checksum doğrulaması storage-authoritative olur.
- Pending/expired intent current olamaz; physical orphan cleanup bu slice'ın
  Done koşulu değildir.
- Finalize status/current pointer/audit/idempotency atomikliğini korur.
- Frontend progress, retry, expired-intent recovery, type/size/checksum
  hataları ve read-only participant görünümünü işler.

Depends on:
P3

Exit checks:

- PDF/DOCX/JPEG/PNG/MP4 için izin verilen media/type matrisi testlidir.
- Concurrent finalize tek current SUBMITTED evidence bırakır.
- Download immutable object version'a pinlidir; başka Deal referansı reddedilir.

### P5 — Buyer review and fulfillment completion vertical

Outcome:
Buyer `ADMIN` current evidence'ı kabul veya gerekçeli reddeder; seller
replacement sunabilir ve terminal fulfillment sonucu iki tarafta görünür.

Direction:

- Accept/reject yalnız current SUBMITTED evidence'a ve expected version'a karşı
  çalışır.
- Reject history'yi korur ve fulfillment'ı EVIDENCE_REQUIRED'a döndürür; yeni
  upload yeni submission olur.
- Accept milestone/fulfillment'ı COMPLETED yapar ancak Deal status, payment,
  settlement veya outbox external effect üretmez.
- Frontend stale conflict'te refetch eder; backend action projection'ından
  hareket eder.

Depends on:
P4

Exit checks:

- Accept ↔ reject ve accept ↔ stale finalize yarışlarında tek geçerli sonuç
  vardır.
- Buyer MEMBER/seller/diğer participant review yapamaz.
- Completion sonrasında bütün mutation action'ları fail-closed olur.

### P6 — Real-browser acceptance and regression hardening

Outcome:
Çok-actor gerçek browser akışı PostgreSQL ve MinIO ile kanıtlanır; Slice 11 ve
önceki Deal akışları regresyonsuzdur.

Direction:

- Browser kabulü §6 matrisini izler.
- Yalnız material state/authorization/concurrency riskleri otomatik testle
  güçlendirilir; adapter için geniş test matrisi kurulmaz.
- Kabul sırasında payment release, Deal COMPLETE veya AI event'i oluşmadığı
  veri/audit görünümüyle doğrulanır.

Depends on:
P5

Exit checks:

- §6 ve §7 kontrolleri geçer.
- Contract validator, Core API verify, frontend typecheck/build ve
  `git diff --check` yeşildir.

## 6. Gerçek browser kabulü

PostgreSQL + MinIO ile en az üç browser context kullanılır: seller `MEMBER`,
buyer `ADMIN`, buyer `MEMBER` veya başka salt-okunur participant.

1. Ratified `ACTIVE` Deal sandbox funding ile `FUNDED` yapılır; lifecycle
   `FULFILLMENT` görünür. DRAFT/ACTIVE-but-not-FUNDED Deal'de start action'ı
   görünmez ve zorlanan istek 409 alır.
2. Seller `MEMBER` fulfillment'ı başlatır. Double-click/same-key replay tek
   fulfillment ve tek primary milestone üretir; buyer aynı durumu görür.
3. Seller PDF veya fotoğraf evidence için intent alır, browser doğrudan MinIO'ya
   PUT eder ve finalize eder. Durum REVIEW_REQUIRED olur; refresh sonrası
   current evidence kalır.
4. Buyer ve diğer participant metadata/history'yi görür, kısa ömürlü link ile
   exact immutable object version'ı indirir. Nonparticipant görünürlük
   kazanmaz.
5. Buyer `MEMBER` accept/reject kontrolü görmez ve zorlanan istek 403 alır.
   Buyer `ADMIN` gerekçeyle reject eder; kayıt REJECTED history olarak kalır ve
   durum EVIDENCE_REQUIRED olur.
6. Seller replacement evidence yükler; eski evidence değişmez/silinmez, yeni
   submission REVIEW_REQUIRED olur.
7. Buyer `ADMIN` iki tab/context ile accept ↔ reject yarışını çalıştırır. Tek
   terminal sonuç oluşur; kaybeden stale/state 409 alır. Same-key retry ikinci
   karar üretmez.
8. Kabul kazandığında milestone ve fulfillment COMPLETED görünür. Deal hâlâ
   ACTIVE, lifecycle FULFILLMENT ve settlement kullanılamazdır; payment
   release/refund/payout kaydı veya external çağrı oluşmaz.
9. Ayrı bir Deal'de MP4 evidence yüklenir ve participant'lar indirebilir; hiçbir
   video analysis job'ı otomatik oluşmaz.
10. Seller dışındaki participant upload/start yapamaz; buyer dışındaki actor
    review yapamaz. Initiator buyer/seller değilse initiator kimliği ek
    fulfillment mutation yetkisi sağlamaz.
11. Document, ratification ve funding read/mutation regresyonları ilgili actor
    sınırlarıyla çalışmaya devam eder.

## 7. Minimum invariant ve validation

Minimum otomatik invariant'lar:

- Yalnız `ACTIVE + FUNDED` Deal'de start; FUNDED status'u frontend veya
  caller input'ından kabul edilmez
- Deal başına concurrent/idempotent start → tek fulfillment + tek milestone
- Seller `ADMIN`/`MEMBER` submit; buyer `ADMIN` review; buyer MEMBER, yanlış
  party, initiator-only ve nonparticipant retleri
- Evidence mutlaka aynı Deal ve milestone'a bağlı; cross-Deal current pointer
  DB/application tarafından reddedilir
- Storage-verified size/checksum/media mismatch reddi; immutable object version
  ve private bucket davranışı
- Presign/verify/download sırasında açık DB transaction olmaması
- Finalize mutation + current pointer + status + audit + idempotency
  atomikliği; rollback'te kısmi current evidence kalmaması
- Concurrent finalize → tek current SUBMITTED; accept/reject stale target ve
  expected version enforcement
- Accept ↔ reject yarışı → tek terminal karar; replay/different-request
  idempotency davranışı
- Rejected/accepted evidence history'nin silinmemesi veya overwrite edilmemesi
- Fulfillment COMPLETED'ın Deal COMPLETED, settlement/release/refund,
  PaymentOperation veya AI job üretmemesi
- Video/AI sonucunun bu slice'ta fulfillment state değiştirecek yolu olmaması
- Backend-derived lifecycle/action; absent optional action frontend'de
  false/read-only
- `ModuleArchitectureTest` fulfillment repository/entity ownership ve cycle
  kontrolü

Zorunlu doğrulama:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm run typecheck
npm run build

Set-Location ..
docker compose -f .\infra\compose.yaml config
git diff --check
```

§6 gerçek browser akışı otomatik komutların yerine geçmez; otomatik testler de
gerçek direct PUT/download ve çok-actor kabulünün yerine geçmez.

## 8. Done tanımı

- [x] ADR-011 actor, cardinality, contractual source, state ve completion
      kararları insan tarafından kabul edildi; ADR index/FORBIDDEN türevleri
      güncellendi
- [ ] P1 additive Core API contract yüzeyi implementasyondan önce review edildi;
      validator exact beklentileri ve contracts README/CHANGELOG aynı birimde
      güncellendi; AI contract'ları değişmedi
- [ ] Yeni forward-only migration'lar uygulandı; V15–V19 değişmedi
- [ ] Fulfillment owning modülü, dar source/storage port'ları ve
      `ModuleArchitectureTest` sınırı çalışıyor
- [ ] ACTIVE + FUNDED start, tek fulfillment/tek milestone ve exact
      FulfillmentStatus akışı uygulanmış
- [ ] Direct private-storage evidence upload/finalize verified size, checksum
      ve immutable object version ile çalışıyor
- [ ] Evidence history/download participant-readable; mutation yetkileri
      party/role bazında server-side uygulanıyor
- [ ] Buyer ADMIN accept/reject, rejection replacement, stale recovery,
      idempotency ve terminal yarışlar testli
- [ ] Fulfillment COMPLETED hiçbir Deal COMPLETE, release, settlement, refund,
      provider veya AI side effect'i üretmiyor
- [ ] Deal detail lifecycle/action projection'ı backend-derived; frontend
      loading/error/empty/read-only durumları gerçek API'ye bağlı
- [ ] §7 minimum invariant testleri ve zorunlu komutlar yeşil
- [ ] §6 çok-actor gerçek browser akışı PostgreSQL + MinIO ile tamamlandı;
      önceki slice regresyonları geçti
