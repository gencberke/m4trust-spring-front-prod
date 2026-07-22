# ADR-017: Invite-Only Identity and Transactional Notification

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Production registration policy, account/member invitations, email
  verification, password recovery, login throttling, notification outbox and
  operator bootstrap
- Değiştirdiği kararlar:
  - ADR-005 §§14, 16, 22 ve 26'daki production verification/recovery kararlarını
    kapatır.
  - ADR-005 §22.1 public register davranışı yalnız local/test için korunur;
    production invite-only olur.
- Bağlı kararlar: ADR-003, ADR-005, ADR-006, ADR-009, ADR-015, ADR-016

## 1. Bağlam

Mevcut authentication slice açık register/login sağlar fakat production email
verification, recovery ve accepted login throttling implementasyonu yoktur.
Deal invitation yalnız önceden hesabı olan normalized e-mail recipient'ı için
uygundur. Production onboarding'in business invitation consent semantiğini
bozmadan hesap aktivasyonu ve üyelik yönetimi sağlaması gerekir.

## 2. Karar

### 2.1 Production registration mode

Closed config `REGISTRATION_MODE`:

- local/test default: `OPEN_LOCAL`;
- staging/production required: `INVITE_ONLY`.

Production `/api/v1/auth/register` aynı contract path'ini compatibility için
korur fakat mutation yapmadan stable `AUTH_REGISTRATION_CLOSED` döndürür.
Frontend production build public register link/route sunmaz.

### 2.2 Invitation aggregate ayrımı

Üç kavram birbirinden ayrılır:

- **AccountInvitation:** yeni/unverified kullanıcının e-mail ownership'ini
  kanıtlayıp account/session oluşturmasına yarar.
- **LegalEntityMemberInvitation:** bir legal entity'ye `ADMIN` veya `MEMBER`
  membership teklifi; explicit authenticated accept/reject gerektirir.
- **DealInvitation:** ADR-009 semantiğini aynen korur; kabul yalnız Deal
  participation'dır, membership/party/contract consent değildir.

Account invitation purpose closed enum'dır:

- `PLATFORM_ONBOARDING`;
- `LEGAL_ENTITY_MEMBERSHIP`;
- `DEAL_PARTICIPATION`.

Account activation underlying membership veya Deal invitation'ı otomatik kabul
etmez. Existing verified user token ile account takeover yapamaz; login olur ve
pending business invitation'ı ayrı authenticated action ile kabul eder.

### 2.3 Token ve lifecycle

Invitation/reset token:

- `SecureRandom` ile 32 byte;
- base64url without padding;
- DB'de yalnız SHA-256 digest;
- path/query yerine SPA URL fragment'ında taşınır;
- API'ye JSON request body ile gönderilir;
- log, audit, analytics veya notification status'a yazılmaz.

Account/member invitation lifecycle:

```text
PENDING -> ACCEPTED | REJECTED | REVOKED | EXPIRED
```

- account/member invite TTL: 72 saat;
- password-reset TTL: 30 dakika;
- terminal token tekrar kullanılamaz;
- resend yeni token/digest üretir ve önceki PENDING token'ı revoke eder;
- expiry server UTC time ile değerlendirilir.

Invitation inspect yalnız masked recipient, purpose, expiry ve existing-account
flag'i döndürür. Unknown/expired/revoked/used token aynı non-disclosing response'u
kullanır.

### 2.4 Public API

Contract-first yüzey:

```text
POST /api/v1/auth/invitations/inspect
POST /api/v1/auth/invitations/accept
POST /api/v1/auth/password-reset/request
POST /api/v1/auth/password-reset/confirm

GET  /api/v1/legal-entities/{legalEntityId}/member-invitations
POST /api/v1/legal-entities/{legalEntityId}/member-invitations
GET  /api/v1/member-invitations/incoming
POST /api/v1/member-invitations/{invitationId}/accept
POST /api/v1/member-invitations/{invitationId}/reject
POST /api/v1/member-invitations/{invitationId}/revoke
POST /api/v1/member-invitations/{invitationId}/resend
```

