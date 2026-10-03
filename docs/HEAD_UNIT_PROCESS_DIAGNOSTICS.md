# Teyp: elle müzik tarama ve süreç teşhisi

## Uygulanan davranış
- İlk açılış, müzik panelinin açılması, MediaStore değişikliği ve USB takılması tam tarama başlatmaz.
- Kayıtlı indeks IO kuyruğundan yüklenir. İndeks yoksa liste Tara komutuna kadar boştur.
- Tara / yenile düğmesi izin gerekiyorsa ister ve taramayı başlatır. Aynı anda iki tarama çalışmaz.
- USB çıkarılırken kaybolan dosyanın oynatılması duraklatılır. Yeni USB içeriği için Tara gerekir.
- Silinen parça indeksten kaldırılır; silme otomatik disk taraması başlatmaz.

## OsmAnd karşılaştırması
OsmAnd MusicManager indeks hazır dinleyicisiyle kayıtlı listeyi yükler ve InternalPlayer durumunu geri getirir. MusicRepository tek IO executor ve tarama durumu kullanır. Açılış taraması auto-scan ayarına ve son başarılı tarama zamanına bağlıdır. Vela da kayıtlı indeks + seri IO tarama yaklaşımını kullanır; kullanıcının isteği doğrultusunda otomatik tarama tümüyle kapalıdır. Harita motorları ve yaşam döngüleri farklıdır; OsmAnd'ın kararlı olması Vela'nın MapLibre/EGL katmanının aynı şekilde davranacağını kanıtlamaz.

## Üç uygulamanın manifest karşılaştırması
| Alan | OsmAnd Car Launcher | CoMaps Auto V2 | Vela car |
|---|---|---|---|
| Paket kimliği | net.osmand.carlauncher | app.comaps.auto; debug için .debug eki | app.vela; appId derleme özelliğiyle değişebilir |
| Home giriş | BootstrapActivity, singleTask | BootstrapActivity, singleTask | CarHome alias → MainActivity, singleTask |
| Medya servisi | MediaBrowserService | MediaBrowserService | MediaBrowserService |
| Bildirim dinleyicisi | BIND_NOTIFICATION_LISTENER_SERVICE | Aynı sistem izni | Aynı sistem izni |
| FileProvider | applicationId.fileprovider | applicationId tabanlı derleme placeholder | applicationId.fileprovider |

Kaynak manifestlerde ortak sharedUserId bulunmadı. OsmAnd ana manifestinde restart gibi yardımcı süreçler var; bunlar Vela/CoMaps ile paylaşılan bir süreç tanımı değil. Aynı kod isimleri ayrı paketlerde çakışmaz; veriler ve bildirim kimlikleri paket kapsamında ayrıdır. Home düğmesi sistemin seçili varsayılan launcher'ını açar. Gerçek APK kimliği derleme varyantına göre doğrulanmalıdır.

Vela'nın kullanılmayan MEDIA_BUTTON servis intent filtresi kaldırıldı; direksiyon/medya tuşları mevcut MediaSession callback'inde işlenir. OsmAnd ve CoMaps manifestleri de yalnızca MediaBrowserService filtresi kullanır.

Vela artık keşfedilen .carlauncher.media.CarMediaService servislerini dış kaynak seçiminde ve medya bildirimi yenilemesinde dışlar. Böylece diğer launcher'ın yansıtılmış oturumunu takip etmez ve Smart Focus onu duraklatmaya çalışmaz. Diğer iki uygulamanın kodu değiştirilmedi; onların Vela'yı takip etmesi ayrıca kendi projelerinde ele alınabilir. OEM Bluetooth/radyo yayınları cihaz protokolüdür; başka isimle değiştirilmedi. Üç uygulamanın arka plan servislerini çalıştırması düşük bellekli teypte yükü artırabilir; birlikte kurulu olmak tek başına çökme kanıtı değildir.

## Daha ayrıntılı rapor
- Java exception raporuna sürüm/PID, son işlem, Activity durumu, Java/native heap, kullanılabilir bellek ve ana iş parçacığı yanıt süresi eklenir.
- Dahili diag/process-session.json yaklaşık 5 saniyede bir atomik yazılır; işlem değişiklikleri kısa gecikmeyle birleştirilir.
- Sonraki süreç açılışında önceki süreç açık kapanma komutu olmadan bittiyse önceki durum raporlanır. Bu rapor normal sistem/OEM süreç sonlandırmasını da kapsar; kesin çökme olarak etiketlenmez.
- API 30+ cihazlarda Android'in eşleşen geçmiş süreç çıkış kaydı eklenir. API 27 teypte bu API yoktur; kesin native SIGSEGV/LMK nedeni için sistem logcat/bugreport gerekir.
- Uygulama görünürken ana iş parçacığı 15 saniye yanıt vermezse bir kez sınırlı thread stack raporu yazılır. Bu gözlem sistemin doğruladığı ANR değildir.
- Raporlar Ayarlar → Diagnostics üzerinden mevcut rapor paylaşma akışıyla dışa aktarılır. Kopyaları uygulamanın external files/logs klasörüne yazılır; iki konumda son beş rapor tutulur.
- Ayarlardan açık kapatma raporda kaydedilir. Home launcher yeniden başlatma aynı süreç içinde Activity'yi yeniler.

## Son logun sınırı
vela_app (6).log içinde indeks 3767 parça / 59 klasörle başarıyla yükleniyor, sonra otomatik tarama başlıyor. Daha sonra yeni süreç açılışları var; Java fatal exception yok. Son satırın tarama olması taramanın kesin neden olduğunu göstermez. Önceki cache başlatma hatası ve foreground bildirim gecikmesi bu logda görülmüyor. Yeni raporla harita/native player/bellek/süreç sonlandırma ayrımı yapılacak.

## Doğrulama
Gradle çalıştırılmadı ve emülatör kullanılmadı. XML, kaynak çağrı yolları, encoding ve git diff kontrolleri yapılır. Teypte ilk açılış, Tara, Home dönüşü, USB çıkar/tak ve açık kapatma ayrıca doğrulanmalıdır.
