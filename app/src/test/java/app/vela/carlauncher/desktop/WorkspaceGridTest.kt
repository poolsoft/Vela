package app.vela.carlauncher.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGridTest {
    @Test
    fun narrowScreenKeepsCardsInBoundsAndApart() {
        val ids = setOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.SPEEDOMETER,
            WidgetIds.MUSIC, WidgetIds.DOCK)
        val widths = mapOf(WidgetIds.CLOCK to 260f, WidgetIds.STATUS to 160f,
            WidgetIds.SPEEDOMETER to 240f, WidgetIds.MUSIC to 300f, WidgetIds.DOCK to 340f)
        val heights = mapOf(WidgetIds.CLOCK to 100f, WidgetIds.STATUS to 100f,
            WidgetIds.SPEEDOMETER to 160f, WidgetIds.MUSIC to 160f, WidgetIds.DOCK to 76f)
        val result = WorkspaceGrid.resolve(ids, DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS, 400f, 760f)

        assertEquals(ids, result.keys)
        result.forEach { (id, placement) ->
            val right = placement.dx + widths.getValue(id) * placement.scale
            val bottom = placement.dy + heights.getValue(id) * placement.scale
            assertTrue(placement.dx >= 0f && right <= 400f)
            assertTrue(placement.dy >= 0f && bottom <= 704f)
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
        assertTrue(result.dy <= 384f)
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
}
