# Teyp log incelemesi — 16 Ekim 2026

## Kapsam

`.logs/vela_app.log`, `vela_map_renderer.log`, `vela_diagnostics.log`, `logcat-diagnostics.log` ve `crash-179212*.txt` incelendi. Yeni oturumlar alps L9211B, gerçek Android API 27 / Android 8.1, armeabi-v7a, GLES 2.0 ve **0.4.123-diagnostic (2223)**. Bunlar emülatördeki 0.4.124'ün kaydı değil.

## Kesin bulgular

1. **Arayüz ciddi biçimde takılıyor.** Dört watchdog raporu 3654, 3560, 3746 ve 4575 ms ana iş parçacığı yanıtsızlığı gösteriyor. Sistemce onaylanmış ANR raporu değil; kullanıcının ekrana tıklayamama gözlemiyle uyumlu. Stack örnekleri harita Compose kurulumu, SettingsHub metin bileşimi ve çekmece GridLayoutManager/TextView ölçümünde. Bunlar örnekleme anlarıdır, tek bir fonksiyonun bütün süreyi tükettiğini kanıtlamaz.
2. **Ana süreç son bölüm boyunca aynı.** 07:19–07:23 PID 27401 korunmuş. 07:22:04 ve 07:22:32–34'te sık HOME intent/pause/resume var. Kendi kendini başlatan döngü olduğu kanıtlanmadı. Kaynakta yüzen buton CATEGORY_LAUNCHER gönderirken kayıtlardaki intent CATEGORY_HOME; göndereni mevcut log söylemiyor.
3. **PMTiles kilit beklemesi uzun.** `dvm_lock_sample`, PmtilesMapBridge.kt:141 dizin kilidinde 9050 ms ve ayrıca 2469/2670/1040 ms beklemeler gösteriyor. Kilit altında disk okuma, açma ve dizin çözme yapılıyor. Bu doğrudan ana UI thread kilidi değil; haritanın geç çizilmesine ve ortak CPU yüküne katkı verebilir.
4. **Açılışta gereksiz olabilecek kaynak değişimleri var.** Her iki harita oturumunda `tr-guney.pmtiles → world.pmtiles → tr-guney.pmtiles`; toplam her oturumda ilk yükleme dahil üç stil yüklemesi. Bunun başlangıç konumu/kamera-idle/paket seçimiyle ilişkisi kod üzerinden ayrıca düzeltilmeli.
5. **Panel geçişleri aynı renderer'ı koruyor.** 07:22–07:23 map PID 27588 ve session a06744b0… korunmuş; 401x406 ↔ 749x406, retainingReady=true. Bu bölümde tekrar map-create veya style-loading yok. Display/frame kayıtları haritayı sıfırdan açmak değil, yeniden boyutlandırma.
6. **Yerel harita kaynakları kullanılmış.** Stil asset, glyph ve sprite file://. Logdaki `https://vela-pmtiles.invalid` yerel arşivi okuyan özel HTTP interceptor adresi; internet erişiminin kanıtı değil. İlgili oturumda glyphs ve sprites yerel dosyalara yönlendirilmiş.
7. **Çekmece önbelleği çalışıyor.** Güncel logcat bölümünde tek `apps scan start generation=0` var (07:19:06). Çekmecede yine pahalı layout var; çözüm yeniden liste taramak değil, view/ölçüm maliyetini azaltmak.
8. **Tema hatası var.** InterlockingClockView dört kez Theme.AppCompat gerektiği hatasını yazıyor. Hostlar Activity/Compose context'inden doğrudan XML şişiriyor; ortak temalı context ile ele alınmalı. Tek başına donmanın kanıtlanmış nedeni değil.

## Çökme kayıtlarının anlamı

- 16 Ekim oturumunda görülebilen yeni FATAL EXCEPTION/native fatal signal/OutOfMemoryError yok. TTS/ONNX/PiperIPC/voice_tts etkinliğini gösteren kayıt da yok; bu, TTS testinin başarılı olduğu anlamına gelmez.
- `nativeVoiceEnabled=false` artık yalnız ASR/Hey Vela güvenlik bayrağıdır; ayrı TTS bayrağının kapalı olduğunu kanıtlamaz. Tanı başlığına nativeTtsEnabled ve seçilen motor eklenmeli.
- Önceki oturumdaki launcher/map/diagnostics süreçleri yeniden başlamış; Android 8 uygulamaya sistem çıkış nedenini vermediği için nedeni bilinmiyor. Dosya adının crash olması tek başına exception demek değil.
- Crash buffer içinde 15 Ekim 07:10 ve saati sıfırlanmış önceki bir oturuma ait gerçek `startForegroundService() did not then call Service.startForeground()` var. Güncel 16 Ekim çökmesi olarak sunulmamalı; hangi servisin olduğu o stack'te belirtilmemiş. Güncel müzik servisi foreground-ready kaydı üretiyor, fakat bütün foreground servis başlangıç yolları ayrıca denetlenmeli.
- Java heap yaklaşık 30–40 MB / 96 MB, lowMemory=false örnekleri var. OOM kanıtı yok. GC'nin toplam 4 saniye sürmesi, ana thread'in 4 saniye durması anlamına gelmez; ilgili kayıttaki pause yalnız 2,3 ms. Yine de yoğun tahsis ve GC CPU yükünü artırıyor.

