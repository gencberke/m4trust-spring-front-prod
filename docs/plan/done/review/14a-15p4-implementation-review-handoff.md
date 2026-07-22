# Slice 14A–15 P4 Implementation Review Handoff

Bu doküman, Slice 13 sonrasında 22 Temmuz 2026'ya kadar tamamlanan ve yeniden
planlanan işleri tek kronolojide toplar. Amaç, farklı acceptance/gate/replan
dosyalarına dağılmış proje durumunu okunabilir kılmaktır. Accepted ADR'lerin,
committed Contracts'ın ve arşivlenmiş planların yerine geçmez.

En önemli durum ayrımı:

| Alan | Durum |
|---|---|
| Slice 14A Dispute/Casework | `ACCEPTED` |
| Gate C0 — 14A browser matrix + Slice 13 historical panel | `ACCEPTED` |
| Slice 11B-A local/CI Moka transport foundation | `ACCEPTED`, simulation-only destek |
| ADR-014–ADR-022 | `ACCEPTED` |
| Slice 15 P1 error authority | `ACCEPTED` |
| Slice 15 P2 contract bundle/digest | `ACCEPTED` |
| Slice 15 P3 observable runtime inventory/gates | `ACCEPTED` |
| Slice 15 P4 Railway-demo repository reconciliation | `ACCEPTED` |
| User-owned UI/UX insertion gate | `ACTIVE / NOT YET ACCEPTED` |
| Slice 15 P5 staging reconciliation | `NOT IMPLEMENTED` |
| Slice 15 P6 final gate + production demo deploy | `NOT IMPLEMENTED` |
| Slice 14B settlement/release | `PLANNING ONLY` |

## 1. İnceleme başlangıç noktası

- Slice 14A implementation: `feature/14a-dispute-casework-foundation@e30c185`,
  merged as `main@0282c0e`.
- Gate C0 local browser acceptance: 21 Temmuz 2026.
- Slice 11B-A accepted main baseline: `main@7e773d9`.
- Slice 15 accepted checkpoints:
  - P1: `d69d7e00`
  - P2–P3: `4845a03`; planner/workflow closure `5051860`
  - ADR-022 + Railway-demo replan: `09fca0a`
  - P4 final: `381ed5b`
- Planlar:
  - [`14a-dispute-and-casework-foundation.md`](../14a-dispute-and-casework-foundation.md)
  - [`11b-a-moka-provider-foundation.md`](../11b-a-moka-provider-foundation.md)
  - [`15-railway-demo-reconciliation-and-deployment.md`](../15-railway-demo-reconciliation-and-deployment.md)
- Ana kararlar: ADR-013–ADR-022; özellikle ADR-019, ADR-021 ve ADR-022.
- Migration sınırı: V22 Slice 14A'dır; V1–V22 frozen accepted history'dir.

## 2. 13 sonrası kronoloji ve neden yeniden planlandı

1. Slice 14A dispute/casework foundation uygulandı ve kabul edildi.
2. Kapanışta ertelenen 14A browser matrisi ile Slice 13 historical video panel
   kontrolü Gate C0'da tamamlandı; açık browser acceptance borcu kalmadı.
3. Slice 11B-A, gerçek HTTP sınırını local/CI Moka emulator ile kanıtladı.
   Ardından founder gerçek provider yerine simulation-only yönünü seçti; gerçek
   provider fazı kapandı.
4. Production reconciliation çalışması ADR-014–ADR-020 ve geniş bir plan üretti.
   AI tarafında yetki aşımı fark edilince ADR-019 ile main ekip yalnız
   backend/frontend/shared-contract ve bunların deployment'ıyla sınırlandı.
5. Slice 15 P1–P3 contract/error/image/runtime doğrulama temelini kurdu.
6. Invite/onboarding işi başlamadan durduruldu. Kullanıcı mevcut auth'u koruyup
   bütün demoyu Railway üzerinde basit tutma kararı verdi.
7. ADR-022 geniş production hardening'i kontrollü demo için erteledi ve Slice 15
   P4–P6 olarak yeniden çizildi.
8. P4 repository reconciliation tamamlandı. Deployment başlamadan önce
   user-owned UI/UX revizyonu araya alındı.

Bu kronoloji, eski geniş production planının veya durdurulmuş 15-T03'ün bugün
uygulanabilir scope olduğu anlamına gelmez.

