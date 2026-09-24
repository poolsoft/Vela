# Vela - Smart Focus, ortak ayarlar ve izin planı

Tarih: 23 Eylül 2026. Durum: üzerinde karar verilecek taslak.
Bu belge davranış önerisidir; aşağıdaki senaryoların tamamı uygulanmış değildir. Bu çalışma uygulama kodunu veya cihaz izinlerini değiştirmez.

## 1. Bugünkü durum ve açıklar

- Vela: minSdk 26, targetSdk 35, compileSdk 36. Android sürümü ile derleme hedefi ayrı değerlendirilmelidir.
- Dahili oynatıcı, kütüphane, listeler, son parça/konum, kaynak seçici ve HCN/XYAuto adaptörleri mevcut. Donanım kabulü tamamlanmadı.
- MusicManager çalıyor görünen adaptörleri sıralayarak etkin kaynağı belirliyor. Elle seçimi koruyan merkezi Smart Focus durum makinesi henüz yok; eski çalıyor bilgisi seçimi geri değiştirebilir.
- UniversalBluetoothAdapter bağlantı olayını kaydediyor fakat bağlantı/kopma olayını işlemiyor. Genel müzik yayınlarını BT kabul edebiliyor; bunlar BT kaynağını kanıtlamaz. Medya tuşları hedef belirtilmeden yayınlanıyor.
- Yerel otomatik oynatma, harici kaynak o anda çalıyor görünmüyorsa başlayabiliyor. Telefonun geç bağlanması açılışta yarış yaratır.
- Radyo frekans bilgisi gerçek ses çıkışını tek başına kanıtlamaz. HCN müzik uygulamasının açılması da oynatmanın başladığını kanıtlamaz.
- OsmAnd requestSmartFocus diğer kaynakları duraklatıyor ve aynı pakete çift komutu önlüyor. Bu yaklaşım aktarım referansıdır; bağlantı, kullanıcı niyeti ve yeniden başlatma kurallarının tamamını çözmez.
- Ayarlar iki girişte: Vela SettingsScreen/SettingsHub ve CarLauncherSettingsView. Vela görünüm bölümünde ayrıca araç modu anahtarı var.
- LauncherStartup yalnızca launcher öne geldiğinde süreç başına uygulama açıyor. Kontak/boot algılama ve her uyanışta güvenilir çalıştırma değildir.

## 2. Temel davranış sözleşmesi

**Önerim:** Elle seçim öncelikli; BT bağlantısı tek başına müziği değiştirmesin. Otomatik başlatma ayrı, açıkça seçilen bir tercih olsun.

Aşağıdaki bilgiler birbirinden ayrılmalı:

| Bilgi | Anlamı |
| --- | --- |
| Tercih edilen kaynak | Sonraki açılış için kullanıcının seçimi |
| İstenen kaynak | Şu an geçilmek istenen kaynak; henüz çalmıyor olabilir |
| Etkin kaynak | Oynatma durumu doğrulanmış, kontrollerin yöneldiği kaynak |
| Bağlantı / hazır olma | BT profili, dosya, uygulama veya servis kullanılabilir mi? |
| Ses çıkışı | Teyp hoparlörü, BT çıkışı veya başka rota |
| Oynatma durumu | Hazırlanıyor / çalıyor / duraklatıldı / bağlantı bekliyor / bilinmiyor / hata |
| Kullanıcı niyeti | Elle başlatma, elle duraklatma, kaynak seçimi veya otomatik devam |
| Kesinti sahibi | Çağrı, sesli yönlendirme, başka uygulama veya bağlantı kaybı |

BT için iki kurulum ayrılmalı: Vela teypte çalışıp telefondan ses alabilir; veya Vela telefonda çalışıp teybe ses gönderebilir. A2DP ses, AVRCP kumanda/metadata, HFP telefon görüşmesi içindir. Sadece HFP bağlantısı müzik bağlantısı sayılmaz. OEM teyp BT altyapısı standart Android API üzerinden görünmeyebilir; cihaz adaptörü gerekir.

Öncelik sırası: çağrı ve sistemin odak kısıtları → son açık kullanıcı komutu → izin verilmiş otomatik devam → açılış profili. Kaynaklar arasında sabit ve koşulsuz “BT her zaman kazanır” kuralı kullanılmamalı.

## 3. Seçilebilir kullanım profilleri

