package app.vela.carlauncher.settings

import app.vela.carlauncher.model.InternalApp
import org.junit.Assert.*
import org.junit.Test

class MapSessionTest {
    @Test fun explicitMapOpeningPreservesMaplessStartupAndIsIdempotent() {
        CarLauncherSettings.setMaplessMode(true)
        assertTrue(CarLauncherSettings.maplessMode.value)
        CarLauncherSettings.openMapForSession()
        assertFalse(CarLauncherSettings.maplessMode.value)
        assertTrue(CarLauncherSettings.maplessPreference.value)
        CarLauncherSettings.openMapForSession()
        assertFalse(CarLauncherSettings.maplessMode.value)
        assertTrue(CarLauncherSettings.maplessPreference.value)
        assertEquals(InternalApp.MAP, InternalApp.fromUri("internal://map"))
        CarLauncherSettings.setMaplessMode(true)
        assertTrue(CarLauncherSettings.maplessMode.value)
        CarLauncherSettings.setMaplessMode(false)
    }
}
