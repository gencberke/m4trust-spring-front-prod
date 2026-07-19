# Yerel altyapı

Bu Compose projesi yalnızca yerel geliştirme içindir. PostgreSQL, RabbitMQ ve MinIO'yu kalıcı, proje kapsamlı volume'larla başlatır. Varsayılan kimlik bilgileri bilinçli olarak yalnızca yerel placeholder değerlerdir; staging veya production ortamında kullanılmamalıdır.

Komutları repository kök dizininde PowerShell ile çalıştırın.

## Başlatma ve durum

Tek komutla altyapıyı başlatın ve üç servisin de healthy olmasını bekleyin:

```powershell
docker compose --project-name m4trust-local --file .\infra\compose.yaml up --detach --wait
```

Durumu ve health bilgisini görüntüleyin:

```powershell
docker compose --project-name m4trust-local --file .\infra\compose.yaml ps
```

Yerel erişim noktaları:

- PostgreSQL: `127.0.0.1:5432`
- RabbitMQ: `127.0.0.1:5672`
- RabbitMQ yönetim arayüzü: `http://127.0.0.1:15672`
- MinIO S3 API: `http://127.0.0.1:9000`
- MinIO konsolu: `http://127.0.0.1:9001`

Port veya yerel credential değiştirmek gerekirse `infra/.env.example` dosyasını `infra/.env` olarak kopyalayıp değerleri düzenleyin. `infra/.env` Git tarafından ignore edilir. Compose dosyası `.env` olmadan da yerel varsayılanlarla çalışır.

Başlatma, `m4trust-documents` private bucket'ını da deterministik olarak oluşturur,
object versioning'i açar. MinIO, `http://localhost:5173` için direct browser
PUT/GET CORS kurallarını server seviyesinde uygular. Bucket ve object listing
public değildir.

## Mock AI Worker profili

AI document extraction akışını gerçek RabbitMQ sınırıyla çalıştırmak için
opsiyonel Mock AI Worker profilini açın:

```powershell
docker compose --project-name m4trust-local --file .\infra\compose.yaml --profile mock-ai up --detach --build --wait
```

Profil verilmezse worker kapalı kalır; PostgreSQL, RabbitMQ ve MinIO normal şekilde
çalışmaya devam eder. Bu davranış, analiz talebinin worker yokken `QUEUED` kalıp
worker açıldığında işlenmesini doğrulamak için de kullanılır.

`M4TRUST_MOCK_AI_SCENARIO` değeri `auto` (varsayılan), `success`,
`retryable_failure` veya `duplicate` olabilir. `auto` modunda senaryo dosya
adından seçilir; production event contract'ına test alanı eklenmez. Ayrıntılı
senaryo adları ve doğrulama komutları `tools/mock-ai-worker/README.md` içindedir.

Core API'nin tarayıcı için ürettiği `localhost` presigned URL'leri container
içinden `host.docker.internal` üzerinden indirilir ve imzalanmış `Host` başlığı
korunur. Bu yalnızca yerel transport köprüsüdür; event içindeki URL yeniden
yazılmaz.

## Seed

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-seed.ps1
```

Slice 0'da business verisi bulunmadığı için seed komutu açıklayıcı bir mesajla başarılı olarak tamamlanır.

## Reset

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-reset.ps1
```

Reset yalnızca sabit `m4trust-local` Compose projesinin container, network ve named volume'larını kaldırır. Başka Compose projelerine veya Docker kaynaklarına dokunmaz. Ardından başlatma komutunu tekrar çalıştırın.

Komutun çalıştırmadan önce hedefleyeceği sabit proje kapsamını görmek için `-WhatIf` ekleyebilirsiniz:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\dev-reset.ps1 -WhatIf
```
