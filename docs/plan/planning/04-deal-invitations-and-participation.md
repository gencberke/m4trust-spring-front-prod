# Slice 4 — Deal Invitations ve Cross-Entity Participation

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 4 (yol haritasında bölünmüş hali)
- Öncül: 03-deal-creation-and-listing (done), 03.9-hardening-and-decisions
  (PR #12 merge edilmeden bu plan `ready/` olmaz — ADR-008 o branch'tedir)
- Ardıl: 05-deal-parties-and-activation

## 1. Amaç ve kullanıcı sonucu

Kullanıcı A (Alpha entity'siyle) gerçek tarayıcıda: deal'ine karşı tarafı
e-postayla davet eder → kullanıcı B ikinci browser profilinde girişte davetini
görür → daveti hangi legal entity'siyle kabul edeceğini seçer (Beta — farklı
tenant) → Beta deal'in participant'ı olur → B deal'i listesinde görür ve
detayını açar → A, participant listesinde Beta'yı görür. Reject ve revoke
akışları da tarayıcıda çalışır.

Bu slice iki kalıcı şeyi kurar: **cross-tenant görünürlük modeli** (ADR-008'in
koda inmesi) ve **HTTP Idempotency-Key altyapısı** (sonraki document finalize
ve payment slice'ları aynı mekanizmayı kullanır).

## 2. Kapsam / kapsam dışı

Kapsam:

- ADR-008 §3 migration **adım 1 + adım 2**: `deal_participant` tablosuna
  `legal_entity_tenant_id` (expand: nullable + backfill + yeni FK), görünürlük
  sorgularının yalnız participant eksenine geçmesi, `deal.tenant_id`
  predicate'inin erişim sorgularından kalkması, eski entity FK'sının drop'u ve
  kolonun NOT NULL yapılması. Adım 3 (kalan kısıt/indeks temizliği) ayrı
  release — kapsam dışı.
- `DealInvitation` aggregate (deal modülü, ADR-003 §4.3): PENDING →
  ACCEPTED / REJECTED / REVOKED durum makinesi
- **Davet modeli (karar, bağlayıcı — insan onaylı):** davet, alıcının
  normalize e-postasına kesilir. Alıcı girişte davetlerini görür ve kabul
  anında hangi legal entity'siyle katılacağını seçer. Kayıtsız e-posta için
  davet bekler; o e-postayla kayıt olunduğunda görünür. E-posta GÖNDERİMİ yok
  (provider yok) — davet yalnız in-app görünür.
- Davet gönderme endpoint'inde **Idempotency-Key** (ADR-006 §24 aday
  listesinde açıkça var — bağlayıcı). Server-side idempotency kaydı ADR-006
  §25'teki alanlarla kurulur; mekanizma modül-bağımsız yeniden kullanılabilir
  olmalıdır.
- Kabulün `deal_participant` satırına dönüşmesi (entity'nin KENDİ tenant'ıyla
  — ADR-008 §2.3, §2.7)
- Frontend: gelen davetler görünümü, deal detayında participant + davet
  yönetimi, kabul akışında entity seçici
- Audit: invite/accept/reject/revoke aynı transaction'da (ADR-003 §24)

Kapsam dışı:

- Buyer/seller ataması ve DRAFT→ACTIVE → **Slice 5**
- E-posta bildirimi/gönderimi (provider entegrasyonu ayrı iş)
- Legal entity ÜYELİK davetleri (kullanıcıyı entity'ye üye yapma — bu slice
  yalnız deal katılımıdır; Slice 2 kapsam dışı kararı sürer)
- Davet listelerinde pagination (küçük listeler; ADR-006 stabil `items`
  DTO'su yine kullanılır)
- ADR-008 §3 adım 3 (contract temizliği)

## 3. Okunacak ADR bölümleri

- **ADR-008 tamamı** (bu slice'ın ana kaynağı — kısa bir ADR'dir)
- ADR-003 §4.3 (deal modülü davet sahipliği), §5 (tenant ≠ legal entity),
  §23–25 (modül erişimi, transaction, concurrency)
- ADR-006 §4 (action endpoint'leri), §19–20 (409 ve 403/404 ayrımı — davet
  non-disclosure), §24–25 (Idempotency-Key davranışı)
- ADR-005 §9 (iki-browser test zemini), §20–21 (context header, authorization)
- ADR-004 §22–23 (checklist, Done tanımı)

## 4. Public API yüzeyi

Yüzey implementasyondan ÖNCE `core-api-v1.yaml`'a tasarlanır (ADR-006 §42–43).
Taslak — kesin şekil OpenAPI tasarım fazında:

- `POST /api/v1/deals/{dealId}/invitations` — davet oluşturma; Idempotency-Key
  header zorunlu; 201. Aynı key farklı request → 409 `IDEMPOTENCY_KEY_REUSED`.
- `GET /api/v1/deals/{dealId}/invitations` — deal'in davetleri (gönderen taraf
  görünümü; participant erişimi gerektirir)
- Alıcı tarafı için "gelen davetlerim" okuma yüzeyi (örn.
  `GET /api/v1/invitations`; authenticated kullanıcının normalize e-postasına
  kesilen PENDING davetler — aktif entity header'ı GEREKTİRMEZ, çünkü alıcı
  henüz entity seçmemiştir; planner endpoint adını/konumunu netleştirir)
- `POST /api/v1/invitations/{invitationId}/accept` — body'de seçilen
  `legalEntityId`; kullanıcının o entity'de membership'i server-side doğrulanır
- `POST /api/v1/invitations/{invitationId}/reject`
- `POST /api/v1/invitations/{invitationId}/revoke` — davet eden taraf

Sabitlenen contract kararları:

- Davet önizlemesi **minimaldir**: deal title + davet eden entity'nin adı;
  başka deal alanı sızmaz. Alıcı, kabul edene kadar deal detayına erişemez.
- Davet oluşturma response'u, davet edilen e-postanın kayıtlı bir hesaba ait
  olup olmadığını AÇIKLAMAZ (enumeration koruması, ADR-005 §15 ruhu).
- Davet action'ları hata modeli: geçersiz durum geçişi (kabul edilmiş daveti
  revoke etme vb.) → 409; davet alıcısı olmayan kullanıcının accept denemesi →
  404 (varlık sızdırılmaz).
