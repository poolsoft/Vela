package app.vela.carlauncher.legacy

/**
 * Bu paket, Vela'daki IKINCI (view tabanli) widget sisteminden kalan kodu tutar.
 * Kod bilincli olarak silinmedi; buraya tasindi ki "hangi sistem canli" sorusu bir kez
 * daha sorulmasin. Yeni isler bu pakete EKLENMEZ.
 *
 * Canli olan tek widget sistemi:
 * - app.vela.carlauncher.desktop -> masaustu grid'i (CellGrid, DesktopPlacementEngine,
 *   MovableWidget, DesktopWidgetLayoutStore, CarAppWidgetHostController).
 * - app.vela.carlauncher.widgets.WidgetRegistry -> TEK widget katalogu (dogal boyut,
 *   masaustu kimligi, secici listesi). Masaustu grid'i de bu kayittan beslenir.
 *
 * Bu paketteki dosyalar:
 * - WidgetManager.kt           : eski yerlesim/kayit defteri. Yalnizca Araclar ekranindaki
 *                                "Sistem widget'i ekle" butonu ve yedekten donus sonrasi
 *                                reload() icin cagriliyor; buraya eklenen widget'lar hicbir
 *                                ekranda cizilmiyor.
 * - BaseWidget.kt              : eski sinif tabani (BaseWidget + GenericWidget).
 * - WidgetPickerDialogView.kt  : cagrilmayan widget secici; yerine DesktopWidgetPicker var.
 * - WidgetActionMenuDialog.kt  : cagrilmayan widget menuyu; yerine MovableWidget popup
 *                                menuyu ve DesktopWidgetSettingsDialog var.
 * - CarWidgetSizing.kt         : 12x6 sabit grid varsayiyor; gercek grid 10x5 (landscape)
 *                                ve 5x8 (portrait). Saglayici boyut sinirlari artik
 *                                desktop/WidgetSpanSizing.kt icinde hesaplaniyor.
 * - CarZeroPaddingWidgetHost.kt, LegacyCarAppWidgetHost.kt, LegacyCarAppWidgetHostView.kt,
 *   CarWidgetVerticalGestures.kt : eski barindirici siniflari; desktop/CarAppWidgetHost.kt
 *                                kullaniliyor.
 *
 * Silmeden once nasil dogrulanir (bu depoda kullanilan yontem):
 * 1) Cagri yeri aramasi - sonuc yalnizca kendi tanimi ve bu klasor olmali:
 *      rg -n "WidgetManager|CarWidgetSizing|WidgetPickerDialogView|WidgetActionMenuDialog" app/src
 * 2) Manifest/XML kontrolu - Android siniflari manifest uzerinden cagrilabilir, bu yuzden
 *    tek basina grep kanit degildir:
 *      rg -n "WidgetManager|CarWidgetSizing" app/src/main/AndroidManifest.xml app/src/car
 * 3) Android Studio: Analyze > Run Inspection by Name > "Unused declaration" (:app modulu).
 * 4) Refleksiyon/serialization kontrolu: sinif adi string olarak geciyor mu?
 *      rg -n "Class.forName|\"WidgetManager\"" app/src
 * 5) Derleme + test: ./gradlew :app:compileCarDebugKotlin :app:testCarDebugUnitTest
 * Bes adim da temizse dosya silinebilir. SU IKI GRUBU SAKIN SILME:
 * - widgets/WidgetRegistry.kt (tek katalog),
 * - InterlockingClockView, MusicVisualizerView, FuturisticSpeedometerView
 *   (paneller, dock ve kucuk kartlar bu view'lari ciziyor).
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
internal object LegacyWidgetSystemNote
