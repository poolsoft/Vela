# OsmAnd → Vela: müzik ve launcher karşılaştırması

Güncelleme: 24 Eylül 2026.
Durum tanımları: **Kod mevcut** = kaynakta uygulanmış, cihaz kabulü bekler. **Kısmi** = temel akış var, aşağıdaki sınırlar sürüyor. **Kalan** = bu turda uygulanmadı.

## Bu turdaki somut düzeltmeler

- Büyük oynatıcı kapak, parça bilgisi ve görselleştirici görünümüyle açılır. Playlist yan düğmeyle açılır. XML'de gizli bırakılmış kapak kartı görünür yapıldı; kapak ve arka plan aynı parçayı izler.
- Sekme bağlayıcısı dosyada duruyordu. İlk açılışı zorla playlist yapan ayar ve gizli kapak tespit edildi; bunların kim tarafından/hangi araçla değiştirildiği Git geçmişinden kanıtlanmadı. Çalışan APK'nın bu kaynakla aynı olduğu da cihazda doğrulanmadı.
- Kütüphane açılınca izin varsa yeniden taranır; aktif sekme saklanır ve belirgin gösterilir. Arama klavyesi açma/kapatma bağlandı.
- Sıra, parçalar, klasörler ve listeler mevcut veri depolarına bağlıdır. Favoriler boş olsa da görünür. Son çalınanlar ve en çok dinlenenler eklendi. Gerçek oynatma başladığında sayılır; her UI güncellemesi/normal pause-resume tekrar sayılmaz.
- Parçalar ve klasör içeriği ad, sanatçı, albüm, eklenme tarihi veya çalınma sayısına göre sıralanabilir. Kuyruk ve kayıtlı playlist sırası bu tercihle değiştirilmez.
- Smart Focus OsmAnd'da zaten vardı. Vela'daki aktarım geliştirilerek elle kaynak kilidi, kaynak başına seçim, diğer MediaSession'ları izleme ve paket başına tek duraklatma eklendi.
- Seçicide “Kaynağı seç” ve “Seç ve çal” ayrıldı. Aynı kaynağın çalıyor durumunda yeniden PLAY gönderilmez. Hazır olmayan kaynağa geçiş mevcut sesi durdurmaz.
- Oynatma komutundan sonra beş saniye içinde çalıyor doğrulaması gelmezse kullanıcıya bildirilir. Bu bir donanım onay protokolü veya otomatik geri alma garantisi değildir.
- Elle duraklatma, eski yerel geçici odak-devam isteğini iptal eder. Geçici odak kaybı için ayrı duraklatma yolu kullanılır.
- Genel BT medya tuşu yayını kaldırıldı. OsmAnd referansındaki XYAuto/HCN komutları kullanılır. A2DP sink bağlantısı ve oynatma ayrı işlenir; telefondaki dışarı ses gönderen A2DP bağlantısı ayrı bir müzik kaynağı sayılmaz.
- Ortak ayar merkezi: harita ve launcher girişleri aynı SettingsScreen'e gider. Araç ayarları bunun alt bölümüdür; eski tercih depoları korunur. Yeni bir paralel ayar kopyası oluşturulmadı.
- İzin ekranı ses dosyası, mikrofon, uygun sürümde BT/bildirim ve özel erişim durumlarını gösterir; sistem ekranından dönünce yenilenir. Ana ekran rolü kullanıcı seçimiyle alınır.
- Vela HOME alias'ı eklendi. HOME ile açılınca araç modu etkinleşir. Boot receiver veya gizli otomatik izin verme eklenmedi.
- Eski sürüm-1 yedekleri yeni Smart Focus grubu olmadan da kabul edilir. İçe aktarma bekleyen otomatik oynatma isteğini iptal eder; canlı medya oturumları/izinler yedeklenmez.

## Özellik karşılaştırması

