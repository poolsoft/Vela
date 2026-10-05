# x86 emülatör testi — 5 Ekim 2026

AVD: OsmAndAuto_LowRam_API30_x86, emulator-5554, Android API 30, 1280×480. Paket: 0.4.72-canary (2072), main kaynak 1d4801731110610fe16fed118c2d275b4c7eda03. x86 APK SHA256: 3af84228a0c2e26fb4f0730eb435b23f41efceb8b329e26379e8d9d0a356c3c3.

Kullanıcı onayıyla eski, farklı imzalı Vela temiz kaldırıldı. Kurulum başarılı. Önceki APK ve harita kopyaları Vela-backups/2026-10-05-map-process-final-0.4.72/emulator-downloads altında saklandı. Diğer uygulamaların verileri silinmedi.

## Doğrulananlar

- İlk kurulum sihirbazı ve konum izni çalıştı. Sistem sesi seçildi.
- Ana ekran harita açılmadan gösterildi; bu durumda yalnız ana süreç vardı.
- Boş motor: library → map-create → style-loading → style-ready → frame olayları geldi.
- Normal harita görünür görüntü üretti. Ana süreç PID 8966, renderer PID 9451 ayrıydı.
- Haritada dokunma/sürükleme ve konum kartı çalıştı.
- Home ve ayrıca Android Ayarlar uygulamasına geçip geri dönüşte ana PID 8966 korundu; mevcut task öne geldi. Son dönüşte renderer PID 9954 ile style-ready/frame üretip görünür harita geldi.
- Renderer PID 9451 kontrollü SIGKILL ile sonlandırıldı. Ana süreç hayatta kaldı; harita hata görünümü geldi. Logcat: renderer process ended: frame; CrashCatcher: Map renderer failure tanı raporu kaydedildi.
- Haritayı kapatma ve tekrar açma ile görüntü geri geldi. Emülatörde son durumda yeni Vela kurulu ve harita açık bırakıldı.

## Açık bulgular ve sınırlar

- İndirme/yer bildirimi hata görünümünün üzerinde kalabiliyor; hata metnini ve yeniden deneme düğmesini kısmen örttü. Bağımsız Yeniden dene düğmesinin başarısı bu testte doğrulanmadı. Haritayı kapatıp açma doğrulandı. Hata görünümünü harita üstü panellerin önüne almak gerekiyor.
- UIAutomator, animasyonlu haritada idle alamadı; kontroller için ekran görüntüsü ve logcat kullanıldı.
- Emülatör konumunda mevcut hız 68 mph idi; gerçek sürüş testi yapılmadı.
- Bu test gerçek SIGSEGV/EGL arızası değildir; kontrollü renderer sonlandırmasıdır. API 27 ARM teyp sürücüsü ve Android Auto doğrulanmadı.
- Müzik taraması, navigasyon rotası, dikey dönüş ve tüm workspace özellikleri bu testin kapsamına alınmadı.
## 0.4.74 emülatör sonucu ve v10/v13 değerlendirmesi

CI #74 (37355244902), kaynak 2a3b0c3c: APK + core/app regresyon testleri başarılı. x86 0.4.74 (2074) veriler korunarak kuruldu. Grafik raporu: GLES 3.1, NVIDIA Quadro K620 host translator; ES2 config=3, ES3 config=3, motor ES3 window adayı=3. Ana PID 10378, renderer 10620. Renderer kontrollü SIGKILL sonrasında üstteki hata paneli ve Retry düğmesi görünür kaldı; düğmeye basınca renderer 10775 ile style/frame ve harita görüntüsü geri geldi. Native teyp çökmesi yeniden üretilmedi.

Sistem kaydı eşleşmesi için testte bir zaman yarışı bulundu: Binder ölüm bildirimi, Android exit-info timestamp'inden 12 ms önce geldi. Yalnız Binder süreç ölümü/binding ölümü bildirimlerinde kayıt üst sınırına 5 saniye eklenir; timeout/send hatalarında sonraki kurtarma kill'inin asıl hata diye raporlanmaması için sınır değişmez. Rapor failedAt ve exitUntil zamanlarını içerir.

Resmi sürüm kaynakları karşılaştırıldı: v10.3.1 EGLConfigChooser ES2 renderable/conformant config ister. v13.6.1 OpenGL EGLConfigChooser ES3 ister; yeni backend seçimi ES2 desteği anlamına gelmez. ES2-only teyp için v10.3.1 uyumluluk adayıdır, kesin çözüm sayılmaz. v10 `com.mapbox.mapboxsdk`, mevcut sürüm `org.maplibre.android` kullanır; paket/API ve overlay/stil uyarlamaları ayrıca değerlendirilmelidir. Bu değişiklikte SDK düşürülmedi.

Kaynaklar: https://github.com/maplibre/maplibre-native/blob/android-v10.3.1/platform/android/MapboxGLAndroidSDK/src/main/java/com/mapbox/mapboxsdk/maps/renderer/egl/EGLConfigChooser.java ve https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/MapLibreAndroid/src/sharedRenderer/opengl/java/org/maplibre/android/maps/renderer/egl/EGLConfigChooser.java.