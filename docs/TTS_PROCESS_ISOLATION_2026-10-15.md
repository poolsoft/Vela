# Vela TTS: isteğe bağlı, ayrı süreç

## Davranış

- Google TTS yeni kurulumda varsayılandır. Motor seçimi kaydedilir; kaydedilmiş Vela seçimi de açılışta yalnız yönlendirme tercihi olarak okunur.
- Eski sürümden kalan Vela motor tercihi tek başına etkinleşmez; bu sürümde açık kullanıcı seçimi ile ayrı süreç kullanımına geçilir.
- Piper ses kitaplığı ve indirme/seçme kontrolleri geri açılır. İndirme tamamlanması motoru kendiliğinden seçmez veya yüklemez.
- Vela seçiliyken test, ses örneği veya rota hazırlığı/konuşma isteği ONNX motorunu `:voice_tts` sürecine bağlar. Uygulamanın açılması bağlamaz.
- Yerel ASR ve Hey Vela için eski güvenlik bayrağı kapalıdır. TTS ayrı bayrakla açılır.
- Model ve AudioTrack, dışa kapalı bağlı servistedir. Ana süreçteki PiperSynth yalnız Messenger istemcisidir.
- Ses modeli, hız, hoparlör ve ses düzeyi tercihleri her istekte süreç özelindeki tercihlere aktarılır. Harita/launcher tercihleri ikincil süreçten değiştirilmez (car varyantının mevcut tercih izolasyonu).
- Motor yükleme hatası, Binder bağlantısı kaybı veya 90 saniye yanıtsızlık durumunda mevcut bekleyen ses istekleri tamamlanarak ses odağı serbest bırakılır; Google seçilir ve tercih kaydedilir. Başarısız motor kendiliğinden tekrar başlatılmaz; açık kullanıcı seçimi yeniden denemeye izin verir.
- Hatalı konuşma otomatik olarak tekrar okunmaz; Google sonraki konuşma isteğinde çalışır. Native süreç izolasyonu sürücü/sistem kaynaklı tüm uygulama öldürmelerini garanti olarak engellemez.

## Değişiklikler

`NativePiperSynth` eski sentezleyicidir; yalnız `PiperService` kullanır. `PiperSynth` ana süreç vekilidir. VoiceGuide ve MapViewModel açılışta yükleme yapmadan motor seçer. Google seçilirken eski neural referansı temizlenir; rota ekranı onu yanlışlıkla ısıtamaz.

## Doğrulama

- İlk Kotlin derlemesindeki erken tercih erişimi düzeltildi; sonraki Kotlin derlemesi başarılı.
- Son kaynaklar için `:app:compileCarReleaseKotlin :core:testReleaseUnitTest --offline --max-workers=2` başarılı: 684 test, 0 hata/başarısızlık, 6 atlanan test (678 çalışan test).
- Manifestte ayrı süreç ve exported=false doğrulandı; staged diff kontrolü geçti. Yeni ADB kontrolünün yardım/argüman yolu çalıştırıldı; cihazda native çökme senaryosu henüz çalıştırılmadı.
- `scripts/check_tts_process.py`: yeni APK üzerinde soğuk açılışta TTS sürecinin başlamamasını ve aktif TTS süreci öldürülünce launcher PID'sinin korunup Google'a geçilmesini ayrı kiplerle doğrular. Çökme testi aktif, indirilmiş bir Vela modeli gerektirir.
- Fiziksel teyp üzerinde ses üretimi, native süreç ölümü ve Google geri dönüşü ayrıca doğrulanmalıdır. Eski emülatör APK'sıyla alınan sonuç bu değişikliğin testi sayılmaz.
