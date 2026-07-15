# Slice 1 — Authentication

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 1
- Öncül: 00-platform-foundation
- Ardıl: 02-organization-and-membership

## 1. Amaç ve kullanıcı sonucu

Kullanıcı gerçek tarayıcıda: kayıt olur → authenticated ekrana yönlendirilir → sayfayı yenilediğinde oturumu korunur → çıkış yapar → korumalı sayfaya erişemez → tekrar giriş yapar. Yanlış şifreyle giremez, aynı e-postayla ikinci kez kayıt olamaz.

Bu akış frontend → Spring → PostgreSQL üzerinden gerçek çalışmadan slice tamamlanmış sayılmaz (ADR-004 §26). Postman/Swagger ile doğrulama yeterli değildir.

## 2. Kapsam / kapsam dışı

Kapsam:

- Register, login, logout, me endpoint'leri + CSRF token endpoint'i
- Server-side opaque session (Spring Session JDBC, PostgreSQL)
- Argon2id parola hashing; 15–128 karakter politikası; kompozisyon kuralı yok (ADR-005 §13)
- E-posta normalizasyonu + case-insensitive unique constraint (ADR-005 §14)
- Generic login hata mesajı (hesap var/yok sızdırılmaz, ADR-005 §15)
- CSRF koruması (unsafe metotlarda token, ADR-005 §10)
- Session cookie güvenlik ayarları (local varyantıyla)
- Idle 30 dk / absolute 8 saat timeout (ADR-005 §6)
- Frontend: register/login ekranları, auth state, protected route, session restore, logout

Kapsam dışı:

- **Login throttling / brute-force koruması** — bilinçli olarak ertelendi (aşağıda "Ertelenen güvenlik işleri" bölümüne bakınız)
- Email verification, password reset, MFA, SSO, remember-me, aktif session yönetim ekranı, CAPTCHA (ADR-005 §26 deferred listesi)
- Tenant/legal entity kavramları (Slice 2) — register bu slice'ta yalnız kullanıcı oluşturur

## 3. Okunacak ADR bölümleri