## Öncelikli düzeltme sırası

1. UI hostlarının tekrar oluşturulmasını ve gereksiz requestLayout/ölçüm dalgalarını azalt; HOME işlemini idempotent yap. HOME kaynağını ayırt eden kayıt ekle; normal HOME davranışını körlemesine engelleme.
2. PMTiles dizin cache-miss işlemlerini tek global kilit altında disk/decode tutmadan yap; eşzamanlı istekler için aynı dizinin tekrar çözülmesini önle. Varint decode/tahsis maliyetini ölç.
3. Açılışta bilinen bölgesel arşivi, geçici merkez/world seçimiyle değiştirmeyi önle; geçerli konum/kamera durumu sonrası tek karar ver.
4. XML hostlarında AppCompat tema bağlamını düzelt; TTS tanı başlığını güncelle; foreground-service başlangıçlarını denetle.

Bu tur incelemedir; kaynak davranışı değiştirilmedi. Teypteki tıklayamama sorunu çözülmüş sayılmıyor.

## Regresyon karşılaştırması — kullanıcının başlangıç zamanı düzeltmesi

İlk öncelik listesi, logdaki eski darboğazlarla yeni regresyonu yeterince ayırmıyordu. GitHub sürüm hedefleri doğrulandı: 0.4.116 = `038655d0`, logdaki 0.4.123 = `e47cd70c`. Aşağıdaki değerlendirme bu aralığın kaynak farkına dayanır; dünkü değişikliklerin nedenselliği cihazda A/B testiyle henüz kanıtlanmış değildir.

### Yeni ve doğrudan görülen gereksiz iş

`e47cd70c / CarAppDrawerHost.uygulamalariYukle`: önbellek varsa önce `apps.value` çiziliyor; hemen ardından StateFlow collector aynı mevcut değeri yeniden alıp `filtrele → adapter.guncelle → notifyDataSetChanged` çalıştırıyor. Dolu önbellekle her açılışta aynı grid iki kez bağlanıyor, simge işleri yeniden başlatılıp önceki işler iptal ediliyor. Eski hostta bu çift ilk yayın yoktu. Güncel watchdog örneğinin GridLayoutManager/TextView ölçümünde olmasıyla uyumlu; sonsuz döngü kanıtı değil, fakat somut yeni regresyon ve ilk düzeltilmesi gereken gereksiz iş.

### Yeni döngü riski

`42aa8c43`, `8e467821 / CarAppDockHost`: her SCROLL_STATE_IDLE olayında yeni smoothScrollBy başlatan listener eklendi. İstenen offset kaydırma sınırında tüketilemezse, tamamlanan animasyon aynı offset ile yeniden başlatılabilir. Kenar kontrolü, tekrar giriş koruması veya ilerleme yoksa durdurma koşulu yok. Bu yeni bir döngü riski; alınan stack örneklerinde ViewFlinger/snap callback bulunmadığından teypte gerçekleştiği doğrulanmadı. Eski sürümde bu listener yoktu.

### Yeni ölçüm yolu

`e47cd70c / CarMusicPlayerHost`: kontrol alanının layout olayında ConstraintSet uygulayan ikinci ölçüm dinleyicisi eklendi. lastPlaybackWidth aynı genişlikte tekrar uygulamayı engelliyor; dolayısıyla tek başına kesin sonsuz döngü denemez. Panel boyutu değişirken yeni ölçüm dalgaları bakımından eski tek dinleyicili yolla karşılaştırılmalı.

### Yeni başlangıcı açıklamayan / doğrulanmayanlar

- PMTiles kilit kodunun son ilgili değişikliği 11 Ekim `9248945f`; bu iki sürüm arasında değişmedi. Gerçek bir yavaşlık, fakat dünkü yeni kilitlenmenin nedeni olarak tek başına sunulmamalı.
- MainActivity HOME işleme yolu ve CarLauncherLayout bu aralıkta değişmedi. Yeni bir kendi kendine HOME gönderme yolu bulunmadı. Logdaki HOME tekrarlarının göndericisi bilinmiyor.
- 0.4.124 ok boyutu değişikliği logdaki 0.4.123'te yok; bu olayın nedeni olamaz.
- Yeni TTS servisinin çalıştığına dair bu oturumlarda kayıt yok; onu neden olarak gösterecek kanıt bulunmadı.

