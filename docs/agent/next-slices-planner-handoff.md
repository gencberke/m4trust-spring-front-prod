# Planner Handoff — Slice 10-11 Sonrası Yol Haritası

Bu doküman, fresh-context bir planner ajanına ilk mesaj olarak verilmek üzere
yazılmıştır. Oturum geçmişi olmadan kendi kendine yeterlidir. Hazırlanma tarihi:
20 Temmuz 2026; branch: `codex/slice-9-11`.

---

## 1. Rolün ve çalışma kuralların

M4Trust projesinin **planner'ısın**: slice planlarsın, kullanıcıya implementer
task paketi üretirsin ve kullanıcı istediğinde implementation review yaparsın;
implementer'ı doğrudan başlatmaz veya yönetmezsin. İşe başlamadan şunları
sırasıyla oku:

1. `docs/agent/WORKFLOW.md` — planner, task paketi ve review sözleşmesi.
2. `docs/plan/README.md` — sekiz bölümlü plan/phase formatı, dizin akışı
   (`planning/ → ready/ → done/`) ve ready gate.
3. `architecture-decisions/ADR-INDEX.md` — Katman 0 cheat-sheet, Katman 1
   trigger sözlüğü, Katman 2 görev reçeteleri, Katman 3 eskalasyon kuralları.
4. `architecture-decisions/FORBIDDEN.md` — konsolide yasaklar.
5. `docs/agent/CURRENT.md` — kabul edilmiş proje durumu (aşağıdaki §2 deltasıyla
   birlikte okunmalı).

Bağlayıcı süreç kuralları:

- Taslak planlar `docs/plan/planning/` altına yazılır; **insan onayı olmadan
  hiçbir plan `ready/`'ye taşınmaz.**
- Plan ile ADR çelişirse **ADR kazanır**; çelişkiyi plana not düş ve insana
  bildir — implementasyona gömme.
- Contract-first: public API yüzeyi implementasyondan önce
  `contracts/openapi/core-api-v1.yaml`'a tasarlanır ve zorunlu contract deltası
  `contracts/scripts/validate_contracts.py` exact beklentileri +
  `contracts/README.md` + `contracts/CHANGELOG.md` ile **tek review birimidir**.
- AI JSON Schema/fixture (`contracts/schemas/`), AsyncAPI ve AI-internal OpenAPI
  değişiklikleri varsayılan kapsam DIŞIDIR; gerekiyorsa ayrı insan onayı iste
  (eskalasyon).
- Payment, funding, settlement, ratification ve ACTIVE cancellation
  mutation'ları her zaman eskalasyon konusudur; yalnız insan-onaylı bir ready
  planın açıkça tanımladığı sınırlar içinde planlanabilir.

## 2. Güncel durum

`docs/agent/CURRENT.md` 20 Temmuz 2026 itibarıyla Slice 0–11 kabulünü kapsar.

- **Slice 10 (Ratification) ve Slice 11 (Funding Foundation) kabul edildi.**
  İş `codex/slice-9-11` branch'indedir ve main'e merge edilmeye hazırdır.
  Planları `docs/plan/done/10-ratification.md` ve
  `docs/plan/done/11-funding-and-payment.md` altında arşivlidir.
