package app.vela.core.config

/** Camera padding positions the location inside the unobscured map rectangle. */
object MapCameraFraming {
    fun fraction(mode: String, headingUp: Boolean): Double = when (mode) {
        "center" -> 0.0
        "bottom" -> 0.45
        else -> if (headingUp) 0.45 else 0.0
    }

    fun topPadding(height: Float, topInset: Int, bottomInset: Int, fraction: Double): Double =
        topInset + (height - topInset - bottomInset).coerceAtLeast(0f) * fraction
}
