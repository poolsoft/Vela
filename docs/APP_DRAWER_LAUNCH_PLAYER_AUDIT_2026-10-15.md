# OsmAnd karşılaştırması: çekmece, açılış ve player

## Kaynakta bulunanlar

OsmAnd `AppDrawerFragment.java`: static liste önbelleği, bellek sınırlandırılmış simge önbelleği, arka planda simge yükleme, paket değişikliği alıcısı; uzun basmada normal/split/overlay dock ekleme, uygulama bilgisi ve kaldırma.

`AppDockFragment.java`: dock uzun basma menüsü ve kaydedilen açılış modu. Split yolu `FLAG_ACTIVITY_LAUNCH_ADJACENT` kullanıyor. `OverlayWindowManager.java`: harici uygulamalar için `ActivityOptions.setLaunchBounds` ile freeform isteği; bazı özel uygulamalar için ayrıca kendi WebView/yerleşik içerik pencereleri var. Harici tüm uygulamaları launcher içine gömen genel bir mekanizma değil. Adapter ve fragment açılış yolları da birebir aynı davranmıyor.

Vela'da önceki durum: her çekmece oluşturulduğunda liste/simgeler yeniden sorgulanıyordu; paket dinleyicisi yoktu. Uzun basma yalnız dock'a normal ekliyordu. JSON içinde launchMode vardı fakat ana tıklama yolu bunu okumuyordu; enumdaki PANEL/POPUP ile OsmAnd SPLIT_SCREEN/OVERLAY adları da farklıydı.

## Uygulananlar

- Süreç ömürlü uygulama listesi önbelleği; yeni host eldeki listeyi hemen gösterir. Progress yalnız ilk veri yokken görünür, yenilemede eski liste kalır.
- Tek paket dinleyicisi: ekleme, kaldırma, güncelleme, etkinlik değişikliği; dil ve harici depolama erişilebilirliği değişimleri de listeyi yeniler. Eşzamanlı yenilemelerde eski taramanın sonucu yayımlanmaz.
- Görünen hücreler için arka planda simge yükleme ve 4 MB LRU. Hücre geri dönüşümünde işler iptal edilir; simge drawable'ları farklı ImageView'ler arasında aynı sınırları paylaşmaz. Diğer mevcut seçim ekranlarının simge davranışı korunur.
- Çekmece uzun basma menüsü: dock'a normal/split/freeform ekleme, uygulama bilgisi, sistem kaldırma ekranı. Dahili uygulamalarda dış uygulama eylemleri gizlenir.
- Dock uzun basma menüsünde açılış tercihi kaydedilir; ana başlatma yöneticisi bu tercihi okur. Önceki PANEL/POPUP değerleri split/freeform olarak da desteklenir.
- Split, Android'e gönderilen yan yana açılma isteğidir; platform ve hedef uygulama desteklemezse tam ekrana açılabilir. Freeform yalnız sistem destek bildirdiğinde seçilebilir. Root veya gizli API ile zorlanmaz. OsmAnd'ın özel WebView overlay içerikleri bu değişiklikte taşınmadı.
- Unified panelin önceki/sonraki simgeleri platform raster kaynağı yerine Vela vektörlerini kullanır. Büyük player'ın XML başlangıç padding'i çalışma anıyla eşitlendi; buton ölçümü, yalnız kök panel boyutuna değil gerçek kontrol alanının yerleşimine bağlandı. Bu değişiklik gözlenen boyut sıçramasının olası iki nedenini giderir; aralıklı belirti cihazda ayrıca gözlenmelidir.

## Doğrulama

- Son kaynaklar için `:app:compileCarReleaseKotlin --offline --max-workers=2` başarılı (2m 13s).
- Kaynak XML ve değişen dosyaların diff kontrolü geçti.
- `scripts/check_app_drawer_cache.py` yeni APK'da çekmeceyi üç kez açıp tek tarama yapıldığını logcat ile doğrular. Henüz cihazda çalıştırılmadı.
- Yeni APK ile simge boyutu, paket ekleme/kaldırma sonrası yenileme ve split/freeform davranışı ayrıca test edilmelidir. Fiziksel teybin pencere desteği emülatörden çıkarılamaz.
