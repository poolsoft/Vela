package app.vela.variant

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    const val available = true
    val settingsTitle = app.vela.R.string.car_settings_hub
    val settingsSubtitle = app.vela.R.string.car_settings_hub_sub
    val permissionsTitle = app.vela.R.string.car_permissions_title
    val permissionsSubtitle = app.vela.R.string.car_permissions_sub
    val statusBarVisible: StateFlow<Boolean> get() = CarLauncherSettings.durumCubuguGoster
    val immersive: StateFlow<Boolean> get() = CarLauncherSettings.tamEkranModu
    fun init(context: Context) = CarLauncherSettings.baslat(context)
    fun onResume(activity: ComponentActivity) =
        CarFloatingButtonManager.getInstance(activity).setAppInForeground(true)
    fun onPause(activity: ComponentActivity) =
        CarFloatingButtonManager.getInstance(activity).setAppInForeground(false)
    fun onCreated(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            CarTelemetryManager.getInstance(activity).telemetriDurumu.collect { tel ->
                CarFloatingButtonManager.getInstance(activity)
                    .updateSpeed(tel.anlikHizKmh.toFloat(), tel.hizSiniriKmh.toFloat())
            }
        }
        CarFloatingButtonManager.getInstance(activity).updateButtonState()
        HeadUnitManager.getInstance(activity)
    }
    fun onHomeIntent(intent: Intent?) {
        if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) CarLauncherSettings.setCarModeEtkin(true)
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
    @Composable fun MapContainer(onOpenSettings: () -> Unit, content: @Composable () -> Unit) {
        val enabled by CarLauncherSettings.carModeEtkin.collectAsState()
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT ||
            (configuration.screenWidthDp < configuration.screenHeightDp)
        CarLauncherLayout(passthrough = !enabled || isPortrait, onOpenSettings = onOpenSettings, haritaIcerigi = content)
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
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = landscape && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!carMode || !isLandscape) return false
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
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ): Boolean {
        val carMode by CarLauncherSettings.carModeEtkin.collectAsState()
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = landscape && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!carMode || !isLandscape) return false
        app.vela.carlauncher.ui.CarCompactEtaBar(
            remainingDistanceMeters = remainingDistanceMeters,
            remainingSeconds = remainingSeconds,
            offRoute = offRoute,
            paused = paused,
            onStop = onStop,
            onPause = onPause,
            onSteps = onSteps,
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
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = landscape && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!carMode || !isLandscape) return false
        app.vela.carlauncher.ui.CarSpeedWidget(
            speedMps = speedMps,
            limitKmh = limitKmh,
            imperial = imperial,
            modifier = modifier
        )
        return true
    }
}
