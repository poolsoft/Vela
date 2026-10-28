# Harita çizim profili ve boşta çalışma

## Mevcut ayarların incelemesi

MapSettingsScreen içindeki 3D binalar ayarı Buildings3d üzerinden vela_settings/buildings_3d anahtarına kaydediliyor; renderer ayar kopyası ayrı sürece taşınıp tekrar başlatılıyor. VelaMapView building-3d katmanının görünürlüğünü bu değere göre değiştiriyor. Ayrıca navigasyon ve uydu görünümünde zaten kapalı. Dolayısıyla kullanıcının bunu kapatması geçerli bir optimizasyon; yeniden kapatmak yeni kazanç sayılmamalı.

Arazi gölgelendirmesi ayrı topographyOn değeriyle hillshade katmanına uygulanıyor; araç navigasyonunda bu da zaten gizleniyor. Microsoft bina ayak izi ek katmanı, düz binalar ve 3D binalar birbirinden farklı. Bu çalışma düz bina verilerini, adresleri, POI'leri veya trafik/navigasyon bilgisini kaldırmıyor.

## İsteğe bağlı profil

Ayarlar > Harita > Harita çizim profili:

- Standart (varsayılan): kullanıcının mevcut katman tercihleri ve ekran yenileme hızına göre çizim bütçesi.
- Hafif: en fazla 30 FPS; kamera/ok takip döngülerinde de yaklaşık 30 Hz işlem sınırı; düz kamera ve eğme hareketinin kapatılması; 3D binalar ve arazi gölgelendirmesinin çizimde gizlenmesi.

Profil mevcut katman ve renk ayarlarını değiştirmez. Standart'a dönüldüğünde kayıtlı katman tercihleri yeniden uygulanır; önceki kamera eğimi otomatik geri getirilmez, standart eğim hareketi yeniden kullanılabilir. Profil tercihi kalıcıdır, renderer sürecine aktarılır ve haritayı/stili yeniden oluşturmadan uygulanır. Profil arka plandaki konum alma, sesli navigasyon veya müzik servisini kapatmaz.

Renk stili ile çizim profili farklıdır. Mevcut Modern/Klasik palet seçimi Görünüm bölümündedir ve Hafif ile birlikte kullanılabilir. Yeni MapLibre stil JSON'ları da hazırlanabilir; offline çalışması için kullanılan source, sprite ve glyph kaynaklarının yerel karşılığı gerekir. Sadece renk değiştirmek tek başına daha hızlı çizim garantisi vermez. Bu değişiklik harici stil indirme veya yeni bir stil editörü eklemez.

## Genel düzeltmeler

- Navigasyon yokken NavController'ın dead-reckoning işi saniyelik uyanmak yerine navigasyon başlayana kadar StateFlow üzerinde bekler.
- Renderer'ın yaşam döngüsü durduğunda iki kare bazlı kamera döngüsü bekler; dönüşte zaman farkı sıfırlanır. Harita nesnesi ve stil korunur.
- Harita görünmüyorken ekran üzerinden hız sınırı sorgusu ve yol etiketi sorgusu da bekler; arka plandaki sesli navigasyon motorunun rota işlemleri korunur.
- Ayarlar haritayı tamamen örttüğünde izole renderer duraklatılır. Ayarlardan dönüşte aynı MapView/stil yeniden kullanılır; tercih değişikliklerinin son hali renderer'a aktarılır. Activity hâlâ ön planda olsa bile bu görünürlük kontrolü geçerlidir.
- Yeniden başlangıç sayacı, kare bekleyişi zaten askıdayken arka plana gidilmiş olsa bile dönüşte ilk kare zaman farkını sıfırlar; kapalı geçen süre tek bir kamera animasyonu adımı olarak uygulanmaz.
- Aynı Surface ve aynı boyut için yinelenen SurfaceHolder callback'i tekrar OPEN göndermez. Gerçek boyut/yüzey değişiminde ve arka plandan dönüşte bağlama devam eder; önceki resize/clip düzeltmesi korunur.

## Doğrulama

Yerel Kotlin derlemesi başarılı (ilk profil değişikliği 8 dakika 1 saniye, küçültülmüş renderer girişi 4 dakika 6 saniye). Son kaynak 0b83c7cbb için Actions 38893076104 başarılı; çekirdek ve renderer sınır testleri geçti. Diagnostic-133 / 0.4.133-diagnostic / versionCode 2233 x86 APK emülatöre mevcut veriler silinmeden kuruldu. Wi-Fi ve mobil veri kapalıydı.

Diagnostic-132 otomatik derleme/testleri geçmesine rağmen API-30 x86 emülatörde VelaMapView için VerifyError verdi. Bu sürümün release başlığı ve notu kullanılmaması için işaretlendi. Hata sessizce yutulmadı: renderer girişindeki 104 ayrı parametre ve tekrarlanan scene/callback dönüşümü kaldırıldı. Zaten mevcut MapRenderScene ve MapRenderCallbacks doğrudan geçirilerek native Compose girişi dört parametreye indirildi; izole süreç seçimi scene.Draw sınırına alındı. Veri alanı ve callback eşlemeleri korunuyor. Bu düzenlemeden sonraki APK ayrıca gerçek Android açılışıyla doğrulanmalı; JVM testlerinin geçmesi ART doğrulama başarısını kanıtlamıyor.

Diagnostic-133 cihaz testinde bu doğrulama da tamamlandı: hem launcher hem renderer açıldı, tam harita karesi geldi ve VerifyError tekrarlanmadı.

