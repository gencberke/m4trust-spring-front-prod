# Slice 7 — Staging Deployment (Railway)

- Durum: planning
- Slice sırası: yol haritası 07 — operasyonel slice; **04–06 ile paralel
  yürüyebilir**, hiçbirini bloklamaz
- Öncül: ADR-007 (Accepted); Slice 0–3 done (deploy edilecek uygulama bu)
- Ardıl: production kurulumu (ayrı iş, kapsam dışı); AI slice öncesi staging
  RabbitMQ eklentisi (ayrı not)

## 1. Amaç ve kullanıcı sonucu

Slice 0 gibi doğrudan business capability sunmaz; hedefi paylaşılan gerçek
kabul ortamıdır: geliştirici main'e merge eder → CI image'ları build eder →
staging'e deploy olur (pre-deploy Flyway migration ile) → kullanıcı gerçek
staging URL'ini tarayıcıda açar ve Slice 1–3 akışlarını (register/login,
entity, deal) HTTPS + production cookie profiliyle çalıştırır.

Bu ortam kurulduktan sonra sonraki slice'ların (4–6+) kabul testleri
paylaşılan staging üzerinde de koşturulabilir (ADR-007 §3.2: "frontend kabul
testleri için ana paylaşımlı ortam").

## 2. Kapsam / kapsam dışı

Kapsam (insan kararıyla daraltıldı):

- Railway staging ortamı: **m4trust-web-edge (public) + m4trust-core-api
  (private) + Railway PostgreSQL** — bu kadar
- Repo'ya explicit Dockerfile'lar (şu an yok): core-api ve web-edge
- Pre-deploy Flyway migration hattı (ADR-007 §23)
- Environment/secret yönetimi: Railway variables, repo'da `.env.example`
- CI köprüsü: image build + commit SHA tag + staging deploy tetikleyicisi
- Staging smoke/kabul akışı ve DEVELOPMENT.md'ye staging bölümü

Kapsam dışı:

- **Production ortamı** (manual approval'lı ayrı iş — ADR-007 §26, §39)
- **Object storage** — insan kararıyla ertelendi: Slice 6 kabulü local
  MinIO ile yapılır; staging storage kararı (harici S3-compatible vs Railway
  public MinIO) Slice 6 staging'e taşınırken verilir. Bu plana yalnız karar
  kaydı düşülür.
- **RabbitMQ** — AI slice (08) öncesinde staging'e eklenir; şimdi kurulmaz
  (YAGNI). Not: eklenirken private + persistent volume + durable (ADR-007 §13).
- FastAPI/Mock AI Worker servisleri, custom domain zorunluluğu, merkezi
  logging/monitoring sağlayıcısı, blue/green–canary (ADR-007 §44–45)

## 3. Okunacak ADR bölümleri

- **ADR-007 tamamı okunmaz; şu bölümler zorunlu:** §2–8 (platform, ortamlar,
  topoloji, edge, same-origin, frontend/Spring deployment), §11 (PostgreSQL),
  §15–19 (image stratejisi, Railway build, monorepo, config/secret), §21–28
  (Flyway staging/prod, forward-only, pipeline, CI kapsamı, release kimliği),
  §29–33 (health, log), §41–42 (port, domain/TLS/forwarded headers)
- ADR-005 §5 (production cookie profili — staging'de aynen), §12 (production
  deployment'a bağlı authentication kuralları)
- ADR-004 §27 (yasak: production benzeri ortamda gerçek para/operasyon —
  staging test verisiyle çalışır)
- FORBIDDEN §6 (deployment yasakları — özellikle `latest` tag, secret,
  flyway clean, replica migration)

## 4. Public API yüzeyi

Public API değişikliği YOK; `core-api-v1.yaml`'a dokunulmaz. Bu slice'ın
"yüzeyi" operasyoneldir:

- Staging public origin'i: Railway default domain (karar önerisi — custom
  domain açık soru). Frontend, API'yi relative `/api/v1` ile çağırır
  (ADR-007 §20); same-origin korunur (§6).
- Health yüzeyleri: web-edge temel health, core-api liveness/readiness
  (mevcut actuator grupları) — public OpenAPI'ye eklenmez (Slice 0 kararı).
- Release kimliği (ADR-007 §28): gitCommitSha/buildVersion info endpoint'i
  ve log alanlarında görünür; mevcut `app.version` config'i CI'dan beslenir.

## 5. Backend / altyapı yönlendirmesi

- **Dockerfile'lar (ADR-007 §15–16):**
  - `services/core-api`: multi-stage (Maven build → JRE runtime), non-root
    user, immutable image, secret'sız; `PORT` env'den (mevcut
    `SERVER_PORT:${PORT:8080}` config'i buna hazır).
  - Web edge: frontend production build'ini üretip static sunan + `/api/*`'ı
    core-api'nin private adresine proxy'leyen küçük image. Web server ürünü
    (Caddy önerilir, Nginx kabul) implementer'a; zorunlu davranış listesi
    ADR-007 §7: SPA fallback, asset caching, forwarded headers, request body
    sınırı, security header'ları, FastAPI route'u YOK.
  - Her ikisi commit SHA ile tag'lenir; production'a promote edilebilir
    (aynı image ilkesi — §15). `latest`'e güvenilmez.
- **Railway kurulumu:** staging ortamı/projesi; core-api yalnız private
  network'te (public domain'i yok), web-edge public; PostgreSQL Railway
  eklentisi. Staging DB'si local/prod ile hiçbir kaynak paylaşmaz (§3.3
  listesi). Backup özelliği staging'de gözlemlenir, production kararına veri
  sağlar (§12, §35 — doğrulama production işine kalır).
- **Config/secret (ADR-007 §18–19):** tüm bağlantı bilgileri Railway
  variables; repo'ya yalnız placeholder'lı `.env.example`. Zorunlu config
  eksikse fail-fast (mevcut davranış). `SPRING_PROFILES_ACTIVE` staging'de
  local profili KULLANMAZ — production-default config (secure cookie,
  `__Host-` adı) geçerlidir; staging'e özel gevşetme yapılmaz (ADR-005 §12).
- **Forwarded headers (ADR-007 §42):** TLS Railway edge'de biter; Spring'in
  scheme/host'u doğru görmesi için forwarded-header stratejisi ve web-edge
  proxy header aktarımı birlikte doğrulanır — Secure cookie ve redirect
  davranışının kabul kriteri budur.
- **Pre-deploy migration (ADR-007 §23):** Railway pre-deploy adımı Flyway
  migrate çalıştırır; migration başarısızsa rollout durur. Uygulama
  startup-migrate'i staging'de devre dışı/pre-deploy'a devredilmiş olur —
  mekanizmanın seçimi (ayrı komut/profil) implementer'a; "her replica migrate
  çalıştırır" durumu oluşmamalıdır (FORBIDDEN §6).
- **CI köprüsü (ADR-007 §26–27):** mevcut workflow'lara image build eklenir;
  staging deploy tetikleyicisi öneri olarak main merge sonrası otomatik
  (§26 izin verir) — kesin tercih açık soru. Production adımı YOK.
- **Seed/reset:** staging'de destructive reset komutu bulunmaz (ADR-007
  §3.3); test verisi normal register/UI akışlarıyla oluşturulur. Staging seed
  ihtiyacı doğarsa ayrı, açık mekanizma (ADR-004 §21) — açık soru.

