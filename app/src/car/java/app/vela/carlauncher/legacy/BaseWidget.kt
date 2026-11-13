/*
 * OLU KOD: cagrilmayan ikinci (view tabanli) widget sisteminden kalinti.
 * Neden oldugu ve silmeden once uygulanacak kontrol adimlari: ayni klasordeki README.kt.
 * Yeni kod buraya EKLENMEZ; canli sistem: app/vela/carlauncher/desktop + widgets/WidgetRegistry.
 */
package app.vela.carlauncher.legacy

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
    var spanX: Int = 1,
    var spanY: Int = 1,
    var appWidgetId: Int = -1,
    var packageName: String? = null,
    var isVisible: Boolean = true,
    var customConfig: String? = null
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

/**
 * Eski dinamik widget veri tutucusu. Yeni kod WidgetPlacement + WidgetRegistry kullanir.
 */
class GenericWidget(
    id: String,
    typeId: String,
    title: String,
    size: WidgetSize = WidgetSize.MEDIUM,
    pageIndex: Int = 0,
    cellX: Int = -1,
    cellY: Int = -1,
    spanX: Int = 1,
    spanY: Int = 1,
    appWidgetId: Int = -1,
    packageName: String? = null,
    customConfig: String? = null
) : BaseWidget(
    id = id,
    typeId = typeId,
    title = title,
    size = size,
    pageIndex = pageIndex,
    cellX = cellX,
    cellY = cellY,
    spanX = spanX,
    spanY = spanY,
    appWidgetId = appWidgetId,
    packageName = packageName,
    customConfig = customConfig
)
