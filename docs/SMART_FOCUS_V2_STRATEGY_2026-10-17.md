# Smart Focus V2: kaynak takibi ve ortak player stratejisi

Tarih: 17 Ekim 2026. Durum: inceleme ve öneri; uygulama davranışı bu çalışmada değiştirilmedi.
Uygulama notu: Büyük player mevcut kaynak seçicisini kullanır. Küçük panelde seçici eklenmez ve dahili player simgesi gösterilmez; yalnız harici kaynak simgesi görünür. Güncel uygulanan kapsam ve donanım sınırları SMART_FOCUS_V2_IMPLEMENTATION_2026-10-17.md belgesindedir.

Bu belge Eylül taslağını güncel MusicManager incelemesiyle tamamlar. Android Auto'nun kapalı uygulama içi algoritmasının birebir tarifi değildir; Vela için önerilen davranış sözleşmesidir.

## 1. Kodda doğrulanan sorunlar

- MusicManager.userCommand(), Play/Pause/Next dahil sıradan kumandaları manualSelection=true yapıyor. Otomatik takip açık görünürken, kullanıcı fark etmeden kalıcı elle seçim kilidine girebiliyor.
- senkronizeEtMedyaDurumu(), adaptör listesindeki ilk çalan kaynağı seçiyor ve mevcut kaynak çalarken yeni kaynağa geçmiyor. Liste sırası ve eski PLAYING durumu sonucu belirleyebiliyor.
- MediaController callback'i ayrıca kaynak seçiyor; karar tek noktadan çıkmıyor. Elle kilitte başka başlayan oturumları duraklatma davranışı var.
- requestSmartFocus(), hedef dışındaki bütün çalan controller'ları duraklatabiliyor. Sadece önceki müzik sahibini devretmek ile bütün oturumları susturmak birbirine karışmış.
- BT adaptörü bağlantı ve oynatmayı ayırıyor; ancak başlangıç durumunu doğrulayan sorgu ve cihaz yetenek doğrulaması eksik. canControl yalnız platform adından türetiliyor. Genel Android'de incoming A2DP sink bilgisi her teypte görünmeyebilir.
- HCN müzik uygulamasının kurulu olması gerçek oynatma değildir. Radyo durumunun OEM kaynağından doğrulanması gerekir.
- Medya modelinde sourceId, kaynak türü ve kaynak etiketi yok. paketAdi yalnız MediaSession adaptörü için dolduruluyor.
- CarSmallMusicWidget içindeki widget_app_icon XML'de sabit play simgesi; update akışı etkin uygulama simgesini bağlamıyor. Büyük player'ın onAppIconClick parametresi mevcut, fakat kaynak simgesi davranışı tamamlanmış değil.

İlgili dosyalar: MusicManager.kt, UniversalBluetoothAdapter.kt, AndroidMediaSessionAdapter.kt, HeadUnitMediaAdapters.kt, LauncherPermissionsSettings.kt, CarSmallMusicWidget.kt, CarMusicPlayerView.kt, CarLauncherModels.kt ve widget_music_modern.xml.

## 2. Üç farklı işlem

1. Kaynak takibi: Kullanıcı gerçekten nerede müzik başlattıysa o kaynağın bilgisini ve kumandasını göster.
2. Otomatik oynatma: Duran bir kaynağa PLAY gönder. Ayrı ve açık bir izin gerektirir.
3. Android ses odağı: Çağrı, navigasyon konuşması ve müzik paylaşımını sistemle yönet. Ekranda kaynak göstermek için tek başına audio focus istenmez.

BT bağlantısı müzik başladığı anlamına gelmez. Metadata/kapak yenilenmesi, uygulamanın öne gelmesi veya bildirim gelmesi de kaynak devri değildir.

## 3. Önerilen varsayılan politika

