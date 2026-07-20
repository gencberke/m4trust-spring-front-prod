# FORBIDDEN — Konsolide Yasak Listesi

Kabul edilmiş ADR'lere dağılmış yasakların tek noktadan görünümü.
**Türetilmiş bir dokümandır: bu liste ile bir ADR çelişirse ADR kazanır.**
ADR'yi değiştiren her PR bu dosyayı da günceller.

Bir görev bu listedeki bir maddeye takılıyorsa workaround üretme: ihtiyaç gerçekse bu bir ADR değişikliği talebidir ([ADR-INDEX.md](ADR-INDEX.md) Katman 3).

Birden fazla ADR'de tekrarlanan yasaklar tek satırda, çoklu kaynakla verilmiştir.

## 1. Sistem sınırları ve AI

| Yasak | Kaynak |
| --- | --- |
| Frontend'in FastAPI'ye doğrudan bağlanması (adresini bilmesi, job oluşturması, sonucu okuması) | ADR-001 §3, §20 |
| Spring ile FastAPI'nin business database paylaşması; birbirlerinin tablolarına erişmesi | ADR-001 §5, §20; ADR-002 §28 |
| FastAPI'nin transaction/business state değiştirmesi, ratification oluşturması, dispute kapatması, kullanıcı yetkisi değerlendirmesi | ADR-001 §2.2, §20; ADR-002 §28; ADR-003 §31 |
| FastAPI'nin payment provider çağırması veya payment release kararı vermesi | ADR-001 §20; ADR-002 §28; ADR-003 §14 |
| Spring'in LLM provider SDK'sı kullanması, model-native response parse etmesi, model/prompt/token/temperature detayına bağımlı olması | ADR-001 §10, §20; ADR-002 §27 |
| Spring'in business davranışını modelFamily/modelVersion/promptVersion'a göre değiştirmesi | ADR-001 §11; ADR-002 §27 |
| Senkron AI inference endpoint'i (`POST /extract`, `/analyze`, `/chat`, `/generate` vb.) | ADR-002 §21.5; ADR-001 §7 |
| Uzun AI işlemini kullanıcı HTTP request'i içinde bekletmek | ADR-001 §20; ADR-006 §39 |
| AI teknik hatasını otomatik business rejection olarak yorumlamak | ADR-001 §14, §20; ADR-002 §12.3 |
| AI sonucunun doğrudan Deal state'ine uygulanması / deterministik validasyonun atlanması | ADR-001 §20; ADR-003 §17, §31 |
| Manual acceptance olmadan AI sonucundan RuleSetVersion veya Deal current rule-set pointer üretmek | ADR-003 §17–§19; ADR-010 §1 |
| Yeni current document'a geçerken eski RuleSetVersion'ı current/ratification-ready bırakmak | ADR-003 §18–§20 |
| Video/AI sonucunun otomatik fulfillment completion, dispute veya payment release üretmesi | ADR-002 §10.1; ADR-003 §22 |
| Ratify edilmemiş AI `deliveryRequirements` çıktısını contractual checklist, milestone veya completion kuralı saymak | ADR-011 §1, §2.1 |
| Broker mesajında provider-specific payload, raw doküman/video, secret, credential, session token taşımak | ADR-001 §20; ADR-002 §29 |
| Contract'ı tek taraflı değiştirmek (FastAPI model gerekçesiyle, Spring yeni required alan dayatarak) | ADR-001 §9; ADR-002 §28 |
| Contract değişikliğini uygulama kodundan sonra yapmak | ADR-002 §25 |
| Aynı major version içinde breaking change (required alan ekleme/çıkarma, tip/semantik değişimi, event adı değişimi) | ADR-001 §12; ADR-002 §15.5 |
| Exactly-once delivery varsaymak | ADR-001 §13; ADR-002 §17 |
| Raw stack trace, provider hata mesajı veya PII'yi error/warning payload'ına koymak | ADR-002 §12.3, §13 |
| Servisler arası source code seviyesinde domain entity paylaşmak; generated transport modelini domain entity yapmak | ADR-001 §20; ADR-002 §23 |
| FastAPI'ye object storage'da genel/sınırsız bucket erişimi vermek | ADR-001 §6 |

## 2. Domain ve veri (Spring)

