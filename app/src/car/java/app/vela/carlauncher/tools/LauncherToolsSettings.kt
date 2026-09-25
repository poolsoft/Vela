package app.vela.carlauncher.tools

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Context.launcherActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.launcherActivity()
    else -> null
}

@Composable
fun LauncherToolsSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var choosingApp by remember { mutableStateOf(false) }

    // OsmAnd Tarzi Moduler Yedekleme Durumlari
    var showExportDialog by remember { mutableStateOf(false) }
    var exportSummaries by remember { mutableStateOf<List<LauncherBackup.CategorySummary>>(emptyList()) }
    var selectedExportCategories by remember { mutableStateOf<Set<LauncherBackup.BackupCategory>>(emptySet()) }

    var pendingRestorePackage by remember { mutableStateOf<LauncherBackup.BackupPackage?>(null) }
    var selectedRestoreCategories by remember { mutableStateOf<Set<LauncherBackup.BackupCategory>>(emptySet()) }

    val revision by LauncherStartup.preferencesChanged.collectAsState()
    val selected = remember(revision) { LauncherStartup.selected(context) }

    // Dışa aktarma (Export) ZIP SAF Launcher
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && selectedExportCategories.isNotEmpty()) {
            scope.launch {
                busy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { outStream ->
                            LauncherBackup.exportZip(context, selectedExportCategories, outStream)
                        }
                    }
                }
                busy = false
                showExportDialog = false
                Toast.makeText(
                    context,
                    if (result.isSuccess) R.string.car_backup_success else R.string.car_tools_failed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // İçe aktarma (Import) SAF Launcher (ZIP veya JSON kabul eder)
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)!!.use { inStream ->
                            LauncherBackup.inspectBackup(context, inStream)
                        }
                    }
                }
                busy = false
                result.onSuccess { pkg ->
                    pendingRestorePackage = pkg
                    val allCats = when (pkg) {
                        is LauncherBackup.BackupPackage.ModularZip -> pkg.manifestCategories
                        is LauncherBackup.BackupPackage.LegacyJson -> pkg.summaries.map { it.category }.toSet()
                    }
                    selectedRestoreCategories = allCats
                }.onFailure {
                    Toast.makeText(context, R.string.car_backup_invalid, Toast.LENGTH_LONG).show()
                }
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

        // OsmAnd Tarzı Seçimli Yedek Al Butonu
        TextButton(
            enabled = !busy,
            onClick = {
                val summaries = LauncherBackup.scanDeviceCategories(context)
                exportSummaries = summaries
                selectedExportCategories = summaries.map { it.category }.toSet()
                showExportDialog = true
            }
        ) {
            Text(stringResource(R.string.car_backup_export))
        }

        // OsmAnd Tarzı Seçimli Geri Yükle Butonu
        TextButton(
            enabled = !busy,
            onClick = {
                importFile.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
            }
        ) {
            Text(stringResource(R.string.car_backup_import))
        }

        Text(stringResource(R.string.car_backup_scope), style = MaterialTheme.typography.bodySmall)

        TextButton(onClick = { choosingApp = true }) {
            Text(
                stringResource(R.string.car_startup_app) + ": " + (selected?.let { pkg ->
                    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                } ?: stringResource(R.string.car_none))
            )
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

    // ==========================================
    // DIALOG 1: OsmAnd Tarzı Yedek Alma (Export) Seçim Diyaloğu
    // ==========================================
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.car_backup_select_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // Tümünü Seç / Seçimi Kaldır
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            selectedExportCategories = exportSummaries.map { it.category }.toSet()
                        }) {
                            Text(stringResource(R.string.car_backup_select_all), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            selectedExportCategories = emptySet()
                        }) {
                            Text(stringResource(R.string.car_backup_deselect_all), fontSize = 12.sp)
                        }
                    }

                    // Kategori Listesi
                    exportSummaries.forEach { summary ->
                        val isChecked = summary.category in selectedExportCategories
                        val countText = summary.formatSummary(context)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedExportCategories = if (isChecked) {
                                        selectedExportCategories - summary.category
                                    } else {
                                        selectedExportCategories + summary.category
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedExportCategories = if (checked) {
                                        selectedExportCategories + summary.category
                                    } else {
                                        selectedExportCategories - summary.category
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(summary.category.titleRes),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (countText.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "($countText)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(summary.category.descRes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (selectedExportCategories.isEmpty()) {
                            Toast.makeText(context, R.string.car_backup_no_items_selected, Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                        exportZip.launch("vela-backup-$timeStr.zip")
                    }
                ) {
                    Text(stringResource(R.string.car_backup_export_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // ==========================================
    // DIALOG 2: OsmAnd Tarzı Geri Yükleme (Restore) Seçim Diyaloğu
    // ==========================================
    pendingRestorePackage?.let { pkg ->
        val summaries = when (pkg) {
            is LauncherBackup.BackupPackage.ModularZip -> pkg.summaries
            is LauncherBackup.BackupPackage.LegacyJson -> pkg.summaries
        }

        AlertDialog(
            onDismissRequest = { pendingRestorePackage = null },
            title = { Text(stringResource(R.string.car_backup_restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // Tümünü Seç / Seçimi Kaldır
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            selectedRestoreCategories = summaries.map { it.category }.toSet()
                        }) {
                            Text(stringResource(R.string.car_backup_select_all), fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            selectedRestoreCategories = emptySet()
                        }) {
                            Text(stringResource(R.string.car_backup_deselect_all), fontSize = 12.sp)
                        }
                    }

                    // Bulunan Kategoriler Listesi
                    summaries.forEach { summary ->
                        val isChecked = summary.category in selectedRestoreCategories
                        val countText = summary.formatSummary(context)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedRestoreCategories = if (isChecked) {
                                        selectedRestoreCategories - summary.category
                                    } else {
                                        selectedRestoreCategories + summary.category
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedRestoreCategories = if (checked) {
                                        selectedRestoreCategories + summary.category
                                    } else {
                                        selectedRestoreCategories - summary.category
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(summary.category.titleRes),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (countText.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "($countText)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(summary.category.descRes),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        if (selectedRestoreCategories.isEmpty()) {
                            Toast.makeText(context, R.string.car_backup_no_items_selected, Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        scope.launch {
                            busy = true
                            app.vela.carlauncher.media.MusicManager.getInstance(context).cancelPendingAutomaticPlayback()
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    when (pkg) {
                                        is LauncherBackup.BackupPackage.ModularZip -> {
                                            LauncherBackup.restoreModularZip(context, pkg.zipData, selectedRestoreCategories)
                                        }
                                        is LauncherBackup.BackupPackage.LegacyJson -> {
                                            LauncherBackup.restoreLegacyJson(context, pkg.jsonText, selectedRestoreCategories)
                                        }
                                    }
                                }
                            }
                            if (result.isSuccess) {
                                CarLauncherSettings.baslat(context, force = true)
                                AppDockManager.getInstance(context).yukleKisayollar()
                                WidgetManager.getInstance(context).reload()
                                MusicPlaylistStore.getInstance(context).reload()
                                LauncherStartup.preferencesChanged.value++
                            }
                            busy = false
                            pendingRestorePackage = null
                            Toast.makeText(
                                context,
                                if (result.isSuccess) R.string.car_backup_restore_success else R.string.car_tools_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.car_backup_restore_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestorePackage = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (choosingApp) {
        var apps by remember { mutableStateOf(emptyList<app.vela.carlauncher.model.AracUygulamasi>()) }
        LaunchedEffect(Unit) {
            apps = CarAppManager.getInstance(context).yukluUygulamalariGetir()
                .filter { !it.paketAdi.startsWith("internal://") && it.paketAdi != context.packageName }
        }
        AlertDialog(
            onDismissRequest = { choosingApp = false },
            title = { Text(stringResource(R.string.car_startup_app)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { LauncherStartup.select(context, null); choosingApp = false }) { Text(stringResource(R.string.car_none)) }
                    apps.forEach { app -> TextButton(onClick = { LauncherStartup.select(context, app.paketAdi); choosingApp = false }) { Text(app.ad) } }
                }
            },
            confirmButton = { TextButton(onClick = { choosingApp = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

