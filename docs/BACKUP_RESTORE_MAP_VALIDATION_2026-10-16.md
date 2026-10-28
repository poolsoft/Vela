# Geri yükleme ve eksik harita incelemesi — 16 Ekim 2026

## Kullanıcının senaryosu

İlk geri yükleme hata vermiş, ikinci denemede hata veren bölüm seçilmemiş. Bu durumun teypteki kilitlenmeye veya eksik haritaya yol açıp açamayacağı incelendi. Mevcut cihaz dosyaları silinmedi/değiştirilmedi.

## Kodda doğrulanan mevcut korumalar

- PortableBackup, seçilen dosyaları canlı harita klasörüne değil `portable-restore/staged` içine aktarır.
- Parça uzunluklarını ve dosyanın SHA-256 değerini doğrulamadan `ready.json` oluşturmaz. Hata/iptal durumunda staging temizlenir.
- Uygulama bu aşamada ölürse, sonraki açılışta ready olmayan staging silinir; canlı dosyalar değiştirilmez.
- Gerçek kurulum yeniden başlatmada, ana süreçte ayar/harita okuyucularından önce yapılır. Journal, eski klasörün taşınması ve committed işareti kullanılır. Kurulum yarıda kesilirse eski içerik geri getirilir.
- İkinci denemede seçilmeyen kategorinin mevcut canlı dosyaları korunur; eski bir bozuk dosya zaten oradaysa o kategori atlanarak temizlenmiş olmaz. Bu, ilk başarısız aktarımın canlı dosyayı bozduğu anlamına gelmez.
- Ayrı renderer servisi, ana süreç bağlantısı ayrıldığında onUnbind içinde kendi sürecini sonlandırır; eski okuyucuyu bilerek açık tutan bir geri yükleme yolu bulunmadı.

## Log bulgusu ve sınırı

Mevcut .logs kayıtlarında PortableBackup/BackupRestore hata sebebi veya `Invalid PMTiles` / dosya sınırı dışı okuma hatası bulunmadı. Önceki aktarım hatası yalnız Logcat'e yazıldığı için bu yokluk, aktarımın hatasız olduğunu kanıtlamaz.

16 Ekim oturumlarında `basemap/tr-guney.pmtiles` 215541566 bayt, `basemap/world.pmtiles` 10960752 bayt olarak açılıyor. Boyutlar önceki oturumlarla aynı. Bu başlığın okunabildiğini gösterir; dosyaların bütün tile içeriklerinin sağlamlığını göstermez. Teypteki dosyalar/yedeğin manifesti elimizde olmadığından SHA-256 değerlerini karşılaştırıp mevcut haritalara sağlam/bozuk hükmü veremiyoruz.

İlk hata veren denemenin canlı haritalarda yarım dosya bırakması mevcut staging/hash/journal tasarımında beklenen davranış değildir. Daha önce zaten bozuk kaynak dosyanın sağlam bir transferle taşınması ise ayrı bir olasılıktır: SHA-256 yalnız aktarım eşitliğini doğrular.

## Eklenen korumalar

- Aktarılan PMTiles dosyasında 127 baytlık v3 başlığı, zoom aralığı, root/metadata/leaf/tile bölümlerinin dosya sınırları kontrol edilir. Taşma, header içine işaret eden bölüm ve kesilmiş dosya reddedilir.
- Yeniden başlatmada canlı klasörler taşınmadan önce staging dosyalarının varlığı/boyutu ve PMTiles bölüm sınırları tekrar kontrol edilir. Bu yalnız bekleyen geri yüklemede yapılır; normal açılışlara harita taraması veya büyük dosya hash hesabı eklenmedi.
- Başarısız aktarım dosya/aşama adıyla FileLogger üzerinden kullanıcının aldığı `Android/data/app.vela/files/logs/vela_app.log` kaydına yazılır. SHA-256/boyut uyuşmazlığı dosya adı içerir.
- Erken açılıştaki kurulum/rollback hatası kalıcı diagnostic kaydında saklanır; FileLogger açılınca normal uygulama loguna aktarılır. Önceden catch içindeki hata nedeninin temizleme sırasında kaybolduğu yer kapatıldı.

## Doğrulamanın kapsamı

Yeni testler geçerli bölüm sınırları, önceden kesilmiş kaynak, kısa header, yanlış imza, dosya dışı metadata, taşan leaf boyutu, ters zoom ve header ile çakışan root durumlarını kapsar. Kontrol bir tam tile/decompression taraması değildir; bölümler içindeki tüm içerik hatalarını bulmayı vaat etmez. Mevcut teyp dosyalarının bütünlüğü ve kilitlenmenin bu dosyalardan kaynaklandığı henüz doğrulanmadı.

## Çalıştırılan kontroller

`:app:compileCarReleaseKotlin` ve `:app:testCarReleaseUnitTest --tests app.vela.offline.PmtilesContainerTest --tests app.vela.offline.PmtilesReaderTest` başarılı. Yeni 8 yapısal doğrulama testi geçti. Önceki okuyucu testleri de çalıştırıldı; gerçek arşiv isteyen opsiyonel test, dosya parametresi verilmediğinden atlanabilir. `git diff --check` başarılı. G:/vela altındaki mevcut log kopyalarında da geri yükleme hatasının nedeni bulunmadı; orada harita/yedek manifest dosyası bulunmadı. Yeni APK bu tur cihaza kurulmadı.
