package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object MapScreenPosition {
    val mode = mutableStateOf("auto")
    val horizontalBias = mutableStateOf(0.5f)

    fun init(context: Context) {
        mode.value = prefs(context).getString("map_screen_position", "auto") ?: "auto"
        horizontalBias.value = prefs(context).getFloat("map_horizontal_bias", 0.5f).coerceIn(0.25f, 0.75f)
    }

    fun set(context: Context, value: String) {
        if (value !in setOf("center", "bottom", "auto")) return
        mode.value = value
        prefs(context).edit().putString("map_screen_position", value).apply()
    }

    fun setHorizontalBias(context: Context, value: Float) {
        horizontalBias.value = value.coerceIn(0.25f, 0.75f)
        prefs(context).edit().putFloat("map_horizontal_bias", horizontalBias.value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
}
