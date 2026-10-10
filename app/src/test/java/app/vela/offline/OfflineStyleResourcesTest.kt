package app.vela.offline

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfflineStyleResourcesTest {
    @Test fun remoteRasterAndVectorSourcesCannotLeaveDanglingLayers() {
        val style = JSONObject("""{"sources":{
          "raster":{"type":"raster","tiles":["https://host/{z}.png"]},
          "vector":{"type":"vector","url":"http://host/tiles.json"},
          "local":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},
          "layers":[{"id":"background","type":"background"},
          {"id":"shade","source":"raster"},{"id":"road","source":"vector"},
          {"id":"route","source":"local"}]}""")
        assertEquals(setOf("raster", "vector"), removeRemoteStyleSources(style))
        assertEquals(setOf("local"), style.getJSONObject("sources").keySet())
        val layers = style.getJSONArray("layers")
        assertEquals(listOf("background", "route"), (0 until layers.length()).map { layers.getJSONObject(it).getString("id") })
        assertTrue(removeRemoteStyleSources(style).isEmpty())
    }
}
