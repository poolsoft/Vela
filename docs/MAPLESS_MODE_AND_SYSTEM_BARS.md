# Haritasız mod ve sistem çubukları

Araç ayarları → Görünüm ve Ekran → **Haritasız mod**.

- “Harita yerine gösterge ekranı” açıldığında harita alanında **Hız göstergesi (Speedo)** veya **Dashboard** seçilir.
- Var olan Neon hız göstergesi ve dijital dashboard kullanılır. Seçim hemen uygulanır, kaydedilir ve launcher yedeğine dahil edilir.
- Harita alanının içeriği tamamen değiştirilir: normal kullanımda MapScreen/renderer/EGL oluşturulmaz. Müzik ve VelaRoot üzerinden gelen GPS telemetrisi devam eder. Bu ayar GPS'yi kapatmaz veya devam eden navigasyonu kendiliğinden sonlandırmaz.
- Gösterge ekranının kapatma düğmesi ayarlara götürür. Haritaya dönmek için haritasız modu kapatın.
- Desktop başlangıcı ayrı bir tercihtir. Desktop görünümündeyken çalışma alanı korunur; normal launcher'a veya harita alanını tam ekran gösteren moda dönüldüğünde bu seçim kullanılır.

Sistem çubukları tek ortak politikadan uygulanır:

- **Tam ekran** açılırsa durum çubuğu seçimi de kapatılır; sistem çubukları gizlenir.
- **Durum çubuğunu göster** açılırsa tam ekran kapanır. Böylece iki ayar birbiriyle çelişmez.
- Eski kayıtta her ikisi açık olduğunda zaten gizlenen durum çubuğu, yüklenen ayar durumunda da kapalı gösterilir.
- Ana pencere ve uygulama içi Compose diyalogları aynı politikayı kullanır. Müzik listesindeki Android diyalogları da aynı politikayı uygular.
- Launcher kök alanı görünür sistem çubuklarının inset alanını tüketir. Ana ekran ve XML düğmeleri status barın altında kalmaz; alt ekranların aynı boşluğu tekrar eklemesi önlenir.
- Pencere odağı geri geldiğinde ana pencereye politika tekrar uygulanır.

Android izin pencereleri, dosya seçici ve dış uygulamaların kendi pencereleri Vela tarafından yönetilmez. Bunlardan Vela'ya dönüldüğünde Vela'nın ayarı tekrar uygulanır.

Fiziksel kontrol: durum çubuğu kapalı/açık ve tam ekran seçeneklerinde ana ekran, ayarlar, uygulama çekmecesi, widget seçici, yedek seçici ve müzik liste diyalogları arasında geçiş yapın. Bar açıkken en üst düğmeler erişilebilir olmalı; Vela'ya dönüşte gizleme tercihi korunmalı.

## Yerel doğrulama — 10 Ekim 2026

- Son kaynakla `:app:testCarReleaseUnitTest :app:assembleCarRelease` başarılı (6 dakika 25 saniye).
- 60 birim test: 0 hata, 0 başarısızlık, 4 atlama.
- Beş APK'da paket `app.vela`, sürüm `0.4.100-diagnostic`, sürüm kodu `2100` aapt ile doğrulandı.
- ARMv7 APK imzası doğrulandı ve Diagnostic99 ile aynı sertifika olduğu kontrol edildi. Dosya hashleri `SHA256SUMS.txt` içinde.
- GPS başlatılması, konum izni varsa ortak launcher yaşam döngüsünden yapılır; `startLocation()` mevcut iş varsa tekrar başlatmaz.
- Açık `NumberForge_Light` ve `KmbbTabletX64` emülatörlerine kurulum yapılmadı. Bu doğrulama fiziksel teybin pencere/harita davranışını veya gerçek GPS hızını doğrulamaz.
