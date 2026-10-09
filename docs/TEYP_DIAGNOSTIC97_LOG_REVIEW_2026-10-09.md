# Teyp diagnostic-97 log incelemesi — 9 Ekim 2026

İncelenen güncel oturum: `0.4.97-diagnostic (2097)`, alps L9211B, Android API 27, ARMv7. `.logs/vela_app.log` önceki günün kayıtlarını da içeriyor; 8 Ekim PID 7863/8287 kayıtları bu oturumun kapanması olarak değerlendirilmedi. Kaynaklar ve raporlar incelendi; uygulama kodu değiştirilmedi.

## Launcher açık kalmış

9 Ekim 07:12:19–07:21:48 aralığında ana süreç PID 11757. Arka plan/ön plan geçişleri, Activity yeniden oluşmaları ve harita hataları sırasında aynı PID korunmuş. Tanı süreci PID 11794; ana süreç için Binder ölüm bildirimi yok. 07:20:14 ve 07:21:47 kayıtları ayarlardan kullanıcının yaptığı açık kapatma işlemleri. Activity yeniden oluşturulması tek başına süreç çökmesi değildir.

Harita süreci üç kez Java exception ile kapanmış, launcher devam etmiş. Bu oturum süreç izolasyonunun çalıştığını doğruluyor; bütün özelliklerin artık sorunsuz olduğunu veya eski kapanmanın tek nedeninin müzik olduğunu kanıtlamıyor.

## Harita hatasının kesin nedeni bulundu

Denemeler:

| Zaman | Harita PID | Son aşama | Sonuç |
|---|---:|---|---|
| 07:16:32 | 13221 | library | ExceptionInInitializerError |
| 07:21:29 | 15299 | library | Aynı hata |
| 07:21:39 | 15388 | library | Aynı hata |

Üç raporda da neden `MapboxConfigurationException`: SDK, Mapbox.getInstance çağrısı yapılmadan kullanılmış. MapLibre 10.3.7'nin Java sınıfları hâlâ `com.mapbox.mapboxsdk` adını taşıyor; bu ad ayrı bir ücretli SDK kullanıldığı anlamına gelmiyor.

Çağrı zinciri:

`VelaMapView → PmtilesMapBridge.install → HttpRequestUtil.setOkHttpClient → HttpRequestImpl.<clinit> → HttpIdentifier → Mapbox.getApplicationContext → MapboxConfigurationException`

[VelaMapView.kt](/D:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/ui/map/VelaMapView.kt:981) önce PMTiles köprüsünü kuruyor; sonraki satır SDK'yı başlatıyor. [PmtilesMapBridge.kt](/D:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/offline/PmtilesMapBridge.kt:31) SDK'nın HTTP sınıfını bu ilk çağrıda başlatıyor. Harita renderer'ı ayrı süreç olduğundan başka süreçte yapılmış SDK başlatması burada geçerli değil.

Gerekli düzeltme: renderer sürecinde önce `MapLibre.getInstance(context)`, ardından PMTiles HTTP köprüsü, en son MapView oluşturma. Hata yalnız yutulmamalı: Java static sınıf başlatması başarısız olduktan sonra aynı süreçte sınıf kullanımı bozulmuş olabilir.

## Grafik testinin sınırı

EGL display, GLES context, pbuffer, make-current ve temizlik aşamaları tamamlanmış. Gerçek GL sorgusu `OpenGL ES 2.0 58bf738`, üretici ARM, renderer Mali-450 MP döndürmüş. Bu, cihazın GLES2 bağlamını kurabildiğini doğruluyor.

Ancak üç deneme de MapView ve harita stili/tile çizimi başlamadan aynı SDK başlatma hatasında durmuş. Dolayısıyla boş harita çiziminin, dosyasız stilin veya gerçek offline harita dosyasının başarıyla render edildiğini söyleyemeyiz. Bu kayıtlardaki kesin hata bozuk/eksik harita dosyası veya native GLES sürücüsü çökmesi değildir; daha sonraki grafik uyumluluğu henüz sınanamadı.

Kanıt: `crash-1791519392459-map_renderer-1029661420443.txt`, `crash-1791519689771-map_renderer-1326973528768.txt`, `crash-1791519699345-map_renderer-1336547847999.txt`; bunların launcher tarafındaki Map renderer failure raporları ve `logcat-diagnostics.log/.old` kayıtları.

