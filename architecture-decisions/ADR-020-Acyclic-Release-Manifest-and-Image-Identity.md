# ADR-020: Acyclic Release Manifest and Image Identity

- Durum: Accepted
- Tarih: 22 Temmuz 2026
- Karar sahibi: M4Trust founder/user
- Kapsam: Main `web`/`core` image kimliği ile release manifestinin tek yönlü bağı
- Kısmen değiştirir: ADR-016 §§2.4, 2.5 ve 4 içindeki
  `io.m4trust.release-manifest-digest` image label zorunluluğu
- Korur: ADR-016'nın build-once, exact image digest promotion, contract digest,
  SBOM/provenance ve release evidence kuralları

## 1. Bağlam

ADR-016 release manifestinin `web` ve `core` image digest'lerini taşımasını, aynı
image'ların da release-manifest digest'ini label olarak taşımasını istemiştir.
Image label image digest'ini değiştirdiği için bu çift yönlü bağ döngüseldir.
Contract bundle digest'ini release manifest digest'i gibi kullanmak da iki farklı
kimliği sessizce birleştirir.

## 2. Karar

Release kimliği tek yönlü ve basit olacaktır:

1. `web` ve `core` bir kez build edilip immutable digest ile publish edilir.
2. Image config en az `org.opencontainers.image.revision` taşır. Bundle'ı paketleyen
   `core` ayrıca `io.m4trust.contract-bundle-digest` taşır. Image config'e
   `io.m4trust.release-manifest-digest` yazılmaz.
3. Release manifesti image publish işleminden sonra oluşturulur ve ADR-016 §2.4'teki
   main SHA, exact `web`/`core` image digest'leri, contract bundle digest, migration
   ceiling ve build time/version alanlarını taşır.
4. Manifest digest'i exact yayımlanmış manifest bytes'ının SHA-256 değeridir.
   Manifest mevcut CI artifact/provenance hattında saklanır; promotion ve deployment
   evidence bu digest'i kaydeder ve manifestteki image digest'lerini kullanır.
5. Yeni registry servisi, custom referrer protokolü veya image'ı ikinci kez build
   eden bir bağlama adımı eklenmez.

Eksik/bozuk main SHA veya digest başka geçerli görünen değerle değiştirilmez.
Özellikle kırk sıfır veya contract bundle digest'i release kimliği yerine
kullanılamaz. Production/release gate fail closed olur; local/test açık fixture
değeri kullanabilir.

## 3. Sonuçlar

- Image digest'i release manifestine tek yönlü bağlanır; hash döngüsü yoktur.
- Staging ve production aynı exact image digest'lerini manifestten tüketir.
- Contract digest ile release manifest digest'i ayrı anlamlarını korur.
- ADR-016'nın recovery, topology, observability ve AI ownership sınırları değişmez.

## 4. Kabul kapıları

- Core image revision ve contract-bundle label'ı source/release girdileriyle eşleşir.
- Image smoke packaged bundle digest'ini yeniden hesaplayıp source ve label ile
  karşılaştırır.
- Release manifesti yalnız image digest'leri bilindikten sonra oluşur; manifest
  digest'i exact artifact bytes'ından hesaplanır.
- Promotion manifestteki exact image digest'lerini kullanır ve image rebuild etmez.
- Release-manifest digest yerine contract digest veya sentetik placeholder yazılmaz.
