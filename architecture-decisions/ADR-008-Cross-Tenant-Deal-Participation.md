# ADR-008: Cross-Tenant Deal Participation

- Durum: Accepted
- Tarih: 16 Temmuz 2026 (kabul: 17 Temmuz 2026)
- Karar sahipleri: M4Trust mimari ekibi
- Kapsam: Deal katılımının tenant sınırları arasındaki semantiği; `deal_participant` veri modeli, Deal görünürlük sorguları, `OperationContext` ve audit tenant ataması
- Bağlı kararlar:
  - ADR-003: Core Domain Model and Deal Lifecycle (§5 multi-tenancy, §23 modül erişimi, §28 authorization bağlamı)
  - ADR-005: Authentication and Security Baseline (§20–21 legal entity context ve authorization katmanı)
- Tetikleyen iş: Slice 0–3 implementasyon review'u (2026-07-16) ve Slice 3.9 planı madde B

## 1. Bağlam

ADR-003 §5 iki şeyi birlikte söyler: tenant teknik izolasyon sınırıdır, LegalEntity
sözleşme tarafıdır ve **bir Deal farklı tenant'lara bağlı legal entity'leri
içerebilir**. Slice 2'de alınan bilinçli basitleştirme ("tek-tenant-per-kayıt") ile
her kullanıcı kaydı kendi tenant'ını üretir; dolayısıyla iki farklı şirketin
kullanıcıları pratikte her zaman farklı tenant'tadır.

Slice 3 implementasyonu, kapsamı gereği (tek participant = initiator) doğru çalışsa
da üç mekanizmayla cross-tenant katılımı imkânsız kılar:

1. `deal_participant` composite FK'sı (`legal_entity_id, tenant_id →
   legal_entity(id, tenant_id)`, `tenant_id` = deal'in tenant'ı) participant
   entity'yi deal'in tenant'ına zorlar.
2. Deal görünürlük sorguları `deal.tenant_id = <çağıranın tenant'ı>` predicate'i
   taşır; başka tenant'taki participant deal'i hiçbir zaman göremez.
3. Davet/katılım akışının bağlanacağı bir cross-tenant semantiği tanımlı değildir.

Slice 4 (Deal Invitations & Cross-Entity Participation) bu üçünü çözmeden
başlayamaz. Bu ADR yalnız semantiği kesinleştirir; migration ve kod değişikliği
Slice 4'ün işidir.

## 2. Karar

### 2.1 Tenant modeli korunur

Tenant-per-kayıt modeli bu aşamada değişmez. Cross-tenant işbirliği tenant modelini
yeniden örgütleyerek değil, Deal katılım katmanında çözülür. Tenant'ların
birleştirilmesi/paylaşılması ihtiyacı doğarsa ayrı bir karar olur.

### 2.2 `deal.tenant_id` hosting tenant'tır; erişim filtresi değildir

