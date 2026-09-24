package app.vela.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.vela.R

/** One status screen; grants always remain owned by Android. */
@Composable
internal fun LauncherPermissionsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val systemScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        runCatching { systemScreen.launch(intent) }.onFailure {
            android.widget.Toast.makeText(context, R.string.car_tools_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val appUri = Uri.parse("package:${context.packageName}")
    val runtime = buildList {
        add(R.string.car_permission_audio to if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        add(R.string.car_permission_microphone to Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 31) add(R.string.car_permission_bt to Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(R.string.car_permission_notifications to Manifest.permission.POST_NOTIFICATIONS)
    }
    val grants = remember(revision) { runtime.associate { (_, name) -> name to
        (ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED) } }
    val overlay = remember(revision) { Settings.canDrawOverlays(context) }
    val writeSettings = remember(revision) { Settings.System.canWrite(context) }
    val notificationAccess = remember(revision) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    SettingsScaffold(title = stringResource(R.string.car_permissions_title), onBack = onBack) { topRow ->
        Text(stringResource(R.string.car_permissions_sub))
        runtime.forEachIndexed { index, (label, name) ->
            PermissionButton(label, grants[name] == true, if (index == 0) topRow else Modifier) {
                if (grants[name] == true) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                else permission.launch(name)
            }
        }
        PermissionButton(R.string.car_permission_overlay, overlay) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri)) }
        PermissionButton(R.string.car_permission_settings, writeSettings) { open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, appUri)) }
        PermissionButton(R.string.car_permission_listener, notificationAccess) { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 29) {
                val roles = context.getSystemService(RoleManager::class.java)
                if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME))
                    open(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                else open(Intent(Settings.ACTION_HOME_SETTINGS))
            } else open(Intent(Settings.ACTION_HOME_SETTINGS))
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.car_permission_home)) }
        Button(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.car_permission_app_settings))
        }
    }
}

@Composable
private fun PermissionButton(label: Int, granted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(label) + " - " + stringResource(if (granted) R.string.car_permission_granted else R.string.car_permission_required))
    }
}

@Composable
fun SmartFocusPreferences() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_music_focus", Context.MODE_PRIVATE) }
    var follow by remember { mutableStateOf(prefs.getBoolean("auto_follow", true)) }
    var source by remember { mutableStateOf(prefs.getString("startup_source", "last") ?: "last") }
    var seconds by remember { mutableIntStateOf(prefs.getInt("bt_wait_seconds", 8).coerceIn(3, 15)) }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(stringResource(R.string.car_smart_focus))
        Text(stringResource(R.string.car_smart_focus_desc))
        Switch(checked = follow, onCheckedChange = {
            follow = it
            prefs.edit().putBoolean("auto_follow", it).apply()
            if (it) app.vela.carlauncher.media.MusicManager.getInstance(context).allowAutomaticSourceTracking()
        })
        val keys = listOf("last", "internal", "bluetooth", "hcn_radio", "xy_radio")
        val labels = listOf(stringResource(R.string.car_source_last), stringResource(R.string.car_source_internal), "Bluetooth", "HCN Radio", "XYAuto Radio")
        Text(stringResource(R.string.car_startup_source))
        keys.forEachIndexed { index, key ->
            Button(onClick = { source = key; prefs.edit().putString("startup_source", key).apply() }, modifier = Modifier.fillMaxWidth()) {
                Text((if (source == key) "✓ " else "") + labels[index])
            }
        }
        Text(stringResource(R.string.car_bt_wait, seconds))
        androidx.compose.material3.Slider(value = seconds.toFloat(), onValueChange = {
            seconds = it.toInt()
            prefs.edit().putInt("bt_wait_seconds", seconds).apply()
        }, valueRange = 3f..15f, steps = 11)
    }
}
