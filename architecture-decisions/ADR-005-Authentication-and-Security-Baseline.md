# ADR-005: Authentication and Security Baseline

- Durum: Accepted
- Tarih: 14 Temmuz 2026
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Spring Boot public authentication, browser session yönetimi, parola güvenliği, CSRF, CORS ve authorization bağlamı
- Bağlı kararlar:
- ADR-001: System Boundaries and Data Ownership
- ADR-003: Core Domain Model and Deal Lifecycle
- ADR-004: Vertical Slice Delivery and Acceptance Testing
- İlgili gelecek kararlar:
- ADR-006: Public API and Error Conventions
- ADR-007: Deployment and Runtime Environments

## 1. Bağlam

M4Trust frontend’i kullanıcıya açık işlemler için yalnız Spring Boot Core Platform ile iletişim kuracaktır. FastAPI AI Service:

- Kullanıcı authentication işlemi yapmaz.
- Kullanıcı session’ı oluşturmaz.
- Frontend tarafından doğrudan çağrılmaz.
- Spring session veya authentication verisine erişmez.

İlk ürün yüzeyi browser tabanlı React frontend olacaktır. Başlangıç kapsamında:

- Mobil uygulama,
- Üçüncü taraf public API,
- Harici OAuth client’ları,
- Farklı domainlerde çalışan birden fazla public frontend
bulunmamaktadır. Bu nedenle authentication modeli mevcut browser–Spring sınırına göre tasarlanacaktır.

## 2. Temel authentication kararı

M4Trust browser authentication modeli:

```text
Server-side opaque session
```

olacaktır. Akış:

```text
React frontend
→ HttpOnly session cookie
→ Spring Security
→ PostgreSQL-backed Spring Session
```

Frontend:

- Access token saklamaz.
- Refresh token saklamaz.
- JWT çözümlemez.
- Authentication kararını kendi başına vermez.
- localStorage veya sessionStorage içinde credential tutmaz.

Session’ın authoritative sahibi Spring’dir.

## 3. JWT kullanılmaması

İlk browser uygulamasında JWT tabanlı authentication kullanılmayacaktır. Başlangıçta aşağıdaki yapılara ihtiyaç yoktur:

- Browser tarafından saklanan access token
- Refresh token rotation
- Frontend token yenileme mekanizması
- JWT revocation listesi
- Frontend’in token expiry hesaplaması
- Logout sırasında istemci tarafı token silmenin yeterli kabul edilmesi

Mobil uygulama, dış müşteri API’si veya bağımsız public client ihtiyacı doğarsa bu kullanım ayrı bir ADR ile değerlendirilir. Bu karar gelecekte servisler arası authentication yöntemini belirlemez.

## 4. Session storage

Spring session kayıtları PostgreSQL üzerinde tutulacaktır. Başlangıç tercihi:

```text
Spring Session JDBC
```

Session state yalnız uygulama instance’ının belleğinde tutulmayacaktır. Bunun sonucunda:

- Birden fazla Spring instance aynı session store’u kullanabilir.
- Spring instance restart olduğunda bütün kullanıcıların zorunlu olarak logout olması gerekmez.
- Session server tarafından iptal edilebilir.
- Password reset sonrası kullanıcının bütün session’ları kapatılabilir.
- Kullanıcıya ait aktif session’lar ileride yönetilebilir.

Session tabloları business tablolarından mantıksal olarak ayrılır.

## 5. Session cookie

Production session cookie’si aşağıdaki güvenlik özelliklerine sahip olacaktır:

```text
Name: __Host-M4TRUST_SESSION
HttpOnly: true
Secure: true
SameSite: Lax
Path: /
Domain: belirtilmez
```

### 5.1. HttpOnly

Session ID JavaScript tarafından okunamaz. Frontend session cookie’sini:

- Parse etmez.
- Loglamaz.
- Başka bir storage alanına kopyalamaz.

### 5.2. Secure

Production session cookie’si yalnız HTTPS üzerinden gönderilir.

### 5.3. SameSite=Lax

SameSite=Lax başlangıç tercihi olarak kullanılacaktır. Bu tercih:

