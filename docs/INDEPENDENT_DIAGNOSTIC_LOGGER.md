# Ayrı süreçte tanı kaydı

## Ne değişti?

Tanı APK'sında logger `app.vela:diagnostics` adlı ayrı süreçte çalışır. Harita, müzik oynatıcı, Piper veya launcher arayüzünü başlatmaz. Normal APK'da bu servis başlatılmaz.

- Uygulamanın erişebildiği logcat kayıtları `Android/data/app.vela/files/logs/logcat-diagnostics.log` dosyasına yazılır. Aktif dosya 2 MB, önceki dosya `.old` adıyla en fazla 2 MB'dır.
- Ana süreç, servise bir Binder bağlantısı gönderir. Bağlantının ölmesi, logcat satırı olmasa bile süreç kaybını bildirir. Servis son ana süreç günlüğünü ve Android'in verebildiği çıkış bilgisini `crash-…-diagnostics-….txt` dosyasına yazar. Bu bir exception veya kesin kill nedeni değildir.
- Ana süreç günlüğü 15 saniye boyunca yenilenmiyorsa, bağlantı hâlâ canlı olsa da ayrı bir takılma gözlemi kaydedilir. Depolama gecikmesi de bu duruma yol açabilir.
- Ana süreç öldükten sonra logcat 60 saniye daha kaydedilir. Bu sırada launcher tekrar açılır ve bağlantı kurarsa kayıt devam eder. Ayarlardan uygulamayı kapat / yeniden başlat işlemi servisi de durdurur.
- Son 32 işlem adımı, iş parçacığı adı, zaman, bellek ve yaşam döngüsü raporda tutulur. Tanı sürümünde üç saniyelik ana iş parçacığı takılması için en fazla dakikada bir yığın kaydı alınır. Bu, Android'in doğruladığı bir ANR olarak adlandırılmaz.
- Raporlar önce geçici dosyada tamamlanır, sonra görünür adına taşınır. Yazım yarıda kesilirse boş exception dosyası yayımlanmaz. İç ve dış kayıtlar süreç başına en fazla beş rapor tutar.

## Açılış yükünü azaltan değişiklikler

Kayıtlı Piper motorunun seçilmesi artık açılışta native ses modelini yüklemez. Rota oluşturma, açık ses seçimi veya konuşma gerektiğinde yükleme yapılır. Native model yükleme, ilk üretim ve hata aşamaları ayrıca kaydedilir. Müzik indeksinde okunan byte miktarı, kayıt sayısı, her 128 kayıtta ilerleme ve dosya kontrol süresi kaydedilir; otomatik tarama açılmadı.

Saat yalnız gösterilen dakika değiştiğinde yeniden yazılır. Medya başlığı, sanatçı ve oynatma durumu değişmediyse panel yeniden güncellenmez. Saat ve görselleştirici görünür yaşam döngüsüne bağlanmıştır. Düşük RAM cihazlarda dock yaklaşık 500 ms, panel yaklaşık 1500 ms sonra kurulur; tek karede bütün arayüzü kurma yükü azaltılır.

## Sınırlar

Logcat'a hiç yazılmamış veya Android'in bu uygulamaya göstermediği bir neden geri getirilemez. Ayrı servis root, ADB veya READ_LOGS yetkisi kazandırmaz. API 27 teypte Android'in uygulamalara sunduğu geçmiş süreç çıkış nedeni API'si yoktur; rapor bunu açıkça söyler. Android/OEM bütün uygulama UID'sini öldürürse tanı süreci de ölebilir. Foreground servis süreç önceliğini etkiler; tanı APK'sının davranışı normal APK ile birebir aynı kabul edilmez.

Android'in Binder ölüm bildirimi: https://developer.android.com/reference/android/os/IBinder.DeathRecipient

## Doğrulama

Derleme ve OsmAndAuto x86 emülatöründe bağımsız süreç / kontrollü sonlandırma testinin sonucu tamamlandığında aşağıya kaydedilir. Emülatör testi teypteki gerçek kapanma nedenini kanıtlamaz.
