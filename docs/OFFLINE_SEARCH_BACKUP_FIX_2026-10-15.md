# Tam yedekte çevrimdışı arama verisi

## Bulgular

Kullanıcının ekran görüntüsünde harita çiziliyor, fakat arama “Download an area” uyarısı veriyor. Bu mesaj okunabilir yer/adres verisi bulunmadığı kontrolden geliyor; haritanın PMTiles dosyası ayrı bir kaynak.

Kod incelemesinde iki yedekleme yolunun da `files/poipacks` klasörünü aldığı, ancak cihazdaki `databases/vela_offline_pois.db` ve `databases/vela_offline_addr.db` indekslerini taşımadığı bulundu. Kaynak cihazın araması bu indekslerden çalışıyorsa tam yedek alınması bunları hedef cihaza taşımıyordu. Kullanıcının eski yedek dosyası incelenmediğinden bu bulgu o yedeğin içeriğini kesin olarak kanıtlamaz.

## Değişiklik

- SQLite ATTACH ve SELECT ile kaynak indekslerin tutarlı kopyası alınır; canlı .db dosyası ham kopyalanmaz. WAL içindeki commit edilmiş kayıtlar da okunur.
- POI, adres ve sokak örnekleri mevcut poipacks şemasına aktarılır. Kaynak/hedef kayıt sayıları kontrol edilir. FTS5 zorunluluğu yoktur.
- Klasör tabanlı taşınabilir yedek ve eski ZIP yedeğine bu paket eklenir. Geri yükleme mevcut poipacks yolunu kullanır.
- Yerel POI önbelleği açılamadığında sağlam bölge paketleri sorgulanmaya devam eder.
- Paket açma hataları dosya adı ve nedenle OfflineSearch loguna yazılır. Paket açılamaması kullanıcıya veri yok mesajından ayrı bildirilir. Yerel indeks/harita ayrımı Türkçe uyarıda açıklanır.

## Kullanım

Yeni APK hem kaynak cihaza hem teybe kurulmalı. Kaynakta yerel arama verisi varsa yeni tam yedek alınmalı, teybe aktarılıp geri yükleme tamamlanmalı. Eski yedekte olmayan arama verisi uygulama güncellemesiyle üretilemez. Kaynak cihazda da yalnız harita varsa Yerler/POI verisi ayrıca kurulmalı.

## Doğrulama

- scripts/check_offline_search_snapshot.py gerçek Kotlin SQL ifadelerini canlı WAL veritabanlarıyla çalıştırır: Türkçe POI adı, aynı normalize sokak adına sahip farklı yazımlar, adres/sokak bağlantısı ve SQLite bütünlük kontrolü geçti.
- Çeviri kontrolü geçti (mevcut diğer dil eksikleri İngilizceye düşüyor).
- Actions 38775559068 başarılı: APK derlemesi, çekirdek ve renderer regresyon testleri geçti.
- Yayımlanan APK: 0.4.116-diagnostic, https://github.com/poolsoft/Vela/releases/tag/diagnostic-116
- Uygulama değişikliği: 038655d0. Fiziksel teyp ve gerçek SAF/USB geri yükleme bu oturumda doğrulanmadı.
