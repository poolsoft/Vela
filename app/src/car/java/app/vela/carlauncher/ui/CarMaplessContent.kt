package app.vela.carlauncher.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.telemetry.CarTelemetryManager

/** Reuses existing gauges without composing or binding the map renderer. */
@Composable
fun CarMaplessContent(onSettings: () -> Unit, onOpenMusic: () -> Unit = {}) {
    val context = LocalContext.current
    val telemetry = remember(context) { CarTelemetryManager.getInstance(context) }
    val mediaManager = remember(context) { app.vela.carlauncher.media.CarMediaManager.getInstance(context) }
    val state by telemetry.telemetriDurumu.collectAsStateWithLifecycle()
    val media by mediaManager.medyaDurumu.collectAsStateWithLifecycle()
    val screen by CarLauncherSettings.maplessScreen.collectAsState()
    val maplessMain by CarLauncherSettings.maplessMainScreen.collectAsState()
    val companionQueueEnabled by CarLauncherSettings.companionQueueModu.collectAsState()
    val settings by rememberUpdatedState(onSettings)
    Box(Modifier.fillMaxSize()) {
        when (screen) {
            "dashboard" -> CarDashboardView(state, onKapat = null, Modifier.fillMaxSize())
            "music" -> {
                val host = remember(context) {
                    CarUnifiedPanelHost(
                        context, state, media,
                        onOynatDuraklat = { mediaManager.oynatVeyaDuraklat() },
                        onSonraki = { mediaManager.sonrakiParca() },
                        onOnceki = { mediaManager.oncekiParca() },
                        onMuzikPaneliAc = { onOpenMusic() },
                        onDashboardAc = {},
                        onAyarlarAc = { settings() }
                    )
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { host.rootView },
                    update = {
                        val isCompanionQueue = companionQueueEnabled && maplessMain == "music"
                        host.setCompanionQueueMode(isCompanionQueue)
                        host.updateMedia(media)
                    }
                )
            }
            else -> {
                val host = remember(context) { CarDashboardHost(context, onCloseClick = { settings() }, isMapless = true) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { host.rootView },
                    update = { host.updateTelemetri(state) },
                )
            }
        }
    }
}
