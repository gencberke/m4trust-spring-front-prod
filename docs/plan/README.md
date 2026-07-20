# M4Trust Slice Plan Workflow

Bu dizin ADR-004 vertical slice planlarını yönetir. Plan, exact kod reçetesi
değil; implementasyon phase'lerini davranış ve mimari düzeyde karar-tam hale
getiren bağlayıcı yönergedir.

## Dizin akışı

```text
planning/  → taslak; sorular ve kararlar tartışılıyor
ready/     → insan onaylı; phase'ler implementasyona alınabilir
done/      → bütün Done koşulları kabul edildi; plan arşivlendi
```

- Plan insan onayı olmadan `ready/` dizinine taşınmaz.
- Ready planda kapsam değişikliği gerekiyorsa plan `planning/` dizinine döner,
  değişiklik açıkça yazılır ve yeniden insan onayı alır.
- Yalnız bir task'ın kabulü planı tamamlamaz. Bütün phase, browser kabulü,
  invariant, validation ve Done maddeleri kanıtlandığında plan `done/` altına
  taşınır; tamamlanma tarihi ve material sapmalar kaydedilir.
- Dosya adı `NN-kebab-case-baslik.md` biçimindedir ve numara ADR-004 slice
  sırasını izler.
- Mevcut tarihsel done planları ve önceden insan-onaylı ready planlar yalnız yeni
  format nedeniyle yeniden yazılmaz.
- Phase ID taşımayan insan-onaylı eski bir ready plan için task paketi,
  `Phases` alanında planın mevcut implementasyon bölümlerini açıkça sayabilir;
  bu eşleme kapsam ekleyemez.

## Sekiz bölümlü ready plan

Her yeni veya yeniden planlanan ready dokümanı şu bölümleri taşır:

1. **Amaç ve kullanıcı sonucu** — gerçek tarayıcıda elde edilen observable sonuç
2. **Kapsam ve sınırlar** — açık in/out kapsamı ve sonraki slice'a bırakılanlar
3. **Kararlar ve ilgili ADR'ler** — bağlayıcı davranış/mimari kararlar ve yalnız
   gerekli ADR bölümleri
4. **Public interface, state ve data etkisi** — API/contract, state transition,
   persistence/migration ve compatibility etkisi
5. **Implementation phases** — bağımlılık sırasındaki runnable phase'ler
6. **Gerçek browser kabulü** — kullanıcı/rol/context bazlı uçtan uca akış
7. **Minimum invariant ve validation** — riskle orantılı otomatik ve operatif
   kontroller
8. **Done tanımı** — planı ready'den done'a taşıyan gözlemlenebilir checklist

Planning taslağı açık sorular taşıyabilir. Ready plan taşıyamaz; bütün
yüksek-etkili ürün, kapsam ve mimari kararlar insan onayından önce kapanır.

## Implementation phase formatı

Phase ID'leri plan içinde stabil ve sıralıdır:

```text
### P<n> — <observable phase name>

Outcome:
<phase sonunda çalışan sonuç>

Direction:
- <owning module ve önemli sınırlar>
- <port, transaction, lock veya compatibility yönü>
- <gerektiğinde contract, persistence ve frontend koordinasyonu>

Depends on:
<P0 veya None>

Exit checks:
- <observable check>
```

Phase'ler mümkün olduğunca runnable vertical parçalar olmalıdır. Contract-first
veya migration bağımlılığı varsa sıra açıkça gösterilir. Planner exact sınıf,
metot veya dosya listesi vermek yerine implementerin yanlış bir mimari yön
seçmesini engelleyecek sınırları tarif eder.

## Ready gate

Uygulanabildiği her yerde plan aşağıdakileri karara bağlamadan ready olamaz:

- actor, authorization ve non-disclosure davranışı;
- state transition, invariant, optimistic concurrency ve idempotency;
- owning module, dar port yönü ve repository ownership;
- transaction, lock order ve external-call sınırı;
- public/shared contract ve additive/breaking compatibility yaklaşımı;
- persistence, migration rollout ve immutable-history etkisi;
- backend-derived lifecycle/action projection ve frontend fail-closed davranışı;
- phase bağımlılıkları, browser acceptance ve minimum validation.

Ready plan şunları içermez:

- exact sınıf veya metot gövdeleri;
- tam SQL DDL veya uygulanmaya hazır migration metni;
- bütün DTO/OpenAPI şemasının kopyası;
- dosya-dosya implementasyon reçetesi;
- implementerin çözmesi beklenen açık ürün veya mimari sorular.

İsim veya wire alanı bir public contract, accepted ADR ya da cross-module sınır
için bağlayıcıysa exact yazılabilir. Local implementasyon mekaniği implementere
kalır.

## Contract ve ADR kuralları

- Mikro kararlar için önce `architecture-decisions/ADR-INDEX.md` Katman 0/1,
  yasaklar için `architecture-decisions/FORBIDDEN.md` kullanılır.
- Plan ile ADR çelişirse ADR kazanır; çelişki implementasyona gömülmez.
- Public API yüzeyi implementasyondan önce
  `contracts/openapi/core-api-v1.yaml` içinde tasarlanır.
- OpenAPI değişikliği; validator exact beklentileri,
  `contracts/README.md` ve `contracts/CHANGELOG.md` ile tek review birimidir.
- İlgili plan aksini açıkça ve insan-onaylı biçimde söylemedikçe AI JSON
  Schema/fixture, AsyncAPI ve AI-internal OpenAPI bu public API deltasına dahil
  değildir.

## Planner ve implementer kullanımı

- Planner süreci, task paketi ve review kuralları:
  `docs/agent/WORKFLOW.md`.
- Implementer phase yürütme ve teslim kuralları:
  `docs/agent/implementer-agent.md`.
- Kullanıcı task paketini implementere, implementer raporunu planner'a taşır.
- Tek aktif implementasyon review kaydı `docs/agent/req-review.md` içindedir.

## Sabit teknoloji kararları

- Backend: Spring Boot modular monolith, PostgreSQL + Flyway, Spring Session JDBC
- Frontend: Vite + React + TypeScript, TanStack Query, committed OpenAPI'den tip
  üretimi
- Local orkestrasyon: Docker Compose ile PostgreSQL, RabbitMQ ve MinIO
- Local frontend `/api` isteklerini Spring'e proxy'ler; production same-origin
  davranışı bu şekilde simüle edilir
