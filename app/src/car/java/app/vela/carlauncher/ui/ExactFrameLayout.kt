package app.vela.carlauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Harita kucuk paneldeyken dokunmalari yakalayip tekrar buyuk panele gecmesini saglayan FrameLayout sinifi.
 * Turkce karakter kullanilmamistir.
 */
class ExactFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var interceptTouch = false
    private var onInterceptClickRunnable: Runnable? = null

    fun setInterceptTouch(intercept: Boolean, clickRunnable: Runnable?) {
        this.interceptTouch = intercept
        this.onInterceptClickRunnable = clickRunnable
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (interceptTouch) {
            return true
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (interceptTouch) {
            if (event.action == MotionEvent.ACTION_UP) {
                onInterceptClickRunnable?.run()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val exactWidth = if (widthMode != MeasureSpec.EXACTLY) {
            MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
        } else {
            widthMeasureSpec
        }

        val exactHeight = if (heightMode != MeasureSpec.EXACTLY) {
            MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }

        super.onMeasure(exactWidth, exactHeight)
    }
}
