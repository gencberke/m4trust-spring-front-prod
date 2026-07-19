# Architecture Decisions

Bu klasör, M4Trust sisteminin geliştirilmesi sırasında alınan ve kabul edilen mimari kararları içerir.

Bu kararlar; Spring Boot backend, React frontend, FastAPI AI servisi, PostgreSQL, RabbitMQ, object storage, güvenlik, API standartları, geliştirme yöntemi ve deployment yaklaşımı için temel referanstır.

## Amaç

Architecture Decision Record dosyaları şu amaçlarla kullanılır:

* Mimari kararların neden alındığını kayıt altında tutmak
* Geliştirici ve implementer ajanların aynı kurallara göre çalışmasını sağlamak
* Sistem sınırlarının zaman içinde bozulmasını önlemek
* Daha önce verilen kararların tekrar tartışılmasını azaltmak
* Yeni kararların mevcut mimariyle uyumunu değerlendirmek
* Proje sohbetleri ve implementasyon görevleri arasında bağlam sürekliliği sağlamak

## Kararların bağlayıcılığı

Durumu `Accepted` olan ADR’ler, aksi yönde yeni bir ADR kabul edilmediği sürece bağlayıcıdır.

Implementasyon sırasında:

* ADR kararları varsayılan davranış olarak uygulanmalıdır.
* ADR ile çelişen değişiklikler sessizce yapılmamalıdır.
* Gerekli değişiklik yeni bir ADR veya mevcut ADR’nin açık şekilde değiştirilmesiyle yapılmalıdır.
* Legacy projedeki yapı, kabul edilmiş ADR’lerin önüne geçmez.
* Legacy repository yalnızca domain ve davranış referansı olarak kullanılır.

## Dosya listesi

### ADR-001 — System Boundaries and Data Ownership

Spring Boot, FastAPI, frontend, PostgreSQL, RabbitMQ ve object storage arasındaki sistem sınırlarını ve veri sahipliğini tanımlar.

### ADR-002 — Spring–AI Contract and Compatibility Policy

Spring ile FastAPI arasındaki asynchronous mesaj sözleşmelerini, schema versioning kurallarını, validation sorumluluklarını ve compatibility politikasını tanımlar.

### ADR-003 — Core Domain Model and Deal Lifecycle

Ana business aggregate’lerini, Spring modular monolith modüllerini, ayrı lifecycle status boyutlarını, versioned domain modellerini ve transaction kurallarını tanımlar.

### ADR-004 — Vertical Slice Delivery and Acceptance Testing

Backend ve frontend’in vertical slice yaklaşımıyla nasıl geliştirileceğini, minimum kod testi politikasını, frontend kabul testlerini ve Mock AI Worker kullanımını tanımlar.

### ADR-005 — Authentication and Security Baseline

Server-side session, Spring Session, cookie güvenliği, CSRF, CORS, parola politikası, session süreleri ve authorization bağlamını tanımlar.

### ADR-006 — Public API and Error Conventions

Spring public API adlandırma, response, RFC 9457 Problem Details, pagination, filtering, optimistic concurrency, idempotency ve OpenAPI standartlarını tanımlar.

### ADR-007 — Deployment and Runtime Environments

Railway deployment topolojisini, ortamları, container sınırlarını, migration, secret, logging, health, backup ve rollback yaklaşımını tanımlar.

### ADR-008 — Cross-Tenant Deal Participation

Deal katılımının tenant sınırları arasındaki semantiğini tanımlar: participant satırının entity'nin kendi tenant'ını taşıması, görünürlüğün yalnız participant ilişkisine dayanması, audit'in actor tenant'ında yazılması ve Slice 4 expand–contract migration sırası.

### ADR-009 — Deal Commitment and Cancellation Consent

Davet kabulü ile ticari rızayı ayırır; initiator'ın taslak koordinatörü olduğunu, ACTIVE geçişinin buyer ve seller'ın aynı immutable package sürümünü onaylamasına bağlı olduğunu ve ACTIVE Deal'in tek taraflı iptal edilemeyeceğini tanımlar.

### ADR-010 — Ratification Commercial Terms and Funding Foundation

Ratification package V1 commercial terms alanlarını ve provider-bağımsız tek
unit funding foundation'ın state machine, polling, idempotency, crash recovery,
authorization ve gerçek-provider sınırlarını tanımlar.

## ADR durumu

ADR dosyalarında aşağıdaki durumlar kullanılabilir:

