package app.vela.offline

import org.json.JSONObject

/** Remove remote sources and their layers together, so an offline style has no dangling layers. */
internal fun removeRemoteStyleSources(style: JSONObject): Set<String> {
    val sources = style.getJSONObject("sources")
    val removed = sources.keys().asSequence().filter { id ->
        val source = sources.getJSONObject(id).toString()
        source.contains("https://") || source.contains("http://")
    }.toSet()
    removed.forEach(sources::remove)
    val layers = style.getJSONArray("layers")
    for (i in layers.length() - 1 downTo 0) {
        if (layers.getJSONObject(i).optString("source") in removed) layers.remove(i)
    }
    return removed
}
