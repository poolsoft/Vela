package app.vela.core.config

/** Camera padding positions the location inside the unobscured map rectangle. */
object MapCameraFraming {
    /** Keep the location in the right half, clear of ETA and right-side controls. */
    fun landscapeLeftInset(width: Float, etaRightEdge: Int, rightInset: Int): Int =
        maxOf((width * 0.5f).toInt(), etaRightEdge + 16)
            .coerceIn(0, (width - rightInset - 32).toInt().coerceAtLeast(0))

    /** Bias within the clear rectangle, never into ETA or the right-side buttons. */
    fun horizontalPadding(width: Float, leftInset: Int, rightInset: Int, bias: Float): Pair<Int, Int> {
        val clearWidth = (width - leftInset - rightInset).coerceAtLeast(0f)
        val offset = bias.coerceIn(0.25f, 0.75f) - 0.5f
        return if (offset >= 0) leftInset + (clearWidth * offset * 2).toInt() to rightInset
            else leftInset to rightInset + (-clearWidth * offset * 2).toInt()
    }

    fun fraction(mode: String, headingUp: Boolean): Double = when (mode) {
        "center" -> 0.0
        "bottom" -> 0.45
        else -> if (headingUp) 0.45 else 0.0
    }

    fun topPadding(height: Float, topInset: Int, bottomInset: Int, fraction: Double): Double =
        topInset + (height - topInset - bottomInset).coerceAtLeast(0f) * fraction
}
