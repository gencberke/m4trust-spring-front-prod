# Slice 04–07 Implementation Review Handoff

Bu doküman, kabul edilmiş Slice 3.9–7 implementasyonunu inceleyecek ajana hızlı
başlangıç sağlar. Slice 7'nin ilk config-as-code durumu ile daha sonra tamamlanan
Railway kabul kanıtı aynı tarihsel kayıtta birleştirilmiştir.
[00–03 handoff'unun](00-03-implementation-review-handoff.md) devamıdır; planların
yerine geçmez. Plan ile ADR çelişirse ADR kazanır; yönlendirme için
`architecture-decisions/ADR-INDEX.md` kullanılır.

## 1. İnceleme başlangıç noktası

- Slice 3.9–6 tabanı: `main@69f8e43` (Slice 6 merge, PR #19).
- Slice 7 repository wiring ve final Railway kabulü:
  `main@832cccab8e6f4e2c32bed8230520bdc76ec9df82`; reviewed implementation
  `codex/slice-07-staging-foundation@2c613262f5cddd21803bc3514a21c0f710d0f974`.
- Slice 3.9, 4, 5, 6 ve 7 kabul edilmiştir.
- Kabul planları:
  - [`03.9-hardening-and-decisions.md`](../03.9-hardening-and-decisions.md)
  - [`04-deal-invitations-and-participation.md`](../04-deal-invitations-and-participation.md)
  - [`05-deal-parties-and-activation.md`](../05-deal-parties-and-activation.md)
  - [`06-document-upload.md`](../06-document-upload.md)
  - [`07-staging-deployment.md`](../07-staging-deployment.md)
- Kabul durumu özeti: [`docs/plan/CURRENT.md`](../../CURRENT.md)

## 2. Slice'lar arası oluşan omurga

Slice 3.9–6 sonunda 00–03 omurgasının üzerine şu katmanlar eklendi:

1. ADR-008 ve ADR-009 Accepted oldu; visibility (participant ilişkisi) ile
   mutation authority (explicit immutable initiator) kalıcı olarak ayrıştı.
2. Participant satırları entity'nin kendi tenant'ını taşır
   (`legal_entity_tenant_id`); cross-tenant Deal katılımı expand→switch
   migration sırasıyla gerçek oldu.
3. Reusable HTTP idempotency altyapısı kuruldu (`idempotency` modülü) ve
   invitation create ile document finalize tarafından paylaşılıyor.
4. Merkezi `DealOperationPolicy` doğdu: initiator kontrolü ve actor-aware
   `availableActions` projection'ı tek noktada; update/cancel/parties/document
   yolları buradan geçer.
5. `document` modülü ve S3/MinIO storage adapter'ı eklendi: presigned direct
   browser PUT/GET, transaction-dışı storage doğrulaması, tek-transaction
   finalize (AVAILABLE + SUPERSEDED + Deal pointer + audit + idempotency).
6. Modül sınırları dar port'larla korunuyor: `DealDocumentMutationPort`,
   `DealDocumentReadPort`, `DealCurrentDocumentQueryPort`.

Modül zinciri (00–03'e ek):

```text
deal -> idempotency (invitation create)
document -> deal ports (lock, pointer, read)
document -> integration/storage (S3 presign/verify)
deal + document -> audit append port
```

Review sırasında controller'a kopyalanmış yetki kontrolü, modüller arası
doğrudan repository erişimi veya tenant-eşleşmesine indirgenen Deal erişimi
görülürse bu beklenen mimari değildir.

## 3. Slice 3.9 — Hardening ve Kararlar

### Gerçekleşen sonuç

- Deal cancel version yarışı düzeltildi: version değişmiş ama hâlâ cancellable
  durumda tek retry; terminal yarış 409 `DEAL_STATE_CONFLICT`.
- ADR-008 (cross-tenant participation) ve ADR-009 (commitment/cancellation
  consent) Accepted; ADR-INDEX ve FORBIDDEN senkronlandı.
- Legal entity seçimi kullanıcı kimliğiyle ayrıştırıldı (issue #10 kapandı).
- Repository ownership ArchUnit kuralı eklendi.

### Başlıca dosyalar

- `deal/DealService.java` (cancel retry yolu)
- `architecture/` modül/repository sınır testleri
- `architecture-decisions/ADR-008-*.md`, `ADR-009-*.md`
- `frontend/src/features/organization/legalEntitySelection.ts`

### Review odağı

- Cancel retry yolunda audit'in tam bir kez yazıldığını doğrulayın.
- ADR-008/009 kararlarının sonraki slice kodunda fiilen uygulandığını §4–§6
  bölümleriyle birlikte okuyun.

## 4. Slice 4 — Deal Invitations ve Cross-Entity Participation

### Gerçekleşen kullanıcı sonucu

- Initiator e-postayla davet oluşturur (`Idempotency-Key` zorunlu); alıcı
  kullanıcı farklı tenant'taki entity'siyle kabul eder ve participant olur.
- Reject ve pending revoke akışları; aynı Deal + normalize e-posta için tek
  PENDING invitation.
- Incoming invitations aktif entity header'ı istemez (kullanıcı-scoped context).
- Alıcı olmayan kullanıcı invitation varlığını öğrenemez (404).

### Public API

- Invitation create/list/revoke (initiator-scoped), incoming/accept/reject
  (user-scoped), Deal detail participant + pending invitation projection'ları.

### Başlıca dosyalar

- Migration: `V6__deal_participant_legal_entity_tenant_expand.sql`,
  `V7__deal_participant_switch_cross_tenant_visibility.sql`,
  `V8__http_idempotency_foundation.sql`, `V9__deal_invitation_foundation.sql`
- Backend: `deal/DealInvitation*.java`, `idempotency/` modülü
- Frontend: incoming invitation ekranları, accept entity seçimi,
  `features/deals/` participant projection'ları

### Test ve kabul kanıtı

- Expand dual-write/backfill uyumluluk testleri; switch sonrası cross-tenant
  visibility testleri; accept/revoke concurrency; idempotency aynı/farklı
  request davranışı.
- Gerçek iki-browser kabulü 2026-07-18'de tamamlandı.

### Review odağı

- Expand→switch sırasının korunması: rollback tabanı expand image'ıdır;
  ADR-008 cleanup release henüz yapılmadı (bilinçli).
- Accept transaction'ının (invitation ACCEPTED + participant insert + audit)
  atomikliği.
- Idempotency kaydının check-then-insert yarışı olmadan sahiplenildiği.

## 5. Slice 5 — Deal Parties (Buyer/Seller)

### Gerçekleşen kullanıcı sonucu

- Initiator, participant'lar arasından buyer/seller'ı atomik tek istekle atar
  (`expectedVersion` zorunlu); yalnız DRAFT'ta.
- Buyer=seller 422; non-participant taraf 422; DRAFT dışı 409; stale 409.
- Roller iki tarafta da rozet olarak görünür; activate endpoint'i yoktur.

### Public API

- `PATCH /api/v1/deals/{dealId}/parties`; Deal detail buyer/seller +
  participant `partyRoles` projection'ları; `canManageParties` action'ı.

### Başlıca dosyalar

- Migration: `V10__deal_party_participant_integrity.sql` — composite FK
  `(deal_id, legal_entity_id) -> deal_participant` + buyer≠seller CHECK
- Backend: `deal/Deal.java` (`assignParties`), `deal/DealOperationPolicy.java`,
  `deal/UpdateDealPartiesRequest.java`
- Frontend: `pages/DealDetailPage.tsx` parties bölümü

### Test ve kabul kanıtı

- Buyer≠seller domain+DB; non-participant reddi; non-initiator mutation reddi;
  stale/version artışı; parties tamamken Deal'in DRAFT kalması.
- İki-browser kabulü 2026-07-18'de tamamlandı; `mvn verify` 59 test.

### Review odağı

- Party bütünlüğünün yalnız uygulamada değil DB'de (composite FK + CHECK)
  korunduğu.
- Update/cancel yetkisinin de `DealOperationPolicy` üzerinden merkezileştiği
  (inline kontrol kalmadığı).

## 6. Slice 6 — Document Upload

### Gerçekleşen kullanıcı sonucu

- Initiator PDF/DOCX'i browser'dan doğrudan private MinIO'ya yükler (client
  SHA-256 → intent → presigned PUT → finalize, `Idempotency-Key`'li).
- Finalize storage-doğrulamalı size/checksum'a dayanır; yeni versiyon öncekini
  SUPERSEDED yapar; geçmiş ve immutable object version referansı korunur.
- Participant'lar current document'i görür/indirir; upload/finalize yalnız
  initiator'a açık. Terminal Deal'de upload kapalı.

### Public API

- Upload intent, finalize, document history, kısa ömürlü download link,
  Deal detail `currentDocument` + actor-aware document actions.

### Başlıca dosyalar

- Migration: `V11` (document foundation + same-Deal current-pointer bütünlüğü)
- Backend: `document/DocumentService.java`, `document/DocumentController.java`,
  `deal/DealDocumentMutationPort.java` + `DealDocumentMutationService.java`,
  `deal/DealDocumentReadPort.java`, `document/DealCurrentDocumentQueryService.java`,
  `integration/storage/S3DocumentObjectStorage.java`, `ObjectStorageProperties.java`
- Frontend: `features/documents/` (upload akışı, history, download)
- Infra: `infra/compose.yaml` `minio-bootstrap` (private bucket, versioning,
  dar CORS)

### Test ve kabul kanıtı

- `DocumentUploadFinalizeIntegrationTest`: idempotent replay, farklı-request
  409, mismatch'in yan etkisizliği, expired upload, non-initiator/terminal
  reddi, audit-failure rollback atomicity, concurrent finalize → tek current.
- `mvn verify` 78 test; gerçek MinIO'ya karşı iki-browser kabulü 2026-07-18.

### Review odağı

- Presign ve storage verify'ın DB transaction'ı DIŞINDA, finalize'ın tek
  transaction'da kaldığı (ADR-003 §24).
- `OBJECT_STORAGE_*` config'inin fail-fast olduğu; staging kurulumunun bu
  değişkenleri gerektirdiği (Slice 7 bağımlılığı).
- Timestamp'lerin mikrosaniyeye truncate edilme sebebi: Postgres timestamptz
  precision ↔ idempotent replay eşitliği.

## 7. Slice 7 — Railway Staging Deployment

Slice 7, ilk config-as-code merge'inden sonra gerçek Railway kurulumu, migration
failure gate, rollback, security ve browser kabulüyle 21 Temmuz 2026'da
tamamlanmıştır. Gate `C2/G4b` `ACCEPTED` durumundadır.

### Kabul edilen deployment

- Railway project `m4trust-staging`, environment `staging`.
- Public HTTPS service `m4trust-web-edge`; private Core ve PostgreSQL.
- Kabul anındaki historical Core root `/services/core-api`, config
  `/services/core-api/railway.json`; web config `/frontend/railway.json`.
- Core ve web `main@832cccab8e6f4e2c32bed8230520bdc76ec9df82` release
  identity'sinde doğrulandı.
- Kabul edilen Core deploy `b810a1f3-4a1f-4db2-9e7e-cd660b2100b0`, image
  digest `sha256:5d581b5c8670f975be3112d3f7cfc83448842dd979141ed1849118cbba7df77e`.
- Kabul edilen web deploy `9c613818-2856-4b36-a673-020e22d4b3eb`, image
  digest `sha256:53f40495437b7c57575819c6b7725bff1b9d02b287910ad88a098724a249ebdc`.

### Config-as-code

- `services/core-api/Dockerfile` (multi-stage, non-root, `run`/`migrate`
  launcher), `services/core-api/railway.json` (pre-deploy `migrate`, readiness
  gate)
- `frontend/Dockerfile` + `frontend/Caddyfile` (static SPA + `/api/*` private
  proxy, `/healthz`), `frontend/railway.json`
- `application-staging.yml` profili; `docs/DEVELOPMENT.md` "Railway staging
  hazırlığı" variable sözleşmesi

### Migration, rollback ve browser kanıtı

- `migrate` ve `run` launcher'ları ayrı çalıştırıldı; shared staging Flyway V22
  kaldı ve runtime startup migration yapmadı.
- Disposable environment'ta intentionally-invalid V23 pre-deploy aşamasında
  güvenli biçimde fail etti; önceki runtime aktif kaldı ve shared history
  kirlenmedi.
- Önceki immutable digest'e rollback V22 üzerinde migration gerektirmeden
  readiness verdi; disposable environment kanıt sonrası silindi.
- İki gerçek browser context'i register/login/session/logout, legal entity,
  Deal CRUD/cancel, deep-link ve unrelated-entity non-disclosing 404 akışını
  same-origin edge üzerinden tamamladı.
- `/healthz` security headers ile 200; public actuator 404; unauthenticated
  `/api/v1/auth/me` 401 verdi. Frontend bundle'da private host/DB variable yoktu.

### Review odağı

- Staging profilinde Flyway'in startup'ta kapalı, yalnız `migrate` komutunda
  açık olduğu.
- Secret'ların repo/image/bundle dışında kaldığı; `latest` tag'ine güven
  olmadığı.
- Kabul kanıtının RabbitMQ, object storage, FastAPI veya AI staging readiness'i
  iddia etmediği. Historical Core loglarında broker retry görülmesi HTTP
  readiness kabulünü genişletmez.
- Bu historical root/config bilgisinin Slice 15 P4'te monorepo-root build için
  `/` root contract'ıyla supersede edildiği; güncel deployment review'ünün
  [`14A–15 P4 handoff`](14a-15p4-implementation-review-handoff.md) üzerinden
  yapılması gerektiği.

## 8. Contract, persistence ve frontend çapraz kontrol tablosu

| Slice | OpenAPI alanı | Migration | Backend modülü | Frontend yüzeyi |
|---|---|---|---|---|
| 3.9 | değişiklik yok | — | `deal` (cancel retry), `architecture` | entity seçimi kullanıcı-scoped |
| 4 | invitations + participants | V6–V9 | `deal`, `idempotency` | incoming invitations, accept entity seçimi |
| 5 | parties + partyRoles | V10 | `deal` (`DealOperationPolicy`) | Deal detail parties bölümü |
| 6 | documents (intent/finalize/history/link) | V11 | `document`, `integration/storage` | `features/documents/` |
| 7 | değişiklik yok | — | `deployment` (launcher) | build-time (Caddy edge) |

`contracts/openapi/core-api-v1.yaml` public contract source of truth'tür;
`frontend/src/generated/core-api.d.ts` ondan üretilir. Elle yazılmış paralel
wire model review bulgusudur.

## 9. Önerilen review sırası

1. ADR-008 ve ADR-009'u okuyun; sonraki her bulguyu bu iki karara karşı test edin.
2. `V6`–`V11` migration zincirini expand→switch sırası, composite bütünlük ve
   tenant taşıma açısından inceleyin.
3. `DealOperationPolicy` → invitation/parties/document mutation yollarını takip
   edin; görünürlük ile mutation yetkisinin hiçbir yerde karışmadığını doğrulayın.
4. `idempotency` modülünün iki tüketicisini (invitation create, document
   finalize) karşılaştırın.
5. `DocumentService.finalizeUpload` transaction sınırlarını ve port zincirini
   inceleyin.
6. OpenAPI hata kodlarını exception handler davranışlarıyla karşılaştırın.
7. Frontend'de action görünürlüğünün yalnız backend projection'larından
   geldiğini ve Idempotency-Key retry davranışını kontrol edin.
8. Slice 7 için release identity, migration failure gate, rollback ve public/
   private topology kanıtını birlikte inceleyin.
9. Son olarak yalnız şüpheli/değişen alanın doğrulamasını çalıştırın.

## 10. Tarihsel doğrulama kanıtı

Repository kökünden:

```powershell
python .\contracts\scripts\validate_contracts.py

Set-Location .\services\core-api
.\mvnw.cmd --batch-mode --no-transfer-progress verify

Set-Location ..\..\frontend
npm ci
npm run typecheck
npm run build
```

Bu komutlar kabul tarihinde geçmiştir. Manuel iki-browser ve Railway kabul
turları da tamamlanmıştır; reviewer implementation değişmedikçe bütün tarihsel
matrisi yeniden çalıştırmak yerine kaydedilmiş kanıtı esas alabilir.

## 11. Açık takipler ve kapsam sınırı

- ADR-008 cleanup release henüz yapılmadı (bilinçli erteleme).
- Slice 7 acceptance RabbitMQ, object storage, FastAPI veya AI behavior
  kanıtlamadı; bu bilinçli scope sınırıdır.
- Slice 8 ve sonraki capabilities bu handoff'tan sonra uygulanmıştır; buradaki
  tarihsel ifadeler güncel proje state'i olarak kullanılmamalıdır.
- Slice 15 P4, Core Railway build root/config sözleşmesini daha sonra değiştirdi.
  Yeni deploy historical `/services/core-api` root ayarını kopyalamamalıdır.

Bu handoff'taki sınırlar Slice 3.9–7'nin kabul durumunu geri almaz ve sonraki
slice'lara sessiz kapsam genişletme yetkisi vermez.