| Profil | Açılış | Telefon bağlanınca | Kaynak bulunamazsa |
| --- | --- | --- | --- |
| Elle kullanım - ilk kurulum önerisi | Son ekran ve parça hazırlanır; ses başlamaz | Sadece bağlantı gösterilir | Bekle, kullanıcıya oynat/seç seçeneği sun |
| Son kaynaktan devam | Otomatik devam açıksa ve son durum çalıyorsa aynı kaynağı hazırla | Son kaynak BT ise bekleyen geçişi tamamla | Sessiz bekle; isteğe bağlı yedek kaynak |
| BT öncelikli | Medya bağlantısı için önerilen 8 saniye bekle | Otomatik devam koşulları sağlanıyorsa bir oynat isteği gönder | Süre sonunda yalnızca kullanıcı seçmişse yedek kaynak |
| Radyo öncelikli | Otomatik devam açıksa son istasyonu aç | Radyoyu kesme; elle BT seçilirse geç | Radyo yoksa hata göster; yedek kaynak isteğe bağlı |
| Dahili müzik öncelikli | Otomatik devam açıksa son dosya ve konum | Yerel müziği kesme | USB/dosya bekle; kendiliğinden rastgele parça seçme |

8 saniye ürün önerisidir, Android şartı değildir; 3–15 saniye aralığı cihaz kabulünde ayarlanabilir. Otomatik devam, son oturumda kullanıcının duraklatmasını geçersiz kılmaz. Ayrı bir “her açılışta zorla başlat” davranışı ilk sürüme önerilmiyor.

BT önceliği varsayılan olarak açılış/bekleme penceresi içindir. Kullanıcı bu sırada radyo veya müzik seçerse pencere iptal olur. Geç bağlanan telefon seçimi geri alamaz.

## 4. Kaynak seçici ve ekran davranışı

- Küçük müzik kartına dokunma: büyük paneli açar; kaynağı değiştirmez, oynat/duraklat komutu göndermez.
- Büyük müzik panelini veya kütüphane sekmesini açma: görüntüleme işlemidir; ses başlatmaz.
- Kaynak seçicide satıra dokunma: önerilen davranış kaynağı seç ve hazırla. Mevcut müzik çalıyorsa hedef hazır olunca devralıp çal; sistem sessizse hedef duraklatılmış kalır.
- Seçicide ayrıca açık bir “Seç ve çal” eylemi: sessiz durumda da kullanıcı komutuyla başlatır. Böylece kaynağa bakmak ile ses başlatmak ayrılır.
- Aynı kaynağı tekrar seçme: parçayı sıfırlamaz, uygulamayı tekrar açmaz, play/pause toggle göndermez.
- Kütüphaneden parçaya dokunma: açık çalma komutudur; dahili oynatıcıya geçer ve o parçayı başlatır.
- Harici uygulamayı aç düğmesi: yalnız uygulamayı açar. Oynatma ancak gerçek durum/komut sonucuyla gösterilir.
- Radyo seçildiğinde istasyon/frekans ve desteklenen tarama tuşları görünür; şarkı ilerleme çubuğu görünmez.
- BT süre/kapak/seek bilgisi sunmuyorsa bu alanlar gizlenir. Sahte ilerleme veya FFT gösterilmez.
- Çekmece, dock, küçük panel, büyük panel ve direksiyon tuşları aynı etkin kaynak modeline bağlanır.

## 5. Senaryo tablosu

Aşağıdaki tablo önerilen varsayılanlar içindir. Profil istisnaları açıkça yazılmıştır.

