package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * "Sadece cevrimdisi calis" (Settings > Offline maps, pref `offline_only`, OFF by default).
 *
 * The head unit's rule: nothing reaches the network unless the user asks for it. With the switch
 * on every remote path is skipped - the Roboto style patch, the camera manifest, the launch update
 * check, every region/routing/places manifest - and the region downloads are refused, so the app
 * runs from the files a backup put on the device. The device may still have Wi-Fi; the point is
 * that Vela never waits on it.
 *
 * The map style itself is on disk either way (see `MapFonts`); this switch is what stops the
 * BACKGROUND traffic and the user-facing download buttons.
 */
object OfflineMode {
    val on = mutableStateOf(false)

    fun init(context: Context) { on.value = prefs(context).getBoolean(KEY, false) }

    fun set(context: Context, value: Boolean) {
        on.value = value
        prefs(context).edit().putBoolean(KEY, value).apply()
    }

    /** Remote work is allowed only while the user did not ask for offline-only. */
    val networkAllowed: Boolean get() = !on.value

    private fun prefs(c: Context) = c.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
    private const val KEY = "offline_only"
}