| Özellik | OsmAnd referansı | Vela durumu / kalan |
| --- | --- | --- |
| Büyük oynatıcı kapak ve FFT | MusicPlayerFragment | Kod mevcut; harici oturumun gerçek FFT erişimi garanti değil |
| Playlist düğmesi ve dört sekme | MusicPlayerFragment | Kod mevcut; gerçek dokunma/boş liste/izin dönüşü cihaz kabulü gerekli |
| Kuyruk, klasör ve kayıtlı listeler | PlaylistManager ve MusicRepository | Kod mevcut; USB kayıtları korunur |
| Favoriler | PlaylistManager | Kod mevcut; boş favori listesi de görünür |
| Son çalınanlar / en çok dinlenenler | PlaylistManager | Kod mevcut; geçmiş en son 200 farklı yerel parçayla sınırlı, harici parça geçmişi tutulmaz |
| Sıralama | TITLE / ARTIST / ALBUM / RECENTLY_ADDED / MOST_PLAYED | Kod mevcut; parçalar ve klasörlerde beş seçenek |
| Yeni eklenenler özel liste | MusicPlayerFragment | Eklenme tarihine göre sıralama var; ayrı akıllı liste kalan |
| Müzik jestleri | MusicGestureListener: çift dokunma/kaydırma | Kalan; düğme kontrolleri mevcut |
| Kaynak seçimi | Paket/adaptör seçimi | Kod mevcut; etkin oturumlar ayrı adlarla seçilir |
| Smart Focus | requestSmartFocus | Kısmi; kullanıcı kilidi ve oturum kontrolü var, tam donanım geçiş işlemi kalan |
| HCN/XYAuto radyo | Donanım adaptörleri | Kısmi; frekans yayını halen fiziksel sesin kesin kanıtı değildir |
| HCN yerel müzik | Paket komutları + oturum bilgisi | Kısmi; oturum yoksa gerçek çalma durumu bilinmez |
| BT metadata ve kontrol | Donanım yöneticisi | Kısmi; XYAuto yayınları ve sink olayları; standart Android'de OEM komutu varsayılmaz |
| BT yeniden bağlanma | Cihaza bağımlı | Kalan: aynı cihaz kimliği, 30 saniye devam kaydı, çoklu telefon, güncel bağlantı sorgusu |
| Açılışta devam | Son kaynak / otomatik oynatma | Kısmi; son gerçek çalma, 3–15 saniye bekleme, son/dahili/BT/HCN radyo/XY radyo tercihi var |
| Kaynak hazır olunca devralma | Adaptör davranışları | Kısmi; erişilebilirlik kontrolü var; asenkron dosya/servis hazırlığı tam geçiş işlemi değil |
| Başarısız geçişten geri alma | Geliştirme ihtiyacı | Kalan; teybin sesi kesildiği doğrulanmadan otomatik geri oynatma yapılmaz |
| Telefon/TTS | Focus ve ducking | Kısmi; dahili focus yönetimi mevcut, tüm MCU radyo/çağrı kombinasyonları kalan |
| Ayar merkezi | Launcher ayarları ve OsmAnd çekirdeği ayrı | Vela ortak giriş tamam; araç alt sayfasında eski kategoriler korunur |
| Ayar araması / D-pad | Vela ana ayarlarında mevcut | Yeni kategoriler aramada var; araç alt sayfasının tüm satırlarında D-pad eşdeğerliği kalan |
| Android widget / yedek / duvar kâğıdı | Launcher yardımcı özellikleri | Önceki tur kodu mevcut; provider iptali, taşınabilirlik ve görsel kabul bekliyor |
| PiP | Activity desteği | Kod mevcut; müziğe özel PiP içerik/kontrol tasarımı kalan |
| Gerçek araç/hava verisi | Kaynak sağlayıcıları | GPS hız aktarımı var; diğer alanların gerçek sağlayıcıları kalan |
| Anten | GPS/pusula destekli yardımcı ekran | Elle koordinat hesabı var; canlı GPS/pusula kalan |
| Sesli komutlar | VoiceCommandService | Kalan: launcher komutları ve Smart Focus'a yönlendirme |
| Parallax / saat-hız görünümleri | Launcher görünümleri | İlave görünümler ve parallax kalan |

## Planın güncel durumu

- P0-1: Görseller esas alınarak ilk açılış kapak/FFT kabul edildi. Seçicide çalan kaynağı devralma, sessizken seçme ve ayrı Seç ve çal uygulandı. Kullanıcı bu kararları değiştirebilir.
- P0-2/3: Smart Focus'un kaynak seçimi/kilit/oturum gözleme temeli eklendi. Tam olay kökeni, bağlantı kimliği, iç içe kesinti ve işlemsel geçiş/geri alma bitmedi.
- P0-4: HCN/XYAuto gerçek cihaz doğrulaması bekliyor.
- P0-5: Tek ayar girişi ve mevcut depoları koruma tamam. Araç ekranının tüm ortak tema/dil/D-pad ayrıntıları bekliyor.
- P1-1: Ortak izin merkezi ve BT sürüm bildirimi eklendi. Tüm Android sürümleri/OEM izin reddi kabulü, dış servis erişim denetimi, gereksiz manifest izinlerinin temizliği bitmedi.
- P1-2: HOME rolü ve kaynak açılış tercihleri eklendi. ACC/kontak, uyku-uyanma, OEM başlatma ve kısa BT kopmasında devam bekliyor.
- P1-3/4: Kaynak kontrolleri yapıldı; cihaz kabulü yapılmadı.
- P2: Hava/araç sağlayıcıları, sesli komutlar, parallax/ek görünümler ve canlı anten verisi bekliyor.

## Sıradaki uygulama sırası

1. Gerçek cihazda bu sürümün müzik görünümünü ve dört sekmesini kabul et; eski APK ile yeni kaynak farkını kapat.
2. HCN/XYAuto modeline göre BT bağlantı kimliği, radyo ses yolu ve gerçek PLAY/PAUSE onaylarını ekle. Yayın adı ve frekans gelmesini oynatma kanıtı sayma.
3. Bu gerçek olayların üzerine kısa kopmada devam, çağrı/TTS kesintileri, bekleyen geçişin iptali ve güvenli geri alma ekle.
4. Ortak ayarların araç bölümünü tema/dil/D-pad ve dar ekran açısından tamamla; manifest izinlerini kullanılan yeteneklere indir.
5. Yardımcı özellikleri ve P2 veri sağlayıcılarını tamamla.

## Doğrulama ve sınırlar

Çalıştırılan: tools/check_car_music_contract.py, değişen XML'lerin ayrıştırılması, UTF-8/BOM ve kaynak adı kontrolü, Kotlin ayraç yapısı, git diff --check.
Kontrol 42 görünüm bağlantısını doğruladı. Bu kontroller Android derlemesi veya davranış testi değildir.
Talimat gereği Gradle veya Android derlemesi çalıştırılmadı. Emulator/teyp testi yapılmadı.

Cihaz kabulü: ilk müzik açılışı → kapak/FFT; playlist düğmesi → dört sekme; izin ret/ver; liste oluştur/favorile/yeniden aç; başlatılan parçanın geçmişte tek kayıt olması; sıralama kuyruğu değiştirmesin; yerel→harici→radyo; elle duraklat→odak geri gelmesi; telefon bağlanırken radyo devam; ayarlar iki girişten aynı değer; eski yedeği içe aktar; HOME rolü ve geri; dikey/yatay dönüş.

[Smart Focus senaryo ve karar belgesi](SMART_FOCUS_SETTINGS_PERMISSIONS_PLAN.md)