* `Proposed`: Tartışma aşamasında
* `Accepted`: Kabul edilmiş ve implementasyon için bağlayıcı
* `Superseded`: Daha yeni bir ADR tarafından değiştirilmiş
* `Deprecated`: Artık yeni geliştirmelerde uygulanmıyor
* `Rejected`: Değerlendirilmiş ancak kabul edilmemiş

Bir ADR başka bir karar tarafından değiştirildiğinde eski dosya silinmez. Eski ADR üzerinde hangi yeni ADR tarafından değiştirildiği belirtilir.

## Dosya adlandırma

ADR dosyaları aşağıdaki formata uygun adlandırılır:

```text
ADR-XXX-Descriptive-Title.md
```

Örnek:

```text
ADR-005-Authentication-and-Security-Baseline.md
```

ADR numaraları sıralıdır ve yeniden kullanılmaz.

## Yeni ADR ne zaman yazılmalı?

Aşağıdaki durumlardan biri oluştuğunda yeni bir ADR değerlendirilmelidir:

* Birden fazla modülü etkileyen mimari karar
* Sistem veya veri sahipliği sınırının değişmesi
* Yeni bir public servis veya public API yüzeyi
* Authentication veya authorization modelinin değişmesi
* Database, broker veya object storage stratejisinin değişmesi
* Deployment platformu veya runtime topolojisinin değişmesi
* Mevcut ADR ile açıkça çelişen yeni gereksinim
* Uzun vadeli bakım ve geliştirme yöntemini etkileyen karar

Küçük implementasyon detayları, sınıf isimleri veya yalnız tek bir task’a özel teknik seçimler için ADR yazılması zorunlu değildir.

## Geliştirme öncesi kullanım

ADR'lere doğrudan dalmadan önce [ADR-INDEX.md](ADR-INDEX.md) kullanılır. Index katmanlıdır: Katman 0 (cheat sheet) sık kararların cevabını ADR açmadan verir; Katman 1 (trigger sözlüğü) anahtar kelimeden ilgili ADR bölümüne yönlendirir; Katman 2 görev reçeteleri, Katman 3 eskalasyon kurallarını içerir. Yasakların konsolide listesi [FORBIDDEN.md](FORBIDDEN.md) dosyasındadır.

Senkronizasyon kuralı: bir ADR'yi değiştiren her PR, `ADR-INDEX.md` ve `FORBIDDEN.md` dosyalarındaki ilgili satırları da günceller. Index ve FORBIDDEN türetilmiş dokümanlardır; çelişkide ADR kazanır.

Yeni bir slice veya implementasyon görevi başlamadan önce geliştirici:

1. ADR-INDEX yönlendirmesiyle ilgili ADR bölümlerini okumalıdır.
2. Görevin ADR kararlarıyla uyumunu kontrol etmelidir.
3. Çelişki varsa kodlamaya başlamadan önce mimari kararı netleştirmelidir.
4. Yeni public contract gerekiyorsa ilgili OpenAPI veya AsyncAPI dosyasını güncellemelidir.
5. Implementasyon tamamlandığında ADR’deki kabul kriterlerine göre sistemi test etmelidir.

## Temel proje ilkeleri

M4Trust geliştirmesinde aşağıdaki ilkeler korunur:

* Frontend yalnız Spring Boot public API’sini çağırır.
* Spring Boot business source of truth’tür.
* FastAPI business veya payment state’ini değiştiremez.
* AI işlemleri asynchronous çalışır.
* Servisler ortak database paylaşmaz.
* Business mutation, audit ve outbox aynı transaction içinde yazılır.
* Frontend lifecycle veya authorization kurallarını kendi başına hesaplamaz.
* Kod düzeyindeki testler minimum tutulur.
* Belirleyici kabul testi gerçek frontend kullanıcı akışıdır.
* AI provider ve modelleri platform testlerinde mocklanabilir.
* Railway ilk staging ve production platformudur.
* Mimari standart protokoller ve taşınabilir container’larla provider bağımsız tutulur.

## İlgili kaynaklar

Bu klasördeki ADR’lerin yanında aşağıdaki contract dosyaları da mimari kararların parçasıdır:

```text
contracts/
contracts/openapi/core-api-v1.yaml
contracts/asyncapi/
contracts/schemas/
```

ADR’ler sistemin neden ve sınırlarını tanımlar.

OpenAPI, AsyncAPI ve JSON Schema dosyaları ise servislerin birbirleriyle hangi formatta iletişim kuracağını tanımlar.