- Teknik envanter (branch çalışma ağacındaki kabul edilmiş kaynak durumu):
  - `services/core-api` yeni modüller: `ratification` (immutable package +
    RFC 8785/JCS canonical hash + approve/reject + atomik RATIFIED→ACTIVE +
    supersession) ve `payment` (FundingPlan/FundingUnit/PaymentOperation,
    durable dispatch relay — provider çağrısı TX dışında, iki katmanlı
    idempotency, query-first reconciliation).
  - Migration'lar: `V18__ratification_package_foundation.sql`,
    `V19__payment_funding_foundation.sql` (DB-level unique/immutability
    invariant'larıyla).
  - `integration/payment` altında `local-sandbox` profiline kilitli sandbox
    provider adapter'ı + production'da fail-closed bootstrap guard.
  - Frontend: `frontend/src/features/ratification/` ve
    `frontend/src/features/funding/` panelleri; `frontend/src/app/money.ts`
    ortak BigInt minor-unit yardımcıları.
  - Contract: ratification (5 operation) ve funding (5 operation) yüzeyleri,
    `DealDetail.ratification` / `DealDetail.funding` optional projection'ları,
    yeni availableActions üyeleri — hepsi additive, validator kilitli.
- Doğrulama durumu: contract validator (21 şema, 13 fixture), backend
  `mvn verify` (230 test), frontend typecheck/build ve gerçek browser kabulü
  **yeşil**.
- Tarafsız mimari review (ADR-001..010 + contract denetimi) yapıldı: yapısal
  ihlal yok. Kabul kanıtı:
  `docs/agent/slice-10-11-acceptance-2026-07-20.md`.

## 3. ÖNKOŞUL — acceptance debt kapandı

Yeni içerik slice'ı planlama önkoşulları tamamlandı:

1. Slice 10 §7 iki-browser/üç-kullanıcı ve 14 adımlı yarış matrisi geçti.
2. Slice 11 §7 üç-context `local-sandbox`
   SUCCESS/DECLINE/TIMEOUT_THEN_SUCCESS matrisi geçti.
3. Reconcile server ve contract birlikte UNCONFIRMED-only'ye sıkılaştırıldı.
4. `CURRENT.md` güncellendi, planlar `done/`'a taşındı ve V15–V19 kabul edilmiş
   migration history olarak donduruldu. Branch merge edildiğinde bu dosyalar
   kabul durumunu main'e taşır; V15–V19 üzerinde yeni edit yapılmaz.
5. Sonraki planlara gerektiğinde taşınacak bilinçli V1 sınırları:
   - Frontend para gösterimi tüm currency'lerde 2 ondalık varsayar
     (`frontend/src/app/money.ts`); JPY/KWD tipi kurlar V1 dışı.
   - Sandbox adapter state'i in-memory; restart sonrası aynı key yeni senaryo
     tüketir (yalnız local).
   - Funding integration testlerinde closed-shape (`$.*` hasSize) assertion'ı
     yok; ucuz bir güçlendirme adayı.
   - Deal COMPLETE endpoint'i hangi slice'ta açılırsa, payment initiate/
     reconcile'daki kilitsiz Deal-status okuması o slice'ta Deal lock'u altına
     alınmalı.

## 4. Planlama görevin — kalan yol haritası

ADR-004 §24 sırası (bölünmüş numaralandırmayla) ve mevcut adaylar:

| Aday | Durum | Not |
| --- | --- | --- |
| Slice 12 — Fulfillment and Evidence | planlanmadı | Funding'in ardılı; FUNDED üzerine kurulur |
| Slice 13 — Video Analysis | planlanmadı | `contracts/schemas/video-analysis` şemaları mevcut |
| Slice 14 — Dispute and Settlement | planlanmadı | ADR-010 §2.7 fail-closed sınırına tabi |
| Slice 11B — Gerçek provider entegrasyonu | planlanmadı | Girdi: `docs/research/moka-united-pool-payments.md`; hukuki/operasyonel önkoşullar (ADR-010 §2.7) |
| Slice 7 — Railway staging | `ready/07-staging-deployment.md` | Bağımsız hat; implementasyona alınmamış |

Senden istenen üç çıktı:

- **Görev A — Sıralama önerisi:** Yukarıdaki adayların bağımlılık/risk analizi
  ve gerekçeli önerilen sıra. §3'teki kapanmış kabul kapısını başlangıç kabulü
  olarak kaydet. İnsan onayına sunulacak kısa bir karar dokümanı olarak yaz
  (`docs/plan/planning/next-slices-sequencing.md`).
- **Görev B — İlk slice taslağı:** Önerdiğin ilk içerik slice'ı için
  `docs/plan/README.md` formatında (sekiz bölümlü, sıralı implementation
  phase'leriyle) tam taslak plan →
  `docs/plan/planning/NN-<kebab-baslik>.md`. "Öneri" ve "karar" ifadelerini
  ayır; exact kod şeması yazma.
- **Görev C — Eskalasyon listesi:** Taslağın gerektirdiği yeni ADR kararları,
  contract-yüzeyi-dışı ihtiyaçlar ve hukuki/operasyonel kararlar (özellikle
  11B/settlement için) — açık maddeler halinde.

## 5. Bilinen mimari bağlar (tasarım kancaları)

- **Modüller arası erişim deseni:** dar port + adapter. Emsaller:
  `deal` ← `ratification` yönü için `RatificationSourcePorts` /
  `RatificationDealSourceAdapter`; `deal`'in okuma projection'ı için
  `RatificationPackageProjectionPort` ve `FundingProjectionPort` (port + impl
  aynı modülde, `DealService` tüketir — modül döngüsü kurmamanın kanıtlanmış
  yolu). Yeni modüller (fulfillment, dispute) aynı deseni izlemeli;
  `ModuleArchitectureTest` MODULES listesi genişletilmeli.
- **Fulfillment/Evidence:** FUNDED durumu `FundingProjectionPort` üzerinden
  okunur; evidence dokümanları için Slice 6'nın direct-upload deseni
  (intent → PUT → finalize, immutable object version) emsal.
- **Settlement/Release:** ADR-010 §2.7 pool release/refund/payout'u bu
  foundation'da YASAKLAR; bu yüzeyler ancak yeni ADR kararı + insan onayıyla
  planlanabilir. Fail-closed varsayılanı koru.
- **Video Analysis:** ADR-002 messaging düzeni + Slice 8'in outbox/inbox ve
  Mock Worker altyapısı yeniden kullanılır; AI sonucu hiçbir zaman doğrudan
  business state üretmez (ADR-003 §17-18).
- **ACTIVE cancellation (mutual):** ADR-009 §2.5'te tanımlı, hiçbir slice'ta
  açılmamış yüzey; Dispute/Settlement planına girdi olabilir.
- **Idempotency:** HTTP katmanı için mevcut `idempotency` modülü; dış-etki
  üreten her yeni komutta `Idempotency-Key` zorunlu (ADR-006 §24-25).
- **Para/yüzde:** integer minor unit / basis points; float hiçbir katmanda
  yok (ADR-003 §21, ADR-006 §28-29).