- ADR-005 tamamı (bu slice'ın ana kaynağı)
- ADR-006 §13–20 (hata modeli ve status politikası — özellikle 401/409/422 ayrımı), §51 (security response prensipleri)
- ADR-004 §8, §26 (kabul akışı), §22 (checklist)
- ADR-003 §4.1 (identity modülü sorumluluk sınırı — legal entity üyeliği identity'ye AİT DEĞİLDİR)

## 4. Public API yüzeyi

`core-api-v1.yaml`'a bu slice'ta eklenecek endpoint'ler (implementasyondan ÖNCE tasarlanır):

- `POST /api/v1/auth/register` — 201; hata: 422 (validation), 409 `AUTH_EMAIL_ALREADY_EXISTS`
- `POST /api/v1/auth/login` — 200; hata: 401 `AUTH_INVALID_CREDENTIALS` (generic)
- `POST /api/v1/auth/logout` — 204; server-side session invalidation
- `GET /api/v1/auth/me` — 200 (public kullanıcı bilgisi; parola/hash/session ID asla dönmez); 401 session yoksa/geçersizse (`AUTH_SESSION_EXPIRED` ayrımı yapılabilir)
- `GET /api/v1/security/csrf` — `{token, headerName}` (ADR-005 §10.1)

Alan detayları OpenAPI tasarımında netleşir; register en az email + password alır, `me` en az id + email + görünen ad döner. Error response'lar Slice 0'daki `ProblemDetail` component'ini kullanır.

## 5. Backend yönlendirmesi

- Modül: `identity` (ADR-003 §4.1). User aggregate bu modülde; membership/tenant burada MODELLENMEZ.
- Flyway: users tablosu (UUID id, normalized email unique, display email, Argon2id hash, timestamps, security state) + Spring Session JDBC tabloları (Spring'in resmi şemasından migration olarak; otomatik schema init'e güvenilmez, ADR-007 §21).
- Spring Security konfigürasyonu: form-login yerine JSON endpoint'ler; session tabanlı; stateless JWT YOK (ADR-005 §3).
- **Session güvenliği (karar, bağlayıcı):** HttpOnly cookie ile login sonrası session ID regeneration alternatif değil, tamamlayıcıdır — HttpOnly, XSS ile cookie okunmasını; regeneration, session fixation'ı engeller. İkisi de uygulanır. Regeneration Spring Security'nin default davranışıdır (`changeSessionId`); implementer yalnız bu default'un kapatılmadığını doğrular, custom kod yazmaz.
- Cookie: production profili `__Host-M4TRUST_SESSION` + Secure + SameSite=Lax; local profil `M4TRUST_SESSION`, Secure=false (ADR-005 §5.5). Profil ayrımı environment config'ten gelir; production ayarı local kolaylığı için gevşetilmez.
- Absolute timeout yalnız cookie süresine bırakılmaz; server tarafında session oluşturma zamanına göre uygulanır (ADR-005 §6.2). Basit bir kontrol filter'ı veya session attribute yaklaşımı yeterlidir.
- Parola: Argon2id (Spring Security `Argon2PasswordEncoder`); parola hiçbir logda, hata mesajında, telemetride görünmez.
- CSRF: cookie authentication kullanıldığı için açık; token'ı `GET /api/v1/security/csrf` verir; health/readiness muaf tutulabilir, muafiyet listesi dar ve açık tutulur (ADR-005 §10.3).
- Security event logging: başarılı/başarısız login, logout loglanabilir; session ID, parola, CSRF token asla loglanmaz (ADR-005 §17).

## 6. Frontend yönlendirmesi

- Route'lar: `/register`, `/login`, korumalı alan (örn. `/app/*`).
- Auth state: frontend authority DEĞİLDİR (ADR-005 §23). Uygulama açılışında `GET /auth/me` ile durum doğrulanır; TanStack Query ile `me` sorgusu auth state'in tek kaynağı yapılabilir.
- Protected route: client-side redirect + her durumda backend'in 401 vermesine güvenme. 401 alan istek sonsuz retry döngüsüne girmez (ADR-005 §6.3); global bir 401 handler kullanıcıyı login'e yönlendirir.
- CSRF: uygulama başlangıcında veya ilk unsafe istekten önce token alınır; fetch wrapper unsafe metotlara header'ı otomatik ekler. CSRF hatası generic network hatası gibi yutulmaz.
- Ekran durumları: loading, invalid credentials, duplicate email, boş form validation. Hata gösterimi Problem Details `code` alanına göre yapılır, `detail` metnine göre değil (ADR-006 §14).
- Tipler committed OpenAPI'den üretilir; elle paralel model yazılmaz.

## 7. Kabul testi (tarayıcı akışı)

ADR-005 §24'teki akış esas alınır:

1. Yeni kullanıcı kayıt olur → authenticated ekrana ulaşır
2. Sayfa yenilenir → session korunur
3. Çıkış yapar → protected route'a erişemez
4. Tekrar giriş yapar → erişir
5. Yanlış parolayla giremez (generic hata)
6. Duplicate e-posta ile kayıt olamaz
7. (Simüle) session expiry sonrası login ekranına yönlendirilir
8. İkinci browser profili farklı hesapla bağımsız session açar (ADR-005 §9 — sonraki slice'ların çok-kullanıcılı test zemini)

## 8. Minimum invariant testleri

- Parola hash'inin Argon2id ürettiği ve plaintext saklanmadığı
- Duplicate normalized email'in reddedildiği (`ABC@x.com` vs `abc@x.com`)
- Login sonrası session ID'nin değiştiği (fixation regression testi)
- Logout'un server-side session'ı gerçekten invalid ettiği
- `me` response'unda hassas alan bulunmadığı

Controller'ın service çağırdığını doğrulayan yüzeysel testler yazılmaz (ADR-004 §6).

## 9. Açık sorular / karar noktaları

- Register başarısında otomatik login yapılıp yapılmayacağı (ADR-005 §22.1 "oluşturabilir" der — öneri: evet, otomatik session; kabul akışı buna göre yazılmıştır)
- `me` response'unun alan seti (minimum: id, email, displayName — planner OpenAPI tasarımında kesinleştirir)

## 10. Ertelenen güvenlik işleri (deferred — sessizce unutulmayacak)

- **Login throttling (ADR-005 §16):** 5 başarısız/15 dk politikası bu slice'ta implemente edilmez. Public production launch ÖNCESİNDE ayrı bir güvenlik işi olarak ele alınmalıdır. Bu maddeyi taşıyan bir takip kaydı (issue/plan) açılmadan slice done sayılmaz — kod değil, kayıt zorunlu.
- Email verification + password reset: ADR-005 §14 gereği public launch öncesi kesinleştirilecek.

## 11. Done tanımı

- [ ] OpenAPI yüzeyi implementasyondan önce `core-api-v1.yaml`'a eklendi
- [ ] Register/login/logout/me/csrf gerçek Spring + PostgreSQL üzerinde çalışıyor
- [ ] Session cookie ayarları profil bazlı doğru (local/prod ayrımı)
- [ ] Session fixation regeneration doğrulandı; absolute timeout server-side uygulanıyor
- [ ] Frontend gerçek API'ye bağlı; loading/error/empty durumları var
- [ ] §7'deki tarayıcı akışı baştan sona manuel çalıştırıldı
- [ ] §8'deki invariant testleri geçiyor
- [ ] Login throttling için takip kaydı açıldı (§10)
- [ ] Parola/session ID/CSRF token hiçbir logda görünmüyor (spot check)
