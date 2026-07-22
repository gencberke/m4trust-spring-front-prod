# Slice 10–11 Kabul Kaydı — 20 Temmuz 2026

## Sonuç

Slice 10 Ratification ve Slice 11 Funding Foundation kabul edildi. Kabul,
`codex/slice-9-11` branch'indeki son kaynak durumu üzerinde, izole PostgreSQL
veritabanı ve `local,local-sandbox` profilleriyle tamamlandı.

## Gerçek tarayıcı kabulü

İki gerçek browser yüzeyi ve üç kullanıcı rolü kullanıldı:

- in-app browser: buyer entity ADMIN; ayrıca aynı entity'nin ikinci ADMIN'i ve
  MEMBER kullanıcısı;
- Chrome: seller entity ADMIN; terminal yarış için aynı seller oturumunun ikinci
  tabı;
- bütün business mutation'ları uygulamanın görünür UI kontrolleri üzerinden
  çalıştırıldı. READY önkoşullu tekrar senaryolarında yalnız document ve accepted
  rule-set fixture'ları izole kabul veritabanına hazırlandı.

### Slice 10 §7

1. İki MONEY önerisi görünürken tutar/currency alanlarının boş kaldığı ve
   otomatik seçim yapılmadığı doğrulandı; initiator farklı exact değer girdi.
2. Buyer ve seller aynı structured terms, document/rule-set snapshot'ı ve aynı
   content hash'i gördü.
3. Package iki tarafta PENDING göründü.
4. Buyer onayı tek approval üretti; Deal DRAFT kaldı.
5. Seller'ın ikinci onayı package'ı RATIFIED, Deal'i ACTIVE yaptı.
6. ACTIVE durumda edit, parties, document/review ve cancel mutation kontrolleri
   kapandı; server-side retler integration testleriyle doğrulandı.
7. Seller reject sonrası REJECTED görüldü; yeni package temiz approval setiyle
   başladı.
8. PENDING package sırasında party değişikliği package'ı SUPERSEDED yaptı; stale
   approval 409 davranışı doğrulandı.
9. Withdrawal ile son onay iki browser'da yarıştı: CANCELLED/SUPERSEDED kazandı,
   son onay state conflict aldı.
10. MEMBER approve/reject/create kontrollerini görmedi; doğrudan mutation 403
    invariant testi geçti.
11. İkinci ADMIN aynı buyer entity adına yalnız bir etkili approval üretti.
12. Approve double-click tek approval üretti.
13. Bir approval sonrası farklı exact terms yeni hash üretti; eski package
    SUPERSEDED oldu ve yeni package approval'ları boş başladı.
14. Approve–reject terminal yarışında yalnız REJECTED kaldı ve kaybeden 409 aldı;
    approve–party-supersede yarışında yalnız SUPERSEDED kaldı ve son onay 409
    aldı.

### Slice 11 §7

1. ACTIVE Deal'de buyer ADMIN explicit funding plan oluşturdu; double-click
   sonunda veritabanında tek plan ve tek unit vardı. DRAFT/CANCELLED ve yetkisiz
   aktör kapıları server invariant testleriyle doğrulandı.
2. SUCCESS akışı PENDING → FUNDED oldu; buyer ve seller aynı sonucu gördü ve
   lifecycle FULFILLMENT'a ilerledi.
3. DECLINE akışı DECLINED/FAILED üretti; UI retry ile yeni operation açıldı ve
   SUCCESS sonucu FUNDED yaptı.
4. TIMEOUT_THEN_SUCCESS akışı UNCONFIRMED kaldı, yeni initiate kapandı ve
   reconcile aynı provider key ile FUNDED yaptı.
5. Payment double-click/same-key replay tek operation üretti; farklı payload'ın
   aynı HTTP key'i kullanması 409 invariant testiyle doğrulandı.
6. Seller initiate/reconcile kontrolü görmedi; server mutation reddi geçti.
7. Buyer MEMBER initiate/retry kontrolü görmedi; doğrudan mutation 403 testi
   geçti.
8. FUNDED unit'te yeni payment kontrolü yoktu; server yeni girişimi 409 ile
   reddetti.
9. Ratification ve önceki slice regresyonları tam backend verify ile geçti.

## Mimari ve contract doğrulaması

- Modül sınırları dar port + adapter yönünde kaldı; `ModuleArchitectureTest`
  ratification ve payment ownership/cycle kontrollerini geçti.
- Payment provider çağrısı request/DB transaction dışında; durable dispatch ve
  query-first recovery testleri geçti.
- Reconcile yalnız `UNCONFIRMED` operation kabul ediyor; `CREATED` ve terminal
  operation'lar state conflict üretiyor.
- FundingPlan ratified `ratificationPackageId`, amount ve currency provenance'ını
  immutable snapshot olarak taşıyor; aynı-Deal composite FK ile korunuyor.
- ACTIVE + FUNDED Deal lifecycle projection'ı FULFILLMENT; diğer ACTIVE funding
  durumları FUNDING.
- V18 ve V19 kabul edilmiş migration olarak bu kabulden sonra donmuştur; sonraki
  değişiklikler yeni versioned migration ile yapılmalıdır.

## Otomatik doğrulama

- Contract validator: 21 schema, 13 valid fixture ve bütün expected-invalid
  kontrolleri geçti.
- Core API: `mvn verify` — 230 test, 0 failure, 0 error, 0 skipped.
- Frontend: `npm run typecheck` ve `npm run build` geçti.
- `git diff --check` geçti.

Testcontainers kapanışında bazı Spring scheduled-session thread'leri kapanmış
geçici PostgreSQL portlarına erişmeye çalışarak log uyarıları yazdı; Surefire
sonucu ve build başarılıdır, test failure değildir.

## Bilinçli V1 sınırları

- Frontend para gösterimi bütün currency'lerde iki ondalık varsayar.
- Sandbox provider state'i process-memory içindedir ve yalnız local profile'da
  kullanılır.
- Funding read response'ları için geniş closed-shape assertion ileride ucuz bir
  test hardening adayıdır.
- Deal COMPLETE mutation'ı açılacağı slice'ta payment initiate/reconcile Deal
  status okuması Deal lock'u altına alınmalıdır.
