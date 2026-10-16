package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/** Rendering budget, independent of palette and the user's individual layer preferences. */
object MapRenderProfile {
    const val STANDARD = "standard"
    const val LIGHT = "light"
    private const val KEY = "map_render_profile"
    val selected = mutableStateOf(STANDARD)
    fun isLight() = selected.value == LIGHT

    fun init(context: Context) {
        selected.value = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .getString(KEY, STANDARD).let { if (it == LIGHT) LIGHT else STANDARD }
    }

    fun set(context: Context, value: String) {
        selected.value = if (value == LIGHT) LIGHT else STANDARD
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, selected.value).apply()
    }
}
