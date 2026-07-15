# ADR Reading Index

Bu index, bir görev için gereken minimum mimari bağlamı bulmanı sağlar. **Türetilmiş bir dokümandır: bu index ile bir ADR çelişirse ADR kazanır.**

Kullanım sırası:

1. Sorunun cevabı **Katman 0 (Cheat Sheet)** içindeyse ADR açma, cevabı kullan.
2. Cevap yoksa görev metnindeki anahtar kelimeyle **Katman 1 (Trigger Sözlüğü)** üzerinden ilgili ADR bölümüne git; yalnız o bölümü oku.
3. Görev tipin **Katman 2 (Görev Reçeteleri)** içinde varsa reçetedeki okuma listesini izle.
4. **Katman 3 (Eskalasyon)** kurallarından biri tetikleniyorsa implementasyona başlamadan dur.
5. Bir ADR'nin tamamını yalnız o ADR'nin core policy'sini değiştirirken oku.

Yasakların konsolide listesi ayrı dosyadadır: [FORBIDDEN.md](FORBIDDEN.md). Terimlerin tuzaklı olanları için bu dosyanın sonundaki Sözlük'e bak.

---

## Katman 0 — Cheat Sheet

En sık gereken mikro-kararlar. Kaynak sütunundaki bölüm, kararın bağlayıcı tanımıdır.

### Public API (frontend ↔ Spring)

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Para formatı | `amountMinor` (integer) + `currency` (ISO 4217); float asla | ADR-006 §28 |
| Yüzde formatı | integer basis points, alan adı `...BasisPoints` | ADR-006 §29 |
| Timestamp | RFC 3339 UTC, `Z` suffix (`2026-07-14T12:30:00Z`) | ADR-006 §26 |
| Saat taşımayan tarih | `YYYY-MM-DD`; sahte midnight timestamp yok | ADR-006 §27 |
| ID formatı | string UUID; iç yapısından anlam çıkarılmaz | ADR-006 §30 |
| Enum formatı | `UPPER_SNAKE_CASE`; public contract'tır | ADR-006 §31 |
| Success envelope | KULLANILMAZ; resource doğrudan body'dir | ADR-006 §6 |
| Liste response | `items/page/size/totalElements/totalPages`; `items` asla null değil | ADR-006 §9 |
| Pagination | 0 tabanlı `page/size`; default 20, max 100 | ADR-006 §10 |
| Sorting | `?sort=alan,desc`; yalnız allowlist alanları | ADR-006 §11 |
| Hata modeli | RFC 9457 Problem Details + `code` + `correlationId` | ADR-006 §13–14 |
| Validation hatası | 422; bozuk JSON/parse → 400 | ADR-006 §18 |
| Business çatışması | 409 (duplicate email, yasak state geçişi, stale version) | ADR-006 §19 |
| 401 / 403 / 404 | session yok/geçersiz / yetki yok / yok veya gizleniyor | ADR-006 §20 |
| Optimistic concurrency | request body'de `expectedVersion`; uyuşmazlık → 409 | ADR-006 §21–22 |
| Idempotency (HTTP) | `Idempotency-Key` header; yalnız riskli side-effect endpoint'lerinde | ADR-006 §24–25 |
| Correlation | `X-Correlation-ID` request+response header; problem body'de de var | ADR-006 §34 |
| Uzun işlem | 202 Accepted + Location; HTTP içinde bekletme yok | ADR-006 §35 |
| Yeni endpoint sırası | önce `contracts/openapi/core-api-v1.yaml`, sonra implementasyon | ADR-006 §42–43 |
| Frontend tipleri | committed OpenAPI'den üretilir; paralel elle model yasak | ADR-006 §44 |

