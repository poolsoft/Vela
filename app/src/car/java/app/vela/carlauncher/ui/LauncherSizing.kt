package app.vela.carlauncher.ui

internal fun drawerColumns(usablePixels: Int, density: Float): Int =
    (usablePixels / (88f * density.coerceAtLeast(0.1f))).toInt().coerceIn(2, 8)

internal fun dockShortcutSize(crossPixels: Int, density: Float, vertical: Boolean): Int =
    (crossPixels - (if (vertical) 12 else 8) * density).toInt()
        .coerceIn((40 * density).toInt().coerceAtLeast(1), (64 * density).toInt().coerceAtLeast(1))

internal fun dockSidebarWidth(dockSizePercent: Int, density: Float): Int {
    val scale = 0.3f + (dockSizePercent.coerceIn(0, 100) / 100f) * 1.4f
    val base = 64f * density
    return (base * scale).toInt().coerceIn((64 * density).toInt(), (104 * density).toInt())
}
