package app.vela.carlauncher.map

import app.vela.ui.map.*
import app.vela.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MapRenderSceneTest {
    @Test fun largeGeometryAndInteractiveStateSurviveWireRoundTrip() {
        val route = List(20_000) { LatLng(37.0 + it / 100_000.0, 36.0) }
        val original = MapRenderScene(routePolyline = route,
            routeTrafficSpans = listOf(Triple(0.1f, 0.7f, 2)),
            alternates = listOf(3 to route.take(20)),
            routeBubbles = listOf(RouteBubble(3, route[10], "5 min", true)),
            markers = listOf(MapMarker("Home", route[0])),
            savedPins = listOf(SavedPin(37.0, 36.0, "home", 0xff0080ff)),
            navExitCallout = route[5] to "Exit 2", cameraLeftInsetPx = 240,
            tuning = mapOf("browseZoom" to 16.0), mapPalette = "classic")
        val wire = MapRenderJson.json.encodeToString(original)
        val restored = MapRenderJson.json.decodeFromString<MapRenderScene>(wire)
        assertEquals(original, restored)
    }
    @Test fun callbacksKeepPoiCoordinatesAndNavigationCompassConsumption() {
        var name = ""
        var payload = JsonArray(emptyList())
        val callbacks = MapRenderCallbacks.sending(consumeCompass = true) { n, args -> name = n; payload = args }
        callbacks.onPoiTap("Cafe", LatLng(37.2, 36.4), "cafe")
        assertEquals("onPoiTap", name)
        assertEquals(LatLng(37.2, 36.4), MapRenderJson.json.decodeFromJsonElement<LatLng>(payload[1]))
        assertTrue(callbacks.onCompassTap())
        assertEquals("onCompassTap", name)
    }
}