| No | Durum / olay | Beklenen davranış |
| --- | --- | --- |
| S01 | İlk kurulum ve ilk açılış | Ses başlatma; kaynak ve izin kurulumu sun |
| S02 | Son oturum dahili müzik çalıyordu, otomatik devam açık | Kütüphane ve odak hazırsa aynı parça/konumdan bir kez devam |
| S03 | Son oturum elle duraklatılmış | Yeniden açılışta sessiz kal |
| S04 | Teyp açıldı, telefon henüz bağlanmadı, BT öncelikli | Bağlantı bekleniyor göster; yerel müziği hemen başlatma |
| S05 | Telefon yalnız görüşme profiliyle bağlandı | Müzik hazır sayma; BT müzik bağlantısını bekle |
| S06 | Medya profili bağlandı, telefonda müzik duruyor | Normal profilde ses başlatma; BT devam profili uygunsa hedefli tek PLAY |
| S07 | Telefon bağlı ve zaten çalıyor, Vela sessiz | Otomatik kaynak takibi açıksa gerçek BT oynatmasını göster; ikinci PLAY gönderme |
| S08 | Radyo çalarken telefon bağlandı | Radyo devam; sadece BT kullanılabilir bilgisi değişir |
| S09 | Dahili müzik çalarken telefon bağlandı | Dahili müzik devam |
| S10 | Radyo çalarken telefondan müzik başlatıldı | Elle kaynak kilidi varsa otomatik geçme; BT'yi durdurma/rota kontrolü ancak doğrulanmış adaptörle. Kontrol yoksa çatışmayı bildir |
| S11 | Kilit yok, harici gerçek oynatma başladı, otomatik takip açık | Tek hedefli geçişle eski kaynağı durdur; yeni gerçek kaynağı göster |
| S12 | Kullanıcı seçiciden BT seçti, bağlantı hazır, müzik çalıyordu | Mevcut kaynağı hedef hazır olduktan sonra durdur; BT başlat ve doğrula |
| S13 | Kullanıcı BT seçti, telefon bağlı değil | İstenen kaynak BT ve bekleme durumu göster; mevcut sesi hazırlık aşamasında koru, “hemen durdur” ayrıca mümkün olsun |
| S14 | BT beklerken kullanıcı duraklat veya başka kaynak seçti | Bekleyen otomatik PLAY iptal; son kullanıcı komutu kazanır |
| S15 | BT bağlantısı bekleme süresi bittikten sonra geldi | Kendiliğinden geçme; kullanıcı yeniden oynatabilir |
| S16 | BT çalarken bağlantı koptu | Durumu bağlantısız yap, varsa devam niyetini kaydet; radyo/yerel müzik başlatma |
| S17 | Kısa kopma sonrası aynı telefon döndü | Önerilen 30 saniyelik devam penceresi, önceden çalıyordu ve yeni kullanıcı komutu yoksa bir devam isteği; tercih kapalıysa sessiz |
| S18 | Başka telefon bağlandı | Önceki telefonun devam niyetini kullanma; tercih edilen cihaz politikasını uygula |
| S19 | BT kesildi, Vela aslında telefonda çalışıyor | Sesin telefon hoparlörüne kaçmasını önlemek için yerel oynatmayı duraklat; rota geri gelince koşullu devam |
| S20 | Kullanıcı radyo seçti | Dahili/harici eski sesi bir kez duraklat; OEM radyo kaynağını seç, istasyonu geri yükle; doğrulama yoksa “durum bilinmiyor” |
| S21 | Radyodan dahili parçaya geçti | Dosyayı hazırla; radyo susturma/rota değişimini doğrula; ardından yerel oynatma |
| S22 | HCN müzik seçildi | Uygulamayı açmakla çalıyor sayma; MediaSession veya OEM durumunu bekle |
| S23 | USB üzerindeki parça çalarken USB çıkarıldı | Oynatmayı durdur, konum ve listeyi koru, dosya erişilemiyor göster |
| S24 | USB geri takıldı | Tara ve dosyayı eşleştir; önerilen varsayılan otomatik ses başlatmamak |
| S25 | Gelen arama | Odak/call sinyaliyle duraklat veya OEM sustur; kesinti öncesi gerçek çalma durumunu kaydet |
| S26 | Arama bitti | Yalnız Vela'nın kesinti nedeniyle durdurduğu aynı kaynak, kullanıcı duraklatmadıysa devam |
| S27 | Arama sırasında kaynak seçildi | Seçimi hatırla, çağrı bitmeden ses başlatma; eski kaynağa geri dönme |
| S28pl | Navigasyon konuştu | Destek varsa geçici duck; podcast/konuşmada tercih edilen duraklatma. Radyo MCU ses yolu ayrıca doğrulanmalı |
| S29 | TTS sürerken kullanıcı ses değiştirdi veya mute yaptı | Eski ses düzeyini zorla geri yazma; kullanıcının yeni seviyesini koru |
| S30 | Kalıcı audio focus kaybı | Duraklat, otomatik geri başlatma niyetini sil; sonraki GAIN tek başına PLAY değildir |
| S31 | Geçici odak kaybı | Geçerli kesinti kaydı varsa ve kullanıcı araya girmediyse devam |
| S32 | Uyku/ACC kapandı | Gerçek cihaz sinyali varsa konumu kaydet, bekleyen geçişleri iptal et; ekran kapanmasını tek başına kontak sayma |
| S33 | Uyanış | Bağlantı ve kaynak durumunu yeniden sorgula; soğuk açılışla aynı komutu iki kere gönderme |
| S34 | Uygulama çöktü / süreç yeniden kuruldu | Kayıtlı tercihi yükle; eski “çalıyor” bilgisini canlı gerçek sayma, odak/rota doğrula |
| S35 | A→B→C hızlı seçim | Eski geçiş sonuçlarını geçiş kimliğiyle reddet; yalnız C etkinleşsin |
| S36 | Radyo hem adaptörde hem MediaSession'da göründü | Tek fiziksel kaynak olarak birleştir; iki PAUSE veya toggle gönderme |
| S37 | Hedef açılmadı veya komuta yanıt vermedi | Hata göster; eski kaynak yalnız Vela durdurduysa ve yeni kullanıcı/çağrı olayı yoksa geri alınabilir |
| S38 | Bildirim erişimi/BT izni sonradan kaldırıldı | İlgili kontrolü devre dışı bırak, durumu bilinmiyor yap; başka kaynağı rastgele oynatma |
| S39 | Yedek geri yüklendi | Tercihler taşınır; aktif çalıyor/odak/izin/BT oturumu taşınmaz, içe aktarma ses başlatmaz |
| S40 | Oynat tuşuna art arda basıldı | Hazırlanan aynı isteği birleştir; çift açılış veya ters toggle olmasın |

