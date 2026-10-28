package app.vela.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavPuckSizingTest {
    @Test fun artworkResolutionDoesNotBecomeScreenSize() {
        assertEquals(88f, NavPuckSizing.displayEdgeDp(202), 0.001f)
        assertTrue(NavPuckSizing.displayEdgeDp(202) < 202f / 2)
    }

    @Test fun overlayAndMapSymbolAgreeAcrossSizesAndScreenDensities() {
        for (scale in listOf(1f, 1.25f, 1.5f)) {
            val bitmapWidth = (NavPuckSizing.BITMAP_EDGE * scale).toInt()
            for (screenDensity in listOf(1f, 1.1625f, 2f, 3f)) {
                val overlayPixels = NavPuckSizing.displayEdgeDp(bitmapWidth) * screenDensity
                val imagePixelRatio = NavPuckSizing.IMAGE_DENSITY_DPI / 160f
                val mapSymbolPixels = bitmapWidth / imagePixelRatio * NavPuckSizing.SYMBOL_SCALE * screenDensity
                assertEquals(overlayPixels, mapSymbolPixels, 0.001f)
                assertEquals(NavPuckSizing.NORMAL_EDGE_DP * scale * screenDensity, overlayPixels, screenDensity)
            }
        }
    }

    @Test fun labelsRemainOutsideEveryArrowSize() {
        for (scale in listOf(1f, 1.25f, 1.5f)) {
            val edge = NavPuckSizing.displayEdgeDp((NavPuckSizing.BITMAP_EDGE * scale).toInt())
            assertTrue(NavPuckSizing.labelGapDp(scale) + 0.001f >= edge / 2f + 8f)
        }
    }
}
