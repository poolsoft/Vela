# CoMaps Auto V2 – Vela açılış karşılaştırması

8 Ekim 2026. Referans: `D:/Projects/CarWorkspace/CoMaps_Auto_V2`, commit `29d9964f9`. Vela: `codex/maplibre-10.3.7`, commit `b442dde9`. Teyp logu: `0.4.95-diagnostic (2095)`.

Bu karşılaştırma yerel kaynak koduna dayanır. CoMaps'in teypte kararlı çalıştığı kullanıcı gözlemidir; bu turda CoMaps yeniden derlenmedi veya teypte çalıştırılmadı. Vela'daki kapanmanın kesin nedeni henüz yakalanmadı. Aşağıdaki farklar gözlenen açılış yükünü açıklayabilecek somut kod farklarıdır, kanıtlanmış kill nedenleri değildir.

## Sonuç

Müzik mimarisinin temeli yakın: merkezi MusicManager, MediaBrowserService, dahili MediaPlayer, OEM/BT adaptörleri, kayıtlı indeks ve asenkron hazırlama. Ancak aktarılan panelin çalışma biçimi aynı değil. Vela'nın telemetri akışı müzik değişmese de paneli her saniye yeniden güncelliyor; saati de ayrı olarak her saniye yazıyor. CoMaps paneli olay/dinleyici ve Activity/Fragment yaşam döngüsüyle çalışıyor, saati 30 saniyede bir güncelliyor.

CoMaps ayrıca düşük RAM'li cihazlar için dock/panel açılışını aşamalıyor. Vela'da düşük RAM algısı var fakat XML launcher host'u bunu başlangıç işlerini ayırmak için kullanmıyor. CoMaps'in kullandığı sistem TTS'sine karşılık Vela açılışta kendi sürecine Piper/Sherpa modelini yüklüyor. Bu yükleme müzik servisinin kapatılmasıyla durmuyor.

## Karşılaştırma tablosu

| Alan | CoMaps Auto V2 | Vela | Son log açısından anlamı |
|---|---|---|---|
| İlk arayüz | Önce hazır launcher/map kabuğu, sonra dock ve görünür panel | Compose ağacı içinden XML dock/panel aynı host güncellemesinde oluşturuluyor | Aynı anda başlayan işler farklı |
| Düşük RAM başlangıcı | LOW_RAM profilinde dock 500 ms, panel 1500 ms gecikmeyle kuyruğa alınıyor | MemoryPressure lowRam=true algılanıyor; host'ta aynı aşamalama yok | Teyp için yazılmış davranış tam aktarılmamış |
| Saat | 30 saniyede bir; onPause durdurur, onResume başlatır | 1 saniyede bir; releasePanel çağrılana kadar sürer; aynı metin tekrar atanır | Gereksiz ölçüm/çizim işi |
| Müzik paneli güncellemesi | onTrackChanged/onPlaybackStateChanged ve panel dinleyicileri | Telemetri saniyelik değişimi AndroidView update'e ulaşıyor; updateMedia tekrar başlık/sanatçı/play ikonunu yazıyor | Müzik kapalı olsa da müzik görünümü güncelleniyor |
| Panel arka plan davranışı | onPause saat, müzik ve visualizer dinleyicileri kaldırılır | collectAsState kullanılıyor; unified panel release yalnız içerik değişimi/host bırakılmasıyla | Arka plana geçiş aynı şekilde işlenmiyor |
| İndeks okuma | Tek IO executor, JSON okuma, dosya erişilebilirlik kontrolü, hazır dinleyicisini main thread'e gönderme | IO coroutine + mutex, JSON/dosya kontrolü/canonicalPath, StateFlow yayını | İkisi de IO'da; Vela bunu ana thread'de yapıyor denemez |
| Otomatik tarama | Ayara ve son tarama zamanına bağlı; startup refresh isteği geciktiriliyor | Kullanıcı talebiyle tamamen kapalı; yalnız Tara/yenile tarar | Kullanıcının istediği bilinçli fark, geri açılmamalı |
| Çalma durumunu geri getirme | İndeks hazır callback'i → restoreState; autoPlay/önceden çalıyorsa resume | StateFlow hazır → restoreSavedPlayback; ayrıca Smart Focus BT bekleme süresi | Diagnostic 95'te Vela restore hazırlamasını atlıyor |
| MediaPlayer | Constructor içinde oluşturuluyor; parça prepareAsync ile hazırlanıyor | Gerektiğinde oluşturuluyor; prepareAsync | Vela'nın mevcut diagnostic sürümünde parça hazırlaması yapılmıyor |
| Kapak | Kaydedilen albumArtUri üzerinden çözümleme | IO'da MediaMetadataRetriever + en fazla 512 px örneklenmiş decode/cache | Diagnostic 95'te dahili kapak yolu kapalı; mevcut logda nedeni diye etiketlenemez |
| Medya servisi | Activity onStart'ta startService; notification/session medya durumuna bağlı | Normal sürümde startForegroundService ve erken foreground bildirimi | Diagnostic 95'te servis başlamıyor; begin/complete etiketleri üst çağrıya ait |
| Ses motoru | Android TextToSpeech | Piper/Sherpa JNI modeli Vela sürecinde | En önemli bağımsız native fark |
| Home/task | BootstrapActivity ve CarLauncherActivity, singleTask | MainActivity ve HOME/LAUNCHER alias'ları, singleTask | Aynı Home ilkesi; Activity mimarisi bire bir aynı değil |
| Harita | CoMaps native motoru, teyp tespitiyle kendi grafik ayarları/2D önlemi | MapLibre ayrı map_renderer süreci, elle açılış kapısı | CoMaps settings.ini anahtarları MapLibre'ye doğrudan aktarılamaz |

