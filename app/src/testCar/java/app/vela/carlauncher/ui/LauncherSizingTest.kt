package app.vela.carlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSizingTest {
    @Test fun measuredWidthDeterminesColumnsBeforeChildrenAreLaidOut() {
        assertEquals(3, drawerColumns(344, 1f))
        assertEquals(8, drawerColumns(820, 1f))
        assertEquals(4, drawerColumns(820, 2f))
        assertEquals(2, drawerColumns(0, 1f))
    }
    @Test fun dockCreationAndRebindingShareTheSameMeasuredSize() {
        assertEquals(52, dockShortcutSize(64, 1f, true))
        assertEquals(56, dockShortcutSize(64, 1f, false))
        assertEquals(40, dockShortcutSize(10, 1f, true))
        assertEquals(128, dockShortcutSize(400, 2f, true))
    }
}
