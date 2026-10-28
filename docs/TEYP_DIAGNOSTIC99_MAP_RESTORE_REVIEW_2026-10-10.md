# Teyp 99: çevrimdışı harita ve yeniden başlatma

## Log bulguları

Kaynak: `.logs` kökündeki 10 Ekim 2026 kayıtları, sürüm 0.4.99-diagnostic (2099).

- 12:35:37: `tiles.openfreemap.org` DNS çözümlemesi başarısız. Bu ilk denemede çevrimiçi stil seçilmiş ve bağlantı yok.
- 12:43:06 sonrasında `tr-guney.pmtiles`, ardından `world.pmtiles` bulunmuş. Font paketi de kurulu olarak algılanmış. Bu, en azından ilgili yedek harita dosyalarının görüldüğünü doğrular; bütün yedek içeriğinin doğrulandığı anlamına gelmez.
- 12:43:23, 12:43:33, 12:43:44 ve 12:44:33: yerel arşiv için 109 stil katmanı yönlendirilmiş, `style-ready` gelmiş. Sonrasında tamamen yüklenmiş kare bildirimi gelmediği için 60 saniyelik istemci zaman aşımı oluşmuş.
- Aynı oturumlarda SDK `connected - false` yazıyor. V10 uyumluluk köprüsü yerel PMTiles dosyalarını `vela-pmtiles.invalid` HTTP adresi üzerinden OkHttp içinde okuyor. SDK'nın bağlantı kapısı bu yerel okumayı da durdurabilir. Bu güçlü adayın teybin yeni sürüm testiyle doğrulanması gerekiyor.
- 12:48:36: başka bir oturumda `frame` ve `map: first frame` var. GPU'nun hiçbir harita karesi üretemediği söylenemez. Bu kayıttan görüntünün içeriği doğrulanamaz.
- Son oturum 12:48:59 `style-loading` aşamasında kalmış; 12:50:00 zaman aşımı raporu oluşmuş. Ana launcher PID 32369 çalışmaya devam etmiş.
- `crash-*` adlı dosyalar arasında harita zaman aşımı, önceki süreç sonlanması ve ana iş parçacığı duraklaması raporları var. Dosya adı tek başına uygulama çökmesi değildir. Müzik listesi sıralamasında 3 saniyeyi aşan duraklama ayrıca görülüyor; bu incelemede değiştirilmedi.
- API 27 sistemin kesin süreç çıkış nedenini uygulamaya sunmuyor. Bu raporlardan yeni bir native çökme nedeni çıkarılamaz.

## Düzeltilen kod

1. PMTiles köprüsü kurulurken MapLibre HTTP kaynak işleyicisinin bağlantı kapısı açılıyor. İnternetsiz yerel dosya istekleri de OkHttp interceptor'a ulaşabiliyor. Gerçek uzak kaynaklar normal ağ istekleri olarak kalıyor ve 30 saniyelik OkHttp sınırına tabi.
2. İlk arşiv açılışında dosya adresi, boyut/değişiklik damgası ve zoom aralığı; okuma hatasında kaynak adresi ve exception loglanıyor.
3. Tamamlanmamış bir kare için stil başına bir kez `frame-partial` bildiriliyor. Bu başarı sayılmıyor; `frame` ve zaman aşımı kriterleri korunuyor.
4. Yedek ekranındaki “Vela’yı Yeniden Başlat” düğmesi yalnız `killProcess` çağırıyordu. Yeniden açma işlemi yoktu. Düğme artık ayrı `:restart` sürecinde dışa kapalı bir Activity'ye devrediyor. Bu Activity yalnız aynı UID'ye ait verilen ana PID'yi kapatıyor, kapanışını bekliyor, ardından yeni MainActivity başlatıyor. Alarm/ek izin gerekmiyor.
5. Varsayılan Home için ayarlardaki yeniden başlatma da aynı mekanizmayı kullanıyor. Program olarak kullanılırken “kapat” davranışı korunuyor.
6. İkincil süreçlerde yedek kurulumunu ve launcher/müzik/ses başlatılmasını engelleyen Application koruması standart varyantta da geçerli. Yedek kurulumu yeni ana sürecin `attachBaseContext` aşamasında, okuyucular oluşmadan yapılıyor.

## Kabul kontrolü

- Aynı yedek yeniden yüklenmeden önce mevcut kurulu haritayla, internet kapalıyken açılmalı.
- Yeni logda `PmtilesBridge: Archive opened` ve ardından `frame` beklenir. Yalnız `frame-partial` varsa kaynak tamamlama sorunu devam ediyor demektir; `Local resource failed` satırı varsa exception incelenmeli.
- Yedek hazırlandıktan sonra yeniden başlat düğmesi uygulamayı otomatik açmalı ve yedek sonucu başarılı görünmeli.
- Launcher başlangıç modu, müzik ve Google sistem ses politikası korunmalı.

Yerel derleme/birim testleri fiziksel teybin internetsiz harita çizimini veya yeniden başlatmasını tek başına doğrulamaz.

Son doğrulama: 0.4.100-diagnostic (2100) derlemesi başarılı; 60 testte 0 başarısızlık/0 hata, 4 atlama. Beş mimari paketin sürümü ve ARMv7 paketinin önceki sürümle aynı imzayı taşıdığı doğrulandı. Kaynak/log yedeği `D:/Projects/CarWorkspace/Vela-backups/2026-10-10-offline-map-restart` altında korunuyor.
