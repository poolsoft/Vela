package app.vela.ui

import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.vela.variant.CarIntegration

fun applyVelaSystemBars(window: Window, darkIcons: Boolean = false) {
    // Window attributes take effect before DecorView is attached. Relying only on the
    // insets controller queues the status-bar request until the first window focus.
    val immersive = CarIntegration.immersive.value
    val showStatus = !immersive && CarIntegration.statusBarVisible.value
    val fullscreenFlag = android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
    val decor = window.decorView
    if (showStatus || decor.hasWindowFocus()) window.clearFlags(fullscreenFlag)
    else if (!decor.isAttachedToWindow) window.addFlags(fullscreenFlag)
    // Once focus is available the controller owns visibility. Do not retain FLAG_FULLSCREEN:
    // on older Android it would disable the keyboard's adjustResize behaviour.
    val controller = WindowCompat.getInsetsController(window, decor)
    controller.isAppearanceLightStatusBars = darkIcons
    controller.isAppearanceLightNavigationBars = darkIcons
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (immersive) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.navigationBars())
        if (showStatus) controller.show(WindowInsetsCompat.Type.statusBars())
        else controller.hide(WindowInsetsCompat.Type.statusBars())
    }
    androidx.core.view.ViewCompat.requestApplyInsets(window.decorView)
}

@Composable
private fun DialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val status by CarIntegration.statusBarVisible.collectAsState()
    val immersive by CarIntegration.immersive.collectAsState()
    val darkIcons = !app.vela.ui.theme.isAppInDarkTheme()
    DisposableEffect(window, view, status, immersive, darkIcons) {
        val apply = Runnable { applyVelaSystemBars(window, darkIcons) }
        val observer = view.viewTreeObserver
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) view.post(apply)
        }
        observer.addOnWindowFocusChangeListener(focus)
        view.post(apply)
        onDispose {
            view.removeCallbacks(apply)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
        }
    }
}

@Composable
fun VelaWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest, properties) {
        DialogSystemBars()
        content()
    }
}

@Composable
fun VelaWindowAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, icon = icon, title = title, containerColor = containerColor,
        text = { DialogSystemBars(); text?.invoke() },
    )
}
