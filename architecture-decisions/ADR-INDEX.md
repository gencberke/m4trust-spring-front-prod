# ADR Reading Index

Bu index, bir görev için gereken minimum mimari bağlamı bulmayı sağlar. **Türetilmiş
bir dokümandır: index ile bir ADR çelişirse ADR kazanır.**

Kullanım sırası:

1. Cevap Katman 0'daysa doğrudan kullan.
2. Değilse Katman 1'deki anahtar kelimeden ilgili ADR bölümüne git.
3. Görev Katman 2'deyse reçeteyi izle.
4. Katman 3 tetikleniyorsa implementasyondan önce dur.

Yasakların konsolide görünümü: [FORBIDDEN.md](FORBIDDEN.md).

---

## Katman 0 — Cheat Sheet

### Public API

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Para | `amountMinor` integer + ISO 4217 `currency`; float yok | ADR-006 §28 |
| Yüzde | integer basis points | ADR-006 §29 |
| Timestamp | RFC 3339 UTC, `Z` suffix | ADR-006 §26 |
| Date | `YYYY-MM-DD` | ADR-006 §27 |
| ID | string UUID | ADR-006 §30 |
| Enum | `UPPER_SNAKE_CASE` | ADR-006 §31 |
| Success response | Global envelope yok; resource doğrudan body | ADR-006 §6 |
| Liste | Stabil DTO; collection null olmaz | ADR-006 §9, §32 |
| Hata | RFC 9457 + `code` + `correlationId` | ADR-006 §13–15 |
| 400 / 422 | parse hatası / semantic-field hatası | ADR-006 §18 |
| 409 | state, duplicate, stale version veya idempotency çatışması | ADR-006 §19 |
| 401 / 403 / 404 | auth yok / operation yetkisi yok / yok veya gizli | ADR-006 §20 |
| Concurrency | request body'de `expectedVersion` | ADR-006 §21–23 |
| Idempotency | Riskli endpoint'te `Idempotency-Key`, server-side kayıt | ADR-006 §24–25 |
| Yeni endpoint | Önce committed OpenAPI, sonra implementasyon | ADR-006 §42–43 |
| Frontend action | Backend projection'ından; status'tan türetilmez | ADR-006 §33, §41 |

