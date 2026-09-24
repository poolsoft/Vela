package app.vela.carlauncher.apps

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import app.vela.carlauncher.model.AppShortcut
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.model.LaunchMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * App Dock Yoneticisi (AppDockManager).
 * OsmAnd Car Launcher ile birebir JSON tabanli kisayol kaydetme/yukleme,
 * dahili uygulamalar (InternalApp) ve yuklu Android uygulamalarini yonetir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class AppDockManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "car_launcher_app_dock"
        private const val KEY_SHORTCUTS = "shortcuts"

        @Volatile
        private var instance: AppDockManager? = null

        fun getInstance(context: Context): AppDockManager {
            return instance ?: synchronized(this) {
                instance ?: AppDockManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _kisayollar = MutableStateFlow<List<AppShortcut>>(emptyList())
    val kisayollar: StateFlow<List<AppShortcut>> = _kisayollar.asStateFlow()

    init {
        yukleKisayollar()
    }

    fun yukleKisayollar() {
        val json = prefs.getString(KEY_SHORTCUTS, null)
        val liste = mutableListOf<AppShortcut>()

        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                val pm = context.packageManager

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val pkg = obj.getString("package")
                    val order = obj.optInt("order", i)
                    val launchModeStr = obj.optString("launchMode", LaunchMode.FULL_SCREEN.name)
                    val launchMode = try {
                        LaunchMode.valueOf(launchModeStr)
                    } catch (e: Exception) {
                        LaunchMode.FULL_SCREEN
                    }

                    val shortcut = cozAppShortcut(pkg, order, launchMode, pm)
                    if (shortcut != null) {
                        liste.add(shortcut)
                    }
                }
            } catch (e: Exception) {
                liste.clear()
            }
        }

        if (json.isNullOrBlank()) {
            liste.addAll(varsayilanKisayollariOlustur())
            kaydetJson(liste)
        }

        _kisayollar.value = liste.sortedBy { it.sira }
    }

    private fun cozAppShortcut(pkg: String, order: Int, launchMode: LaunchMode, pm: PackageManager): AppShortcut? {
        if (InternalApp.isInternalUri(pkg)) {
            val internal = InternalApp.fromUri(pkg) ?: return null
            return AppShortcut(
                paketAdi = internal.uri,
                ad = internal.getAd(context),
                ikon = internal.getIkon(context),
                sira = order,
                launchMode = launchMode
            )
        }

        return try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            val ad = pm.getApplicationLabel(appInfo).toString()
            val ikon = pm.getApplicationIcon(appInfo)
            AppShortcut(
                paketAdi = pkg,
                ad = ad,
                ikon = ikon,
                sira = order,
                launchMode = launchMode
            )
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun varsayilanKisayollariOlustur(): List<AppShortcut> {
        val pm = context.packageManager
        val varsayilanlar = listOf(
            "internal://neon_dashboard",
            "internal://dashboard",
            "internal://music",
            "internal://settings",
            "com.spotify.music",
            "com.google.android.dialer"
        )

        val liste = mutableListOf<AppShortcut>()
        var order = 0

        for (pkg in varsayilanlar) {
            val shortcut = cozAppShortcut(pkg, order, LaunchMode.FULL_SCREEN, pm)
            if (shortcut != null) {
                liste.add(shortcut)
                order++
            }
        }
        return liste
    }

    private fun kaydetJson(liste: List<AppShortcut>) {
        try {
            val array = JSONArray()
            for (s in liste) {
                val obj = JSONObject().apply {
                    put("package", s.paketAdi)
                    put("order", s.sira)
                    put("launchMode", s.launchMode.name)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun kisayolEkle(paketAdi: String): Boolean {
        val guncel = _kisayollar.value.toMutableList()
        if (guncel.any { it.paketAdi == paketAdi }) return false

        val pm = context.packageManager
        val shortcut = cozAppShortcut(paketAdi, guncel.size, LaunchMode.FULL_SCREEN, pm) ?: return false

        guncel.add(shortcut)
        kaydetJson(guncel)
        _kisayollar.value = guncel
        return true
    }

    fun kisayolTasi(paketAdi: String, delta: Int) {
        val items = _kisayollar.value.toMutableList()
        val from = items.indexOfFirst { it.paketAdi == paketAdi }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, items.lastIndex)
        if (to == from) return
        items.add(to, items.removeAt(from))
        val ordered = items.mapIndexed { index, item -> item.copy(sira = index) }
        kaydetJson(ordered)
        _kisayollar.value = ordered
    }

    fun kisayolSil(paketAdi: String): Boolean {
        val guncel = _kisayollar.value.toMutableList()
        val index = guncel.indexOfFirst { it.paketAdi == paketAdi }
        if (index == -1) return false

        guncel.removeAt(index)
        val yenidenSirali = guncel.mapIndexed { idx, item -> item.copy(sira = idx) }
        kaydetJson(yenidenSirali)
        _kisayollar.value = yenidenSirali
        return true
    }
}
