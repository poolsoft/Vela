# Vela çevrimdışı yedek ve aktarım

## Amaç ve kullanım

Araç teybi internete bağlanamadığında, Vela'nın çevrimdışı verisini bir telefon veya emülatörde indirip USB bellek ya da yerel dosya aktarımıyla teybe taşımak. Aynı Vela uygulaması iki cihazda da kurulu olmalı. Aktarım için ağ, bulut hesabı veya geniş depolama izni gerekmez.

1. Kaynak cihazda gereken bölgeleri indirin. İndirmelerin ve devam eden yolculuk kaydının bitmesini bekleyin.
2. **Ayarlar → Çevrimdışı yedekle ve yükle → Tam yedek oluştur** bölümünden USB veya yerel klasör seçin. Uygulama `Vela-backup-YYYYMMDD-HHMMSS` adında yeni bir alt klasör oluşturur. Ekranda işlem tamamlandı yazana kadar klasörü çıkarmayın.
3. Bu alt klasörün **tamamını** teybe taşıyın. `vela-backup.json` ve `part-0000001.bin` benzeri tüm parçalar aynı klasörde kalmalı. Tek bir dosyanın üst sınırı 256 MiB; bu FAT32 USB belleklerde de kullanılabilir.
4. Teypte **Ayarlar → Çevrimdışı yedekle ve yükle → Yedek klasörünü yükle** ile doğrudan `Vela-backup-...` klasörünü seçin. İçerik, boyut ve değiştirilecek veri gruplarını inceleyin. **Doğrula ve hazırla** tüm parçaları okuyup SHA-256 bütünlüğünü kontrol eder.
5. **Vela'yı şimdi kapat** düğmesine basıp uygulamayı yeniden açın. Kurulum açılışta, harita ve ayar okuyucuları başlamadan tamamlanır. Favorilerde ve çevrimdışı haritada örnek bir konumu kontrol edin. Yükleme kesilirse önceki veriler geri getirilir; sonuç Ayarlar ekranında görünür.

İçe yükleme aynı gruptaki hedef veriyi değiştirir. Hedefte korunması gereken kayıtlar varsa önce ondan da yedek alın. Yedeklenen grup kaynakta boşsa hedefte de boş olur. Kaynakta hiç bulunmayan bir veri grubu hedefte kalır.

## Kapsam

- Çevrimdışı PMTiles haritaları (`basemap`), işletmeler/yerler (`places`), bina katmanları (`overlays`) ve yerel harita yazı tipleri/sprite dosyaları.
- OBF çevrimdışı rota grafikleri ve bölge indeksleri (`obf`), çevrimdışı POI/adres veritabanları (`poipacks`).
- Kayıtlı yolculuk/rota tekrar dosyaları (`trips`). Çalışmakta olan navigasyon otomatik devam ettirilmez.
- Favoriler, kayıtlı yer listeleri, Ev/İş kısayolları, park konumu/geçmişi, son aramalar ve son görüntülenen yerler.
- Launcher düzeni ve taşınabilir ayarlar; Smart Focus tercihleri, çalma listelerinin tanımları. Android widget kimlikleri cihazda kalır.
- İndirilmiş Piper ve ASR ses modelleri, seçili ses tercihi ve kaçınılacak yol türleri.

**Şimdiki sınırlar:** Eski MapLibre “kayıtlı alan” veritabanı (`.mapbox`/`mbgl-offline.db`) ile çevrimiçi ortam önbelleği, harici müzik dosyaları, diğer uygulamaların verileri, cihaz izinleri ve Bluetooth eşleşmeleri aktarılmaz. Kaynak uygulamadaki eski kayıtlı alanlar gerekiyorsa kaynak cihazda yeni çevrimdışı bölge PMTiles indirmesini yapıp tekrar yedek alın. Çalma listelerindeki medya URI'leri hedefte farklı olabileceğinden müzik dosyalarını hedefe ayrıca kopyalayıp taratın.

## İşleyiş ve doğrulama

Yedek, her dosyanın göreli yolu, uzunluğu, SHA-256 özeti ve parça adını içeren sürümlü bir manifest kullanır. Manifest en son yazılır; eksik klasör yüklenemez. İçe yükleme yalnızca izin verilen uygulama veri klasörlerine yapılır, yol kaçışı ve yinelenen parça adları reddedilir. Yerel depolama yeterliliği önceden kontrol edilir. Dosyalar doğrulanırken mevcut veri değişmez. Açılıştaki kurulum günlüklenir; güç kesintisinde sonraki açılış önceki verileri geri getirir.

Elle kabul testi: Telefonda bir PMTiles bölgesi, OBF bölgesi, POI paketi, favori, kayıtlı liste, yolculuk ve ses modeli oluşturun. Yedeği USB ile çevrimdışı teybe taşıyın; sayıları ve harita çizimini karşılaştırın. Bir parçayı silerek bozuk yedeğin reddedildiğini ve önceki verinin korunduğunu; doğrulama sırasında iptalin temizlendiğini; açılış kurulumunun ardından ikinci açılışta tekrar yükleme yapılmadığını kontrol edin. FAT32 bellek ve dar depolamalı teyp üzerinde ayrıca deneyin.

Bu değişiklikte Gradle derlemesi ve cihaz testleri çalıştırılmadı; repo talimatı Gradle çalıştırmayı yasaklıyor. Bunlar araç teybindeki kabul testinin bir parçasıdır.

## Sonraki iyileştirmeler

1. Eski MapLibre kayıtlı alanlarını kaynak veritabanının tutarlı anlık görüntüsü ve `mergeOfflineRegions` üzerinden taşımak; bunu ayrı bir uyumluluk testi ile yapmak.
2. Hedefte mevcut favori ve kayıtlı listeler için “birleştir” seçeneği ve yükleme öncesi daha ayrıntılı karşılaştırma.
3. Kaynak/yükleme aşamasındaki işlemleri gerçek cihazlarda, özellikle düşük depolama ve güç kesilmesi durumunda sınamak; hata ve süre ölçümlerine göre ayarlamak.
