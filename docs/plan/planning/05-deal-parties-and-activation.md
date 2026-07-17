# Slice 5 — Deal Parties (Buyer/Seller) ve Activation

- Durum: planning
- Slice sırası: ADR-004 §24 → Slice 4'ün ikinci yarısı (yol haritasında 05)
- Öncül: 04-deal-invitations-and-participation
- Ardıl: 06-document-upload (bağımsız); ileride ratification bu slice'ın
  taraf modeline dayanır

## 1. Amaç ve kullanıcı sonucu

Kullanıcı A gerçek tarayıcıda: deal'inin participant'ları arasından buyer ve
seller atar → aynı entity'yi iki tarafa atamaya çalışınca engellenir → taraflar
tamamken deal'i açık bir aksiyonla ACTIVE durumuna geçirir → kullanıcı B kendi
ekranında entity'sinin rolünü (örn. SELLER) ve deal'in ACTIVE durumunu görür.

Bu slice, ratification/funding'e giden zincirin ilk halkası olan **taraf
modelini** ve DRAFT→ACTIVE geçişinin gerçek tetikleyicisini kurar
(Slice 3'te geçiş domain'de tanımlıydı ama kullanıcıya kapalıydı).

## 2. Kapsam / kapsam dışı

Kapsam:

- `deal` tablosuna buyer/seller referansları (Slice 3 planındaki "nullable
  şimdi ekle" önerisi uygulanmamıştı — migration bu slice'ta gelir)
- Taraf atama ve kaldırma (deal DRAFT'tayken)
- "Aynı legal entity hem buyer hem seller olamaz" invariant'ı (ADR-003 §7.1;
  ADR-004 §7 açık test adayı) — domain + DB seviyesinde
- `POST /deals/{dealId}/activate` business action'ı ve precondition'ları
- `availableActions` projection'ının genişlemesi (atama/activate
  görünürlüğü backend'den)
- Lifecycle projection'ın ACTIVE deal için anlamlı değer üretmesi
- Frontend: taraf atama UI'ı, rol rozetleri, activate aksiyonu

Kapsam dışı:

- Ratification, çift taraflı onay mekanizması (taraflar activate'i
  "onaylamaz" — o ratification'ın işi, Slice 10)
- Doküman/AI önkoşulları (activate bu slice'ta doküman gerektirmez;
  gereklilik zinciri ilerleyen slice'larda sıkılaşabilir — nota bakınız)
- Participant çıkarma/ayrılma akışları
- ACTIVE→COMPLETED geçişinin tetikleyicisi (settlement zinciri, Slice 15)

## 3. Okunacak ADR bölümleri

- ADR-003 §7–7.1 (Deal aggregate alanları ve invariant'lar), §9 (DealStatus
  geçişleri), §16 (lifecycle projection önceliği), §25 (concurrency)
- ADR-008 §2.3–2.4 (taraf entity'leri farklı tenant'ta olabilir — FK ve sorgu
  tasarımını etkiler)
- ADR-006 §4 (action endpoint), §18–19 (422/409 ayrımı), §21–23
  (expectedVersion), §41 (availableActions)
- ADR-004 §7 (bu slice'ın invariant'ları listede açıkça var), §22–23

## 4. Public API yüzeyi

Yüzey implementasyondan ÖNCE `core-api-v1.yaml`'a tasarlanır. Taslak:

- Taraf atama: tek bir "parties" güncelleme endpoint'i (örn.
  `PUT /api/v1/deals/{dealId}/parties` — buyer + seller birlikte, atomik) veya
  rol bazlı action'lar; **öneri: tek atomik parties endpoint'i** — iki rolün
  tutarlılık invariant'ı tek istekte doğrulanır. Kesin biçim OpenAPI fazında.
  `expectedVersion` zorunlu; stale → 409 `DEAL_STALE_VERSION`.
- `POST /api/v1/deals/{dealId}/cancel` mevcut; yanına
  `POST /api/v1/deals/{dealId}/activate` — body'siz action; geçersiz
  durumdan/eksik taraflarla → 409 `DEAL_STATE_CONFLICT`.
- `DealDetail` genişler: buyer/seller alanları (atanmamışken null — ADR-006
  §32 null semantiği), participant başına rol bilgisi, genişleyen
  `availableActions`. Additive değişiklik kuralları (ADR-006 §47) korunur.

Sabitlenen contract kararları:

- buyer = seller gönderimi → 422 `VALIDATION_FAILED` (istek semantik olarak
  geçersiz — ADR-006 §18'deki "buyer ve seller aynı legal entity" örneği
  birebir budur)
- Participant olmayan entity'yi taraf atama → 422 (alan geçersiz); deal
  durumu atamaya izin vermiyorsa → 409
- Activate precondition ihlali (taraflar eksik) → 409 `DEAL_STATE_CONFLICT`
  (mevcut state ile çatışma)

## 5. Backend yönlendirmesi

- **Migration:** `deal` tablosuna nullable `buyer_legal_entity_id` ve
  `seller_legal_entity_id`. FK tasarımı ADR-008 sonrası dünyaya uyar: taraf
  entity'si deal'in tenant'ında olmayabilir — `deal_participant`taki çifte
  tenant modeli izlenir (öneri: taraf, participant satırına referansla
  bağlanır ya da entity FK'sı kendi tenant çiftiyle kurulur; planner
  gerekçelendirir). `buyer_legal_entity_id <> seller_legal_entity_id` CHECK
  constraint'i (ikisi de non-null iken) + domain invariant'ı birlikte.
- **Karar (bağlayıcı):** yalnız mevcut participant entity'ler taraf
  atanabilir. Atama ve activate yetkisi bu slice'ta **initiator**
  entity'sindedir; diğer participant'lar okur. (Çift taraflı iş onayı
  ratification'ın işidir; burada taklit edilmez.) Yetki kontrolü
  `OperationContext` + application katmanında — yeni `RequestedOperation`
  değerleri.
- Atama/aktivasyon `Deal` aggregate davranış metotlarıyla yürür (Slice 3
  deseni); status setter'ı yok. Activate `DealStatus.activate()` geçişini
  kullanır (Slice 3'te tanımlı, tetikleyicisi yoktu).
- **Lifecycle projection (karar gerekli):** mevcut
  `DealLifecycleProjectionCalculator` DRAFT ve ACTIVE'i DRAFT'a indirger.
  Bu slice'ta ACTIVE deal'in projection'ı netleşir — öneri: ADR-003 §16
  öncelik listesine göre analysis henüz istenmemişken ACTIVE deal
  `CONTRACT_ANALYSIS` aşamasında gösterilir (sözleşme analizi beklenen ilk
  business adımdır). Planner ADR-003 §16 ile gerekçelendirir; frontend
  hesaplamaz (ADR-003 §29). Calculator imzası gelecekteki status girdilerine
  açık kalır (Slice 3'teki tasarım notu).
- **Update/cancel yetkisi (not, karar):** çok-participant dünyada mevcut
  "her participant update/cancel edebilir" davranışı bu slice'ta KORUNUR;
  daraltma ihtiyacı ratification planında yeniden değerlendirilir. Bu bilinçli
  bir erteleme olarak plana yazılır — sessiz varsayım değildir.
- Audit: taraf atama ve activate ayrı audit action'larıyla aynı
  transaction'da.

## 6. Frontend yönlendirmesi

- Deal detayında "Taraflar" bölümü: participant listesinden buyer/seller
  seçimi (yalnız initiator'da düzenlenebilir — görünürlük
  `availableActions`'tan), atanmış rollerin rozetleri, activate butonu.
- Activate onay diyaloğu; başarı sonrası ACTIVE durum ve yeni lifecycle
  ekranda görünür.
- buyer=seller denemesi 422 field error'larıyla formda gösterilir; eksik
  taraflarla activate denemesi 409 mesajıyla.
- İki-browser: B, rolünün atandığını ve deal'in ACTIVE olduğunu kendi
  ekranında görür (yenileme/refetch yeterli).
- Tipler committed OpenAPI'den; hata dallanması `code` ile.

## 7. Kabul testi (tarayıcı akışı)

1. A, Slice 4 akışıyla Beta'yı participant yapmış durumda; taraflar bölümünde
   Alpha=BUYER, Beta=SELLER atar → kaydeder → rozetler görünür
2. A, aynı entity'yi iki role atamayı dener → form hatası (422), kayıt olmaz
3. B kendi ekranında Beta'nın SELLER olduğunu görür
4. A activate eder → durum ACTIVE, lifecycle projection yeni aşamayı gösterir
5. Taraflar atanmadan önce ikinci bir deal'de activate denenir → 409, anlamlı
   mesaj
6. B (initiator olmayan) taraf atamayı/activate'i göremez veya denerse
   reddedilir (availableActions + backend yeniden doğrulama)
7. CANCELLED bir deal'de taraf atama/activate kapalıdır; zorlanırsa 409
8. İki tab'da eş zamanlı taraf ataması → stale version akışı çalışır

## 8. Minimum invariant testleri

ADR-004 §7 listesinden bu slice'a düşenler:

- Aynı legal entity hem buyer hem seller olamaz (domain + DB constraint)
- Participant olmayan entity taraf atanamaz
- Activate precondition'ları: taraflar eksikken/terminal durumda reddedilir;
  DRAFT + taraflar tamken geçer
- Atama stale `expectedVersion` → 409; başarılı atama version'ı artırır
- Initiator olmayan participant'ın atama/activate mutation'ı reddedilir
- Lifecycle calculator: ACTIVE deal için kararlaştırılan projection değeri

## 9. Açık sorular / karar noktaları

- Taraf atama endpoint biçimi: tek atomik `parties` güncellemesi (öneri) vs
  rol bazlı iki action — OpenAPI fazında kesinleşir
- Initiator'ın kendisi taraf olmayabilir mi? (öneri: olabilir — initiator
  yalnız aracıysa buyer/seller iki farklı davetli olabilir; invariant yalnız
  buyer≠seller'dır)
- ACTIVE deal'de taraf DEĞİŞTİRME serbest mi? (öneri: hayır — taraf değişimi
  DRAFT'a özgü; ACTIVE'de değişiklik ihtiyacı ratification/versiyonlama
  dünyasının işi. Planner onayıyla kesinleşir)
- Lifecycle projection değeri (§5'teki öneri) planner tarafından ADR-003 §16
  ile teyit edilir

## 10. Done tanımı

- [ ] OpenAPI yüzeyi implementasyondan önce tasarlandı (parties, activate,
      genişleyen DealDetail/availableActions)
- [ ] Migration uygulandı; buyer≠seller DB + domain seviyesinde engelli
- [ ] Taraf atama ve activate gerçek Spring + PostgreSQL üzerinde çalışıyor;
      yetki initiator'da, backend yeniden doğruluyor
- [ ] Lifecycle projection ACTIVE için kararlaştırılan değeri üretiyor;
      frontend hesaplamıyor
- [ ] §8 invariant testleri geçiyor; audit aynı transaction'da
- [ ] §7 iki-browser akışı gerçek tarayıcıda baştan sona çalıştırıldı
- [ ] Slice 3–4 kabul akışları regresyonsuz
- [ ] Contract validator + `mvn verify` + frontend typecheck/build yeşil
