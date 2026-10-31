package app.vela.carlauncher.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import app.vela.carlauncher.widgets.InterlockingClockView

/** One saved appearance and renderer for the panel and its live settings preview. */
internal data class SmallClockAppearance(
    val palette: Int = 6,
    val primary: Int = 0xFFB8DDD4.toInt(),
    val secondary: Int = 0xFFD8CDE4.toInt(),
    val dual: Boolean = true,
    val embossed: Boolean = false,
    val scale: Float = 0.88f,
    val spacing: Float = -0.16f,
    val colonSpacing: Float = 0.06f,
    val outline: Float = 1.15f,
    val outlineColor: Int = Color.WHITE,
    val opacity: Int = 100,
    val font: Int = 0,
    val format: String = "system",
    val seconds: Boolean = false,
    val showColon: Boolean = true,
    val showDate: Boolean = false,
) {
    fun normalized() = copy(palette = palette.coerceIn(0, 8),
        scale = (if (scale.isFinite()) scale else 0.88f).let { s ->
            // Migration: Eger eski surumlerden kucuk scale (0.15f..0.50f) geldiyse %88'e uyarla
            if (s in 0.15f..0.50f) (s / 0.28f * 0.88f).coerceIn(0.20f, 0.98f)
            else if (s > 0.98f) 0.98f
            else s.coerceIn(0.20f, 0.98f)
        },
        spacing = (if (spacing.isFinite()) spacing else -0.16f).coerceIn(-0.26f, 0.12f),
        colonSpacing = (if (colonSpacing.isFinite()) colonSpacing else 0.06f).coerceIn(0f, 0.2f),
        outline = (if (outline.isFinite()) outline else 1.15f).coerceIn(0.5f, 3f),
        opacity = opacity.coerceIn(0, 100), font = font.coerceIn(0, 3),
        format = format.takeIf { it in setOf("system", "24", "12") } ?: "system")

    fun timePattern(context: Context): String {
        val use24 = format == "24" || (format == "system" && android.text.format.DateFormat.is24HourFormat(context))
        val sep = if (showColon) ":" else ""
        return (if (use24) "HH${sep}mm" else "h${sep}mm") + if (seconds) "${sep}ss" else ""
    }

    fun datePattern(): String = "dd/MM EEE"

    fun apply(view: InterlockingClockView, context: Context, background: Pair<Int, Int>) {
        val soft = if (android.os.Build.VERSION.SDK_INT >= 31)
            context.getColor(android.R.color.system_accent1_100) to context.getColor(android.R.color.system_accent2_200)
            else 0xFFEBE8DB.toInt() to 0xFFD7C8E7.toInt()
        val colors = when (palette) {
            1 -> androidx.core.content.ContextCompat.getColor(context, app.vela.R.color.cl_primary) to secondary
            2 -> 0xFFE7D5B4.toInt() to secondary
            3 -> Color.TRANSPARENT to Color.TRANSPARENT
            4 -> Color.WHITE to secondary
            5 -> primary to secondary
            6 -> soft
            7 -> background
            8 -> (if (primary != 0xFFB8DDD4.toInt()) primary else Color.WHITE) to Color.TRANSPARENT
            else -> 0xFFB8DDD4.toInt() to secondary
        }
        val night = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        fun fill(color: Int): Int = if (color == Color.TRANSPARENT) color else
            (color and 0xFFFFFF) or ((opacity * (if (night) 204 else 255) / 100) shl 24)
        view.fillColor = fill(colors.first)
        view.minuteFillColor = if ((dual || palette == 8) && palette != 3) fill(colors.second) else null
        view.embossed = embossed
        view.digitSpacingEm = spacing
        view.colonSpacingEm = colonSpacing
        view.outlineWidthDp = outline
        view.setTextColor(outlineColor)
        view.clockFont = font
        view.fitTextToBounds = true
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit().putInt("clock_color", palette).putInt("clock_custom_primary", primary)
            .putInt("clock_custom_secondary", secondary).putBoolean("clock_two_colors", dual)
            .putBoolean("clock_embossed", embossed).putFloat("clock_scale", scale)
            .putFloat("clock_spacing", spacing).putFloat("clock_colon_spacing", colonSpacing)
            .putFloat("clock_outline", outline).putInt("clock_outline_color", outlineColor)
            .putInt("clock_opacity", opacity).putInt("clock_font", font)
            .putString("clock_format", format).putBoolean("clock_seconds", seconds)
            .putBoolean("clock_show_colon", showColon).putBoolean("clock_show_date", showDate).apply()
    }

    companion object {
        fun backgroundColors(source: android.graphics.drawable.Drawable?): Pair<Int, Int> {
            val drawable = source ?: return Color.rgb(235, 232, 219) to Color.rgb(215, 200, 231)
            val bitmap = android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888)
            val bounds = android.graphics.Rect(drawable.bounds)
            try {
                drawable.setBounds(0, 0, 12, 12)
                drawable.draw(android.graphics.Canvas(bitmap))
                var red = 0; var green = 0; var blue = 0
                for (y in 0 until 12) for (x in 0 until 12) {
                    val pixel = bitmap.getPixel(x, y)
                    red += Color.red(pixel); green += Color.green(pixel); blue += Color.blue(pixel)
                }
                val hsv = FloatArray(3)
                Color.colorToHSV(Color.rgb(red / 144, green / 144, blue / 144), hsv)
                return Color.HSVToColor(floatArrayOf(hsv[0], 0.12f, 0.94f)) to
                    Color.HSVToColor(floatArrayOf((hsv[0] + 35f) % 360f, 0.18f, 0.87f))
            } finally {
                drawable.bounds = bounds
                bitmap.recycle()
            }
        }

        fun preferences(context: Context): SharedPreferences = context.getSharedPreferences("small_music_panel", Context.MODE_PRIVATE)
        fun read(p: SharedPreferences): SmallClockAppearance {
            val mode = p.getInt("clock_color", 6)
            return SmallClockAppearance(mode, p.getInt("clock_custom_primary", 0xFFB8DDD4.toInt()),
                p.getInt("clock_custom_secondary", 0xFFD8CDE4.toInt()), p.getBoolean("clock_two_colors", mode in setOf(6, 7, 8)),
                p.getBoolean("clock_embossed", false), p.getFloat("clock_scale", 0.88f),
                p.getFloat("clock_spacing", -0.16f), p.getFloat("clock_colon_spacing", 0.06f),
                p.getFloat("clock_outline", 1.15f), p.getInt("clock_outline_color", Color.WHITE),
                p.getInt("clock_opacity", 100), p.getInt("clock_font", 0),
                p.getString("clock_format", "system") ?: "system", p.getBoolean("clock_seconds", false),
                p.getBoolean("clock_show_colon", true), p.getBoolean("clock_show_date", false)).normalized()
        }
    }
}
