# ADR-015: Domain Event and Outbox Policy

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Business mutation, audit, domain/integration event, durable dispatch,
  notification outbox and inbox atomicity
- Değiştirdiği kararlar:
  - ADR-003 §§24 ve 26'daki blanket `mutation + audit + outbox` ifadesini bu
    ADR'deki event-presence kuralıyla netleştirir.
  - ADR-013 §2.6'nın Slice 14A için event/outbox üretmeme kararı geçerli kalır.
- Bağlı kararlar: ADR-001, ADR-002, ADR-003, ADR-006, ADR-010, ADR-013, ADR-014

## 1. Bağlam

ADR-003 her business mutation için audit ve outbox'ı aynı transaction içinde
ifade ederken ADR-013 Slice 14A casework mutation'larının event/outbox
üretmeyeceğini açıkça kabul etmiştir. Kod ADR-013'ü izlemektedir. Her mutation
için anlamsız event icat etmek domain contract'ını şişirir; yayımlanması gereken
bir event'i transaction dışında üretmek ise kayıp mesaj riski yaratır.

## 2. Karar

### 2.1 Kayıt türleri ayrılır

- **Business audit:** Actor veya system command'in business sonucunu append-only
  kaydeder. Auditable mutation ile aynı transaction'dadır.
- **Internal domain notification:** Aynı process içindeki optional bildirimdir;
  authoritative state veya cross-service delivery garantisi değildir.
- **Integration event:** Başka service/consumer için versioned contract taşıyan
  durable mesajdır; transactional outbox gerektirir.
- **Durable dispatch:** Provider, simulator, reconciliation, email veya benzeri
  transaction-dışı iş için kalıcı work item'dır; ilgili mutation ile aynı
  transaction'da yazılır.
- **Inbox:** Inbound at-least-once mesaj identity'si ve business application sonucu
  aynı transaction'da tutulur.

### 2.2 Atomicity kuralı

Her auditable business mutation:

```text
business mutation + audit + HTTP idempotency result
```

öğelerini aynı PostgreSQL transaction'da yazar. Yalnız accepted ADR/contract/plan
o operation için integration event veya durable external work tanımlıyorsa aynı
transaction ayrıca:

```text
+ integration outbox event and/or durable dispatch
```

yazar. Event tanımlanmamış mutation için placeholder, generic
`ENTITY_CHANGED` veya tüketicisi olmayan outbox kaydı üretilmez.

Inbound processing:

```text
inbox identity + business mutation + audit
+ required follow-up outbox/dispatch
```

şeklinde atomiktir. Duplicate inbox delivery mutation-free replay olur.

### 2.3 Event tanımlama kapısı

Yeni integration event ancak şu bilgiler accepted ADR/ready plan ve shared
contract'ta sabitse üretilebilir:

- producer ve consumer;
- business/technical amacı;
- event adı, major/schema version ve canonical payload;
- partition/routing identity;
- idempotency identity;
- retry/DLQ ve PII sınıflandırması;
- producer transaction'ı ve consumer side effect'i.

Bu bilgiler yoksa implementer event icat etmez ve mevcut mutation'a outbox
eklemez.

### 2.4 Notification outbox

User invitation, password recovery ve business notification isteği doğuran
mutation, secret token'ın kendisini değil notification template id, recipient
reference, locale, non-secret template parameters ve ADR-017'deki encrypted
one-time delivery material'ı aynı transaction'daki notification outbox'a yazar.
Plaintext token persist edilmez. Provider çağrısı relay tarafından transaction
dışında yapılır. Delivery/bounce sonucu ayrı idempotent inbound işlemle kaydedilir.

### 2.5 Casework ve settlement ilişkisi

Accepted Slice 14A open/comment/acknowledge/withdraw mutation'ları RabbitMQ event
veya integration outbox üretmez. ADR-014 settlement eligibility/result
transaction'ları casework narrow read/lock port'unu kullanır. Future resolution,
notification veya cancellation event'i ancak kendi accepted ADR/contract'ı ile
eklenebilir.

### 2.6 Delivery ve recovery

- Relay kısa claim transaction'ı kullanır; external call sırasında DB transaction
  açık kalmaz.
- Publisher confirm, retry, lease recovery ve DLQ uygulanabilir.
- Duplicate publish beklenir; exactly-once varsayılmaz.
- Business recovery'nin kaynağı PostgreSQL state + outbox/inbox + idempotent
  consumer + reconciliation'dır.
- Outbox/dispatch row'u business outcome'u tek başına temsil etmez.

## 3. Sonuçlar

- ADR-003 ile ADR-013 arasındaki ifade çelişkisi kapanır.
- Audit zorunluluğu event zorunluluğundan ayrılır.
- Contract'sız veya tüketicisiz event üretimi engellenir.
- Email/provider çağrıları transaction dışında kalırken request kaybı önlenir.

## 4. Kabul kapıları

- Mutation testleri audit'in atomik, event/dispatch'in yalnız tanımlı operations
  için var olduğunu kanıtlar.
- Transaction rollback audit/outbox/dispatch'i birlikte geri alır.
- Relay crash/duplicate/retry testleri business duplicate üretmez.
- Architecture testleri foreign repository ve external-call-in-transaction
  ihlallerini reddeder.
