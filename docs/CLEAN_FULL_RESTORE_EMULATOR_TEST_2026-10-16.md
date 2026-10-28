# Temiz veriyle tam geri yükleme testi — 16 Ekim 2026

## Ortam ve hazırlık

- Açık emülatör: emulator-5554, x86 / Android API 30, GLES 2.0 olarak raporlanıyor.
- Kurulan APK: 0.4.126-diagnostic (2226), kaynak 346c6d2e. GitHub Actions 38862874969 başarılı; x86 APK diagnostic-126 sürümünden indirildi.
- Kullanıcı onayıyla yalnız `app.vela` verileri `pm clear` ile temizlendi. Diğer uygulamalar ve ortak depolamadaki yedek korunmuştur.
- İlk kurulum tamamlandı; sistem sesi seçildi. Wi-Fi ve mobil veri kapatıldı; test sonunda aktif varsayılan ağ yok.
- Kullanılan tam yedek: `/storage/emulated/0/VelaSearchTestk /Vela-backup-20261016-051214`.
- Yedek önce bilgisayarda `.logs/restore-repro-20261016/backup` altına kopyalandı. 784 dosya / 418568027 bayt; tüm dosyaların boyut ve SHA-256 değerleri manifestle eşleşti. İki PMTiles dosyasının bölüm sınırları da kontrol edildi.

## Uygulamanın kendi arayüzüyle geri yükleme

Ayarlar → Backup and Restore → Restore Backup üzerinden Android klasör seçiciyle tam yedek seçildi. Seçili kategoriler daraltılmadı. Haritalar, glyphs/sprites/overlays, OBF, places/poipacks ve kişisel/launcher ayarları aktarıldı.

Aktarım sırasında canlı `files/basemap` klasörü henüz yoktu; dosyalar yalnız staging alanındaydı. Uygulama logunda iki harita için SHA-256 ve bölüm sınırı doğrulaması görüldü. Sonunda `Restore staged: files=784` kaydı oluştu. Aktarım yaklaşık 3 dakika 28 saniye sürdü; bu bilgisayar/emülatör/SAF depolama ölçümüdür, teyp hızı değildir.

“Restart Vela” düğmesiyle uygulama yeniden başlatıldı; elle ikinci kez açmak gerekmedi. `portable-restore-result=success`, diagnostic kaydı `result=success,error=""`. Ana süreç 16225 → 17913, yeni renderer 17986 olarak açıldı.

## Sonuç kontrolleri

- Canlı dosyalarda 782 veri dosyası bulundu: tamamının SHA-256 değeri yedekle aynı, eksik/değişmiş dosya yok. Diğer iki JSON, ayar aktarımı içindir; kurulum bunları tüketir.
- `tr-guney.pmtiles`: 215541566 bayt; `world.pmtiles`: 10960375 bayt. Bu yedekteki world dosyasının boyutu eski teyp logundaki world ile aynı değildir; iki ayrı dosyayı birbirine eşit varsaymadık.
- Harita internet kapalıyken çizildi; ilk görüntüde yollar, yazılar ve renkli simgeler görüldü.
- OfflineSearch: `packFiles=[tr-guney.db] opened=1`. Restoran kategorisi çevrimdışı olarak 30 sonuç verdi; “paket indir” hatası çıkmadı.
- Çekmece üç kez açılıp kapatıldı; büyük müzik paneli açılıp kapatıldı; Android ayarları açılarak arka plana gidildi ve Vela'ya dönüldü. Ana/renderer PID ve renderer session değişmedi. Açılış başına tek paket taraması görüldü.
- Test girişlerinde ADB için `input -d 0` kullanmak gerekliydi. Varsayılan display ile ilk denemeler renderer'ın sanal ekranındaki katman menüsüne gitti. Bu aşamada iki style yüklemesi görüldü; bunları çekmece/panel yeniden yüklemesi olarak değerlendirmedik. Doğru display ile yapılan geçişlerde (cihaz log saati 07:09 sonrası) yeni style-loading kaydı yok.
- Müzik kütüphanesi temiz kurulumdan sonra taranmadı; müzik panelinin açılması doğrulandı, parça çalma doğrulanmadı.

## Kalan somut bulgu

Çökme veya kalıcı tıklanamama tekrarlanmadı; fakat “hiç takılma yok” denemez. Watchdog, yedek yüklenmeden önce 3056 ms (ViewModel/Hilt oluşturma) ve 3011 ms (Compose/Chip ölçümü), geri yükleme sonrası açılışta 3043 ms (ViewModel/Hilt oluşturma) ana iş parçacığı beklemesi kaydetti. Bunlar sistem tarafından doğrulanmış ANR değildir; uygulama devam etti. Bu beklemelerin boş veriyle de görülmesi, bu testteki açılış yükünün yalnız bozuk haritadan kaynaklanmadığını gösterir. Soğuk açılışta ilk tam harita frame'i ana sürecin başlangıcından yaklaşık 47 saniye sonra görüldü; düşük kaynaklı cihazlar için açılış yükü hâlâ incelenmelidir.

Bu sonuç emülatörde bulunan tam yedek içindir. Eski başarısız aktarımın birebir aynı USB/yedek olduğu veya gerçek teybin kilitlenmesinin çözüldüğü kanıtlanmış değildir.

## Kanıt dosyaları

`.logs/restore-repro-20261016/` altında `backup-verification.json`, `installed-verification.json`, `installed-sha256.txt`, `ready-manifest.json`, `test-result.json`, `final-logs/`, `restore-ready.png`, `restored-home.png`, `music-after-restore.png`, `offline-search-result.png`, `final-home.png` saklandı. APK ve yedek Git'e eklenmedi. Emülatör Vela'nın ana ekranında, veriler geri yüklenmiş ve internet kapalı bırakıldı.