Deal, initiator legal entity'nin tenant'ı altında oluşturulur (ADR-003 §5 "hosting
veya initiating tenant"). `deal.tenant_id` veri sahipliği ve operasyonel izolasyon
işaretidir. **Deal erişim/görünürlük sorgularında filtre olarak kullanılmaz.**

### 2.3 Participant satırı iki tenant bağlamı taşır

`deal_participant` tablosu şu semantiğe evrilir:

- `tenant_id` → deal'in hosting tenant'ı (mevcut kolon; `deal(id, tenant_id)`
  FK'sı korunur).
- `legal_entity_tenant_id` → participant entity'nin **kendi** tenant'ı (yeni
  kolon). Entity FK'sı buna taşınır:
  `(legal_entity_id, legal_entity_tenant_id) → legal_entity(id, tenant_id)`.

Böylece participant satırı hem deal'in hem entity'nin tenant bütünlüğünü DB
seviyesinde korur ve iki değer farklı olabilir.

### 2.4 Görünürlük ekseni yalnız participant ilişkisidir

Bir Deal, çağıranın doğrulanmış aktif legal entity'si o Deal'in participant'ı ise
görünürdür — başka hiçbir koşulla değil. Erişim sorgusunun bağı:

```text
participant.legal_entity_id        = context.activeLegalEntityId
participant.legal_entity_tenant_id = context.tenantId   (savunma katmanı)
```

`context.activeLegalEntityId` zaten membership ile doğrulanmış olduğundan ikinci
predicate savunma amaçlıdır. `deal.tenant_id = context.tenantId` predicate'i
görünürlük sorgularından kaldırılır. ADR-006 §20 non-disclosure davranışı
(participant olmayan için `DEAL_NOT_FOUND`) aynen korunur.

### 2.5 `OperationContext.tenantId` anlamı değişmez

`OperationContext.tenantId` her zaman çağıranın kendi tenant'ıdır (aktif legal
entity membership'inden türetilir). Cross-tenant Deal okuma/yazmalarında deal'in
hosting tenant'ını taşımaz; hosting tenant gerektiğinde deal kaydından okunur.

### 2.6 Audit, actor'ın tenant'ı altında yazılır

Deal mutation audit kayıtları `tenant_id = context.tenantId` (actor'ın tenant'ı)
ile yazılır. Bu, mevcut `audit_record` FK bütünlüğünü
(`actor_user_id, tenant_id → tenant_user`) korur. Deal-eksenli audit okuması
`subject_type = 'DEAL' AND subject_id = dealId` üzerinden yapılır ve tenant'lar
arası kayıtları doğal olarak birleştirir. İleride hosting-tenant görünümü
gerekirse `deal_tenant_id` benzeri bir kolon **additive** olarak eklenebilir; bu
ADR bunu zorunlu kılmaz.

### 2.7 Davetler hosting tenant'ta yaşar

Slice 4'teki DealInvitation aggregate'i deal'in hosting tenant'ı altında
oluşturulur. Davetin kabulü, davet edilen legal entity'nin (kendi tenant'ıyla
birlikte) `deal_participant` satırına dönüşmesidir. Davet edilen tarafın daveti
görebilmesi participant olmadan mümkün olmalıdır; bu erişim davet aggregate'inin
kendi kurallarıyla (ör. davet edilen entity üyeliği) sağlanır ve Slice 4 planında
ayrıntılandırılır.

## 3. Migration sırası (Slice 4'te uygulanır, expand–contract — ADR-007 §24–25)

1. **Expand:** `legal_entity_tenant_id` nullable eklenir; mevcut satırlar
   `tenant_id` değeriyle backfill edilir; yeni FK eklenir; uygulama çift-yaz
   (iki kolonu da doldurur).
2. **Switch:** görünürlük sorguları §2.4 eksenine geçer; eski
   `(legal_entity_id, tenant_id) → legal_entity` FK'sı kaldırılır; kolon
   `NOT NULL` yapılır.
3. **Contract:** geçiş doğrulandıktan sonra gereksiz kalan kısıt/indeks temizliği
   ayrı release'te yapılır.

Her adım tek başına deploy edilebilir ve geri alınabilir (önceki image ile uyumlu)
olmalıdır.

## 4. Yasaklanan yaklaşımlar

- `deal.tenant_id` (veya çağıranın tenant'ı) eşleşmesini Deal görünürlük/erişim
  koşulu olarak geri getirmek
- Participant satırını entity'nin kendi tenant bağı olmadan yazmak
- Cross-tenant katılımı "davet edilen kullanıcıyı deal'in tenant'ına üye yapmak"
  gibi tenant sınırını delen geçici çözümlerle sağlamak
- `OperationContext.tenantId`'ye çağıranın tenant'ı dışında bir anlam yüklemek

## 5. Sonuçlar

Olumlu:

- ADR-003 §5'in cross-tenant Deal vaadi implementasyonla bağdaşır hale gelir.
- Deal görünürlüğü participant ilişkisi eksenine iner; mutation authority ADR-009'a
  göre ayrıca operation bazlı değerlendirilir.
- Audit FK bütünlüğü ve mevcut Slice 1–3 davranışları korunur.
- Slice 4 migration'ı net, üç adımlı ve geri alınabilir bir yola oturur.

Maliyet:

- `deal_participant` bir kolon ve bir FK daha taşır; sorgular participant-join
  eksenine taşınırken indeksler gözden geçirilir.
- "Hosting tenant" kavramının operasyonel anlamı (raporlama, retention) ileride
  ayrıca netleştirilebilir.

## 6. Kabul

17 Temmuz 2026'da insan onayıyla `Accepted` durumuna alındı. ADR-INDEX.md ve
FORBIDDEN.md aynı değişiklik setinde güncellenmiştir. Slice 4 planı bu karar
üzerine kurulur; §3'teki migration sırası Slice 4'te uygulanır.
