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
    val scale: Float = 1f
)

object WidgetIds {
    const val CLOCK = "clock"
    const val MUSIC = "music"
    const val SPEEDOMETER = "speedometer"
    const val STATUS = "status"
    const val DOCK = "dock"
    const val MINIMAP = "minimap"
    const val AW_PREFIX = "aw_"
}
