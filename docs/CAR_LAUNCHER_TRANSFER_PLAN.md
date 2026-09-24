# Vela Car Launcher aktarım planı

Tarih: 23 Eylül 2026

## Kapsam ve yöntem

Kaynak: OsmAnd/src-carlauncher. Hedef: app/src/main/java/app/vela/carlauncher ve ilgili kaynaklar.
Harita motoru, harita verisi ve rota hesaplama aktarım dışında. Vela'nın mevcut konum/navigasyon çıktıları launcher'a arayüz üzerinden sağlanmalı; OsmAndApplication gibi çekirdek bağımlılıklar taşınmamalı.

Bu envanter kaynak kod incelemesine dayanır. Sınıf veya ekran bulunması, özelliğin cihazda doğrulandığı anlamına gelmez. Mevcut Vela değişiklikleri korunur; aşamalar ayrı değişiklikler olarak uygulanır. Gradle/build veya yerine geçen Android derlemesi ajan tarafından çalıştırılmaz.

## Bu turda uygulanan düzeltmeler

- Masaüstü modu için ikinci kez tersine çeviren callback kaldırıldı. Başlangıçta map_only seçimi eski masaüstü tercihini temizler.
- XML host'larına en güncel medya/telemetri ve callback'ler iletilir. Dock mini müzik bilgisi de güncellenir.
- Müzik/uygulama çekmecesi değiştirilirken ve AndroidView bırakılırken host coroutine scope'ları iptal edilir. İç ComposeView composition'ı bırakılır.
- Dock konumu/boyutu, panel konumu/boyutu ve masaüstü durumu değişince yerleşim yeniden uygulanır. Medya güncellemeleri tek başına yerleşimi yeniden kurmaz; tam harita durumu korunur.
- Büyük müzik görselleştiricisinin bağlamı her medya güncellemesinde sıfırlanmaz.

Durum: kod düzeltmeleri uygulandı; cihaz kabul kontrolü bekliyor.

## Aşama 0 - Mevcut düzeltmelerin cihaz kabulü

1. Split → tam harita → masaüstü → kapat → split akışını dene; masaüstünü döngüden çıkarıp tekrar dene.
2. Normal, map_only ve desktop başlangıçlarını ayrı ayrı kontrol et.
3. Yerel ve harici müzikte parça değişimi, pause/play, küçük panel, büyük panel ve yatay dock bilgisini karşılaştır.
4. Gösterge açıkken değişen gerçek konum/hız verisinin ekrana geldiğini kontrol et.
5. Sağ/sol/alt dock, dock boyutu, panel yönü ve ayırıcı sürüklemeyi hem split hem tam haritada dene.
6. Müzik ve uygulama çekmecesini tekrar tekrar aç/kapat; profiler ile eski host/collector birikmediğini kontrol et. Launcher kapatma ve Activity yeniden oluşturmayı da kapsa.

Kabul: çift mod değişimi, eski medya bilgisi, yanlış panel açılması ve biriken host nesneleri görülmemesi. Bu kontroller bu turda cihaz üzerinde yapılmadı.

## Aşama 1 uygulama kaydı - 23 Eylül 2026

Kodda tamamlananlar (cihaz kabulü bekliyor):

