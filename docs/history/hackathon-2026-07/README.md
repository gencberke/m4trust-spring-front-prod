# Hackathon arşivi — Temmuz 2026

Bu dizin M4Trust'ın hackathon dönemindeki plan ve karar kayıtlarını tutar.
**Hiçbiri güncel proje durumu değildir.** Tarihsel kayıttır: kararların *neden*
alındığını taşır, *ne yapılacağını* değil.

## Güncel kaynaklar

| Soru | Dosya |
|---|---|
| Bugün kabul edilmiş durum ne? | [`docs/plan/CURRENT.md`](../../plan/CURRENT.md) |
| Bundan sonra ne yapıyoruz? | [`docs/ROADMAP.md`](../../ROADMAP.md) |
| Bağlayıcı mimari kural ne? | [`architecture-decisions/ADR-INDEX.md`](../../../architecture-decisions/ADR-INDEX.md) |
| Neyi yapmak yasak? | Historical `FORBIDDEN.md` (removed; see Git history) |

## İçerik

```text
done/          Bütün Done koşulları kabul edilmiş slice planları (Slice 0 → Plan 18c)
done/review/   Implementer review handoff kayıtları (kabul kanıtı)
planning/      Taslak planlar ve sıralama notları; birçoğu kendi içinde
               "proje durumu değildir" banner'ı taşır
planning/gates/ Founder gate kararları (G1–G4, simulation-only pivot)
review/        Dönemin tek aktif implementasyon review inbox'ı (req-review.md)
```

## Neden arşivlendi

Hackathon dönemi `planning/ → ready/ → review/ → done/` akışı ve sekiz bölümlü
task-packet protokolü, yarış temposunda iki kişilik bir ekip için tasarlanmıştı.
Yarış bittiğinde bu süreç makinesi ağır kalıyordu. Kayıtlar silinmedi çünkü
historical numbered ADR'ler (özellikle ADR-014; removed, see Git history)
buradaki gate kararlarını kanıt olarak referans veriyor.

Bu dizindeki dosyalar arasındaki çapraz referanslar arşiv içi yollara göre
güncellenmiştir; dosyaların gövde metinlerine dokunulmamıştır.