- Normal same-site kullanımını korur.
- E-posta davetleri gibi top-level navigation senaryolarını gereksiz yere bozmaz.
- Cross-site form tabanlı isteklerde ek koruma sağlar.

SameSite , CSRF korumasının yerine geçmez.

### 5.4. __Host- prefix’i

Production cookie’sinde __Host- prefix’i kullanılır. Bu nedenle:

- Cookie Secure olmalıdır.
- Path=/ olmalıdır.
- Domain attribute’u kullanılmamalıdır.

### 5.5. Local development

Local geliştirme ortamında HTTPS zorunlu olmayabilir. Bu nedenle local cookie ayarları environment üzerinden değiştirilebilir:

```text
Cookie name: M4TRUST_SESSION
Secure: false
HttpOnly: true
SameSite: Lax
```

Production güvenlik ayarları local geliştirme kolaylığı nedeniyle gevşetilmeyecektir.

## 6. Session süreleri

Başlangıç session süreleri:

```text
Idle timeout: 30 dakika
Absolute timeout: 8 saat
Remember me: kapalı
```

### 6.1. Idle timeout

Kullanıcı 30 dakika boyunca geçerli activity üretmezse session sona erer.

### 6.2. Absolute timeout

Kullanıcı aktif olsa bile session oluşturulduktan en geç sekiz saat sonra yeniden authentication gerekir. Absolute timeout yalnız cookie expiry değerine bırakılmaz; Spring tarafında uygulanır.

### 6.3. Session expiry davranışı

Session sona erdiğinde public API standart bir unauthenticated response döndürür.

Frontend:

- Auth state’i temizler.
- Kullanıcıyı login ekranına yönlendirir.
- Mümkünse session’ın sona erdiğini anlaşılır biçimde gösterir.
- Başarısız isteği sonsuz döngüyle tekrar göndermez.

## 7. Session fixation koruması

Başarılı login sonrasında session ID yenilenir. Session ID ayrıca kritik privilege değişikliklerinde yeniden üretilebilir. Örnek:

- Authentication başarısı
- Kullanıcı privilege değişikliği
- Kritik security context değişikliği

Önceden oluşturulmuş anonim session ID authenticated session olarak kullanılmaya devam etmez.

## 8. Logout davranışı

Logout yalnız frontend state’ini temizlemekten ibaret değildir. POST /api/v1/auth/logout :

- Mevcut server-side session’ı invalid eder.
- Session cookie’sini temizler.
- İlgili CSRF state’ini geçersiz kılar.
- Başarılı logout sonucunu frontend’e döndürür.

Başlangıçta logout yalnız mevcut browser session’ını kapatır.

```text
Logout current session
```

“Tüm cihazlardan çıkış” başlangıç slice’ının zorunlu parçası değildir. Password reset özelliği eklendiğinde başarılı reset bütün aktif session’ları kapatacaktır.

## 9. Çoklu cihaz ve çoklu browser

Bir kullanıcının aynı anda birden fazla session’a sahip olmasına başlangıçta izin verilecektir. Örnek:

Chrome → ABC kullanıcısı Safari → XYZ kullanıcısı veya:

Chrome normal profil → ABC Chrome gizli profil → XYZ Her browser profili kendi session cookie’sini tutar. Bu yapı vertical slice testlerinde özellikle desteklenecektir. Örnek kabul akışı:

ABC browser session’ında Deal oluşturur. ABC, XYZ’ye davet gönderir. XYZ ikinci browser session’ında daveti görür. XYZ daveti kabul eder. ABC ekranında güncel katılımcı durumu görünür. Başlangıçta diğer browser’daki değişiklik:

- Sayfa yenileme,
- Manuel refresh,
- Kontrollü polling
ile görülebilir. SSE veya WebSocket zorunlu başlangıç gereksinimi değildir. Gerçek zamanlı güncelleme ihtiyacı ilgili slice sırasında ayrıca değerlendirilir.

## 10. CSRF koruması

Cookie tabanlı authentication kullanıldığı için CSRF koruması açık olacaktır. CSRF global olarak kapatılmayacaktır. Unsafe HTTP metotları en az:

POST PUT

PATCH DELETE için CSRF token gerektirir.