- Dahili uygulamalar tek katalogdan iki çekmeceye de ekleniyor: Müzik, Dashboard, Dijital Gösterge, Ayarlar, Anten. Anten henüz uygulanmadığından açık bir bilgi mesajı gösterir; tamamlanmış araç sayılmaz.
- Çekmece/dock/masaüstü aynı exact URI yönlendirmesini kullanır. Dashboard ile Dijital Gösterge ayrı mevcut ekranları açar. Masaüstü müzik açma bağlantısı tamamlandı.
- Çekmecede uzun basarak dock'a ekleme; dock'ta uzun basarak öne/arkaya taşıma ve kaldırma; canlı liste güncellemesi. Boş dock yeniden başlatmada varsayılanlara dönmez.
- Kaydedilebilir panel/tam harita durumu üst bileşene taşındı; geri tuşu ve masaüstü dönüşü tek akışta. Aktif panelin yön değişiminde korunması cihazda doğrulanacak.
- Küçük panelde açma davranışı kullanıcı geri bildirimiyle kaldırıldı. Varsayılan swap; eski compact tercihi swap olarak taşınır. Müzik, çekmece ve diğer dahili içerikler büyük panelde açılır; overlay seçimi korunur.
- Dikey panel yüksekliği ayrı tercih. Dock boyut hesabı eski ölçü yerine seçili değeri kullanır; görünür ayırıcı ve yön değişiminde eski kısıtların temizliği düzeltildi.
- Sistem boşlukları gerçek WindowInsets üzerinden hesaplanır; sistem çubuğu görünürlüğünün sahibi MainActivity olarak bırakıldı.
- Küçük/büyük görselleştirici tipi ve 15/30/60 fps tercihi ayrı; mevcut host'lara canlı uygulanır. Gerçek FFT yakalama Aşama 2 işidir.
- Tercih edilen müzik uygulaması seçimi ve MediaSession seçim önceliği bağlandı. Kaynak geçişi/otomatik oynatmanın cihaz kabulü Aşama 2 kapsamında.
- Yeni arayüz metinleri Türkçe ve varsayılan dil kaynaklarında. Önceden var olan sabit metinlerin ve tema renklerinin toplu uyarlanması henüz bitmedi.

Statik doğrulama: XML/resource referansları, UTF-8/BOM ve delimiter kontrolleri. Gradle/Android derlemesi veya cihaz testi çalıştırılmadı.

Sonraki iş: Aşama 1'in eski metin/tema uyarlamasını tamamlamak ve kabul senaryolarını cihazda doğrulamak; ardından Aşama 2 müzik işlevleri. Aşama 2 kütüphane ve oynatma düzeltmeleri aşağıdaki ek kayıtta; Aşama 3–5 henüz uygulanmadı.

## Aşama 1 - Ekran ve ayar bağlantılarını tamamlama

Öncelik: yüksek. Bağımlılık: Aşama 0.

- CarAppDockHost içindeki RecyclerView için gerçek kısayol adaptörü, seçme/sıralama/silme ve canlı liste güncellemesi. İlk incelemede yalnızca LayoutManager bağlıydı; sonraki yerel değişiklik adaptörü ekledi, bu aşamada canlı güncelleme ve düzenleme tamamlandı.
- Masaüstündeki müzik açma callback'i şu anda boş. Dahili internal:// kısayolları masaüstünde PackageManager yerine ortak launcher yönlendiricisiyle açılmalı.
- swap/overlay tercihi okunuyor fakat düzen davranışına uygulanmıyor. Ekran durumunu tek model altında toplamak; tam harita/panel/masaüstü dönüşleri ve geri tuşunu birlikte ele almak.
- Dikey panel yüksekliği ile yatay panel genişliğini ayrı saklamak; yön değişiminde aktif ekranı korumak.
- Tercih edilen müzik uygulaması, küçük/büyük görselleştirici türü ve kalite ayarlarının gerçek tüketicilerini bağlamak.
- Sistem çubuğu boşluklarını sabit 28/48 dp yerine gerçek WindowInsets üzerinden hesaplamak.
- Yeni metinleri strings_car.xml kaynaklarına taşımak; Türkçe ve varsayılan dil; Vela'nın dil/tema altyapısıyla tutarlılık.

Kabul: her görünen ayar somut davranış değiştirir ve yeniden açılışta korunur; dahili/harici kısayollar çalışır; dikey-yatay dönüşte panel durumu kaybolmaz.

## Aşama 2 - Müzik işlevlerinde eşdeğerlik

Öncelik: yüksek. Bağımlılık: Aşama 1.

