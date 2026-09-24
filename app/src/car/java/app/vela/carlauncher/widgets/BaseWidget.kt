package app.vela.carlauncher.widgets

import android.content.Context

/**
 * Masaustu ve panel widget'larinin temel soyut sinifi.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
abstract class BaseWidget(
    val id: String,
    val typeId: String,
    var title: String,
    var size: WidgetSize = WidgetSize.MEDIUM,
    var pageIndex: Int = 0,
    var cellX: Int = -1,
    var cellY: Int = -1,
    var isVisible: Boolean = true
) {

    enum class WidgetSize {
        SMALL,   // 1x1 veya kucuk kompakt
        MEDIUM,  // 2x1 veya orta genis
        LARGE    // 2x2 veya tam buyuk
    }

    protected var isStarted: Boolean = false

    open fun onStart(context: Context) {
        isStarted = true
    }

    open fun onStop() {
        isStarted = false
    }

    open fun onDestroy() {
        isStarted = false
    }
}
