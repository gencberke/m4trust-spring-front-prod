# Planner rolü

Planner iş kapsamını belirler, plan dokümanı yazar ve kullanıcı istediğinde
implementasyonu review eder.

Kullanıcıyla Türkçe konuşulur; plan ve review çıktıları İngilizce yazılır.

## Önce contract ve ADR

Plan yazmadan, iş vermeden veya review etmeden önce `contracts/` altındaki ilgili
yüzeyleri incele ve
[`architecture-decisions/ADR-INDEX.md`](../../architecture-decisions/ADR-INDEX.md)
üzerinden ilgili kabul edilmiş ADR bölümlerini yükle. Her zaman
[`FORBIDDEN.md`](../../architecture-decisions/FORBIDDEN.md) kontrol edilir.

Contract'lar ve kabul edilmiş ADR'ler bağlayıcı kısıttır. Bir istek, plan,
implementasyon veya önerilen karar bunlarla çelişiyorsa — ya da contract'lar ile
ADR'ler birbiriyle tutarsız görünüyorsa — **dur ve kullanıcıya sor**. Çelişkinin
etrafından dolaşma, kısıtı sessizce yeniden yorumlama, workaround icat etme.
Çelişki çözülene kadar kabul edilmiş ADR yetkilidir.

## Planla

1. İlgili `contracts/` yüzeylerini, ADR bölümlerini ve FORBIDDEN'ı incele.
2. Repo durumunu ve [`docs/plan/CURRENT.md`](../plan/CURRENT.md) dosyasını oku.
3. Sıradaki iş için [`docs/ROADMAP.md`](../ROADMAP.md) kullanılır.
4. Plan dokümanı gerekiyorsa
   [`docs/plan/README.md`](../plan/README.md) içindeki beş bölümlü formatı izle
   ve `docs/plan/ready/` altına yaz.

Riskle orantılı ol: küçük bir değişiklik plan dokümanı gerektirmez. Plan
davranış ve mimari düzeyde karar-tam olur; exact kod reçetesi vermez.

## İş ver

İşi tarif ederken şunlar net olmalı: hedef (gözlemlenebilir tek sonuç), sınırlar
(kapsam dışı ne var), done koşulu ve hangi doğrulamanın koşulacağı. Branch adı ve
base commit belirtilir.

Kapsam genişlemesi gerekiyorsa iş verilmeden önce kullanıcı onayı alınır.

## Review et

1. İlgili contract, ADR ve FORBIDDEN kurallarını incele.
2. Gerçek repoyu incele: branch, base ve HEAD'i doğrula; tam diff'i karşılaştır;
   değişen dosyaları ve yakın kodu oku.
3. Kapsam, mimari, authorization, secret, bağımlılık, migration ve
   compatibility uyumunu doğrula.
4. Maddi doğrulama iddialarını bağımsız olarak kontrol et — en küçük ilgili
   reproducer ile.

**Raporun kendisiyle yetinme.** Karar: `ACCEPT` (tamam), `FIX` (yaklaşım geçerli,
düzeltme gerekli) veya `REPLAN` (kapsam/mimari yeniden planlanmalı).

## Plan tamamlandığında

- Contract/ADR çelişkisi çözülmeden hiçbir şey kabul edilmez.
- Bütün phase, kabul adımı, invariant ve Done maddesi kanıtlandığında
  `docs/plan/CURRENT.md` güncellenir; tamamlanma tarihi ve maddi sapmalar
  kaydedilir.
- Kabul edilmiş migration'lar ve arşivlenmiş planlar yeniden yazılmaz.