### Domain ve authorization

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Ana aggregate | `Deal`; `Transaction` adı yasak | ADR-003 §2 |
| Tenant vs LegalEntity | tenant teknik sınır; legal entity business actor | ADR-003 §5 |
| Deal visibility | Yalnız participant ilişkisi; `deal.tenant_id` erişim filtresi değil | ADR-008 §2.4 |
| Participant tenant'ı | Entity'nin kendi tenant'ı `legal_entity_tenant_id` ile tutulur | ADR-008 §2.3 |
| Initiator kimliği | Deal'de immutable `initiatorLegalEntityId`; dolaylı veriden çıkarılmaz | ADR-009 §2.2 |
| Visibility vs mutation | Participant olmak read visibility verir; mutation yetkisi ayrıca operation bazlıdır | ADR-009 §2.2 |
| Davet kabulü | Participation'dır; buyer/seller veya contractual consent değildir | ADR-009 §2.1 |
| DRAFT yönetimi | Initiator taslak koordinatörüdür; diğer participant başlangıçta read/list | ADR-009 §2.2 |
| Deal activation | Buyer ve seller aynı immutable package'ı onaylayıp package RATIFIED olduğunda atomik | ADR-009 §2.3 |
| Package rejection | RATIFIED öncesi taraf ADMIN'i reject eder; yeni package gerekir | ADR-009 §2.4 |
| ACTIVE cancel | Tek taraflı doğrudan cancel yok; mutual buyer+seller veya casework kararı | ADR-009 §2.5 |
| Yüksek riskli onay | İlk rol modelinde ratification/cancellation approval yalnız `ADMIN` | ADR-009 §2.6 |
| Ratified değişiklik | Package mutation yok; yeni version + yeni ratification | ADR-003 §11, §20; ADR-009 §2.4 |
| Ratification commercial terms | `amountMinor` + `currency`, explicit teyit, dedicated immutable snapshot, RFC 8785 hash | ADR-010 §2.1 |
| Funding V1 | ACTIVE Deal, tek plan/unit, buyer ADMIN, sandbox/polling-first, async `202 + Location` | ADR-010 §2.2–§2.5 |
| Unknown payment | Failure veya yeni charge değil; aynı operation için reconciliation | ADR-003 §21; ADR-010 §2.3–§2.4 |
| Fulfillment V1 | `ACTIVE + FUNDED`, tek fulfillment/primary milestone; seller ADMIN/MEMBER submit, buyer ADMIN review | ADR-011 §2.1–§2.3 |
| Fulfillment completion | Manual buyer ADMIN kararı; Deal ACTIVE kalır, release/settlement/provider side effect yok | ADR-011 §2.5 |
| Video Analysis V1 | Current finalized VIDEO/MP4 veya PHOTO JPEG/PNG için buyer ADMIN explicit request/retry; participant read; sonuç advisory-only | ADR-012 §2.1–§2.5 |
| Dispute/Casework V1 | ACTIVE + started fulfillment; buyer/seller ADMIN open, party users read/comment, counterparty ADMIN acknowledge, opener ADMIN withdraw | ADR-013 §2.1–§2.3 |
| Dispute disclosure | Yalnız buyer/seller; diğer participant casework ve actor-aware DISPUTE lifecycle'ını göremez | ADR-013 §2.3, §2.8 |
| Transaction | Mutation + audit aynı transaction; accepted operation event/dispatch tanımlıyorsa outbox da aynı transaction | ADR-003 §24; ADR-015 §2.2 |
| External çağrı | DB transaction açıkken yapılmaz | ADR-003 §24 |
| Concurrency | Mutable aggregate `version`; sessiz last-write-wins yasak | ADR-003 §25 |
| Modül erişimi | Port/ID/event/projection; repository/JPA entity paylaşımı yok | ADR-003 §23 |
| Lifecycle UI | Projection Spring'de merkezi; frontend hesaplamaz | ADR-003 §16, §29 |
| Yetki bağlamı | user + tenant + active entity + deal + operation; application katmanı | ADR-003 §28; ADR-005 §21 |

### Spring ↔ FastAPI

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| İletişim | Asenkron RabbitMQ; senkron inference endpoint'i yok | ADR-001 §7; ADR-002 §21.5 |
| Completed event | Teknik başarı; business kabul değil | ADR-002 §11; ADR-003 §17 |
| Delivery | At-least-once; consumer duplicate-safe | ADR-002 §17 |
| Retry | Yeni job kararı Spring; AI-side technical retry implementation'ı AI owner'dadır ve contract-visible semantiği korur | ADR-002 §18; ADR-019 §2.2 |
| Dosya | Broker'da raw binary yok; presigned reference | ADR-002 §7.1, §29 |
| Contract değişikliği | Koddan önce, ortak review + fixture/validator | ADR-002 §25 |

### Güvenlik

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Authentication | Server-side opaque session; browser JWT yok | ADR-005 §2–4 |
| Cookie | `__Host-M4TRUST_SESSION`, HttpOnly, Secure, SameSite=Lax | ADR-005 §5 |
| CSRF / pre-auth | Authenticated unsafe metotlarda CSRF; ADR-017 pre-auth yüzeyi yalnız broad-production hardening yeniden açılırsa uygulanır | ADR-005 §10; ADR-017 §2.4; ADR-022 §2.2 |
| Demo register | Railway controlled demo mevcut açık register/login/session akışını korur; broad production invite-only kararı ertelenmiştir | ADR-022 §2.2 |
| Legal entity header | Yetki kanıtı değil; membership ile doğrulanır | ADR-005 §20 |
| Authorization | Controller/frontend yeterli değil; application katmanında | ADR-005 §21 |