## 3. Slice 14A — Dispute and Casework Foundation

### Gerçekleşen kullanıcı sonucu

- Fulfillment başladıktan sonra buyer veya seller legal-entity `ADMIN` bir
  active dispute açabilir.
- Buyer/seller `ADMIN` ve `MEMBER` read/comment yapabilir.
- Yalnız counterparty `ADMIN` acknowledge, yalnız opener entity `ADMIN`
  withdraw yapabilir.
- Other participant ve nonparticipant party-only, non-disclosing davranış alır;
  actor-aware Deal lifecycle yalnız taraflarda `DISPUTE` gösterir.
- Opening snapshot, mevcut fulfillment/milestone/finalized evidence ve o anda
  başarılı olan video-analysis provenance'ını immutable tutar.
- Comments append-only; mutation'lar versioned, idempotent ve audited'dir.
- Casework hiçbir Deal, fulfillment, evidence, funding, payment, settlement,
  provider, messaging veya AI state'ini değiştirmez.

### Public API

- `POST /deals/{dealId}/disputes`
- `GET /deals/{dealId}/disputes`
- `GET /deals/{dealId}/disputes/{disputeId}`
- `GET|POST /deals/{dealId}/disputes/{disputeId}/comments`
- `POST /deals/{dealId}/disputes/{disputeId}/acknowledge`
- `POST /deals/{dealId}/disputes/{disputeId}/withdraw`

OpenAPI server base'i `/api/v1`'dir.

### Persistence ve başlıca yüzeyler

- V22 dispute/case/comment/snapshot persistence ve invariant'larını ekler.
- `casework` state, authorization, snapshot, comments ve public behavior'ın
  sahibidir; diğer modüllere dar portlarla erişir.
- Frontend generated types ile open/history/snapshot/comment/acknowledge/
  withdraw, stale/error ve evidence-access durumlarını gösterir.

### Recorded validation

- Contract validator: 21 schema, 13 fixture.
- Implementer Core full verify: 331 test.
- Focused migration, authorization, idempotency, concurrency, lifecycle,
  Slice 12/13, Deal/payment ve architecture matrix geçti.
- Frontend typecheck/build ve `git diff --check` geçti.
- Planner, kullanıcı yönlendirmesiyle bu otomatik komutları kapanışta yeniden
  çalıştırmadı; bunlar implementer evidence olarak kaydedildi.

### Review odağı

- Party-only disclosure ile general Deal participant visibility ayrımını
  kontrol edin.
- Open/replay/concurrent-open ve stale mutation davranışını V22 constraint ve
  lock sırasıyla birlikte inceleyin.
- Snapshot'ın opening-time immutable değer olduğunu, sonraki evidence/video
  değişiminden etkilenmediğini doğrulayın.
- Casework lifecycle projection'ının business state mutation'ı olmadığını ve
  hiçbir ödeme/fulfillment side effect üretmediğini kontrol edin.

## 4. Gate C0 — 14A ve Slice 13 browser borcunun kapanışı

Gate C0 ikinci-control fix sonrasında local Compose PostgreSQL/RabbitMQ/MinIO/
Mock AI Worker, Core `local,local-sandbox` (Flyway V22) ve Vite ile geçti.

### Kanıtlanan matris

- Gerçek cross-tenant buyer/seller ADMIN ve MEMBER actor matrisi.
- Dispute open/refresh, same-key replay ve empty active slate üzerinde gerçek
  concurrent distinct-key open: bir `201`, bir `409`, tek active case.
- MEMBER read/comment ve forced mutation 403 davranışı.
- Seller ADMIN acknowledge; opener ADMIN withdraw; stale version 409.
- Party lifecycle `DISPUTE`; other participant ve nonparticipant için
  non-disclosing görünüm.
- Immutable opening snapshot; late evidence reject ve late video analysis
  sonrasında snapshot hash'i değişmedi.
- Fulfillment/video aksiyonlarının dispute boyunca bağımsız kalması.
- 21 comment pagination, history retention, loading/empty/backend-error ve
  desktop/390×844 mobile presentation.
- Case open→comment→acknowledge→withdraw boyunca Deal/fulfillment/evidence/
  payment/video/outbox business satırlarında side effect olmadığı exact DB
  diff ile doğrulandı.
- Slice 13 rejected historical VIDEO paneli sonucu doğru kaynaktan gösterdi;
  history row'da request/retry kontrolü veya duplicate current result yoktu.

