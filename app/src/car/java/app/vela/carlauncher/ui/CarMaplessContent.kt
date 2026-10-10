package app.vela.carlauncher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.telemetry.CarTelemetryManager

/** Reuses existing gauges without composing or binding the map renderer. */
@Composable
fun CarMaplessContent(onSettings: () -> Unit) {
    val context = LocalContext.current
    val telemetry = remember(context) { CarTelemetryManager.getInstance(context) }
    val state by telemetry.telemetriDurumu.collectAsStateWithLifecycle()
    val screen by CarLauncherSettings.maplessScreen.collectAsState()
    val settings by rememberUpdatedState(onSettings)
    when (screen) {
        "dashboard" -> CarDashboardView(state, { settings() }, Modifier.fillMaxSize())
        else -> {
            val host = remember(context) { CarDashboardHost(context) { settings() } }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { host.rootView },
                update = { host.updateTelemetri(state) },
            )
        }
    }
}