### Deployment

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Migration | Flyway forward-only; breaking değişiklik expand–contract | ADR-007 §21, §24–25 |
| Rollback | Şemayla uyumlu önceki immutable image | ADR-007 §38 |
| Secret | Repo/image/frontend bundle dışında | ADR-007 §19–20 |
| Log | Structured stdout/stderr + correlation/release identity | ADR-007 §28, §32 |
| Ortamlar | local / staging / production kaynak paylaşmaz | ADR-007 §3 |
| Controlled demo topology | Mevcut Railway US West; web public, Core/DB private, versioned MinIO bucket private ve yalnız presigned S3 API erişilebilir | ADR-022 §§2.1–2.4 |
| Controlled demo artifact | Exact main SHA'dan Railway native build; environment deployment/image kimliği kaydedilir, `latest` yoktur | ADR-022 §2.5 |
| Controlled demo evidence smoke | Document storage smoke zorunludur; fulfillment evidence yalnız meşru public `ACTIVE + FUNDED` yolu varsa canlı doğrulanır. Yoksa no-seed/no-bypass deferred risk; local/test fixture geçmişi korunur | ADR-022 §2.10 |
| Controlled demo backup waiver | Railway native backup Pro gerektirdiği ve founder geçemediği için yalnız ilk production-demo deploy'unda backup gate açıkça waive edilmiştir; inventory zorunlu, data-loss riski kabul, backup/PITR/RPO/RTO iddiası yok | ADR-022 §2.7 |
| Broad production release/recovery | GHCR manifest promotion ve PITR/RPO/RTO ADR-016/020'de tanımlıdır fakat controlled demo için ertelenmiştir | ADR-016 §§2.4, 2.8; ADR-020 §2; ADR-022 §§2.5, 2.7 |

---

## Katman 1 — Trigger Sözlüğü

| Anahtar kelime | Git | Not |
| --- | --- | --- |
| initiator | ADR-009 §2.2 | Immutable Deal alanı; creator/tenant/participant sırasından çıkarılmaz |
| activate / ACTIVE | ADR-003 §9; ADR-009 §2.3 | Initiator action'ı değil; ratification sonucu |
| cancel (Deal) | ADR-003 §9; ADR-009 §2.2, §2.4–2.5 | DRAFT withdrawal ile ACTIVE cancellation ayrıdır |
| invitation | ADR-008 §2.7; ADR-009 §2.1–2.2; ADR-017 | Account activation, entity membership ve Deal participation ayrı consent'tir |
| participant / cross-tenant | ADR-008; ADR-009 §2.2 | Visibility ≠ mutation authority |
| buyer / seller | ADR-003 §7, §20; ADR-009 §2.3 | Aynı package sürümünün gerekli tarafları |
| ratification | ADR-003 §11, §20; ADR-009 §2.3–2.6; ADR-010 §2.1 | Onaylı Slice 10 scope'u bağlayıcı; sapma ESKALASYON |
| authorization / yetki | ADR-003 §28; ADR-005 §21; ADR-009 | Operation bazlı, application katmanında |
| Deal state / lifecycle | ADR-003 §8–9, §16; ADR-009 | Projection authoritative state değildir |
| dispute / casework | ADR-003 §15; ADR-009 §2.5; ADR-013 | Slice 14A foundation open/comment/acknowledge/withdraw; resolution ve cancellation hâlâ sonraki karar |
| audit | ADR-003 §4.10, §24 | Security event logundan ayrı |
| version / stale | ADR-003 §25; ADR-006 §21–23 | Optimistic concurrency |
| idempotency (HTTP) | ADR-006 §24–25 | Claim server-side, double-click korumasından fazlası |
| migration / Flyway | ADR-007 §21–25 | Uygulanmış migration değiştirilmez |
| tenant | ADR-003 §5; ADR-008 | Tenant ≠ LegalEntity |
| public OpenAPI / runtime drift | ADR-006 §42–48; ADR-021 | Committed spec tek design authority; raw runtime exact servlet inventory |
| error / Problem Details | ADR-006 §13–20 | Stable code |
| money / percentage | ADR-003 §21, §27; ADR-006 §28–29 | integer |
| payment / funding | ADR-003 §12, §21; ADR-010; ADR-014 | Slice 11 sandbox production'a çıkmaz; production yalnız açık `DEMO_SIMULATED`, gerçek provider/sapma ESKALASYON |
| settlement / release / simulated | ADR-014 | `DEMO_SIMULATED`, buyer ADMIN explicit release, query-only terminal proof, no real money |
| outbox / event / notification dispatch | ADR-015 | Event/dispatch yalnız accepted contract/plan tanımlıyorsa; audit her auditable mutation'da |
| production runtime / digest / PITR | ADR-016; ADR-020; ADR-022 | Controlled Railway demo exact SHA/deployment evidence kullanır; broad-production promotion/PITR ertelenmiştir |
| account invitation / password reset / Postmark | ADR-017; ADR-022 | Controlled demo mevcut auth'u korur; invite/reset/Postmark broad-production hardening'e ertelenmiştir |
| malware / quarantine / GuardDuty | ADR-018; ADR-022 | Controlled demo scan iddiası taşımaz ve yalnız demo verisi kullanır; broad production clean gate ertelenmiştir |
| AI provider / model / worker internals | ADR-019 §§2.1–2.2 | AI owner kararıdır; main ekip yalnız shared-contract uyumu ve Spring boundary'sini yönetir, öneri/uyumsuzluk raporlar |
| fulfillment / evidence | ADR-003 §13, §22; ADR-011; ADR-022 §2.10 | Tek milestone V1, direct-storage evidence, seller submit + buyer ADMIN manual review; Railway canlı smoke yalnız meşru `ACTIVE + FUNDED` yolu varsa, seed/bypass yok |
| video analysis V1 | ADR-002 §9–§10; ADR-003 §22; ADR-012 | Evidence-bound job/result history; buyer ADMIN request, participant read, advisory-only |
| object storage | ADR-001 §6; ADR-007 §14; ADR-018; ADR-022 | Controlled demo: Railway MinIO, private/versioned bucket ve presigned exact-version; broad production scan gate ertelenmiştir |
| document upload | ADR-001 §6; ADR-006 §49–50 | Spring upload binary proxy'si değil |
| RabbitMQ / schema | ADR-002 §5–6, §15, §25 | Contract süreci |
| AI result | ADR-002 §11–13; ADR-003 §17–18 | Advisory/technical result business karar değil |
| deployment / Railway | ADR-007 | Provider-specific business logic yok |
| test / coverage | ADR-004 §6–10 | Minimum critical tests; browser acceptance |

