# Açılışta haritanın üç kez yüklenmesi — 16 Ekim 2026

0.4.127 soğuk açılışında aynı renderer PID/session içinde MapStyle kayıtları, basemap kaynağının tr-guney → world → tr-guney değiştiğini gösteriyor. Bu gerçek style yeniden yüklemesidir; sadece tanı yazısının değişmesi değildir. Örnek renderer 21683: ilk stil 07:36:55, world 07:36:58, bölgesel stile dönüş 07:37:02 (cihaz log saati).

VelaMapView, ilk kamerayı MapLibre varsayılanından konuma animasyonla götürüyor; idle/viewport dinleyicileri bundan önce kaydediliyor. Ara kamera alanları MapViewModel.onViewport üzerinden arşiv seçimini etkileyebiliyor. 500 ms debounce ve iptal kontrolü bu açılış sırasını tek başına çözmemişti.

Düzeltme: getMapAsync tamamlandığında güncel cameraTarget/myLocation, rememberUpdatedState üzerinden alınır. Kamera viewport/idle dinleyicileri eklenmeden önce bu hedef ve zoom'a doğrudan taşınır. Son hedef de işaretlenerek aynı açılış hedefi için genel fly-to kolunun yeniden çalışması önlenir. Rota/arama/normal takip davranışları ve bölge dışına gerçek kullanıcı kaydırmasındaki arşiv seçimi korunur. Konum henüz yoksa bir hedef uydurulmaz; sonradan gerçek konum gelince gerekli kaynak değişimi normaldir.

Yeni bir süreç/servis, sabit bekleme süresi veya arşivi sonsuza kadar kilitleme eklenmedi. İlk kameranın dinleyicilerden önce yerleştirildiği MapCamera kaydı eklendi.

Doğrulama: Kotlin derlemesi başlatıldı; son APK ile aynı yedek ve internet kapalı durumda soğuk açılışta style kaynaklarının değişip değişmediği kontrol edilecek. Sistem çubuğu düzeltmesi e5cc4aef içinde ayrıca tamamlandı ve üç görünürlük durumu emülatörde kontrol edildi.

## Tamamlanan doğrulama

ffc018ae için yerel Kotlin derlemesi başarılı. GitHub Actions 38868946732 başarılı; diagnostic-128 x86 APK, emülatörde mevcut veriler korunarak kuruldu. İnternet kapalı iki ayrı soğuk açılışta da yalnızca bir MapStyle Reload kaydı oluştu ve tr-guney.pmtiles kullanıldı; world arşivine geçiş olmadı. Her ikisinde kamera dinleyicilerden önce konuma yerleşti ve tam harita karesi geldi. Tam kareden sonra ayrıca 12 saniye izlenerek gecikmiş kaynak değişimi kontrol edildi.

Kanıtlar: .logs/map-camera-bootstrap-test/cold-1-result.json, cold-2-result.json, ilgili renderer/app logları ve ekran görüntüleri. Bu sonuç emülatörde açılış kaynak değişimini doğrular; fiziksel teypte hız ve kararlılık ayrıca denenmelidir. Konumun sonradan değişmesi veya gerçekten başka bölgeye geçilmesi gerektiğinde kaynak değişimi hâlâ mümkündür.
