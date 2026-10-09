# Diagnostic 98: teyp ve sesli komut incelemesi

İncelenenler: `.logs/` kökündeki 9 Ekim kayıtları, 0.4.98-diagnostic (2098), ana PID 23773; eski alt dizinler bu sonucun kaynağı değildir.

## Doğrulanan sonuçlar

- `diagnostic-tests.json`: dokuz test başarılı. Tarama 1243 parça bulmuş; seçilen MP3 hazırlanmış ve oynatma konumu ilerlemiş. Bu dosyada kapak yok; kapaklı dosyanın görüntü çözümlemesi bu denemede doğrulanmış sayılmaz.
- Medya servisi, MediaSession ve bildirim 5 saniye çalışmış. Bu, normal müzik akışındaki bütün özelliklerin veya uzun kullanımın doğrulandığı anlamına gelmez.
- Boş harita, seçili stil ve normal harita testleri ilk kareye ulaşmış. Normal launcher ekranına dönüldükten sonra da 18:26:26 ve 18:27:23'te kare kaydı var.
- Bağımsız tanı süreci PID 23810, 18:27:37.918'de ana PID 23773 için Binder ölümünü kaydetmiş. Ana süreç kaybı kesin; sebebi henüz kesin değil.

## Son hata kaydı neden okunamıyor?

`crash-1791559659943-diagnostics-2924303624939.txt` 3239 bayt; **3239 baytın tamamı 0x00**. İçinden bir exception veya yığın izi çıkarılamıyor.

`logcat-diagnostics.log` 665408 bayt ve 132053 adet 0x00 içeriyor. Yer yer zaman damgalı kayıtlar yarıda kesiliyor; araya ikili veri, OsmAnd native kütüphane dizeleri ve Java kaynak metni giriyor. Son okunabilir güncel zaman damgalı bölüm yaklaşık 18:26:03'te kalmış. Başındaki 07:16/07:21 exception kayıtları eski renderer denemelerine ait; son 18:27 kapanmasının nedeni olarak kullanılamaz.

Kayıtların cihazda mı, dosya aktarımında mı, yoksa aradaki depolamada mı bozulduğu bu kopyadan anlaşılamıyor. Logcat yazıcısı logcat'in metin satırlarını UTF-8 olarak yazıyor; CrashCatcher metni geçici dosyaya yazıp fd.sync ve rename yapıyor. Dolayısıyla yalnız bu kopyaya bakarak logger'ın boş exception ürettiği veya cihazın depolamasının arızalı olduğu sonucuna varılamaz. Dahili crash kopyası da mevcut; sonraki sürümde onu mevcut dışa aktarma akışından yeniden yayımlamak karşılaştırma olanağı sağlar.

## Sesli komut ve TTS ayrımı

`MapScreen.startLocalVoice` → `MapViewModel.voiceListen` → `AsrRecognizer.listen` yolu, başlangıçta TTS ile karşılama okutmaz. Yerel tanıma sırası:

1. Native konuşma modelini yükle (sherpa-onnx OfflineRecognizer).
2. Native Silero VAD oluştur.
3. AudioRecord ve isteğe bağlı AEC/NS ses efektlerini oluştur.
4. Devam eden TTS'yi durdur; ses odağı al ve mikrofon kaydını başlat.
5. Sesi çözümle; çıkan metni komut işlemeye gönder.

Sistem sağlayıcısı seçiliyse bunun yerine harici RECOGNIZE_SPEECH etkinliği açılır. Loglar hangi yolun seçildiğini göstermiyor.

Bu ASR aşamalarında kalıcı `checkpointAndFlush` işaretleri bulunmuyor; başarısızlıkların bir kısmı yalnız Android Log'a yazılıyor. Piper yüklemesinde kalıcı işaretler var ancak bu oturumun uygulama logunda Piper yükleme kaydı yok. **TTS veya ASR kesin suçlu denemez.** Kullanıcının mikrofon denemesiyle kapanma gözlemi ses yolunu öncelikli aday yapıyor. Native sinyal/abort Kotlin try-catch ile yakalanamaz.

## Ayrı performans bulgusu

18:26:43'teki `crash-1791559603621-2867981860397.txt` gerçek bir Java exception değil, 3017 ms ana iş parçacığı takılması raporu. Yığın `MusicLibraryController.sortedTracks` içinde başlıkları tekrar tekrar küçük harfe çevirip sıralıyor; çağrı `render/refresh` üzerinden geliyor. O andaki Java heap yaklaşık 28 MB / 96 MB, `lowMemory=false`. Bu kayıt OOM kanıtı değildir; kapanmadan yaklaşık 54 saniye önceki ayrı bir UI gecikmesidir.

Testler arası renderer süreçleri bilerek bırakılıyor. `Previous process ended` başlıklı renderer raporları tek başlarına harita çökmesi kanıtı değildir.

## Önerilen sonraki uygulama sırası

1. Mikrofon girişinden önce seçilen yolu, ASR model yükleme/geri dönüş, VAD, AudioRecord, efektler, startRecording, decode ve temizleme aşamalarını kalıcı kayda geçir. Konuşma metnini kaydetmek gerekmiyor; aşama, motor, PID ve sonuç yeterli.
2. Tanı ekranına ayrı mikrofon, ASR model, VAD, çözümleme ve TTS testleri ekle. Önce yalnız mikrofon; sonra yerel tanıma; son olarak sistem TTS ve Piper ayrı ayrı çalışsın. Mevcut dokuz test sesli komut/TTS'yi kapsamıyor.
3. Dahili raporu dışa yeniden kopyalayıp uzunluk ve SHA-256 bilgisiyle kaydet; bozuk dış dosyayı sessizce sağlam sayma. Böylece aktarım ile üretim sorunu ayrıştırılabilir.
4. Müzik listesi sıralamasını veri/sıralama tercihi değişince bir kez hesapla; ana iş parçacığındaki tekrarlı sıralamayı kaldır.
5. Son işaret native ASR/TTS içinde kalırsa ilgili motoru haritadaki gibi ayrı süreçte çalıştır. Ana launcher'ın motor ölümü karşısında açık kalmasını sağla. Buna ihtiyaç duyulup duyulmadığını aşamalı ses testleri belirlesin.

Bu incelemede uygulama kodu değiştirilmedi; yeni APK oluşturulmadı. Gerçek kapanmanın yığın izi bu dosyalarda korunmadığı için kök neden çözülmüş sayılmıyor.