30 saniye de ürün önerisidir. Zamanlar monoton saatle ölçülmeli; saat dilimi/sistem saati değişmesi devam penceresini bozmasın.

## 6. Smart Focus uygulama tasarımı

1. Kaynakları sabit sourceId ve mümkünse paket/oturum/cihaz kimliğiyle tanımla. Olayın kaynağı, zamanı ve güvenilirliğini sakla.
2. Adaptör yetenekleri: play, pause, next, previous, seek, durum doğrulama, kaynak seçme, ses yolu yönetimi. Desteklenmeyen düğmeyi gizle/devre dışı bırak.
3. Bir karar merkezi kur: kullanıcı komutu ve sistem olayları seri işlenir. UI doğrudan bağımsız oynatıcı başlatmaz.
4. Geçiş: istek → hedef hazır mı kontrolü → eski kaynağı duraklat/rota değiştir → gerekli odak → hedefi başlat → gerçek durum doğrulaması → etkin kaynak güncellemesi.
5. Hazırlıkta eski sesi koru; hedef devralırken iki kaynak aynı anda duyulmasın. OEM durdurulamıyorsa başarı varsayma. Android dışındaki MCU radyosunda tek ses garantisi adaptör desteğine bağlıdır.
6. Her geçişe kimlik ve süre sınırı ver. İlk öneri hazır kaynağa komut yanıtı için 3 saniye; uygulama açılışı/BT bağlantısı için ayrı pencere. Cihaz ölçümüyle düzelt.
7. Kullanıcı duraklatma ve kaynak değiştirme, eski otomatik devam kayıtlarını iptal eder. Kaynak kilidi kullanıcı başka kaynak seçene veya “otomatik takip”e dönene kadar sürer; kalıcı tercih ayrıca saklanır.
8. Çağrı/TTS/bağlantı kesintileri ayrı kayıtlarla tutulur. İç içe kesintilerde son engel kalkmadan devam etme.
9. Durum bilinmiyorsa bunu göster; metadata gelmesi oynatma onayı değildir. Genel medya yayınlarını BT kanıtı olarak kullanma.
10. BT komutlarını global medya tuşu yerine doğrulanmış oturum/OEM kanalıyla gönder. Aynı cihazın iki adaptöründen gelen olayları birleştir.

Android audio focus Smart Focus'un yerine geçmez: biri sistemin ses paylaşımı, diğeri uygulamanın kaynak politikasıdır. Target 35 ve üzeri uygulamalarda Android 15 üzerinde odak istemek için öndeki uygulama veya uygun foreground service gerekir. Kaynak geçişi bu koşulu sağlamalıdır. [Android audio focus](https://developer.android.com/media/optimize/audio-focus)

## 7. Ayarları birleştirme önerisi

