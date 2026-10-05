# MapLibre 10.3.7 / GLES2 denemesi

Deneme dalı: `codex/maplibre-10.3.7`. Başlangıç: `a880ea2dcc1e45490bb24bfdbd9b28fa8f2e773d` (main).
Geri dönüş yedeği: `D:/Projects/CarWorkspace/Vela-backups/2026-10-05-before-maplibre-10.3.7-a880ea2d/GERI_YUKLEME.md`.
Main bu denemeyle değiştirilmez. CI bu dalın APK'lerini artifact olarak yükler, main/canary yayınını değiştirmez.

## Değişiklik

- SDK 11.8.8 → 10.3.7. v10 API'si `com.mapbox.mapboxsdk`, GeoJSON `com.mapbox.geojson`, hareket algılayıcıları `com.mapbox.android.gestures` paketindedir. Kotlin alias'ları mevcut harita isimlerini korur.
- Renderer ön kontrolü ES3 yerine ES2 pencere yapılandırmasını sorgular. Tanı raporu motor sürümünü ve ES2 adaylarını kaydeder.
- SDK v10'da yerleşik PMTiles protokolü olmadığı için `PmtilesMapBridge`, rezerv `vela-pmtiles.invalid` URL'lerini SDK'nin desteklediği OkHttp interceptor üzerinden cevaplar. Gerçek sunucu/DNS bağlantısı bu URL'ler için yapılmaz. Yerel arşivler byte aralıklarıyla diskten, uzak arşivler doğrulanmış HTTP 206 Range yanıtlarıyla okunur. Dizini mevcut PmtilesReader ayrıştırır. Basemap ve overlay kaynakları bu adaptöre yönlendirilir.
- Arşiv/dizin önbellekleri ve gzip açılımı sınırlıdır. Kullanıcı harita dosyaları ve yedek biçimi değiştirilmez. HTTP sunucusu veya yeni bağımlılık eklenmez.
- Launcher/harita süreç sınırı, müzik ve çalışma alanı mantığı korunur.

## Kontroller

Yerel Gradle veya Android derleyicisi çalıştırılmayacak. GitHub Actions derlemesi ve mevcut birim testleri kullanılır. PMTiles köprüsünün sıkıştırılmış tile, eksik tile, dizin önbelleği ve bozuk/çok büyük dizin kontrolleri eklenmiştir.
GLES2 emülatörü: OsmAndAuto_LowRam_API30_x86; API30 x86; host GPU Quadro K620. Önceki 0.4.75 sürümünde ES2=3, ES3=0; harita `No config chosen` ve `render timeout: map-create` hatasıyla açılamadı; launcher kaldı.
Yeni v10 APK için derleme/kurulum, boş motor, gerçek basemap, Home dönüşü ve hata kurtarma doğrulaması henüz yapılmadı.
Bu deneme teyp sürücüsüyle birebir eşdeğer değildir.

## Resmi kaynaklar

- https://github.com/maplibre/maplibre-native/releases/tag/android-v10.3.7
- https://github.com/maplibre/maplibre-native/blob/android-v10.3.7/platform/android/MapboxGLAndroidSDK/src/main/java/com/mapbox/mapboxsdk/maps/renderer/egl/EGLConfigChooser.java
- https://github.com/maplibre/maplibre-native/blob/android-v10.3.7/platform/android/MapboxGLAndroidSDK/src/main/java/com/mapbox/mapboxsdk/module/http/HttpRequestUtil.java