### Domain (Spring)

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Ana aggregate adı | `Deal`; ana aggregate için Transaction adı yasak | ADR-003 §2 |
| Tenant vs LegalEntity | tenant = teknik izolasyon; legal entity = sözleşme tarafı; eş DEĞİL | ADR-003 §5 |
| Silme | generic soft delete yok; `CANCELLED/ARCHIVED/SUPERSEDED/WITHDRAWN` durumları | ADR-003 §7.2 |
| Para (DB) | bigint minor units + currency; yüzde → integer basis points | ADR-003 §27 |
| ID / zaman (DB) | UUID / timestamptz | ADR-003 §27 |
| Transaction kuralı | business mutation + audit + outbox = aynı PostgreSQL transaction | ADR-003 §24 |
| External çağrı | DB transaction'ı açıkken external çağrı (broker, storage, provider) yasak | ADR-003 §24 |
| Concurrency | mutable aggregate'te `version` + optimistic locking; sessiz last-write-wins yasak | ADR-003 §25 |
| Modüller arası erişim | port / ID / domain event / projection; repository ve JPA entity paylaşımı yasak | ADR-003 §23 |
| Lifecycle gösterimi | `DealLifecycleProjection` Spring'de merkezi hesaplanır; frontend hesaplamaz | ADR-003 §16, §29 |
| Yetki bağlamı | `authenticatedUserId + tenantId + activeLegalEntityId + dealId + operation`; application katmanında | ADR-003 §28 |

### Spring ↔ FastAPI (AI)

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| İletişim | asenkron RabbitMQ; senkron inference endpoint'i yasak | ADR-002 §2, §21.5 |
| Event adları | `ai.job.requested/completed/failed/cancel.requested` + `.v1` | ADR-002 §4 |
| Job türü | event adından değil envelope `jobType` alanından | ADR-002 §4 |
| `transactionId` | semantik olarak Deal kimliğidir (`dealId`); v1 adı korunuyor | ADR-002 §6 not |
| Completed event | teknik başarı; business kabul DEĞİL — Spring ayrıca doğrular | ADR-002 §11 |
| AI teknik hatası | business rejection'a çevrilmez; job durumu güncellenir | ADR-001 §14 |
| Teslimat garantisi | at-least-once; her consumer duplicate-safe olmak zorunda | ADR-002 §17 |
| Retry sahipliği | FastAPI teknik retry (max 3); yeni job kararı Spring'in | ADR-002 §18 |
| Raw dosya | broker mesajında taşınmaz; kısa ömürlü presigned URL | ADR-002 §7.1, §29 |
| Contract değişikliği | uygulama kodundan ÖNCE, ortak review ile | ADR-002 §25 |

### Güvenlik

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Authentication | server-side opaque session (Spring Session JDBC); JWT yasak | ADR-005 §2–4 |
| Cookie (prod) | `__Host-M4TRUST_SESSION`, HttpOnly, Secure, SameSite=Lax | ADR-005 §5 |
| Session süreleri | idle 30 dk, absolute 8 saat (server-side uygulanır) | ADR-005 §6 |
| CSRF | açık; unsafe metotlar token ister; token `GET /api/v1/security/csrf` | ADR-005 §10 |
| Parola | Argon2id; 15–128 karakter; kompozisyon kuralı yok | ADR-005 §13 |
| Login hatası | generic mesaj; hesap var/yok sızdırılmaz | ADR-005 §15 |
| Legal entity header | `X-M4Trust-Legal-Entity-Id`; yetki kanıtı DEĞİL, membership ile doğrulanır | ADR-005 §20 |

### Deployment / operasyon

| Soru | Cevap | Kaynak |
| --- | --- | --- |
| Migration | Flyway, forward-only; breaking değişiklik expand–contract ile bölünür | ADR-007 §21, §24–25 |
| Rollback | önceki immutable image; DB rollback migration'ı ana yöntem değil | ADR-007 §38 |
| Secret | repo, image ve frontend bundle dışında; asla loglanmaz | ADR-007 §19–20 |
| Log | stdout/stderr structured JSON + correlationId | ADR-007 §32 |
| Liveness vs readiness | liveness dış dependency'ye bağlanmaz; readiness trafik kabulünü gösterir | ADR-007 §30–31 |
| Ortamlar | local / staging / production; kaynak paylaşımı yok | ADR-007 §3 |

---

## Katman 1 — Trigger Sözlüğü

Görev metninde geçen kelimeden ilgili bölüme git. Çift anlamlı terimlerde bağlama göre doğru satırı seç.

