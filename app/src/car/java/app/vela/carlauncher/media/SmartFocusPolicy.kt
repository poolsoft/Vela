package app.vela.carlauncher.media

/** Source tracking never sends playback commands. Media connection alone is not playback. */
internal class SmartFocusPolicy {
    data class Source(val id: String, val playing: Boolean, val eligible: Boolean = true,
        val available: Boolean = true, val preparing: Boolean = false)
    private var previous = emptyMap<String, Source>()
    private val starts = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var consumed = 0L
    private var initialized = false
    private var stoppedAt: Long? = null
    private var selectedWhilePaused: String? = null
    var nextCheckInMs: Long? = null
        private set

    fun selected(id: String, sources: List<Source>) {
        observe(sources)
        initialized = true
        consumed = sequence
        selectedWhilePaused = id
        stoppedAt = null
    }

    private fun observe(sources: List<Source>) {
        sources.filter { it.playing && previous[it.id]?.playing != true }.forEach { starts[it.id] = ++sequence }
        previous = sources.associateBy { it.id }
        starts.keys.retainAll(previous.keys)
    }

    fun choose(sources: List<Source>, current: String, automatic: Boolean, pinned: Boolean,
        interrupted: Boolean, now: Long): String {
        val first = !initialized
        observe(sources)
        initialized = true
        nextCheckInMs = null
        val active = sources.firstOrNull { it.id == current }
        if (active?.playing == true) { stoppedAt = null; selectedWhilePaused = null }
        if (!automatic || pinned || interrupted) { consumed = sequence; return current }
        val candidates = sources.filter { it.eligible && it.available && it.playing }
        if (!first) {
            val newest = candidates.filter { (starts[it.id] ?: 0) > consumed }.maxByOrNull { starts[it.id] ?: 0 }
            consumed = sequence
            if (newest != null && newest.id != current) { stoppedAt = null; selectedWhilePaused = null; return newest.id }
        } else {
            consumed = sequence
            if (active?.playing != true) return candidates.firstOrNull { it.id == "bluetooth" }?.id
                ?: candidates.firstOrNull()?.id ?: current
        }
        if (active?.playing == true || selectedWhilePaused == current) return current
        val since = stoppedAt ?: now.also { stoppedAt = it }
        val wait = if (active?.preparing == true) 8_000L else 2_000L
        if (active?.available == true && now - since < wait) {
            nextCheckInMs = wait - (now - since)
            return current
        }
        stoppedAt = null
        return candidates.firstOrNull()?.id
            ?: if (current == "bluetooth" || active?.available != true)
                sources.firstOrNull { it.id == "internal" }?.id ?: current else current
    }
}

internal object MediaSourceIdentity {
    fun kind(id: String, pkg: String?): String = when {
        id == "internal" -> "internal"
        id == "bluetooth" || pkg?.contains("bluetooth", ignoreCase = true) == true -> "bluetooth"
        id.endsWith("radio") -> "radio"
        pkg != null && isProjection(pkg) -> "projection"
        else -> "application"
    }
    fun isProjection(pkg: String): Boolean = pkg.startsWith("com.zjinnova.zlink") ||
        pkg.startsWith("com.suding.speedplay") || pkg.startsWith("cn.manstep.phonemirrorBox") ||
        pkg == "com.google.android.projection.gearhead"
}
