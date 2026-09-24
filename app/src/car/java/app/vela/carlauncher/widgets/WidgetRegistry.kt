package app.vela.carlauncher.widgets

import android.content.Context

/**
 * Dinamik Widget Kayit ve Katalog Sistemi.
 * Yeni widget tipleri buraya register edilir ve UI katalogdan dinamik olarak secer.
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

    data class WidgetEntry(
        val typeId: String,
        val displayName: String,
        val description: String,
        val defaultSize: BaseWidget.WidgetSize,
        val supportedSizes: List<BaseWidget.WidgetSize>,
        val creator: (id: String, pageIndex: Int, size: BaseWidget.WidgetSize) -> BaseWidget
    )

    private val availableWidgets = mutableListOf<WidgetEntry>()

    init {
        register(
            WidgetEntry(
                typeId = TYPE_COMBINED,
                displayName = "Dashboard (Saat + Hız)",
                description = "Saat, hız ve temel telemetriyi tek kartta birleştiren ana gösterge",
                defaultSize = BaseWidget.WidgetSize.LARGE,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE)
            ) { id, page, size -> GenericWidget(id, TYPE_COMBINED, "Dashboard", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_SPEED,
                displayName = "Hız Göstergesi",
                description = "Büyük dijital hız, hız sınırı ve aşırı hız uyarısı",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE)
            ) { id, page, size -> GenericWidget(id, TYPE_SPEED, "Hız Göstergesi", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_CLOCK,
                displayName = "Dijital Saat & Tarih",
                description = "Modern neon saat, tarih ve gün bilgisi",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM)
            ) { id, page, size -> GenericWidget(id, TYPE_CLOCK, "Dijital Saat", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_MUSIC,
                displayName = "Müzik Çalar",
                description = "Çalan şarkı, albüm kapağı ve medya kontrolleri",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE)
            ) { id, page, size -> GenericWidget(id, TYPE_MUSIC, "Müzik Çalar", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_WEATHER,
                displayName = "Hava Durumu",
                description = "Anlık hava durumu, sıcaklık ve ikon",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM)
            ) { id, page, size -> GenericWidget(id, TYPE_WEATHER, "Hava Durumu", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_COMPASS,
                displayName = "Pusula & Yön",
                description = "Canlı pusula derecesi ve seyahat yönü",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                supportedSizes = listOf(BaseWidget.WidgetSize.SMALL, BaseWidget.WidgetSize.MEDIUM)
            ) { id, page, size -> GenericWidget(id, TYPE_COMPASS, "Pusula", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_OBD,
                displayName = "OBD2 / Araç Verileri",
                description = "Motor devri (RPM), hararet sıcaklığı ve akü voltajı",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE)
            ) { id, page, size -> GenericWidget(id, TYPE_OBD, "OBD2 Verileri", size, page) }
        )

        register(
            WidgetEntry(
                typeId = TYPE_SHORTCUTS,
                displayName = "Uygulama Kısayolları",
                description = "Hızlı erişim için favori uygulamalar grid listesi",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                supportedSizes = listOf(BaseWidget.WidgetSize.MEDIUM, BaseWidget.WidgetSize.LARGE)
            ) { id, page, size -> GenericWidget(id, TYPE_SHORTCUTS, "Kısayollar", size, page) }
        )
    }

    fun register(entry: WidgetEntry) {
        availableWidgets.removeAll { it.typeId == entry.typeId }
        availableWidgets.add(entry)
    }

    fun getAvailableWidgets(): List<WidgetEntry> = availableWidgets.toList()

    fun createWidget(typeId: String, pageIndex: Int = 0, size: BaseWidget.WidgetSize? = null): BaseWidget? {
        val entry = availableWidgets.find { it.typeId == typeId } ?: return null
        val uniqueId = "${typeId}_${System.currentTimeMillis()}"
        val chosenSize = size ?: entry.defaultSize
        return entry.creator(uniqueId, pageIndex, chosenSize)
    }
}

/**
 * Dinamik widget veri tutucusu
 */
class GenericWidget(
    id: String,
    typeId: String,
    title: String,
    size: WidgetSize,
    pageIndex: Int = 0
) : BaseWidget(id, typeId, title, size, pageIndex)
