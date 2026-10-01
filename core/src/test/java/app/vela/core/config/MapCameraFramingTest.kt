package app.vela.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCameraFramingTest {
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
