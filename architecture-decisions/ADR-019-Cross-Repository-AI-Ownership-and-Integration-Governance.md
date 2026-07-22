# ADR-019: Cross-Repository AI Ownership and Integration Governance

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Main backend/frontend ekibi ile ayrı AI repository ekibi arasındaki
  karar, değişiklik, contract-review ve production-claim yetkisi
- Supersedes:
  - Aynı numarada hazırlanmış fakat implementasyon öncesinde geri çekilen
    `Production AI Providers, Privacy, and Model Supply Chain` taslağının tamamı.
  - ADR-001 §21 ve ADR-007 §§9, 15, 17, 27, 31, 39-40 içindeki AI service/image/
    replica/deployment tariflerinin main planner veya main implementer için AI
    değişiklik yetkisi oluşturduğu her yorum.
- Preserves:
  - ADR-001, ADR-002, ADR-003 ve ADR-012'nin business authority, asynchronous
    contract, advisory result, versioning ve compatibility sınırları.
  - ADR-002 §26'nın provider/model/prompt/RAG/embedding/OCR/parser/video pipeline/
    retry/concurrency değişikliklerini contract semantiği değişmediği sürece AI
    internal concern sayan kuralı.
- Bağlı kararlar: ADR-001, ADR-002, ADR-003, ADR-006, ADR-007, ADR-012, ADR-016,
  ADR-018

## 1. Bağlam

Main repository Spring backend, React frontend, shared contract authority ve
bunların deployment kaynaklarını içerir. AI implementation ayrı repository'de
ayrı bir ekip sahibi tarafından geliştirilmektedir.

Production reconciliation sırasında main planner'ın AI provider/model revision,
worker topology, image split, dependency lock, retry/reconnect ve AI deployment
seçimleri yapması önerilmiş; hatta belirli bir NER replacement'ı yanlış biçimde
Accepted karar gibi yazılmıştır. Kullanıcı bu yetkiyi vermemiştir. Lisans,
provenance, privacy veya reliability riski bulmak main ekibe AI implementasyonu
seçme/değiştirme yetkisi vermez.

## 2. Karar

### 2.1 Main ekibin kapalı yetki alanı

Main planner/implementer yalnız şunları tasarlayabilir, değiştirebilir ve kabul
edebilir:

- `services/core-api/**` Spring backend;
- `frontend/**` React frontend;
- main repository'deki `contracts/**` dosyalarının repository stewardship'i;
- Core/Web image, CI/CD, Railway/AWS/Postmark/Grafana config ve bunların deployment/
  recovery/observability işleri;
- main-owned PostgreSQL, RabbitMQ, object-storage ve notification entegrasyonu;
- Spring'in AI command publish, result consume, inbox/outbox/idempotency,
  authorization ve business-state uygulama davranışı;
- frontend'in yalnız Core projection'larını kullanan advisory AI deneyimi;
- bir named AI revision'a karşı read-only contract compatibility kontrolü ve exact
  mismatch raporu.

Shared contract dosyalarının main repository'de bulunması main ekibe AI consumer
implementasyonunu değiştirme yetkisi vermez. Breaking veya semantic contract
değişikliği ADR-002 §25 uyarınca iki owner review'ü olmadan implementasyon yetkisi
kazanmaz.

### 2.2 AI owner'ın tek yetki alanı

Aşağıdaki konular AI repository owner/team'in kararıdır; main plan, ADR veya task
packet bunlar için bağlayıcı seçim/değişiklik üretemez:

- provider, model, model revision/weight/file, prompt, masking, NER, RAG, embedding,
  vector store, OCR, parser ve video pipeline seçimi;
- model lisans/provenance değerlendirmesi ve replacement kararı;
- Python/framework/dependency/lockfile ve runtime sürümü;
- API/worker process veya container ayrımı;
- queue consumer implementation, concurrency/prefetch, heartbeat, executor,
  retry/reconnect, lease ve graceful shutdown tasarımı;
- AI image, registry, SBOM/provenance, CI/CD, health/readiness, metrics/logging;
- AI replica/scaling, region, network, Redis/cache, object-storage erişimi ve AI
  service deployment topolojisi;