- Deal davranış değişikliği: participant artışıyla mevcut list/detail
  contract'ı DEĞİŞMEZ; detail response'una participant listesi eklenir
  (yeni optional/required alan tasarımı OpenAPI fazında, ADR-006 §47 additive
  kuralları içinde).

## 5. Backend yönlendirmesi

- **Migration (ADR-008 §3, ADR-007 §24–25):** iki Flyway adımı önerilir —
  V6 expand (kolon + backfill + yeni FK, uygulama çift-yaz), V7 switch/daralt
  (eski FK drop + NOT NULL). Local'de ikisi art arda uygulanır; rollout sırası
  staging/prod'da release'lere bölünebilir. Planner adım sınırlarını ADR-007
  expand–contract kuralına göre kesinleştirir. Uygulanmış migration'a
  dokunulmaz.
- **Görünürlük sorguları (ADR-008 §2.4):** `DealRepository` erişim
  predicate'i "participant.legal_entity_id = aktif entity AND
  participant.legal_entity_tenant_id = context.tenantId" eksenine taşınır;
  `deal.tenant_id = ?` erişim filtresi olmaktan çıkar. FORBIDDEN'a eklenen
  yasaklar geçerlidir: tenant eşleşmesini erişim koşulu yapma geri gelmez.
- **DealInvitation:** deal modülünde ayrı aggregate; durum geçişleri aggregate
  davranış metotlarında tek yerde (Slice 3 `Deal`/`DealStatus` deseni izlenir);
  optimistic lock `version` alanı taşır. Davet kabulü tek transaction'da:
  invitation ACCEPTED + participant insert + audit (+ gerekli inbox/outbox yok
  — henüz external event yok).
- **Idempotency mekanizması:** ADR-006 §25 alanlarıyla (actor, tenant,
  operation type, key, canonical request hash, result reference, expiry)
  kalıcı kayıt; modüller port üzerinden kullanır (ADR-003 §23). Tablo ve
  bileşen adlandırması implementer'a; davranış: aynı key + aynı canonical
  request → ilk sonucu döndür, aynı key + farklı request → 409.
- **Alıcı eşleme:** davet e-postası `identity` modülündeki normalize e-posta
  kurallarıyla (Slice 1 `EmailAddress`) aynı biçimde normalize edilir. Alıcının
  davetlerini listeleme, kullanıcının e-postası üzerinden yapılır; deal
  modülü identity'ye yalnız port/ID üzerinden erişir (ADR-003 §23).
- **Authorization:** davet oluşturma/revoke participant yetkisi ister ve
  `OperationContext` mekanizmasından geçer; accept/reject ile "gelen
  davetlerim" listesi entity header'sız çalışır (kullanıcı-scoped) — bu ayrım
  `OperationContext` çözümlemesinde mevcut annotation modelinin genişletilmesi
  olabilir; kopyala-yapıştır kontrol yasak (Slice 2 kuralı).
- Davet eden entity'nin davet anında hâlâ participant olduğu, deal'in terminal
  durumda olmadığı (CANCELLED/ARCHIVED deal'e davet → 409) doğrulanır.

## 6. Frontend yönlendirmesi

- Gelen davetler: authenticated layout'ta görünür bir giriş (topbar rozeti
  veya organizasyon sayfasında bölüm — implementer seçer); davet kartında
  minimal önizleme + kabul (entity seçici) / reddet aksiyonları. Kullanıcının
  hiç entity'si yoksa kabul akışı önce entity oluşturmaya yönlendirir.