- Klasörler ve Listeler sekmeleri şu anda tüm parçaları gösteriyor. Gerçek klasör gruplama, çalma listesi oluşturma/düzenleme/silme ve kalıcılık eklemek.
- Kuyruk değişimlerini canlı izlemek; aramayı aktif sekmeyle tutarlı yapmak; USB/SD çıkarılması ve medya izinleri akışını tamamlamak.
- Görselleştiricide FFT işleyen çizim var; updateVisualizer çağrısını besleyen gerçek ses yakalama hattı bulunamadı. Ses oturumu/izin/başlatma/bırakma bağlantısını kurmak; veri yokken sahte animasyon göstermemek.
- İç ve dış oynatıcı ilerleme/süre verisinin tek aktif kaynaktan gelmesini sağlamak; kullanıcı seekbar sürüklerken otomatik güncellemenin müdahalesini önlemek.
- OsmAnd'daki HcnMusicAdapter, HcnRadioAdapter ve XyAutoRadioAdapter karşılıklarını eklemek; mevcut Bluetooth/XYAuto müzik adaptörleriyle kaynak seçimini doğrulamak.
- Albüm kapağı, otomatik tarama/oynatma ve ses odağı/TTS ducking davranışlarını kaynak projeyle karşılaştırıp tamamlamak.

Kabul: yerel, MediaSession, Bluetooth ve desteklenen teyp kaynaklarında aynı kontrol davranışı; gerçek klasör/playlist kalıcılığı; ses kesintisi ve USB çıkarılmasında güvenli toparlanma. Donanım adaptörleri ilgili ünitede denenmeden tamamlandı sayılmaz.

## Aşama 3 - Gerçek hava durumu ve araç verileri

Öncelik: yüksek. Bağımlılık: Aşama 1; araç adaptörleri için Aşama 2.

- WeatherWidgetView sabit 24°C/Açık gösteriyor. Gerçek sağlayıcı, konum seçimi, önbellek, güncellik zamanı, çevrimdışı/hata durumu ve etkinlik anahtarını bağlamak.
- OBD widget'ı şu anda GPS hızı/ortalama/irtifa gösteriyor. OBD/CAN veri sağlayıcısı, bağlantı durumu, RPM/sıcaklık/voltaj alanları ve gerçek dashboard bağları eklemek.
- Veri yokken ölçüm uydurmamak; boş/eski veri durumunu açıkça göstermek.
- Telemetride GPS ve donanım hız kaynağı önceliği, eşzamanlı güncellemeler, zaman aşımında hız aşımı bayrağının temizlenmesi ve sürüş süresi hesaplamasını düzeltmek.
- Haritadan gelen hız limiti ve manevra bilgilerini Vela arayüzü üzerinden bağlamak; motor kodunu taşımamak.

Kabul: ağ/konum/OBD bağlantısı kesildiğinde yanlış güncel veri gösterilmez; kaynak değişimleri ve zaman aşımı tutarlı; gerçek araç ölçümleriyle doğrulama.

## Aşama 4 - Launcher yardımcı özellikleri ve kişiselleştirme

Öncelik: orta. Bağımlılık: Aşama 1–3.

- OsmAnd LauncherBackupManager eşdeğeri: sürümlü dışa/içe aktarma, doğrulama ve bozuk dosyada mevcut veriyi koruma. Ayar, dock ve widget düzenlerini kapsama.
- AutoLaunchManager eşdeğeri: açılışta başlatma ve seçili uygulamalar; manifest/izin/üretici sınırlamaları. HOME launcher rolü ürün kararı olarak ele alınmalı; mevcut manifestte HOME filtresi yok.
- Android AppWidgetHost/SystemAppWidget eşdeğeri: widget seçme, bağlama izni, yapılandırma ve kimlik temizliği.
- Masaüstü sürükleme/yerleştirme, saat/hız paneli çeşitleri, duvar kağıdı/paralaks, yazı ölçeği ve PiP eşdeğerliği.
- Anten hizalama araçlarının harita dışı hesaplama/arayüz kısmı; harita katmanı kapsam dışında.
- Vela'nın mevcut sesli arama altyapısını koruyarak OsmAnd launcher komutları için eylem yönlendirme; modeli/import akışını ayrı değerlendirme.

Kabul: yedek round-trip aynı düzeni getirir; bozuk yedek veri kaybettirmez; widget silinince sistem kaynağı bırakılır; açılış davranışı ilgili cihazda doğrulanır.

