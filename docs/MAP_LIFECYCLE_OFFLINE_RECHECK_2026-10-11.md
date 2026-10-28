# Harita yaşam döngüsü ve çevrimdışı arama — 11 Ekim 2026

## Kanıtlanan sorunlar

- Home alias'ı ve uygulama simgesi aynı süreçte ayrı MainActivity kayıtları oluşturabiliyordu. Chrome → Home dönüşünde yeni renderer oturumu, mevcut renderer'ın eski oturumuyla çakışıyordu.
- Harita paneli boyut değiştirince aynı MapView/style korunmasına rağmen istemci `hasFrame=false` yapıp başlangıç panelini gösteriyordu. Çekmece ve büyük müzik panelinde görülen `display` bununla ilişkiliydi.
- Android API 30 SQLite FTS5 desteklemiyor. İçe aktarılmış `poi_fts` tablosunun varlığı, FTS5 desteği sanılıyordu. Gerçek sorgu `no such module: fts5` hatası veriyordu.
- Arama sonuçları gösterilmeden önce 20 adresin ters geocode ile tamamlanması sonucu geciktiriyordu.
- Her renderer hatası kalıcı haritasız modu açıyordu; bu davranış ilk açılış dışındaki hatalara da uygulanıyordu.

## Değişiklikler

- HOME doğrudan singleTask MainActivity; simge alias'ları aynı ana Activity'yi öne getirir.
- Yüzen buton arka plan durumunu onPause yerine onStop üzerinden izler; tercih değişikliği hemen uygulanır.
- FTS desteği gerçek sorguyla kontrol edilir; destek yoksa mevcut SQL araması kullanılır. Kategori ve düşük FTS sonuçlarında SQL tamamlaması yapılır. Yüklü paketler veri sayımına dahil edilir.
- Yalnız çevrimdışı tercihi aramada dikkate alınır. Toplu adres tamamlama sonuçları engellemez; seçilen yerin mevcut adres tamamlama yolu kullanılır.
- Panel boyutu değişikliği hazır haritayı başlangıç durumuna sıfırlamaz.
- Otomatik haritasız mod yalnız süreçteki ilk harita denemesine aittir. İlk hazır kare veya uygulamanın arka plana gitmesi bu aşamayı kapatır. Daha sonraki hata tercih değiştirmez; hata ve yeniden deneme harita alanında görünür.
- Ekran dışı renderer penceresi odak ve sistem dokunması almaz; giriş zaten IPC üzerinden açıkça aktarılır.

## Emülatör kanıtları

AVD: OsmAndAuto_LowRam_API30_x86, emulator-5554. Wi-Fi ve mobil veri kapalı; uygulama yalnız çevrimdışı modda.

0.4.111 diagnostic APK ile Chrome → Geri / Home / uygulama simgesi testlerinin üçünde de ana süreç 7681 ve renderer 7781 değişmedi, yeni MainActivity oluşmadı ve style yeniden yüklenmedi. Yüzen buton Chrome'da göründü, Vela'da gizlendi. Yerel PMTiles dosyalarından sokaklar/etiketler çizildi. Çevrimdışı Restaurants araması 30 sonuç verdi; adres tamamlama nedeniyle gecikme görüldü.

Bu test tek başına çekmece/müzik boyut değişikliği ve ANR'nin çözüldüğünü kanıtlamaz. Son kaynak APK üzerinde ek doğrulama aşağıya kaydedilecek.

## ANR incelemesi

Sistem kayıtları alındı: 23:05 MainActivity input-dispatch timeout, 23:06 SystemUI/navigation/wallpaper timeout, 23:09 renderer service execution timeout. MainActivity/renderer dump'larında nativePollOnce görüldü; doğrudan tek bir uygulama kilidini kanıtlamıyor. Emülatör yük ortalaması 17.48, GC ve grafik/sistem süreçleri de yoğun. Bu bulgular ANR'yi yok sayma gerekçesi değildir. Test yükünü ayırmak için bu oturumun yerel Gradle derlemesi durduruldu; son APK Actions üzerinden doğrulanacak.

Ham kanıtlar yerelde `.logs/offline-chrome-2026-10-11/` altında; kullanıcı verisi içerebilecek tam loglar Git'e eklenmedi.

## Son açılışta bulunan ek neden

