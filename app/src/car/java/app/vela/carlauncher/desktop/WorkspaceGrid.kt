package app.vela.carlauncher.desktop

import kotlin.math.abs
import kotlin.math.roundToInt

/** Places the existing cards on a bounded grid without changing the saved user layout. */
object WorkspaceGrid {
    private const val STEP = 16f

    private fun size(id: String): Pair<Float, Float> = when {
        id.startsWith(WidgetIds.APP_PREFIX) -> 80f to 96f
        id == WidgetIds.CLOCK -> 260f to 100f
        id == WidgetIds.STATUS -> 160f to 100f
        id == WidgetIds.SPEEDOMETER -> 240f to 160f
        id == WidgetIds.MUSIC -> 300f to 160f
        id == WidgetIds.COMBINED -> 340f to 120f
        id == WidgetIds.WEATHER || id == WidgetIds.COMPASS -> 170f to 90f
        id == WidgetIds.OBD -> 280f to 90f
        id == WidgetIds.DOCK -> 340f to 76f
        else -> 280f to 140f
    }

    fun resolve(
        ids: Set<String>,
        saved: Map<String, WidgetPlacement>,
        width: Float,
        height: Float
    ): Map<String, WidgetPlacement> {
        if (width <= 0f || height <= 0f) return emptyMap()
        val occupied = mutableMapOf<Int, MutableList<Rect>>()
        val result = linkedMapOf<String, WidgetPlacement>()
        val usableHeight = (height - 56f).coerceAtLeast(80f)
        ids.sortedWith(compareBy<String> { saved[it]?.page ?: 0 }.thenBy {
            listOf(WidgetIds.CLOCK, WidgetIds.STATUS, WidgetIds.SPEEDOMETER, WidgetIds.MUSIC,
                WidgetIds.DOCK).indexOf(it).let { rank -> if (rank < 0) 100 else rank }
        }.thenBy { it }).forEach { id ->
            val preferred = saved[id] ?: DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS[id] ?: WidgetPlacement()
            val (baseWidth, baseHeight) = size(id)
            val fitScale = minOf(1f, width / baseWidth, usableHeight / baseHeight)
            val scale = preferred.scale.coerceIn(0.6f, 2.2f).coerceAtMost(fitScale)
            val cardWidth = baseWidth * scale
            val cardHeight = baseHeight * scale
            val columns = ((width - cardWidth) / STEP).coerceAtLeast(0f).toInt()
            val rows = ((usableHeight - cardHeight) / STEP).coerceAtLeast(0f).toInt()
            val preferredX = (preferred.dx / STEP).roundToInt().coerceIn(0, columns)
            val preferredY = (preferred.dy / STEP).roundToInt().coerceIn(0, rows)
            val cells = (0..rows).flatMap { y -> (0..columns).map { x -> x to y } }
                .sortedBy { (x, y) -> abs(x - preferredX) + abs(y - preferredY) }
            for (page in preferred.page..preferred.page + ids.size) {
                val rectangles = occupied.getOrPut(page) { mutableListOf() }
                val location = cells.firstOrNull { (x, y) ->
                    val candidate = Rect(x * STEP, y * STEP, cardWidth, cardHeight)
                    rectangles.none { it.intersects(candidate) }
                } ?: continue
                val rect = Rect(location.first * STEP, location.second * STEP, cardWidth, cardHeight)
                rectangles += rect
                result[id] = WidgetPlacement(rect.x, rect.y, scale, page)
                break
            }
        }
        return result
    }

    private data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun intersects(other: Rect): Boolean =
            x < other.x + other.width && x + width > other.x &&
                y < other.y + other.height && y + height > other.y
    }
}