---

## Katman 2 — Görev Reçeteleri

**R1 — Yeni public endpoint**  
Önce `contracts/openapi/core-api-v1.yaml`. Mutation için ADR-006 §13–25; liste
ise §9–12; action availability için §33/§41.

**R2 — Yeni tablo veya migration**  
ADR-003 §27 ve ADR-007 §21–25. Uygulanmış migration değiştirilmez; rollout
compatibility açıkça yazılır.

**R3 — AI contract veya RabbitMQ değişikliği**  
Önce `contracts/` değişikliği, ortak review, fixture + validator. ADR-002 §5–6,
§15, §25 ve `contracts/README.md`.

**R4 — Yeni frontend ekranı**  
İlgili slice planı, ADR-004 §18, ADR-006 §41. Loading/error/empty/yetkisiz;
tipler committed OpenAPI'den.

**R5 — Yeni slice**  
`docs/plan/README.md`, ADR-004 §4–5 ve §22–23. Ana kabul gerçek browser akışıdır.

**R6 — Authentication/yetki kodu**  
ADR-005 §20–21, ADR-003 §28 ve gerekiyorsa ADR-009. Merkezi `OperationContext`
veya açık kullanıcı-scoped context kullan; endpoint'e kopya kontrol yazma.

---

## Katman 3 — Eskalasyon Kuralları

Implementasyondan önce dur ve planner/insana çık. İstisna: exact mutation veya
contract yüzeyi, insan-onaylı güncel bir `docs/plan/ready/` planında açıkça
tanımlanmış ve ilgili ADR kararı kabul edilmişse implementer o sınırlar içinde
ilerler; kapsam sapması yine eskalasyondur.

1. Payment, funding, settlement, ratification veya ACTIVE cancellation mutation'ı.
2. `contracts/` altında, onaylı ready planın tarif ettiği yüzey dışındaki değişiklik.
3. İki ADR çelişiyor görünüyorsa.
4. Cevap hiçbir ADR'de yok ve birden fazla modülü etkiliyorsa.
5. İhtiyaç FORBIDDEN listesine takılıyorsa.
6. Index ile ADR arasında tutarsızlık varsa.

`ADR-012` kabul edilmiştir. Video Analysis V1 actor, trigger, job/history,
messaging reuse ve advisory-only manual-review sınırı için bağlayıcıdır.

