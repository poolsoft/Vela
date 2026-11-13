package app.vela.carlauncher.widgets

import app.vela.carlauncher.desktop.WidgetIds
import app.vela.carlauncher.legacy.BaseWidget
import app.vela.carlauncher.legacy.GenericWidget

/**
 * Dinamik Widget Kayit ve Katalog Sistemi.
 * Yeni widget tipleri buraya register edilir ve UI katalogdan dinamik olarak secer.
 *
 * TEK KAYNAK: Masaustu (Compose/CellGrid) tarafi da panel widget'lari da ayni kaydi ve
 * ayni boyut tablosunu kullanir:
 * - [desktopId]  : masaustu yerlesiminde kullanilan kimlik (WidgetIds sabiti).
 * - [baseWidthDp] / [baseHeightDp] : widget'in dogal boyutu; WorkspaceGrid.size buradan
 *   beslenir, boylece "iki ayri boyut tablosu" sorunu ortadan kalkar.
 * - [symbol] / [displayName] : widget secici kartlarinda gosterilen metin.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object WidgetRegistry {

    const val TYPE_SPEED = "speed"
    const val TYPE_CLOCK = "clock"
    const val TYPE_MUSIC = "music"
    const val TYPE_WEATHER = "weather"
    const val TYPE_COMPASS = "compass"
    const val TYPE_OBD = "obd"
    const val TYPE_COMBINED = "combined"
    const val TYPE_SHORTCUTS = "shortcuts"
    const val TYPE_STATUS = "status"
    const val TYPE_DOCK = "dock"
    const val TYPE_APP_SHORTCUT = "app_shortcut"

    data class WidgetEntry(
        val typeId: String,
        val displayName: String,
        val description: String,
        val defaultSize: BaseWidget.WidgetSize,
        val supportedSizes: List<BaseWidget.WidgetSize>,
        // Masaustu (desktop/) tarafinin kullandigi kimlik ve dogal boyut.
        val desktopId: String = typeId,
        val baseWidthDp: Float = 280f,
        val baseHeightDp: Float = 140f,
        val symbol: String = "",
        // Widget secicide listelenmeli mi? (once masaustunde gorunenler)
        val showOnDesktop: Boolean = true,
        val creator: (id: String, pageIndex: Int, size: BaseWidget.WidgetSize) -> BaseWidget
    )

    private val availableWidgets = mutableListOf<WidgetEntry>()

    init {
        register(
            WidgetEntry(
                typeId = TYPE_COMBINED,
                displayName = "Saat + Hız",
                description = "Saat, hız ve temel telemetriyi tek kartta birleştiren ana gösterge",
                defaultSize = BaseWidget.WidgetSize.LARGE,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.COMBINED,
                baseWidthDp = 340f,
                baseHeightDp = 120f,
                symbol = "\u25F4"
            ) { id, page, size -> GenericWidget(id, TYPE_COMBINED, "Dashboard", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_SPEED,
                displayName = "Hız göstergesi",
                description = "Büyük dijital hız, hız sınırı ve aşırı hız uyarısı",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.SPEEDOMETER,
                baseWidthDp = 240f,
                baseHeightDp = 160f,
                symbol = "\u25C9"
            ) { id, page, size -> GenericWidget(id, TYPE_SPEED, "Hız Göstergesi", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_CLOCK,
                displayName = "Dijital saat",
                description = "Modern neon saat, tarih ve gün bilgisi",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM),
                desktopId = WidgetIds.CLOCK,
                baseWidthDp = 260f,
                baseHeightDp = 100f,
                symbol = "22:55"
            ) { id, page, size -> GenericWidget(id, TYPE_CLOCK, "Dijital Saat", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_MUSIC,
                displayName = "Müzik çalar",
                description = "Çalan şarkı, albüm kapağı ve medya kontrolleri",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.MUSIC,
                baseWidthDp = 300f,
                baseHeightDp = 160f,
                symbol = "\u266B"
            ) { id, page, size -> GenericWidget(id, TYPE_MUSIC, "Müzik Çalar", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_WEATHER,
                displayName = "Hava durumu",
                description = "Anlık hava durumu, sıcaklık ve ikon",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM),
                desktopId = WidgetIds.WEATHER,
                baseWidthDp = 170f,
                baseHeightDp = 90f,
                symbol = "\u2600"
            ) { id, page, size -> GenericWidget(id, TYPE_WEATHER, "Hava Durumu", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_COMPASS,
                displayName = "Pusula",
                description = "Canlı pusula derecesi ve seyahat yönü",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM),
                desktopId = WidgetIds.COMPASS,
                baseWidthDp = 170f,
                baseHeightDp = 90f,
                symbol = "\u25C7"
            ) { id, page, size -> GenericWidget(id, TYPE_COMPASS, "Pusula", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_OBD,
                displayName = "OBD2 verileri",
                description = "Motor devri (RPM), hararet sıcaklığı ve akü voltajı",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.OBD,
                baseWidthDp = 280f,
                baseHeightDp = 90f,
                symbol = "OBD"
            ) { id, page, size -> GenericWidget(id, TYPE_OBD, "OBD2 Verileri", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_STATUS,
                displayName = "Sistem durumu",
                description = "Sistem durumu, bağlantı ve cihaz bilgileri",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.STATUS,
                baseWidthDp = 280f,
                baseHeightDp = 140f,
                symbol = "\u25A5"
            ) { id, page, size -> GenericWidget(id, TYPE_STATUS, "Sistem Durumu", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_DOCK,
                displayName = "Uygulama dock'u",
                description = "Sık kullanılan uygulamalar için alt dock",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                desktopId = WidgetIds.DOCK,
                baseWidthDp = 340f,
                baseHeightDp = 76f,
                symbol = "\u2022\u2022\u2022"
            ) { id, page, size -> GenericWidget(id, TYPE_DOCK, "Dock", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_SHORTCUTS,
                displayName = "Uygulama kısayolları",
                description = "Hızlı erişim için favori uygulamalar grid listesi",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE),
                baseWidthDp = 280f,
                baseHeightDp = 140f,
                showOnDesktop = false
            ) { id, page, size -> GenericWidget(id, TYPE_SHORTCUTS, "Kısayollar", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_APP_SHORTCUT,
                displayName = "Uygulama kısayolu",
                description = "Tek bir uygulamayı açan masaüstü kısayolu",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL),
                baseWidthDp = 80f,
                baseHeightDp = 96f,
                showOnDesktop = false
            ) { id, page, size -> GenericWidget(id, TYPE_APP_SHORTCUT, "Kısayol", size, page) }
        )
    }

    fun register(entry: WidgetEntry) {
        availableWidgets.removeAll { it.typeId == entry.typeId }
        availableWidgets.add(entry)
    }

    fun getAvailableWidgets(): List<WidgetEntry> = availableWidgets.toList()

    /** Masaustunde eklenebilir widget'lar (widget secici bu listeyi gosterir). */
    fun desktopEntries(): List<WidgetEntry> = availableWidgets.filter { it.showOnDesktop }

    fun byTypeId(typeId: String): WidgetEntry? = availableWidgets.firstOrNull { it.typeId == typeId }

    fun byDesktopId(desktopId: String): WidgetEntry? =
        availableWidgets.firstOrNull { it.desktopId == desktopId }

    /** Widget'in dogal boyutu (dp); WorkspaceGrid.size buradan beslenir. */
    fun baseSize(desktopId: String): Pair<Float, Float>? =
        byDesktopId(desktopId)?.let { it.baseWidthDp to it.baseHeightDp }

    fun createWidget(
        typeId: String,
        pageIndex: Int = 0,
        size: BaseWidget.WidgetSize? = null,
        cellX: Int = -1,
        cellY: Int = -1,
        spanX: Int = 1,
        spanY: Int = 1,
        appWidgetId: Int = -1,
        packageName: String? = null,
        customConfig: String? = null
    ): BaseWidget? {
        val entry = byTypeId(typeId)
        val uniqueId = "${typeId}_${System.currentTimeMillis()}"
        val chosenSize = size ?: entry?.defaultSize ?: BaseWidget.WidgetSize.MEDIUM
        val title = entry?.displayName ?: "Widget"
        return GenericWidget(
            id = uniqueId,
            typeId = typeId,
            title = title,
            size = chosenSize,
            pageIndex = pageIndex,
            cellX = cellX,
            cellY = cellY,
            spanX = spanX,
            spanY = spanY,
            appWidgetId = appWidgetId,
            packageName = packageName,
            customConfig = customConfig
        )
    }
}

// GenericWidget artik legacy/BaseWidget.kt'de yasar (yalnizca cagrilmayan eski sistem
// kullanir). Katalogun kendisi (kayit + boyut tablosu) burada, canli kalir.
