/*
 * OLU KOD: cagrilmayan ikinci (view tabanli) widget sisteminden kalinti.
 * Neden oldugu ve silmeden once uygulanacak kontrol adimlari: ayni klasordeki README.kt.
 * Yeni kod buraya EKLENMEZ; canli sistem: app/vela/carlauncher/desktop + widgets/WidgetRegistry.
 */
package app.vela.carlauncher.legacy

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/**
 * Car Launcher icin tekil AppWidgetHost yoneticisi.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarAppWidgetHost(context: Context, hostId: Int = HOST_ID) : AppWidgetHost(context, hostId) {

    companion object {
        const val HOST_ID = 1024
    }

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return CarAppWidgetHostView(context)
    }
}