| Yasak | Kaynak |
| --- | --- |
| Ana aggregate'a `Transaction` adını vermek | ADR-003 §2, §31 |
| Bütün yaşam döngüsünü tek dev status alanında tutmak | ADR-003 §8, §31 |
| Generic soft delete (`deleted=true`) — açık domain durumları yerine | ADR-003 §7.2, §31 |
| Para/yüzde değerlerinde floating-point kullanmak | ADR-003 §21, §31; ADR-006 §28–29 |
| Modüllerin birbirlerinin repository'lerini veya JPA entity'lerini doğrudan kullanması | ADR-003 §23, §31 |
| Audit veya outbox kaydını business mutation'dan ayrı transaction'da yazmak | ADR-003 §24, §31 |
| External çağrı (broker, storage, provider, email) boyunca DB transaction açık tutmak | ADR-003 §24, §31 |
| Sessiz last-write-wins (optimistic lock conflict'i yutmak) | ADR-003 §25 |
| Full event sourcing ile başlamak | ADR-003 §26, §31 |
| Ratified package'ı mutation ile güncellemek (yeni version yerine) | ADR-003 §20, §31; ADR-009 §2.4 |
| Ratification `contentHash` girdisine mutable status/approval/action, actor-specific görünürlük veya audit metadata'sı dahil etmek | ADR-003 §20; ADR-010 §2.1 |
| Kabul edilmiş extraction/rule-set geçmişini silmek veya değiştirmek | ADR-003 §19 |
| Provider timeout'u otomatik payment failure kabul etmek | ADR-003 §21, §31 |
| UNCONFIRMED payment varken yeni provider key ile otomatik charge retry etmek | ADR-010 §2.3–§2.4 |
| Async payment initiate/reconcile HTTP request'i içinde provider sonucunu beklemek | ADR-006 §35, §54; ADR-010 §2.4–§2.5 |
| Browser redirect sonucunu provider doğrulaması olmadan FUNDED kabul etmek | ADR-010 §2.5 |
| Belirsiz pool/refund davranışını çözmek için otomatik approve-then-refund yapmak | ADR-010 §2.7 |
| Kart verisi, provider credential veya raw provider payload'ını domain/audit/public API'ye taşımak | ADR-007 §19–§20, §33; ADR-010 §2.6 |
| Payment sandbox adapter'ını production profile'da açmak veya production API'ye scenario test-control alanı/yüzeyi eklemek | ADR-004 §19; ADR-010 §2.2, §2.6 |
| Participant/initiator görünürlüğünü fulfillment mutation yetkisi saymak; seller dışı actor'ın submit veya buyer ADMIN dışı actor'ın accept/reject yapması | ADR-009 §2.2; ADR-011 §2.2 |
| Accepted/rejected evidence object veya history kaydını overwrite etmek, silmek ya da replacement olarak mutation ile kullanmak | ADR-003 §22; ADR-011 §2.3–§2.4 |
| Fulfillment acceptance'tan otomatik Deal COMPLETED, settlement, release, payout, refund, provider çağrısı veya AI job üretmek | ADR-011 §2.5 |
| Query edilen temel domain alanlarını (money, status, party, authorization) JSONB'ye gömmek | ADR-003 §27 |
| Yalnız `tenantId` eşleşmesiyle Deal erişimi vermek (participant ilişkisi olmadan) | ADR-003 §5; ADR-008 §2.4 |
| `deal.tenant_id` (veya çağıranın tenant'ı) eşleşmesini Deal görünürlük/erişim koşulu olarak kullanmak | ADR-008 §2.2, §4 |
| Participant satırını entity'nin kendi tenant bağı olmadan yazmak; cross-tenant katılımı kullanıcıyı deal tenant'ına üye yaparak sağlamak | ADR-008 §2.3, §4 |
| Initiator legal entity'yi hosting tenant, creator user veya participant sırasından çıkarmak | ADR-009 §2.2, §4 |
| Participant görünürlüğünü update/cancel/invite/party/activation yetkisi saymak | ADR-009 §2.2, §4 |
| Davet kabulünü buyer/seller rolü veya contractual consent saymak | ADR-009 §2.1, §4 |
| Buyer ve seller aynı immutable ratification package sürümünü onaylamadan Deal'i ACTIVE yapmak | ADR-009 §2.3, §4 |
| Aynı legal entity'deki birden fazla ADMIN onayını birden fazla taraf onayı saymak | ADR-009 §2.3, §4 |
| Buyer ve seller karşılıklı onayı veya casework/dispute kararı olmadan ACTIVE Deal'i cancel etmek | ADR-009 §2.5, §4 |
| `MEMBER` rolüne şirketi bağlayan ratification veya mutual cancellation onayı vermek | ADR-009 §2.6, §4 |

## 3. Public API

| Yasak | Kaynak |
| --- | --- |
| Global success/data/message envelope | ADR-006 §6, §53 |
| JPA entity veya Spring `PageImpl` nesnesini public API'den döndürmek | ADR-006 §9, §40, §53 |
| Frontend'in `detail`/`title` metnine göre business logic yazması (code yerine) | ADR-006 §14, §53 |
| Frontend'in lifecycle stage veya available action hesaplaması | ADR-003 §29, §31; ADR-006 §41, §53 |
| Collection alanını null döndürmek | ADR-006 §32, §53 |
| Timestamp'i local timezone/offset ile döndürmek | ADR-006 §26, §53 |
| Sınırsız generic filter dili (`filter=`, `where=`, `sql=`) açmak | ADR-006 §12, §53 |
| Database column adlarını public sort/filter contract'ı yapmak | ADR-006 §11, §53 |
| Duplicate riskli endpoint'te yalnız frontend button-disable'a güvenmek | ADR-006 §25, §53 |
| Generated client yerine paralel/tahmini TypeScript modelleri yazmak | ADR-004 §27; ADR-006 §44, §53 |
| Runtime OpenAPI ile committed contract'ın sessizce ayrışmasına izin vermek | ADR-006 §43, §53 |
| Internal exception, SQL, stack trace bilgisini public hata response'una koymak | ADR-006 §15, §53 |
| Business aggregate'te generic `DELETE` veya `/update-status` tarzı endpoint | ADR-006 §4–5 |
| GET isteğinin business mutation üretmesi | ADR-006 §5 |

## 4. Güvenlik

| Yasak | Kaynak |
| --- | --- |
| Access/refresh token'ı localStorage veya JS-erişimli storage'da saklamak | ADR-005 §2, §27 |
| Browser authentication için ilk çözüm olarak JWT | ADR-005 §3, §27 |
| Cookie authentication kullanırken CSRF'yi global kapatmak | ADR-005 §10, §27 |
| Production'da insecure session cookie; local kolaylık için prod ayarını gevşetmek | ADR-005 §5.5, §27 |
| Credential'lı CORS'ta wildcard origin | ADR-005 §11, §27 |
| Parolayı truncate etmek, plaintext saklamak, loglamak | ADR-005 §13, §27 |
| Session ID veya CSRF token loglamak/döndürmek | ADR-005 §17, §27; ADR-006 §51 |
| Frontend local state'ini authentication authority kabul etmek | ADR-005 §23, §27 |
| `X-M4Trust-Legal-Entity-Id` header'ını yetki kanıtı kabul etmek | ADR-005 §20, §27 |
| Authorization'ı yalnız frontend buton görünürlüğüne veya controller annotation'ına bırakmak | ADR-005 §21, §27 |
| Login hatasıyla hesabın var olup olmadığını açıklamak | ADR-005 §15, §27 |
| Logout'ta yalnız frontend state temizlemek (server-side invalidation olmadan) | ADR-005 §8, §27 |
| Password hash'i public API'den döndürmek | ADR-006 §51 |

## 5. Test ve geliştirme yöntemi

| Yasak | Kaynak |
| --- | --- |
| Bütün backend'i bitirip frontend entegrasyonunu sona bırakmak | ADR-004 §1, §27 |
| Frontend mock ile çalışırken slice'ı tamamlanmış saymak; production'a gizli mock fallback bırakmak | ADR-004 §18, §27 |
| Test coverage yüzdesini başarı metriği yapmak; her sınıf/metoda test zorunluluğu | ADR-004 §6, §27 |
| Spring'in kendi içinde fake AI sonucu üretmesini ana E2E test kabul etmek (messaging sınırını atlamak) | ADR-004 §14, §27 |
| Gerçek FastAPI'yi günlük geliştirme için zorunlu kılmak | ADR-004 §11, §27 |
| Yalnız Swagger/Postman testiyle capability'yi done saymak | ADR-004 §23, §27 |
| Mock scenario alanını production event contract'ına eklemek | ADR-004 §13; ADR-007 §10 |
| Development ortamında gerçek para hareketi / production operasyonu | ADR-004 §19, §27 |
| Seed verilerini production migration zincirine gömmek | ADR-004 §21; ADR-007 §21 |

## 6. Deployment ve operasyon

| Yasak | Kaynak |
| --- | --- |
| FastAPI'yi public internete açmak; PostgreSQL/RabbitMQ'yu public erişilebilir yapmak | ADR-007 §4–5, §46 |
| Mock AI Worker'ı production'da çalıştırmak veya production queue'larına bağlamak | ADR-007 §10, §46 |
| Production'da `latest` image tag'ine güvenmek | ADR-007 §15, §46 |
| Secret'ı repository'ye, Docker image'a veya frontend bundle'a koymak | ADR-007 §19–20, §46 |
| Production'da `flyway clean`; uygulanmış migration dosyasını değiştirmek | ADR-007 §23, §46 |
| Breaking DB değişikliğini tek rollout'ta yapmak (expand–contract yerine) | ADR-007 §25, §46 |
| Container filesystem'ini business storage olarak kullanmak; session'ı yalnız memory'de tutmak | ADR-007 §8, §46 |
| Production deploy'u staging kontrolü ve manual approval olmadan otomatikleştirmek | ADR-007 §26, §46 |
| Railway'e özel business logic yazmak | ADR-007 §43, §46 |
| Object storage'ı public bucket olarak açmak | ADR-007 §14, §46 |
| Her Spring replica'nın kontrolsüz Flyway migration çalıştırması | ADR-007 §23, §46 |
| Local portları koda hard-code etmek | ADR-005 §25; ADR-007 §41 |

## 7. Asla loglanmayacaklar

| Veri | Kaynak |
| --- | --- |
| Parola ve parola hash'i | ADR-005 §13, §17; ADR-007 §33 |
| Raw session ID, CSRF token, cookie içeriği | ADR-005 §17; ADR-007 §33 |
| DB / RabbitMQ / object storage / AI provider credential'ları | ADR-007 §33 |
| Raw doküman ve video içeriği; raw prompt | ADR-001 §17; ADR-002 §29; ADR-007 §33 |
| Tam payment credential | ADR-007 §33 |
| Gereksiz kişisel veri (PII) | ADR-001 §16; ADR-007 §33 |
