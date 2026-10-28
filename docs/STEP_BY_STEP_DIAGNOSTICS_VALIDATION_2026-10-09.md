# Tanı ekranı doğrulaması — 9 Ekim 2026

Test edilen kod: `62c32d7b196e1ac21af18b6aa72d070a748fcbb8`,
dal: `codex/maplibre-10.3.7`. APK: `0.4.98-diagnostic (2098)`.

## Derleme ve imza

Yerelde aşağıdaki görevler başarıyla tamamlandı:

```
gradlew :app:testCarReleaseUnitTest :app:assembleCarRelease -PdiagnosticApk=true -PappVersionCode=2098 -PappVersionName=0.4.98-diagnostic --max-workers=2 --no-daemon
```

60 birim testi: 0 hata, 4 atlanan. Yeni üç sıra testi de geçti:
sıra korunuyor, ilk başarısızlıktan sonra ilerlenmiyor, iptal sonraki teste geçmiyor.
ARMv7, ARM64, x86, x86_64 ve universal APK üretildi. İmza önceki Diagnostic 97 ile
aynı; emülatörde veriler silinmeden `install -r` ile güncellendi.

GitHub workflow dispatch iki defa HTTP 422 / “Actions has been disabled for this
repository” yanıtı verdi. Depo/workflow okuma API'leri enabled/active gösteriyordu;
bu çelişkinin nedeni kesinleştirilmedi. Bu nedenle APK yerelde üretildi ve ayrı
`diagnostic-local-2098` yayınına yüklendi.

## Çalışma zamanı

Yalnız `emulator-5556`, `OsmAndAuto_LowRam_API30_x86` kullanıldı. Diğer emülatörlere
dokunulmadı. Grafik tanısında advertised GLES 2.0, üç ES2 yapılandırması ve **sıfır
ES3 yapılandırması** görüldü. Ayrı ES2 tanı bağlamı OpenGL ES 2.0 bildirdi.

Dokuz test sıralı çalıştırmada tamamlandı:

| Test | Sonuç |
|---|---|
| Ayrı süreç IPC | Oturum/PID doğrulamalı yanıt |
| Gerçek müzik taraması | 18 parça; hata yok |
| Seçilen dosyayı hazırlama | MediaPlayer hazır; ses başlamadı |
| Oynatma | 5 saniyede oynatma konumu ilerledi |
| Metadata / kapak | Gömülü 320×180 kapak çözümlemesi tamamlandı |
| Vela müzik servisi | MediaSession / bildirim 5 saniye açık kaldı |
| Boş yerel harita | Stil + ilk kare; 5 saniye gözlem |
| Arşivsiz stil | Stil + ilk kare; 5 saniye gözlem |
| Mevcut harita ayarları | Stil + ilk kare; 5 saniye gözlem |

Derleme sürerken yapılan ilk denemelerde harita servisi bağlantısı `bind timeout`
verdi; ana süreç ve tanı ekranı ayakta kaldı. Derleme bittikten sonraki son koşuda
harita adımları geçti. Zaman ilişkisi, önceki zaman aşımının kesin nedenini kanıtlamaz.
Eski `ExceptionInInitializerError` yerine son koşuda stil/ilk kare aşamalarına ulaşıldı.

Ek kontroller:

- Dosya seçmeden sıralı çalıştırma 3. adımda durdu; 4 ve sonraki testler başlamadı.
- Kütüphaneden dosya seçimiyle müzik adımları tamamlandı.
- **Durdur** devam eden servis testini iptal etti ve `DURDURULDU` kaydı üretti.
- HOME niyeti tanı ekranını kapatıp normal başlangıcı gösterdi; ana PID değişmedi.
- Yalnız emülatörde doğrulanmış ana PID, servis testi çalışırken SIGKILL ile kapatıldı.
  Yeni açılışta eski test **YARIM KALDI** olarak görüldü; otomatik devam etmedi.
  Elle tekrar çalıştırılan servis testi geçti. Bağımsız gözlemcinin kaydı da alındı.
  Bu yapay kesinti gerçek teybin kapanma nedenini kanıtlamaz.

Ham kanıtlar yerel `.logs/diagnostic-screen-runtime/` klasöründe tutuldu; özel
dosya adları / cihaz logları depoya eklenmedi. Kullanım: [adım adım tanı](STEP_BY_STEP_DIAGNOSTICS.md).

## Sınırlar

Fiziksel teyp bu turda kullanılmadı. Emülatörün GPU sürücüsü Mali-450 ile aynı değil.
Harita testleri ilk kare ve kısa gözlemdir; çevrimdışı dosyaların bütünlüğünü, uzun
navigasyonu veya sesin fiziksel çıkışını doğrulamaz. Müzik testleri temel Android
oynatıcı/kapak bileşenlerini ve gerçek Vela servisini ayrı sınar; Smart Focus ve
bütünleşik kuyruk akışının tamamının kabul testi değildir.
