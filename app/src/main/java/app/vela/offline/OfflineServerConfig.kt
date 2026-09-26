package app.vela.offline

import android.content.Context
import app.vela.BuildConfig

/**
 * Cevrimdisi harita ve POI paketleri icin sunucu yapilandirmasi.
 * Resmi Vela sunucusu, Poolsoft fork sunucusu (Turkiye paketleri) ve ozel sunucu destegi saglar.
 */
object OfflineServerConfig {
    private const val PREFS = "vela_settings"
    private const val KEY_SERVER_MODE = "offline_server_mode"
    private const val KEY_CUSTOM_POI_URL = "offline_custom_poi_manifest_url"

    const val MODE_HYBRID = 0    // Hibrit: Poolsoft (Turkiye) + Resmi Vela (Diger Ulkeler) - Onerilen
    const val MODE_POOLSOFT = 1  // Sadece Poolsoft Fork
    const val MODE_UPSTREAM = 2  // Sadece Resmi Vela
    const val MODE_CUSTOM = 3    // Ozel URL

    // Poolsoft fork varsayilan manifest URL'leri (GitHub Releases veya raw main)
    const val POOLSOFT_POI_MANIFEST_URL =
        "https://raw.githubusercontent.com/poolsoft/Vela/main/releases/poi-packs/poi-pack-manifest.json"

    fun getServerMode(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SERVER_MODE, MODE_HYBRID)
    }

    fun setServerMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SERVER_MODE, mode)
            .apply()
    }

    fun getCustomPoiUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_POI_URL, "") ?: ""
    }

    fun setCustomPoiUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CUSTOM_POI_URL, url.trim())
            .apply()
    }

    /**
     * Aktif yapilandirmaya gore taranacak POI manifest URL listesini dondurur.
     */
    fun getPoiManifestUrls(context: Context): List<String> {
        return when (getServerMode(context)) {
            MODE_POOLSOFT -> listOf(POOLSOFT_POI_MANIFEST_URL)
            MODE_UPSTREAM -> listOf(BuildConfig.POI_PACK_MANIFEST_URL)
            MODE_CUSTOM -> {
                val custom = getCustomPoiUrl(context)
                if (custom.isNotBlank()) listOf(custom) else listOf(BuildConfig.POI_PACK_MANIFEST_URL)
            }
            else -> { // MODE_HYBRID (Varsayilan): Poolsoft once, Upstream sonra (Turkiye paketi oncelikli)
                listOf(POOLSOFT_POI_MANIFEST_URL, BuildConfig.POI_PACK_MANIFEST_URL)
            }
        }
    }
}
