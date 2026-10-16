# Harita çizim profili ve boşta çalışma

## Mevcut ayarların incelemesi

MapSettingsScreen içindeki 3D binalar ayarı Buildings3d üzerinden vela_settings/buildings_3d anahtarına kaydediliyor; renderer ayar kopyası ayrı sürece taşınıp tekrar başlatılıyor. VelaMapView building-3d katmanının görünürlüğünü bu değere göre değiştiriyor. Ayrıca navigasyon ve uydu görünümünde zaten kapalı. Dolayısıyla kullanıcının bunu kapatması geçerli bir optimizasyon; yeniden kapatmak yeni kazanç sayılmamalı.

Arazi gölgelendirmesi ayrı topographyOn değeriyle hillshade katmanına uygulanıyor; araç navigasyonunda bu da zaten gizleniyor. Microsoft bina ayak izi ek katmanı, düz binalar ve 3D binalar birbirinden farklı. Bu çalışma düz bina verilerini, adresleri, POI'leri veya trafik/navigasyon bilgisini kaldırmıyor.

## İsteğe bağlı profil

Ayarlar > Harita > Harita çizim profili:

- Standart (varsayılan): kullanıcının mevcut katman tercihleri ve ekran yenileme hızına göre çizim bütçesi.
- Hafif: en fazla 30 FPS; kamera/ok takip döngülerinde de yaklaşık 30 Hz işlem sınırı; düz kamera ve eğme hareketinin kapatılması; 3D binalar ve arazi gölgelendirmesinin çizimde gizlenmesi.

Profil mevcut katman ve renk ayarlarını değiştirmez. Standart'a dönüldüğünde kayıtlı katman tercihleri yeniden uygulanır; önceki kamera eğimi otomatik geri getirilmez, standart eğim hareketi yeniden kullanılabilir. Profil tercihi kalıcıdır, renderer sürecine aktarılır ve haritayı/stili yeniden oluşturmadan uygulanır. Profil arka plandaki konum alma, sesli navigasyon veya müzik servisini kapatmaz.

Renk stili ile çizim profili farklıdır. Mevcut Modern/Klasik palet seçimi Görünüm bölümündedir ve Hafif ile birlikte kullanılabilir. Yeni MapLibre stil JSON'ları da hazırlanabilir; offline çalışması için kullanılan source, sprite ve glyph kaynaklarının yerel karşılığı gerekir. Sadece renk değiştirmek tek başına daha hızlı çizim garantisi vermez. Bu değişiklik harici stil indirme veya yeni bir stil editörü eklemez.

## Genel düzeltmeler

- Navigasyon yokken NavController'ın dead-reckoning işi saniyelik uyanmak yerine navigasyon başlayana kadar StateFlow üzerinde bekler.
- Renderer'ın yaşam döngüsü durduğunda iki kare bazlı kamera döngüsü bekler; dönüşte zaman farkı sıfırlanır. Harita nesnesi ve stil korunur.
- Harita görünmüyorken ekran üzerinden hız sınırı sorgusu ve yol etiketi sorgusu da bekler; arka plandaki sesli navigasyon motorunun rota işlemleri korunur.
- Aynı Surface ve aynı boyut için yinelenen SurfaceHolder callback'i tekrar OPEN göndermez. Gerçek boyut/yüzey değişiminde ve arka plandan dönüşte bağlama devam eder; önceki resize/clip düzeltmesi korunur.

## Doğrulama

Kotlin derlemesi ve emülatörde profil/panel/arka plan testleri tamamlandığında sonuçlar buraya eklenecek. Fiziksel teypte CPU/FPS ölçümü yapılmadan performans yüzdesi veya tüm takılmaların giderildiği iddiası yoktur. Özellikle 3D ve arazi zaten kapalıysa Hafif'in ek katkısı kare/işlem sınırı ve düz kamera olacaktır.