- Smart Focus: açık.
- BT müzik çalmaya başlayınca takip et: açık; gerçek, güncel oynatma sinyali gerekir.
- Diğer uygulamalar: uygulama başına “Smart Focus'a dahil et”. Yalnız etkinleştirilen müzik uygulamaları otomatik devralabilir. Elle seçmek için bu izin gerekmez.
- Dahili player: takibe dahil.
- Otomatik PLAY: ayrı mevcut ayar; bağlantı veya panel açılmasıyla tetiklenmez.
- BT durunca önceki kaynağı otomatik çal: başlangıçta kapalı. İstenirse yalnız Vela'nın devralırken durdurduğu kaynak için açılabilir.
- “Bu kaynakta kal”: seçicide ayrı, görünür kilit. Normal Play/Pause/Next otomatik takip modunu kapatmaz.
- Kaynak simgesine kısa dokunma: kaynak seçici. Kaynağı ayrıca açma eylemi bulunur. Paneli büyütme kaynak veya oynatma durumunu değiştirmez.

## 4. Kaynak karar sırası

Önce çağrı/TTS gibi geçici kesinti belirlenir. Bunlar müzik kaynağı değildir ve yeni müzik başlatmaz.

1. Açık kullanıcı kaynak seçimi veya etkin “bu kaynakta kal” kilidi.
2. Kilit yoksa, Smart Focus'a izin verilmiş kaynaktan yeni ve doğrulanmış oynatma başlangıcı.
3. Yeni başlangıç yoksa, hâlen geçerli olan mevcut çalan kaynak korunur.
4. Etkin kaynak durdu/koptuysa, gerçekten çalan uygun diğer kaynak izlenir.
5. Hiçbir kaynak çalmıyorsa son kullanılabilir BT dışı kaynak gösterilir; PLAY gönderilmez. O da yoksa dahili player duraklatılmış gösterilir.

Duraklatılmış kaynak göstermek, onu çalmaya zorlamak değildir. Kontroller ve simge her zaman gösterilen kaynağa aittir.

İlk durum keşfinde birkaç kaynak PLAYING diyorsa, bu birden fazla yeni başlama olayı sayılmaz. Geçerli mevcut kaynak, sonra kullanıcı tercihi, sonra doğrulanmış güncellik değerlendirilir. Her yenilemede seçim sıçramaz.

## 5. Senaryolar

| Durum | Ekran/kumanda hedefi | Otomatik ses başlatma |
| --- | --- | --- |
| Telefon yalnız bağlandı, müzik yok | Çalan dahili/izinli harici kaynak | Hayır |
| Telefon bağlı ve gerçekten müzik çalıyor, kilit yok | Bluetooth | İkinci PLAY yok; mevcut oynatma takip edilir |
| Dahili çalarken telefondan müzik başlatıldı | BT'ye geç; önceki yerel sahibi bir kez duraklat | BT'ye tekrar PLAY yok |
| BT bağlı fakat duruyor, başka izinli player çalıyor | Diğer player | Yeni PLAY yok |
| BT durdu, hiçbir kaynak çalmıyor | Son BT dışı kaynak, duraklatılmış | Hayır; önceki müziğe devam ayarı ayrı |
| BT parça değiştirirken hazırlanıyor | BT korunur | Başka kaynak başlatılmaz |
| BT bağlantısı koptu | Çalan uygun kaynak; yoksa sessiz yedek | Varsayılan hayır |
| İzinli Spotify/diğer player'da kullanıcı Play yaptı | O uygulama | Var olan oturum takip edilir |
| Takibe kapalı uygulama müzik başlattı | Vela otomatik kaynak devri yapmaz | Vela onu otomatik durdurmaz; gerekirse kullanıcı kaynağı seçer |
| Kullanıcı seçiciden kaynak seçti | Seçilen kaynak | “Seç” sesi başlatmaz; “Seç ve çal” açık komuttur |
| Kullanıcı Pause yaptı | Mevcut kaynak durur; takip modu açık kalır | Yerine başka müzik başlatılmaz |
| “Bu kaynakta kal” açıkken başka kaynak başladı | Kilitli kaynak korunur; çatışma gösterilebilir | Rastgele üçüncü uygulama durdurulmaz |
| TTS konuşuyor / telefon görüşmesi var | Mevcut kaynak, kesinti durumu | Kullanıcının ayrıca durdurduğu müzik sonradan başlatılmaz |
| Uygulama çekmecesi, büyük/küçük panel veya ekran yönü değişti | Aynı kaynak ve aynı oturum | Hiçbir PLAY/PAUSE yok |
| Android Auto/ZLink öne geldi | Müzik kaynağı durumu izlenir | ZLink/TTS oturumu müzik kaynağı sayılmaz; ekran/task açılmaz |
| Teyp açıldı | Önce kayıtlı ekran ve kaynak; bağlantı keşfi | Yalnız otomatik devam açık ve son oturum gerçekten çalıyorsa koşullu devam |

