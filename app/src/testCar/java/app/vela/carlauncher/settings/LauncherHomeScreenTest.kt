package app.vela.carlauncher.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherHomeScreenTest {
    @Test fun `selection applies immediately and repeated Home restores the selection`() {
        val previous = CarLauncherSettings.baslangicEkrani.value
        try {
            val before = CarLauncherSettings.homeScreenRequest.value
            CarLauncherSettings.setBaslangicEkrani("desktop")
            assertTrue(CarLauncherSettings.desktopModu.value)
            assertTrue(CarLauncherSettings.homeScreenRequest.value > before)
            CarLauncherSettings.setDesktopModu(false)
            CarLauncherSettings.requestHomeScreen()
            assertTrue(CarLauncherSettings.desktopModu.value)
            val first = CarLauncherSettings.homeScreenRequest.value
            CarLauncherSettings.requestHomeScreen()
            assertTrue(CarLauncherSettings.homeScreenRequest.value > first)
            CarLauncherSettings.setBaslangicEkrani("normal")
            assertFalse(CarLauncherSettings.desktopModu.value)
        } finally {
            CarLauncherSettings.setBaslangicEkrani(previous)
        }
    }
}
