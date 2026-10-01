package app.vela.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCameraFramingTest {
    @Test fun `manual horizontal adjustment stays between overlays`() {
        val left = MapCameraFraming.landscapeLeftInset(1200f, 700, 88)
        val centers = listOf(0.25f, 0.5f, 0.75f).map { bias ->
            val (l, r) = MapCameraFraming.horizontalPadding(1200f, left, 88, bias)
            (l + 1200 - r) / 2f
        }
        org.junit.Assert.assertTrue(centers[0] < centers[1] && centers[1] < centers[2])
        centers.forEach { org.junit.Assert.assertTrue(it > 716 && it < 1112) }
    }

    @Test fun `landscape centers in the right half and clears a wide ETA panel`() {
        assertEquals(600, MapCameraFraming.landscapeLeftInset(1200f, 180, 88))
        assertEquals(716, MapCameraFraming.landscapeLeftInset(1200f, 700, 88))
        assertEquals(0, MapCameraFraming.landscapeLeftInset(80f, 100, 88))
        val left = MapCameraFraming.landscapeLeftInset(1200f, 180, 88)
        val arrowX = (left + 1200 - 88) / 2f
        org.junit.Assert.assertTrue(arrowX > 600 && arrowX < 1112)
    }

    @Test fun `automatic follows orientation while explicit choices stay fixed`() {
        assertEquals(0.45, MapCameraFraming.fraction("auto", true), 0.001)
        assertEquals(0.0, MapCameraFraming.fraction("auto", false), 0.001)
        assertEquals(0.0, MapCameraFraming.fraction("center", true), 0.001)
        assertEquals(0.45, MapCameraFraming.fraction("bottom", false), 0.001)
    }
    @Test fun `arrow position respects visible area rather than whole screen`() {
        assertEquals(100.0, MapCameraFraming.topPadding(1000f, 100, 300, 0.0), 0.001)
        assertEquals(370.0, MapCameraFraming.topPadding(1000f, 100, 300, 0.45), 0.001)
        assertEquals(100.0, MapCameraFraming.topPadding(200f, 100, 300, 0.45), 0.001)
    }
}