Bu gate production, staging, real provider veya Slice 14B kanıtı değildir.
Disposable local hesap parolaları repository'ye yazılmadı ve gate sonrasında
session'lar invalid edildi.

## 5. Slice 11B-A — Sonradan gelen simulation-only köprü

### Kabul edilen teknik temel

- Ayrı çalışan deterministic Python HTTP Moka emulator.
- Bounded CheckKey/auth ve exact money conversion.
- Existing durable funding relay'in DB transaction dışında real HTTP portuna
  çıkması.
- Success: one query + one initiate → `FUNDED`.
- Timeout: `UNCONFIRMED`, ikinci charge yok.
- Recovery: aynı lifetime-fixed provider key ile query-first; ikinci initiate
  yok, toplam üç query + bir initiate.
- Pool approve/query yalnız probe/integration yüzeyidir ve payment business
  service'inden erişilemez.

### Validation ve sınır

- Emulator: 11 test; focused Java transport/bootstrap/architecture/payment
  testleri geçti.
- Planner material `PaymentFundingIntegrationTest`i ayrıca çalıştırdı ve geçti.
- Public contract/frontend/migration değişmediği için browser ve full backend
  suite tekrarlanmadı.
- Bu kabul gerçek provider, 3D/callback, credential, settlement veya production
  suitability kanıtı değildir.
- Sonraki founder kararı gerçek-provider 11B-B yolunu supersede etti. Temel
  yalnız simulation güvenliği ve HTTP boundary kanıtı olarak korunur.

## 6. ADR-014–ADR-022 ile sabitlenen yön

| ADR | Bu dönemdeki etkisi |
|---|---|
| ADR-014 | Gelecekteki release yalnız açık `DEMO_SIMULATED`; gerçek para yok. 14B henüz uygulanmadı. |
| ADR-015 | Audit her auditable mutation'da; outbox yalnız accepted event/dispatch varsa aynı transaction'da. |
| ADR-016/020 | Broad-production runtime/release tasarımı korunur fakat controlled demo için ertelenmiştir. |
| ADR-017/018 | Invite-only/Postmark ve GuardDuty quarantine broad production'a aittir; demo bunları uygulamaz. |
| ADR-019 | AI provider/model/worker/image/deployment kararları ayrı AI owner'a aittir; main ekip değiştiremez. |
| ADR-021 | Committed OpenAPI tek authority; runtime gate yalnız observable inventory + focused HTTP evidence kullanır. |
| ADR-022 | Mevcut Railway projesi, mevcut auth, versioned Railway MinIO ve exact source SHA ile `RAILWAY_DEMO_READY` hedeflenir. |

Özellikle ADR-019 nedeniyle bu dönemde hazırlanmış AI model/provider/worker
önerileri main implementasyon kapsamından çıkarılmıştır. Main repo yalnız shared
contract mismatch'i raporlayabilir ve kendi Spring/frontend sınırını koruyabilir.

## 7. Slice 15 P1 — Closed public error authority

### Gerçekleşen sonuç

- OpenAPI `ApiErrorCode` ve `FieldErrorCode` kapalı catalog haline geldi.
- `ProblemDetail.code` ve field error code committed enum'lara bağlandı.
- Java catalog exact-set validator ile OpenAPI authority'ye eşlendi; frontend
  yalnız generated type kullanır.
- Undocumented birleşik fulfillment error'ları kaldırıldı; Deal,
  fulfillment ve evidence not-found ayrımı granular code'larla yapıldı.
- Generic Spring Security denial için grandfathered `ACCESS_DENIED` 403
  contract'a alındı; CSRF `CSRF_TOKEN_INVALID` 403 kaldı.
- Public handler'larda `String`/`valueOf` kaçışları kaldırıldı.
- Nested evidence yollarında önce Deal visibility kontrolü, sonra resource
  ayrımı uygulanarak non-disclosure korundu.

### Kabul ve review sonucu

- İlk revizyondaki `ACCESS_DENIED` yerine `INTERNAL_ERROR/500` dönüşü review'da
  reddedildi ve Rev 3'te düzeltildi.
- Contract validator, focused Core tests, Core verify ve frontend generation/
  typecheck/build bu checkpoint'te geçti.
- Accepted checkpoint: `d69d7e00`.

## 8. Slice 15 P2–P3 — Contract bundle ve observable runtime gate

