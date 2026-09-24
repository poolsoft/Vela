package app.vela.carlauncher.ui

/**
 * CoMaps_Auto_V2 ve OsmAnd PanelContent Uyumlu Tanim.
 * Sag panelde ve masaustunde gosterilecek icerik modlari.
 */
enum class CarPanelContent {
    WIDGETS,      // Varsayilan birlesik panel (saat, kucuk muzik widgeti, telemetri, bildirim)
    MUSIC,        // Buyuk tam ekran / genis muzik calar
    DESKTOP,      // Cok sayfali Grid masaustu widget alani
    APP_DRAWER,   // Uygulama listesi cekmecesi
    SETTINGS,     // Arac bas unitesine ozel ayarlar paneli
    DASHBOARD,    // Spor kadran ve telemetri paneli
    WEATHER,      // Saatlik ve gunluk hava durumu paneli
    ANTENNA       // Noktadan noktaya anten hizalama paneli
}
