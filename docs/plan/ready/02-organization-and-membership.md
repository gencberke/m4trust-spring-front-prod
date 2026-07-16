# Slice 2 — Tenant, Legal Entity ve Membership

- Durum: ready
- Slice sırası: ADR-004 §24 → Slice 2
- Öncül: 01-authentication
- Ardıl: 03-deal-creation-and-listing

## 1. Amaç ve kullanıcı sonucu

Kullanıcı gerçek tarayıcıda: bir legal entity oluşturur → üyeliklerini görür → aktif legal entity'sini seçer → sonraki isteklerinde bu bağlamla işlem yapar. Yetkili olmadığı bir legal entity adına istek attığında sistem 403 döner.

Bu slice ayrıca sonraki TÜM slice'ların dayanacağı application-layer authorization iskeletini kurar; bu iskeletin kalitesi projenin geri kalanını belirler.

## 2. Kapsam / kapsam dışı

Kapsam:

- Tenant modeli — **karar (bağlayıcı):** tenant, register sırasında otomatik oluşturulur (tek-tenant-per-kayıt basitleştirmesi). Kullanıcıya "tenant yönetimi" ekranı sunulmaz; tenant teknik izolasyon sınırıdır (ADR-003 §5).
- Legal entity oluşturma ve listeleme (kullanıcının üye olduğu entity'ler)
- Legal entity membership: entity'yi oluşturan kullanıcı otomatik olarak yönetici rolüyle üye olur
- Aktif legal entity bağlamı: `X-M4Trust-Legal-Entity-Id` header'ı + server-side membership doğrulaması
- Authorization bağlam nesnesi: `authenticatedUserId + tenantId + activeLegalEntityId + requestedOperation` (ADR-003 §28, ADR-005 §21)
- Frontend: entity oluşturma formu, entity listesi, aktif entity switcher, üye listesi görüntüleme

Kapsam dışı:

- Başka kullanıcıyı entity'ye davet etme / üye ekleme-çıkarma (davet mekanizması Deal davetleriyle birlikte Slice 4'te veya ayrı bir slice'ta ele alınır — planner bu sınırı korur)
- Rol/permission matrisinin tam tasarımı (bu slice'ta minimum rol seti yeter: örn. ADMIN + MEMBER; genişletme ihtiyacı doğduğunda ayrı karar)
- Entity silme/arşivleme
- Çoklu tenant senaryoları, tenant'lar arası herhangi bir görünürlük

## 3. Okunacak ADR bölümleri

- ADR-003 §4.2 (organization modülü), §5 (multi-tenancy ve business actor ayrımı — tenantId ≠ legal entity), §28 (authorization bağlamı)
- ADR-005 §20 (legal entity context header kuralları), §21 (authorization katmanı)
- ADR-006 §4 (action endpoint'leri), §9–12 (liste/pagination), §17–20 (status politikası, özellikle 403/404 ayrımı)
- ADR-004 §22 (checklist), §24 (iki-browser test yaklaşımı)

## 4. Public API yüzeyi

`core-api-v1.yaml`'a eklenecek yüzey (implementasyondan önce tasarlanır):

- `POST /api/v1/legal-entities` — entity oluşturma; oluşturan otomatik admin üye; 201
- `GET /api/v1/legal-entities` — kullanıcının üye olduğu entity'ler (küçük liste; pagination bu endpoint'te zorunlu değilse ADR-006 liste DTO'su yine kullanılır)
- `GET /api/v1/legal-entities/{legalEntityId}` — detay; üye olmayan için 404 (varlık sızdırılmaz, ADR-006 §20)
- `GET /api/v1/legal-entities/{legalEntityId}/members` — üye listesi
- `GET /api/v1/auth/me` — mevcut `id/email/displayName` alanlarını koruyan ve required, non-null `memberships` listesi ekleyen bootstrap projection; register/login response'ları mevcut `PublicUser` wire formatını korur

Sabitlenen public contract kararları:

- Minimum rol seti kapalı enum olarak `ADMIN` + `MEMBER`'dır.
- Create request yalnız required `legalName` (trim sonrası 1–200) ve required `registrationNumber` (trim sonrası 1–100) alır. Registration number için global uniqueness, ülke veya vergi semantiği varsayılmaz.
- `GET /legal-entities` yalnız authenticated kullanıcının membership listesidir; `POST /legal-entities` ile birlikte aktif entity header'ı gerektirmez.
- Aktif entity seçimi authoritative server state değildir; client seçimi scoped isteklerde `X-M4Trust-Legal-Entity-Id` olarak gönderilir.
- Detail ve member-list okumalarında header path'teki entity ile eşleşir ve membership server-side doğrulanır. Eksik/geçersiz/eşleşmeyen header 403 `LEGAL_ENTITY_ACCESS_DENIED`; bulunmayan veya kullanıcıdan gizlenen entity aynı 404 `LEGAL_ENTITY_NOT_FOUND` response'unu üretir.
- Liste endpoint'leri Slice 2'de pagination olmadan stabil `items` DTO'ları döndürür; collection alanları hiçbir zaman null olmaz.

## 5. Backend yönlendirmesi

- Modül: `organization` (ADR-003 §4.2). Tenant, LegalEntity, Membership aggregate'leri burada. `identity` modülü DEĞİŞTİRİLMEZ; register akışına tenant oluşturma, identity'nin organization port'unu çağırmasıyla veya internal domain event ile bağlanır (modüller arası repository erişimi yasak, ADR-003 §23).
- Flyway: tenants, legal_entities, legal_entity_memberships tabloları. Tüm business tablolarında `tenantId` (ADR-003 §5), UUID id, timestamptz, optimistic lock `version` (ADR-003 §27).
- Register akışı güncellemesi: kullanıcı oluşturulurken aynı transaction içinde tenant oluşturulur ve kullanıcıya bağlanır. Mevcut Slice 1 kabul akışı bozulmamalıdır.
- **Aktif legal entity bağlamı (kritik tasarım):**
  - Header authoritative server session state olarak TUTULMAZ (ADR-005 §20 — farklı tab'ların bağlamı çakışmasın diye); her istekte header okunur ve membership ile doğrulanır.
  - Doğrulama tek bir yerde yapılır: bir resolver/interceptor header'ı okur, membership'i kontrol eder, doğrulanmış `OperationContext` nesnesini application service'e verir. Sonraki slice'lar bu mekanizmayı aynen kullanır — kopyala-yapıştır authorization kontrolü yasak.
  - Header geçersiz/eksikken organization-scoped endpoint'ler 403 (`LEGAL_ENTITY_ACCESS_DENIED`) döner; entity'nin varlığını sızdırmamak gereken yerde 404 tercih edilir. Endpoint bazında hangi davranışın seçildiği OpenAPI'de yazılır.
- Authorization application katmanında kesinleşir; controller annotation'ları tek başına yeterli değildir (ADR-005 §21).
- Audit: entity oluşturma ve membership ataması append-only audit kaydı üretir; audit business mutation ile aynı transaction'da yazılır (ADR-003 §24). Bu slice audit modülünün ilk gerçek kullanıcısıdır — basit bir audit yazma port'u kurmak yeterli, tam audit sorgulama ekranı kapsam dışı.

## 6. Frontend yönlendirmesi

- Ekranlar: entity oluşturma formu, "entity'lerim" listesi (boş state: hiç entity yoksa kullanıcı oluşturmaya yönlendirilir), üye listesi, header/topbar'da aktif entity switcher.
- Aktif entity seçimi client state'tir (örn. localStorage'da entity ID — credential değildir, saklanabilir); sayfa yenilemede korunur. Ancak frontend seçimi yetki kanıtı olarak KULLANMAZ; her istek header'la gider ve backend doğrular.
- Fetch wrapper güncellemesi: seçili entity varsa `X-M4Trust-Legal-Entity-Id` header'ını otomatik ekler.
- 403 durumu kullanıcıya anlamlı gösterilir (generic hata değil); seçili entity artık geçersizse (membership kaldırılmışsa) switcher sıfırlanır.
- Tipler committed OpenAPI'den üretilir.

## 7. Kabul testi (tarayıcı akışı)

1. Kullanıcı kayıt olur (tenant otomatik oluşur — kullanıcıya görünmez)
2. Hiç entity'si yokken boş state görür ve oluşturma akışına yönlenir
3. Legal entity oluşturur → listede görür → otomatik admin üyesi olduğunu üye listesinde görür
4. Aktif entity'yi seçer; sayfa yenilemede seçim korunur
5. İkinci browser profilinde ikinci kullanıcı kayıt olur, kendi entity'sini oluşturur
6. İkinci kullanıcı birinci kullanıcının entity ID'siyle (elle URL/istek) erişmeyi dener → 404/403 alır, hiçbir veri sızmaz
7. Header'sız veya geçersiz header'lı organization-scoped istek 403 döner (dev araçlarıyla doğrulanabilir)

## 8. Minimum invariant testleri

- Membership'i olmayan kullanıcının entity verisine erişememesi (authorization sınırı — ADR-004 §7 listesinde açıkça var)
- Header'daki entity ID'nin membership ile doğrulandığı (sahte ID → red)
- Register + tenant oluşturmanın aynı transaction'da olduğu (yarım kayıt kalmaz)
- Entity oluşturanın otomatik admin üye olduğu

## 9. Açık sorular / karar noktaları

- Phase 1 public API tasarımında açık karar kalmadı. Rol seti, bootstrap projection, minimum create alanları, header kapsamı ve non-disclosure davranışı §4'te sabitlendi.

## 10. Done tanımı

- [x] OpenAPI yüzeyi implementasyondan önce tasarlandı
- [x] Tenant otomatik oluşturma register akışına eklendi; Slice 1 kabul akışı hâlâ geçiyor
- [x] Entity oluşturma/listeleme/üye listesi gerçek çalışıyor
- [x] `OperationContext` + header doğrulama mekanizması kuruldu ve TEK noktadan geçiyor
- [x] Audit kayıtları business mutation ile aynı transaction'da yazılıyor
- [ ] İki-browser izolasyon testi (§7 adım 5–6) manuel çalıştırıldı
- [x] §8 invariant testleri geçiyor
- [x] Frontend'de loading/error/empty durumları ve entity switcher implemente edildi; typecheck/build geçiyor (manuel tarayıcı kabulü kullanıcıda)