### P2 kabul edilen sonuç

- Deterministic contract bundle digest Python ve Java'da aynı kuralla üretildi.
- `contracts/schemas/**` Core artifact classpath'ine paketlendi.
- Probe-token korumalı `GET /internal/v1/contracts` bundle/release identity
  yayımlar.
- Core image monorepo root context'inden build edilir; contract bundle ve digest
  OCI label/smoke ile doğrulanır; runtime non-root'tur.

### P3 kabul edilen sonuç

- Production profile'da springdoc kapalıdır.
- İlk full-equality yaklaşımı raw springdoc'un gözlemleyemediği committed
  security/response/schema bilgisini kopyalayarak false-green ürettiği için
  kaldırıldı.
- ADR-021 sonrası active gate exact public path/method ve güvenilir named path
  parameter inventory'sini raw runtime'dan karşılaştırır.
- Security/status/media/schema semantiği committed validator, negative fixtures,
  generated-type clean diff ve focused HTTP behavior testleriyle korunur.
- Disabled full-equality, runtime self-mutation veya ikinci annotation/spec
  authority yoktur.
- Main CI consumer drift'i Core/frontend değişikliklerinde de kontrol eder;
  AI repository için yalnız `UNVERIFIED_EXTERNAL_GATE`/read-only evidence sınırı
  vardır, AI endpoint veya label uydurulmaz.

### Kabul ve review sonucu

- P2 ilk implementasyonundan sonra release identity ve runtime projection
  sorunları review/fix/replan ile düzeltildi.
- Final accepted checkpoint: `4845a03`; workflow policy closure: `5051860`.
- Bu dönemde repository-wide suites bir kez çalıştırılmış olsa da daha sonra
  ADR-022 ile incremental review politikası minimum affected tests olarak
  sabitlendi; final full suite P6'ya ayrıldı.

## 9. Durdurulan 15-T03 ve Railway-demo replan

- 15-T03 invite/onboarding implementasyona başlamadan durduruldu; yalnız
  `docs/plan/review/req-review.md` değişmişti ve implementation evidence
  sayılmadı.
- Kullanıcı mevcut register/login/session sisteminin demo için yeterli olduğuna
  karar verdi. Invite-only onboarding, password reset, Postmark, yeni login
  throttle ve member-invitation bu yoldan çıkarıldı.
- Aynı sadeleştirme AWS S3/GuardDuty, Grafana, GHCR promotion, geniş pilot/SLO ve
  yeni project/region kurulumunu controlled demo yolundan çıkardı.
- ADR-022 ve replacement ready plan `09fca0a` checkpoint'inde kabul edildi.

### Read-only Railway discovery özeti

- Mevcut project: `m4trust-staging`; environments: `staging`, `production`.
- Staging'de public web, private Core ve private PostgreSQL çalışıyordu; web/Core
  source revision'ları farklıydı ve RabbitMQ/object storage yoktu.
- Live Core service root'u eski `/services/core-api` ayarındaydı; P2 Dockerfile
  monorepo root istediği için repository/deployment uyumsuzluğu vardı.
- Production'da web/Core yoktu; PostgreSQL volume ile başarısız/kapalı MinIO
  service+volume vardı. Bunlar non-destructive korunacak; silme/reset yapılmayacak.
- Discovery hiçbir Railway kaynağını değiştirmedi.

## 10. Slice 15 P4 — Railway-demo repository reconciliation

### Gerçekleşen sonuç

- ADR-017/018'den kalmış fakat hiçbir runtime endpoint'i kullanmayan tam on dead
  error code OpenAPI, Java, generated frontend types ve changelog'dan birlikte
  kaldırıldı. `ACCESS_DENIED` ve existing `DEAL_INVITATION_*` korundu.
- `services/core-api/railway.json`, Core build için repository root `/`,
  `services/core-api/Dockerfile` ve `services/core-api/** + contracts/**` watch
  contract'ını taşır.
- AI Rabbit listener yalnız `app.messaging.topology.enabled=true` ise aktif olur;
  messaging kapalı demoda broker-free startup korunur.
- Messaging topology veya relay açık olduğunda host, port, username ve password
  fail-fast zorunludur.
- RabbitMQ port'u integer ve `1..65535` aralığında olmak zorundadır; malformed,
  zero, negative ve above-range değerler startup'ta reddedilir.
- Auth ve business behavior değişmedi.

### Review ve minimal validation