Takibe kapalı player'ın fiziksel sesi üzerinde sistem/OEM audio focus yine etkili olabilir. Vela arayüzünün otomatik geçmemesi, o sesi zorla engelleyebileceği anlamına gelmez.

## 6. Geçişlerin kararlı olması

- Yeni bir kaynak gerçekten PLAYING olduğunda olay zamanıyla işlenir; metadata olayı başlangıç sayılmaz.
- Önerilen 500 ms oynatma doğrulaması ve 2 saniye durma beklemesi kısa titremeyi önler. Bunlar Android zorunluluğu değil, testle ayarlanacak ürün değerleridir.
- BUFFERING/CONNECTING için önerilen 8 saniye koruma; kullanıcı başka kaynak seçerse bekleme anında iptal olur. Süre sonunda çalmıyorsa hata/sessiz yedek gösterilir.
- Çağrı veya TTS nedeniyle geçici durma, normal BT durması gibi yorumlanmaz.
- Kullanıcı duraklatması, ses komutunu ve bekleyen otomatik devamı iptal eder.
- Geçiş kimliği sayesinde eski callback, timeout veya gecikmiş PLAY seçimi geri çeviremez.
- Aktif/pasif devir ayrılır: Zaten çalan kaynağı izlerken PLAY gönderilmez. “Seç ve çal” geçişinde hazır olma kontrolü, önceki müzik sahibini bırakma, tek hedefli PLAY ve sonuç doğrulaması vardır.
- Yeni kaynak 5 saniyede oynatmayı doğrulamazsa “Başlatılamadı” gösterilir; kullanıcı tercihi olmadan kaynaklar arasında oynatmayı deneyen döngü kurulmaz.
- BT aynı zamanda OEM MediaSession olarak görünüyorsa iki bağımsız kaynak sayılmaz. Mantıksal kaynak kimliği ile kontrol/metadata uçları eşleştirilir.

## 7. Büyük ve küçük player'da kaynak simgesi

Her iki player ve dock/kaynak seçici aynı kaynak kimliğini kullanır.

| Kaynak | Gösterilecek simge |
| --- | --- |
| Dahili Vela | Vela müzik simgesi |
| Teypte Spotify / diğer MediaSession uygulaması | O paketin gerçek uygulama simgesi |
| Telefondan Bluetooth | Bluetooth simgesi; varsa telefon adı |
| Donanım radyosu | Radyo simgesi; frekans/bant |
| Kaynak yok / durum bilinmiyor | Nötr müzik simgesi ve açıklama |

Albüm kapağı kaynak simgesinin yerine geçmez. Kaynak simgesi kapaktan ayrı, sabit boyutlu ve yüksek kontrastlıdır. Büyük player'da öneri 32 dp; küçükte 24 dp. Tıklama alanı en az 48 dp olur. Dikey/yatay konum değişse de kaynak kimliği korunur.

Telefonda Spotify çaldığını teybin BT bağlantısından her zaman öğrenemeyiz. Paket kimliği/simge bilgisi gerçekten sağlanmıyorsa Spotify simgesi uydurulmaz; Bluetooth simgesi kullanılır. Android Auto telefon uygulamasıyla doğrudan konuşurken, sıradan Android teyp BT yolu aynı bilgiyi sunmayabilir.

Harici kaynakta dahili playlist gösterilmez. Seek/süre, shuffle, repeat ve ileri/geri kontrolleri adaptörün gerçek yeteneklerine göre etkinleştirilir. Gerçek ses verisi olmayan kaynakta sahte görselleştirici üretilmez.

## 8. Ayarlar

Smart Focus bölümünde:

