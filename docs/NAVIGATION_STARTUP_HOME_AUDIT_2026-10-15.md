# Navigasyon girişleri, arama açılışı ve varsayılan Home incelemesi

Dal: codex/maplibre-10.3.7. İlgisiz çalışma dosyaları korunmuştur.

## Bulgular ve düzeltmeler

1. Manifestte MAIN + APP_MAPS kategorisi yoktu. Navigasyon uygulaması seçicilerinde görünmek için DEFAULT ile eklendi. geo: ve mevcut web bağlantıları korunur. Bazı OEM seçicileri sabit paket listeleri kullandığından bütün teyp yazılımlarında görünme garantisi verilemez.
2. google.navigation: VIEW filtresi ve q hedefini mevcut MapLink çözümleyicisine aktaran destek eklendi. Koordinat ve metin hedefleri için regresyon testi eklendi. Bu değişiklik dış hedefi mevcut yer/arama akışına açar; otomatik sürüş başlatma eklemez.
3. Arama kutusu dokunmatik modda açılışta odak alabiliyordu. Alan artık dokunma/OK ile etkinleşir; renderer hazır olmadan giriş ve tam ekran arama kapalıdır. Harita MapSurface çağrısı zaten arama açıkken de bileşimde kalır: otomatik açılan opak arama sayfası haritayı örtüyordu.
4. Arama odağı warmAsrForSearch çağırır. Otomatik odağın engellenmesi, kullanıcının aramayı açmadığı başlangıçta bu yoldan ASR ısıtılmasını da önler.
5. Android 8/9 izinler sayfası Home seçimini daima false gösteriyordu; sistemin çözdüğü HOME activity artık kontrol edilir. Android 10+ için RoleManager ve çözümlenen HOME birlikte desteklenir.
6. Başlangıçta Home rolünü isteyen veya varsayılan seçimi temizleyen kod bulunmadı. HOME bileşeni MainActivity olarak sabit tutuldu. Her açılışta gerçek seçimin kaybolması fiziksel cihaz logu olmadan doğrulanamadı. Yeni HomeRole satırı default ve çözümlenen paket/activity bilgisini kaydeder. UI durum düzeltmesi, sistem seçiminin gerçekten silinmesiyle aynı sorun değildir.
7. Kısa Google Maps bağlantısı ACTION_SEND ile çözülürken ACTION_VIEW ile yok sayılıyordu. Doğrudan dokunulan kısa bağlantılar da mevcut çözümleyiciye yönlendirildi.

## Doğrulama

- Kaynak farkı ve manifest girişleri incelendi.
- MapLinkParserTest koordinat/metin/boş google.navigation hedeflerini kapsar.
- Yerel :core:testDebugUnitTest --tests app.vela.core.MapLinkParserTest: BUILD SUCCESSFUL, 14 test, 0 hata.
- Actions 38773256011: başarılı APK derlemesi ve testler. https://github.com/poolsoft/Vela/actions/runs/38773256011
- Yayımlanan son kaynak APK: 0.4.115-diagnostic, https://github.com/poolsoft/Vela/releases/tag/diagnostic-115
- Kod commitleri: 0ef91e04 ve 0d04f308.
- Şu an bağlı emulator-5556 üzerinde başka test olabileceğinden kurulum/varsayılan launcher değişikliği yapılmadı. Fiziksel telefon/teyp doğrulaması yapılmadı.

## Kalanlar

- HomeRole loguyla cihazın seçimi gerçekten kaybedip kaybetmediğini ayırmak; gerekiyorsa sistemin Home çökme/kurtarma davranışını incelemek.
- İnternet açık/kapalı POI katmanlarındaki siyah simgelerin kesin nedeni önceki incelemede tamamlanmadı; font/sprite eksikliği kanıtı bulunmadı.
- Dönüşte harita bağlantısını koruyan fe5305bf değişikliği için fiziksel cihaz doğrulaması hâlâ gerekli.