Yayımlanan 0.4.109 üzerinde internet kapalı ilk açılışta `style-ready` → `frame-partial` geldi, yerel PMTiles okumaları sürdü; istemci yalnız `frame` kabul ettiğinden `render timeout: frame-partial` ile haritasız moda geçti. Çevrimdışı/eksik karolarda MapLibre'ın tam çizim bayrağı sürekli false kalabilir. İstemci stil hazırken çizilen ilk kısmi kareyi de kabul edecek şekilde düzeltildi (336021c4). Bu, harita içeriğinin bütünüyle doğru olduğunu iddia etmez; ilk çizimi başarısız başlangıç sanmayı önler.

Workflow son APK testinde derlemeyle aynı sürüm parametrelerini kullanır; sırf sürüm farkı yüzünden yeniden derleme önlenir.

## 0.4.111: yarım harita ve kaynak filtresi düzeltmesi

- Önceki APK'da sorun ekran görüntüsüyle yeniden üretildi: 832×378 harita/pencere/GL tamponuna rağmen SurfaceFlinger destinationClip 344×378 kaldı. Sağ taraf boş, yazılar ve simgeler yatayda sıkışmış görünüyordu. Önceki yalnız lifecycle-log kontrolü bunu yakalayamamıştı.
- Boyut değişiminde VirtualDisplay çıktı yüzeyi önce ayrılıp yeni boyutla tekrar bağlanır. Display, pencere, MapView ve style korunur.
- Android JSONObject adresleri `https:\/\/` olarak serileştirdiği için uzak kaynak filtresi çalışmıyordu. Filtre artık kaynakların çözülmüş url/data/tiles değerlerini denetler; kaçışlı URL regresyon testi eklendi.
- Commit b45864f8; Actions 38127791201 başarılı. Release diagnostic-111 x86 APK mevcut emulator-5554 üzerine veri silmeden kuruldu. İnternet kapalı.
- Yerel test derlemesi Windows native memory allocation/pagefile yetersizliğiyle tamamlanamadı; CI derleme ve testleri başarılı.
- Emülatör fontları: Noto Sans Regular/Italic/Bold Latin ve Latin Extended dosyaları; dört sprite dosyası mevcut. Yeni loglarda removedOnlineSources=[ne2_shaded], glyphs ve sprite file://. Önceki doğal-dünya raster istekleri ağ koruması tarafından engellenmişti; bu katman artık stilde yok.
- İlk iki müzik paneli turunda görüntü ve çıktı alanı düzeldi; üçüncü turda destinationClip yeniden 344 kaldı. Bu nedenle b45864f8 yüzey düzeltmesi yeterli değildir. Aynı traversal içinde detach/attach birleşmesini önlemek için bağlama iki çizim turu sonrasına alındı; bu ek düzeltmenin sonucu aşağıda doğrulanacak.
- Bu doğrulama fiziksel teybin grafik sürücüsünü veya bütün ANR nedenlerini doğrulamaz. Açılıştaki region/world/region seçimi ve ayrı süreç hata sonrası kurtarma ayrıca incelenebilir.

## 0.4.112: tekrarlı doğrulama

- Ek düzeltme 1223d7c2: çıktı yüzeyini yeniden bağlamak için iki Choreographer çizim turu beklenir. Yeni bir OPEN veya SUSPEND önceki bekleyen bağlantıyı geçersiz kılar. Harita/style yeniden oluşturulmaz.
- Actions 38128739070 başarılı; diagnostic-112 x86 APK emulator-5554 üzerine güncelleme olarak kuruldu.
- İnternet kapalıyken on müzik paneli büyütme/küçültme turu (20 boyut değişimi) tamamlandı. Her küçük panelde display/destinationClip 344×378, her büyük panelde 832×378. Test exit code 0; bu turlarda yeni map-create veya style-loading yok.
- 3. ve 10. büyük panel dahil ekran görüntüleri incelendi: boş sağ yarı ve yatay sıkışmış simgeler/yazılar görülmedi. Kanıtlar yerel `.logs/offline-chrome-2026-10-11/verified-resize-*` ve `resize-verified-results.json` dosyalarında.
- Son renderer logları yerel PMTiles/font/sprite kaynaklarını ve removedOnlineSources=[ne2_shaded] bilgisini doğruladı. Yeni renderer oturumunda engellenmiş uzak kaynak isteği görülmedi. Bağlantı durumu Active default network: none.
- Çevrimdışı Restaurants araması ayrıca 30 sonuç verdi. Fiziksel teyp testi yapılmadı; onun sürücü davranışı ve uzun süreli ANR kontrolü ayrı doğrulama gerektirir.
