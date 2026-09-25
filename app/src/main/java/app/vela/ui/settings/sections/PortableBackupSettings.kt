package app.vela.ui.settings.sections

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vela.R
import app.vela.backup.BackupRestore
import app.vela.backup.PortableBackup
import app.vela.carlauncher.tools.LauncherBackup
import app.vela.ui.dpadHighlight
import app.vela.ui.settings.PageIntro
import app.vela.ui.settings.SettingsScaffold

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun PortableBackupSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val status by PortableBackup.state.collectAsStateWithLifecycle()
    var refreshPendingTrigger by remember { mutableStateOf(0) }
    val pending = remember(refreshPendingTrigger, status.ready) {
        BackupRestore.pending(context) || status.ready
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportCategories by remember { mutableStateOf<List<PortableBackup.CategoryStat>>(emptyList()) }
    var selectedExportCategories by remember { mutableStateOf<Set<PortableBackup.ModularCategory>>(emptySet()) }
    var selectedRestoreCategories by remember { mutableStateOf<Set<PortableBackup.ModularCategory>>(emptySet()) }

    LaunchedEffect(status.review) {
        if (status.review) {
            selectedRestoreCategories = status.availableRestoreCategories.map { it.category }.toSet()
        }
    }

    BackHandler(status.busy || pending || status.review) {
        if (status.review) PortableBackup.dismissReview()
        else onBack()
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && selectedExportCategories.isNotEmpty()) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            PortableBackup.export(context, uri, selectedExportCategories)
        }
    }

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            PortableBackup.inspect(context, uri)
        }
    }

    // ==========================================
    // DIALOG 1: OsmAnd Tarzi Secimli Disa Aktarma (Export)
    // ==========================================
    if (showExportDialog) {
        Dialog(
            onDismissRequest = { showExportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(680.dp)
                    .fillMaxHeight(0.94f)
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.car_backup_select_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedExportCategories = exportCategories.map { it.category }.toSet()
                            }) {
                                Text(stringResource(R.string.car_backup_select_all), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                selectedExportCategories = emptySet()
                            }) {
                                Text(stringResource(R.string.car_backup_deselect_all), fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        exportCategories.forEach { stat ->
                            val isChecked = stat.category in selectedExportCategories
                            val sizeStr = if (stat.fileCount > 0) {
                                "${stat.fileCount} dosya (${LauncherBackup.formatFileSize(stat.totalBytes)})"
                            } else ""

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedExportCategories = if (isChecked) {
                                            selectedExportCategories - stat.category
                                        } else {
                                            selectedExportCategories + stat.category
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedExportCategories = if (checked) {
                                            selectedExportCategories + stat.category
                                        } else {
                                            selectedExportCategories - stat.category
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(stat.category.titleRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (sizeStr.isNotBlank()) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "($sizeStr)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(stat.category.descRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showExportDialog = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedExportCategories.isEmpty()) {
                                    Toast.makeText(context, R.string.car_backup_no_items_selected, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                showExportDialog = false
                                export.launch(null)
                            }
                        ) {
                            Text(stringResource(R.string.car_backup_export_btn))
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // DIALOG 2: OsmAnd Tarzi Secimli Geri Yukleme (Restore)
    // ==========================================
    if (status.review) {
        Dialog(
            onDismissRequest = PortableBackup::dismissReview,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(680.dp)
                    .fillMaxHeight(0.94f)
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.car_backup_restore_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.backup_replace_warning),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedRestoreCategories = status.availableRestoreCategories.map { it.category }.toSet()
                            }) {
                                Text(stringResource(R.string.car_backup_select_all), fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                selectedRestoreCategories = emptySet()
                            }) {
                                Text(stringResource(R.string.car_backup_deselect_all), fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        status.availableRestoreCategories.forEach { stat ->
                            val isChecked = stat.category in selectedRestoreCategories
                            val sizeStr = "${stat.fileCount} dosya (${LauncherBackup.formatFileSize(stat.totalBytes)})"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRestoreCategories = if (isChecked) {
                                            selectedRestoreCategories - stat.category
                                        } else {
                                            selectedRestoreCategories + stat.category
                                        }
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedRestoreCategories = if (checked) {
                                            selectedRestoreCategories + stat.category
                                        } else {
                                            selectedRestoreCategories - stat.category
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(stat.category.titleRes),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "($sizeStr)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = stringResource(stat.category.descRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = PortableBackup::dismissReview) {
                            Text(stringResource(android.R.string.cancel))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedRestoreCategories.isEmpty()) {
                                    Toast.makeText(context, R.string.car_backup_no_items_selected, Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                PortableBackup.restore(context, selectedRestoreCategories)
                            }
                        ) {
                            Text(stringResource(R.string.car_backup_restore_btn))
                        }
                    }
                }
            }
        }
    }

    SettingsScaffold(stringResource(R.string.backup_title), onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PageIntro(stringResource(R.string.backup_intro))
        Button(
            onClick = {
                val stats = PortableBackup.scanDeviceCategories(context)
                exportCategories = stats
                selectedExportCategories = stats.map { it.category }.toSet()
                showExportDialog = true
            },
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_ready),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.backup_ready_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.let { app ->
                                app.findActivity()?.finishAffinity()
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.backup_close_app))
                    }
                    TextButton(
                        onClick = {
                            app.vela.backup.BackupRestore.discard(context)
                            PortableBackup.dismissReview()
                            refreshPendingTrigger++
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.backup_discard))
                    }
                }
            }
        }
        when (BackupRestore.result(context)) {
            "success" -> Text(stringResource(R.string.backup_installed), Modifier.padding(16.dp))
            "rolled_back" -> Text(stringResource(R.string.backup_rolled_back), Modifier.padding(16.dp))
        }
        Text(stringResource(R.string.backup_limit), Modifier.padding(16.dp))
    }
}