- Deal detayı: participant listesi + davet bölümü (bekleyen davetler, revoke).
  Buton görünürlüğü backend'in verdiği action/availability bilgisinden gelir;
  status kombinasyonundan türetilmez (ADR-006 §41).
- Davet gönderme formu Idempotency-Key üretir (UUID) ve tekrar denemelerde
  aynı key'i korur; double-click yalnız client-side disable'a bırakılmaz.
- İki-browser akışı Slice 2–3'teki test zeminiyle çalışır; entity seçimi
  per-kullanıcı mekanizması (3.9) aynen kullanılır.
- Tipler committed OpenAPI'den üretilir; hata dallanması `code` ile.

## 7. Kabul testi (tarayıcı akışı)

1. A (Alpha) deal detayında B'nin e-postasına davet gönderir; bekleyen davet
   listede görünür
2. B ikinci browser profilinde girer; davetini görür (deal title + Alpha adı
   dışında bilgi yok)
3. B daveti Beta entity'siyle kabul eder → deal B'nin listesinde belirir;
   detayını açar
4. A'nın ekranında Beta participant olarak görünür (yenileme/refetch yeterli)
5. B olmayan üçüncü kullanıcı davete erişemez; A'nın davet listesi B dışına
   görünmez
6. A ikinci bir daveti revoke eder → B tarafında davet kaybolur/reddedilmiş
   görünür; revoke edilmiş davet kabul edilemez (409 tarayıcıda gözlenir)
7. Reject akışı: B daveti reddeder → A tarafında REJECTED görünür
8. Davet formunda çift tıklama/tekrar gönderme ikinci davet YARATMAZ
   (idempotency)

## 8. Minimum invariant testleri

- Cross-tenant kabul: farklı tenant'taki entity participant olur ve deal'i
  görür; participant olmayan entity için 404 non-disclosure sürer
- Görünürlük sorgusunun `deal.tenant_id` ile DEĞİL participant ilişkisiyle
  çalıştığı (farklı tenant'tan okuma testi)
- Migration backfill: mevcut participant satırları `legal_entity_tenant_id`
  değerini doğru alır
- Davet durum makinesi: REVOKED/REJECTED/ACCEPTED davet tekrar accept
  edilemez (409); terminal deal'e davet oluşturulamaz
- Idempotency: aynı key + aynı request → tek davet, ilk sonuç; aynı key +
  farklı request → 409 `IDEMPOTENCY_KEY_REUSED`
- Kabul transaction'ı: invitation + participant + audit tek transaction
  (rollback testi — Slice 2/3 atomicity test deseni)
- Davet alıcısı olmayan kullanıcı accept edemez (404)

## 9. Açık sorular / karar noktaları

- Davet expiry: öneri — başlangıçta yok (REVOKE yeterli); eklenirse ayrı
  durum + zaman kontrolü gerekir. Planner onayıyla kesinleşir.
- Aynı deal + aynı e-posta için ikinci PENDING davet: öneri — 409 (duplicate);
  reddedilmiş/revoke edilmiş davet sonrası yeni davet serbest.
- "Gelen davetlerim" yüzeyinin yeri (`/invitations` vs `/me/invitations` vb.)
  ve deal detail response'una participant listesinin ekleniş biçimi — OpenAPI
  tasarım fazında.
- V6/V7 migration'larının tek PR'da mı iki PR'da mı ilerleyeceği (local
  geliştirmede fark yok; staging rollout'u Slice 7 zamanlamasına bağlı).

## 10. Done tanımı

- [ ] OpenAPI yüzeyi implementasyondan önce tasarlandı (davet endpoint'leri,
      Idempotency-Key işaretlemesi, hata kodları)
- [ ] ADR-008 migration adım 1+2 uygulandı; görünürlük sorguları participant
      eksenine geçti; mevcut Slice 3 kabul akışları regresyonsuz
- [ ] Davet oluştur/kabul/reddet/revoke gerçek Spring + PostgreSQL üzerinde
      çalışıyor; kabul cross-tenant participant üretiyor
- [ ] Idempotency mekanizması kuruldu, davet gönderme üzerinde çalışıyor ve
      yeniden kullanılabilir durumda
- [ ] §8 invariant testleri geçiyor; audit kayıtları aynı transaction'da
- [ ] §7 iki-browser akışı baştan sona gerçek tarayıcıda çalıştırıldı
- [ ] Frontend'de gelen davetler, participant listesi, entity seçici; loading/
      error/empty/yetkisiz durumlar mevcut
- [ ] Contract validator + `mvn verify` + frontend typecheck/build yeşil
