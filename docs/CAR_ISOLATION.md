# Vela araÃ§ baÅŸlatÄ±cÄ±sÄ±nÄ± ana projeden ayÄ±rma

Bu dalda harita ve navigasyon kodu `app/src/main` iÃ§inde kalÄ±r. AraÃ§ teybine Ã¶zel baÅŸlatÄ±cÄ± `car` derleme tÃ¼rÃ¼ndedir. `standard` tÃ¼rÃ¼nde ana ekran (HOME) kaydÄ±, araÃ§ mÃ¼zik servisleri, bunlarÄ±n izinleri ve araÃ§ arayÃ¼zÃ¼ bulunmaz. Ä°ki tÃ¼rÃ¼n uygulama kimliÄŸi `app.vela` olarak kalÄ±r; bÃ¶ylece araÃ§ta kurulu sÃ¼rÃ¼mÃ¼ gÃ¼ncellerken veriler korunur. AynÄ± cihaza bu iki tÃ¼rden yalnÄ±zca biri kurulabilir.

Ã‡alÄ±ÅŸma dalÄ±: `codex/vela-car-isolation`. Dal, `feature/vela-auto-v2` dalÄ±nÄ±n `74ea6cd991dd349e486345f877b056db8c172209` noktasÄ±ndan aÃ§Ä±ldÄ±. Dosyalar taÅŸÄ±nmadan Ã¶nce deÄŸiÅŸikliklerin ve Git hazÄ±rlÄ±k alanÄ±nÄ±n tam yedeÄŸi `D:/Projects/CarWorkspace/Vela-backups/Vela-working-tree-20260924-104652.zip` dosyasÄ±na alÄ±ndÄ±. SHA-256: `b292fae07088c6d3ca091ca9685497db9b1b0711ca9fcfdbc108ae859babf08c`. Yedek depo dÄ±ÅŸÄ±nda tutulur. Bu Ã§alÄ±ÅŸma henÃ¼z commit edilmedi veya gÃ¶nderilmedi.

## DosyalarÄ±n yeri

- `app/src/main`: harita, rota, ortak ayarlar ve taÅŸÄ±nabilir yedekleme.
- `app/src/car`: araÃ§ baÅŸlatÄ±cÄ±sÄ±, HOME ve mÃ¼zik servislerinin manifest kayÄ±tlarÄ±, izinleri, gÃ¶rselleri ve araÃ§ ayarlarÄ±nÄ±n yedekleme desteÄŸi.
- `app/src/standard`: araÃ§ baÅŸlatÄ±cÄ±sÄ± olmadan Ã§alÄ±ÅŸan karÅŸÄ±lÄ±klar. Standart sÃ¼rÃ¼m, araÃ§ sÃ¼rÃ¼mÃ¼nde alÄ±nan yedekten harita ve kiÅŸisel verileri yÃ¼kleyebilir; araÃ§ ayarlarÄ±nÄ± uygulamaz.

Ortak kod, iki derleme tÃ¼rÃ¼nde ayrÄ± karÅŸÄ±lÄ±ÄŸÄ± bulunan `CarIntegration` ve `LauncherBackupPort` Ã¼zerinden araÃ§ Ã¶zelliklerine ulaÅŸÄ±r. Yeni araÃ§ Ã¶zellikleri `src/car` iÃ§ine eklenmeli. Mevcut Android Auto ekranlarÄ± ortak kodda kalÄ±r; teybin HOME baÅŸlatÄ±cÄ±sÄ±ndan farklÄ±dÄ±r.

## Derleme ve upstream gÃ¼ncellemesi

AraÃ§ sÃ¼rÃ¼mÃ¼nÃ¼ `:app:assembleCarDebug`, standart sÃ¼rÃ¼mÃ¼ `:app:assembleStandardDebug` ile derleyin. YayÄ±n sÃ¼rÃ¼mleri `:app:assembleCarRelease` ve `:app:assembleStandardRelease` gÃ¶revleridir. Forkun otomatik yayÄ±n Ã§Ä±ktÄ±sÄ± araÃ§ sÃ¼rÃ¼mÃ¼dÃ¼r. MÃ¼zik arayÃ¼zÃ¼nÃ¼n statik denetimi: `python tools/check_car_music_contract.py`.

Her iki sÃ¼rÃ¼mÃ¼ emÃ¼latÃ¶rde ve araÃ§ teybindeki kritik akÄ±ÅŸlarÄ± doÄŸruladÄ±ktan sonra bu dalÄ± commit edin. Forkun `main` dalÄ±nÄ± `upstream/main` ile gÃ¼ncelleyin; ardÄ±ndan bu Ã§alÄ±ÅŸma dalÄ±nÄ± yeni `main` Ã¼zerine birleÅŸtirin. Ã‡akÄ±ÅŸmalarda upstream harita deÄŸiÅŸikliklerini `src/main` iÃ§inde, teybe Ã¶zel deÄŸiÅŸiklikleri `src/car` iÃ§inde tutun. BirleÅŸtirme sonrasÄ±nda iki manifesti ve iki yedekleme karÅŸÄ±lÄ±ÄŸÄ±nÄ± karÅŸÄ±laÅŸtÄ±rÄ±n. Son farklarÄ± ve APK'larÄ± incelemeden GitHub'a gÃ¶ndermeyin. Ã‡alÄ±ÅŸma klasÃ¶rÃ¼ndeki ilgisiz ekran gÃ¶rÃ¼ntÃ¼leri ve ses dosyalarÄ± commit'e eklenmemeli.

## Kalan doÄŸrulamalar

1. Ä°ki hata ayÄ±klama sÃ¼rÃ¼mÃ¼nÃ¼ derleyip temiz emÃ¼latÃ¶re kurun. `standard` sÃ¼rÃ¼mÃ¼nde HOME rolÃ¼ ve araÃ§ mÃ¼zik servisi bulunmadÄ±ÄŸÄ±nÄ±, `car` sÃ¼rÃ¼mÃ¼nde bulunduÄŸunu kontrol edin.
2. AraÃ§ aÃ§Ä±lÄ±ÅŸÄ±, harita, mÃ¼zik sekmeleri, izinler, uygulama Ã§ekmecesi ve yatay/dikey yerleÅŸimi emÃ¼latÃ¶rde ve teyipte deneyin.
3. Telefon veya emÃ¼latÃ¶rde yedek alÄ±n; araÃ§ta harita, rota, favoriler ve ayarlarÄ± yÃ¼kleyin. AraÃ§ yedeÄŸini standart sÃ¼rÃ¼me de yÃ¼kleyip araÃ§ ayarlarÄ±nÄ±n uygulanmadÄ±ÄŸÄ±nÄ± kontrol edin.
4. Ä°lk baÅŸarÄ±lÄ± derlemeden sonra otomatik yayÄ±ndaki APK yolunu, imzalamayÄ± ve baÅŸlangÄ±Ã§ profili Ã¼retimini doÄŸrulayÄ±n.