- OpenAI, Roboflow veya başka provider credential/DPA/retention/config kararı.

Main ekip bu alanlarda yalnız non-authoritative öneri ve audit observation
paylaşabilir. Öneri task değildir; AI owner kabul etmeden `Accepted`, `Closed`,
`Done` veya production evidence sayılamaz.

### 2.3 Korunan cross-system sınırlar

Bu ownership ayrımı shared product contract'ını gevşetmez:

- Public browser API ve business authority Spring'dedir; frontend doğrudan AI
  service'e bağlanmaz.
- AI result advisory'dir; Core authorization/deterministic validation olmadan
  Deal, payment, fulfillment, casework veya release state'i değiştiremez.
- Spring–AI transport accepted AsyncAPI/JSON Schema ile asynchronous ve
  at-least-once/duplicate-safe çalışır.
- Broker mesajı raw binary, credential, provider-native payload veya shared domain
  entity taşımaz.
- Contract major/breaking/semantic değişiklik joint review olmadan uygulanmaz.
- Main Core AI provider/model metadata'sına göre business branch yapmaz.

Main ekip bu sınırları kendi kodunda uygular ve gelen payload'ı reddedebilir. AI
repository'deki düzeltmenin nasıl yapılacağına karar veremez.

### 2.4 Uyumsuzluk ve risk raporlama

Read-only kontrol shared contract uyumsuzluğu bulursa main ekip şu kanıtı üretir:

```text
main contract revision:
AI repository revision:
affected schema/event/fixture:
exact expected vs actual difference:
compatibility and rollout impact:
joint decision required:
AI owner acknowledgement:
```

Mismatch sessizce contract gevşetilerek veya AI repository'ye doğrudan patch
verilerek kapatılmaz. Affected main release/feature gate açık kalır.

Contract dışındaki lisans, privacy, model, worker veya deployment riski ayrı bir
`informational/non-authoritative` observation olarak bildirilir. Main planner
replacement seçmez ve user/AI owner adına acceptance yazmaz.

### 2.5 Production readiness iddiası

ADR-016 yalnız Main Core/Web deployment readiness'ini yönetir. Main ekip:

- AI image build/publish/promote/deploy etmez;
- AI provider/model/worker readiness'i kabul etmez;
- AI internal SLO veya recovery drill sonucu icat etmez;
- Mock AI Worker kanıtını production AI kanıtı saymaz.

AI-enabled ürün akışı pilotta zorunluysa AI owner kendi capability/evidence
reference'ını sağlar. Main ekip yalnız shared contract uyumu ile uçtan uca görünen
Core state davranışını doğrular. Evidence yoksa AI-enabled product-readiness claim
bloklanır; bu durum main ekibe AI tarafını değiştirme yetkisi vermez.

## 3. Sonuçlar

- Main fix planında AI repository implementasyon fazı ve task packet'i bulunmaz.
- Main release manifesti AI artifact/model/provider alanlarını owned artifact gibi
  taşımaz; varsa AI-owner evidence reference dış bağımlılıktır.
- Eski ADR'lerdeki AI deployment örnekleri tarihsel/system-context bilgisidir;
  main planner tarafından yeni AI değişiklik yetkisi olarak kullanılamaz.
- AI work management gibi AI-side contract/worker değişimi gerektiren future paket
  main roadmap'ten çıkarılır; ihtiyaç AI owner ile joint discovery'ye döner.
- Güvenlik ve production riskleri kaybolmaz: doğru owner'a öneri/gate olarak
  görünür kalır, fakat sahte bir main-team acceptance'a dönüşmez.

## 4. Kabul kapıları

- Hiçbir main ready plan/task packet AI repository write scope'u içermez.
- Main ADR'ler provider/model/worker/image/deployment seçimi yapmaz.
- Shared-contract değişikliği AI owner review gereksinimini açıkça taşır.
- Cross-repository check read-only'dir ve yalnız mismatch/evidence raporlar.
- AI owner evidence bulunmadığında main implementer mock/fallback ile production
  claim üretmez.
