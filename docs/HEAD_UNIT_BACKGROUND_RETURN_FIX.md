# Teypte arka plandan dönmeme incelemesi

## Logların gösterdiği

- 29 Eylül kayıtlarındaki iki Java çökmesi `TextureViewRenderer / EGLImpl.eglCreateContext` kaynaklı. Bunlar haritanın grafik sürücüsü yoluna ait; müzik bildirimi çökmesi değiller. Mevcut kaynakta bu hata için SurfaceView'e dönüş var.
- 3 Ekim kayıtlarında arka plandan başarılı dönüşler de var. Sonraki açılışlarda önbellek uyarısı ve birkaç saniye sonra yeni `VelaApp` başlangıcı tekrar ediyor. Güncel kayıtlarda bu ölümlerin Java hata yığını veya sistem ANR raporu yok. Dolayısıyla cihazın güncel süreç kapatma nedenini kesinleştiremiyoruz.
- 3767 parçalık USB taraması yaklaşık iki dakika sürüyor. İlk kurulumda indeks yok; sonraki açılışlarda indeks okuma yolu çalışıyor. Verileri temizleyince geçici düzelme bu kod hatasıyla uyumlu.

## Düzeltilen kod hataları

1. `MusicRepository` kurucusu, `_parcalar` ve `_klasorler` oluşturulmadan `loadCachedIndex()` çağırıyordu. Dolu önbellek akışlara yazarken null hatası veriyordu. Akışlar önce oluşturuluyor; JSON ve USB dosya kontrolleri IO üzerinde yapılıyor. Tam tarama bu yüklemeyi bekliyor, eski önbellek yeni taramayı ezmiyor.
2. Dahili parçanın kapak resmi ana iş parçacığında USB'den okunup tam boyut çözülüyordu. İşlem IO'ya taşındı, çözme boyutu sınırlandı ve önceki parçadan geç gelen kapak yeni parçayı ezemiyor.
3. Medya servisi, foreground bildirimini ancak yöneticiler ve oturum oluşturulduktan sonra gönderiyordu. İlk bildirim artık bu işlerden önce gönderiliyor. Bildirim kapatıldığında servis gerçekten duruyor ve `START_NOT_STICKY` dönüyor; yeni dahili oynatma servisi yeniden başlatıyor. Normal çalışmadaki `START_STICKY` korunuyor.
4. Araç sürümünün ana ekranı `singleTop` yerine `singleTask`: Home, uygulama simgesi ve medya bildirimi mevcut görevi geri getiriyor. Standart sürüm değiştirilmedi. Medya bildirimi görev yeniden kullanma bayraklarını taşıyor.
5. Harita yaşam döngüsü gözlemcisi mevcut durumu kendisi gönderdiği halde `onStart/onResume` önce elle de çağrılıyordu. Çift çağrı kaldırıldı.

## Doğrulama ve cihaz denemesi

- XML, kaynak kodu değişiklikleri ve boşluk denetimi yapıldı. Önbelleğin ana ekranı bekletmeden yüklenmesini kontrol eden `MusicRepositoryCacheTest` eklendi; bu tur Gradle/Android test çalıştırılmadı.
- Emülatörler kullanılmadı. Yeni sürümün teypte doğrulanması gerekiyor; tam çözüm olduğu henüz cihazda kanıtlanmış değil.
- Önce verileri silmeden güncelleyin. USB takılıyken açın: önbellek uyarısı yerine yüklenen parça sayısı beklenir.
- Başka uygulamaya geçin, Home ile dönün; 10 kez tekrarlayın. `onNewIntent` kayıtları aynı görevde dönüşü göstermeli.
- Müzik bildirimine dokunarak dönün. Oynatmayı duraklatıp bildirimi kaldırın; ardından dahili müziği yeniden çalın: bildirim geri gelmeli.
- Navigasyon ve müzik açıkken arka plana geçip geri dönün. Verileri silmek gerekmemeli.
- Sorun tekrarlarsa yeni uygulama logu ile cihazın ANR/logcat veya hata raporu gerekli. Java çökmesi olmadan süreç kapanmasının nedeni yalnız uygulama logundan çıkarılamıyor.

## Kill davranışı

Manifestte `persistent=true` yok ve süreç öldürmeyi engelleyen özel bir koruma bulunmadı. Home rolü ve sistemin bağlandığı bildirim dinleyicisi cihazın süreç yönetimini etkileyebilir; bu, Android'in zorla durdurmasını uygulamanın engellediği anlamına gelmez. Medya servisi artık bildirim kapatılınca boşta yeniden canlanmıyor. Teybin kendi hafıza temizleyicisinin Home uygulamalarına yaklaşımı ayrıca cihazda gözlenmeli.
