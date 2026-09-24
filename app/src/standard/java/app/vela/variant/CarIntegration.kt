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
}
