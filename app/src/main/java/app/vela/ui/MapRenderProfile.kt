package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf

/** Rendering budget, independent of palette and the user's individual layer preferences. */
object MapRenderProfile {
    const val AUTO = "auto"
    const val STANDARD = "standard"
    const val LIGHT = "light"
    const val CUSTOM = "custom"
    private const val KEY = "map_render_profile"
    const val FPS_KEY = "map_custom_max_fps"
    const val TILT_KEY = "map_custom_tilt"
    const val AUTO_BUDGET_KEY = "map_auto_device_budget"
    val selected = mutableStateOf(STANDARD)
    val customMaxFps = mutableIntStateOf(30)
    val customTilt = mutableStateOf(false)
    val autoDeviceBudget = mutableStateOf(false)
    // Session-only downgrade: never overwrite the user's profile or layer preferences.
    val slowRendering = mutableStateOf(false)
    internal class FrameBudget {
        private var start = 0L
        private var frames = 0
        private var slow = 0
        private var slowWindows = 0
        fun sample(nowMs: Long, durationMs: Long): Boolean {
            if (start == 0L) start = nowMs
            frames++
            if (durationMs >= 80) slow++
            if (nowMs - start < 10_000) return false
            if (frames >= 20) slowWindows = if (slow * 2 >= frames) slowWindows + 1 else 0
            else slowWindows = 0
            start = nowMs; frames = 0; slow = 0
            return slowWindows >= 3
        }
    }
    fun isLight(profile: String = selected.value) = profile == LIGHT
    fun maxFps(profile: String = selected.value) = when (profile) { LIGHT -> 30; CUSTOM -> customMaxFps.intValue; else -> 0 }
    fun flatCamera(profile: String = selected.value) = isLight(profile) || (profile == CUSTOM && !customTilt.value)
    fun effective(profile: String, lowEnd: Boolean, startup: Boolean, automaticBudget: Boolean = false): String =
        if (startup) LIGHT
        else if (profile == AUTO) if (lowEnd) LIGHT else STANDARD
        else if (automaticBudget && lowEnd && profile == STANDARD) LIGHT else profile
    private fun valid(value: String?) = when (value) { AUTO, LIGHT, CUSTOM -> value; else -> STANDARD }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
        selected.value = valid(prefs.getString(KEY, STANDARD))
        customMaxFps.intValue = prefs.getInt(FPS_KEY, 30).coerceIn(15, 60)
        customTilt.value = prefs.getBoolean(TILT_KEY, false)
        autoDeviceBudget.value = prefs.getBoolean(AUTO_BUDGET_KEY, false)
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

    fun setAutoDeviceBudget(context: Context, value: Boolean) {
        autoDeviceBudget.value = value
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_BUDGET_KEY, value).apply()
    }
}