### 10.1. CSRF token edinme

Frontend aşağıdaki gibi operasyonel bir endpoint üzerinden CSRF token alabilir:

GET /api/v1/security/csrf Response en az:

```json
{
"token": "...",
"headerName": "X-CSRF-TOKEN"
}
```

bilgisini sağlar. Session cookie HttpOnly kalır. CSRF token session ID değildir ve authentication credential olarak kullanılmaz.

### 10.2. Frontend davranışı

Frontend:

- Başlangıçta veya gerektiğinde CSRF token alır.
- Unsafe isteklerde token’ı ilgili header ile gönderir.
- Session yenilendiğinde eski CSRF token’a güvenmez.
- CSRF hatasını generic network hatası gibi gizlemez.

### 10.3. CSRF uygulanmayacak istekler

Liveness ve readiness gibi authentication gerektirmeyen salt okunur operasyonel endpoint’ler CSRF’den bağımsız olabilir. CSRF istisnaları açıkça tanımlanır; geniş wildcard kullanılmaz.

## 11. CORS

Production’da tercih edilen yapı frontend ile Spring’in aynı origin üzerinden sunulmasıdır. Örnek:

```text
https://app.example.com/
https://app.example.com/api/v1/...
```

Kesin production domain’i bu ADR ile belirlenmez. Frontend ve Spring farklı origin’lerde çalışırsa Spring yalnız açıkça yapılandırılmış origin’lere izin verir. Kurallar:

- Allowed origins environment/config üzerinden gelir.
- Credential kullanılan CORS konfigürasyonunda wildcard origin kullanılmaz.
- Yalnız gereken HTTP metotlarına izin verilir.
- Yalnız gereken header’lara izin verilir.
- Session cookie ile yapılan isteklerde frontend credentials mode kullanır.
- Preflight istekleri authentication’a takılmadan doğru işlenir.

Local geliştirme portları ADR içinde sabitlenmez. Local frontend ve Spring adresleri environment/config ile belirlenir.

## 12. Production deployment’a bağlı authentication kuralları

ADR-005 genel deployment mimarisini belirlemez. Ancak production authentication için aşağıdaki koşullar zorunludur:

- HTTPS
- Secure session cookie
- Doğru reverse proxy forwarded-header işleme
- Güvenilir host ve origin yapılandırması
- Environment bazlı CORS allowlist
- Secret’ların repository dışında tutulması
- Production cookie ayarlarının local profile tarafından override edilmemesi

Container, cloud provider, reverse proxy ürünü, orchestration ve rollout yöntemi ADR-007 kapsamında belirlenecektir.

## 13. Parola politikası

Başlangıç parola politikası:

```text
Minimum uzunluk: 15 karakter
Maximum uzunluk: 128 karakter
```

Hash algoritması: Argon2id

### 13.1. Parola içeriği

Aşağıdaki kompozisyon kuralları zorunlu değildir:

- En az bir büyük harf
- En az bir küçük harf
- En az bir rakam
- En az bir özel karakter

Uzun ve hatırlanabilir passphrase kullanımına izin verilir. Unicode ve boşluk desteklenebilir. Parola:

- Sessizce truncate edilmez.
- Loglanmaz.
- Analytics sistemine gönderilmez.
- Frontend hata telemetry’sine eklenmez.
- Plaintext olarak saklanmaz.

### 13.2. Zayıf parola kontrolü

Bilinen yaygın veya kolay tahmin edilen parolalar reddedilir. Frontend parola gücü hakkında yardımcı geri bildirim gösterebilir. Nihai parola kabul kararının sahibi Spring’dir.

### 13.3. Periyodik parola değişimi

Kullanıcıdan belirli aralıklarla zorunlu parola değiştirmesi istenmeyecektir. Parola değişimi:

- Kullanıcının isteği,
- Şüpheli hesap aktivitesi,
- Credential compromise,
- Operasyonel güvenlik kararı
gibi gerekçelerle uygulanabilir.

## 14. E-posta işleme

E-posta adresi authentication identifier olarak kullanılabilir. E-posta:

- Trim edilir.
- Karşılaştırma için normalize edilir.
- Case-insensitive unique constraint ile korunur.
- Kullanıcıya gösterilecek orijinal biçim ayrıca saklanabilir.

Başarısız login response’u bir hesabın mevcut olup olmadığını açıklamaz. E-posta verification, password reset ve production public registration politikası bu ADR’nin ilk implementation kapsamı dışında tutulmuştur. İlk authentication slice’ında email + parola ile register/login akışı geliştirilebilir. Public production launch öncesinde email verification ve account recovery akışları ayrıca kesinleştirilmelidir.

## 15. Login hata mesajları

Login başarısızlığında kullanıcıya genel mesaj döndürülür:

E-posta veya parola geçersiz. Response aşağıdaki ayrımları dışarıya açıklamaz:

- Hesap mevcut değil
- Parola yanlış
- Hesap disabled
- Hesap geçici olarak throttled
- E-posta henüz doğrulanmadı

Security ve operasyon ekipleri gerekli ayrıntıyı güvenli internal loglardan görebilir. Frontend kullanıcı enumeration yapacak farklı metin veya status üretmez.

## 16. Login throttling

Başlangıç login throttling politikası:

## 5. başarısız deneme / 15 dakika

→ 15 dakika geçici engelleme Değerlendirme en az şu sinyalleri kullanabilir:

- Normalize edilmiş e-posta
- IP adresi
- Kısa zaman aralığındaki deneme sayısı

Kalıcı hesap kilitleme varsayılan yöntem değildir.

Başarılı login, ilgili başarısız deneme state’ini temizler veya azaltır. Throttling mekanizması:

- Kullanıcının hesabı var mı bilgisini sızdırmaz.
- Plaintext parola saklamaz.
- Uygulamayı kolay DoS hedefi hâline getirecek sınırsız state üretmez.
- Birden fazla Spring instance ile tutarlı çalışır.

İleride risk bazlı veya kademeli throttling ayrı security geliştirmesi olarak eklenebilir.

## 17. Authentication event logging

Aşağıdaki olaylar security event olarak loglanabilir:

- Başarılı login
- Başarısız login
- Throttling
- Logout
- Session expiry
- Password change
- Password reset
- Hesap disable/enable
- Çoklu başarısız authentication paterni

Loglarda bulunmaması gerekenler:

- Parola
- Parola hash’i
- Raw session ID
- CSRF token
- Authentication cookie
- Secret
- Gereksiz kişisel veri

Security logları business audit ile aynı kavram değildir. Business state mutation’ları ADR-003 kapsamındaki append-only audit kurallarına tabidir.

## 18. MFA

Multi-factor authentication ilk authentication slice’ının zorunlu parçası değildir. Ancak mimari MFA eklenmesini engellemeyecek şekilde kurulacaktır. İleride MFA veya step-up authentication özellikle şu işlemler için değerlendirilecektir:

- Payment release
- Organization admin değişikliği
- Kritik membership değişikliği
- Ratification ile ilgili yüksek riskli işlem
- Tüm cihazlardan logout
- Hassas hesap güvenliği işlemi

MFA eklenmesi ayrı security kararı veya ADR güncellemesi gerektirir. Başlangıç parola uzunluğu, MFA geldiğinde otomatik olarak düşürülmez.

## 19. Remember-me

Başlangıçta kalıcı remember-me cookie kullanılmayacaktır. Session browser kapanışından sonra cookie ayarına bağlı olarak devam etse bile absolute timeout geçerli olmaya devam eder. Uzun ömürlü persistent login token mekanizması ayrıca tasarlanmadan eklenemez.

## 20. Tenant ve legal entity context

Authentication yalnız kullanıcının kim olduğunu belirler. Authentication aşağıdaki business yetkileri otomatik sağlamaz:

- Belirli tenant adına işlem yapma
- Belirli legal entity adına işlem yapma
- Deal görme
- Deal değiştirme
- Davet gönderme
- Ratification yapma
- Payment işlemi başlatma

Organization-scoped request’lerde frontend aktif legal entity bağlamını açıkça gönderebilir:

X-M4Trust-Legal-Entity-Id: <uuid> Bu header:

