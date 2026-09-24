package app.vela.carlauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import app.vela.R

object SystemWidgets {
    private var instance: AppWidgetHost? = null
    private var clients = 0
    fun host(context: Context): AppWidgetHost = instance ?: AppWidgetHost(context.applicationContext, 2048).also { instance = it }
    fun acquire(context: Context) {
        if (clients++ == 0) runCatching { host(context).startListening() }
    }
    fun release() { if (clients > 0 && --clients == 0) instance?.stopListening() }
    fun delete(context: Context, id: Int) { runCatching { host(context).deleteAppWidgetId(id) } }
}

@Composable
fun SystemWidgetAddButton(page: Int = 0, onAdded: () -> Unit = {}) {
    val context = LocalContext.current
    var pendingId by rememberSaveable { mutableStateOf(-1) }
    var pendingPage by rememberSaveable { mutableStateOf(0) }
    val manager = remember { AppWidgetManager.getInstance(context) }
    fun cancel() {
        if (pendingId >= 0) SystemWidgets.delete(context, pendingId)
        pendingId = -1
    }
    fun complete() {
        val info = manager.getAppWidgetInfo(pendingId)
        if (pendingId < 0 || info == null) { cancel(); return }
        WidgetManager.getInstance(context).addSystemWidget(pendingId, info.label, pendingPage)
        pendingId = -1
        onAdded()
    }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) complete() else cancel()
    }
    fun configureOrComplete() {
        val info = manager.getAppWidgetInfo(pendingId)
        if (info == null) cancel()
        else if (info.configure == null) complete()
        else runCatching {
            configure.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
        }.onFailure { cancel(); Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show() }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrComplete() else cancel()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) cancel()
        else if (manager.getAppWidgetInfo(pendingId) != null) configureOrComplete()
        else {
            val provider = result.data?.getParcelableExtra<android.content.ComponentName>(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
            if (provider == null) cancel()
            else if (runCatching { manager.bindAppWidgetIdIfAllowed(pendingId, provider) }.getOrDefault(false)) configureOrComplete()
            else runCatching {
                bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider))
            }.onFailure { cancel(); Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show() }
        }
    }
    TextButton(onClick = {
        if (pendingId >= 0) return@TextButton
        pendingPage = page
        pendingId = runCatching { SystemWidgets.host(context).allocateAppWidgetId() }.getOrElse {
            Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show()
            return@TextButton
        }
        runCatching {
            pick.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId))
        }.onFailure { cancel(); Toast.makeText(context, R.string.car_tools_failed, Toast.LENGTH_LONG).show() }
    }) { Text(stringResource(R.string.car_system_widget_add)) }
}

@Composable
fun SystemWidgetView(id: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)
    if (info == null) {
        Text(stringResource(R.string.car_system_widget_missing))
        return
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(id, lifecycle) {
        var listening = false
        fun sync() {
            val active = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
            if (active && !listening) { SystemWidgets.acquire(context); listening = true }
            else if (!active && listening) { SystemWidgets.release(); listening = false }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer)
        sync()
        onDispose {
            lifecycle.removeObserver(observer)
            if (listening) SystemWidgets.release()
        }
    }
    AndroidView(modifier = modifier, factory = {
        SystemWidgets.host(context).createView(it, id, info).apply {
            setAppWidget(id, info)
            addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                if (r - l != oldR - oldL || b - t != oldB - oldT) {
                    val density = resources.displayMetrics.density
                    val width = ((r - l) / density).toInt().coerceAtLeast(1)
                    val height = ((b - t) / density).toInt().coerceAtLeast(1)
                    updateAppWidgetSize(null, width, height, width, height)
                }
            }
        }
    })
}
