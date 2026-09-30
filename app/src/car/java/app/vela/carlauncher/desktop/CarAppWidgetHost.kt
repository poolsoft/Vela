package app.vela.carlauncher.desktop

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Android 3. parti AppWidget barindirici yoneticisi (CarAppWidgetHostController).
 * UmainLauncher mimarisi ornek alinmistir.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarAppWidgetHostController(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context.applicationContext, HOST_ID)

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun allocateId(): Int = host.allocateAppWidgetId()
    fun deleteId(id: Int) = host.deleteAppWidgetId(id)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    @Suppress("DEPRECATION")
    fun createView(id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context, id, info)
    }

    companion object {
        const val HOST_ID = 0x7E1A
    }
}

val LocalCarAppWidgetHost = staticCompositionLocalOf<CarAppWidgetHostController?> { null }

@Composable
fun HostedAppWidgetView(appWidgetId: Int, modifier: Modifier = Modifier) {
    val host = LocalCarAppWidgetHost.current ?: return
    val context = LocalContext.current
    val info = remember(appWidgetId) { host.info(appWidgetId) } ?: return

    AndroidView(
        modifier = modifier,
        factory = { host.createView(appWidgetId) ?: View(context) },
        update = { view ->
            if (view is AppWidgetHostView) {
                val minW = info.minWidth.coerceAtLeast(80)
                val minH = info.minHeight.coerceAtLeast(80)
                view.updateAppWidgetSize(Bundle(), minW, minH, minW * 2, minH * 2)
            }
        }
    )
}
