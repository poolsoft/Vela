package app.vela.carlauncher.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.Xfermode
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * CoMaps / OsmAnd InterlockingClockView.
 * Saat karakterlerini konturlu ve ic ice gecmeli neon bicimde cizen ozel saat gorunumu.
 * Kod icerisinde Turkce karakter kullanilmamistir.
 */
class InterlockingClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    companion object {
        private const val OVERLAP_EM = 0.16f
        private const val COLON_GAP_EM = 0.06f
        private const val COLON_SCALE = 0.72f
        private val CLEAR_XFERMODE: Xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    var fillColor: Int = android.graphics.Color.TRANSPARENT
        set(value) { field = value; invalidate() }

    var embossed: Boolean = false
        set(value) { field = value; invalidate() }
    var fitTextToBounds: Boolean = false
        set(value) { field = value; invalidate() }

    init {
        applyClockTypeface()
    }

    private fun applyClockTypeface() {
        try {
            typeface = Typeface.createFromAsset(context.assets, "fonts/Cross Boxed.ttf")
        } catch (e: Exception) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    override fun onDraw(canvas: Canvas) {
        val value = text
        if (value.isNullOrEmpty()) return

        val p = paint
        val originalTextSize = p.textSize
        var baseTextSize = originalTextSize
        var overlap = p.textSize * OVERLAP_EM
        var colonGap = p.textSize * COLON_GAP_EM
        var contentWidth = 0f

        for (i in 0 until value.length) {
            val isColon = value[i] == ':'
            p.textSize = if (isColon) baseTextSize * COLON_SCALE else baseTextSize
            contentWidth += p.measureText(value, i, i + 1)
            if (i < value.length - 1) {
                contentWidth += spacingAfter(value, i, overlap, colonGap)
            }
        }
        p.textSize = baseTextSize

        // Measure the actual clock glyphs, including overlap, before fitting the panel.
        if (fitTextToBounds) {
            val inset = resources.displayMetrics.density * 3f
            val availableWidth = (width - paddingLeft - paddingRight - inset * 2).coerceAtLeast(1f)
            val availableHeight = (height - paddingTop - paddingBottom - inset * 2).coerceAtLeast(1f)
            val glyphHeight = p.fontMetrics.let { it.bottom - it.top }
            val scale = minOf(1f, availableWidth / contentWidth.coerceAtLeast(1f),
                availableHeight / glyphHeight.coerceAtLeast(1f))
            baseTextSize *= scale
            overlap *= scale
            colonGap *= scale
            contentWidth *= scale
            p.textSize = baseTextSize
        }
        val metrics = p.fontMetrics
        var x = paddingLeft + (width - paddingLeft - paddingRight - contentWidth).coerceAtLeast(0f) / 2f
        val baseline = paddingTop + (height - paddingTop - paddingBottom - metrics.bottom - metrics.top) / 2f

        val oldStyle = p.style
        val oldColor = p.color
        val oldStrokeWidth = p.strokeWidth
        val oldXfermode = p.xfermode
        val outlineWidth = (resources.displayMetrics.density * 1.15f).coerceAtLeast(1f)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        for (i in 0 until value.length) {
            val glyph = value.substring(i, i + 1)
            val isColon = value[i] == ':'
            p.textSize = if (isColon) baseTextSize * COLON_SCALE else baseTextSize
            val glyphBaseline = if (isColon) baseline - baseTextSize * 0.10f else baseline

            p.xfermode = CLEAR_XFERMODE
            p.style = Paint.Style.FILL_AND_STROKE
            p.strokeWidth = outlineWidth * 2.4f
            canvas.drawText(glyph, x, glyphBaseline, p)

            p.xfermode = oldXfermode
            if (embossed) {
                val depth = resources.displayMetrics.density
                p.style = Paint.Style.STROKE
                p.strokeWidth = outlineWidth * 1.6f
                p.color = 0xB0000000.toInt()
                canvas.drawText(glyph, x + depth * 1.4f, glyphBaseline + depth * 1.4f, p)
                p.color = 0xA0FFFFFF.toInt()
                canvas.drawText(glyph, x - depth * 0.7f, glyphBaseline - depth * 0.7f, p)
            }
            if (fillColor != android.graphics.Color.TRANSPARENT) {
                p.style = Paint.Style.FILL
                p.color = fillColor
                canvas.drawText(glyph, x, glyphBaseline, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = outlineWidth
            var outlineColor = currentTextColor
            if (isColon) {
                outlineColor = (outlineColor and 0x00FFFFFF) or 0xB0000000.toInt()
            }
            p.color = outlineColor
            canvas.drawText(glyph, x, glyphBaseline, p)
            x += p.measureText(glyph)
            if (i < value.length - 1) {
                x += spacingAfter(value, i, overlap, colonGap)
            }
        }

        p.style = oldStyle
        p.color = oldColor
        p.strokeWidth = oldStrokeWidth
        p.textSize = originalTextSize
        p.xfermode = oldXfermode
        canvas.restoreToCount(layer)
    }

    private fun spacingAfter(value: CharSequence, index: Int, overlap: Float, colonGap: Float): Float {
        return if (value[index] == ':' || value[index + 1] == ':') colonGap else -overlap
    }
}
