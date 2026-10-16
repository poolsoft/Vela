package app.vela.carlauncher.apps

import app.vela.carlauncher.model.AppShortcut
import app.vela.carlauncher.model.LaunchMode
import org.junit.Assert.*
import org.junit.Test

class DockMetadataTest {
    @Test fun pendingMetadataPreservesReorderModeAddsAndRemovals() {
        val a = AppShortcut("a", "placeholder", sira = 1, launchMode = LaunchMode.OVERLAY)
        val b = AppShortcut("b", "B", sira = 0)
        val added = AppShortcut("new", "New", sira = 2)
        val loaded = mapOf("a" to a.copy(ad = "Loaded A", sira = 0, launchMode = LaunchMode.FULL_SCREEN),
            "removed" to AppShortcut("removed", "Old"))
        val result = mergeDockMetadata(listOf(b, a, added), loaded)
        assertEquals(listOf("b", "a", "new"), result.map { it.paketAdi })
        assertEquals("Loaded A", result[1].ad)
        assertEquals(1, result[1].sira)
        assertEquals(a.launchMode, result[1].launchMode)
    }

    @Test fun unavailablePackagesDisappearWithoutRemovingUnrelatedEntries() {
        val items = listOf(AppShortcut("missing", "Missing"), AppShortcut("keep", "Keep"))
        assertEquals(listOf(items[1]), mergeDockMetadata(items, mapOf("missing" to null)))
    }
}