| Kontrol | Sonuç |
|---|---|
| Soğuk açılış | Tek MapStyle Reload, tr-guney.pmtiles; dünya arşivine gidip gelme yok; tam çizim geldi. Testin toplam 85,3 saniyesi bekleme ve araç işlemlerini de içerir, açılış süresi ölçümü değildir. |
| Hafif / Standart geçişi | Renderer MapProfile kaydı sırasıyla light fps=30 ve standard fps=60. Kaydedilmiş katman/renk tercihleri değişmedi; profil anahtarı renderer ayar dosyasına da ulaştı. Bu FPS değerleri uygulanan sınırdır, ölçülmüş gerçek FPS değildir. |
| Drawer, büyük müzik, tam harita, Ayarlar ve Android Ayarlar uygulamasından dönüş | Ana PID 30960 ve renderer PID 31067 korundu; toplam stil yükleme sayısı 1 kaldı. Son durumda normal launcher ve Standart profil bırakıldı. |
| Tam ekran çizimi | İlk anlık görüntüde eski harita genişliği ve sağda siyah alan vardı. Sekiz saniye beklendikten sonraki görüntüde tüm genişlik doldu. Kalıcı yarım çizim gözlenmedi; geçişin sıfır gecikmeli olduğu iddia edilmiyor. |
| Çökme kontrolü | Son PID için yeni main FATAL yok; son events logcat örneğinde bu iki PID için am_anr yok. Bu kontrol geçmiş kayıtların veya bütün olası senaryoların hatasız olduğu anlamına gelmez. |

Watchdog iki adet yaklaşık 3 saniyelik main-thread stall kaydı üretti: açılışta ve büyük müzik paneline geçerken. İkincisinin stack'i HardwareRenderer.nSyncAndDrawFrame içinde; bu bir sistem ANR doğrulaması değildir, ancak bütün takılmaların çözülmüş sayılmaması için kayda alındı. Panel geçişindeki kayıt .logs/map-profile-test-20261016/music-panel-stall.txt içinde.

Ayarlar haritayı örterken aynı emülatörde alınan iki kısa örnek: diagnostic-128 renderer'ı 10,83 saniyede 558 CPU tick; diagnostic-133 renderer'ı 11,70 saniyede 2 CPU tick tüketti. Süreler ve süreçler farklı; bu tek örnek kontrollü bir performans benchmark'ı değildir. Native renderer'ın örtülü ekran altında gereksiz çalışmasının durmasıyla uyumludur; uygulamanın toplam CPU'su veya fiziksel teyp kazancı olarak yorumlanmamalı.

Kanıtlar: .logs/map-profile-test-20261016/ içinde exercise.py, snapshot.py, check_boot.py, profile-result.json, transition-result.json, old-settings-cpu.json, new-settings-cpu.json, ekran görüntüleri ve loglar. Soğuk açılış çıktısı .logs/map-camera-bootstrap-test/profile133-result.json içindedir. Müzik kütüphanesi bu testte boştu; panel geçişi doğrulandı, parça çalma testi yapılmadı.

## Kalan doğrulama ve sonraki paket

Fiziksel teypte CPU/FPS ölçümü yapılmadan performans yüzdesi veya tüm takılmaların giderildiği iddiası yoktur. Özellikle 3D ve arazi zaten kapalıysa Hafif'in ek katkısı kare/işlem sınırı ve düz kamera olacaktır. Tam ekran geçişinin ilk karesindeki gecikme, düşük donanımda ayrıca ölçülmeli. Güvenli paket indeks yazımı, font swap koruması ve çevrimdışı rota uç kontrolü bu ilk çizim/boşta çalışma paketinin dışında, upstream raporundaki sıradaki işlerdir.


## Özel profil ve geçici Hafif başlangıç

- Özel profilde 15-60 FPS sınırı ve kamera eğimi seçilebilir. Mevcut katman anahtarları kullanılır; ikinci bir 3D bina veya arazi tercihi oluşturulmaz.
- Özel FPS/eğim değerleri profil değiştirildiğinde korunur.
- Her yeni harita örneği kayıtlı seçimden bağımsız olarak geçici Hafif bütçesiyle başlar. Stil hazırken ilk kullanılabilir çizim karesi gelince kayıtlı profil aynı MapView üzerinde uygulanır. Tercih dosyası değiştirilmez; profil geçişi stil yüklemesi başlatmaz.
- Arka plandan dönüş, panel büyütme ve sıradan boyut değişiklikleri başlangıç bütçesini yeniden etkinleştirmez.
- FPS zamanlayıcısı 45 FPS gibi ara değerleri destekler; uzun duraklamadan sonra birikmiş kareleri topluca çalıştırmaz.
- Kaynak commit: `4a75eb2dc`; Build APK çalışması `38897473872` başarılı, `diagnostic-134` x86 APK emülatöre veri silmeden kuruldu.
- Yerel `compileCarReleaseKotlin` ve `RenderFrameBudgetTest` başarılı: 2 test, 0 hata.
- Çevrimdışı soğuk açılışta kayıtlı Özel 45 FPS/eğim açık tercihine rağmen önce `profile=light fps=30 tilt=false startup=true requested=custom`, ardından `profile=custom fps=45 tilt=true startup=false requested=custom` kaydedildi. Yerel tr-guney stili yalnız bir kez yüklendi ve tam harita karesi geldi.
- Kanıtlar: `.logs/map-camera-bootstrap-test/profile134-custom-*`, `.logs/map-profile-test-20261016/custom-enabled.json` ve `custom-controls.png`.
- Bu test performans sorunlarının tamamının giderildiği anlamına gelmez: soğuk açılışta main-thread stall tanı kaydı hâlâ var. Fiziksel teyp doğrulaması yapılmadı.

- Özel arayüz testi de başarılı: 45 FPS/eğim açık seçimi, Hafif → Özel dönüşünde korundu; launcher/renderer süreçleri aynı kaldı ve stil yeniden yüklenmedi (`custom-result.json`).