Member invite create/list/revoke/resend yalnız target entity `ADMIN`; accept/reject
yalnız normalized recipient user'dır ve active legal-entity header kullanmaz.
Create/revoke/resend/accept/reject `Idempotency-Key` ve mutable target action'ları
`expectedVersion` ister. Hidden entity/invite non-disclosing 404'tür. Authenticated
member actions session CSRF ister. Pre-auth invitation inspect/accept ve password
reset endpoints session/cookie authority kullanmaz; exact same-origin
`Origin`/`Sec-Fetch-Site` policy, closed CORS ve dedicated abuse throttle uygular.

Password-reset request account varlığından bağımsız generic `202 Accepted`
döndürür. Reset confirm generic invalid-token response kullanır; success bütün
principal sessions'larını server-side invalidate eder ve yeni session açmaz.

### 2.5 Account activation ve verification

Yeni account invitation accept:

- token ve normalized recipient'i lock altında doğrular;
- 15-128 character password'u ADR-005'e göre Argon2id hashler;
- account'u `emailVerifiedAt` ile oluşturur veya uygun unverified account'u
  atomik activate eder;
- token'ı terminal yapar, audit/idempotency/notification state'i yazar;
- session fixation korumasıyla authenticated session oluşturur.

Existing verified account'ın password/hash/verification state'i invite token ile
değişmez. E-mail address değişikliği bu ADR kapsamında değildir.

### 2.6 Membership invitation

Entity ADMIN explicit target role (`ADMIN|MEMBER`) ve normalized recipient e-mail
ile invite oluşturur. Aynı entity/e-mail için en fazla bir PENDING invite DB
constraint ile korunur. Accept anında recipient identity ve target role tekrar
doğrulanır, membership ve invite terminal state aynı transaction'da yazılır.
Existing equivalent membership idempotent equivalent result; conflicting active
membership/role 409'dur. Role değişimi invitation side effect'i olarak yapılmaz.
Resend yalnız PENDING member invitation'da aynı ADMIN tarafından yapılır; linked
PENDING AccountInvitation'ı revoke edip yeni token/digest/outbox material üretir,
member invitation identity'sini korur ve version artırır. Revoke linked account
token'ını da terminal yapar. Farklı business invitation'ın token/lifecycle'ı
etkilenmez.

### 2.7 Deal invitation notification bridge

Deal invitation create transaction'ı ADR-009 aggregate'ını değiştirmez. Aynı
transaction'da notification outbox yazar. Recipient account yok/unverified ise
linked-purpose AccountInvitation oluşturulur; verified ise login/incoming-invite
notification gönderilir. Account activation Deal invitation'ı accept etmez.

### 2.8 Login throttling

PostgreSQL-backed bounded throttling bütün Core replica'ları için authoritative'dir:

- iki bağımsız subject: normalized e-mail HMAC-SHA-256 ve trusted client-IP
  HMAC-SHA-256; raw e-mail/IP throttle key olarak persist edilmez;
- herhangi bir subject için rolling 15 dakikada 5 failed attempt, eşik sonrası
  15 dakika block;
- successful login e-mail subject state'ini temizler; shared-IP subject kendi
  window'unda decay eder ve bir saldırganın kendi successful login'i ile
  sıfırlanamaz;
- PostgreSQL row/advisory locking aynı attempt'in replica yarışında kaybolmasını
  engeller;
- block/window bittikten 24 saat sonra expired rows background cleanup ile silinir.

Client IP yalnız ADR-016 trusted proxy chain'inden alınır. Unknown account, wrong
password, disabled/unverified account ve blocked account aynı HTTP status,
`AUTH_INVALID_CREDENTIALS` code ve generic detail'i döndürür. Plain e-mail,
password veya raw IP security telemetry'de tutulmaz; iki signal da rotation-aware
keyed digest kullanır.

Pre-auth abuse sınırı ayrıca uygulanır:

- password-reset request: e-mail subject başına 3/saat ve IP subject başına
  20/saat; limitte de aynı generic `202`, notification outbox yok;
- invalid invitation inspect/accept/reset-confirm: IP subject başına rolling
  15 dakikada 30 request, sonra 15 dakika generic block;
- valid token success bu IP abuse history'sini temizlemez.

### 2.9 Transactional notification