- Authentication credential değildir.
- Yetkinin kanıtı değildir.
- Spring tarafından doğrudan güvenilir kabul edilmez.
- Kullanıcının membership kayıtlarıyla doğrulanır.

Aktif legal entity tercihi authoritative server session state olarak tutulmayacaktır.

Bunun nedeni farklı browser tab veya pencerelerinin birbirinin organization context’ini beklenmedik biçimde değiştirmesini engellemektir.

## 21. Authorization katmanı

Authorization yalnız controller annotation’larına bırakılmayacaktır. Her mutating veya hassas read operation en az aşağıdaki bağlamla değerlendirilir:

authenticatedUserId tenantId requestedLegalEntityId dealId requestedOperation Application katmanı:

- Kullanıcının membership’ini,
- Legal entity rolünü,
- Deal participation ilişkisini,
- İstenen business action’ı,
- Deal lifecycle durumunu
doğrular. Controller authentication yapabilir; business authorization application/domain katmanında kesinleştirilir. Frontend’in butonu gizlemesi authorization değildir. Spring public API gerekirse kullanıcıya izin verilen action’ları projection olarak sunar.

## 22. Authentication endpoint başlangıç yüzeyi

İlk authentication slice için başlangıç API yüzeyi:

POST /api/v1/auth/register POST /api/v1/auth/login POST /api/v1/auth/logout GET /api/v1/auth/me GET /api/v1/security/csrf Kesin request, response ve error formatları ADR-006 ve core-api-v1.yaml içinde tanımlanacaktır.

### 22.1. Register

Register:

- Kullanıcıyı oluşturur.
- Parolayı Argon2id ile hashler.
- Duplicate normalized email’i engeller.
- Başarılı olduğunda yeni authenticated session oluşturabilir.
- Session fixation korumasını uygular.

### 22.2. Login

Login:

- Generic hata mesajı kullanır.
- Throttling uygular.
- Başarılı olduğunda session ID’yi yeniler.
- Current user bilgisinin auth/me üzerinden alınmasına izin verir.

### 22.3. Me

auth/me yalnız gerekli public kullanıcı bilgisini döndürür. Parola, hash, session ID ve internal security state döndürülmez.

### 22.4. Logout

Logout mevcut session’ı server tarafında invalid eder.

## 23. Frontend auth state

Frontend auth state’in sahibi değildir; Spring session state’inin görünümünü tutar. Frontend başlangıçta:

GET /api/v1/auth/me ile session durumunu doğrular. Frontend yalnız local state var diye kullanıcıyı authenticated kabul etmez. Sayfa yenileme sonrasında:

- Session geçerliyse kullanıcı state’i restore edilir.
- Session geçersizse anonymous state’e geçilir.

Protected route kontrolü yalnız client-side redirect değildir.

Spring bütün protected endpoint’lerde authentication ve authorization uygulamaya devam eder.

## 24. Frontend kabul testleri

Authentication slice’ın belirleyici testi gerçek browser akışıdır. En az şu senaryolar test edilir:

1. Yeni kullanıcı kayıt olur.

2. Authenticated ekrana ulaşır.

3. Sayfayı yeniler ve session korunur.

4. Çıkış yapar.

5. Protected route’a erişemez.

6. Tekrar giriş yapar.

7. Yanlış parolayla giriş yapamaz.

8. Duplicate e-posta ile kayıt olamaz.

9. Session expiry sonrası login ekranına yönlendirilir.

10. İkinci browser profili farklı hesapla bağımsız session açabilir.

Multi-user business slice’larında iki browser/session yaklaşımı kullanılabilir:

Browser A → ABC Browser B → XYZ Bu test ortamı projenin kabul edilen geliştirme yöntemidir.

## 25. Local port politikası

ADR-005 local port numaralarını sabitlemez. Frontend, Spring ve diğer local servis adresleri environment/config üzerinden belirlenir. Dokümantasyon örnek port gösterebilir fakat bu değerler mimari karar değildir. Authentication ve CORS konfigürasyonu hard-coded port değerlerine bağımlı olmayacaktır.

## 26. Deferred konular

Aşağıdaki konular başlangıç authentication slice’ının dışında tutulmuştur:

