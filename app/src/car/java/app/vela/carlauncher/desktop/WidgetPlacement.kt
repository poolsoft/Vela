package app.vela.carlauncher.desktop

/**
 * Masaustu widget konum ve olcek modeli (WidgetPlacement).
 * UmainLauncher mimarisi temel alinarak tasarlanmistir.
 * Her widget'in dx, dy ofseti ve scale degeri bagimsizdir.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
data class WidgetPlacement(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val page: Int = 0,
    val widthScale: Float = scale,
    val heightScale: Float = scale,
    val opacity: Float = 1f,
    val backgroundColor: Long = 0xF0141624,
    // Hucre modu alanlari (Launcher tarzi grid): cellX/Y -1 ise henuz yerlesmemistir,
    // solver en yakin bos hucreye yerlestirir. Serbest modda kullanilmaz.
    val cellX: Int = -1,
    val cellY: Int = -1,
    val spanX: Int = 0,
    val spanY: Int = 0
)

/**
 * Olcek sinirlari (PlacementBounds). MovableWidget ve WorkspaceGrid ayni sinirlari
 * kullanmak zorunda; aksi halde buyutulen widget resolve sonrasi geri kuculur.
 */
object PlacementBounds {
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.5f
}

object WidgetIds {
    fun type(id: String): String = id.substringBefore("#")
    const val CLOCK = "clock"
    const val MUSIC = "music"
    const val SPEEDOMETER = "speedometer"
    const val STATUS = "status"
    const val DOCK = "dock"
    const val COMBINED = "combined"
    const val WEATHER = "weather"
    const val COMPASS = "compass"
    const val OBD = "obd"
    const val AW_PREFIX = "aw_"
    const val APP_PREFIX = "app_"
}
