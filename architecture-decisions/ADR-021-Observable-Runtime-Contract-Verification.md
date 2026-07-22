# ADR-021: Observable Runtime Contract Verification

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Main public API için committed OpenAPI ile Spring runtime doğrulamasının sınırı
- Kısmen değiştirir: ADR-006 §43 ve ADR-016 §2.10 içindeki runtime/committed
  OpenAPI karşılaştırmasının uygulanma biçimi
- Korur: Committed OpenAPI otoritesi, generated frontend drift gate'i, production'da
  API docs kapalılığı ve sessiz contract drift yasağı

## 1. Bağlam

Committed OpenAPI design contract; security requirements, reusable responses,
media types ve design-level schema `$ref` katalogları taşır. Annotation kullanılmayan
Spring controller'larından üretilen raw springdoc çıktısı bu alanların tamamını
implementation gerçeği olarak çıkaramaz.

Committed alanları raw çıktıya kopyalayıp sonra eşitlik karşılaştırmak false-green
üretir. Bütün controller yüzeyine yalnız bu testi yeşile çevirmek için geniş OpenAPI
annotation'ları eklemek ise ikinci bir dokümantasyon otoritesi ve gereksiz bakım yükü
oluşturur.

## 2. Karar

1. `contracts/openapi/core-api-v1.yaml` tek reviewed public design contract ve
   frontend generation kaynağıdır.
2. Test profilindeki raw Spring runtime çıktısı yalnız mekanik olarak gözlemlenebilen
   servlet inventory için committed contract ile karşılaştırılır:
   - exact public path ve HTTP method;
   - güvenilir biçimde yayımlanan named path/query/header/cookie parameter için
     name, location ve required bilgisi.
3. Security, status, media type, schema ve Problem Details semantiği raw springdoc
   full-equality iddiasıyla değil birlikte çalışan şu kanıtlarla korunur:
   - committed OpenAPI validator ve deliberate invalid/negative fixtures;
   - kritik security, CSRF/session, status, content type ve Problem Details davranışını
     doğrulayan odaklı MockMvc contract testleri;
   - generated TypeScript clean-diff;
   - ilgili slice'ın gerçek frontend–Spring acceptance akışı.
4. Test hiçbir committed alanı actual runtime dokümanına kopyalayamaz. Disabled bir
   full-equality testi veya runtime dokümanını kendi mutate edilmiş kopyasıyla
   karşılaştıran test acceptance kanıtı sayılmaz.
5. Sırf full-equality üretmek için geniş annotation programı, runtime contract overlay,
   codegen abstraction veya ikinci bir spec kaynağı eklenmez.
6. Production'da springdoc/Swagger route'ları kapalı kalır.

Bu gate tüm endpoint'lerin bütün olası response davranışını otomatik kanıtladığını
iddia etmez. Yeni veya değişen public davranış önce committed OpenAPI'ye girer ve
değişen slice için orantılı davranış/acceptance testiyle kanıtlanır.

## 3. Sonuçlar

- Runtime inventory drift'i sahte projection olmadan yakalanır.
- Committed OpenAPI tek contract otoritesi olarak kalır.
- Security/response semantiği annotation sayısından değil gerçek HTTP davranışından
  test edilir.
- Geniş controller annotation bakımı ve yeni runtime abstraction eklenmez.

## 4. Kabul kapıları

- Raw runtime ile committed contract arasında exact public path/method inventory
  karşılaştırması geçer.
- Named servlet parameter drift'i için gerçek runtime tarafını değiştiren negatif
  fixture gate'i düşürür.
- Committed semantic negative matrix ve odaklı HTTP behavior testleri geçer.
- Expected contract alanlarını actual dokümana kopyalayan projection bulunmaz.
- Disabled full-equality veya redundant runtime-self-mutation testi bulunmaz.
- Production profile public API docs route'u açmaz.
