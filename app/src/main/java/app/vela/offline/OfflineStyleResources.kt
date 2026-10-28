package app.vela.offline

import org.json.JSONObject

/** Remove remote sources and their layers together, so an offline style has no dangling layers. */
internal fun removeRemoteStyleSources(style: JSONObject): Set<String> {
    val sources = style.getJSONObject("sources")
    val removed = sources.keys().asSequence().filter { id ->
        val source = sources.getJSONObject(id)
        // Android's JSONObject escapes slashes when serializing (https:\/\/).
        // Inspect decoded URL values instead of depending on JSON serialization.
        fun remote(value: String) = value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        val tiles = source.optJSONArray("tiles")
        remote(source.optString("url")) || remote(source.optString("data")) ||
            (tiles != null && (0 until tiles.length()).any { remote(tiles.optString(it)) })
    }.toSet()
    removed.forEach(sources::remove)
    val layers = style.getJSONArray("layers")
    for (i in layers.length() - 1 downTo 0) {
        if (layers.getJSONObject(i).optString("source") in removed) layers.remove(i)
    }
    return removed
}
