package app.vela.carlauncher.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.Xfermode
import android.util.AttributeSet
import android.view.View
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

    var minuteFillColor: Int? = null
        set(value) { field = value; invalidate() }

    var embossed: Boolean = false
        set(value) { field = value; invalidate() }
    var fitTextToBounds: Boolean = false
        set(value) { field = value; invalidate() }

    var digitSpacingEm: Float = -OVERLAP_EM
        set(value) { field = value.coerceIn(-0.26f, 0.12f); invalidate() }
    var colonSpacingEm: Float = COLON_GAP_EM
        set(value) { field = value.coerceIn(0f, 0.2f); invalidate() }
    var outlineWidthDp: Float = 1.15f
        set(value) { field = value.coerceIn(0.5f, 3f); invalidate() }
    var clockFont: Int = 0
        set(value) { if (field != value) { field = value; applyClockTypeface(); invalidate() } }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        applyClockTypeface()
    }

    private fun applyClockTypeface() {
        when (clockFont) {
            1 -> {
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            }
            2 -> {
                typeface = Typeface.create("monospace", Typeface.BOLD)
            }
            3 -> {
                try {
                    typeface = Typeface.createFromAsset(context.assets, "fonts/condensed_clock.ttf")
                } catch (e: Exception) {
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
            else -> {
                try {
                    typeface = Typeface.createFromAsset(context.assets, "fonts/Cross Boxed.ttf")
                } catch (e: Exception) {
                    typeface = Typeface.DEFAULT_BOLD
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val value = text
        val p = paint
        val metrics = p.fontMetrics
        val textH = (metrics.bottom - metrics.top).coerceAtLeast(1f)
        val density = resources.displayMetrics.density
        val minH = (36f * density).toInt()

        val desiredHeight = (textH + paddingTop + paddingBottom + density * 4f).toInt().coerceAtLeast(minH)
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }

        val measuredWidth = if (widthMode == MeasureSpec.EXACTLY) {
            widthSize
        } else {
            var w = 0f
            if (!value.isNullOrEmpty()) {
                val baseTextSize = p.textSize
                val overlap = -baseTextSize * digitSpacingEm
                val colonGap = baseTextSize * colonSpacingEm
                for (i in 0 until value.length) {
                    val isColon = value[i] == ':'
                    p.textSize = if (isColon) baseTextSize * COLON_SCALE else baseTextSize
                    w += p.measureText(value, i, i + 1)
                    if (i < value.length - 1) {
                        w += spacingAfter(value, i, overlap, colonGap)
                    }
                }
                p.textSize = baseTextSize
            }
            val desiredWidth = (w + paddingLeft + paddingRight + density * 4f).toInt()
            if (widthMode == MeasureSpec.AT_MOST) minOf(desiredWidth, widthSize) else desiredWidth
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            invalidate()
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val value = text
        if (value.isNullOrEmpty()) return

        val p = paint
        val originalTextSize = p.textSize
        var baseTextSize = originalTextSize
        var overlap = -p.textSize * digitSpacingEm
        var colonGap = p.textSize * colonSpacingEm
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
            val widthScale = availableWidth / contentWidth.coerceAtLeast(1f)
            val heightScale = if (layoutParams != null && layoutParams.height > 0) {
                val availableHeight = (height - paddingTop - paddingBottom - inset * 2).coerceAtLeast(1f)
                val glyphHeight = p.fontMetrics.let { it.bottom - it.top }
                availableHeight / glyphHeight.coerceAtLeast(1f)
            } else 1f
            val scale = minOf(1f, widthScale, heightScale)
            if (scale < 1f) {
                baseTextSize *= scale
                overlap *= scale
                colonGap *= scale
                contentWidth *= scale
                p.textSize = baseTextSize
            }
        }
        val metrics = p.fontMetrics
        var x = paddingLeft + (width - paddingLeft - paddingRight - contentWidth).coerceAtLeast(0f) / 2f
        val baseline = paddingTop + (height - paddingTop - paddingBottom - metrics.bottom - metrics.top) / 2f

        val oldStyle = p.style
        val oldColor = p.color
        val oldStrokeWidth = p.strokeWidth
        val oldXfermode = p.xfermode
        val outlineWidth = (resources.displayMetrics.density * outlineWidthDp).coerceAtLeast(0.5f)
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
            val glyphColor = if (isMinute(value, i)) minuteFillColor ?: fillColor else fillColor
            if (glyphColor != android.graphics.Color.TRANSPARENT) {
                p.style = Paint.Style.FILL
                p.color = glyphColor
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

    private fun isMinute(value: CharSequence, index: Int): Boolean {
        val colonIdx = value.indexOf(':')
        return if (colonIdx >= 0) {
            index > colonIdx
        } else {
            index >= (value.length / 2)
        }
    }

    private fun spacingAfter(value: CharSequence, index: Int, overlap: Float, colonGap: Float): Float {
        return if (value[index] == ':' || value[index + 1] == ':') colonGap else -overlap
    }
}
