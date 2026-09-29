package app.vela.carlauncher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * DuoLauncher boyutlandirma ve hucre yerlesim motorunun Vela 12x6 izgarasina uyarlanmis hali.
 * - Sistem widget'larinin minWidth, minHeight, minResize ve maxResize degerlerini 12x6 hucreye cevirir.
 * - Dahili Vela widget'lari icin guvenli en-boy kisitlamalari sunar.
 * - Surukleme sirasinda parmagin tuttugu noktayi koruyarak hedef hucreyi hesaplar (adjustedWidgetDropCell).
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object CarWidgetSizing {

    const val COLUMNS = 12
    const val ROWS = 6

    data class SpanConstraints(
        val minSpanX: Int,
        val maxSpanX: Int,
        val minSpanY: Int,
        val maxSpanY: Int,
        val canResizeHorizontally: Boolean = true,
        val canResizeVertically: Boolean = true
    )

    /**
     * Verilen widget tipi icin min ve max hucre boyutlarini hesaplar.
     */
    fun getConstraintsForWidget(
        widget: BaseWidget,
        providerInfo: AppWidgetProviderInfo?,
        cellWidthDp: Float,
        cellHeightDp: Float
    ): SpanConstraints {
        // 1. SISTEM WIDGETI KISITLAMALARI
        if (widget.typeId == "system" && providerInfo != null) {
            val minWidthDp = providerInfo.minWidth.toFloat()
            val minHeightDp = providerInfo.minHeight.toFloat()
            val minResizeWidthDp = if (providerInfo.minResizeWidth > 0) providerInfo.minResizeWidth.toFloat() else minWidthDp
            val minResizeHeightDp = if (providerInfo.minResizeHeight > 0) providerInfo.minResizeHeight.toFloat() else minHeightDp
            val maxResizeWidthDp = if (providerInfo.maxResizeWidth > 0) providerInfo.maxResizeWidth.toFloat() else 0f
            val maxResizeHeightDp = if (providerInfo.maxResizeHeight > 0) providerInfo.maxResizeHeight.toFloat() else 0f

            val resizeMode = providerInfo.resizeMode
            val canResizeH = (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
            val canResizeV = (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

            val calcMinX = spanForDimension(minResizeWidthDp, cellWidthDp).coerceIn(1, COLUMNS)
            val calcMinY = spanForDimension(minResizeHeightDp, cellHeightDp).coerceIn(1, ROWS)
            val calcMaxX = if (maxResizeWidthDp > 0f) spanForDimension(maxResizeWidthDp, cellWidthDp).coerceIn(calcMinX, COLUMNS) else COLUMNS
            val calcMaxY = if (maxResizeHeightDp > 0f) spanForDimension(maxResizeHeightDp, cellHeightDp).coerceIn(calcMinY, ROWS) else ROWS

            return SpanConstraints(
                minSpanX = calcMinX,
                maxSpanX = calcMaxX,
                minSpanY = calcMinY,
                maxSpanY = calcMaxY,
                canResizeHorizontally = canResizeH || resizeMode == 0,
                canResizeVertically = canResizeV || resizeMode == 0
            )
        }

        // 2. DAHILI VELA OTOMOTIV WIDGET'LARI
        return when (widget.typeId) {
            WidgetRegistry.TYPE_COMBINED -> SpanConstraints(minSpanX = 4, maxSpanX = 12, minSpanY = 2, maxSpanY = 6)
            WidgetRegistry.TYPE_SPEED -> SpanConstraints(minSpanX = 2, maxSpanX = 6, minSpanY = 2, maxSpanY = 6)
            WidgetRegistry.TYPE_CLOCK -> SpanConstraints(minSpanX = 2, maxSpanX = 6, minSpanY = 2, maxSpanY = 4)
            WidgetRegistry.TYPE_MUSIC -> SpanConstraints(minSpanX = 4, maxSpanX = 12, minSpanY = 2, maxSpanY = 6)
            WidgetRegistry.TYPE_WEATHER -> SpanConstraints(minSpanX = 2, maxSpanX = 6, minSpanY = 1, maxSpanY = 4)
            WidgetRegistry.TYPE_COMPASS -> SpanConstraints(minSpanX = 2, maxSpanX = 6, minSpanY = 1, maxSpanY = 4)
            WidgetRegistry.TYPE_OBD -> SpanConstraints(minSpanX = 4, maxSpanX = 12, minSpanY = 2, maxSpanY = 4)
            WidgetRegistry.TYPE_SHORTCUTS -> SpanConstraints(minSpanX = 3, maxSpanX = 12, minSpanY = 2, maxSpanY = 6)
            "shortcut" -> SpanConstraints(minSpanX = 1, maxSpanX = 2, minSpanY = 1, maxSpanY = 2)
            else -> SpanConstraints(minSpanX = 1, maxSpanX = COLUMNS, minSpanY = 1, maxSpanY = ROWS)
        }
    }

    /**
     * DuoLauncher surukleme hesaplayicisi:
     * Parmagin tuttugu ofseti koruyarak sol-ust hedef hucre koordinatlarini belirler.
     */
    fun adjustedWidgetDropCell(
        sourceCellX: Int,
        sourceCellY: Int,
        spanX: Int,
        spanY: Int,
        dragOffsetX: Float,
        dragOffsetY: Float,
        cellWidthPx: Float,
        cellHeightPx: Float,
        spacingPx: Float
    ): Pair<Int, Int> {
        val totalCellWPx = cellWidthPx + spacingPx
        val totalCellHPx = cellHeightPx + spacingPx

        val deltaCol = (dragOffsetX / totalCellWPx).roundToInt()
        val deltaRow = (dragOffsetY / totalCellHPx).roundToInt()

        val targetCol = (sourceCellX + deltaCol).coerceIn(0, COLUMNS - spanX)
        val targetRow = (sourceCellY + deltaRow).coerceIn(0, ROWS - spanY)

        return Pair(targetCol, targetRow)
    }

    /**
     * Ekran kenarinda beklenildiginde sayfa degistirme yonunu belirler (-1: Onceki, 1: Sonraki, 0: Yok)
     */
    fun calculateEdgePageTurn(
        touchX: Float,
        containerWidth: Float,
        edgeMarginPx: Float = 40f
    ): Int {
        return when {
            touchX < edgeMarginPx -> -1
            touchX > containerWidth - edgeMarginPx -> 1
            else -> 0
        }
    }

    private fun spanForDimension(sizeDp: Float, cellDp: Float): Int {
        if (cellDp <= 0f) return 1
        return ceil((sizeDp.coerceAtLeast(1f) / cellDp).toDouble()).toInt().coerceAtLeast(1)
    }
}
