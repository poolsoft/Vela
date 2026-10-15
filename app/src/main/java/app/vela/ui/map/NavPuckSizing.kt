package app.vela.ui.map

/** The bitmap is high resolution artwork, not a physical screen-size specification. */
internal object NavPuckSizing {
    const val BITMAP_EDGE = 202
    const val NORMAL_EDGE_DP = 88f
    // MapLibre derives image pixelRatio from bitmap density / 160.
    const val IMAGE_DENSITY_DPI = 160
    const val SYMBOL_SCALE = NORMAL_EDGE_DP / BITMAP_EDGE

    fun displayEdgeDp(bitmapWidth: Int): Float = bitmapWidth * SYMBOL_SCALE
    fun labelGapDp(userScale: Float): Float = NORMAL_EDGE_DP * userScale / 2f + 8f
}
