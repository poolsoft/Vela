package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf

/** Rendering budget, independent of palette and the user's individual layer preferences. */
object MapRenderProfile {
    const val STANDARD = "standard"
    const val LIGHT = "light"
    const val CUSTOM = "custom"
    private const val KEY = "map_render_profile"
    const val FPS_KEY = "map_custom_max_fps"
    const val TILT_KEY = "map_custom_tilt"
    val selected = mutableStateOf(STANDARD)
    val customMaxFps = mutableIntStateOf(30)
    val customTilt = mutableStateOf(false)
    fun isLight(profile: String = selected.value) = profile == LIGHT
    fun maxFps(profile: String = selected.value) = when (profile) { LIGHT -> 30; CUSTOM -> customMaxFps.intValue; else -> 0 }
    fun flatCamera(profile: String = selected.value) = isLight(profile) || (profile == CUSTOM && !customTilt.value)
    fun effective(profile: String, lowEnd: Boolean, startup: Boolean): String =
        if (startup || (lowEnd && profile == STANDARD)) LIGHT else profile
    private fun valid(value: String?) = when (value) { LIGHT, CUSTOM -> value; else -> STANDARD }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
        selected.value = valid(prefs.getString(KEY, STANDARD))
        customMaxFps.intValue = prefs.getInt(FPS_KEY, 30).coerceIn(15, 60)
        customTilt.value = prefs.getBoolean(TILT_KEY, false)
    }

    fun set(context: Context, value: String) {
        selected.value = valid(value)
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .edit().putString(KEY, selected.value).apply()
    }

    fun setMaxFps(context: Context, value: Int) {
        customMaxFps.intValue = value.coerceIn(15, 60)
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .edit().putInt(FPS_KEY, customMaxFps.intValue).apply()
    }

    fun setTilt(context: Context, value: Boolean) {
        customTilt.value = value
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(TILT_KEY, value).apply()
    }
}