- Mandatory email verification
- Password reset akışının tam tasarımı
- MFA
- SSO
- OAuth2/OIDC login
- Enterprise identity provider
- Tüm cihazlardan logout ekranı
- Aktif session yönetimi ekranı
- Risk bazlı authentication
- Device fingerprinting
- CAPTCHA
- Mobil uygulama authentication
- Üçüncü taraf public API authentication

Bu konular ihtiyaç oluştuğunda ayrı kararlarla ele alınacaktır.

## 27. Yasaklanan yaklaşımlar

Bu ADR ile aşağıdaki yaklaşımlar yasaklanmıştır:

- Browser access token’ını localStorage içinde saklamak
- Browser refresh token’ını JavaScript erişimli storage’da saklamak
- Authentication için ilk çözüm olarak JWT kullanmak
- Cookie authentication kullanırken CSRF’yi global kapatmak
- Production’da insecure session cookie kullanmak
- Credential kullanılan CORS’ta wildcard origin kullanmak
- Session ID veya CSRF token loglamak
- Parolayı truncate etmek
- Plaintext parola saklamak
- Frontend local state’ini authentication authority kabul etmek
- Legal entity header’ını authorization kanıtı kabul etmek
- Authorization’ı yalnız frontend buton görünürlüğüne bırakmak
- Kullanıcıya hesabın mevcut olup olmadığını login hatasıyla açıklamak
- Local portları authentication koduna hard-code etmek
- Logout sırasında yalnız frontend state’ini temizlemek

## 28. Sonuçlar

Olumlu sonuçlar:

- Browser tarafında token saklama riski azaltılır.
- Logout ve session iptali server kontrolünde olur.
- Birden fazla Spring instance session paylaşabilir.
- CSRF ve cookie güvenliği açık biçimde yönetilir.
- Frontend authentication implementasyonu sadeleşir.
- Çoklu browser ile iki farklı kullanıcı kolayca test edilebilir.
- Tenant ve legal entity authorization’ı authentication’dan ayrılır.
- Local port ve deployment sağlayıcısı mimariye sabitlenmez.
- Gelecekte MFA ve session yönetimi eklenebilir.

Maliyetler:

- Spring Session tabloları ve temizliği gerekir.
- CSRF token yönetimi frontend’de uygulanmalıdır.
- Absolute session timeout için ek server-side kontrol gerekebilir.
- Farklı origin geliştirme ortamlarında CORS konfigürasyonu gerekir.
- Mobil veya external API geldiğinde yeni authentication kararı gerekebilir.

Bu maliyetler browser güvenliği, logout kontrolü ve sade frontend entegrasyonu için kabul edilmiştir.

## 29. Nihai karar

M4Trust:

- Browser authentication için server-side opaque session kullanacaktır.
- Session state’i PostgreSQL-backed Spring Session ile yönetecektir.
- Production’da `HttpOnly`, `Secure`, `SameSite=Lax` ve `__Host-` session cookie kullanacaktır.
- Local cookie ve origin ayarlarını environment üzerinden yönetecektir.
- Local portları mimari olarak sabitlemeyecektir.
- Cookie authentication nedeniyle CSRF korumasını açık tutacaktır.
- Production’da mümkün olduğunda frontend ve Spring’i aynı origin’den sunacaktır.
- Farklı origin kullanımında explicit CORS allowlist kullanacaktır.
- Argon2id ve 15–128 karakter parola politikasını uygulayacaktır.
- Remember-me mekanizmasını başlangıçta kullanmayacaktır.
- 30 dakika idle ve sekiz saat absolute session timeout uygulayacaktır.
- Aynı kullanıcının birden fazla browser veya cihaz session’ına izin verecektir.
- Çok kullanıcılı akışları farklı browser profilleriyle test etmeyi destekleyecektir.
- Legal entity seçimini authoritative session state olarak tutmayacaktır.
- Legal entity context’ini request ile alıp server-side membership üzerinden doğrulayacaktır.
- Business authorization kararını application katmanında verecektir.
- MFA, email verification, password recovery ve SSO’yu sonraki güvenlik geliştirmelerine bırakacaktır.
