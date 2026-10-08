package app.vela.carlauncher.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import app.vela.R
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.media.CarMediaManager
import app.vela.carlauncher.media.CarMediaService
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.telemetry.CarTelemetryManager

@Composable
fun CarLauncherLayout(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    passthrough: Boolean = false,
    onAsistanTiklandi: () -> Unit = {},
    isVoiceListening: Boolean = false,
    voiceAudioLevel: Float = 0f,
    haritaIcerigi: @Composable () -> Unit
) {
    if (passthrough) {
        Box(modifier.fillMaxSize()) { haritaIcerigi() }
        return
    }
    val context = LocalContext.current
    val visualizerPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, R.string.car_music_visualizer_permission, Toast.LENGTH_LONG).show()
    }
    val requestVisualizer: () -> Unit = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            visualizerPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    val audioPermission = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) app.vela.carlauncher.media.MusicRepository.muzikleriTara(context, zorla = true)
        else Toast.makeText(context, R.string.car_music_permission, Toast.LENGTH_LONG).show()
    }
    val scanMusic: () -> Unit = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            app.vela.carlauncher.media.MusicRepository.muzikleriTara(context, zorla = true)
        else scanLauncher.launch(audioPermission)
    }
    val libraryRepository = remember { app.vela.carlauncher.media.MusicRepository.getInstance(context) }
    val libraryTracks by libraryRepository.parcalar.collectAsState()
    var playbackRestored by remember { mutableStateOf(false) }
    val mediaManager = remember { CarMediaManager.getInstance(context) }
    val telemetry = remember { CarTelemetryManager.getInstance(context) }
    val apps = remember { CarAppManager.getInstance(context) }
    val media by mediaManager.medyaDurumu.collectAsState()
    val telemetryState by telemetry.telemetriDurumu.collectAsStateWithLifecycle()
    val desktop by CarLauncherSettings.desktopModu.collectAsState()
    val autoPlay by CarLauncherSettings.otomatikOynat.collectAsState()
    var contentMode by rememberSaveable { mutableStateOf("UNIFIED") }
    var fullMap by rememberSaveable { mutableStateOf(CarLauncherSettings.getStartupScreen() == "map_only") }
    val homeScreenRequest by CarLauncherSettings.homeScreenRequest.collectAsState()
    LaunchedEffect(homeScreenRequest) {
        CarLauncherSettings.setDesktopModu(CarLauncherSettings.getStartupScreen() == "desktop")
        contentMode = "UNIFIED"
        fullMap = CarLauncherSettings.getStartupScreen() == "map_only"
    }

    LaunchedEffect(Unit) {
        try {
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: media service start begin")
            CarMediaService.baslat(context)
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: media service start complete")
        } catch (error: Exception) {
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: media service start failed")
            app.vela.util.FileLogger.e("CarLauncherLayout", "Media service unavailable", error)
            android.util.Log.w("CarLauncherLayout", "Media service unavailable", error)
        }
    }
    LaunchedEffect(Unit) {
        app.vela.diag.ProcessDiagnostics.checkpoint("launcher: startup tools begin")
        if (!app.vela.carlauncher.tools.LauncherStartup.run(context)) {
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: startup tools failed")
            app.vela.util.FileLogger.e("CarLauncherLayout", "Startup tool launch failed")
            Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show()
        } else {
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: startup tools complete")
        }
    }
    LaunchedEffect(libraryTracks, autoPlay) {
        if (!playbackRestored && libraryTracks.isNotEmpty()) {
            app.vela.diag.ProcessDiagnostics.checkpoint("music playback: restore begin")
            playbackRestored = mediaManager.getMusicManager().restoreSavedPlayback(libraryTracks, autoPlay)
            app.vela.diag.ProcessDiagnostics.checkpoint("music playback: restore complete")
        }
    }

    fun showPanel(mode: String) {
        CarLauncherSettings.setDesktopModu(false)
        contentMode = if (contentMode == mode && !fullMap && mode != "UNIFIED") "UNIFIED" else mode
        fullMap = false
    }
    fun showSplit() {
        CarLauncherSettings.setDesktopModu(false)
        contentMode = "UNIFIED"
        fullMap = false
    }
    fun launchApp(uri: String) {
        val launched = apps.uygulamayiBaslat(uri) { internal ->
            when (internal) {
                InternalApp.SETTINGS -> { showSplit(); onOpenSettings() }
                InternalApp.MUSIC -> { contentMode = "UNIFIED"; showPanel("MUSIC") }
                InternalApp.DASHBOARD -> { contentMode = "UNIFIED"; showPanel("DASHBOARD") }
                InternalApp.NEON_DASHBOARD -> { contentMode = "UNIFIED"; showPanel("NEON_DASHBOARD") }
                InternalApp.ANTENNA -> { contentMode = "UNIFIED"; showPanel("ANTENNA") }
            }
        }
        if (!launched) Toast.makeText(context, R.string.car_app_launch_failed, Toast.LENGTH_SHORT).show()
        else if (!InternalApp.isInternalUri(uri)) showSplit()
    }
    BackHandler(enabled = desktop || fullMap || contentMode != "UNIFIED") { showSplit() }

    Box(modifier.fillMaxSize()) {
        CarLauncherHostView(
            telemetri = telemetryState,
            medya = media,
            contentMode = contentMode,
            fullScreenMap = fullMap,
            isVoiceListening = isVoiceListening,
            voiceAudioLevel = voiceAudioLevel,
            onPanelChange = ::showPanel,
            onToggleMode = {
                when {
                    desktop -> showSplit()
                    fullMap && CarLauncherSettings.isDesktopInModeCycleEnabled() -> {
                        fullMap = false
                        contentMode = "UNIFIED"
                        CarLauncherSettings.setDesktopModu(true)
                    }
                    fullMap -> showSplit()
                    else -> { contentMode = "UNIFIED"; fullMap = true }
                }
            },
            onLaunchApp = ::launchApp,
            onOynatDuraklat = { mediaManager.oynatVeyaDuraklat() },
            onSonraki = { mediaManager.sonrakiParca() },
            onOnceki = { mediaManager.oncekiParca() },
            onScanMusic = scanMusic,
            onVisualizerPermission = requestVisualizer,
            onAyarlarAc = { onOpenSettings() },
            onAsistanTiklandi = onAsistanTiklandi,
            modifier = Modifier.fillMaxSize(),
            haritaIcerigi = haritaIcerigi
        )
    }
}
