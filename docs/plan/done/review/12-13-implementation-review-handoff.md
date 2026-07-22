# Slice 12–13 Implementation Review Handoff

Bu doküman, kabul edilmiş Slice 12 Fulfillment/Evidence ve Slice 13 Video
Analysis implementasyonunu inceleyecek ajana tek başlangıç noktası sağlar.
[08–11 handoff'unun](08-11-implementation-review-handoff.md) devamıdır; planların,
Contracts'ın veya ADR'lerin yerine geçmez. Çelişkide Accepted ADR ve committed
contract kazanır.

## 1. İnceleme başlangıç noktası

- Slice 12 kabulü: `codex/slice-12-fulfillment@a21a75e`, 20 Temmuz 2026.
- Slice 13 kabulü: `codex/slice-13-video-analysis@cdfb97a`, 21 Temmuz 2026.
- Kabul planları:
  - [`12-fulfillment-and-evidence.md`](../12-fulfillment-and-evidence.md)
  - [`13-video-analysis.md`](../13-video-analysis.md)
- Ana kararlar: ADR-001–ADR-006 ile özellikle ADR-011 ve ADR-012.
- Migration sınırı: V20 Slice 12, V21 Slice 13'tür; V1–V21 frozen accepted
  history'dir.
- Eski ayrı Slice 12 ve Slice 13 kabul kayıtlarının doğrulama, sapma ve browser
  kanıtları bu dosyada birleştirilmiştir.
- Güncel proje durumu: [`docs/plan/CURRENT.md`](../../CURRENT.md).

## 2. Slice'lar arası oluşan omurga

Slice 12–13, accepted ratification ve funding omurgasını teslimat kanıtı ile
advisory video analizine bağlar:

```text
ACTIVE + FUNDED Deal
  -> seller starts one V1 fulfillment / primary milestone
  -> browser uploads immutable evidence directly to private storage
  -> Core verifies exact object version and finalizes SUBMITTED evidence
  -> buyer ADMIN accepts or rejects manually
  -> eligible current VIDEO/MP4 may receive an explicit analysis job
  -> outbox -> RabbitMQ -> local Mock AI Worker -> inbox/result router
  -> participant-safe advisory projection only
```

Kalıcı sınırlar:

1. Evidence bytes Core üzerinden proxy edilmez; presign ve storage verification
   DB transaction'ı dışında çalışır.
2. Finalize ve buyer kararı, audit/idempotency/current-pointer değişimleriyle
   birlikte atomiktir.
3. Evidence geçmişi append-only, object key/version kimliği immutable'dır.
4. AI işi exact evidence identity/version/hash/size'a bağlanır.
5. AI sonucu advisory'dir; evidence veya başka business state üzerinde karar
   vermez.
6. Frontend actor/action bilgisini backend projection'ından alır.

## 3. Slice 12 — Fulfillment and Evidence

### Gerçekleşen kullanıcı sonucu

- `ACTIVE + FUNDED` Deal'de seller kullanıcısı tek V1 fulfillment ve primary
  milestone başlatır.
- Deal participant'ları fulfillment detail ve immutable evidence history'yi
  okuyabilir.
- Seller doğrudan private MinIO'ya PDF, DOCX, JPEG, PNG veya MP4 yükler; Core
  size, media type, SHA-256 ve exact object version'ı doğrular.
- Buyer legal-entity `ADMIN` current evidence'ı kabul veya gerekçeyle reddeder.
- Ret yeni immutable evidence yüklemesine alan açar; eski kayıt korunur.
- Kabul primary milestone ve fulfillment'ı `COMPLETED` yapar; Deal `ACTIVE`
  kalır ve lifecycle `FULFILLMENT` olur.
- Fulfillment completion release, settlement, refund, provider-payment,
  dispute veya AI side effect üretmez.

### Public API

- `POST /deals/{dealId}/fulfillment`
- `GET /deals/{dealId}/fulfillment`
- `POST /deals/{dealId}/fulfillment/evidence/upload-intents`
- `POST /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/finalize`
- `GET /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/download-link`
- `POST /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/accept`
- `POST /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/reject`

OpenAPI server base'i `/api/v1`'dir. Riskli mutation'lar committed contract'taki
`Idempotency-Key` ve `expectedVersion` kurallarını uygular.

### Persistence ve başlıca yüzeyler

- Migration: `V20__fulfillment_and_evidence.sql`.
- Backend ownership: `fulfillment` modülü; Deal, payment, ratification ve
  storage bilgisine consumer-owned dar portlarla erişir.
- Storage identity: unique object key + immutable `versionId`; aynı Deal/
  milestone/evidence ilişkileri composite DB constraints ile korunur.
- Lock sırası: Deal → fulfillment/milestone → current evidence.
- Frontend: Deal detail içindeki seller start, evidence upload/history/download
  ve buyer review paneli.

### Test ve kabul kanıtı

- Contract validator geçti.
- Implementer full Core verify: 250 test, 0 failure/error.
- Planner focused `FulfillmentServiceTest`, `FulfillmentIntegrationTest` ve
  `FulfillmentMigrationIntegrationTest`: 17 test geçti.
- Frontend typecheck ve production build, Compose config ve `git diff --check`
  geçti.
- Gerçek browser minimum kabulü izole PostgreSQL, local sandbox payment ve real
  MinIO ile şu kritik yolu tamamladı:
  ratify → fund → seller start → direct PDF upload/finalize → buyer reject →
  replacement upload → buyer accept.
- İlk finalize denemesinde idempotency key'i yalnız retry yolunda oluşturan
  frontend hatası browser kabulünde bulundu, düzeltildi ve kritik yol yeniden
  başarıyla çalıştırıldı.

### Kabul sapması

Kullanıcı yönlendirmesiyle seller MEMBER, buyer MEMBER, başka participant,
stale/replay ve terminal-race senaryolarının tamamı browser'da tekrarlanmadı.
Accepted server integration suite bu invariant'ları kapsar. Bu kayıt browser
kanıtı varmış gibi genişletilmemelidir.

### Review odağı

- Start/upload/review authorization'ının participant visibility ile
  karıştırılmadığını kontrol edin.
- Storage verify'ın transaction dışında, finalize ve review kararının doğru
  lock sırası ile transaction içinde olduğunu izleyin.
- Current evidence pointer'ın aynı Deal/milestone'a ait olduğunu ve concurrent
  finalize/review'da yalnız bir terminal kazanan kaldığını doğrulayın.
- Rejected/accepted geçmişin overwrite edilmediğini ve download link'in exact
  version'a pinli olduğunu kontrol edin.
- Completion'ın Deal, payment veya release state'ini değiştirmediğini doğrulayın.

## 4. Slice 13 — Video Analysis

### Gerçekleşen kullanıcı sonucu

- Buyer legal-entity `ADMIN`, yalnız current ve finalized `VIDEO`/`video/mp4`
  evidence için analizi açıkça request/retry eder; otomatik job yoktur.
- Bütün Deal participant'ları current ve historical evidence üzerindeki safe
  status/result projection'ını okuyabilir.
- Job exact evidence id, object version, SHA-256, size, Deal hosting tenant,
  fulfillment ve milestone kimliğine bağlanır.
- Request persistence, audit, HTTP idempotency ve outbox enqueue aynı
  transaction'dadır.
- Shared result router schema validation, inbox idempotency, first-terminal-wins
  ve immutable canonical result history uygular.
- Local-only Mock AI Worker version-pinned MinIO object'ini indirip size/hash
  doğrular ve deterministic success/warning/failure/duplicate senaryoları üretir.
- Public projection canonical provider payload'ını değil yalnız contract'ta
  izin verilen safe advisory alanları gösterir.

### Public API

- `GET /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis`
- `POST /deals/{dealId}/fulfillment/evidence/{evidenceSubmissionId}/video-analysis`

OpenAPI server base'i `/api/v1`'dir. Mutation buyer-ADMIN-only, read ise
participant-scoped ve non-disclosing'dir.

### Persistence, messaging ve başlıca yüzeyler

- Migration: V21.
- Fulfillment-owned video-analysis job/result persistence; document analysis
  ile ortak result queue/router kullanılır.
- Accepted JSON Schema/AsyncAPI event'leri değişmeden kullanılır.
- At-least-once delivery; inbox ve terminal guards duplicate-safe davranır.
- Frontend current ve historical VIDEO/MP4 kartlarında queued polling,
  retry/failure ve advisory result gösterir.

### Review sırasında bulunan ve düzeltilen sorunlar

- Canonical result saklanırken safe public projection'ın doğru filtrelenmesi.
- Cross-tenant test fixture'ında hosting tenant/composite FK doğruluğu.
- Media type wire serialization'ının contract enum/string biçimi.
- Rejected historical VIDEO/MP4 evidence üzerinde advisory panelin korunması.
- Manual-review ile terminal result yarışlarında AI sonucunun business state
  değiştirmemesi.

### Test ve kabul kanıtı

- Contract validator, generated types ve invalid fixtures geçti.
- Implementer Core verify: 292 test; focused Slice 13 matrix: 65 test.
- Planner focused regression: 17 test.
- Mock AI Worker: 27 test.
- Frontend typecheck/build, Compose config ve `git diff --check` geçti.
- Gerçek browser kabulü PostgreSQL, RabbitMQ, MinIO, rebuilt Mock AI Worker,
  Core ve frontend ile yapıldı. Cross-tenant visibility, actor matrix,
  queue/result/failure/retry/duplicate, manual-review race, replacement
  isolation, exact hash ve no-side-effect davranışı geçti.
- Kapanışta yalnız historical VIDEO/MP4 panelinin son post-fix browser gözlemi
  kullanıcı yönlendirmesiyle devredilmişti. Bu borç daha sonra Gate C0'da gerçek
  browser ile kapatıldı; kanıt
  [`14A–15 P4 handoff`](14a-15p4-implementation-review-handoff.md) içindedir.

### Review odağı

- Job subject identity'sinin current pointer'dan yeniden türetilmediğini, exact
  immutable evidence version'da kaldığını kontrol edin.
- Queue publish'in outbox üzerinden, result apply'ın schema+inbox+terminal guard
  üzerinden ilerlediğini doğrulayın.
- Participant read ile buyer-ADMIN request authority ayrımını ve hidden
  resource 404 davranışını kontrol edin.
- Safe DTO'nun raw/canonical provider payload sızdırmadığını doğrulayın.
- Result'ın accept/reject, fulfillment completion, Deal, payment, dispute veya
  release mutation'ı üretmediğini kontrol edin.

## 5. Contract, persistence ve frontend çapraz kontrol tablosu

| Slice | OpenAPI | Migration | Backend ownership | Frontend yüzeyi |
|---|---|---|---|---|
| 12 | fulfillment/evidence lifecycle | V20 | `fulfillment` + storage port | start, upload, history, review |
| 13 | per-evidence video analysis | V21 | fulfillment-owned job/result + shared messaging | current/historical advisory panel |

## 6. Mimari çapraz review odağı

1. Deal visibility, seller mutation, buyer review ve buyer analysis authority
   aynı kavram değildir.
2. Object identity database, presigned URL ve worker input'unda aynı key/version/
   hash/size değerleriyle korunmalıdır.
3. External storage ve broker I/O açık DB transaction içinde olmamalıdır.
4. HTTP idempotency, outbox/inbox idempotency ve optimistic concurrency farklı
   problemleri çözer; biri diğerinin yerine geçmez.
5. Frontend status'tan action icat etmemeli, backend `availableActions`
   projection'ını kullanmalıdır.
6. AI'nın teknik başarısı business acceptance değildir.

## 7. Önerilen review sırası

1. ADR-011, ADR-012 ve ilgili OpenAPI yollarını okuyun.
2. V20–V21 migration/invariant zincirini inceleyin.
3. Fulfillment start → upload intent → finalize → accept/reject transaction ve
   lock sınırlarını takip edin.
4. Video request → outbox → worker → result router → inbox/public projection
   zincirini exact evidence identity ile izleyin.
5. Authorization/non-disclosure ve no-side-effect testlerini karşılaştırın.
6. Frontend current/historical evidence davranışını generated types üzerinden
   inceleyin.
7. Şüphe varsa yalnız ilgili focused testi çalıştırın; tarihsel full suite'i
   rutin olarak tekrarlamayın.

## 8. Kabul sınırları ve sonradan kapanan borç

- Slice 12/13 production deployment, real payment provider, settlement/release,
  dispute/casework ve broad-production readiness sağlamaz.
- Slice 13 kabulü yalnız local Mock AI Worker kanıtı taşır; AI provider/model/
  production deployment iddiası değildir.
- Historical VIDEO/MP4 panel gözlem borcu Gate C0 ile kapanmıştır; Slice 13'ün
  diğer kabul kapsamı veya tarihsel sapması yeniden yazılmamıştır.
- V20 ve V21 değiştirilemez; yeni persistence ihtiyacı forward-only migration
  kullanır.