| Anahtar kelime | Git | Not |
| --- | --- | --- |
| 202 / async response | ADR-006 §35–36 | |
| 400 / 422 ayrımı | ADR-006 §18 | |
| 409 / conflict | ADR-006 §19 | |
| audit | ADR-003 §4.10, §24 | security event logu ayrı: ADR-005 §17 |
| authorization / yetki | ADR-003 §28; ADR-005 §21 | controller annotation tek başına yetmez |
| backup / restore | ADR-007 §35–37 | |
| cancel (AI job) | ADR-002 §20 | best-effort; geç sonuç gelebilir |
| cancel (Deal) | ADR-003 §9 | state machine geçişi |
| capabilities endpoint | ADR-002 §21.3 | |
| confidence | ADR-002 §8.1 | 0.0–1.0; business kabul kararı değil |
| cookie | ADR-005 §5 | |
| CORS | ADR-005 §11; ADR-007 §6 | prod'da same-origin tercih |
| correlation ID | ADR-006 §34; ADR-002 §30 | |
| CSRF | ADR-005 §10 | |
| dead-letter | ADR-002 §5.4 | |
| deadline (AI job) | ADR-002 §19 | |
| Deal state / status | ADR-003 §8–9 | 7 bağımsız state machine; tek dev status yasak |
| DELETE / silme | ADR-006 §5; ADR-003 §7.2 | business aggregate'te generic DELETE yok |
| Docker / container | ADR-007 §15–16 | |
| doküman upload | ADR-006 §50; ADR-001 §6 | presigned storage; Spring proxy'lemez |
| dispute | ADR-003 §15 | AI otomatik dispute açamaz |
| enum | ADR-006 §31 (API); ADR-002 §14 (AI contract) | AI contract'ta closed set + UNKNOWN politikası |
| envelope (event) | ADR-002 §6 | |
| error / hata formatı | ADR-006 §13–16 (API); ADR-002 §12 (AI) | iki farklı contract, karıştırma |
| evidence | ADR-003 §22 | |
| filtering | ADR-006 §12 | endpoint-specific; generic filter dili yasak |
| Flyway / migration | ADR-007 §21–25 | forward-only |
| funding / payment | ADR-003 §12, §21 | ESKALASYON: bkz. Katman 3 |
| health endpoint | ADR-007 §29–31; ADR-002 §21.1–21.2 | |
| idempotency (HTTP) | ADR-006 §24–25 | |
| idempotency (broker) | ADR-002 §17; ADR-001 §13 | |
| JSONB | ADR-003 §27 | yalnız kontrollü alanlarda |
| JWT / token | ADR-005 §3 | kullanılmaz |
| lifecycle projection | ADR-003 §16 | frontend hesaplamaz |
| login / register | ADR-005 §13–16, §22 | |
| manual review | ADR-003 §10, §17 | requiresManualReview advisory'dir |
| Mock AI Worker | ADR-004 §12–14; ADR-007 §10 | production'da yasak |
| model / LLM / prompt değişikliği | ADR-002 §26 | contract değişikliği gerektirmez |
| object storage / MinIO / S3 | ADR-001 §6; ADR-007 §14 | |
| OpenAPI (public) | ADR-006 §42–48 | slice bazlı hibrit akış |
| OpenAPI (AI internal) | ADR-002 §21 | yalnız operasyonel endpoint'ler |
| outbox / inbox | ADR-003 §26; ADR-001 §13 | |
| pagination | ADR-006 §9–10 | |
| Problem Details | ADR-006 §13–16 | |
| queue / exchange / routing key | ADR-002 §5; contracts/asyncapi/ | isimler contract'tır |
| rate limit / throttling | ADR-006 §37; ADR-005 §16 | login throttling Slice 1'de ertelendi (bkz. plan 01) |
| ratification | ADR-003 §11, §20 | immutable package; ESKALASYON: bkz. Katman 3 |
| retry | ADR-002 §18 (AI); ADR-003 §21 (provider) | provider timeout ≠ failure |
| rule-set | ADR-003 §19 | immutable version |
| schema version | ADR-002 §15 | |
| session | ADR-005 §2–9 | |
| settlement | ADR-003 §14 | ESKALASYON: bkz. Katman 3 |
| slice / kabul testi | ADR-004 §3, §8–9, §22–23 | |
| soft delete | ADR-003 §7.2 | yasak; açık domain durumları |
| tenant | ADR-003 §5 | ≠ legal entity; tenantId filtresi tek başına yetki değil |
| test / coverage | ADR-004 §6–7 | minimum test; coverage hedefi yasak |
| timeout (session) | ADR-005 §6 | |
| timeout (AI job) | ADR-002 §19 | business timeout Spring'in |
| timeout (payment provider) | ADR-003 §21 | UNKNOWN outcome + reconciliation |
| versioning (public API) | ADR-006 §2, §47 | |
| versioning (AI contract) | ADR-002 §15–16 | |
| version (optimistic lock) | ADR-006 §21–23; ADR-003 §25 | |
| video analysis | ADR-002 §9–10; ADR-003 §22 | advisory-only |
| warning (AI) | ADR-002 §13 | severity yalnız INFO/WARNING |

