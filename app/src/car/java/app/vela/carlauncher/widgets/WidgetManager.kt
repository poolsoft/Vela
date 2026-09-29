package app.vela.carlauncher.widgets

import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Masaustu widget motorunu ve yerlesimini yoneten merkezi yonetici (WidgetManager).
 * - Launcher 2/3 mimarisi: 8x4 hucreli izgara duzeni (cellX, cellY, spanX, spanY).
 * - SharedPreferences uzerinde JSON olarak widget sayfalarini, boyutlarini ve hucrelerini saklar.
 * - Dinamik widget ekleme, silme, boyutlandirma ve sayfa tasima islemlerini reaktif Flow ile sunar.
 * - CarAppWidgetHost uzerinden sistem widget'larinin yaşam dongusunu yonetir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class WidgetManager private constructor(private val context: Context) {

    companion object {
        const val COL_COUNT = 8
        const val ROW_COUNT = 4

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

    val appWidgetHost: AppWidgetHost = CarAppWidgetHost(context)

    init {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        loadConfig()
    }

    fun startListening() {
        try {
            appWidgetHost.startListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        try {
            appWidgetHost.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reload() {
        loadConfig()
    }

    fun getWidgetsForPage(pageIndex: Int): List<BaseWidget> {
        return _widgetsFlow.value.filter { it.pageIndex == pageIndex && it.isVisible }
    }

    fun getPageCount(): Int {
        val maxPage = _widgetsFlow.value.maxOfOrNull { it.pageIndex } ?: 0
        return maxOf(2, maxPage + 1)
    }

    /**
     * Verilen sayfada spanX ve spanY boyutunda bos hucre arar.
     */
    fun findVacantCell(pageIndex: Int, spanX: Int, spanY: Int): Pair<Int, Int>? {
        val pageWidgets = getWidgetsForPage(pageIndex)
        val occupied = Array(ROW_COUNT) { BooleanArray(COL_COUNT) }

        for (w in pageWidgets) {
            val startX = w.cellX.coerceIn(0, COL_COUNT - 1)
            val startY = w.cellY.coerceIn(0, ROW_COUNT - 1)
            val endX = (startX + w.spanX - 1).coerceIn(0, COL_COUNT - 1)
            val endY = (startY + w.spanY - 1).coerceIn(0, ROW_COUNT - 1)

            for (r in startY..endY) {
                for (c in startX..endX) {
                    occupied[r][c] = true
                }
            }
        }

        for (r in 0..(ROW_COUNT - spanY)) {
            for (c in 0..(COL_COUNT - spanX)) {
                var canFit = true
                for (dr in 0 until spanY) {
                    for (dc in 0 until spanX) {
                        if (occupied[r + dr][c + dc]) {
                            canFit = false
                            break
                        }
                    }
                    if (!canFit) break
                }
                if (canFit) {
                    return Pair(c, r)
                }
            }
        }
        return null
    }

    /**
     * Belirtilen alanin bos olup olmadigini test eder.
     */
    fun isRegionVacant(
        pageIndex: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        ignoreWidgetId: String? = null
    ): Boolean {
        if (cellX < 0 || cellY < 0 || cellX + spanX > COL_COUNT || cellY + spanY > ROW_COUNT) {
            return false
        }

        val pageWidgets = getWidgetsForPage(pageIndex).filter { it.id != ignoreWidgetId }
        for (w in pageWidgets) {
            val wLeft = w.cellX
            val wRight = w.cellX + w.spanX
            val wTop = w.cellY
            val wBottom = w.cellY + w.spanY

            val targetLeft = cellX
            val targetRight = cellX + spanX
            val targetTop = cellY
            val targetBottom = cellY + spanY

            val overlapX = Math.max(0, Math.min(wRight, targetRight) - Math.max(wLeft, targetLeft))
            val overlapY = Math.max(0, Math.min(wBottom, targetBottom) - Math.max(wTop, targetTop))

            if (overlapX > 0 && overlapY > 0) {
                return false
            }
        }
        return true
    }

    /**
     * Standart Car Launcher Widget'i ekle.
     */
    fun addWidget(
        typeId: String,
        pageIndex: Int = 0,
        size: BaseWidget.WidgetSize? = null,
        spanX: Int? = null,
        spanY: Int? = null
    ): BaseWidget? {
        val defaultSpanX = spanX ?: when (size) {
            BaseWidget.WidgetSize.LARGE -> 2
            BaseWidget.WidgetSize.MEDIUM -> 2
            BaseWidget.WidgetSize.SMALL -> 1
            null -> 1
        }
        val defaultSpanY = spanY ?: when (size) {
            BaseWidget.WidgetSize.LARGE -> 2
            BaseWidget.WidgetSize.MEDIUM -> 1
            BaseWidget.WidgetSize.SMALL -> 1
            null -> 1
        }

        val vacant = findVacantCell(pageIndex, defaultSpanX, defaultSpanY)
        val cellX = vacant?.first ?: 0
        val cellY = vacant?.second ?: 0

        val newWidget = WidgetRegistry.createWidget(
            typeId = typeId,
            pageIndex = pageIndex,
            size = size,
            cellX = cellX,
            cellY = cellY,
            spanX = defaultSpanX,
            spanY = defaultSpanY
        ) ?: return null

        val currentList = _widgetsFlow.value.toMutableList()
        currentList.add(newWidget)
        _widgetsFlow.value = currentList
        saveConfig()
        return newWidget
    }

    /**
     * Uygulama Kisayolu ekle (1x1).
     */
    fun addShortcut(packageName: String, label: String, pageIndex: Int = 0): BaseWidget? {
        val vacant = findVacantCell(pageIndex, 1, 1)
        val cellX = vacant?.first ?: 0
        val cellY = vacant?.second ?: 0

        val newWidget = GenericWidget(
            id = "shortcut_${packageName}_${System.currentTimeMillis()}",
            typeId = "shortcut",
            title = label,
            size = BaseWidget.WidgetSize.SMALL,
            pageIndex = pageIndex,
            cellX = cellX,
            cellY = cellY,
            spanX = 1,
            spanY = 1,
            packageName = packageName
        )

        val currentList = _widgetsFlow.value.toMutableList()
        currentList.add(newWidget)
        _widgetsFlow.value = currentList
        saveConfig()
        return newWidget
    }

    fun addSystemWidget(appWidgetId: Int, providerTitle: String, pageIndex: Int): BaseWidget? {
        return addSystemWidget(
            appWidgetId = appWidgetId,
            providerTitle = providerTitle,
            packageName = "",
            pageIndex = pageIndex,
            spanX = 2,
            spanY = 2
        )
    }

    fun addSystemWidget(
        appWidgetId: Int,
        providerTitle: String,
        packageName: String = "",
        pageIndex: Int = 0,
        spanX: Int = 2,
        spanY: Int = 2
    ): BaseWidget? {
        val safeSpanX = spanX.coerceIn(1, COL_COUNT)
        val safeSpanY = spanY.coerceIn(1, ROW_COUNT)
        val vacant = findVacantCell(pageIndex, safeSpanX, safeSpanY)
        val cellX = vacant?.first ?: 0
        val cellY = vacant?.second ?: 0

        val newWidget = GenericWidget(
            id = "appwidget_${appWidgetId}",
            typeId = "system",
            title = providerTitle,
            size = if (safeSpanX > 2 || safeSpanY > 1) BaseWidget.WidgetSize.LARGE else BaseWidget.WidgetSize.MEDIUM,
            pageIndex = pageIndex,
            cellX = cellX,
            cellY = cellY,
            spanX = safeSpanX,
            spanY = safeSpanY,
            appWidgetId = appWidgetId,
            packageName = packageName
        )

        val currentList = _widgetsFlow.value.toMutableList()
        currentList.add(newWidget)
        _widgetsFlow.value = currentList
        saveConfig()
        return newWidget
    }

    /**
     * Widget konumunu ve boyutunu kalici olarak guncelle.
     */
    fun updateWidgetPlacement(
        widgetId: String,
        pageIndex: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int
    ) {
        val currentList = _widgetsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == widgetId }
        if (index != -1) {
            val item = currentList[index]
            item.pageIndex = pageIndex
            item.cellX = cellX.coerceIn(0, COL_COUNT - 1)
            item.cellY = cellY.coerceIn(0, ROW_COUNT - 1)
            item.spanX = spanX.coerceIn(1, COL_COUNT - item.cellX)
            item.spanY = spanY.coerceIn(1, ROW_COUNT - item.cellY)

            item.size = when {
                item.spanX >= 3 || item.spanY >= 2 -> BaseWidget.WidgetSize.LARGE
                item.spanX >= 2 -> BaseWidget.WidgetSize.MEDIUM
                else -> BaseWidget.WidgetSize.SMALL
            }

            _widgetsFlow.value = currentList.toList()
            saveConfig()
        }
    }

    fun removeWidget(widgetId: String) {
        val currentList = _widgetsFlow.value.toMutableList()
        val target = currentList.firstOrNull { it.id == widgetId }
        if (target != null && target.appWidgetId != -1) {
            try {
                appWidgetHost.deleteAppWidgetId(target.appWidgetId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentList.removeAll { it.id == widgetId }
        _widgetsFlow.value = currentList
        saveConfig()
    }

    fun resizeWidget(widgetId: String, newSize: BaseWidget.WidgetSize) {
        val currentList = _widgetsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == widgetId }
        if (index != -1) {
            val item = currentList[index]
            val newSpanX = when (newSize) {
                BaseWidget.WidgetSize.LARGE -> 3
                BaseWidget.WidgetSize.MEDIUM -> 2
                BaseWidget.WidgetSize.SMALL -> 1
            }
            val newSpanY = when (newSize) {
                BaseWidget.WidgetSize.LARGE -> 2
                BaseWidget.WidgetSize.MEDIUM -> 1
                BaseWidget.WidgetSize.SMALL -> 1
            }
            updateWidgetPlacement(widgetId, item.pageIndex, item.cellX, item.cellY, newSpanX, newSpanY)
        }
    }

    fun moveWidgetToPage(widgetId: String, targetPage: Int) {
        val currentList = _widgetsFlow.value.toMutableList()
        val item = currentList.find { it.id == widgetId } ?: return
        val vacant = findVacantCell(targetPage, item.spanX, item.spanY)
        val newCellX = vacant?.first ?: 0
        val newCellY = vacant?.second ?: 0
        updateWidgetPlacement(widgetId, targetPage, newCellX, newCellY, item.spanX, item.spanY)
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
                val cellX = obj.optInt("cellX", 0)
                val cellY = obj.optInt("cellY", 0)
                val spanX = obj.optInt("spanX", 1)
                val spanY = obj.optInt("spanY", 1)
                val appWidgetId = obj.optInt("appWidgetId", -1)
                val packageName = if (obj.has("packageName")) obj.getString("packageName") else null
                val isVisible = obj.optBoolean("isVisible", true)

                val widget = GenericWidget(
                    id = id,
                    typeId = typeId,
                    title = title,
                    size = size,
                    pageIndex = pageIndex,
                    cellX = cellX,
                    cellY = cellY,
                    spanX = spanX,
                    spanY = spanY,
                    appWidgetId = appWidgetId,
                    packageName = packageName
                ).apply {
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

        // Sayfa 0 (Launcher 2/3 Hucre Yerlesimi: 8x4 Grid)
        // Dashboard (Saat + Hiz): Sol ust 4x2
        defaults.add(
            GenericWidget(
                id = "w_comb_0",
                typeId = WidgetRegistry.TYPE_COMBINED,
                title = "Dashboard",
                size = BaseWidget.WidgetSize.LARGE,
                pageIndex = 0,
                cellX = 0,
                cellY = 0,
                spanX = 4,
                spanY = 2
            )
        )
        // Muzik Calar: Orta ust 2x2
        defaults.add(
            GenericWidget(
                id = "w_music_0",
                typeId = WidgetRegistry.TYPE_MUSIC,
                title = "Medya Çalar",
                size = BaseWidget.WidgetSize.LARGE,
                pageIndex = 0,
                cellX = 4,
                cellY = 0,
                spanX = 2,
                spanY = 2
            )
        )
        // Hava Durumu: Sag ust 2x1
        defaults.add(
            GenericWidget(
                id = "w_weath_0",
                typeId = WidgetRegistry.TYPE_WEATHER,
                title = "Hava Durumu",
                size = BaseWidget.WidgetSize.MEDIUM,
                pageIndex = 0,
                cellX = 6,
                cellY = 0,
                spanX = 2,
                spanY = 1
            )
        )
        // Pusula: Sag alt 2x1
        defaults.add(
            GenericWidget(
                id = "w_comp_0",
                typeId = WidgetRegistry.TYPE_COMPASS,
                title = "Pusula & Yön",
                size = BaseWidget.WidgetSize.MEDIUM,
                pageIndex = 0,
                cellX = 6,
                cellY = 1,
                spanX = 2,
                spanY = 1
            )
        )
        // Hiz Gostergesi: Sol alt 2x2
        defaults.add(
            GenericWidget(
                id = "w_speed_0",
                typeId = WidgetRegistry.TYPE_SPEED,
                title = "Hız & Limit",
                size = BaseWidget.WidgetSize.MEDIUM,
                pageIndex = 0,
                cellX = 0,
                cellY = 2,
                spanX = 2,
                spanY = 2
            )
        )
        // Dijital Saat: Orta sol alt 2x2
        defaults.add(
            GenericWidget(
                id = "w_clock_0",
                typeId = WidgetRegistry.TYPE_CLOCK,
                title = "Dijital Saat",
                size = BaseWidget.WidgetSize.MEDIUM,
                pageIndex = 0,
                cellX = 2,
                cellY = 2,
                spanX = 2,
                spanY = 2
            )
        )
        // OBD2 / Arac: Sag alt 4x2
        defaults.add(
            GenericWidget(
                id = "w_obd_0",
                typeId = WidgetRegistry.TYPE_OBD,
                title = "OBD2 / Araç Verileri",
                size = BaseWidget.WidgetSize.LARGE,
                pageIndex = 0,
                cellX = 4,
                cellY = 2,
                spanX = 4,
                spanY = 2
            )
        )

        // Sayfa 1 (Varsayilan Kısayollar)
        defaults.add(
            GenericWidget(
                id = "w_short_1",
                typeId = WidgetRegistry.TYPE_SHORTCUTS,
                title = "Uygulama Kısayolları",
                size = BaseWidget.WidgetSize.LARGE,
                pageIndex = 1,
                cellX = 0,
                cellY = 0,
                spanX = 4,
                spanY = 2
            )
        )

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
                    put("cellX", w.cellX)
                    put("cellY", w.cellY)
                    put("spanX", w.spanX)
                    put("spanY", w.spanY)
                    put("appWidgetId", w.appWidgetId)
                    if (w.packageName != null) {
                        put("packageName", w.packageName)
                    }
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
