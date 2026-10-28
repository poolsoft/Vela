package app.vela.carlauncher.map

import app.vela.ui.map.RenderFrameBudget
import org.junit.Assert.*
import org.junit.Test

class RenderFrameBudgetTest {
    @Test fun fractionalLimitsUseTheDisplayBudgetWithoutDropping45To30() {
        for (limit in listOf(15, 20, 30, 45, 60)) {
            val budget = RenderFrameBudget()
            val count = (0 until 600).count { budget.shouldRender(1L + it * 1_000_000_000L / 60, limit) }
            assertTrue("$limit FPS: $count frames", count in limit * 10 - 1..limit * 10 + 1)
        }
    }

    @Test fun resumeAndChangingLimitsDoNotWaitOnThePreviousDeadlineOrBurst() {
        val budget = RenderFrameBudget()
        assertTrue(budget.shouldRender(1L, 15))
        assertFalse(budget.shouldRender(2L, 15))
        assertTrue(budget.shouldRender(3L, 60))
        budget.reset()
        assertTrue(budget.shouldRender(4L, 60))
        assertTrue(budget.shouldRender(5_000_000_000L, 60))
        assertFalse(budget.shouldRender(5_000_000_001L, 60))
        assertTrue(budget.shouldRender(5_000_000_002L, 0))
        assertTrue(budget.shouldRender(5_000_000_003L, 0))
    }
}
