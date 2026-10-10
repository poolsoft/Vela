package app.vela.variant

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.vela.carlauncher.hardware.HardwareMediaKeyRouter
import app.vela.carlauncher.hardware.HeadUnitManager
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.telemetry.CarTelemetryManager
import app.vela.carlauncher.ui.CarFloatingButtonManager
import app.vela.carlauncher.ui.CarLauncherLayout
import app.vela.carlauncher.ui.CarLauncherSettingsView
import app.vela.ui.settings.LauncherPermissionsSettings
import app.vela.ui.settings.Hint
import app.vela.ui.settings.SettingsGroup
import app.vela.ui.settings.ToggleRow
import app.vela.ui.map.MapUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/** Variant-owned seam: the map and activity never import launcher implementation types. */
object CarIntegration {
    fun mapStorageAvailable(): Boolean = !CarLauncherSettings.maplessMode.value
    const val available = true
    private val mapRenderLevel = androidx.compose.runtime.mutableIntStateOf(3)
    fun isolatedAutoSurface(context: Context): app.vela.ui.map.SurfaceMapController? = app.vela.carlauncher.map.AutoSurfaceMap(context)
    @Composable fun RenderIsolatedMap(
        scene: app.vela.ui.map.MapRenderScene,
        callbacks: app.vela.ui.map.MapRenderCallbacks,
        dpad: app.vela.ui.map.MapDpadController?,
        modifier: androidx.compose.ui.Modifier,
    ): Boolean {
        val context = androidx.compose.ui.platform.LocalContext.current
        if (!app.vela.util.ProcessIdentity.isMain(context)) return false
        val level = mapRenderLevel.intValue
        val isolatedScene = when (level) {
            1 -> scene.copy(styleUri = "asset://styles/renderer-empty.json", routePolyline = emptyList(),
                markers = emptyList(), ambientPois = emptyList(), savedPins = emptyList(), stopPins = emptyList(),
                routeBubbles = emptyList(), alternates = emptyList(), trafficOn = false, satelliteOn = false,
                transitOn = false, topographyOn = false, buildingOverlays = emptyList(), addressOverlays = emptyList(),
                maxspeedOverlays = emptyList(), placesOverlays = emptyList(), basemapArchive = null)
            2 -> scene.copy(basemapArchive = null, buildingOverlays = emptyList(), addressOverlays = emptyList(),
                maxspeedOverlays = emptyList(), placesOverlays = emptyList())
            else -> scene
        }
        app.vela.carlauncher.map.IsolatedMap(isolatedScene, callbacks, dpad, modifier)
        return true
    }
    fun mapRendererStage(stage: String) = app.vela.carlauncher.map.MapRendererEvents.report(stage)

