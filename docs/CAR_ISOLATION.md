# Vela araç başlatıcısını ana projeden ayırma

Bu dalda harita ve navigasyon kodu `app/src/main` içinde kalır. Araç teybine özel başlatıcı `car` derleme türündedir. `standard` türünde ana ekran (HOME) kaydı, araç müzik servisleri, bunların izinleri ve araç arayüzü bulunmaz. İki türün uygulama kimliği `app.vela` olarak kalır; böylece araçta kurulu sürümü güncellerken veriler korunur. Aynı cihaza bu iki türden yalnızca biri kurulabilir.

Çalışma önce `codex/vela-car-isolation` dalında `7d41b2af` olarak kaydedildi. Güncel `upstream/main` (`698f5d85`) ile `codex/vela-upstream-integration` dalında birleştirildi (`f5b89983`). Taşıma öncesi tam çalışma ağacı yedeği `D:/Projects/CarWorkspace/Vela-backups/Vela-working-tree-20260924-104652.zip` dosyasındadır; SHA-256: `b292fae07088c6d3ca091ca9685497db9b1b0711ca9fcfdbc108ae859babf08c`.

## Dosyaların yeri

- `app/src/main`: harita, rota, ortak ayarlar ve taşınabilir yedekleme.
- `app/src/car`: araç başlatıcısı, HOME ve müzik servislerinin manifest kayıtları, izinleri, görselleri ve araç ayarlarının yedekleme desteği.
- `app/src/standard`: araç başlatıcısı olmadan çalışan karşılıklar. Standart sürüm, araç sürümünde alınan yedekten harita ve kişisel verileri yükleyebilir; araç ayarlarını uygulamaz.

Ortak kod, iki derleme türünde ayrı karşılığı bulunan `CarIntegration` ve `LauncherBackupPort` üzerinden araç özelliklerine ulaşır. Yeni araç özellikleri `src/car` içine eklenmeli. Mevcut Android Auto ekranları ortak kodda kalır; teybin HOME başlatıcısından farklıdır.

## Derleme ve upstream güncellemesi

Araç sürümünü `:app:assembleCarDebug`, standart sürümü `:app:assembleStandardDebug` ile derleyin. Yayın sürümleri `:app:assembleCarRelease` ve `:app:assembleStandardRelease` görevleridir. Forkun otomatik yayın çıktısı araç sürümüdür. Müzik arayüzünün statik denetimi: `python tools/check_car_music_contract.py`.

Upstream birleşmesindeki yayın iş akışı, manifest ve harita görünümü çakışmaları çözüldü. Birleşmiş kodla araç ve standart geliştirme APK'ları ile araç yayın APK'sı derlendi. Müzik arayüzünün statik kontrolü geçti. Gerçek teyipte müzik, izin ve yedekten yükleme kabul testleri ayrıca yapılmalı; telefon verileri üzerinde geri yükleme deneyi yapılmadı.

## Kalan doğrulamalar

1. İki hata ayıklama sürümünü derleyip temiz emülatöre kurun. `standard` sürümünde HOME rolü ve araç müzik servisi bulunmadığını, `car` sürümünde bulunduğunu kontrol edin.
2. Araç açılışı, harita, müzik sekmeleri, izinler, uygulama çekmecesi ve yatay/dikey yerleşimi emülatörde ve teyipte deneyin.
3. Telefon veya emülatörde yedek alın; araçta harita, rota, favoriler ve ayarları yükleyin. Araç yedeğini standart sürüme de yükleyip araç ayarlarının uygulanmadığını kontrol edin.
4. İlk başarılı derlemeden sonra otomatik yayındaki APK yolunu, imzalamayı ve başlangıç profili üretimini doğrulayın.
