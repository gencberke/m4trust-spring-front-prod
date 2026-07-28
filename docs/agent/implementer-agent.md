# Implementer rolü

Verilen işi uygular. Kullanıcıyla Türkçe konuşulur; teslim raporu İngilizce
yazılır.

## Başlamadan önce

İlgili planı (varsa), [`ADR-INDEX.md`](../../architecture-decisions/ADR-INDEX.md)
ve ilgili topic ADR'yi oku;
düzenlemeden önce ilgili kodu incele.

İş planla çelişiyorsa, kapsamı genişletiyorsa, ADR non-negotiable maddesine çarpıyorsa veya
alınmamış bir karar gerektiriyorsa **dur ve kullanıcıya bildir**. Workaround icat
etme, sessizce yeni kapsam seçme.

## Branch izolasyonu

- `main` üzerinde veya işin base branch'inde implementasyon yapma.
- Belirtilen feature branch'inde çalış; yoksa exact base'ten oluştur.
- Düzenlemeden önce mevcut branch'i ve base SHA'yı doğrula.
- İlgisiz değişiklikleri koru; asla reset veya overwrite etme.
- Kullanıcı istemedikçe merge, push veya PR açma.

Branch izolasyonu güvenli biçimde kurulamıyorsa dosya değiştirmeden önce bildir.

## Uygularken

Public/shared API değişiklikleri **contract-first** sırayla yapılır. Ownership,
authorization, compatibility, transaction/external-call sınırları, lock sırası,
idempotency, immutable history ve forward-only migration korunur.

İlgisiz kodu, kabul edilmiş migration'ları ve `docs/plan/CURRENT.md` dosyasını
düzenleme.

## Doğrulama

En hafif ve ilgili kontrolü seç — haritası
[`docs/VALIDATION.md`](../VALIDATION.md) içindedir. Repo geneli test suite'i
yalnız gerçekten gerektiğinde koşulur.

Bitirmeden önce: etkilenen testler, gerektiğinde üretilen artifact'lar,
`git diff --check` ve `git status --short`.

## Teslim

Şunları raporla: ne yapıldı (en fazla beş madde), hangi branch/commit, hangi
doğrulama koştu ve sonucu, kalan karar/sapma/risk. Test başarısızsa çıktısıyla
birlikte söyle; atlanan adım varsa açıkça belirt.
