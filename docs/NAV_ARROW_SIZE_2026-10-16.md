# Navigasyon oku boyutu

## Gözlem

Emülatörde 0.4.120 üzerinde navigasyon sırasında mavi disk yaklaşık 150 px çapındaydı. Büyük/çok büyük boyut tercihi kayıtlı değildi. Takipten çıkıp geri dönüldüğünde de genişliği büyük kaldı; yalnız iki çizim yolu arasındaki geçiş neden olarak doğrulanmadı. Navigasyon takip görünümü tekrar açıldı.

202 px bitmap tam fiziksel boyutunda Compose'a veriliyordu. Harita simgesinin de açık bir ekran boyutu kuralı yoktu. Ayrıca takip oku kamera eğiminin kosinüsüyle dikey olarak sıkıştırılıyordu; emülatör/düşük donanım ve kuzey-yukarı görünümde 0°, diğer durumda varsayılan 55° olması görünür yüksekliği değiştiriyordu.

## Değişiklik

- Yüksek çözünürlüklü bitmap 202 px kalır; normal ekrandaki kutusu 88 dp olur. Büyük 1.25x ve çok büyük 1.5x tercihler korunur.
- Ortak `NavPuckSizing` hem Compose kutusunu hem MapLibre iconSize değerini belirler. Haritaya eklenen bitmapin yoğunluğu açıkça 160 DPI seçilir; böylece bitmap yoğunluğundan gelen ikinci bir ölçek olmaz.
- Navigasyon simgesi viewport'a göre çizilir; takip görünümündeki tilt squash kaldırılır. Kamera eğimi okun boyutunu değiştirmez; yön dönüşü korunur. Normal konum yön ışınının önceki harita davranışı korunur.
- Yol adı ve GPS uyarısı boşluğu, ok boyutu ve ekran yoğunluğuna göre hesaplanır.

## Kontrol

`:app:testCarReleaseUnitTest --tests app.vela.ui.map.NavPuckSizingTest --offline --max-workers=2` başarılı (6m 55s): 3 test, 0 hata/başarısızlık. Normal boyut sınırı, üç kullanıcı boyutu/dört ekran yoğunluğunda iki çizim yolunun eşitliği ve etiket boşluğu sınandı. Kotlin/Java derlemesi ve diff kontrolü geçti. Yeni APK ile görsel kontrol henüz yapılmadı.

Önceki ekran görüntüleri yerel `.logs/arrow-size/` altında. Kaynak başvurusu: MapLibre android-v10.3.7 Style.toImage, bitmap yoğunluğunu 160'a bölerek sprite pixelRatio değerini oluşturur: https://github.com/maplibre/maplibre-native/blob/android-v10.3.7/platform/android/MapboxGLAndroidSDK/src/main/java/com/mapbox/mapboxsdk/maps/Style.java#L1252
