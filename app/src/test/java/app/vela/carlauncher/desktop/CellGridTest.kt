package app.vela.carlauncher.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CellGrid.cozumleyici regresyon testleri. Onceki hatalarin tekrar etmemesi icin:
 * - kayitli hucreye sadik kalma (savrulma olmasin)
 * - sayfa dolu oldugunda ust uste binme yerine sonraki sayfaya tasma
 * - bozuk "tum sayfa" span'lerinin taban boyuta donmesi
 * - widget'in hicbir zaman kaybolmamasi
 */
class CellGridTest {

    private val grid = CellGrid.build(1000f, 500f, cols = 10, rows = 5)

    private fun WidgetPlacement.at(cellX: Int, cellY: Int, spanX: Int, spanY: Int, page: Int = 0) =
        copy(dx = cellX * grid.cellWidth, dy = cellY * grid.cellHeight,
            cellX = cellX, cellY = cellY, spanX = spanX, spanY = spanY, page = page)

    private fun assertNoOverlap(result: Map<String, WidgetPlacement>) {
        val entries = result.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                if (a.value.page != b.value.page) continue
                val separated = a.value.cellX + a.value.spanX <= b.value.cellX ||
                    b.value.cellX + b.value.spanX <= a.value.cellX ||
                    a.value.cellY + a.value.spanY <= b.value.cellY ||
                    b.value.cellY + b.value.spanY <= a.value.cellY
                assertTrue("${a.key} overlaps ${b.key}", separated)
            }
        }
    }

    @Test
    fun keepsSavedCellExactly() {
        val id = WidgetIds.CLOCK
        val saved = mapOf(id to WidgetPlacement().at(3, 2, 3, 2))
        val result = CellGrid.solve(setOf(id), saved, grid).getValue(id)

        assertEquals(3, result.cellX)
        assertEquals(2, result.cellY)
        assertEquals(3, result.spanX)
        assertEquals(2, result.spanY)
        assertEquals(3 * grid.cellWidth, result.dx, 0.001f)
    }

    @Test
    fun fullWidthSpanIsRespected() {
        val id = WidgetIds.DOCK
        val saved = mapOf(id to WidgetPlacement().at(0, 4, grid.cols, 1))
        val result = CellGrid.solve(setOf(id), saved, grid).getValue(id)

        assertEquals(grid.cols, result.spanX)
        assertEquals(1, result.spanY)
    }

    @Test
    fun wholeBoardSpanIsTreatedAsCorrupt() {
        val id = WidgetIds.MUSIC
        val saved = mapOf(id to WidgetPlacement().at(0, 0, grid.cols, grid.rows))
        val result = CellGrid.solve(setOf(id), saved, grid).getValue(id)

        val (baseX, baseY) = CellGrid.spanOf(WidgetIds.MUSIC, grid)
        assertEquals(baseX, result.spanX)
        assertEquals(baseY, result.spanY)
    }

    @Test
    fun fullPageOverflowsToNextPageInsteadOfStacking() {
        // 4x2 span'li 7 widget = 56 hucre > 50 hucre (10x5). 6'si sayfa 0'a sığar,
        // 7.si üst üste binmek yerine sayfa 1'e taşmak zorunda.
        val saved = (0 until 7).associate { index ->
            val id = "${WidgetIds.WEATHER}#$index"
            id to WidgetPlacement().at(0, 0, 4, 2, page = 0)
        }
        val result = CellGrid.solve(saved.keys, saved, grid)

        assertEquals(saved.size, result.size)
        assertNoOverlap(result)
        // 10x5 tahtada 4x2 bloklar en fazla 2 sutun x 2 bant = 4 tanedir; kalanlar
        // ust uste binmek yerine sonraki sayfalara tasinir.
        assertEquals(4, result.count { it.value.page == 0 })
        assertEquals(3, result.count { it.value.page > 0 })
    }

    @Test
    fun missingCellsFallBackToDpPosition() {
        val id = WidgetIds.WEATHER
        val saved = mapOf(id to WidgetPlacement(dx = 5 * grid.cellWidth, dy = 2 * grid.cellHeight, page = 0))
        val result = CellGrid.solve(setOf(id), saved, grid).getValue(id)

        assertEquals(5, result.cellX)
        assertEquals(2, result.cellY)
    }

    @Test
    fun degenerateGridDoesNotLoseWidgets() {
        val tiny = CellGrid.build(1f, 1f, 10, 5)
        val ids = setOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.MUSIC, WidgetIds.DOCK)
        val result = CellGrid.solve(ids, ids.associateWith { WidgetPlacement() }, tiny)

        assertEquals(ids, result.keys)
        result.forEach { (id, p) ->
            assertTrue("$id out of bounds", p.cellX in 0 until tiny.cols)
            assertTrue("$id out of bounds", p.cellY in 0 until tiny.rows)
        }
    }

    @Test
    fun neverOverlapsOnTheSamePage() {
        val saved = (0 until 8).associate { index ->
            val id = "${WidgetIds.WEATHER}#$index"
            id to WidgetPlacement().at(index % 5, index / 5, 2, 2)
        }
        val result = CellGrid.solve(saved.keys, saved, grid)

        assertEquals(saved.size, result.size)
        assertNoOverlap(result)
        assertFalse(result.values.any { it.cellX < 0 || it.cellY < 0 })
    }
}