**Evet, tek ayar merkezi daha iyi.** Mevcut Vela kategori yapısı korunarak araç/müzik bölümleri eklenmeli. Launcher'daki dişli aynı merkezin Araç ve Launcher bölümüne, müzik dişlisi Müzik ve Kaynaklar bölümüne gider. Geri tuşu gelinen launcher/harita/panele dönmeli.

Önerilen bölümler:

- Görünüm: ortak tema, dil, yazı; launcher'a özgü arka plan ayrı alt grup.
- Araç ve Launcher: araç modu, dikey/yatay panel, dock, çekmece, masaüstü/widget, varsayılan ana ekran.
- Müzik ve Kaynaklar: Smart Focus profili, kaynaklar, açılışta devam, BT bekleme, kısa kopmada devam, yedek kaynak, radyo adaptörü.
- Ses ve Yönlendirme: TTS, medya kısma/duraklatma, çağrı sonrası devam; mevcut ses kütüphanesi korunur.
- İzinler ve Cihaz Uyumluluğu: izin durumu, neden gerektiği, açılacak sistem ekranı, adaptör yetenekleri.
- Yedekleme ve Başlangıç: dışa/içe aktarma, açılış uygulaması; açılış uygulaması ile müzik otomatik oynatma farklı tercihler.
- Mevcut harita, navigasyon, yerler, çevrimdışı, arama, gizlilik ve tanılama kategorileri korunur.

Birleştirme adımları:

1. Aynı anlamdaki tüm ayarları ve mevcut depolarını listele; iki ayrı tema/dil/otomatik oynatma değeri bırakma.
2. Ortak ayar modeli ve bölüm yönlendirmesi kur. Aynı modelin telefon/dar ekran ve teyp/geniş ekran sunumu farklı olabilir.
3. Önce eski depolara erişen ortak katman kullan; tüm depoları aynı anda taşımak şart değil.
4. Gerekli taşımalara sürüm ve başarı işareti ekle. Yeni değer varsa onu koru; yoksa eski açık kullanıcı seçimini al. Çakışmaları tanımlı öncelikle çöz.
5. Eski girişleri yeni bölüm kısayoluna çevir. Arama, D-pad, geri odağı, ekran dönüşü ve dil değişimini koru.
6. Yedek şemasını güncelle; eski yedekleri oku. Cihaza bağlı widget kimlikleri, izinler ve canlı medya oturumlarını taşınabilir ayar sanma.
7. Eski ekranı ancak tüm alanlar, arama ve geçiş kabulü tamamlanınca kaldır.

## 8. OsmAnd manifest karşılaştırması

Referans kullanılan dosya OsmAnd/AndroidManifest-carlauncher.xml; OsmAnd/build.gradle carlauncher kaynak kümesini buna bağlıyor. Ayrıca src-carlauncher/AndroidManifest.xml içinde WRITE_SECURE_SETTINGS var; bu dosyanın varlığını etkin birleşmiş manifestte izin varmış gibi yorumlamamak gerekir.

| Konu | OsmAnd / Vela durumu | Yapılacak iş |
| --- | --- | --- |
| Ana ekran | OsmAnd bootstrap HOME/DEFAULT; Vela alias'ları LAUNCHER, HOME yok | Vela için isteğe bağlı HOME giriş bileşeni ve geri/yeni intent davranışı tasarla; varsayılan seçimini kullanıcı yapar |
| Medya | İkisinde medya servisi ve bildirim dinleyici var | Dış istemci erişimi, servis yaşam döngüsü, bildirim ve kaynak kontrol yetkisini denetle |
| Mikrofon servisi | OsmAnd microphone FGS ve VoiceCommandService içeriyor; Vela aynı launcher ses servisini taşımamış | Gerçek arka plan mikrofon özelliği yapılırsa bileşen ve izinleri birlikte ekle |
| Depolama | Eski depolama ve READ_MEDIA_AUDIO bildirimleri mevcut | Sürüm sınırlarını ve kullanım anı isteğini koru; eski WRITE iznini modern sürümlere çözüm sayma |
| BT | Vela manifestinde BLUETOOTH_CONNECT yok | Seçilen standart API'lerin gerektirdiği sürüm kontrollü izinleri ekle; tarama yoksa SCAN isteme |
| Overlay / sistem ayarı | Bildirimler mevcut | Sadece gerçekten kullanılan özellikte erişim iste; normal panel için overlay zorunlu olmasın |
| Widget | BIND_APPWIDGET / BIND_REMOTEVIEWS bildirimleri var | Host bind-onay akışını kullan; bildirimlerin otomatik yetki vermediğini dikkate al |
| PiP / yön | OsmAnd bazı ayrı aktiviteleri sabit yönlü; Vela tek host ve PiP destekli | Sabit yönleri kopyalama; mevcut ölçüye dayalı yerleşimi ve PiP içeriğini doğrula |
| Boot | Vela'da boot receiver yok | Önce varsayılan launcher/OEM başlangıç yolu; boot receiver tek başına müziği başlatma çözümü değildir |
| Ağ / FileProvider | OsmAnd cleartext açık; Vela networkSecurityConfig ve FileProvider kullanıyor | Genel cleartext açma; mevcut ağ politikası, authorities ve dar URI erişimini koru |

