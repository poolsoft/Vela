# Sistem çubuklarını ilk pencere çiziminden önce uygulama — 16 Ekim 2026

## Değişiklik

MainActivity, immersive/durum çubuğu ayarlarını artık SplashScreen kurulumu sonrasında fakat `super.onCreate` öncesinde uygular. CarIntegration ayarları VelaApp içinde zaten senkron yüklenmiştir; geri yükleme varsa bu yüklemeden önce tamamlanır. Yeni bir açılış taraması veya gecikme eklenmedi.

Ortak sistem çubuğu uygulayıcısı, pencere henüz ekrana bağlanmamışken görünür olması istenmeyen durum çubuğunu geçici FLAG_FULLSCREEN ile kapatır. Pencere odak kazanınca bu bayrak bırakılır ve normal WindowInsetsController görünürlüğü yönetir; eski Android sürümlerinde klavyenin adjustResize davranışının kalıcı fullscreen bayrağı yüzünden bozulması önlenir.

API 30 ve üstünde bağlanmadan önceki InsetsController istekleri ertelenebilir. `onAttachedToWindow` içinde politika ilk yerleşimden önce tekrar uygulanır. Edge-to-edge kurulumu sonrası uygulama, Compose içindeki canlı ayar takibi, onResume ve odak geri kazanımı korunur. Diyaloglar aynı ortak politikayı kullanmayı sürdürür.

Açılış kaydı: `[SystemBars] Before activity create: immersive=..., statusBar=...`.

## Sınır

Bu değişiklik uygulamanın kendi ilk penceresi/yerleşimi içindir. Android'in uygulama kodu çalışmadan önce, manifest temasından oluşturduğu sistem başlangıç önizlemesi kayıtlı tercihi okuyamaz. Bu önizlemenin her tercihte dinamik olacağı iddia edilmez.

## Doğrulama

Kaynak farkı kontrol edildi; `git diff --check` başarılı. Kotlin derlemesi başlatıldı. Canlı/soğuk açılış için üç durum kontrol edilecek: immersive açık; immersive kapalı ve durum çubuğu açık; immersive kapalı ve durum çubuğu kapalı. Mevcut emülatör verileri korunacak.

## Tamamlanan doğrulama

Kotlin derlemesi ve Actions 38867000913 başarılı. 0.4.127-diagnostic x86 emülatöre veriler korunarak kuruldu. Üç soğuk açılışta ilk pencere ve 5 saniye sonraki pencere karşılaştırıldı: immersive açıkken iki çubuk gizli; immersive kapalı/durum çubuğu açıkken iki çubuk görünür; yalnız durum çubuğu kapalıyken status gizli ve gezinme görünür. Görünür durumun native requested-insets listesinde override bulunmaması Android'in varsayılan görünür davranışıdır; ekran görüntüsü de bunu doğrular. Üst arama alanı görünür durum çubuğunun altında yerleşti. Önceki immersive=true/status=false ayar dosyası test sonunda geri kondu.

Kanıtlar `.logs/system-bars-startup-test/` altında initial/settled ekran görüntüleri, pencere dökümleri ve `[SystemBars] Before activity create` kayıtlarıdır. Soğuk açılıştaki harita region/world/region tekrarları bu testte ayrıca görüldü ve ayrı kamera başlangıcı düzeltmesine konu oldu.
