package app.vela.ui.map

import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.sources.Source

/**
 * The part of a loaded MapLibre style the palette functions need: a layer by id, a source by id,
 * the layer list. A [Style] has all three; a [MapSnapshotter] (the Android Auto renderer) has the
 * first two and no list, and until 2026-09-21 the car map could not be themed at all because every
 * palette function took a [Style]. The same `applyMapTheme` now runs on both, so the car screen
 * draws Vela's own colors instead of the stock Liberty look.
 */
interface StyleLayers {
    fun getLayer(id: String): Layer?
    fun getSource(id: String): Source?
    val layers: List<Layer>
}

class StyleHost(private val style: Style) : StyleLayers {
    override fun getLayer(id: String): Layer? = runCatching {
        if (style.isFullyLoaded) style.getLayer(id) else null
    }.getOrNull()

    override fun getSource(id: String): Source? = runCatching {
        if (style.isFullyLoaded) style.getSource(id) else null
    }.getOrNull()

    override val layers: List<Layer> get() = runCatching {
        if (style.isFullyLoaded) style.layers else emptyList()
    }.getOrDefault(emptyList())
}

/** A snapshotter exposes layers and sources by id only; the loops over the whole list (Vela's own
 *  runtime layers, none of which exist on the car map) see nothing, which is right. */
class SnapshotterHost(private val snapshotter: MapSnapshotter, private val layerIds: List<String> = emptyList()) : StyleLayers {
    override fun getLayer(id: String): Layer? = runCatching { snapshotter.getLayer(id) }.getOrNull()
    override fun getSource(id: String): Source? = runCatching { snapshotter.getSource(id) }.getOrNull()
    /** The snapshotter has no layer list of its own; the caller hands in the ids parsed out of
     *  the style JSON it loaded, so the palette's blanket passes (label halos, landuse) run on the
     *  car too. Empty ids = only the named-layer passes, which is how it was until 2026-09-22. */
    override val layers: List<Layer> get() = layerIds.mapNotNull { getLayer(it) }
}
