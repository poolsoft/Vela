package app.vela.carlauncher.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextView

/**
 * Android Sistem Widget'lari icin guvenli HostView.
 * - RemoteViews null geldiginde crash veya varsayilan hata ekrani yerine yukleniyor gosterir.
 * - Uzun basma olaylarini (Long Click) edit modu icin yukari iletir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private var hasReceivedRemoteViews = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var isLongClickPending = false

    private val longClickRunnable = Runnable {
        if (isLongClickPending) {
            isLongClickPending = false
            performLongClick()
        }
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (remoteViews == null) {
            if (!hasReceivedRemoteViews) {
                showLoadingPlaceholder()
            }
            return
        }
        hasReceivedRemoteViews = true
        try {
            super.updateAppWidget(remoteViews)
        } catch (e: Exception) {
            showErrorPlaceholder(e.localizedMessage ?: "Widget Hatasi")
        }
    }

    private fun showLoadingPlaceholder() {
        removeAllViews()
        val frame = FrameLayout(context).apply {
            setBackgroundColor(0x22FFFFFF)
        }
        val pBar = ProgressBar(context).apply {
            isIndeterminate = true
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            layoutParams = lp
        }
        frame.addView(pBar)
        addView(frame)
    }

    private fun showErrorPlaceholder(message: String) {
        removeAllViews()
        val errorText = TextView(context).apply {
            text = "Widget Yüklenemedi\n$message"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            textSize = 11f
        }
        addView(errorText)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isLongClickPending = true
                mainHandler.postDelayed(longClickRunnable, 500L)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - downX)
                val dy = Math.abs(ev.y - downY)
                if (dx > 20 || dy > 20) {
                    isLongClickPending = false
                    mainHandler.removeCallbacks(longClickRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isLongClickPending = false
                mainHandler.removeCallbacks(longClickRunnable)
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
