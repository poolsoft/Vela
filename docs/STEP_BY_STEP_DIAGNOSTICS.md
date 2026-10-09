# Teypte adım adım tanı

Harita kapalı başlangıç ekranındaki **Adım adım tanı testleri** düğmesi açar.
Hiçbir test açılışta kendiliğinden başlamaz. Her satırdaki **Çalıştır** bağımsızdır.
**Sırayla çalıştır** 1–9 sırasını izler; ilk hata, süre aşımı veya eksik ön koşulda durur.
**Durdur**, geri tuşu veya ekranı kapatmak devam eden testi iptal eder.
Yeniden açılışta otomatik devam edilmez; süreç öldüyse son adım **YARIM KALDI** görünür.

## Hazırlık

- Tanı APK'sını kur; normal başlangıçta müzik ve harita izolasyonu korunur.
- **Tarama izni** ile Android'in müzik/dosya erişim iznini ver.
- **Ses dosyası seç** ile tercihen teybin USB belleğinden bir MP3 seç.
- Sistem dosya seçicisi yoksa tarama sonrası **Kütüphanedeki ilk dosyayı seç** kullanılabilir.
- Test 4 sesli çalışır. Mevcut Vela çalışma/çalma listesi değiştirilmez.
- Test 9 için harita dosyalarını önceden ekle; dosya yokken bu adım çevrimdışı harita doğrulaması sayılmaz.

## Adımların kapsamı

| Adım | Çalışan bileşen | Başarı ölçütü |
|---|---|---|
| 1 | Boş ayrı harita servisi, Binder | Doğru süreçten oturum/PID doğrulamalı yanıt |
| 2 | Gerçek Vela müzik tarayıcısı | Tarama tamamlandı; parça sayısı ve hata durumu |
| 3 | Android MediaPlayer, seçilen dosya | `prepareAsync` tamamlandı; ses başlamadı |
| 4 | Android MediaPlayer, ses odağı, seçilen dosya | 5 saniyede oynatma konumu ilerledi; ses ayrıca dinlenmeli |
| 5 | MediaMetadataRetriever ve BitmapFactory | Metadata, gömülü kapak varsa en fazla 512 piksele örneklenerek çözümleme |
| 6 | Gerçek Vela CarMediaService | MediaSession/bildirim kurulumu tamamlandı; 5 saniye açık kaldı |
| 7 | Ayrı süreçte EGL, MapLibre, boş yerel stil | Stil ve ilk kare geldi; 5 saniye gözlendi |
| 8 | Ayarlı stil, yerel harita arşivleri hariç | Stil ve ilk kare geldi; çevrimiçi kaynak içerebilir |
| 9 | Mevcut harita ayarları ve arşivler | Stil ve ilk kare geldi; görsel içeriği ayrıca kontrol et |

3–5 temel Android bileşenlerini ayrı sınar; Vela'nın kuyruk, Smart Focus, sesli yönlendirme
ve bütünleşik oynatıcı akışının tamamını doğrulamaz. 6 gerçek Vela servisidir; parça
hazırlama/otomatik geri yükleme/kapak okuma izolasyonu açık kalır. Test servisinin izni
yalnız mevcut oturumdadır; test bitince veya iptal edilince kapanır ve otomatik yeniden
başlama istemez. Harita testleri sonunda yüzey ve ayrı harita süreci bırakılır.

## Kayıt

`Android/data/app.vela/files/logs/diagnostic-tests.json` son sonuçları, oturum kimliğini,
PID'yi, zamanı ve son adımı tutar. Her adım **yerel koda girmeden önce** atomik olarak
yazılır. Mevcut `vela_app.log`, süreç tanısı, harita raporu ve bağımsız gözlemci kayıtları
da çalışır. ZIP gerekmez; aynı logs klasörünü alabilirsin.

JSON'daki eski **ÇALIŞIYOR** kaydı, sonraki ekran açılışında **YARIM KALDI** olarak
gösterilir. Bu tek başına çökme nedenini kanıtlamaz; süreç kaydı/logcat ile birlikte
incelenir. Android'in uygulamaya göstermediği sistem kayıtlarına yeni yetki sağlamaz.
Home tuşu tanıyı kapatıp seçili başlangıç ekranına döner. Yerel bir işlem iptale hemen
yanıt vermezse, işlem bitene kadar başka test başlatılmaz. Dar ekranda düğmeler alt
satıra geçer. Seçilen ses dosyasının URI'si de işlem öncesi günlüğe yazılır.

## Bu değişiklikte düzeltilen harita başlangıcı

`MapLibre.getInstance(context)` artık `PmtilesMapBridge.install()` öncesinde çağrılır.
Diagnostic 97'deki `HttpRequestImpl` statik başlatma hatasının nedeni bu ters sıraydı.
Bu düzeltme, teybin GPU sürücüsünün gerçek harita çiziminde sorunsuz olduğunu tek başına
kanıtlamaz; 7, 8 ve 9 bunu kademeli olarak sınamak içindir.