## Aşama 5 - Uyumluluk ve saha doğrulaması

Öncelik: yayın öncesi. Her aşamada kısmi kontrol, sonunda bütünleşik kontrol.

- Vela minSdk 26, kaynak OsmAnd minSdk 24. Android 7/7.1 desteği gerekiyorsa bağımlılık/API denetimiyle ayrı uyumluluk işi açılmalı; yalnızca minSdk sayısı düşürülmemeli.
- HCN/XYAuto ünitelerde donanım tuşları, radyo/Bluetooth kaynak değişimi, uyku/uyanma ve güç döngüsü.
- Donanım olay kaydı/dışa aktarma ve başlangıç süre ölçümü; OsmAnd araçlarının Vela karşılıkları.
- Uygulama/görselleştirici/overlay yaşam döngüsü, izin reddi, düşük bellek ve uzun sürüş kontrolü.
- Aktarılan dosyaları seçerek VCS'ye alma; geçici ekran görüntüsü/APK/diagnostic dosyalarını eklememe. Vela'da launcher kaynaklarının önemli kısmı inceleme sırasında untracked idi.

Kabul: hedef cihaz matrisi ve sonuçları kayıtlı; doğrulanmayan cihaz/özellikler açıkça belirtilmiş; motor dışı özellik envanterindeki her kalemin uygulanmış/ertelenmiş durumu belli.

## Uygulama sırası

0 → 1 → 2 → 3 → 4 → 5. Hava durumu işi, ekran bağlantıları tamamlandıktan sonra müzik donanım adaptörlerinden bağımsız yürütülebilir. Sonraki çalışma Aşama 2 kalan işleri ve cihaz kabulünü ele almalı; bu belge diğer aşamaların uygulanmış olduğu anlamına gelmez.


## Aşama 2 uygulama kaydı - müzik kütüphanesi ve kontroller

Kodda tamamlananlar; derleme ve cihaz kabulü henüz yapılmadı:

- Mevcut Gemini XML görünümü korunarak gerçek sıra/parçalar/klasörler/listeler bağlandı. Aktif sekmede arama, klasöre girme/geri dönme, tümünü çalma ve karıştırma.
- Kalıcı çalma listesi oluşturma, ad değiştirme, silme, parça seçme/ekleme/çıkarma, favoriler. Bağlı olmayan USB dosyaları listeden otomatik silinmez.
- MediaStore değişimlerini gözlemleme, tarama/boş/hata durumları, Android sürümüne uygun ses dosyası izni ve yeniden tarama.
- Büyük oynatıcıda tek aktif kaynaktan ilerleme; sürükleme sırasında güncelleme durur. Harici kaynakta yerel karıştır/tekrar düğmeleri devre dışıdır.
- Kuyruktan seçim sırayı yeniden karıştırmaz. Elle sonraki düğmesi tek parça tekrarında da ilerler. Hazırlanırken duraklatma, eski oynatıcıyı bırakma, dosya hatası bildirimi düzeltildi.
- Ses dosyası content URI ile açılır; albüm kapağı URI yanlışlıkla ses kaynağı olarak kullanılmaz. Kapak albüm kimliğiyle sorgulanır.
- Harici kontroller seçili adaptöre gönderilir; uygulamanın kendi MediaSession kaynağı harici olarak seçilmez.
- Dahili ses oturumuna gerçek FFT yakalama, kullanıcı izni, görünürlük/detach sırasında bırakma eklendi. FFT verisindeki boş/tek uzunluklu dizi ve büyüklük taşması kontrol edildi.

Kalan Aşama 2 işleri:
- Harici uygulama ve teyp kaynaklarında gerçek ses yakalama: Android bu kaynakların ses oturumunu her cihazda paylaşmaz. Bu kaynaklarda sahte FFT üretilmez; donanım desteği ayrıca doğrulanmalı.
- HCN/XYAuto radyo adaptörleri ve donanım üzerinde kaynak geçişi, ses odağı/TTS, uyku/uyanma.
- Son parçayı açılışta geri yükleme ve otomatik oynatma bütünleşmesi; eski kayıtlı konumun rastgele seçilen yeni parçaya uygulanması kaldırıldı.
- Eski sabit metin/tema uyarlaması.

