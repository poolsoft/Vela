# Emülatör: çevrimdışı arama ve yedek testi

## Ortam

- OsmAndAuto_LowRam_API30_x86, emulator-5554, API 30, x86.
- Actions 38775559068 çıktısı 0.4.116-diagnostic (2216), mevcut kurulum üzerine veri silmeden kuruldu.
- emulator-5556 üzerindeki diğer çalışmaya dokunulmadı.
- Wi-Fi ve mobil veri kapalı; son kontrolde aktif varsayılan ağ yok.

## Doğrulananlar

- Mevcut tr-guney.db paketiyle restoran kategorisinde çevrimdışı 30 sonuç gösterildi.
- Kaynak yerel POI/adres indekslerine kontrollü test kaydı eklendi; aramada bulundu.
- Uygulamanın gerçek Yedekle ekranı ve Android klasör seçicisiyle Yerler/Favoriler kapsamı dışarı aktarıldı.
- Manifest içindeki yeni arama paketinin SHA-256 değeri doğrulandı. Pakette POI, adres ve sokak örneği bulundu.
- Aynı yedek gerçek Geri Yükle ekranından hazırlandı. Kaynak yerel indeksler orijinal boş durumuna getirildi; yeniden açılış geri yüklemeyi başarıyla tamamladı.
- Test kaydı yalnız geri yüklenen paketten tekrar bulundu: 1 sonuç. Böylece yerel indekslerin taşınması Android üzerinde doğrulandı.
- Vela varsayılan HOME olarak kaldı; APP_MAPS kategori sorgusunda MainActivity bulundu.
- Son açılışta arama kendiliğinden açılmadı; harita çevrimdışı çizildi.

## Temizlik ve sınırlar

- Test paketi kaldırıldı; orijinal yerel veritabanları/journal dosyaları ve launcher tercihleri geri kondu. Asıl tr-guney.db korundu.
- Emülatör Vela açık ve çevrimdışı bırakıldı. Test yedeği ayrı test klasöründe duruyor.
- Yön değişimi komutları ekranı gerçekten döndürmedi. Süreç kimliklerinin değişmemesi bu nedenle yön değişimi testi olarak sayılmadı; sonuç dosyası not_verified olarak düzeltildi.
- Bazı POI simgeleri hâlâ siyah/gri. Bu test o görsel sorunu çözmüyor.
- Bu test eski yedekte bulunmayan veriyi üretemez. Kaynak cihazda yeni sürümle yeniden yedek almak gerekir; kaynakta arama verisi yoksa ayrıca arama paketi gerekir.
- Fiziksel teybin sürücü, bellek ve süreç davranışı bu emülatör sonucu ile doğrulanmış değildir.

Kanıtlar yerel `.logs/emulator-search-2026-10-15/` altında: restaurants.png, restored-ui.xml, snapshot-result.json, exported-backup ve final.png. Test bu kapsamda kod değişikliği gerektirmedi.