## Somut kaynak zincirleri

### 1. Her saniye müzik görünümüne giden telemetri

- [CarTelemetryManager.kt](/D:/Projects/CarWorkspace/Vela/app/src/car/java/app/vela/carlauncher/telemetry/CarTelemetryManager.kt:49): sürüş süresini 1000 ms aralıkla değiştiriyor.
- [CarLauncherLayout.kt](/D:/Projects/CarWorkspace/Vela/app/src/car/java/app/vela/carlauncher/ui/CarLauncherLayout.kt:76): telemetryState collectAsState ile okunuyor ve host'a veriliyor.
- [CarLauncherHostView.kt](/D:/Projects/CarWorkspace/Vela/app/src/car/java/app/vela/carlauncher/ui/CarLauncherHostView.kt:222): unifiedHost.updateMedia(medya) her AndroidView update çağrısında uygulanıyor.
- [CarUnifiedPanelHost.kt](/D:/Projects/CarWorkspace/Vela/app/src/car/java/app/vela/carlauncher/ui/CarUnifiedPanelHost.kt:168): aynı başlık/sanatçı/ikon tekrar atanıyor. Clock ayrıca 1000 ms'de text atıyor; layout değişimi içinde resizeContents çalışıyor.
- [CoMaps UnifiedPanelFragment.java](/D:/Projects/CarWorkspace/CoMaps_Auto_V2/android/app/src/carlauncher/java/app/organicmaps/carlauncher/ui/UnifiedPanelFragment.java:78): clock 30_000 ms. onResume/onPause saat ve medya/visualizer dinleyicilerini yönetiyor.

Dock kısayol adaptörü değişmeyen listede zaten erken dönüyor; onu sürekli listeyi yeniden kuruyormuş gibi değerlendirmemeliyiz. Dock müzik metni/ikon güncellemesi ise ayrıca koşulsuz çağrılıyor.

### 2. Düşük RAM açılışının aşamalanması

- [CoMaps CarLauncherActivity.java](/D:/Projects/CarWorkspace/CoMaps_Auto_V2/android/app/src/carlauncher/java/app/organicmaps/carlauncher/CarLauncherActivity.java:368): scheduleLauncherContent; düşük RAM'de 500/1500 ms, normal cihazda panel 120 ms.
- [LauncherStartupProfile.java](/D:/Projects/CarWorkspace/CoMaps_Auto_V2/android/app/src/carlauncher/java/app/organicmaps/carlauncher/performance/LauncherStartupProfile.java:40): isLowRamDevice, <=3 GiB toplam RAM, düşük kullanılabilir bellek kontrolü.
- Vela MemoryPressure teybi lowRam=true algılıyor; CarLauncherHostView factory/update bu bilgiyle dock/panel işlerini ayırmıyor.

### 3. Piper açılış çelişkisi

