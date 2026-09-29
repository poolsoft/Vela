package app.vela.carlauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset

/**
 * DuoLauncher mimarisinden uyarlanan ZeroPadding AppWidgetHost ve View.
 * - Sistem widget'larinin varsayilan genis padding'lerini (16-24dp) sifirlar (setPadding 0).
 * - ExactWidgetSize yayini yaparak Android 12+ duyarlı/responsive widget'larin tam oturmasini saglar.
 * - Widget ici dikey kaydirma tespit edildiginde parent HorizontalPager'i disallowIntercept ile engeller.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarZeroPaddingWidgetHost(context: Context, hostId: Int = 1024) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return CarZeroPaddingWidgetHostView(context)
    }
}

class CarZeroPaddingWidgetHostView(context: Context) : AppWidgetHostView(context) {

    private var providerOwnsVerticalGesture = false

    private val publishCurrentSize = Runnable {
        if (!isAttachedToWindow || appWidgetId < 0 || width <= 0 || height <= 0) return@Runnable
        val density = resources.displayMetrics.density
        publishExactWidgetSize(this, width / density, height / density)
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
        scheduleSizePublication()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleSizePublication()
    }

    override fun onSizeChanged(widthPx: Int, heightPx: Int, oldWidthPx: Int, oldHeightPx: Int) {
        super.onSizeChanged(widthPx, heightPx, oldWidthPx, oldHeightPx)
        if (appWidgetId < 0 || widthPx <= 0 || heightPx <= 0) return
        scheduleSizePublication()
    }

    override fun onDetachedFromWindow() {
        if (providerOwnsVerticalGesture) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        providerOwnsVerticalGesture = false
        removeCallbacks(publishCurrentSize)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                providerOwnsVerticalGesture = nativeWidgetConsumesVerticalGesture(
                    this,
                    Offset(event.rawX, event.rawY)
                )
                if (providerOwnsVerticalGesture) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (providerOwnsVerticalGesture) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                providerOwnsVerticalGesture = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun scheduleSizePublication() {
        removeCallbacks(publishCurrentSize)
        post(publishCurrentSize)
    }
}

internal fun exactWidgetSizeOptions(widthDp: Float, heightDp: Float) = Bundle().apply {
    val width = widthDp.coerceAtLeast(1f)
    val height = heightDp.coerceAtLeast(1f)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height.toInt())
    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width, height)))
}

private fun publishExactWidgetSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
    try {
        val options = exactWidgetSizeOptions(widthDp, heightDp)
        val current = AppWidgetManager.getInstance(view.context).getAppWidgetOptions(view.appWidgetId)
        val sameBounds = listOf(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        ).all { current.getInt(it) == options.getInt(it) }

        val currentSizes = current.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        val nextSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sameBounds || currentSizes != nextSizes) {
            view.updateAppWidgetOptions(options)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
