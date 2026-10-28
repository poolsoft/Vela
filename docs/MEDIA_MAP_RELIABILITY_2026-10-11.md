# Müzik ve çevrimdışı harita oturumu

Dal: `codex/maplibre-10.3.7`. Başlangıç commit'i: `e0b1c9cb`.
Kaynak yedeği: `D:/Projects/CarWorkspace/Vela-backups/2026-10-11-media-map-reliability/source-before.zip`.

## Değişiklikler

- Dahili oynatıcı kurucusundaki senkron tercih okuması kaldırıldı. Tercihler arka planda okunuyor; kayıtlı listenin JSON çözümlemesi ve parça eşleştirmesi arka planda yapılıyor. Oynatıcı/arayüz durumu ana thread'de uygulanıyor. Aynı oynatıcı için geri yükleme Mutex ile sıralanıyor; bu sırada kullanıcı bir parça seçmişse üzerine yazılmıyor.
- Parça kimliği indeksi bir kez kuruluyor. Kayıtlı kuyruk sırası ve tekrar eden kayıtlar korunuyor; değişmiş USB portları bağıl yol, sonra dosya adı ile eşleştiriliyor. Bağıl yol, aynı adlı başka dosyadan önce gelir. Son parça yoksa çözümlenen ilk parça seçiliyor; eski parçanın saniyesi ona uygulanmıyor.
- Kuyruk ve oynatıcı tercihlerinin yazılması sıralı IO kuyruğunda. Smart Focus tercihleri ve liste deposunun yüklenmesi de ana ekranı bekletmiyor. Liste henüz yüklenmemişken yapılan değişiklikler yükleme bitince uygulanıyor. Ön planda mevcut MusicManager varsa medya durumu yeniden yayımlanıyor; ikinci bir oynatıcı kurulmaz.
- Harita ComposeView'i panelin geçici detach işlemiyle dispose edilmiyor; Activity yaşam döngüsüne bağlı ve gerçek host release sırasında temizleniyor. Renderer'ın ComposeView'i servis bağlamı yerine kendi sanal ekranının Dialog bağlamını kullanıyor. Boyut değişiminde pencerenin boyutu açıkça güncelleniyor. Aynı MapView/stil ve servis korunuyor; görünmeyen yüzeyde mevcut SUSPEND yolu kullanılıyor.
- Çevrimdışı arşiv seçiminde merkez verisi olan bölge, ekran köşeleri bölgenin dışındayken de seçilebiliyor. Mevcut kapsam testleri korunuyor; dünya haritasına gereksiz düşüş azaltılıyor.
- Yerel stil içindeki uzak raster/vector kaynakları ve onlara bağlı katmanlar birlikte kaldırılıyor. Yazı tipi ve ikon kaynakları yerelleştiriliyor. Eksik yazı dosyaları isimleriyle loglanıyor. Üç yazı stili için Latin ve Latin Extended dosyaları, ikonlar için dört dosya kontrol ediliyor. İndirilen yazı paketi mevcut paketi değiştirmeden önce aynı kontrolden geçiyor.
- Çevrimdışı modda SDK HTTP yolu, yerel PMTiles köprüsünü açık tutup gerçek uzak istekleri anında 404 ile sonlandırıyor. Uzak PMTiles range okumaları da engelleniyor. Renderer tercih aktarımında OfflineMode güncelleniyor. MapTiler seçili olsa bile çevrimdışı modda yerel Liberty stili kullanılıyor.
- Aynı uzun ana-thread donması için 15 saniyede bir yeni stack raporu alınabiliyor; ilk açılış beklemesi yüzünden daha sonraki gerçek tıkanma artık tek raporla örtülmüyor. Harita yüzey logu boyut, DPI ve oturumun korunup korunmadığını içeriyor. Müzik geri yükleme logu kayıt/çözümlenen/eksik sayısını ve süreyi içeriyor.
- İlk emülatör denemesinde harita hazır olmadan 15 saniyelik heartbeat kontrolünün süreci kapattığı görüldü. İlk çizim için zaten tanınan 60 saniye heartbeat kontrolüne de uygulandı; hazır haritada 15 saniyelik kontrol korunuyor.

## Doğrulama

- 1.200 parçalık indeks testi: USB port değişimi, doğrudan content URI, bulunamayan kayıt, kuyruk sırası ve tekrarlar. Ayrıca aynı adlı dosyalarda bağıl yol önceliği.
- Çevrimdışı stil testi: uzak kaynaklar çıkarılırken onların katmanları da çıkarılıyor; yerel kaynak ve arka plan korunuyor; ikinci uygulama sonucu değiştirmiyor.
- Son derleme başarılı (`:app:testCarReleaseUnitTest :app:assembleCarRelease`, diagnostic x86 APK dahil); 64 test, 0 hata, 4 atlanan test.
- API 30 x86/GLES2 emülatöründe internet kapalı, 18 kayıtlı parça: geri yükleme 46 ms. Parçalar/Klasörler/Listeler içerikleri ekran döndürmeden değişti. Vela varsayılan Home yapıldı; sistem ayarlarından Home ile dönüşte aynı launcher PID korundu.
- Son APK Home üzerinden başlatıldı. Yerel bölge PMTiles, yazı ve ikon paketiyle sokaklar, adlar ve POI simgeleri görüldü. İlk renderer display 21:42:15, frame 21:42:55: yaklaşık 40 saniye. Bu süre hâlâ optimize edilecek bir soğuk başlangıç maliyeti; anında açılış iddiası yok.
- Büyük müzik (344x462), normal (832x462), tam harita (1188x462), masaüstü sayfası ve Home dönüşü denendi. Renderer PID 6063 ve oturum `305933c5-6981-4092-906c-de6dc05e8e55` korundu. Yüzey değişimleri `retained=true`; yeni `map-create` veya `style-loading` oluşmadı. Boyut değişiminden sonraki frame yaklaşık 0.5–1 saniye.
- Müzik parçası seçilerek çalma/duraklatma ve sistem ayarlarından Home dönüşü denendi; kapak/parça bilgisi ve playlist sekmeleri kullanılabilir kaldı. Fiziksel teypte aynı davranış henüz doğrulanmadı.
- Kod commit'i `9248945f`; GitHub APK işi: https://github.com/poolsoft/Vela/actions/runs/38088411956 .

## Sınırlar

Yerel stilin yüklenmesi veya `frame` olayı, tüm harita verisinin/yazıların eksiksiz olduğunu kanıtlamaz. Eksik PMTiles verisi üretilemez; eksik yazı paketi teypte internetten otomatik tamamlanmaz. Bunlar yedekle taşınmalı veya bağlantılı cihazda hazırlanmalı. API 27 teybin sürücüsündeki çizim ve fiziksel cihaz performansı ayrıca doğrulanmalıdır.

Harita hâlihazırda ayrı süreçteki bağlı serviste çalışır. Yeni bir sürekli foreground servis eklenmedi. Panel değişimi için servisi yeniden kurmak yerine mevcut oturum korunur. Sistem süreci gerçekten öldürürse bellekteki MapView'i korumak mümkün değildir; mevcut hata/güvenli mod davranışı geçerlidir.