- [MapViewModel.kt](/D:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/ui/map/MapViewModel.kt:603): kayıtlı ses motoruyla voice.init(savedEngine).
- [VoiceGuide.kt](/D:/Projects/CarWorkspace/Vela/core/src/main/java/app/vela/core/voice/VoiceGuide.kt:254): neural motor seçilmişse warmUp çağrısı.
- [PiperSynth.kt](/D:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/voice/PiperSynth.kt:95): IO worker'da OfflineTts JNI modeli yükleniyor. MapViewModel içindeki "ilk rota öncesi yüklenir, açılışta yüklenmez" açıklaması mevcut çağrı zinciriyle çelişiyor.
- [CoMaps TtsPlayer.java](/D:/Projects/CarWorkspace/CoMaps_Auto_V2/android/sdk/src/main/java/app/organicmaps/sdk/sound/TtsPlayer.java:210): sistem TextToSpeech oluşturuluyor; bu yolda Vela'nın Piper modelini yükleyen JNI çağrısı yok.

## Son teyp loguyla ilişki

- 0.4.95 diagnostic sürümü doğru; logcat kaydı çalışıyor.
- Müzik servisi/restore/kapak izolasyonu var; MusicManager/OEM adaptörleri, indeks ve müzik görünümü bütünüyle kapalı değil.
- İki süreçte de Piper .onnx yükleme config'i görülüyor; başarı veya yakalanan model-load-failed kaydı yok. Tamamlanma durumu bilinmiyor.
- Ana ekran ölçümleri yaklaşık 1,8 ve 3,5 saniye; 390/397/303 atlanan kare kayıtları var. Tekrarlı görünüm güncellemeleri ve eşzamanlı model yüklemesi bu yük için adaydır; birinin kesin kapanma nedeni olduğu kanıtlanmadı.
- Java fatal exception/native fatal signal/sistem ANR kill kaydı yok. Harita oluşturma kaydı da yok; Android OpenGLRenderer/HWUI kaydı MapLibre'nin açıldığı anlamına gelmez.
- Music cache load bitişi görülmüyor. İndeks işlemi için süre/kaç dosyada kaldığı tanısı gerekli; arka plan IO işlemini ana thread ANR nedeni diye varsaymamalıyız.

## Uygulama sırası

Çökme deneylerini birbirinden ayrı sürümlerde yap; tek APK'da hem indeks hem Piper'ı kapatıp hangi farkın etkili olduğunu kaybetme.

1. Kullanıcının müzik şüphesini ayırmak için diagnostic sürümde yalnız kayıtlı indeksin açılış yükünü atla; dosyayı silme, Tara ile yükleme çalışsın. Piper ve panel davranışı bu deneyde aynı kalsın.
2. Kapanma sürerse ikinci deneyde indeks yükünü geri getir, yalnız Piper native yüklemesini kapat; sistem TTS veya sessiz geri dönüş kullanılabilir. Bunun normal sürümdeki kalıcı karşılığı ilk açılış warmUp'ını kaldırıp ses gerektiğinde yüklemektir.
3. CoMaps'ten aktarılacak somut UI düzeltmeleri: medya değişmediyse metin/ikon yazma; dakika saati değişmediyse text yazma; panel saatini/dinleyicilerini görünür yaşam döngüsüne bağla. Sürüş süresi güncellemesi dashboard'u güncellesin; müzik panelini yeniden yazmasın.
4. Mevcut düşük RAM algısını kullanarak kabuk, dock ve paneli aşamalı kur. Yeni bağımlılık veya ayrı bir profil altyapısı şart değil.
5. Diagnostic heartbeat için kısa (2–3 saniye) takılmaların sınırlı stack örneklerini kaydet; bu raporları kesin sistem ANR diye adlandırma. İndeks/Piper işlemlerinin başlangıç, ilerleme ve bitişini ayrı etiketle.
6. Normal müzik servisinin foreground/session/bildirim davranışını bundan sonra değerlendirme; kapalı servis mevcut diagnostic kapanmanın başlangıcı olamaz, OEM adaptörleri ayrıca ayrılabilir.



## 8 Ekim uygulaması

Açılıştaki Piper warmUp kaldırıldı; rota/ses seçimi ve konuşma yüklemesi korunuyor. İndeks yükü bu sürümde kapatılmadı; süre ve ilerleme kaydı eklendi. Medya/saat güncellemeleri değişen değerlerle sınırlandı, panel saati/görselleştirici yaşam döngüsüne bağlandı. Düşük RAM cihazlarda dock ve panel aşamalı kuruluyor. Ayrı süreç tanı servisi, kısa donma kayıtları ve tamamlanmış raporu atomik yayımlama eklendi. Ayrıntılar INDEPENDENT_DIAGNOSTIC_LOGGER.md içindedir. Teypteki kapanmanın kesin nedeni henüz kanıtlanmadı.