Cihaz kabul sırası:
1. Eski compact ayarı olan kurulumda küçük müzik, uygulama çekmecesi ve dahili dashboard açılırken büyük paneli kontrol et; geri ile split görünümüne dön.
2. Ses iznini reddet, Tara ile yeniden ver; klasör, arama, sıra ve boş durumlarını kontrol et.
3. Liste oluştur, parça seç, favorile, yeniden adlandır; uygulamayı yeniden açıp kalıcılığı kontrol et.
4. USB çıkar/tak; erişilemeyen kayıtlar korunmalı, tarama sonrası erişilebilir olmalı.
5. Karışık sıradan seçim, tekrar-tek + sonraki, ilerleme sürükleme ve hazırlama sırasında duraklatmayı kontrol et.
6. Harici oynatıcıya geç; başlık/kapak/ilerleme ve kontroller aynı kaynağı izlemeli.


## Dikey/yatay yerleşim ve müziğe devam etme - 23 Eylül 2026

- Yerleşim yönü host alanının ölçülen en/boy oranından belirlenir; dock yönü ve panel kısıtları birlikte güncellenir.
- Dock kısayolları kalan alan içinde kaydırılır. Dar ekranda mini oynatıcı ve yardımcı mikrofon alanı kısayolları sıkıştırmaz.
- Küçük panel hesabından kenar ve ayırıcı boşlukları çıkarılır; kontrolleri koruyan boyut sınırları uygulanır.
- Küçük panel saat, yazı ve düğmeleri ölçülere uyarlanır; görselleştirici kalan yüksekliği kullanır. Saatler gerçek zamanı gösterir.
- Büyük müzik ekranında dar alanda yan menü üst sıraya geçer; oynatma düğmeleri genişliğe sığar.
- Son yerel parça, sıra ve konum kütüphane hazır olunca geri yüklenir. Otomatik oynatma tercihi uygulanır; çalan harici kaynağın üzerine yerel müzik başlatılmaz.

Doğrulama: statik kaynak kontrolleri; Gradle/Android derlemesi ve cihaz testi yapılmadı.
Cihaz kabulü: dikey → yatay → dikey; müzik/çekmece/tam harita açıkken dönüş; sol/sağ/alt dock ve boyut tercihleri; ayırıcı sürükleme; yeniden açılışta parça ve konum.
Kalan işler: hedef donanım adaptörleri ve kaynak geçişinin cihaz kabulü, ardından Aşama 3 gerçek hava/araç verileri.


## Priority: music and launcher tools

User priority refers to items 2 and 5 in the conversation (music + helper features), not document stage 5.
Code prepared in this pass:
- Explicit music source selector; HCN local music and HCN/XYAuto radio adapters. Radio broadcasts are consumed only for a selected installed source. No fabricated HCN playback state.
- Removable-media rescans outside the music screen; MediaSession seek capability follows the selected source.
- Versioned backup through Android document selection. Full validation precedes writes; failed writes attempt rollback. Includes settings, dock, playlists and built-in widget layout.
- Device-bound Android widgets and wallpaper access are not portable. Existing Android widgets are preserved on import.
- Android widget pick/bind/configure flow, cancellation cleanup, desktop rendering and widget-ID deletion.
- Desktop drag-to-swap, page/size changes now publish immutable snapshots; removing all widgets stays empty.
- Wallpaper, desktop text scale, picture-in-picture action, and optional app launch once per foreground launcher process.
- Map-independent antenna bearing/distance/local elevation calculation from user-supplied coordinates.

Still requires device acceptance: HCN/XYAuto commands/frequency variants, Bluetooth transitions, focus/TTS, USB removal, widget providers/cancellation, PiP, backup round-trip, and startup app.
Not claimed implemented: automatic activity launch from device boot, wallpaper parallax, extra speed/clock skins, live antenna compass/GPS guidance, voice-command routing.
No Gradle build or Android compiler emulation is performed.
