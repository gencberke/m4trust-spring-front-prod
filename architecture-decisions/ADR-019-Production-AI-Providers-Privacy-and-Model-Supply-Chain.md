# ADR-019: Production AI Providers, Privacy, and Model Supply Chain

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Production AI provider allowlist, privacy/masking, local model pinning,
  Roboflow provenance, Python/image supply chain, worker recovery and readiness
- Değiştirdiği kararlar:
  - ADR-001 §§16-19 ve ADR-002 §§26, 29-30 için production implementation
    seçimlerini kapatır; Spring/FastAPI ownership değişmez.
  - ADR-012 §2.7 local mock deployment sınırını production real-capability
    topology ile genişletir; video sonucu advisory kalır.
- Bağlı kararlar: ADR-001, ADR-002, ADR-004, ADR-007, ADR-012, ADR-016, ADR-018
- Repository authority:
  - Contracts/main: `m4trust-spring-front-prod`
  - AI producer/worker: `https://github.com/RqyRen/M4Trust-Gayrettepe`
- Provider/model references:
  - https://developers.openai.com/api/docs/models/gpt-5.4-mini
  - https://platform.openai.com/docs/models/default-usage-policies-by-endpoint
  - https://platform.openai.com/docs/api-reference/introduction#request-ids
  - https://docs.roboflow.com/developer/rest-api/authenticate-with-the-rest-api
  - https://huggingface.co/BAAI/bge-m3
  - https://huggingface.co/akdeniz27/bert-base-turkish-cased-ner

## 1. Bağlam

AI repository document extraction, video analysis, local BGE-M3 RAG ve Turkish
NER masking akışlarını içerir; ancak production için floating model downloads,
query-string provider key, fail-open masking/RAG ve synchronous BlockingConnection
worker davranışı kabul edilemez. Main ve AI contract bytes bugün eşleşse de
cross-repo per-PR authority/digest gate'i eksiktir.

## 2. Karar

### 2.1 Provider ve capability allowlist

Production provider scope closed'dır:

- document LLM: OpenAI;
- video detection: private Roboflow workspace/models;
- embeddings/retrieval: local BAAI `bge-m3`;
- Turkish PII NER: local `akdeniz27/bert-base-turkish-cased-ner`.

İlk taslakta adı geçen `savasy/bert-base-turkish-ner-cased` repository metadata'sı
production commercial-use için açık license beyanı taşımadığı için seçilmemiştir.
Implementer license yorumlayamaz. Kabul edilen replacement MIT metadata'sı,
safetensors ağırlığı ve documented Turkish NER evaluation'ı bulunan yukarıdaki
modeldir.

Provider seçimi yalnız AI service internal pipeline/config concern'üdür. Spring
provider/model adına göre business branch yapmaz. Mock/fake provider production
fallback'i yoktur.

Capability config/provenance gate'i kapanmıyorsa ilgili job fail-closed technical
failure üretir. Required launch capability eksikse AI worker unready ve production
promotion blocked olur.

### 2.2 OpenAI boundary

Production model exact snapshot:

```text
gpt-5.4-mini-2026-03-17
```

Request `store=false` kullanır. Key project/environment scoped, secret store'da,
rate/spend limitli ve rotation runbook'ludur. Eligibility varsa Zero Data
Retention/Modified Abuse Monitoring seçilir; yoksa documented provider retention
ve customer disclosure legal/product gate'tir.

Masking risk azaltan pseudonymization'dır; anonimleştirme veya bütün serbest metin
PII'sı için mutlak garanti olarak sunulamaz. Production OpenAI processing ancak
provider DPA/retention/region evidence, customer-facing processing notice ve
privacy owner'ın residual-risk/DPIA acceptance kimliği release manifestinde
bulunursa açılır. Closed config `EXTERNAL_AI_PROCESSING_MODE` production'da
default `DISABLED`, accepted mode yalnız `OPENAI_MASKED` olur; approval reference
eksikse worker bu capability için ready olmaz. Engineer consent/legal basis
uyduramaz.

OpenAI'ye raw unmasked document gönderilemez. Provider request/log/error response
raw prompt, PII, API key veya native response içermez. Canonical output contract
değişmez.

### 2.3 Local privacy and retrieval gates

Üçüncü taraf LLM çağrısından önce:

1. local parser/OCR;
2. local Turkish NER + deterministic masking;
3. local BGE-M3 retrieval;
4. masked text + verified retrieval context ile provider request

sırası uygulanır.

Masker exception, model-load error veya validation failure durumunda raw input
passthrough yasaktır; provider çağrısı yapılmaz ve canonical technical failure:

```text
MODEL_PROVIDER_UNAVAILABLE
dependency=pii-masker
```

üretir. Retrieval/model error durumunda context'siz LLM çağrısı yapılmaz:

```text
RETRIEVAL_SERVICE_UNAVAILABLE
```

