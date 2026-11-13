package app.vela.carlauncher.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Birakma/yerlesim kurali regresyon testleri (DesktopPlacementEngine).
 * Kapsam:
 * - bos hedefe birakma
 * - tek isaretci ile swap (izinli) / sigmayan isaretci (red)
 * - eski bolgemizde ucuncu widget varken swap red
 * - iki widget hedefe tasiyorsa red (ust uste binme olmaz)
 * - sayfada yer var mi taramasi (swap dahil)
 * - serbest modda (grid == null) her yerlesim serbest
 */
class DesktopPlacementEngineTest {

    private val grid = CellGrid.build(1000f, 500f, cols = 10, rows = 5)

    private fun place(
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        page: Int = 0
    ) = WidgetPlacement(
        dx = cellX * grid.cellWidth,
        dy = cellY * grid.cellHeight,
        page = page,
        cellX = cellX,
        cellY = cellY,
        spanX = spanX,
        spanY = spanY
    )

    private fun assertNoOverlap(layout: Map<String, WidgetPlacement>) {
        val entries = layout.entries.toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i].value
                val b = entries[j].value
                assertFalse(
                    "${entries[i].key} ile ${entries[j].key} ust uste biniyor",
                    DesktopPlacementEngine.overlaps(a, b)
                )
            }
        }
    }

    @Test
    fun freeTargetIsAllowed() {
        val layout = mapOf("a" to place(0, 0, 2, 2))
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", place(4, 0, 2, 2), grid))
    }

    @Test
    fun differentPageDoesNotBlock() {
        val layout = mapOf(
            "a" to place(0, 0, 2, 2, page = 0),
            "b" to place(0, 0, 2, 2, page = 1)
        )
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", place(0, 0, 2, 2, page = 2), grid))
    }

    @Test
    fun unknownWidgetCannotLandOnOccupiedCells() {
        val layout = mapOf("a" to place(0, 0, 2, 2))
        assertFalse(DesktopPlacementEngine.allowed(layout, "yeni", place(1, 1, 2, 2), grid))
        assertTrue(DesktopPlacementEngine.allowed(layout, "yeni", place(2, 0, 2, 2), grid))
    }

    @Test
    fun swapIsAllowedWhenOccupantFitsOldRegionAndRegionIsClear() {
        val layout = mapOf(
            "a" to place(0, 0, 2, 2),
            "b" to place(2, 0, 2, 2)
        )
        // b (2x2) a'nin eski bolgesine (2x2) sigiyor, a'nin eski bolgesinde baska kimse yok.
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", place(2, 0, 2, 2), grid))
    }

    @Test
    fun swapIsRejectedWhenOccupantIsBiggerThanOldRegion() {
        val layout = mapOf(
            "a" to place(0, 0, 1, 1),
            "b" to place(1, 0, 3, 3)
        )
        assertFalse(DesktopPlacementEngine.allowed(layout, "a", place(1, 0, 1, 1), grid))
    }

    @Test
    fun swapIsRejectedWhenThirdWidgetSitsInOldRegion() {
        // Kucultme senaryosu: a kuculup b'nin ustune gitmek istiyor, a'nin eski bolgesinde
        // c duruyor. Swap yapilirsa b bir yere sigmaz -> red.
        val layout = mapOf(
            "a" to place(0, 0, 4, 2),
            "b" to place(4, 0, 2, 2),
            "c" to place(0, 0, 2, 1)
        )
        assertFalse(DesktopPlacementEngine.allowed(layout, "a", place(4, 0, 2, 2), grid))
    }

    @Test
    fun twoOccupantsAreRejectedInsteadOfOverlapping() {
        // Hedefi iki ayri widget kapliyor: swap tanimsiz, birakma reddedilmeli.
        val layout = mapOf(
            "a" to place(0, 0, 2, 2),
            "b" to place(2, 0, 1, 1),
            "c" to place(3, 0, 1, 1)
        )
        assertFalse(DesktopPlacementEngine.allowed(layout, "a", place(2, 0, 2, 1), grid))
    }

    @Test
    fun resizeOverNeighbourIsRejected() {
        val layout = mapOf(
            "a" to place(0, 0, 2, 2),
            "b" to place(2, 0, 2, 2)
        )
        // a 3 hucre genislemek istiyor: b ile cakisiyor, b eski bolgeye (2x2) sigmaz -> red.
        assertFalse(DesktopPlacementEngine.allowed(layout, "a", place(0, 0, 3, 2), grid))
    }

    @Test
    fun swapPlanLeavesNoOverlap() {
        val layout = mapOf(
            "a" to place(0, 0, 2, 2),
            "b" to place(2, 0, 2, 2),
            "c" to place(2, 2, 2, 2)
        )
        val target = place(2, 0, 2, 2)
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", target, grid))
        val writes = DesktopPlacementEngine.swapPlan(layout, "a", target, grid)
        val next = layout.toMutableMap()
        writes.forEach { (id, p) -> next[id] = p }
        assertNoOverlap(next)
        assertEquals(2, writes.size)
        assertEquals(0, next.getValue("b").cellX)
        assertEquals(0, next.getValue("b").cellY)
        assertEquals(0f, next.getValue("b").dx, 0.001f)
    }

    @Test
    fun swapOnPartialShiftUsesFreedStrip() {
        // a 1 hucre sola kayiyor (x2..5 -> x1..4); bosalan serit x=4 sutunudur.
        // isaretci 1x1 bosalan seride konmali, eski bolgenin sol-ustune degil
        // (sol-ust yeni konumun icinde kalirdi ve ust uste binerdi).
        val layout = mapOf(
            "a" to place(2, 0, 3, 2),
            "b" to place(1, 0, 1, 1)
        )
        val target = place(1, 0, 3, 2)
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", target, grid))
        val writes = DesktopPlacementEngine.swapPlan(layout, "a", target, grid)
        val next = layout.toMutableMap()
        writes.forEach { (id, p) -> next[id] = p }
        assertNoOverlap(next)
        assertEquals(4, next.getValue("b").cellX)
        assertEquals(0, next.getValue("b").cellY)
    }

    @Test
    fun hasRoomFindsFreeCornerWhenMiddleIsPacked() {
        // 10x5 tahtada orta bant dolu; 1x1 icin hala yer var.
        val layout = (0 until 5).associate { row ->
            "w$row" to place(0, row, 10, 1)
        }
        assertFalse(DesktopPlacementEngine.hasRoom(layout, "x", 0, 1, 1, grid))
        // 1 satir bosaltinca yer acilir.
        val freed = layout.minus("w4")
        assertTrue(DesktopPlacementEngine.hasRoom(freed, "x", 0, 1, 1, grid))
    }

    @Test
    fun hasRoomIsFalseOnFullPageAndTrueOnEmptyPage() {
        val layout = (0 until 5).associate { row ->
            "w$row" to place(0, row, 10, 1)
        }
        assertFalse(DesktopPlacementEngine.hasRoom(layout, "x", 0, 2, 1, grid))
        assertTrue(DesktopPlacementEngine.hasRoom(layout, "x", 1, 2, 1, grid))
    }

    @Test
    fun freeModeAllowsEverything() {
        val layout = mapOf("a" to place(0, 0, 4, 4))
        assertTrue(DesktopPlacementEngine.allowed(layout, "a", place(0, 0, 4, 4), null))
        assertTrue(DesktopPlacementEngine.hasRoom(layout, "a", 0, 9, 9, null))
    }
}
