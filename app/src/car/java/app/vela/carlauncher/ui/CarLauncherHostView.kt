package app.vela.carlauncher.ui

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import app.vela.R
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.layout.CarLayoutManager
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.settings.CarLauncherSettings

/** XML panels share the saved screen state owned by CarLauncherLayout. */
@Composable
fun CarLauncherHostView(
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    contentMode: String,
    fullScreenMap: Boolean,
    onPanelChange: (String) -> Unit,
    onToggleMode: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onAyarlarAc: () -> Unit,
    onScanMusic: () -> Unit,
    onVisualizerPermission: () -> Unit,
    onAsistanTiklandi: () -> Unit = {},
    isVoiceListening: Boolean = false,
    voiceAudioLevel: Float = 0f,
    modifier: Modifier = Modifier,
    haritaIcerigi: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var measuredPortrait by remember { mutableStateOf(configuration.orientation == Configuration.ORIENTATION_PORTRAIT) }
    val shortcuts by AppDockManager.getInstance(context).kisayollar.collectAsState()
    val tamEkran by CarLauncherSettings.tamEkranModu.collectAsState()
    val dockKonumu by CarLauncherSettings.dockKonumu.collectAsState()
    val dockBoyutu by CarLauncherSettings.dockBoyutu.collectAsState()
    val panelKonumu by CarLauncherSettings.panelKonumu.collectAsState()
    val panelWidth by CarLauncherSettings.panelGenislikYuzdesi.collectAsState()
    val panelHeight by CarLauncherSettings.panelYukseklikYuzdesi.collectAsState()
    val expansion by CarLauncherSettings.panelGenislemeDavranisi.collectAsState()
    val dimEnabled by CarLauncherSettings.geceKarartmaEtkin.collectAsState()
    val dimLevel by CarLauncherSettings.geceKarartmaSeviyesi.collectAsState()
    val desktop by CarLauncherSettings.desktopModu.collectAsState()
    val mapless by CarLauncherSettings.maplessMode.collectAsState()
    val maplessMain by CarLauncherSettings.maplessMainScreen.collectAsState()
    val visualizerType by CarLauncherSettings.gorsellestiriciTipi.collectAsState()
    val largeVisualizer by CarLauncherSettings.largeVisualizer.collectAsState()
    val visualizerFps by CarLauncherSettings.visualizerFps.collectAsState()
    val latestTelemetry by rememberUpdatedState(telemetri)
    val latestMedya by rememberUpdatedState(medya)
    val latestPlay by rememberUpdatedState(onOynatDuraklat)
    val latestNext by rememberUpdatedState(onSonraki)
    val latestPrevious by rememberUpdatedState(onOnceki)
    val latestVisualizerPermission by rememberUpdatedState(onVisualizerPermission)
    val latestScan by rememberUpdatedState(onScanMusic)
    val latestSettings by rememberUpdatedState(onAyarlarAc)
    val latestPanel by rememberUpdatedState(onPanelChange)
    val latestToggle by rememberUpdatedState(onToggleMode)
    val latestLaunch by rememberUpdatedState(onLaunchApp)
    val latestMap by rememberUpdatedState(haritaIcerigi)
    val latestAssistant by rememberUpdatedState(onAsistanTiklandi)

    var dockReady by remember { mutableStateOf(!app.vela.ui.MemoryPressure.lowRam) }
    var panelReady by remember { mutableStateOf(!app.vela.ui.MemoryPressure.lowRam) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!dockReady) {
            kotlinx.coroutines.delay(500)
            dockReady = true
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: dock stage ready")
            kotlinx.coroutines.delay(1_000)
            panelReady = true
            app.vela.diag.ProcessDiagnostics.checkpoint("launcher: panel stage ready")
        }
    }
    val parentComposition = rememberCompositionContext()
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val root = LayoutInflater.from(ctx).inflate(R.layout.activity_car_launcher, null, false) as ConstraintLayout
            val map = root.findViewById<ExactFrameLayout>(R.id.map_container)
            val panel = root.findViewById<FrameLayout>(R.id.widget_panel)
            val dock = root.findViewById<FrameLayout>(R.id.app_dock)
            panel.clipToOutline = true
            map.clipToOutline = true
            val mapCompose = ComposeView(ctx).apply {
                // Panel reparenting must not dispose the renderer's client and kill its service.
                setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setParentCompositionContext(parentComposition)
                setContent {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) { latestMap() }
                }
            }
            map.addView(mapCompose)
            val manager = CarLayoutManager(
                ctx, root, map, panel, dock,
                root.findViewById<ImageButton>(R.id.widget_handle),
                root.findViewById<FrameLayout>(R.id.app_drawer_container)
            )
            val binding = LauncherHostBinding(manager, panel, mapCompose)
            root.tag = binding
            root.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                if (r - l > 0 && b - t > 0) measuredPortrait = b - t > r - l
                if (r - l != oldR - oldL || b - t != oldB - oldT) binding.applyLayout()
            }
            root
        },
        update = { root ->
            val binding = root.tag as LauncherHostBinding
            val manager = binding.manager
            val panel = binding.panel
            val map = root.findViewById<ExactFrameLayout>(R.id.map_container)
            val dock = root.findViewById<FrameLayout>(R.id.app_dock)
            val portrait = measuredPortrait
            val vertical = !portrait && dockKonumu != "bottom"

            if (dockReady && binding.dockHost?.isVertical != vertical) {
                binding.dockHost?.release()
                dock.removeAllViews()
                binding.dockHost = CarAppDockHost(
                    context = root.context,
                    isVertical = vertical,
                    onAppDrawerClick = { latestPanel("APP_DRAWER") },
                    onDesktopClick = { latestToggle() },
                    onShortcutClick = { latestLaunch(it.paketAdi) },
                    onMiniMusicClick = { latestPanel("MUSIC") },
                    onPlayPauseClick = { latestPlay() },
                    onNextClick = { latestNext() },
                    onAssistantClick = { latestAssistant() },
                    onSettingsClick = { latestSettings() }
                ).also { dock.addView(it.rootView) }
            }
            binding.dockHost?.updateShortcuts(shortcuts)
            if (binding.lastMedia != medya) {
                binding.lastMedia = medya
                binding.dockHost?.updateMedia(medya)
                binding.musicHost?.updateMediaState(medya)
                binding.unifiedHost?.updateMedia(medya)
            }
            binding.dockHost?.updateModeButton(desktop, fullScreenMap)
            binding.dockHost?.setVoiceListening(isVoiceListening, voiceAudioLevel)

            val effectiveMode = if (desktop) "DESKTOP" else if (mapless && contentMode == "UNIFIED") {
                when (maplessMain) { "dashboard" -> "DASHBOARD"; "speedometer" -> "NEON_DASHBOARD"; else -> "MUSIC" }
            } else contentMode
            if (panelReady && binding.contentMode != effectiveMode) {
                binding.releasePanel(keepUnified = effectiveMode == "CLOCK" ||
                    (binding.contentMode == "CLOCK" && effectiveMode == "UNIFIED"))
                binding.contentMode = effectiveMode
                when (effectiveMode) {
                    "DESKTOP" -> {
                        binding.panelCompose = ComposeView(root.context).apply {
                            setParentCompositionContext(parentComposition)
                            setContent {
                                androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                                    app.vela.carlauncher.tools.DesktopAppearance {
                                        CarDesktopWorkspaceView(
                                            telemetri = latestTelemetry,
                                            medya = latestMedya,
                                            onOynatDuraklat = { latestPlay() },
                                            onSonraki = { latestNext() },
                                            onOnceki = { latestPrevious() },
                                            onMuzikPaneliAc = { latestPanel("MUSIC") },
                                            onKapat = null,
                                            onLaunchApp = { latestLaunch(it) }
                                        )
                                    }
                                }
                            }
                        }.also { panel.addView(it) }
                    }
                    "MUSIC" -> {
                        binding.musicHost = CarMusicPlayerHost(
                            root.context, { latestPanel("UNIFIED") },
                            { latestPlay() }, { latestPrevious() }, { latestNext() }, { latestScan() }, { latestVisualizerPermission() }
                        ).also { panel.addView(it.rootView) }
                    }
                    "NEON_DASHBOARD" -> {
                        binding.dashboardHost = CarDashboardHost(root.context) { latestPanel("UNIFIED") }
                            .also { panel.addView(it.rootView) }
                    }
                    "ANTENNA" -> {
                        binding.panelCompose = ComposeView(root.context).apply {
                            setContent {
                                androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                                    androidx.compose.material3.Surface {
                                        app.vela.carlauncher.tools.AntennaPanel { latestPanel("UNIFIED") }
                                    }
                                }
                            }
                        }.also { panel.addView(it) }
                    }
                    "DASHBOARD" -> {
                        binding.panelCompose = ComposeView(root.context).apply {
                            setContent {
                                CarDashboardView(latestTelemetry, { latestPanel("UNIFIED") })
                            }
                        }.also { panel.addView(it) }
                    }
                    "APP_DRAWER" -> {
                        binding.drawerHost = CarAppDrawerHost(
                            root.context, { latestPanel("UNIFIED") }, { latestLaunch(it.paketAdi) }
                        ).also { panel.addView(it.rootView) }
                    }
                    "CLOCK" -> {
                        val clock = binding.unifiedHost ?: CarUnifiedPanelHost(
                            root.context, telemetri, medya,
                            { latestPlay() }, { latestNext() }, { latestPrevious() },
                            { latestPanel("MUSIC") }, { latestPanel("NEON_DASHBOARD") },
                            { latestSettings() }
                        ).also { binding.unifiedHost = it }
                        // Keep the real player alive above the retained map, with no duplicate clock.
                        map.addView(clock.rootView, FrameLayout.LayoutParams(-1, -1))
                        binding.panelCompose = ComposeView(root.context).apply {
                            setParentCompositionContext(parentComposition)
                            setContent {
                                androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                                    ClockCustomizationScreen(onBack = { latestPanel("UNIFIED") })
                                }
                            }
                        }.also { panel.addView(it) }
                    }
                    else -> {
                        val clock = binding.unifiedHost ?: CarUnifiedPanelHost(
                            root.context, telemetri, medya,
                            { latestPlay() }, { latestNext() }, { latestPrevious() },
                            { latestPanel("MUSIC") }, { latestPanel("NEON_DASHBOARD") },
                            { latestSettings() }
                        ).also { binding.unifiedHost = it }
                        panel.addView(clock.rootView, FrameLayout.LayoutParams(-1, -1))
                    }
                }
            }
            binding.setMapContentVisible(effectiveMode != "CLOCK")
            binding.musicHost?.updateVisualizerType(largeVisualizer, visualizerFps)
            if (binding.dashboardHost != null && binding.lastTelemetry != telemetri) {
                binding.lastTelemetry = telemetri
                binding.dashboardHost?.updateTelemetri(telemetri)
            }
            binding.unifiedHost?.updateVisualizerType(visualizerType, visualizerFps)
            map.setInterceptTouch(!mapless && contentMode != "UNIFIED" && contentMode != "CLOCK" && !fullScreenMap) { latestPanel("UNIFIED") }

            root.findViewById<View>(R.id.night_dim_overlay).apply {
                visibility = if (dimEnabled) View.VISIBLE else View.GONE
                alpha = dimLevel
            }
            binding.fullScreenMap = fullScreenMap && !mapless
            manager.clockEditor = contentMode == "CLOCK"
            manager.setDesktopModeState(desktop)
            manager.setContentFullScreen(mapless || contentMode != "UNIFIED")
            val inputs = listOf(
                dockKonumu, dockBoyutu, panelKonumu, panelWidth, panelHeight, expansion,
                desktop, fullScreenMap && !mapless, contentMode, mapless, maplessMain, tamEkran,
                measuredPortrait, configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp
            )
            if (binding.layoutInputs != inputs) {
                binding.layoutInputs = inputs
                binding.applyLayout()
            }
        },
        onRelease = { root ->
            (root.tag as? LauncherHostBinding)?.release()
            root.tag = null
        }
    )
}