    val settingsTitle = app.vela.R.string.car_settings_hub
    val settingsSubtitle = app.vela.R.string.car_settings_hub_sub
    val permissionsTitle = app.vela.R.string.car_permissions_title
    val permissionsSubtitle = app.vela.R.string.car_permissions_sub
    val statusBarVisible: StateFlow<Boolean> get() = CarLauncherSettings.durumCubuguGoster
    val immersive: StateFlow<Boolean> get() = CarLauncherSettings.tamEkranModu
    val homeScreenRequest: StateFlow<Long> get() = CarLauncherSettings.homeScreenRequest
    fun init(context: Context) = CarLauncherSettings.baslat(context)
    fun onResume(activity: ComponentActivity) =
        CarFloatingButtonManager.getInstance(activity).setAppInForeground(true)
    fun onPause(activity: ComponentActivity) =
        CarFloatingButtonManager.getInstance(activity).setAppInForeground(false)
    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }
    fun prepareForExit(activity: ComponentActivity) {
        app.vela.carlauncher.diag.DiagnosticService.stop(activity)
        CarFloatingButtonManager.getInstance(activity).setSuspended(true)
        val music = app.vela.carlauncher.media.MusicManager.getInstance(activity)
        music.duraklat()
        music.internalPlayer.serbestBirak()
        activity.stopService(Intent(activity, app.vela.carlauncher.media.CarMediaService::class.java))
        activity.getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(app.vela.carlauncher.media.CarMediaService.NOTIFICATION_ID)
    }
    @Composable fun ExitControl() = app.vela.carlauncher.ui.LauncherExitControl()
    fun onCreated(activity: ComponentActivity) {
        app.vela.carlauncher.diag.DiagnosticService.start(activity)
        activity.lifecycleScope.launch {
            CarLauncherSettings.ekranYonu.collect { orientation ->
                activity.requestedOrientation = when (orientation) {
                    "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "sensor" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                    else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
        }
        CarFloatingButtonManager.getInstance(activity).setSuspended(false)
        activity.lifecycleScope.launch {
            CarTelemetryManager.getInstance(activity).telemetriDurumu.collect { tel ->
                CarFloatingButtonManager.getInstance(activity)
                    .updateSpeed(tel.anlikHizKmh.toFloat(), tel.hizSiniriKmh.toFloat())
            }
        }
        CarFloatingButtonManager.getInstance(activity).updateButtonState()
        HeadUnitManager.getInstance(activity)
        app.vela.diag.ProcessDiagnostics.checkpoint("launcher: hardware ready")
    }
    fun onHomeIntent(intent: Intent?) {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
            CarLauncherSettings.setCarModeEtkin(true)
            CarLauncherSettings.requestHomeScreen()
        }
    }
    fun onKeyDown(activity: ComponentActivity, keyCode: Int): Boolean =
        HardwareMediaKeyRouter.getInstance(activity).route(HardwareMediaKeyRouter.Source.ACTIVITY, keyCode)
    fun onMapState(context: Context, state: MapUiState) {
        val limit = state.speedLimitKmh ?: state.speedLimitOverlayKmh
        CarTelemetryManager.getInstance(context).guncelleGpsVerisi(
            hizMs = state.mySpeed, hizSiniriKmhGelen = limit, irtifaMetre = 0.0,
            pusulaYonu = state.compassHeading ?: state.myBearing ?: 0f,
        )
    }
    @Composable fun MapContainer(
        onOpenSettings: () -> Unit,
        onVoiceClick: () -> Unit = {},
        isVoiceListening: Boolean = false,
        voiceAudioLevel: Float = 0f,
        content: @Composable () -> Unit
    ) {
        // Mapless mode replaces the whole map composition, so no renderer or EGL surface starts.
        val mapless by CarLauncherSettings.maplessMode.collectAsState()
        // Session-only manual map close remains available for diagnosis.
        // Gate the whole MapScreen, not just its visibility, so no hidden EGL surface is created.
        val context = androidx.compose.ui.platform.LocalContext.current
        var testsOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
        val homeRequest by CarLauncherSettings.homeScreenRequest.collectAsState()
        androidx.compose.runtime.LaunchedEffect(homeRequest) { testsOpen = false }
        if (testsOpen) {
            app.vela.carlauncher.diag.DiagnosticTestScreen(
                onClose = { testsOpen = false },
                onSettings = onOpenSettings,
                setMapLevel = { mapRenderLevel.intValue = it },
                mapContent = content,
            )
            return
        }
        var mapOpen by remember { androidx.compose.runtime.mutableStateOf(true) }
        val failureHost = remember { app.vela.carlauncher.map.MapRendererFailureHost() }
        val enabled by CarLauncherSettings.carModeEtkin.collectAsState()
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            (configuration.screenWidthDp < configuration.screenHeightDp)
        CarLauncherLayout(
            passthrough = !enabled || (isPortrait && !mapless),
            onOpenSettings = onOpenSettings,
            onAsistanTiklandi = onVoiceClick,
            isVoiceListening = isVoiceListening,
            voiceAudioLevel = voiceAudioLevel,
            haritaIcerigi = {
                if (mapless) {
                    app.vela.carlauncher.ui.CarMaplessContent(onOpenSettings, onDiagnostics = { testsOpen = true })
                } else if (mapOpen) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            app.vela.carlauncher.map.LocalMapRendererFailureHost provides failureHost,
                        ) { content() }
                        androidx.compose.runtime.LaunchedEffect(failureHost.failure) {
                            failureHost.failure?.let {
                                CarLauncherSettings.disableFailedMap(it.stage)
                                android.widget.Toast.makeText(context, "Harita açılamadı. Müzik ve gösterge ekranına geçildi.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { CarLauncherSettings.setMaplessMode(true) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) { Text(stringResource(app.vela.R.string.car_map_renderer_close)) }
                    }
                } else Surface(modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(app.vela.R.string.car_map_manual_start_detail))
                        OutlinedButton(onClick = { testsOpen = true }) { Text("Adım adım tanı testleri") }
                        Button(onClick = { mapRenderLevel.intValue = 3; mapOpen = true }) { Text(stringResource(app.vela.R.string.car_map_manual_start)) }
                        OutlinedButton(onClick = { mapRenderLevel.intValue = 1; mapOpen = true }) {
                            Text(stringResource(app.vela.R.string.car_map_empty_test))
                        }
                        OutlinedButton(onClick = { mapRenderLevel.intValue = 2; mapOpen = true }) {
                            Text(stringResource(app.vela.R.string.car_map_style_test))
                        }
                        OutlinedButton(onClick = onOpenSettings) { Text(stringResource(app.vela.R.string.car_map_open_settings)) }
                    }
                }
            }
        )
    }
    @Composable fun Settings(onBack: () -> Unit, onPermissions: () -> Unit, onBackup: () -> Unit) {
        CarLauncherSettingsView(
            onKapat = onBack,
            onCarModeKapatildi = { CarLauncherSettings.setCarModeEtkin(false) },
            onPermissions = onPermissions, onBackup = onBackup,
        )
    }
    @Composable fun Permissions(onBack: () -> Unit) = LauncherPermissionsSettings(onBack)
    @Composable fun Appearance() {
        val context = androidx.compose.ui.platform.LocalContext.current
        val enabled by CarLauncherSettings.carModeEtkin.collectAsState()
        SettingsGroup(title = "Araç Modu (Car Launcher)") {
            ToggleRow("Car Launcher Arayüzü", enabled, { CarLauncherSettings.setCarModeEtkin(it) })
        }
        Hint("Sol araç dock çubuğu, anlık dijital hız, hız sınırı tabelası ve müzik kontrol widget'ını etkinleştirir.")
    }

    @Composable
    fun RenderManeuverBanner(
        landscape: Boolean,
        text: String,
        distanceMeters: Double,
        type: app.vela.core.model.ManeuverType,
        roundabout: app.vela.core.model.RoundaboutGeometry? = null,
        nextText: String? = null,
        nextType: app.vela.core.model.ManeuverType? = null,
        nextRoundabout: app.vela.core.model.RoundaboutGeometry? = null,
        nextDistanceMeters: Double? = null,
        offRoute: Boolean = false,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ): Boolean {
        val carMode by CarLauncherSettings.carModeEtkin.collectAsState()
        if (!carMode || !landscape) return false
        app.vela.carlauncher.ui.CarCompactManeuverBanner(
            text = text,
            distanceMeters = distanceMeters,
            type = type,
            roundabout = roundabout,
            nextText = nextText,
            nextType = nextType,
            nextRoundabout = nextRoundabout,
            nextDistanceMeters = nextDistanceMeters,
            offRoute = offRoute,
            modifier = modifier
        )
        return true
    }

    @Composable
    fun RenderNavControls(
        landscape: Boolean,
        remainingDistanceMeters: Double,
        remainingSeconds: Double,
        offRoute: Boolean,
        paused: Boolean = false,
        onStop: () -> Unit,
        onPause: (() -> Unit)? = null,
        onSteps: (() -> Unit)? = null,
        roadName: String? = null,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ): Boolean {
        val carMode by CarLauncherSettings.carModeEtkin.collectAsState()
        if (!carMode || !landscape) return false
        app.vela.carlauncher.ui.CarCompactEtaBar(
            remainingDistanceMeters = remainingDistanceMeters,
            remainingSeconds = remainingSeconds,
            offRoute = offRoute,
            paused = paused,
            onStop = onStop,
            onPause = onPause,
            onSteps = onSteps,
            roadName = roadName,
            modifier = modifier
        )
        return true
    }

    fun isCarMode(): Boolean = CarLauncherSettings.carModeEtkin.value

    @Composable
    fun RenderSpeedWidget(
        landscape: Boolean,
        speedMps: Float?,
        limitKmh: Double?,
        imperial: Boolean,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ): Boolean {
        val carMode by CarLauncherSettings.carModeEtkin.collectAsState()
        if (!carMode || !landscape) return false
        app.vela.carlauncher.ui.CarSpeedWidget(
            speedMps = speedMps,
            limitKmh = limitKmh,
            imperial = imperial,
            modifier = modifier
        )
        return true
    }
}
