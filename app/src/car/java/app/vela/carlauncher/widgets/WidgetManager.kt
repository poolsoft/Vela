package app.vela.carlauncher.widgets

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Masaustu widget motorunu ve yerlesimini yoneten merkezi yonetici (WidgetManager).
 * - SharedPreferences uzerinde JSON olarak widget sayfalarini, boyutlarini ve hucrelerini saklar.
 * - Dinamik widget ekleme, silme, boyutlandirma ve sayfa tasima islemlerini reaktif Flow ile sunar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class WidgetManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "vela_car_launcher_widgets"
        private const val KEY_WIDGET_CONFIG = "widget_config"

        @Volatile
        private var instance: WidgetManager? = null

        fun getInstance(context: Context): WidgetManager {
            return instance ?: synchronized(this) {
                instance ?: WidgetManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _widgetsFlow = MutableStateFlow<List<BaseWidget>>(emptyList())
    val widgetsFlow: StateFlow<List<BaseWidget>> = _widgetsFlow.asStateFlow()

    init {
        loadConfig()
    }

    fun reload() { loadConfig() }

    fun getWidgetsForPage(pageIndex: Int): List<BaseWidget> {
        return _widgetsFlow.value.filter { it.pageIndex == pageIndex && it.isVisible }
    }

    fun getPageCount(): Int {
        val maxPage = _widgetsFlow.value.maxOfOrNull { it.pageIndex } ?: 0
        return maxOf(2, maxPage + 1)
    }

    fun addWidget(typeId: String, pageIndex: Int = 0, size: BaseWidget.WidgetSize? = null) {
        val newWidget = WidgetRegistry.createWidget(typeId, pageIndex, size) ?: return
        val currentList = _widgetsFlow.value.toMutableList()
        currentList.add(newWidget)
        _widgetsFlow.value = currentList
        saveConfig()
    }

    fun removeWidget(widgetId: String) {
        val currentList = _widgetsFlow.value.toMutableList()
        currentList.firstOrNull { it.id == widgetId }?.typeId?.removePrefix("system:")?.toIntOrNull()?.let { SystemWidgets.delete(context, it) }
        currentList.removeAll { it.id == widgetId }
        _widgetsFlow.value = currentList
        saveConfig()
    }

    fun resizeWidget(widgetId: String, newSize: BaseWidget.WidgetSize) {
        val currentList = _widgetsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == widgetId }
        if (index != -1) {
            val item = currentList[index]
            currentList[index] = copyWidget(item, size = newSize)
            _widgetsFlow.value = currentList.toList()
            saveConfig()
        }
    }

    fun moveWidgetToPage(widgetId: String, targetPage: Int) {
        val currentList = _widgetsFlow.value.toMutableList()
        val item = currentList.find { it.id == widgetId } ?: return
        val index = currentList.indexOf(item)
        currentList[index] = copyWidget(item, page = targetPage.coerceIn(0, 19))
        _widgetsFlow.value = currentList.toList()
        saveConfig()
    }

    private fun copyWidget(item: BaseWidget, size: BaseWidget.WidgetSize = item.size, page: Int = item.pageIndex): BaseWidget =
        GenericWidget(item.id, item.typeId, item.title, size, page).apply {
            cellX = item.cellX; cellY = item.cellY; isVisible = item.isVisible
        }

    fun addSystemWidget(id: Int, title: String, page: Int) {
        if (_widgetsFlow.value.any { it.typeId == "system:$id" }) return
        _widgetsFlow.value = _widgetsFlow.value + GenericWidget("system_$id", "system:$id", title, BaseWidget.WidgetSize.LARGE, page)
        saveConfig()
    }

    fun swapWidgets(first: String, second: String) {
        val items = _widgetsFlow.value.toMutableList()
        val a = items.indexOfFirst { it.id == first }
        val b = items.indexOfFirst { it.id == second }
        if (a < 0 || b < 0 || items[a].pageIndex != items[b].pageIndex) return
        java.util.Collections.swap(items, a, b)
        _widgetsFlow.value = items
        saveConfig()
    }

    private fun loadConfig() {
        val jsonStr = prefs.getString(KEY_WIDGET_CONFIG, null)
        if (jsonStr.isNullOrBlank()) {
            loadDefaultWidgets()
            return
        }

        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<BaseWidget>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                val typeId = obj.optString("typeId")
                val title = obj.optString("title", "Widget")
                val sizeStr = obj.optString("size", "MEDIUM")
                val size = try { BaseWidget.WidgetSize.valueOf(sizeStr) } catch (e: Exception) { BaseWidget.WidgetSize.MEDIUM }
                val pageIndex = obj.optInt("pageIndex", 0)
                val isVisible = obj.optBoolean("isVisible", true)

                val widget = GenericWidget(id, typeId, title, size, pageIndex).apply {
                    this.isVisible = isVisible
                }
                list.add(widget)
            }
            _widgetsFlow.value = list
        } catch (e: Exception) {
            e.printStackTrace()
            loadDefaultWidgets()
        }
    }

    private fun loadDefaultWidgets() {
        val defaults = mutableListOf<BaseWidget>()

        // Sayfa 0 (Ana Surus Ekrani)
        defaults.add(GenericWidget("w_comb_0", WidgetRegistry.TYPE_COMBINED, "Dashboard", BaseWidget.WidgetSize.LARGE, 0))
        defaults.add(GenericWidget("w_music_0", WidgetRegistry.TYPE_MUSIC, "Müzik Çalar", BaseWidget.WidgetSize.MEDIUM, 0))
        defaults.add(GenericWidget("w_speed_0", WidgetRegistry.TYPE_SPEED, "Hız Göstergesi", BaseWidget.WidgetSize.MEDIUM, 0))
        defaults.add(GenericWidget("w_clock_0", WidgetRegistry.TYPE_CLOCK, "Dijital Saat", BaseWidget.WidgetSize.SMALL, 0))

        // Sayfa 1 (Telemetri ve Kisisel Kisayollar)
        defaults.add(GenericWidget("w_short_1", WidgetRegistry.TYPE_SHORTCUTS, "Uygulama Kısayolları", BaseWidget.WidgetSize.LARGE, 1))
        defaults.add(GenericWidget("w_weath_1", WidgetRegistry.TYPE_WEATHER, "Hava Durumu", BaseWidget.WidgetSize.SMALL, 1))
        defaults.add(GenericWidget("w_comp_1", WidgetRegistry.TYPE_COMPASS, "Pusula & Yön", BaseWidget.WidgetSize.SMALL, 1))
        defaults.add(GenericWidget("w_obd_1", WidgetRegistry.TYPE_OBD, "OBD2 / Araç Verileri", BaseWidget.WidgetSize.MEDIUM, 1))

        _widgetsFlow.value = defaults
        saveConfig()
    }

    private fun saveConfig() {
        try {
            val array = JSONArray()
            for (w in _widgetsFlow.value) {
                val obj = JSONObject().apply {
                    put("id", w.id)
                    put("typeId", w.typeId)
                    put("title", w.title)
                    put("size", w.size.name)
                    put("pageIndex", w.pageIndex)
                    put("isVisible", w.isVisible)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_WIDGET_CONFIG, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
