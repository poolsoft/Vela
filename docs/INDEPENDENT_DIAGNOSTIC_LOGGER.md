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

8 Ekim 2026: [diagnostic-97 APK'ları](https://github.com/poolsoft/Vela/releases/tag/diagnostic-97), sürüm `0.4.97-diagnostic / 2097`, üretim imzasıyla ve debuggable olarak oluşturuldu. [GitHub derlemesi ve kontrolleri](https://github.com/poolsoft/Vela/actions/runs/37807599264) başarılı. Yalnız `codex/maplibre-10.3.7` dalı güncellendi; main korunuyor.

- Yerel çekirdek testleri: 680 test, hata yok, 6 atlama. Sonradan eklenen `VoiceGuideStartupTest` de geçti; kayıtlı neural motorun ilk seçiminde ve tekrar seçiminde warmUp/konuşma çalışmadığını doğrular.
- Araç varyantı: 57 test, hata yok, 4 atlama.
- OsmAndAuto_LowRam_API30_x86 / emulator-5556 üzerine x86 APK mevcut veriler korunarak kuruldu. Diğer emülatör kullanılmadı. OsmAndAuto başlangıçta askıya alınmıştı; yeniden başlatılmadan devam ettirildi.
- Ana PID 9639 ve tanı PID 9698 ayrıydı. Ana süreç SIGSTOP ile 22 saniye durduruldu: tanı süreci çalıştı ve günlüğün 18.322 ms eskidiğini dosyaya kaydetti. Sonra SIGCONT ile devam ettirildi.
- Ana süreç kontrollü SIGKILL ile öldürüldü: tanı PID 9698 hayatta kaldı, Binder ölümü raporu yazıldı. Android API 30 çıkış kaydı aynı PID için `reason=2 (signal), status=9` verdi. Bu testte neden zaten uygulanan SIGKILL idi; teyp hatasıyla karıştırılmamalı.
- Launcher tekrar açıldı: yeni ana PID 10031, aynı tanı PID 9698'e bağlandı. Ayarlardaki Close application ile kapatıldığında tanı servisi/bildirimi durdu; tekrar açılışta logger yeniden çalıştı. Normal kapatma için yanlış Binder ölümü raporu oluşmadı.
- Kopyalanan raporların hiçbiri boş değildi. Müzik önbelleğindeki 17 kayıt yüklendi; bir tarama başlatılmadı. Bu testte harita süreci açılmadı. Emülatörde Piper modeli kurulu olmadığı için gerçek native model yükleme testi yapılmadı.

Başlangıç yedeği ve test kanıtları: `D:/Projects/CarWorkspace/Vela-backups/2026-10-08-independent-diagnostics/`. APK, git bundle, süreç günlüğü, ölüm/takılma raporları ve ekran görüntüsü burada tutuluyor. Emülatör testi teypteki gerçek kapanma nedenini kanıtlamaz.
