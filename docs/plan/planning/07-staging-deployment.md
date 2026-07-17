# Slice 7 — Staging Deployment (Railway)

- Durum: planning
- Slice sırası: operasyonel slice; 04–06 local geliştirmesiyle paralel yürüyebilir
- Öncül: ADR-007; kabul edilmiş uygulama slice'ları
- Ardıl: production kurulumu ve AI servislerinin staging'e eklenmesi

## 1. Amaç ve kullanıcı sonucu

Main'e merge edilen immutable image'lar staging'e deploy edilir. Kullanıcı gerçek
HTTPS URL'inde production cookie profiliyle register/login, legal entity ve Deal
akışlarını çalıştırır. Migration rollout'tan önce tek kontrollü adımda uygulanır.

Staging, Slice 4–6 local kodlamasını bloklamaz; ancak ADR-008 switch release'i ve
sonraki cross-tenant kabulü staging rollout/rollback kanıtı olmadan tamamlanmış
sayılmaz.

## 2. Kapsam / kapsam dışı

Kapsam:

- Public web-edge, private core-api ve Railway PostgreSQL
- Explicit core-api ve web-edge Dockerfile'ları
- Commit SHA ile immutable image/release kimliği
- Tek pre-deploy Flyway migration adımı
- Environment/secret yönetimi
- Main merge sonrası staging deploy hattı
- Browser smoke, migration gate ve rollback provası

Kapsam dışı:

- Production ortamı
- FastAPI/Mock AI Worker ve RabbitMQ
- Staging object storage (Slice 6 staging kabulünde seçilir)
- Merkezi monitoring sağlayıcısı, canary/blue-green

## 3. Okunacak ADR bölümleri

- ADR-007 §2–8, §15–28, §29–33, §38, §41–42
- ADR-005 §5, §12
- ADR-004 §22–23
- ADR-008 §3
- FORBIDDEN deployment bölümü

## 4. Public API yüzeyi

Core public OpenAPI değişmez.

Operasyonel yüzey:

- Web-edge public origin; frontend relative `/api/v1` kullanır.
- Core liveness/readiness private network üzerinden doğrulanır.
- Info/log alanlarında build version ve git commit SHA görünür.
- Browser internal core-api veya database adresini görmez.

## 5. Backend / altyapı yönlendirmesi

### Image ve ağ

- Core API multi-stage, non-root ve secret'sız image'dır.
- Web edge static frontend, SPA fallback ve `/api/*` private proxy davranışını
  sağlar.
- Core API ve PostgreSQL public internete açılmaz.
- Image'lar commit SHA ile tag'lenir; `latest` rollback hedefi değildir.

### Migration

- Flyway yalnız pre-deploy adımında çalışır.
- Staging web process'inde startup migration açıkça kapatılır; her replica migration
  çalıştırmaz.
- Migration başarısızsa yeni image rollout'u başlamaz.
- Failure gate ortak staging Flyway history'sini kirletecek sahte migration ile
  denenmez; disposable database/environment kullanılır.
- Expand–contract rollout'larında rollback yalnız mevcut şemayla uyumlu önceki
  image'a yapılır. ADR-008 switch sonrasında doğrudan Slice 3 image'ına dönüş
  hedeflenmez; expand/dual-write image rollback tabanıdır.

### Runtime güvenliği

- Production-default secure cookie profili staging'de korunur.
- Web-edge proxy header aktarımı ve Spring forwarded-header stratejisi explicit
  olarak yapılandırılır; scheme/host/cookie davranışı browser'da doğrulanır.
- Secret'lar Railway variables içindedir; repo/image/frontend bundle'a girmez.
- `APP_VERSION` yanında commit SHA release identity olarak log/info'ya taşınır.

## 6. Frontend yönlendirmesi

Business UI değişikliği beklenmez.

- Production build relative API kullanır.
- SPA deep-link refresh çalışır.
- Frontend bundle'da private service URL veya secret bulunmaz.
- Same-origin cookie/CSRF davranışı gerçek staging origin'inde doğrulanır.

## 7. Kabul testi

1. HTTPS staging URL ve uygulama kabuğu açılır.
2. Register/login/logout/refresh production cookie profiliyle çalışır.
3. Legal entity create/select ve Deal create/list/detail/update/DRAFT cancel smoke'u
   tamamlanır.
4. Deep-link refresh SPA fallback ile çalışır.
5. API same-origin akar; core-api ve PostgreSQL public değildir.
6. Pre-deploy migration logu ve release commit SHA görülür.
7. Disposable ortamda migration failure rollout'u durdurur.
8. Şemayla uyumlu önceki image'a rollback provası yapılır.

Slice 4 switch release'i hazır olduğunda ayrıca cross-tenant browser kabulü bu
ortamda çalıştırılır.

## 8. Minimum invariant testleri

Yeni business invariant testi yoktur.

- Mevcut contract/build doğrulamaları
- İki image build'i
- Migration failure gate
- Browser smoke
- Şema-uyumlu rollback
- Secret/bundle/network spot check

## 9. Açık sorular / karar noktaları

- Staging deploy main merge sonrası otomatik önerilir.
- Railway default domain ilk aşamada yeterlidir.
- Web server ürünü ve Railway komut biçimi implementasyon detayıdır; davranış bu
  planla sabittir.
- Staging test verisi normal UI akışıyla oluşturulur; destructive reset yoktur.

## 10. Done tanımı

- [ ] Core-api ve web-edge immutable image'ları CI'da build oluyor
- [ ] Public edge, private core-api ve private PostgreSQL ayakta
- [ ] Flyway yalnız pre-deploy adımında çalışıyor
- [ ] Failure gate disposable ortamda kanıtlandı
- [ ] Production cookie ve forwarded-header davranışı doğrulandı
- [ ] Commit SHA release identity olarak görünür
- [ ] §7 browser smoke tamamlandı
- [ ] Şema-uyumlu rollback provası yapıldı
- [ ] Secret'lar repo/image/bundle dışında ve DEVELOPMENT.md güncel