---

## Katman 2 — Görev Reçeteleri

Görev tipin buradaysa okuma listesini aynen izle; reçete dışına çıkman gerekiyorsa Katman 1'e dön.

**R1 — Yeni public endpoint ekliyorsun**
Önce yüzeyi `contracts/openapi/core-api-v1.yaml`'a tasarla (ADR-006 §42–43), sonra implemente et. Oku: ADR-006 §3–8 (adlandırma/metot/response), §13–20 (hata/status). Mutation varsa: §21–25 (expectedVersion/idempotency). Liste dönüyorsa: §9–12.

**R2 — Yeni tablo veya migration ekliyorsun**
Oku: ADR-003 §27 (tipler: UUID, timestamptz, bigint money, basis points, version), ADR-007 §21–25 (Flyway, forward-only, expand–contract). Uygulanmış migration'ı asla değiştirme; yeni migration ekle.

**R3 — RabbitMQ mesajına veya AI contract'ına dokunuyorsun**
DUR-kontrol: payload/şema/queue adı değişiyorsa bu bir contract değişikliğidir → önce `contracts/` PR'ı, ortak review, fixture + validator (ADR-002 §25 sırası). Oku: ADR-002 §5–6 (topoloji/envelope), §15 (versioning), `contracts/README.md` (extensibility sınırları).

**R4 — Yeni frontend ekranı yapıyorsun**
Oku: ilgili slice planı (`docs/plan/`), ADR-004 §18 (mock politikası — mock'la biten iş done değildir), ADR-006 §41 (lifecycle/availableActions Spring'den gelir, türetme). Zorunlu durumlar: loading, error, empty, yetkisiz. Tipler committed OpenAPI'den üretilir (ADR-006 §44).

**R5 — Yeni slice başlatıyorsun**
Oku: `docs/plan/README.md` (şablon + workflow), ADR-004 §4–5 (geliştirme sırası, hibrit OpenAPI), §22–23 (checklist, Done tanımı). Slice'ın kabul testi gerçek tarayıcı akışıdır; Postman/Swagger yeterli değildir.

