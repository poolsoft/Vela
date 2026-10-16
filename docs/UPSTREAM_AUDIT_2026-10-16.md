# Vela upstream karşılaştırması — 16 Ekim 2026

## Kapsam ve yöntem

Bizim incelenen sürüm: codex/maplibre-10.3.7, ffc018aec6bbeaa88c4bc4cf2ce7f3bfbfacd303 (poolsoft/Vela). Kaynak upstream: PimpinPumpkin/Vela main, 3c8fc207652e7a4fabfe53089de6ccace308ac61, 12 Ekim 2026 snapshot. Remote güncellendi; upstream main geçmişi zorla yeniden yazılmış. Bu nedenle dal ayrışma sayıları eksik özellik sayısı değildir.

git cherry karşılaştırmasında 1249 patch eşdeğeri ve 754 eşdeğeri bulunmayan commit çıktı. Eşdeğeri bulunmaması da tek başına eksik özellik kanıtı değildir: aşağıdaki öneriler mevcut dosyaların içeriği ve ilgili upstream değişiklikleri karşılaştırılarak seçildi. Bu bir hedefli güvenilirlik/performans incelemesidir; bütün upstream özelliklerinin tek tek regresyon testi değildir. Upstream kaynakları salt okunur .logs/upstream-audit-20261016/source altına çıkarıldı. Bu turda upstream kodu birleştirilmedi.

## Önerilen uygulama sırası

| Öncelik | Değişiklik / mevcut kanıt | Beklenen yarar ve aktarım koşulu |
|---|---|---|
| P1 | Çevrimdışı indeksleri güvenli yazma. ObfStore.writeIndex doğrudan indexFile.writeText kullanıyor; PoiPackStore revs.json da doğrudan yazılıyor. Upstream c34c912b5 AtomicFiles ve store uyarlamaları ekliyor. | İşlem yazma sırasında ölürse indeksin boş/bozuk kalıp kurulu paketin kayıp görünmesini azaltır. Önce OBF/POI indeks ve revision kayıtlarına uygulanmalı. Üst sürüm helper'ın rename başarısızlığında hedefi silen fallback'i ayrıca güçlendirilmeli; adında atomic olması her dosya sisteminde kesintisiz güvence verdiği anlamına gelmez. Başarısız yazma/rename ve yeniden açılış testleri gerekli. Mevcut yedek yükleme journal/rollback korumasının yerine geçmez. |
| P1 | Çevrimdışı rota uçlarını gerçek yol verisine göre doğrulama, upstream 28718df3d. Bizde ObfRouteEngine rota öncesinde dosya bbox kontrolü yapıyor, searchRoute sonrasında doğrudan toRoute döndürüyor; uçların en yakın yola uzaklığı ve sonuç uçlarının uzaklığı kontrol edilmiyor. | Harita kutusu içinde fakat gerçek yol verisi dışında kalan hedefin çok uzaktaki yola bağlanmasını önler. Upstream 2 km eşiğini kullanıyor; otopark/özel yol ve sınır yakınındaki geçerli rotalarla doğrulanmalı. Birbirine komşu birden çok OBF ve veri dışındaki hedef test edilmeli. |
| P1 | Navigasyon yokken saniyelik dead-reckoning döngüsünü uyutma, c34c912b5. Bizde NavController.tunnelDeadReckonLoop her saniye uyanıp sonradan eligible kontrolü yapıyor. | Navigasyon kapalıyken gereksiz uyanmaları azaltır. Navigating StateFlow üzerinden bekleme aktarılabilir. Tek başına teypteki tüm CPU yükünü veya ANR'yi çözdüğü iddia edilemez. Navigasyonu başlat/durdur ve tünelde konum kaybı senaryoları gerekli. Commit'in renderer bölümleri ayrı değerlendirilmeli. |
| P2 | Yakın mesafeli sesli yönlendirmeleri birleştirme, f906f18b9. Bizde NavEngine yalnız o anda geçilmiş mesafe bantlarını işaretliyor; BAND_MERGE_S ve konuşma süresine göre ilk cümleyi kısaltma bulunmuyor. | Kısa aralıkla aynı manevranın tekrar söylenmesini ve uzun cümlenin dönüş komutuyla yarıda kesilmesini azaltır. Upstream NavPromptGapTest senaryoları uyarlanmalı; Türkçe uzun cümle ve düşük TTS hızıyla ek kontrol gerekli. İzole/on-demand TTS mimarisi korunmalı. |
| P2 | Font paketini eski kopyayı koruyarak değiştirme, 191322f3d. Bizde GlyphPackStore cacheDir'de hazırlıyor, root'u silip rename yapıyor. | Başarısız değişimde önceki fontları korumak yararlı. Önemli fark: bizde glyphRoot şu an context.filesDir; upstream'deki SD kart/iki ayrı dosya sistemi hatasının aynısı mevcut yerleşimde kanıtlanmış değil. Yanına staging/old ve geri dönüş koruması seçici aktarılmalı. Bizdeki tüm Regular/Italic/Bold ve Türkçe 256–511 doğrulaması korunmalı; upstream'in tek dosya kontrolüne düşürülmemeli. |
| P3 | Baseline profile yolculuğunu düzeltip bizim APK için yeniden üretme, 26e299618. Bizim generator map kaydırması yapıyor fakat gerçek Settings yolculuğu yok; generated profil dosyası da mevcut ağaçta yok. | Açılış ve ilk ayar kaydırmasında derleme/JIT maliyetini azaltabilir. Onboarding'i geçip gerçek harita, drawer ve ayarları açan senaryo bizim UI'ye uyarlanmalı. Upstream'in MapLibre 13 sınıfları için üretilmiş profilini doğrudan kopyalamayalım. Upstream ölçümü yaklaşık 50 ms; saniyeler süren beklemeyi tek başına çözmesi beklenmemeli. |

