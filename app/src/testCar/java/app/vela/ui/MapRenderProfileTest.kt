package app.vela.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapRenderProfileTest {
    @Test fun automaticBudgetUsesDeviceCapacityAndRequiresSustainedSlowFrames() {
        assertEquals(MapRenderProfile.LIGHT, MapRenderProfile.effective(MapRenderProfile.AUTO, true, false))
        assertEquals(MapRenderProfile.STANDARD, MapRenderProfile.effective(MapRenderProfile.AUTO, false, false))
        val budget = MapRenderProfile.FrameBudget()
        var limited = false
        for (i in 1..299) limited = budget.sample(i * 100L, 90)
        assertEquals(false, limited)
        for (i in 300..305) limited = budget.sample(i * 100L, 90) || limited
        assertEquals(true, limited)
        val idle = MapRenderProfile.FrameBudget()
        repeat(10) { assertEquals(false, idle.sample(it * 15_000L + 1, 100)) }
    }
    @Test fun deviceProtectionIsOptInAndPreservesExplicitChoices() {
        assertEquals(MapRenderProfile.STANDARD, MapRenderProfile.effective(MapRenderProfile.STANDARD, true, false))
        assertEquals(MapRenderProfile.LIGHT, MapRenderProfile.effective(MapRenderProfile.STANDARD, true, false, true))
        assertEquals(MapRenderProfile.STANDARD, MapRenderProfile.effective(MapRenderProfile.STANDARD, false, false))
        assertEquals(MapRenderProfile.CUSTOM, MapRenderProfile.effective(MapRenderProfile.CUSTOM, true, false, true))
        assertEquals(MapRenderProfile.LIGHT, MapRenderProfile.effective(MapRenderProfile.CUSTOM, false, true))
    }
}