## 6. Frontend yönlendirmesi

- Kod değişikliği beklenmez: production build zaten relative `/api/v1`
  kullanıyor; `PUBLIC_*` dışı env değeri bundle'a girmez (ADR-007 §20 —
  build sırasında doğrulanır).
- Web-edge image'ı frontend build'ini içerir; SPA fallback sayesinde derin
  linkler (örn. `/app/deals/{id}`) yenilemede çalışır — kabul akışında
  test edilir.

## 7. Kabul testi (tarayıcı akışı — gerçek staging URL'inde)

1. Staging URL açılır; HTTPS ve uygulama kabuğu yüklenir
2. Yeni kullanıcı register olur → authenticated alana geçer; cookie
   geliştirici araçlarında `__Host-M4TRUST_SESSION`, Secure, HttpOnly,
   SameSite=Lax görünür
3. Sayfa yenileme → session korunur; logout → korumalı sayfa kapalı; tekrar
   login
4. Legal entity oluşturma + aktif seçim; deal create/list/detail/update/
   cancel ana akışı (Slice 2–3 kabullerinin staging özeti)
5. Derin link yenilemesi (deal detayında F5) SPA fallback ile çalışır
6. `/api/v1/...` istekleri same-origin'den akar; tarayıcı hiçbir internal
   servis adresi görmez
7. Deploy kanıtı: migration logları pre-deploy adımında; info/health
   yüzeylerinde commit SHA; loglar structured JSON ve correlationId taşıyor
8. Negatif kontroller: core-api'nin public URL'i YOK; secret'lar bundle'da
   ve loglarda yok (spot check); Postgres dışarıdan erişilemiyor

## 8. Minimum invariant testleri

Bu slice'ta yeni business invariant yok; kod düzeyinde yeni test beklenmez
(ADR-004 §6). Doğrulama operasyoneldir:

- CI: image build'leri + mevcut contract/build workflow'ları yeşil
- Pre-deploy migration'ın başarısızlıkta rollout'u durdurduğu bir kez
  bilinçli olarak gözlemlenir/kanıtlanır (örn. geçici sahte migration ile
  staging'de deneme — production'a asla taşınmayan bir doğrulama; implementer
  güvenli yöntemi seçer)
- Rollback provası: önceki image'a manuel dönüş bir kez denenir (ADR-007 §38)

## 9. Açık sorular / karar noktaları

- Staging deploy tetikleyicisi: main merge'de otomatik (öneri) vs manuel
- Custom domain: öneri — şimdilik Railway default domain; kesin production
  domain'i ADR-007 gereği zaten ayrı karar
- Staging seed/test verisi politikası: öneri — UI üzerinden manuel; ihtiyaç
  doğarsa ayrı seed mekanizması
- Web server ürünü (Caddy vs Nginx) ve pre-deploy migration mekanizmasının
  exact biçimi — implementer önerir, PR'da karara bağlanır
- Railway maliyet/limit gözlemi için basit bir not/uyarı düzeni (opsiyonel)

## 10. Done tanımı

- [ ] Dockerfile'lar repo'da; image'lar CI'da commit SHA tag'iyle build
      oluyor
- [ ] Railway staging ortamı ayakta: public web-edge, private core-api,
      Railway PostgreSQL; hiçbir kaynak local/prod ile paylaşılmıyor
- [ ] Pre-deploy Flyway migration hattı çalışıyor; başarısız migration
      rollout'u durduruyor (kanıtlandı)
- [ ] Production cookie/security profili staging'de doğrulandı (forwarded
      headers dahil)
- [ ] §7 kabul akışı gerçek staging URL'inde baştan sona çalıştırıldı
- [ ] Rollback provası yapıldı (önceki image'a dönüş)
- [ ] Secret'lar repo/image/bundle dışında; `.env.example` güncel
- [ ] DEVELOPMENT.md'ye staging bölümü eklendi (URL şeması, deploy akışı,
      sorumluluklar)
