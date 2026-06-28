# CraftionFarmer Yayın Kontrol Listesi

## Build

- `git status --short --branch`
- `git diff --check`
- `mvn test`
- `mvn package`
- YAML parse: `config.yml`, `messages.yml`, `paper-plugin.yml`

## Jar Shade Kontrolü

- Oluşan jar şu hook paketlerini içermemeli:
- `me/clip/placeholderapi`
- `net/milkbowl/vault`
- `fr/euphyllia/skyllia`
- `de/oliver/fancynpcs`
- `com/fancyinnovations/fancynpcs`
- `io/github/fancyplugins/fancynpcs`
- `com/fancyplugins/fancynpcs`

## Backups

- `plugins/CraftionFarmer/config.yml` ve `messages.yml` yedeğini al.
- CraftionFarmer veritabanı dosyasını veya MySQL veritabanını yedekle.
- Geri dönüş için önceki çalışan jar dosyasını hazır tut.

## İlk Açılış

- Sunucuyu temiz konsolla bir kez başlat.
- Fatal config validation uyarısı olmadığını kontrol et.
- Veritabanı migration işlemlerinin tamamlandığını doğrula.
- `/farmer help` komutunun cevap verdiğini kontrol et.

## Hook Kontrolleri

- PlaceholderAPI yokken plugin açılmalı ve placeholder sistemi güvenli şekilde pasif kalmalı.
- PlaceholderAPI varken `%craftionfarmer_farmer_id%` ve storage placeholder değerleri cache’den dönmeli veya `-` olmalı.
- Vault yokken satış işlemleri deposit yapmadan güvenli şekilde başarısız olmalı.
- Vault varken fiyatlı bir ürünü sat ve yalnızca tek deposit oluştuğunu kontrol et.
- Skyllia yokken ada komutları hard crash olmadan güvenli hata vermeli.
- Skyllia varken `/farmer create`, `/farmer info` ve `/farmer reconcile [ada]` adayı çözmeli.
- FancyNPCs yokken plugin visual fallback ile açılmalı.
- FancyNPCs varken farmer NPC spawn/remove çalışmalı.

## Runtime Smoke Komutları

- `/farmer help`
- `/farmer create`
- `/farmer open`
- `/farmer info`
- `/farmer admin info [ada]`
- `/farmer admin logs [ada] [limit]`
- `/farmer reload`
- `/farmer reconcile [ada]`

## Persistence Smoke

- Eşya çek, restart at ve storage miktarının düşük kaldığını doğrula.
- Eşya sat, restart at ve duplicate deposit olmadan storage miktarının düşük kaldığını doğrula.
- Bir modül toggle et, restart at ve state değerinin korunduğunu doğrula.
- Mümkünse geçici DB hatası oluştur ve dirty retry loglarının geldiğini kontrol et.

## Rollback

- Sunucuyu kapat.
- Önceki jar dosyasını geri koy.
- Veri hatalı migrate olduysa config/database yedeklerini geri yükle.
- Sunucuyu başlat ve `/farmer help`, `/farmer open`, storage görünümünü kontrol et.
