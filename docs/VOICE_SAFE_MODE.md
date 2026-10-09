# Teyp için geçici sistem sesi düzeni

9 Ekim 2026 değişikliği; MapLibre 10.3.7 deneme dalı.

- Vela'nın yerel ASR, Piper TTS ve Hey Vela motorları `NATIVE_VOICE_ENABLED=false` ile giriş noktalarında kapalıdır. İndirilen modeller silinmez. Eski motor seçimi bu sürümde native yüklemeyi başlatmaz.
- Google TTS (`com.google.android.tts`) yalnız ilk konuşma isteğinde bağlanır; launcher açılışı ses modeli veya TTS hizmeti başlatmaz. Google TTS yoksa başka bir motora sessizce geçilmez.
- Sesli komut, kurulu sistem sağlayıcısına `RECOGNIZE_SPEECH` isteği gönderir. Google TTS kurulumu tek başına bu sağlayıcının kurulu olduğunu göstermez. Sağlayıcı yoksa açıklama gösterilir.
- Müzik servisi, kayıtlı oynatma ve kapak okuma üzerindeki tanı engeli kaldırıldı. Harita normal başlangıçta açılır; mevcut ayrı harita süreci ve hata paneli korunur. Otomatik yeni müzik taraması eklenmedi.
- Tanı APK'sının bağımsız gözlemcisi ve ayrıntılı kayıtları `DIAGNOSTIC_BUILD` üzerinden çalışmaya devam eder; müzik engeline bağlı değildir.

## Test ekranı

**Ayarlar → Ses → Sesli komut ve TTS testleri** veya **Ayarlar → Tanı → Sesli komut ve TTS testleri**.

1. Sağlayıcı kontrolü: Google TTS ve konuşma sağlayıcısı var mı?
2. Google TTS testi: bir örnek konuşmayı başlatır; motorun tamamlama/hata bildirimi veya 30 saniye sınırı gözlenir. Gerçek sesi kullanıcı dinleyerek doğrular.
3. Sistem konuşma tanıma testi: harici sağlayıcı mikrofonu açar. Tanınan sonuç yalnız başarı/boş/iptal olarak raporlanır; arama veya rota başlatılmaz, konuşulan metin loga yazılmaz.

Testler otomatik başlamaz, süreç kaybından sonra devam etmez. Sonuçlar hem dahili `files/diag/voice-tests.txt` hem dışarıdan erişilebilen `Android/data/app.vela/files/logs/voice-tests.txt` dosyasına kaydedilir. TTS hataları ayrıca uygulama loguna yazılır.

## Hata sınırı

TTS başlatma, konuşma ve gecikmeli ses çağrılarının yakalanabilir Java/Binder hataları ses işlemini başarısız duruma geçirir; ses odağı bırakılır ve hata kaydedilir. Google TTS ve sistem konuşma sağlayıcısı kendi uygulama süreçlerinde çalışır. Şüpheli yerel sherpa-onnx JNI motorları launcher içinde çağrılmaz.

Bu düzen tüm cihaz/işletim sistemi/sürücü hatalarında uygulamanın asla kapanmayacağını garanti etmez. Yerel ses motorlarını yeniden açmadan önce onları ayrı süreçte test etmek ve cihazdaki kapanma nedenini doğrulamak gerekir.
