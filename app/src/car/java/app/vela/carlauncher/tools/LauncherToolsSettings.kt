package app.vela.carlauncher.tools

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vela.R
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.media.MusicPlaylistStore
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.widgets.SystemWidgetAddButton
import app.vela.carlauncher.widgets.WidgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun Context.launcherActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.launcherActivity()
    else -> null
}

@Composable
fun LauncherToolsSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var choosingApp by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val revision by LauncherStartup.preferencesChanged.collectAsState()
    val selected = remember(revision) { LauncherStartup.selected(context) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(LauncherBackup.export(context).toByteArray(Charsets.UTF_8))
                }
            } }
            busy = false
            Toast.makeText(context, if (result.isSuccess) R.string.car_tools_saved else R.string.car_tools_failed, Toast.LENGTH_LONG).show()
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching { withContext(Dispatchers.IO) {
                LauncherBackup.read(context, uri).also { LauncherBackup.validate(it) }
            } }
            busy = false
            result.onSuccess { pendingImport = it }.onFailure {
                Toast.makeText(context, R.string.car_backup_invalid, Toast.LENGTH_LONG).show()
            }
        }
    }
    val wallpaper = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.getSharedPreferences("vela_launcher_tools", 0).edit().putString("wallpaper_uri", uri.toString()).apply()
            LauncherStartup.preferencesChanged.value++
        }.onFailure { Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show() }
    }
    Column {
        Text(stringResource(R.string.car_launcher_tools), style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = !busy, onClick = { export.launch("vela-launcher.json") }) { Text(stringResource(R.string.car_backup_export)) }
        TextButton(enabled = !busy, onClick = { importFile.launch(arrayOf("application/json", "text/plain")) }) { Text(stringResource(R.string.car_backup_import)) }
        Text(stringResource(R.string.car_backup_scope), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { choosingApp = true }) {
            Text(stringResource(R.string.car_startup_app) + ": " + (selected?.let { pkg ->
                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            } ?: stringResource(R.string.car_none)))
        }
        Text(stringResource(R.string.car_startup_scope), style = MaterialTheme.typography.bodySmall)
        SystemWidgetAddButton()
        TextButton(onClick = { wallpaper.launch(arrayOf("image/*")) }) { Text(stringResource(R.string.car_wallpaper_choose)) }
        TextButton(onClick = {
            context.getSharedPreferences("vela_launcher_tools", 0).edit().remove("wallpaper_uri").apply()
            LauncherStartup.preferencesChanged.value++
        }) { Text(stringResource(R.string.car_wallpaper_clear)) }
        var scale by remember(revision) { mutableStateOf(context.getSharedPreferences("vela_launcher_tools", 0).getFloat("text_scale", 1f).coerceIn(0.8f, 1.4f)) }
        Text(stringResource(R.string.car_desktop_text_scale))
        Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.8f..1.4f, onValueChangeFinished = {
            context.getSharedPreferences("vela_launcher_tools", 0).edit().putFloat("text_scale", scale).apply()
            LauncherStartup.preferencesChanged.value++
        })
        TextButton(onClick = {
            runCatching {
                val activity = context.launcherActivity() ?: error("No activity")
                check(activity.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()))
            }.onFailure { Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show() }
        }) { Text(stringResource(R.string.car_picture_in_picture)) }
    }
    pendingImport?.let { text ->
        AlertDialog(onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.car_backup_import)) },
            text = { Text(stringResource(R.string.car_backup_confirm)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    app.vela.carlauncher.media.MusicManager.getInstance(context).cancelPendingAutomaticPlayback()
                    val result = runCatching { withContext(Dispatchers.IO) { LauncherBackup.restore(context, text) } }
                    if (result.isSuccess) {
                        CarLauncherSettings.baslat(context, force = true)
                        AppDockManager.getInstance(context).yukleKisayollar()
                        WidgetManager.getInstance(context).reload()
                        MusicPlaylistStore.getInstance(context).reload()
                        LauncherStartup.preferencesChanged.value++
                    }
                    busy = false
                    pendingImport = null
                    Toast.makeText(context, if (result.isSuccess) R.string.car_tools_saved else R.string.car_tools_failed, Toast.LENGTH_LONG).show()
                }
            }) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (choosingApp) {
        var apps by remember { mutableStateOf(emptyList<app.vela.carlauncher.model.AracUygulamasi>()) }
        LaunchedEffect(Unit) { apps = CarAppManager.getInstance(context).yukluUygulamalariGetir().filter { !it.paketAdi.startsWith("internal://") && it.paketAdi != context.packageName } }
        AlertDialog(onDismissRequest = { choosingApp = false },
            title = { Text(stringResource(R.string.car_startup_app)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { LauncherStartup.select(context, null); choosingApp = false }) { Text(stringResource(R.string.car_none)) }
                apps.forEach { app -> TextButton(onClick = { LauncherStartup.select(context, app.paketAdi); choosingApp = false }) { Text(app.ad) } }
            } },
            confirmButton = { TextButton(onClick = { choosingApp = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
