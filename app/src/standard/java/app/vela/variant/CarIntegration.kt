package app.vela.variant

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import app.vela.ui.map.MapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Normal Vela variant: no launcher service, HOME role or OEM hardware hooks. */
object CarIntegration {
    const val available = false
    val settingsTitle = app.vela.R.string.settings_title
    val settingsSubtitle = app.vela.R.string.settings_title
    val permissionsTitle = app.vela.R.string.settings_title
    val permissionsSubtitle = app.vela.R.string.settings_title
    val statusBarVisible: StateFlow<Boolean> = MutableStateFlow(true)
    val immersive: StateFlow<Boolean> = MutableStateFlow(false)
    fun init(context: Context) = Unit
    fun onResume(activity: ComponentActivity) = Unit
    fun onPause(activity: ComponentActivity) = Unit
    fun onCreated(activity: ComponentActivity) = Unit
    fun onHomeIntent(intent: Intent?) = Unit
    fun onKeyDown(activity: ComponentActivity, keyCode: Int) = false
    fun onMapState(context: Context, state: MapUiState) = Unit
    @Composable fun MapContainer(onOpenSettings: () -> Unit, content: @Composable () -> Unit) = content()
    @Composable fun Settings(onBack: () -> Unit, onPermissions: () -> Unit, onBackup: () -> Unit) = Unit
    @Composable fun Permissions(onBack: () -> Unit) = Unit
    @Composable fun Appearance() = Unit

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
    ): Boolean = false

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
    ): Boolean = false

    fun isCarMode(): Boolean = false

    @Composable
    fun RenderSpeedWidget(
        landscape: Boolean,
        speedMps: Float?,
        limitKmh: Double?,
        imperial: Boolean,
        modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
    ): Boolean = false
}
