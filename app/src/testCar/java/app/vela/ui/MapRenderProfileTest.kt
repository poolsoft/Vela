package app.vela.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapRenderProfileTest {
    @Test fun weakDevicesKeepLightAfterStartupButHonorExplicitCustom() {
        assertEquals(MapRenderProfile.LIGHT, MapRenderProfile.effective(MapRenderProfile.STANDARD, true, false))
        assertEquals(MapRenderProfile.STANDARD, MapRenderProfile.effective(MapRenderProfile.STANDARD, false, false))
        assertEquals(MapRenderProfile.CUSTOM, MapRenderProfile.effective(MapRenderProfile.CUSTOM, true, false))
        assertEquals(MapRenderProfile.LIGHT, MapRenderProfile.effective(MapRenderProfile.CUSTOM, false, true))
    }
}
