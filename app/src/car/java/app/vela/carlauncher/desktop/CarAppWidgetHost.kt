package app.vela.carlauncher.desktop

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val density = LocalDensity.current.density
    val info = remember(appWidgetId) { host.info(appWidgetId) } ?: return
    var hostViewRef by remember { mutableStateOf<AppWidgetHostView?>(null) }

    AndroidView(
        modifier = modifier.onSizeChanged { size ->
            val view = hostViewRef ?: return@onSizeChanged
            if (size.width > 0 && size.height > 0) {
                val widthDp = size.width / density
                val heightDp = size.height / density
                val minW = info.minWidth.coerceAtLeast(40)
                val minH = info.minHeight.coerceAtLeast(40)
                val targetW = widthDp.toInt().coerceAtLeast(minW)
                val targetH = heightDp.toInt().coerceAtLeast(minH)
                val options = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minH)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, targetW)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, targetH)
                    putParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        arrayListOf(SizeF(targetW.toFloat(), targetH.toFloat()))
                    )
                }
                val manager = AppWidgetManager.getInstance(view.context)
                runCatching {
                    manager.updateAppWidgetOptions(view.appWidgetId, options)
                    view.updateAppWidgetSize(options, minW, minH, targetW, targetH)
                }
            }
        },
        factory = {
            val v = host.createView(appWidgetId) ?: View(context)
            if (v is AppWidgetHostView) {
                hostViewRef = v
                val minW = info.minWidth.coerceAtLeast(80)
                val minH = info.minHeight.coerceAtLeast(80)
                v.updateAppWidgetSize(Bundle(), minW, minH, minW * 2, minH * 2)
            }
            v
        },
        update = { view ->
            if (view is AppWidgetHostView) {
                hostViewRef = view
            }
        }
    )
}
