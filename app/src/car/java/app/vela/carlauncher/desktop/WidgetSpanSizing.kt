package app.vela.carlauncher.desktop

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Bir widget'in hucre cinsinden alabilecegi en kucuk/en buyuk span (WidgetSpanLimits).
 * Saglayici (provider) bildiriminden turetilir; sinir disi bir span asla yazilmaz.
 */
data class WidgetSpanLimits(
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int
) {
    fun clamp(spanX: Int, spanY: Int): Pair<Int, Int> =
        spanX.coerceIn(minX, maxX) to spanY.coerceIn(minY, maxY)

    /** Verilen span bu sinirlarin icinde mi? */
    fun accepts(spanX: Int, spanY: Int): Boolean =
        spanX in minX..maxX && spanY in minY..maxY
}

/**
 * Bir widget icin cozumleme kurali: ilk yerlestirmede kullanilacak tercih edilen span
 * (Android 31 targetCellWidth/Height veya minWidth) ve resize sinirlari.
 */
data class WidgetSpanRule(
    val defaultX: Int,
    val defaultY: Int,
    val limits: WidgetSpanLimits
)

/**
 * dp cinsinden boyut bildirimlerini hucre span sinirlarina cevirir (resize C).
 *
 * Android widget'lari boyutlarini dp ile bildirir (minWidth/minResizeWidth/maxResizeWidth,
 * API 31'de targetCellWidth). Masaustu ise hucre ile calisir; bu yuzden iki birim arasinda
 * yuvarlama gerekir:
 * - Alt sinir YUKARI yuvarlanir: minimum dp'yi icine alabilen en kucuk hucre sayisi.
 * - Ust sinir ASAGI yuvarlanir: maksimum dp'yi asmayan en buyuk hucre sayisi.
 * 0 dp "bildirilmemis" demektir; ust sinirda bu "sinirsiz" (tum tahta) anlamina gelir.
 *
 * Compose'dan bagimsizdir; WidgetSpanSizingTest ile dogrulanir.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object WidgetSpanSizing {

    /** dp'yi icine alan en kucuk hucre sayisi. */
    fun minCells(dp: Int, cellDp: Float, cells: Int): Int =
        if (dp <= 0 || cellDp <= 0f) 1
        else ceil(dp / cellDp).toInt().coerceIn(1, cells.coerceAtLeast(1))

    /** dp'yi ASMAYAN en buyuk hucre sayisi; 0 = sinirsiz (tum tahta). */
    fun maxCells(dp: Int, cellDp: Float, cells: Int): Int =
        if (dp <= 0 || cellDp <= 0f) cells.coerceAtLeast(1)
        else floor(dp / cellDp).toInt().coerceIn(1, cells.coerceAtLeast(1))

    /**
     * Saglayici bildiriminden span sinirlari.
     *
     * resizableX/Y false ise o eksende buyutme KAPALIDIR (max = min): Android'de
     * resizeMode == 0 olan widget'lar yeniden boyutlandirilamaz.
     */
    fun limits(
        minWidthDp: Int,
        minHeightDp: Int,
        maxWidthDp: Int,
        maxHeightDp: Int,
        grid: CellGrid.Grid,
        resizableX: Boolean = true,
        resizableY: Boolean = true
    ): WidgetSpanLimits {
        val minX = minCells(minWidthDp, grid.cellWidth, grid.cols)
        val minY = minCells(minHeightDp, grid.cellHeight, grid.rows)
        val maxX = if (resizableX) {
            maxCells(maxWidthDp, grid.cellWidth, grid.cols).coerceAtLeast(minX)
        } else {
            minX
        }
        val maxY = if (resizableY) {
            maxCells(maxHeightDp, grid.cellHeight, grid.rows).coerceAtLeast(minY)
        } else {
            minY
        }
        return WidgetSpanLimits(minX, minY, maxX, maxY)
    }

    /**
     * Hazir boyut onerileri (resize E): widget'in sinirlari icine dusen, tahtaya sigan
     * standart span'ler. Ayni boyut iki kez listelenmez; mevcut boyut her zaman ilk sirada.
     */
    fun presets(
        limits: WidgetSpanLimits,
        grid: CellGrid.Grid,
        currentX: Int,
        currentY: Int
    ): List<Pair<Int, Int>> {
        val candidates = mutableListOf(currentX to currentY)
        val steps = listOf(1 to 1, 2 to 1, 2 to 2, 3 to 1, 3 to 2, 4 to 2, 4 to 3, 5 to 2, 6 to 3)
        candidates += steps
        // Genisligi koruyup yuksekligi bir artirmak da pratik bir kisayol.
        candidates += (currentX to currentY + 1)
        return candidates
            .filter { (sx, sy) -> sx in 1..grid.cols && sy in 1..grid.rows }
            .filter { (sx, sy) -> limits.accepts(sx, sy) }
            .distinct()
            .take(6)
    }
}
