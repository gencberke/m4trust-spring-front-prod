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
