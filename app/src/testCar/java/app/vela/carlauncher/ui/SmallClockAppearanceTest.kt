package app.vela.carlauncher.ui

import org.junit.Assert.*
import org.junit.Test

class SmallClockAppearanceTest {
    @Test fun defaultsPreserveTheExistingClockGeometry() {
        val style = SmallClockAppearance().normalized()
        assertEquals(6, style.palette)
        assertEquals(1f, style.scale, 0f)
        assertEquals(-0.16f, style.spacing, 0f)
        assertEquals(0.06f, style.colonSpacing, 0f)
        assertEquals(1.15f, style.outline, 0f)
        assertTrue(style.dual)
        assertFalse(style.seconds)
    }

    @Test fun restoredValuesStayWithinResponsiveDrawingBounds() {
        val style = SmallClockAppearance(palette = 99, scale = 5f, spacing = -2f,
            colonSpacing = 4f, outline = 10f, opacity = -5, font = 22, format = "unknown").normalized()
        assertEquals(7, style.palette)
        assertEquals(1.5f, style.scale, 0f)
        assertEquals(-0.26f, style.spacing, 0f)
        assertEquals(0.2f, style.colonSpacing, 0f)
        assertEquals(3f, style.outline, 0f)
        assertEquals(0, style.opacity)
        assertEquals(2, style.font)
        assertEquals("system", style.format)
    }

    @Test fun invalidFloatingPointValuesDoNotReachTheCanvas() {
        val style = SmallClockAppearance(scale = Float.NaN, spacing = Float.NEGATIVE_INFINITY,
            colonSpacing = Float.POSITIVE_INFINITY, outline = Float.NaN).normalized()
        assertEquals(SmallClockAppearance(), style)
    }
    @Test fun clockPreferencesAreIncludedInThePortableLauncherSchema() {
        val schema = app.vela.carlauncher.tools.LauncherBackup.schema.getValue("small_music_panel")
        assertEquals("Float", schema["clock_scale"])
        assertEquals("Float", schema["clock_spacing"])
        assertEquals("Int", schema["clock_custom_secondary"])
        assertEquals("String", schema["clock_format"])
        assertEquals("Boolean", schema["clock_seconds"])
    }

}