### Düzeltilmiş çalışma sırası

Önce eski PMTiles yoluna müdahale etmek yerine yeni çekmece çift bağlamasını kaldırmak, dock snap işlemini tek seferlik/kenarda duran hâle getirmek ve player ölçümünü kontrollü karşılaştırmak gerekir. UI regresyonu ayrıştırıldıktan sonra eski PMTiles performans sorunu ele alınır. Gerekirse yalnız bu yeni UI davranışlarını kapatan karşılaştırma APK'sı hazırlanır; bütün dünkü özellikleri veya kullanıcı verilerini geri almak gerekmez.

## Uygulanan düzeltmeler

- Çekmece artık StateFlow değerini tek collector üzerinden, gecikmeden uygular. Önbellekteki aynı listenin ilk açılışta iki kez bağlanması kaldırıldı; paket değişimindeki yeni simgeler yenilenmeye devam eder.
- Dikey dock yalnız kullanıcı sürüklemesi/fling tamamlandığında bir hizalama yapar. Kendi hizalama animasyonu ikinci hizalama başlatamaz; kaydırılamayan kenarda veya 1 piksel toleransta durur. Aynı boyuttaki kısayola tekrar layoutParams atanmaz.
- Player kontrollerinde genişlik değil sonuç düğme boyutu karşılaştırılır; tüm ConstraintSet yeniden uygulanmaz. Boyut değiştiğinde ilgili düğmeler güncellenir; önceki/sonraki simge düzeltmesi korunur.
- Dock, çekmece, birleşik panel ve büyük player XML'leri AppCompat tema bağlamıyla oluşturulur.
- PMTiles dizin okumaları/decode ortak LRU kilidi dışına alındı. Aynı dizine eşzamanlı istekler tek FutureTask paylaşır; farklı dizinler birbirini beklemez. Başarısız iş cache'e yazılmaz ve sonraki istek tekrar deneyebilir. Tamamlanan işler bekleyenler tablosundan çıkarılır; LRU sınırı 8 olarak korunur.
- Mevcut harita varken geçici kamera merkezleri 500 ms boyunca birleştirilir. İptal edilmiş seçimin bloklayan dosya kontrolünden sonra eski arşivi uygulaması engellenir. İlk harita yüklemesi bu gecikmeyi beklemez. Bu, başlangıçtaki region/world geçişlerinin her olasılıkta bittiğine dair cihaz kanıtı değildir.
- İndirme biterken foreground servis başlatma yarışı düzeltildi: boş kalan başlangıç da önce bildirim yükümlülüğünü yerine getirir, sonra kapanır. Eski logdaki servis hatasının bu servisten geldiği doğrulanmış değildir. Medya/tanı servisleri zaten başlangıçta foreground olur; navigasyon servisi normal başlangıcında foreground olur.
- Logcat tanı başlığı artık native ASR ve native TTS bayraklarını ayrı gösterir.

### Dock neden güncellenmeli?

Yön, kullanılabilir alan, dock ayarı veya kısayol listesi değiştiğinde yerleşim güncellenmelidir. Sabit ekranda sürekli yeniden oluşturmak gerekmez. Buradaki düzeltme çizimi kapatmaz; aynı ölçüleri tekrar uygulamayı ve hizalama animasyonunun kendini tekrar başlatmasını engeller. Saatin dakika değişimindeki ve aktif dinleme animasyonundaki normal çizimler korunur.

### Sınırlar

HOME göndericisi bu Android sürümünde mevcut loglardan belirlenemiyor. Normal HOME davranışı engellenmedi. Loglarda yeni TTS çalışması görülmediğinden TTS'yi kapatarak neden ilan edilmedi. Gerçek teybin dokunma kilitlenmesinin giderildiği, bu sürüm teypte denenmeden doğrulanamaz.

### Doğrulama sonucu

- `:app:compileCarReleaseKotlin` başarılı.
- `:app:testCarReleaseUnitTest --tests app.vela.offline.PmtilesTileSourceTest` başarılı: 5 test, 0 hata. Yeni test, yavaş/failing leaf okuması sırasında farklı leaf isteğinin tamamlandığını ve başarısız leaf'in sonraki istekte tekrar okunabildiğini doğrular. Mevcut test aynı dizin için tek okuma ve eşzamanlı tile body okumasını doğrular.
- `git diff --check` başarılı.
- Bu tur yeni APK emülatöre kurulmadı; kaynak derlemesi ve yerel birim testleri doğrulandı. Teyp henüz tekrar denenmedi.