API 29+ için HOME rolü kullanıcı onayıyla istenir; API 26–28 için uyumlu varsayılan ana ekran ayar yolu gerekir. [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)

Target 35 uygulamalar Android 15'te BOOT_COMPLETED içinden mediaPlayback foreground service başlatamaz. Bu yüzden “boot geldi, servisi aç, çal” planı uygun değildir. [Android 15 servis kısıtları](https://developer.android.com/about/versions/15/changes/foreground-service-types)

## 9. İzinlerin otomatik verilmesi

Normal uygulama kendi kendine bütün izinlerini açamaz. Ama kullanıcıyı doğru sistem ekranına götüren, dönüşte sonucu kontrol eden ve eksik özelliği açıklayan tek bir kurulum akışı yapılabilir.

| İzin / erişim | Olağan kurulumda yol | Reddedilince |
| --- | --- | --- |
| INTERNET, ağ durumu, VIBRATE, uygun normal FGS izinleri | Bildirimle sistem verir; servis başlatma koşulları ayrıca geçerli | İlgili yetenek doğrulanır |
| Ses dosyaları | API 33+ READ_MEDIA_AUDIO; eski sürümde uygun READ_EXTERNAL_STORAGE kullanıcı isteği | Yerel kütüphane kapalı; dış kaynaklar kullanılabilir |
| RECORD_AUDIO | Mikrofon/gerçek visualizer gerektiğinde kullanıcı onayı | Sesli giriş/FFT kapalı; müzik oynatma devam |
| Konum | Özellik anında coarse/fine kullanıcı seçimi | İlgili navigasyon yeteneği kısıtlı; launcher çalışır |
| READ_CONTACTS | Kişi araması kullanılırken kullanıcı onayı | Kişi araması kapalı |
| POST_NOTIFICATIONS | API 33+ kullanıcı onayı; bildirim erişiminden farklı | Platformun FGS bildirim kuralları ayrıca uygulanır |
| BLUETOOTH_CONNECT | API 31+ ilgili bağlantı API'leri için kullanıcı onayı | BT bağlantı kontrolü kullanılamaz; diğer müzik kaynakları çalışır |
| SYSTEM_ALERT_WINDOW | Özel sistem erişim ekranı | Yüzen pencere kapalı |
| WRITE_SETTINGS | Özel erişim ekranı ve canWrite kontrolü | Sistem ayarı değiştirme kapalı |
| Bildirim dinleyici erişimi | Kullanıcı sistemde Vela dinleyicisini açar | Harici MediaSession keşfi kısıtlı; dahili müzik çalışır |
| Widget bağlama | Önceden izin varsa bind; yoksa sistem widget onayı | Widget ekleme iptal edilir |
| REQUEST_INSTALL_PACKAGES | Bu kaynaktan yüklemeye izin + yükleme onayı | Harici APK kurulumu yapılamaz |
| QUERY_ALL_PACKAGES | Runtime diyaloğu değildir; kapsam ihtiyacı değerlendirilir | LAUNCHER queries yeterliyse geniş görünürlük kaldırılabilir |
| BIND_REMOTEVIEWS / servis BIND izinleri | Korunan bağlama sözleşmeleri; sıradan runtime izin değildir | Manifest satırı ile elde edilmiş sayılmaz |
| WRITE_SECURE_SETTINGS | Sıradan uygulamanın kullanıcı diyaloğuyla alacağı izin değildir | Standart ayar ekranları kullanılır |