`ADR-013` kabul edilmiştir. Dispute/Casework V1 actor, party-only disclosure,
opening snapshot, concurrency ve no-side-effect sınırı için bağlayıcıdır.

`ADR-014`–`ADR-022` kabul edilmiştir. Settlement demo boundary, event/outbox
semantics, main application production runtime, invite-only identity, upload
quarantine, cross-repository AI ownership governance ve tek yönlü release manifest
kimliği kararları için bağlayıcıdır. ADR-021 runtime contract doğrulamasını yalnız
gözlemlenebilir inventory ve gerçek HTTP davranış kanıtlarına ayırır. ADR-019 AI
internal implementation seçimi yapmaz. ADR-022 mevcut Railway projesindeki
controlled demo için ADR-005/007/016/017/018/020'nin açıkça sayılan ağır production
zorunluluklarını erteler; sonuç broad-production readiness değildir.

---

## Sözlük

| Terim | Anlamı / tuzak |
| --- | --- |
| participant | Deal görünürlüğü ilişkisi; genel mutation rolü değil |
| initiator | Deal'de explicit immutable legal entity; DRAFT koordinatörü |
| invitation acceptance | Participation kabulü; contract approval değil |
| ACTIVE | Required parties aynı current package'ı ratify etmiş aktif business ilişki |
| ratification package | Tarafların onayladığı immutable canonical snapshot |
| version | Optimistic lock token; package/domain version ile karıştırılmaz |
| tenant | Teknik izolasyon; sözleşme tarafı LegalEntity'dir |
| DealLifecycleProjection | Türetilmiş UI görünümü; mutation state'i değil |
| completed AI event | Teknik başarı; Spring business validation gerekir |

---

## ADR kapsam tablosu

| ADR | Tek cümlelik kapsam |
| --- | --- |
| ADR-001 | Sistem sınırları ve veri sahipliği |
| ADR-002 | Spring–FastAPI contract, messaging ve compatibility |
| ADR-003 | Core domain, aggregate ve lifecycle state machine'leri |
| ADR-004 | Vertical slice, minimum test ve browser acceptance |
| ADR-005 | Session authentication, security ve authorization context |
| ADR-006 | Public API, errors, concurrency ve idempotency |
| ADR-007 | Deployment, migration, secrets, health ve rollback |
| ADR-008 | Cross-tenant participant tenant/visibility modeli |
| ADR-009 | Deal initiator, commitment, mutual ratification ve ACTIVE cancellation consent |
| ADR-010 | Ratification commercial terms ve provider-bağımsız funding/payment foundation |
| ADR-011 | Fulfillment V1 actor, evidence, state ve completion sınırı |
| ADR-012 | Video Analysis V1 subject, actor, job/result ve advisory sınırı |
| ADR-013 | Dispute/Casework V1 actor, lifecycle, snapshot, disclosure ve no-side-effect sınırı |
| ADR-014 | Demo-simulated settlement/release, contractual window, dispute race ve query-only terminality |
| ADR-015 | Audit, integration event, durable dispatch, notification outbox ve inbox atomicity |
| ADR-016 | Main Core/Web production topology, immutable release, recovery, observability ve pilot gate |
| ADR-017 | Invite-only identity, account/member invitation, recovery, throttling ve Postmark |
| ADR-018 | S3 quarantine, GuardDuty clean gate, immutable-version read ve orphan retention |
| ADR-019 | Main/AI karar yetkisi, read-only contract compatibility ve non-authoritative observation sınırı |
| ADR-020 | Main image digest'leri ile release manifesti arasındaki tek yönlü, döngüsüz kimlik bağı |
| ADR-021 | Committed OpenAPI otoritesi ile gözlemlenebilir runtime inventory ve HTTP behavior doğrulaması |
| ADR-022 | Existing Railway project üzerinde açık mevcut auth, versioned MinIO, exact source revision ve controlled-demo deployment sınırı |

## Reading rules

- Planner cross-cutting görevlerde birden fazla ADR okuyabilir.
- Implementer prompt'larına ADR tam metni değil bölüm referansı verilir.
- Core policy değişiyorsa ilgili ADR tamamen okunur.
- ADR değiştiren PR, bu index'i ve FORBIDDEN.md'yi günceller.