- Otomatik kaynak takibi.
- BT oynatmasını takip et.
- Takibe dahil uygulamalar listesi: gerçek uygulama simgesi, ad, açık/kapalı.
- Boştayken gösterilecek kaynak: son BT dışı / dahili / seçilen uygulama.
- Başlangıç kaynağı ve otomatik devam: birbirinden ayrı.
- BT durunca/kopunca önceki müziğe otomatik devam: kapalı; kullanıcı isterse açar.
- Mevcut kaynakta kal kilidi ve “Otomatik takibe dön” eylemi.
- Bilgi satırı: “Kaynak Spotify: kullanıcı oynatma başlattı”, “BT bağlı, çalmıyor”, “Oynatma durumu alınamıyor”.

Önceki auto_follow tercihi korunur. Daha önce sıradan bir kumandanın yarattığı gizli manualSelection kilidi kalıcı kilit olarak taşınmaz. Eski last_source başlangıç ipucu olur; fiilen çalmayan kaynak çalıyor diye gösterilmez.

## 9. Uygulama planı

1. Kaynak kimliği ve yetenekleri: MusicManager içinde tek kaynak kaydı; bağlı/hazır/çalıyor/bilinmiyor ayrımı, MediaSession ve OEM BT eşlemesi. Mevcut adaptörler korunur.
2. Tek karar noktası: UI değişiminden bağımsız kaynak seçici; sıradan kumanda ile kaynak kilidini ayırma, uygulama izin listesi, kararlı olay sırası, tek hedefli komutlar. Mevcut farklı callback seçim yolları burada birleştirilir.
3. Ayarlar ve iki player: kaynak simgesi/adı, aynı kaynak seçici, kaynak yeteneklerine göre kontroller. Uygulama simgeleri önbelleğe alınır; her zamanlayıcıda PackageManager sorgusu yapılmaz.
4. Açılış ve kesintiler: geciken telefon, BT kopması, uygulama kapanması, çağrı/TTS, son kullanıcı komutunun korunması. Android ses odağı ayrı yönetilir; kayıtlı ses düzeyi kullanıcı değişikliğinin üstüne yazılmaz.
5. Kabul: önce birim senaryoları, sonra emülatörde yerel/harici MediaSession ve panel geçişleri, en son teybe gerçek telefonla BT/OEM testi. Emülatör gerçek BT sink ve MCU radyo kabulünün yerine geçmez.

Periyodik agresif tarama, yeni medya motoruna geçiş veya ayrı sürekli servis ilk çözüm değildir. Mevcut listener/callback akışı üzerinden olayla güncellenir. Tanı ekranı kaynak başına son olay, bağlılık, oynatma, izin, seçilme nedeni ve komut sonucunu gösterir; parça bilgisi değişmeyince tekrar yayın yapılmaz.

## 10. Kabul ölçütleri

- Yalnız BT bağlantısı mevcut çalan kaynağı değiştirmez.
- Gerçek BT oynatma başlangıcı, otomatik takip açıkken Play/Pause kullanılmış olsa bile algılanır.
- İzin verilen harici uygulama devralır; izin verilmeyen uygulamaya otomatik komut gönderilmez.
- Aynı olaya çift pause/play gönderilmez; kaynaklar arasında gidip gelme olmaz.
- Her iki panelin simgesi, başlığı ve kumanda hedefi aynıdır.
- Panel/drawer/rotasyon kaynak veya oynatmayı başlatıp durdurmaz.
- Durdurma, çağrı ve TTS sonrası istenmeyen otomatik müzik başlamaz.
- OEM doğrulanmayan durumda “bilinmiyor” gösterilir; kurulmuş uygulama veya eski metadata PLAYING kanıtı sayılmaz.

## Resmî referanslar

- [Android Auto/AAOS oynatma kontrolü](https://developer.android.com/training/cars/media/enable-playback): MediaSession durum/komutları ve bağlantıda kendiliğinden oynatmama.
- [Android ses odağı](https://developer.android.com/media/optimize/audio-focus): geçici kesinti ve oynatma odağı. Özellikle eski Android teypte platformun başka uygulamayı zorla susturmasına güvenilmez.
- [AOSP araç Bluetooth bağlantısı](https://source.android.com/docs/automotive/ivi_connectivity): A2DP alıcı yolu ve AVRCP kumanda ilişkisi. Bu AAOS belgesi, Vela'nın bütün OEM ayrıcalıklarına erişebildiği anlamına gelmez.