## Müzik taraması çalışmış; oynatma bilinçli kapalı

Tanı sürümü müzik servisini, kaydedilmiş parçanın hazırlanmasını ve dahili kapak çıkarmayı kapatıyor. Ayrıca InternalMusicPlayer içinde oynatma koruması var. Müzik çalmaması bu sürümün beklenen davranışı.

Kütüphane/indeks ve manuel tarama açık:

- Açılışta 3767 önbellek kaydı kontrol edilmiş; mevcut/geçerli 1072 parça, 16 klasör yüklenmiş. Dosya kontrolü 8253 ms sürmüş ve arka plan worker'ında tamamlanmış.
- 07:14:49'da ayrı bir tarama başlatılmış, 07:15:48'de bitmiş: yine 1072 parça. Bu işlem boyunca ana süreç yaşamış. Kayıt sayısında tarama nedeniyle artış görünmüyor; bu sayı tek başına her parçanın benzersizliğini kanıtlamaz.
- Önbellekteki 3767 kaydın 1072'ye düşmesi; erişilmeyen, kayıp veya aynı dosyaya karşılık gelen kayıtların elenmesiyle ilgili olabilir. Özellikle takılı olmayan USB içeriğini listede koruma politikası ayrıca incelenmeli; log toplamları elenen her dosyanın nedenini vermiyor.
- Bu oturumda Piper native model yükleme kaydı yok. Önceki tanı sürümünde müzik izolasyonu zaten vardı; yeni sürümde Piper açılış yükü, UI güncellemeleri ve tanı servisinin süreç önceliği de değişti. Dolayısıyla eski kapanmayı yalnız müzik servisine bağlayamayız.

## İki kısa donma kaydı

07:19:30 raporu, 3016 ms ana thread yanıt gecikmesini ve o sırada `MusicLibraryController.sortedTracks → String.toLowerCase → TimSort` yığınını gösteriyor. [MusicLibraryController.kt](/D:/Projects/CarWorkspace/Vela/app/src/car/java/app/vela/carlauncher/ui/MusicLibraryController.kt:146) sıralama karşılaştırmalarında tekrar tekrar lowercase üretiyor. Birden fazla akış değişimi `render()` çağırıyor; kuyruk akışından gelen güncelleme bile Parçalar sekmesindeki bütün listeyi tekrar sıralayabiliyor. Sıralama ana thread üzerinde.

07:21:12 raporu, 3002 ms yanıt gecikmesini ve o sırada `CarLayoutManager.applyLayout → ConstraintSet.clone` yığınını gösteriyor. Bu tek örnek bütün üç saniyenin yalnız clone işleminde harcandığını kanıtlamaz; yeniden yerleşim yolu incelenmeli.

İki kayıtta Java kullanımı yaklaşık 23–25 MiB / 96 MiB, native heap yaklaşık 8–9 MiB, sistem kullanılabilir belleği yaklaşık 835–872 MiB ve lowMemory=false. Bu örneklerde OOM kanıtı yok. Bu dosyalar ölüm/exception raporu değil, kısa donma gözlemleri.

## Sonraki uygulama sırası

1. SDK/PMTiles başlatma sırasını düzelt; boş motor → dosyasız stil → gerçek harita dosyası testlerini tekrar aç. Müzik izolasyonu ilk harita doğrulaması için korunabilir.
2. Kütüphane sıralamasını değişen veriye göre önbellekle ve ağır hesaplamayı UI thread dışına taşı. Kullanıcının Sıra listesinin düzenini değiştirme. Yerleşim güncellemelerinin gereksiz tekrarını incele.
3. Ayrı tanı servisini koruyarak müzik oynatmayı geri aç; kapak çıkarma ve Piper açılış yükünü aynı anda geri açma. Böylece oynatma/parça hazırlama etkisi ayrı ölçülür.
4. Gerçek harita dosyası yüklenince tile/stil/render aşamasını ayrıca doğrula. Mevcut kayıtla tam harita uyumluluğu onaylanamaz.

Logların değişmeden yedeği: `D:/Projects/CarWorkspace/Vela-backups/2026-10-09-teyp-diagnostic97/.logs/`.
