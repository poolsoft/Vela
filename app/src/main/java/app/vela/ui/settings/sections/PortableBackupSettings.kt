package app.vela.ui.settings.sections

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.R
import app.vela.backup.BackupRestore
import app.vela.backup.PortableBackup
import app.vela.ui.dpadHighlight
import app.vela.ui.settings.PageIntro
import app.vela.ui.settings.SettingsScaffold

private fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun PortableBackupSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by PortableBackup.state.collectAsStateWithLifecycle()
    val pending = BackupRestore.pending(context) || status.ready
    BackHandler(status.busy || pending || status.review) {
        if (status.review) PortableBackup.dismissReview()
        // Transfer continues through DownloadWork when the user leaves Settings.
        else onBack()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            ) }
            PortableBackup.export(context, uri)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            PortableBackup.inspect(context, uri)
        }
    }
    if (status.review) AlertDialog(
        onDismissRequest = PortableBackup::dismissReview,
        title = { Text(stringResource(R.string.backup_review_title)) },
        text = { Text(stringResource(R.string.backup_replace_warning) + "\n\n" + status.detail) },
        confirmButton = { TextButton(onClick = { PortableBackup.restore(context) }) {
            Text(stringResource(R.string.backup_restore))
        } },
        dismissButton = { TextButton(onClick = PortableBackup::dismissReview) {
            Text(stringResource(R.string.backup_cancel))
        } },
    )
    SettingsScaffold(stringResource(R.string.backup_title), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PageIntro(stringResource(R.string.backup_intro))
        Button(
            onClick = { export.launch(null) },
            enabled = !status.busy && !pending,
            modifier = topRow.fillMaxWidth().padding(horizontal = 16.dp).dpadHighlight(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
        ) { Text(stringResource(R.string.backup_export)) }
        OutlinedButton(
            onClick = { import.launch(null) },
            enabled = !status.busy && !pending,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.backup_import)) }
        if (status.busy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (status.total > 0) status.done.toFloat() / status.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            TextButton(onClick = PortableBackup::cancel) { Text(stringResource(R.string.backup_cancel)) }
        }
        if (status.message != 0) Text(stringResource(status.message), Modifier.padding(16.dp))
        if (status.detail.isNotBlank() && !status.review) Text(status.detail, Modifier.padding(horizontal = 16.dp))
        if (pending) {
            Text(stringResource(R.string.backup_ready_hint), Modifier.padding(16.dp))
            Button(onClick = {
                context.let { app ->
                    app.findActivity()?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.backup_close_app))
            }
            TextButton(onClick = {
                app.vela.backup.BackupRestore.discard(context)
                PortableBackup.dismissReview()
            }) { Text(stringResource(R.string.backup_discard)) }
        }
        when (BackupRestore.result(context)) {
            "success" -> Text(stringResource(R.string.backup_installed), Modifier.padding(16.dp))
            "rolled_back" -> Text(stringResource(R.string.backup_rolled_back), Modifier.padding(16.dp))
        }
        Text(stringResource(R.string.backup_limit), Modifier.padding(16.dp))
    }
}