private class LauncherHostBinding(
    val manager: CarLayoutManager,
    val panel: FrameLayout,
    private val mapCompose: ComposeView
) {
    var dockHost: CarAppDockHost? = null
    var musicHost: CarMusicPlayerHost? = null
    var dashboardHost: CarDashboardHost? = null
    var unifiedHost: CarUnifiedPanelHost? = null
    var drawerHost: CarAppDrawerHost? = null
    var panelCompose: ComposeView? = null
    var contentMode: String? = null
    var fullScreenMap = false
    var layoutInputs: List<Any>? = null
    private var released = false
    var lastMedia: MedyaParcasi? = null
    var lastTelemetry: HizTelemetrisi? = null

    fun setMapContentVisible(visible: Boolean) {
        mapCompose.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun applyLayout() {
        if (!released) manager.applyLayout(isWidgetPanelOpen = !fullScreenMap)
    }

    fun releasePanel(keepUnified: Boolean = false) {
        musicHost?.release()
        drawerHost?.release()
        unifiedHost?.rootView?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        if (!keepUnified) unifiedHost?.release()
        panelCompose?.disposeComposition()
        musicHost = null
        dashboardHost = null
        if (!keepUnified) unifiedHost = null
        drawerHost = null
        panelCompose = null
        panel.removeAllViews()
    }

    fun release() {
        released = true
        releasePanel()
        dockHost?.release()
        dockHost = null
        mapCompose.disposeComposition()
    }
}