Başarılı retrieval'ın meşru biçimde boş sonuç döndürmesi ayrı valid outcome'dur.
Spring bu technical error'ları business rejection saymaz.

Release-blocking masking sentinel suite e-mail, telefon, TCKN, vergi numarası,
IBAN ve açık PERSON spans içeren Türkçe fixtures için provider request snapshot'ında
sıfır cleartext sentinel ister. Ayrı representative validation corpus NER recall/
false-positive sonuçlarını raporlar; privacy owner kabulü olmadan yalnız unit-test
başarısı production privacy gate'ini kapatmaz.

### 2.4 Model pinning ve offline runtime

Production image build aşağıdaki reviewed immutable revision ve runtime file
SHA-256 setini checked-in model lock manifest olarak kullanır. Hash'ler 22 Temmuz
2026'da source repository revision metadata/content'i üzerinden doğrulanmıştır.

`BAAI/bge-m3@142964af7e05de16511657561de8e8750fc153a0` (MIT, dense-only):

| Runtime file | SHA-256 |
|---|---|
| `1_Pooling/config.json` | `e54c164a07274f2eb45bb724f54a79d1efcc90c41573887cd9a29aeee0597352` |
| `config.json` | `26159e7ad065073448460117eb24b7a4572f6f4e78eadff65dc0a11c052449fa` |
| `config_sentence_transformers.json` | `1eef72430e7194a1e59680e635aed81ffa083f05668dbc5bb1c56c04c0999c38` |
| `model.safetensors` | `993b2248881724788dcab8c644a91dfd63584b6e5604ff2037cb5541e1e38e7e` |
| `modules.json` | `84e40c8e006c9b1d6c122e02cba9b02458120b5fb0c87b746c41e0207cf642cf` |
| `sentence_bert_config.json` | `eb9b44b13c0f52a3b3685c3b1cbdea1ba8b04bea123b98f61610048940776eb1` |
| `sentencepiece.bpe.model` | `cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865` |
| `special_tokens_map.json` | `8c785abebea9ae3257b61681b4e6fd8365ceafde980c21970d001e834cf10835` |
| `tokenizer.json` | `21106b6d7dab2952c1d496fb21d5dc9db75c28ed361a05f5020bbba27810dd08` |
| `tokenizer_config.json` | `a62b2b6784f990259fddef5f16388693a8043be4f69179e6a5257eeb3f9abac4` |

`akdeniz27/bert-base-turkish-cased-ner@99995f7d2be4b3a28c74f0d36ee97f8c04ee0571`
(MIT):

| Runtime file | SHA-256 |
|---|---|
| `config.json` | `3fbee7f6d361174e29cf8a5987bc324b9fc2572beba54704ebe3a26bfdb4e59e` |
| `model.safetensors` | `be2bc35fe98f7ff09534c58487ac1b526e040f23b644013a3e67f85f062c3711` |
| `special_tokens_map.json` | `303df45a03609e4ead04bc3dc1536d0ab19b5358db685b6f3da123d05ec200e3` |
| `tokenizer.json` | `9fe651b08086b38c51e2dece708586ecaadc8b7ad876eba6a6cccbcb24a9f95a` |
| `tokenizer_config.json` | `8be0ecb9d1145d71dcd7e339441ab3611cc3ee5c54d5efbfbb3544f8bb51f21c` |
| `vocab.txt` | `ca72d2012b1e46ed42f5df2dc9675f9da0b37a481601785cb4dff91f67fcca65` |

BGE adapter yalnız dense vectors üretir ve `trust_remote_code=false`,
`local_files_only=true`, safetensors load kullanır. Repository'deki
`pytorch_model.bin`, `colbert_linear.pt`, `sparse_linear.pt`, ONNX ve non-runtime
assets image'a alınmaz. Mevcut legal-corpus embeddings aynı pinned dense adapter
ile yeniden üretilip corpus/source/adapter/model digest'leriyle lock edilir;
startup mismatch readiness'i kapatır.

Model assets build time'da hash verify edilip `ai-worker` image'a bake edilir.
Production runtime `HF_HUB_OFFLINE=1`/equivalent ile network download yapamaz.
Yalnız listedeki runtime files allowlist'tir. License/model card ve source
revision SBOM/release manifestine girer. Sonraki revision/file değişikliği ayrı
reviewed model-manifest kararıdır.

### 2.5 Roboflow security ve provenance

Production yalnız organization-controlled private Roboflow workspace ve pinned
model version id kullanır. Community/public identifiers, özellikle mevcut
`logistics-sz9jr/2` ve `detecting-a-damaged-parcel/11`, production guard tarafından
reddedilir; private approved replacements environment allowlist'te bulunmalıdır.

API key URL/query parametresine girmez; `Authorization: Bearer` header kullanılır.
HTTP client/logging query/header redaction uygular. Model ownership/license,
private visibility, retention/training policy, DPA ve representative golden-set
accuracy evidence production gate'tir.

