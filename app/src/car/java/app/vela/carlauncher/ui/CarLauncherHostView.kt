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
    val visualizerType by CarLauncherSettings.gorsellestiriciTipi.collectAsState()
    val largeVisualizer by CarLauncherSettings.largeVisualizer.collectAsState()
    val visualizerFps by CarLauncherSettings.visualizerFps.collectAsState()
    val latestTelemetry by rememberUpdatedState(telemetri)
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

    val parentComposition = rememberCompositionContext()
    AndroidView(
        modifier = modifier.windowInsetsPadding(
            if (tamEkran) WindowInsets.displayCutout else WindowInsets.safeDrawing
        ),
        factory = { ctx ->
            val root = LayoutInflater.from(ctx).inflate(R.layout.activity_car_launcher, null, false) as ConstraintLayout
            val map = root.findViewById<ExactFrameLayout>(R.id.map_container)
            val panel = root.findViewById<FrameLayout>(R.id.widget_panel)
            val dock = root.findViewById<FrameLayout>(R.id.app_dock)
            panel.clipToOutline = true
            map.clipToOutline = true
            val mapCompose = ComposeView(ctx).apply {
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

            if (binding.dockHost?.isVertical != vertical) {
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
                    onNextClick = { latestNext() }
                ).also { dock.addView(it.rootView) }
            }
            binding.dockHost?.updateShortcuts(shortcuts)
            binding.dockHost?.updateMedia(medya)
            binding.dockHost?.updateModeButton(desktop, fullScreenMap)

            if (binding.contentMode != contentMode) {
                binding.releasePanel()
                binding.contentMode = contentMode
                when (contentMode) {
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
                    else -> {
                        binding.unifiedHost = CarUnifiedPanelHost(
                            root.context, telemetri, medya,
                            { latestPlay() }, { latestNext() }, { latestPrevious() },
                            { latestPanel("MUSIC") }, { latestPanel("NEON_DASHBOARD") },
                            { latestSettings() }
                        ).also { panel.addView(it.rootView) }
                    }
                }
            }
            binding.musicHost?.updateMediaState(medya)
            binding.musicHost?.updateVisualizerType(largeVisualizer, visualizerFps)
            binding.dashboardHost?.updateTelemetri(telemetri)
            binding.unifiedHost?.updateMedia(medya)
            binding.unifiedHost?.updateVisualizerType(visualizerType, visualizerFps)
            map.setInterceptTouch(contentMode != "UNIFIED" && !fullScreenMap) { latestPanel("UNIFIED") }

            root.findViewById<View>(R.id.night_dim_overlay).apply {
                visibility = if (dimEnabled) View.VISIBLE else View.GONE
                alpha = dimLevel
            }
            binding.fullScreenMap = fullScreenMap
            manager.setDesktopModeState(desktop)
            manager.setContentFullScreen(contentMode != "UNIFIED")
            val inputs = listOf(
                dockKonumu, dockBoyutu, panelKonumu, panelWidth, panelHeight, expansion,
                desktop, fullScreenMap, contentMode, tamEkran,
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

    fun applyLayout() {
        if (!released) manager.applyLayout(isWidgetPanelOpen = !fullScreenMap)
    }

    fun releasePanel() {
        musicHost?.release()
        drawerHost?.release()
        unifiedHost?.release()
        panelCompose?.disposeComposition()
        musicHost = null
        dashboardHost = null
        unifiedHost = null
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
