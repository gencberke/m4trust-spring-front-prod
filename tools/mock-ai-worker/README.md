# Mock AI Worker yer tutucusu

Bu dizin gelecekteki geliştirme ve test amaçlı Mock AI Worker için ayrılmıştır.
Slice 0 kapsamında worker bilinçli olarak uygulanmamıştır; burada kod, package
manifesti, senaryo veya RabbitMQ queue/topoloji tanımı bulunmaz.

Gelecekteki worker RabbitMQ sınırında çalışacak, contract uyumlu command
mesajlarını tüketip contract uyumlu completed veya failed event'ler
yayımlayacaktır. Böylece AI modeli mocklanırken servisler arası messaging sınırı
gerçek kalacaktır.

Mock AI Worker production ortamında hiçbir zaman çalıştırılamaz ve production
queue'larına hiçbir zaman bağlanamaz. Gerçek secret'lar repository'ye,
konfigürasyon örneklerine veya loglara eklenmez.