- Rev 1 `c1206cf`: catalog/config/listener değişiklikleri.
- Review finding: messaging açıkken eksik Rabbit config fail-fast değildi.
- Rev 2 `3bc077f`: `MessagingBrokerBootstrapGuard` ve both-disabled davranışı.
- Review finding: port parse/range doğrulaması eksikti.
- Rev 3 `381ed5b`: integer/TCP range fail-fast.
- Rev 1'de contract validator, 10 focused deployment/catalog testi, frontend
  generate/typecheck ve Docker smoke geçti.
- Rev 2–3'te yalnız affected bootstrap/deployment tests ve `git diff --check`
  çalıştırıldı; geçti.
- Kullanıcı yönlendirmesi uyarınca P4 final review ana planner tarafından hızlı
  ve focused yapıldı; ayrı reviewer agent kullanılmadı.
- Full Core/frontend suite çalıştırılmadı; accepted plan bunu P6 final gate'e
  ayırır.

### Review odağı

- Railway Core Root Directory'nin `/`, config path'in
  `/services/core-api/railway.json` olduğunu deployment sırasında doğrulayın.
- Messaging disabled iken hiçbir broker credential zorunlu olmamalı; topology
  veya relay'den biri enabled ise eksik/invalid config sessiz fallback yapmamalı.
- P4'ün repository readiness olduğunu, live Railway wiring veya deployment
  kanıtı olmadığını koruyun.

## 11. Contract, persistence, frontend ve deployment çapraz tablosu

| Alan | Accepted sonuç | Açık kalan |
|---|---|---|
| Casework | Slice 14A + C0 browser matrix | resolution/operator/cancellation yok |
| Payment transport | 11B-A local/CI query-first HTTP boundary | real provider yolu kapalı; release yok |
| Error authority | Slice 15 P1 closed catalog | yeni code önce contract ister |
| Contract artifact | P2 classpath bundle + digest + internal probe | external AI compatibility ayrı owner evidence ister |
| Runtime verification | P3 observable inventory + focused semantics | raw full OpenAPI equality iddiası yok |
| Repository deploy config | P4 monorepo Railway config + messaging guard | live staging/production wiring yapılmadı |
| Frontend | Existing generated-type UI korunuyor | user-owned UI/UX revizyonu sıradaki gate |

## 12. Önerilen review sırası

1. Bu dosyanın başındaki durum tablosunu ve ADR-019/021/022'yi okuyun.
2. Slice 14A/V22 ve Gate C0 kanıtını, no-side-effect ve disclosure ekseninde
   inceleyin.
3. 11B-A'nın yalnız local/CI simulation foundation olduğunu doğrulayın.
4. P1 error authority'yi OpenAPI → Java → generated frontend exact set olarak
   takip edin.
5. P2 bundle/digest/image probe zincirini inceleyin.
6. P3 runtime inventory'nin committed alanları actual'a kopyalamadığını kontrol
   edin.
7. P4 Railway config/messaging guard'ı ADR-022'nin mevcut auth ve broker-optional
   sınırıyla karşılaştırın.
8. Şüphe varsa yalnız smallest relevant test/reproducer çalıştırın; full suite
   P6'dan önce rutin review aracı değildir.

## 13. Açık işler ve kesin kapsam sınırı

- Şu anda deployment task'i çıkarılamaz. User-owned UI/UX revizyonu uygulanıp
  kabul edilmeli ve kullanıcı deployment'a devam edilmesini açıkça onaylamalıdır.
- P5 mevcut Railway staging'i same-source web/Core ve versioned private MinIO ile
  reconcile edip gerçek browser demo akışını kanıtlayacaktır.
- P6 final full gate'i bir kez çalıştırıp aynı accepted source SHA'yı mevcut
  production environment'a non-destructive deploy edecektir.
- P5/P6 tamamlanmadan `RAILWAY_DEMO_READY` denemez.
- Controlled demo `PROD_READY`, broad-production, malware-safe, AI-ready veya
  real-money-ready değildir.
- Slice 14B settlement/release planning-only'dir; bu dosyadaki hiçbir kabul ona
  implementasyon yetkisi vermez.
- AI provider/model/worker/image/deployment değişikliği main ekibin yetkisi
  dışındadır; yalnız shared-contract mismatch veya non-authoritative risk
  observation bildirilebilir.
- V1–V22 migration history değiştirilemez.
