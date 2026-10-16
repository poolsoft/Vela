package app.vela.ui.map

/** Keeps fractional rates (for example 45 on a 60 Hz display) without catch-up bursts. */
internal class RenderFrameBudget {
    private var deadline = 0L
    private var previousFps = 0
    fun reset() { deadline = 0L }

    fun shouldRender(now: Long, fps: Int): Boolean {
        if (fps != previousFps) { reset(); previousFps = fps }
        if (fps <= 0) { reset(); return true }
        val interval = 1_000_000_000L / fps
        if (deadline == 0L || now - deadline > interval) {
            deadline = now + interval
            return true
        }
        if (now < deadline) return false
        deadline += interval
        return true
    }
}