Video frame minimization mevcut contract input/deadline içinde bounded sampling
kullanır. Personal-data içerebilecek frame processing için customer disclosure ve
accepted data-processing policy olmadan capability production'a promote edilmez.

### 2.6 Python ve image supply chain

- Python runtime 3.13 patch release image digest'ine pinlenir;
- Linux dependency resolution hash-locked/frozen lockfile kullanır;
- source build/wheel provenance CI'da görünürdür;
- `ai-api` lightweight image, `ai-worker` model-containing image olarak ayrılır;
- iki image non-root, read-only runtime filesystem ve bounded temp directory
  kullanır;
- image SBOM, vulnerability scan, provenance attestation ve contract/model digest
  labels taşır.

### 2.7 Worker connection, concurrency ve recovery

Worker synchronous `pika.BlockingConnection` callback içinde uzun inference
çalıştırmaz. `aio-pika` robust connection/event loop heartbeat'i canlı tutar;
pipeline bounded executor/`asyncio.to_thread` içinde çalışır.

Başlangıç production policy:

- one worker replica;
- prefetch/concurrency 1;
- publisher confirms;
- exponential backoff + jitter reconnect;
- Redis lease/idempotency/PENDING_PUBLISH recovery;
- at-least-once delivery ve duplicate-safe result.

SIGTERM:

1. readiness false/new consume stop;
2. bounded in-flight drain;
3. sonuç publish edildiyse confirm/state persist;
4. bitmediyse nack/requeue veya expired Redis lease recovery;
5. unknown result business success/failure sayılmaz.

Long inference event loop heartbeat'ini bloke edemez. Reconnect yeni canonical
job identity icat etmez. OpenAI Responses API için documented exactly-once veya
idempotent create garantisi varsayılmaz: transport outcome ambiguous ise veya
Redis operational history geri döndürülemez biçimde kaybolursa aynı job bounded
retry sırasında provider'ı yeniden çağırabilir. Her provider attempt ayrı stable
privacy-safe `X-Client-Request-Id`/attempt identity ve metric taşır; Spring inbox
aynı job'ın duplicate result'ını business mutation'a ikinci kez uygulamaz. Bu
durum observability/runbook'ta açıkça görünür, success uydurulmaz ve provider call
count için exactly-once iddiası yapılmaz.

### 2.8 Readiness, metrics ve deadlines

AI API readiness contract bundle ve operational config'i; worker readiness:

- RabbitMQ connection/consumer;
- Redis read/write/lease;
- contract bundle digest;
- offline BGE/NER models loaded;
- provider config/provenance allowlist

kontrollerini gerektirir. Liveness external provider availability'ye bağlanmaz.

Job hard deadline 30 dakika, queue oldest-age alert 5 dakika, target end-to-end
p95 5 dakika/p99 10 dakikadır. Metrics job type, status, duration, queue age,
retry, duplicate, contract violation ve provider category taşır; raw content/PII
taşımaz.

### 2.9 Contract authority ve deployment

Main repository `contracts/**` shared authority'dir. Deterministic contract bundle
digest ADR-016 algoritmasıyla iki repo/image'da aynıdır. AI `/internal/v1/contracts`
exact digest ve supported schemas döndürür.

Every AI PR pinned main default branch contract'ını checkout/diff/validate eder.
Every main contract change AI producer/consumer suite'ini exact AI base üzerinde
çalıştırır. `.sync-manifest` source commit/digest merge öncesi güncellenir.

AI API/worker private Railway services'dir; public inference route veya frontend
connection yoktur. FastAPI business database/payment provider kullanmaz.

## 3. Sonuçlar

- Masking/RAG availability model quality concern değil production privacy gate'i
  olur.
- Model/provider updates Spring deploy veya business contract change gerektirmez,
  fakat lock/provenance review ve AI artifact release gerektirir.
- Roboflow private model evidence olmadan video capability production-ready
  sayılamaz.

## 4. Kabul kapıları

- Unit/integration tests masker ve retrieval exception'ında zero OpenAI call
  kanıtlar.
- URL/log tests provider key, raw prompt ve PII sızıntısını reddeder.
- Masking sentinel suite cleartext leak üretmez; representative corpus raporu ve
  privacy/DPA/notice approval reference release manifestinde bulunur.
- Offline image smoke model files/hash/load ve network-disabled startup'ı
  doğrular.
- Broker disconnect, heartbeat, long-job, SIGTERM, duplicate, Redis loss ve
  PENDING_PUBLISH recovery tests geçer.
- Ambiguous provider timeout/Redis-loss testi canonical job/business result'in
  duplicate olmadığını, olası repeated provider attempt'in ayrı sayıldığını ve
  exactly-once provider-call iddiası yapılmadığını kanıtlar.
- Cross-repo contract digest/per-PR drift gate'i çalışır.
- Staging real-provider golden flow canonical contracts ile geçer; provider/DPA/
  private model evidence olmadan production promotion yapılmaz.