Commit kaynakları: [indeks/idle](https://github.com/PimpinPumpkin/Vela/commit/c34c912b5), [rota sınırı](https://github.com/PimpinPumpkin/Vela/commit/28718df3d), [ses tekrarları](https://github.com/PimpinPumpkin/Vela/commit/f906f18b9), [font değişimi](https://github.com/PimpinPumpkin/Vela/commit/191322f3d), [profil](https://github.com/PimpinPumpkin/Vela/commit/26e299618).

## Zaten farklı biçimde çözülmüş veya doğrudan taşınmaması gerekenler

- Android 9 CJK regex çökmesi için cad7410ef var. Bizim PlaceNames:318 zaten IsHan yerine açık Unicode aralıkları kullanıyor. Dolayısıyla bu çökme düzeltmesini bizde eksik veya mevcut teyp çökmesinin nedeni diye sunmak yanlış olur. Upstream'in codepoint yaklaşımı daha geniş CJK kapsaması için isteğe bağlı iyileştirme olabilir.
- 97eae2fdd çevrimdışı aksan eşleme/sıralama ekliyor. Bizde farklı POI şeması ve normalizasyon yolu mevcut (OfflinePoiStore içinde Normalizer); upstream'deki OfflineRank dosyası yok. Bu yüzden bütün commit doğrudan uygulanamaz ve aksan desteği bizde tamamen yok denemez. Türkçe i/ı/İ/I, ş/s, ç/c; eski yedekten gelen DB ve sonuç limiti öncesi sıralama için ayrı veri testi yapılmalı.
- Yerel sprite/font kullanımı, Türkçe glyph aralıklarının doğrulanması, tam yedek staging/hash/rollback ve izole renderer yaşam döngüsü fork'a özel korunmalı. Yeni upstream olması bunların daha iyi bir karşılığı olduğu anlamına gelmiyor.
- Upstream SDK 13.6.1 android-sdk-opengl, bizim dal 10.3.7 kullanıyor. compileSdk upstream'de 37, bizde 36. SDK/renderer/build dosyalarını topluca almak GLES2 teyp için yaptığımız izolasyonu geri alabilir.
- Upstream build dosyası ONNX/sherpa native kütüphanelerini armeabi-v7a, x86 ve x86_64 için hariç tutuyor. Bizim isteğe bağlı izole ses süreciyle aynı dağıtım kararı değil; ses/build değişiklikleri topluca alınmamalı.
- Android Auto cluster, Street View cache ve çevrimiçi stil seçenekleri mevcut bağımsız/offline teyp sorunları için ilk öncelik değil. Ağ gerektiren yeni varsayılanlar eklenmemeli. Araştırma amaçlı router dalları üretim düzeltmesi gibi taşınmamalı.

## Uygulama ve doğrulama planı

1. Mevcut dalda ayrı küçük commitlerle önce indeks yazma, sonra rota uç doğrulaması, sonra idle döngü değişikliği. Her biri için başarısızlık senaryosu ve odaklı test.
2. NavEngine tekrar önleme ve font swap düzeltmesi. Türkçe TTS testini otomatik motor açılışına dönüştürmeden tanı ekranından çalıştırma.
3. Mevcut tam yedekle temiz geri yükleme; internet kapalı POI/rota; HOME, drawer, büyük/küçük player ve arka plan dönüşü. Renderer PID/session ve style yükleme sayısını karşılaştırma.
4. Sonra baseline profile üretimi ve aynı cihazda önce/sonra ölçümü. Emülatör başarısı fiziksel teyp kararlılığını kanıtlamaz; son APK teypte denenmeli.

## Bu inceleme sırasında tamamlanan mevcut işler

Sistem çubuğu açılış düzeltmesi e5cc4aef, diagnostic-127: immersive açık, status açık ve yalnız navigation bar açık olmak üzere üç durum emülatörde kontrol edildi. Harita kamera başlangıç düzeltmesi ffc018ae, diagnostic-128: internet kapalı iki soğuk açılışın ikisinde de bir stil yüklemesi; önceki tr-guney → world → tr-guney döngüsü oluşmadı. Son APK emülatöre veriler korunarak kuruldu. Ayrıntılar SYSTEM_BARS_STARTUP_2026-10-16.md ve MAP_STARTUP_CAMERA_2026-10-16.md içinde.