Provider port implementation'ı Postmark transactional stream'dir. Mutation
transaction'ı ADR-015 notification outbox yazar; Postmark HTTP call transaction
dışında relay tarafından yapılır.

Production gate:

- verified sender/domain;
- DKIM/SPF/DMARC;
- environment-specific server token/stream;
- security-link open/click tracking disabled;
- template version ve locale pinned;
- secrets/log/token redaction.

Delivery/bounce webhook Caddy allowlisted path üzerinden gelir, Basic Auth +
provider source allowlist ile korunur ve `(MessageID, RecordType)` identity'siyle
idempotent işlenir. Webhook user/business state mutation'ı yapmaz; notification
delivery state ve operational metric günceller.

Notification delivery closed state'i:

```text
PENDING -> IN_FLIGHT -> SENT
                    -> RETRY_WAIT -> IN_FLIGHT
                    -> DEAD_LETTER | CANCELLED | EXPIRED
```

Relay lease claim transaction'ından sonra linked invitation/token generation'ın
hâlâ PENDING/current olduğunu kısa DB read ile doğrular; stale/revoked generation
send edilmeden `CANCELLED` olur ve ciphertext temizlenir. Provider call DB
transaction dışındadır. Password-reset retry schedule `30s, 2m, 5m` ve en geç
15 dakikada dead-letter; invitation schedule `1m, 5m, 15m, 1h, 4h, 12h`, en çok
6 attempt'tir. Token expiry bütün schedule'ı keser. Postmark exactly-once/idempotent
send garantisi vermediğinden send-sonrası/crash-before-commit aynı güvenli token'ı
duplicate e-mail olarak iletebilir; business invitation/reset consumption yine
tek kullanımlı ve idempotent kalır. Outbox id delivery metadata/tag olarak gider,
recipient'e internal identity gösterilmez.

### 2.10 Initial operator bootstrap

Public super-admin endpoint yoktur. One-shot application CLI:

- yalnız explicit `operator-bootstrap` mode'da;
- interactive/environment secret input'i loglamadan;
- existing operator yoksa;
- verified user identity ve minimal `PLATFORM_OPERATOR` security grant oluşturur;
- append-only security/audit evidence yazar;
- success sonrasında tekrar çalışmayı reddeder.

Bu grant casework resolution veya payment override authority vermez; bu
capability'ler future accepted ADR gerektirir.

## 3. Transaction ve data sınırları

Invitation/account/membership mutation, audit, HTTP idempotency ve notification
outbox aynı transaction'dadır. E-mail send dışarıdadır. Token plaintext yalnız
oluşturma request memory'sinde encryption tamamlanana kadar bulunur. Persisted
notification outbox token material için yalnız versioned application-secret
AES-256-GCM ciphertext, random 96-bit nonce ve key version tutar. AAD en az
`outboxId`, notification type, recipient keyed digest ve `expiresAt` içerir.
Production active/decrypt-only key ring yalnız secret store'dan yüklenir;
missing/weak/unknown key version startup'ı fail eder. Relay decrypt edip link'i
memory'de materialize eder; successful send veya token expiry sonrasında
ciphertext/nonce null edilerek delivery metadata korunur. Rotation runbook önce
yeni active key'i deploy eder, pending ciphertext'i re-encrypt eder, sonra eski
key'i kaldırır. Public/audit/log projection plaintext/ciphertext taşımaz.

Forward-only migration V22 sonrasındaki next available version'dır. Existing user
rows verification state için açık migration policy kullanır: local/test users
korunur; production launch account'ları invite ile verified olur; migration
existing account'ı sessizce verified yapmaz.

## 4. Sonuçlar ve kabul kapıları

- Production self-registration kapanır; local developer flow korunur.
- Account activation, membership consent ve Deal participation birbirine
  karışmaz.
- Enumeration, replay, token theft ve multi-replica throttling riskleri
  fail-closed ele alınır.
- Contract/error/generated-client, migration, concurrency, session revoke,
  Postmark retry/bounce ve two-browser role matrix testleri geçmeden kabul yoktur.
- Planner browser acceptance invite -> account -> membership -> Deal invitation
  akışını ve register'ın production'da kapalı olduğunu kanıtlar.