Tehlikeli izinlerin onayı kullanım anında istenir; zaten verilmişse tekrar sorulmaz. [Runtime izinler](https://developer.android.com/training/permissions/requesting)
Overlay ve sistem ayarı gibi özel erişimler ayrı sistem akışı gerektirir; dönüşte yeniden kontrol edilir. [Özel izinler](https://developer.android.com/training/permissions/requesting-special)
BT izinleri kullanılan API ve sürüme göre seçilmelidir; bağlantı izni evrensel AVRCP/OEM kontrolü sağlamaz. [Bluetooth izinleri](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
Widget bağlama izni yoksa sistem onay ekranı gerekir. [Widget host](https://developer.android.com/develop/ui/views/appwidgets/host)
Korunan izinler ve bildirimlerin anlamı için [Manifest permission referansı](https://developer.android.com/reference/android/Manifest.permission). tools:ignore yalnız denetim uyarısını susturur, yetki vermez.

### Kurulum türüne göre otomasyon

- Normal APK: önerilen standart. İzin kontrolü, gerekçe, kullanıcı işlemi, dönüşte doğrulama; reddi sürekli yeniden sorma. İzin kaldırılınca özellik güvenli biçimde kapanır.
- ADB ile kişisel teyp kurulumu: ayrıca istenirse cihaz/sürüm/paket doğrulayan yardımcı araç planlanabilir. Yalnız desteklenen grant/app-op işlemleri, öncesi-sonrası kontrol ve geri alma kaydı. Her iznin pm grant ile verilebileceği varsayılmaz. Bu belgede cihazda komut çalıştırılmadı.
- Yönetilen cihaz: önceden uygun biçimde provision edilmiş device/profile owner belirli runtime izinlerini yönetebilir. Sensör ve yönetim kapsamı sınırlamaları vardır; genel özel erişim açma yöntemi değildir. [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- OEM/sistem uygulaması: üretici entegrasyonu, uygun imza/kurulum ve izin türüne göre allowlist gerektirir. APK'yı sıradan kurmakla kazanılmaz. [Privileged izinler](https://source.android.com/docs/core/permissions/perms-allowlist)
- Root: ilk sürümün gereksinimi veya varsayılan otomatik yolu yapılmamalı. Belirli cihaz için ayrıca kapsamlandırılmalı.

İzin merkezi her satırda “verildi / gerekli / desteklenmiyor / kullanıcı kapattı” durumunu gösterir. Yedekten izin verilmiş bayrağı yüklenmez; gerçek sistem durumu okunur.

## 10. Uygulama sırası ve kabul ölçütleri

| Öncelik | İş | Bağımlılık / bitiş koşulu |
| --- | --- | --- |
| P0-1 | Bu belgedeki profil ve seçici kararları | Aşağıdaki karar alanları netleşir |
| P0-2 | Kaynak kimliği, yetenek ve olay modeli | BT/radyo/MediaSession çift görünümü tek kaynağa iner; eski olay reddedilir |
| P0-3 | Smart Focus karar merkezi | S08–S16, S25–S31, S35–S40 geçer; elle pause bozulmaz |
| P0-4 | BT ve OEM adaptör doğrulaması | HCN/XYAuto gerçek cihazda bağlantı, rota, play/pause/istasyon kanıtlanır |
| P0-5 | Ortak ayar merkezi ve geçiş katmanı | İki giriş aynı değeri okur/yazar; eski tercihler korunur; geri ve D-pad çalışır |
| P1-1 | İzin merkezi ve manifest sadeleştirme | API 26/30/31/33/34/35/36 izin verme, ret, iptal senaryoları geçer |
| P1-2 | Açılış, uyku/uyanma ve HOME akışı | Soğuk açılış ile OEM resume ayrılır; çift PLAY yok; geç BT seçimi çalmaz |
| P1-3 | Müzik ve yardımcı özellik kabulü | USB, listeler, widget iptali/provider, yedek gidiş-dönüş, PiP ve açılış uygulaması |
| P1-4 | Dikey/yatay arayüz kabulü | Küçük/büyük panel, çekmece, dock ve ayarlar dönmede taşmaz; seçili sekme korunur |
| P2-1 | Gerçek hava ve araç verileri | Sağlayıcı, yenileme, çevrimdışı durum, gerçek veri yok mesajı |
| P2-2 | Sesli komut yönlendirmesi | Müzik komutları aynı Smart Focus merkezine gider; mikrofon yaşam döngüsü |
| P2-3 | Masaüstü görünüm seçenekleri | Parallax, ek saat/hız görünümleri; önce temel widget kabulü |
| P2-4 | Anten canlı GPS/pusula | Sensör doğruluğu ve izin akışı; mevcut elle koordinat hesabından ayrı |

P0-2/3 altyapısı ve P0-5 ayar tasarımı birbirini engellemeden hazırlanabilir; son birleştirme aynı ayar sözleşmesine bağlıdır. Kullanıcının önceki 2 ve 5 önceliği müzik ve yardımcı özellikler olarak korunmuştur.

Test planı: saf karar merkezi için olay dizisi testleri; ardından gerçek cihazda kaynak geçişleri. Yazılım kontrollerinde XML, kaynak tutarlılığı ve git diff kontrolü. Bu belgede Gradle çalıştırılmadı, uygulama derlenmedi, cihaz kabulü yapılmadı.
Tanılama: olay zamanı, anonim kaynak kimliği, komut/yanıt, geçiş nedeni ve yetenek durumu. Telefon numarası, kişi listesi ve kalıcı BT adresini gereksiz loglama.

## 11. Üzerinde çalışılacak kararlar

- [ ] İlk kurulum profili: Elle / Son kaynak / BT öncelikli / Radyo / Dahili. Önerim: Elle.
- [ ] Otomatik devam kullanıcı açınca son gerçek çalma durumunu izlesin mi? Önerim: Evet.
- [ ] Seçici satırı: çalan sesi devralsın, sessiz durumda sadece hazırlasın mı? Önerim: Evet; ayrı Seç ve çal olsun.
- [ ] BT bekleme: öneri 8 saniye. Benim tercihim: __________
- [ ] Kısa BT kopmasında devam: açık/kapalı; öneri isteğe bağlı, 30 saniye. Benim tercihim: __________
- [ ] BT yoksa yedek kaynak: Yok / Radyo / Dahili. Önerim: Yok.
- [ ] Kullanıcı başka kaynak seçince BT otomatik geçiş kilitlensin mi? Önerim: Evet.
- [ ] Harici gerçek oynatmayı otomatik takip: açık/kapalı. Önerim: Elle kilit yokken isteğe bağlı.
- [ ] Tek ayar merkezi ve launcher bölüm kısayolu: önerim Evet.
- [ ] Kurulum türü: Normal APK / kişisel ADB kurulumu / yönetilen cihaz / OEM.
- [ ] Vela nerede çalışıyor: Teyp / Telefon / İkisi de.
- [ ] Teyp marka-modeli, Android sürümü, HCN/XYAuto paketleri: __________

Notlarım ve değiştirmek istediğim senaryolar:

> S__ :
>
> İstediğim davranış:
>
> Gerekçem:

## 12. Yerel inceleme kaynakları

- Vela: app/src/main/AndroidManifest.xml ve app/build.gradle.kts.
- Vela: app/src/main/java/app/vela/carlauncher/media/MusicManager.kt.
- Vela: aynı media/adapters altında UniversalBluetoothAdapter.kt ve HeadUnitMediaAdapters.kt.
- Vela: ui/settings/SettingsScreen.kt, SettingsHub.kt, sections/AppearanceSettings.kt; carlauncher/ui/CarLauncherSettingsView.kt.
- Vela: carlauncher/tools/LauncherStartup.kt ve LauncherBackup.kt.
- OsmAnd: OsmAnd/AndroidManifest-carlauncher.xml, OsmAnd/build.gradle, src-carlauncher içindeki music/MusicManager.java.
- Önceki iş listesi: [Car Launcher aktarım planı](CAR_LAUNCHER_TRANSFER_PLAN.md).

## 13. Uygulama güncellemesi - 24 Eylül 2026

Smart Focus OsmAnd müzik oynatıcısında zaten vardı; bu çalışma Vela aktarımını geliştirdi. Bölüm 1, uygulama öncesi inceleme kaydıdır. İlk görünüm artık kapak/parça/görselleştirici; playlist düğmeyle açılır.
Elle kaynak kilidi, ayrı Seç ve çal, gerçek MediaSession gözleme, OEM BT komutları, ortak ayar merkezi, izin durum ekranı ve HOME rolü eklendi. Son çalınanlar, en çok dinlenenler ve beş sıralama seçeneği aktarıldı.
Planın tamamı bitmiş değildir. Uygulananlar, kısmi kalanlar, donanım kabulü ve sonraki sıra [güncel özellik karşılaştırmasında](MUSIC_FEATURE_PARITY.md) kayıtlıdır.
