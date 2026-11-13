package app.vela.carlauncher.desktop

import app.vela.carlauncher.widgets.WidgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGridTest {
    @Test
    fun narrowScreenKeepsCardsInBoundsAndApart() {
        val ids = setOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.SPEEDOMETER,
            WidgetIds.MUSIC, WidgetIds.DOCK)
        val widths = mapOf(WidgetIds.CLOCK to 260f, WidgetIds.STATUS to 280f,
            WidgetIds.SPEEDOMETER to 240f, WidgetIds.MUSIC to 300f, WidgetIds.DOCK to 340f)
        val heights = mapOf(WidgetIds.CLOCK to 100f, WidgetIds.STATUS to 140f,
            WidgetIds.SPEEDOMETER to 160f, WidgetIds.MUSIC to 160f, WidgetIds.DOCK to 76f)
        val result = WorkspaceGrid.resolve(ids, DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS, 400f, 760f)

        assertEquals(ids, result.keys)
        result.forEach { (id, placement) ->
            val right = placement.dx + widths.getValue(id) * placement.scale
            val bottom = placement.dy + heights.getValue(id) * placement.scale
            assertTrue(placement.dx >= 0f && right <= 400f)
            assertTrue(placement.dy >= 0f && bottom <= 760f)
        }
        result.entries.forEach { (id, a) ->
            result.entries.filter { it.key != id && it.value.page == a.page }.forEach { (otherId, b) ->
                val separated = a.dx + widths.getValue(id) * a.scale <= b.dx ||
                    b.dx + widths.getValue(otherId) * b.scale <= a.dx ||
                    a.dy + heights.getValue(id) * a.scale <= b.dy ||
                    b.dy + heights.getValue(otherId) * b.scale <= a.dy
                assertTrue("$id overlaps $otherId", separated)
            }
        }
    }

    @Test
    fun savedPageSurvivesReflow() {
        val id = WidgetIds.MUSIC
        val saved = mapOf(id to WidgetPlacement(900f, 900f, 1f, 2))
        val result = WorkspaceGrid.resolve(setOf(id), saved, 400f, 600f).getValue(id)

        assertEquals(2, result.page)
        assertTrue(result.dx <= 100f)
        assertTrue(result.dy <= 600f)
    }

    @Test
    fun pageIndexIsNotCapped() {
        val result = WorkspaceGrid.resolve(
            setOf(WidgetIds.CLOCK),
            mapOf(WidgetIds.CLOCK to WidgetPlacement(page = 50)),
            400f,
            600f
        ).getValue(WidgetIds.CLOCK)

        assertEquals(50, result.page)
    }

    @Test
    fun independentResizeDimensionsSurviveReflow() {
        val saved = mapOf(
            WidgetIds.MUSIC to WidgetPlacement(
                dx = 16f,
                dy = 16f,
                widthScale = 1.4f,
                heightScale = 0.75f
            )
        )

        val result = WorkspaceGrid.resolve(setOf(WidgetIds.MUSIC), saved, 600f, 400f)
            .getValue(WidgetIds.MUSIC)

        assertEquals(1.4f, result.widthScale, 0.001f)
        assertEquals(0.75f, result.heightScale, 0.001f)
    }
    @Test
    fun duplicateClocksKeepTheirPagesAndCardDimensions() {
        val first = WidgetIds.CLOCK
        val second = "clock#second"
        val saved = mapOf(first to WidgetPlacement(page = 0), second to WidgetPlacement(page = 2, dx = 32f))
        val result = WorkspaceGrid.resolve(saved.keys, saved, 400f, 600f)
        assertEquals(0, result.getValue(first).page)
        assertEquals(2, result.getValue(second).page)
        assertEquals("clock", WidgetIds.type(second))
        assertEquals(1f, result.getValue(second).widthScale, 0.001f)
    }

    @Test
    fun freeLayoutKeepsUnsnappedPosition() {
        val saved = mapOf(WidgetIds.CLOCK to WidgetPlacement(dx = 25f, dy = 33f))
        val result = WorkspaceGrid.resolve(saved.keys, saved, 400f, 600f, snapToGrid = false)
            .getValue(WidgetIds.CLOCK)
        assertEquals(25f, result.dx, 0.001f)
        assertEquals(33f, result.dy, 0.001f)
    }

    @Test
    fun baseSizesComeFromTheWidgetRegistry() {
        // Tek kaynak: bu tablo WidgetRegistry'de yasar. Degerler degisirse masaustu
        // yerlesimi de degisir; bu test ikisinin ayrismasini engeller.
        val expected = mapOf(
            WidgetIds.CLOCK to (260f to 100f),
            WidgetIds.STATUS to (280f to 140f),
            WidgetIds.SPEEDOMETER to (240f to 160f),
            WidgetIds.MUSIC to (300f to 160f),
            WidgetIds.COMBINED to (340f to 120f),
            WidgetIds.WEATHER to (170f to 90f),
            WidgetIds.COMPASS to (170f to 90f),
            WidgetIds.OBD to (280f to 90f),
            WidgetIds.DOCK to (340f to 76f)
        )
        expected.forEach { (id, size) ->
            assertEquals("$id dogal boyutu", size, WorkspaceGrid.size(id))
            assertNotNull("$id kayit defterinde yok", WidgetRegistry.byDesktopId(id))
        }
        // Ayni turden ikinci kopya da ayni boyutu alir.
        assertEquals(260f to 100f, WorkspaceGrid.size("clock#second"))
        // Uygulama kisayolu ayri boyut kullanir.
        assertEquals(80f to 96f, WorkspaceGrid.size(WidgetIds.APP_PREFIX + "com.example"))
        // Bilinmeyen kimlik guvenli varsayilana duser.
        assertEquals(280f to 140f, WorkspaceGrid.size("bilinmeyen"))
    }

    @Test
    fun desktopRegistryIdsAreUniqueAndSized() {
        val entries = WidgetRegistry.desktopEntries()
        assertTrue(entries.size >= 9)
        val ids = entries.map { it.desktopId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(entries.all { it.baseWidthDp > 0f && it.baseHeightDp > 0f })
        // Secicide gorunen her widget masaustune eklenebilmeli.
        assertTrue(entries.all { WidgetRegistry.baseSize(it.desktopId) != null })
    }

    @Test
    fun spanUsesRegistryBaseSize() {
        val grid = CellGrid.build(1000f, 500f, cols = 10, rows = 5)
        val (sx, sy) = CellGrid.spanOf(WidgetIds.MUSIC, grid)
        // 300x160 dp / 100x100 hucre -> 3x2
        assertEquals(3, sx)
        assertEquals(2, sy)
    }

}
