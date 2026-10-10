package app.vela.carlauncher.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import app.vela.ui.VelaWindowAlertDialog as AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vela.MainActivity
import app.vela.R
import app.vela.variant.CarIntegration

private tailrec fun Context.mainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.mainActivity()
    else -> null
}

@Composable
fun LauncherExitControl() {
    val context = LocalContext.current
    val activity = context.mainActivity() ?: return
    var confirm by remember { mutableStateOf(false) }
    var defaultHome by remember { mutableStateOf(CarIntegration.isDefaultHome(context)) }
    val title = stringResource(if (defaultHome) R.string.car_exit_restart else R.string.car_exit_close)
    val detail = stringResource(if (defaultHome) R.string.car_exit_restart_detail else R.string.car_exit_close_detail)
    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
        defaultHome = CarIntegration.isDefaultHome(context)
        confirm = true
    }) { Text(title) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                activity.restartOrCloseFromSettings()
            }) { Text(title) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.car_exit_cancel)) } },
    )
}
