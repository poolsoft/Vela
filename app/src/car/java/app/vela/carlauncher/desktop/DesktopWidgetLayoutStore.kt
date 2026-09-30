package app.vela.carlauncher.desktop

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Masaustu widget yerlesimlerini kalici olarak saklayan magaza (DesktopWidgetLayoutStore).
 * UmainLauncher'in parseLayout ve formatLayout mantigini uygular.
 * Bir widget'in boyutu degistiginde yalnizca o widget guncellenir;
 * komsu widget'larin boyutu veya konumu asla bozulmaz.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class DesktopWidgetLayoutStore private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "vela_desktop_widget_layout"
        private const val KEY_LAYOUT = "widget_layout"
        private const val KEY_ACTIVE_WIDGETS = "active_widgets"
        private const val KEY_PAGE_COUNT = "page_count"

        @Volatile
        private var instance: DesktopWidgetLayoutStore? = null

        fun getInstance(context: Context): DesktopWidgetLayoutStore {
            return instance ?: synchronized(this) {
                instance ?: DesktopWidgetLayoutStore(context.applicationContext).also { instance = it }
            }
        }

        val DEFAULT_PLACEMENTS = mapOf(
            WidgetIds.CLOCK to WidgetPlacement(dx = 30f, dy = 24f, scale = 1.0f),
            WidgetIds.STATUS to WidgetPlacement(dx = 320f, dy = 24f, scale = 1.0f),
            WidgetIds.SPEEDOMETER to WidgetPlacement(dx = 30f, dy = 140f, scale = 1.0f),
            WidgetIds.MUSIC to WidgetPlacement(dx = 380f, dy = 140f, scale = 1.0f),
            WidgetIds.COMBINED to WidgetPlacement(dx = 30f, dy = 140f, scale = 1.0f),
            WidgetIds.WEATHER to WidgetPlacement(dx = 30f, dy = 320f, scale = 1.0f),
            WidgetIds.COMPASS to WidgetPlacement(dx = 200f, dy = 320f, scale = 1.0f),
            WidgetIds.OBD to WidgetPlacement(dx = 380f, dy = 320f, scale = 1.0f),
            WidgetIds.DOCK to WidgetPlacement(dx = 30f, dy = 440f, scale = 1.0f)
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _layout = MutableStateFlow<Map<String, WidgetPlacement>>(loadLayout())
    val layout: StateFlow<Map<String, WidgetPlacement>> = _layout.asStateFlow()

    private val _activeWidgets = MutableStateFlow<Set<String>>(loadActiveWidgets())
    val activeWidgets: StateFlow<Set<String>> = _activeWidgets.asStateFlow()

    private val _pageCount = MutableStateFlow(
        maxOf(1, prefs.getInt(KEY_PAGE_COUNT, 1), (_layout.value.values.maxOfOrNull { it.page } ?: 0) + 1)
    )
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private fun loadLayout(): Map<String, WidgetPlacement> {
        val raw = prefs.getString(KEY_LAYOUT, null)
        val loaded = parseLayout(raw)
        return if (loaded.isEmpty()) DEFAULT_PLACEMENTS else loaded
    }

    private fun loadActiveWidgets(): Set<String> {
        val saved = prefs.getStringSet(KEY_ACTIVE_WIDGETS, null)
        return saved ?: setOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.SPEEDOMETER, WidgetIds.MUSIC, WidgetIds.DOCK)
    }

    fun setPlacement(id: String, placement: WidgetPlacement) {
        val current = _layout.value.toMutableMap()
        current[id] = placement
        prefs.edit().putString(KEY_LAYOUT, formatLayout(current)).apply()
        _layout.value = current
        ensurePageCount(placement.page + 1)
    }

    fun addPage(): Int {
        val page = _pageCount.value
        ensurePageCount(page + 1)
        return page
    }

    fun ensurePageCount(count: Int) {
        if (count <= _pageCount.value) return
        prefs.edit().putInt(KEY_PAGE_COUNT, count).apply()
        _pageCount.value = count
    }

    fun removePage(page: Int): Boolean {
        if (_pageCount.value <= 1 || page !in 0 until _pageCount.value) return false
        val targetPage = (page - 1).coerceAtLeast(0)
        val current = _layout.value.mapValues { (_, placement) ->
            when {
                placement.page == page -> placement.copy(page = targetPage)
                placement.page > page -> placement.copy(page = placement.page - 1)
                else -> placement
            }
        }
        val newCount = _pageCount.value - 1
        prefs.edit()
            .putString(KEY_LAYOUT, formatLayout(current))
            .putInt(KEY_PAGE_COUNT, newCount)
            .apply()
        _layout.value = current
        _pageCount.value = newCount
        return true
    }

    fun removePlacement(id: String) {
        val current = _layout.value.toMutableMap()
        current.remove(id)
        prefs.edit().putString(KEY_LAYOUT, formatLayout(current)).apply()
        _layout.value = current

        val active = _activeWidgets.value.toMutableSet()
        active.remove(id)
        prefs.edit().putStringSet(KEY_ACTIVE_WIDGETS, active).apply()
        _activeWidgets.value = active
    }

    fun addWidget(id: String, initialPlacement: WidgetPlacement? = null) {
        val active = _activeWidgets.value.toMutableSet()
        active.add(id)
        prefs.edit().putStringSet(KEY_ACTIVE_WIDGETS, active).apply()
        _activeWidgets.value = active

        if (initialPlacement != null || !_layout.value.containsKey(id)) {
            val p = initialPlacement ?: DEFAULT_PLACEMENTS[id] ?: WidgetPlacement(dx = 50f, dy = 100f, scale = 1.0f)
            setPlacement(id, p)
        }
    }

    fun resetLayout() {
        prefs.edit().remove(KEY_LAYOUT).remove(KEY_ACTIVE_WIDGETS).putInt(KEY_PAGE_COUNT, 1).apply()
        _layout.value = DEFAULT_PLACEMENTS
        _activeWidgets.value = setOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.SPEEDOMETER, WidgetIds.MUSIC, WidgetIds.DOCK)
        _pageCount.value = 1
    }

    private fun parseLayout(raw: String?): Map<String, WidgetPlacement> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            runCatching {
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0]
                val coords = parts[1].split(",")
                val dx = coords.getOrNull(0)?.toFloatOrNull() ?: 0f
                val dy = coords.getOrNull(1)?.toFloatOrNull() ?: 0f
                val scale = coords.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                val page = coords.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val widthScale = coords.getOrNull(4)?.toFloatOrNull() ?: scale
                val heightScale = coords.getOrNull(5)?.toFloatOrNull() ?: scale
                id to WidgetPlacement(dx, dy, scale, page, widthScale, heightScale)
            }.getOrNull()
        }.toMap()
    }

    private fun formatLayout(map: Map<String, WidgetPlacement>): String {
        return map.entries.joinToString(";") { (id, p) ->
            "$id:${p.dx},${p.dy},${p.scale},${p.page},${p.widthScale},${p.heightScale}"
        }
    }
}
