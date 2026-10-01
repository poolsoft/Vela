package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object MapScreenPosition {
    val mode = mutableStateOf("auto")

    fun init(context: Context) {
        mode.value = prefs(context).getString("map_screen_position", "auto") ?: "auto"
    }

    fun set(context: Context, value: String) {
        if (value !in setOf("center", "bottom", "auto")) return
        mode.value = value
        prefs(context).edit().putString("map_screen_position", value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
}
