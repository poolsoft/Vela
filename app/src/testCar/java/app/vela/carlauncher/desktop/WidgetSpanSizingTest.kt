package app.vela.carlauncher.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saglayici boyut bildirimlerinin hucre span'ine cevrilmesi (resize C) regresyon testleri.
 * Grid: 1000x500 dp, 10x5 hucre -> hucre 100x100 dp.
 */
class WidgetSpanSizingTest {

    private val grid = CellGrid.build(1000f, 500f, cols = 10, rows = 5)

    @Test
    fun minCellsRoundsUpAndMaxCellsRoundsDown() {
        // 150 dp iki hucre ister (bir hucre yetmez).
        assertEquals(2, WidgetSpanSizing.minCells(150, grid.cellWidth, grid.cols))
        // 250 dp'yi asmayan en buyuk hucre sayisi 2.
        assertEquals(2, WidgetSpanSizing.maxCells(250, grid.cellWidth, grid.cols))
        // 0 = bildirilmemis: alt sinir 1, ust sinir tum tahta.
        assertEquals(1, WidgetSpanSizing.minCells(0, grid.cellWidth, grid.cols))
        assertEquals(10, WidgetSpanSizing.maxCells(0, grid.cellWidth, grid.cols))
    }

    @Test
    fun limitsFromProviderDp() {
        val limits = WidgetSpanSizing.limits(
            minWidthDp = 150,
            minHeightDp = 50,
            maxWidthDp = 350,
            maxHeightDp = 250,
            grid = grid
        )
        assertEquals(WidgetSpanLimits(minX = 2, minY = 1, maxX = 3, maxY = 2), limits)
        assertTrue(limits.accepts(3, 2))
        assertFalse(limits.accepts(4, 2))
        assertEquals(3 to 2, limits.clamp(9, 9))
    }

    @Test
    fun nonResizableAxisCollapsesToSingleSpan() {
        // resizeMode == 0: iki eksen de kapali -> boyut sabitlenir.
        val limits = WidgetSpanSizing.limits(
            minWidthDp = 150,
            minHeightDp = 50,
            maxWidthDp = 0,
            maxHeightDp = 0,
            grid = grid,
            resizableX = false,
            resizableY = false
        )
        assertEquals(2, limits.maxX)
        assertEquals(1, limits.maxY)
    }

    @Test
    fun maxNeverBelowMin() {
        // maxResizeWidth minResizeWidth'den kucuk bildirilmis olsa bile max >= min.
        val limits = WidgetSpanSizing.limits(
            minWidthDp = 400,
            minHeightDp = 100,
            maxWidthDp = 100,
            maxHeightDp = 0,
            grid = grid
        )
        assertEquals(limits.minX, limits.maxX)
    }

    @Test
    fun presetsKeepCurrentFirstAndDropOutOfRangeSizes() {
        val limits = WidgetSpanLimits(minX = 2, minY = 1, maxX = 3, maxY = 2)
        val presets = WidgetSpanSizing.presets(limits, grid, currentX = 2, currentY = 1)
        assertEquals(listOf(2 to 1, 2 to 2, 3 to 1, 3 to 2), presets)
    }

    @Test
    fun presetsNeverExceedGrid() {
        val tinyGrid = CellGrid.build(200f, 100f, cols = 2, rows = 1)
        val limits = WidgetSpanSizing.limits(0, 0, 0, 0, tinyGrid)
        val presets = WidgetSpanSizing.presets(limits, tinyGrid, currentX = 1, currentY = 1)
        assertTrue(presets.isNotEmpty())
        assertTrue(presets.all { (sx, sy) -> sx <= 2 && sy <= 1 })
    }

    @Test
    fun solveAppliesProviderLimitsToSavedSpan() {
        val saved = mapOf(
            "aw_1" to WidgetPlacement(page = 0, cellX = 0, cellY = 0, spanX = 8, spanY = 4)
        )
        val rule = WidgetSpanRule(defaultX = 2, defaultY = 2, limits = WidgetSpanLimits(2, 1, 3, 2))
        val result = CellGrid.solve(setOf("aw_1"), saved, grid, rules = { id ->
            if (id == "aw_1") rule else null
        })
        val placed = result.getValue("aw_1")
        assertEquals(3, placed.spanX)
        assertEquals(2, placed.spanY)
    }

    @Test
    fun solveUsesProviderDefaultSpanForNewWidget() {
        val rule = WidgetSpanRule(defaultX = 4, defaultY = 3, limits = WidgetSpanLimits(2, 1, 6, 4))
        val result = CellGrid.solve(setOf("aw_7"), emptyMap(), grid, rules = { rule })
        val placed = result.getValue("aw_7")
        assertEquals(4, placed.spanX)
        assertEquals(3, placed.spanY)
        // Hucre koordinati ile dp konumu tutarli kalmali.
        assertEquals(0f, placed.dx, 0.001f)
        assertEquals(0f, placed.dy, 0.001f)
    }

    @Test
    fun solveWithoutRulesKeepsOldBehaviour() {
        val result = CellGrid.solve(setOf(WidgetIds.CLOCK), emptyMap(), grid)
        val placed = result.getValue(WidgetIds.CLOCK)
        val (baseX, baseY) = CellGrid.spanOf(WidgetIds.CLOCK, grid)
        assertEquals(baseX, placed.spanX)
        assertEquals(baseY, placed.spanY)
    }
}