**R6 — Authentication/yetki koduna dokunuyorsun**
Oku: ADR-005 §20–21 (legal entity context + authorization katmanı), ADR-003 §28 (bağlam nesnesi). Yetki kontrolü application katmanında, tek merkezi mekanizmadan (Slice 2'de kurulan `OperationContext`); endpoint'e kopyala-yapıştır kontrol yazma.

---

## Katman 3 — Eskalasyon Kuralları

Aşağıdakilerden biri tetikleniyorsa **implementasyona başlamadan dur** ve planner'a/insana çık:

1. Görev **payment, funding, settlement veya ratification mutation'ına** dokunuyor → bu alanlar Spring'in en korumalı otorite alanıdır (ADR-003 §14, §20–21); planner onayı olmadan değişiklik yapma.
2. Görev **`contracts/` altında değişiklik** gerektiriyor → tek taraflı contract değişikliği yasak (ADR-001 §9, ADR-002 §25); süreci başlat, kodu bekle.
3. **İki ADR çelişiyor** görünüyor → daha spesifik ve daha yeni olan kazanır; emin değilsen karar verme, çelişkiyi raporla.
4. Sorunun cevabı **hiçbir ADR'de yok ve birden fazla modülü etkiliyor** → kendin karar verip "accidental convention" üretme; yeni karar ihtiyacını planner'a taşı (README "Yeni ADR ne zaman yazılmalı?").
5. Yapmak üzere olduğun şey **[FORBIDDEN.md](FORBIDDEN.md) listesinde** → workaround arama; ihtiyaç gerçekse bu bir ADR değişikliği talebidir.
6. Bu index ile ADR arasında **tutarsızlık fark ettin** → index'i düzeltme PR'ına not düş; ADR'ye göre davran.

---

## Sözlük — terminoloji tuzakları

| Terim | Anlamı / tuzak |
| --- | --- |
| `transactionId` (AI envelope) | Deal aggregate kimliğidir (`dealId`); DB veya payment transaction'ı DEĞİL. v2'de rename adayı (ADR-002 §6 not) |
| `version` | Optimistic lock token'ı (ADR-006 §23). `ruleSetVersionNumber`, `schemaVersion` (payload semver) ve event adındaki `.v1` (major) ile karıştırma |
| tenant | Teknik izolasyon sınırı; sözleşme tarafı olan `LegalEntity`'dir. `tenantId` eşleşmesi yetki vermez (ADR-003 §5) |
| `AnalysisStatus.FAILED` | AI denemesi tamamlanamadı demek; Deal'in business reddi DEĞİL (ADR-003 §10) |
| completed event | AI işleminin teknik başarısı; business kabul için Spring validation zinciri gerekir (ADR-002 §11, ADR-003 §17) |
| `requiresManualReview`, `advisoryOutcome` | FastAPI önerisi; Spring bunları doğrudan business karara çevirmez (ADR-002 §8.1, §10.1) |
| `DealLifecycleProjection` | Diğer status'lardan türetilen görünüm; authoritative mutable state değil (ADR-003 §16) |
| Mock AI Worker | RabbitMQ üzerinden çalışan gerçek-sınır mock'u; Spring'in kendi içinde fake sonuç üretmesi bunun yerine geçmez (ADR-004 §14) |
| `subjectId` | Envelope'ta işlenen document/video kimliği; ADR-001'deki eski `documentId` alanının genelleştirilmiş hali |

---

## ADR kapsam tablosu

| ADR | Tek cümlelik kapsam |
| --- | --- |
| ADR-001 | Sistem sınırları, veri sahipliği, Spring–AI iletişim modeli, DB/storage izolasyonu |
| ADR-002 | Spring–FastAPI event contract'ları: envelope, topoloji, versioning, idempotency, hata/warning |
| ADR-003 | Deal domain modeli: modüller, aggregate'ler, state machine'ler, validation sahipliği, transaction kuralları |
| ADR-004 | Vertical slice yöntemi: tamamlanma ölçütü, test politikası, Mock AI Worker, kabul akışları |
| ADR-005 | Authentication ve güvenlik: opaque session, cookie, CSRF, parola, authorization bağlamı |
| ADR-006 | Public API standartları: adlandırma, Problem Details, pagination, concurrency, format kuralları, OpenAPI |
| ADR-007 | Deployment: Railway topolojisi, ortamlar, config/secret, Flyway, health, log, backup, rollback |

## Reading rules

- Planner-reviewer'lar cross-cutting görev şekillendirirken birden fazla ADR okuyabilir.
- Implementer'lar planner'ın seçtiği bölümlerle ve bu index'in yönlendirmesiyle başlar.
- Implementer prompt'larına ADR tam metni yapıştırılmaz; bölüm referansı verilir.
- Bir ADR'nin core policy'si değişiyorsa ADR'nin tamamı okunur.
- ADR'yi değiştiren her PR bu index'i ve FORBIDDEN.md'yi de günceller.
- Bu index ile bir ADR çeliştiğinde ADR kazanır.
