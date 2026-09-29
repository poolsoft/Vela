package app.vela.carlauncher.ui

import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.vela.R
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.media.MediaNotificationListener
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.ui.AppLocale
import app.vela.ui.settings.GroupDivider
import app.vela.ui.settings.SettingsGroup
import app.vela.ui.settings.SmartFocusPreferences
import app.vela.ui.settings.ToggleRow
import kotlinx.coroutines.launch

/**
 * Vela Ayarlar Arayuzu ile Tam Uyumlu Modern M3 Car Launcher Ayarlar Ekrani.
 * Yatay ekranlarda hizli erisim icin sol gezinme sutunu + sagda Vela M3 ayar kartlari sunar.
 */
@Composable
fun CarLauncherSettingsView(
    onKapat: () -> Unit,
    onCarModeKapatildi: () -> Unit = {},
    onPermissions: () -> Unit = {},
    onBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var aktifKategori by rememberSaveable { mutableStateOf("GORUNUM") } // GORUNUM, DOCK, MUZIK, SISTEM

    // Reaktif ayar degerleri
    val durumCubugu by CarLauncherSettings.durumCubuguGoster.collectAsState()
    val dockKonumu by CarLauncherSettings.dockKonumu.collectAsState()
    val dockBoyutu by CarLauncherSettings.dockBoyutu.collectAsState()
    val panelKonumu by CarLauncherSettings.panelKonumu.collectAsState()
    val panelGenislikYuzdesi by CarLauncherSettings.panelGenislikYuzdesi.collectAsState()
    val panelHeight by CarLauncherSettings.panelYukseklikYuzdesi.collectAsState()
    val expansion by CarLauncherSettings.panelGenislemeDavranisi.collectAsState()
    val desktopModu by CarLauncherSettings.desktopModu.collectAsState()
    val desktopDongudeEtkin by CarLauncherSettings.desktopDongudeEtkin.collectAsState()
    val baslangicEkrani by CarLauncherSettings.baslangicEkrani.collectAsState()

    val floatingButtonModu by CarLauncherSettings.floatingButtonModu.collectAsState()
    val floatingButtonBoyutu by CarLauncherSettings.floatingButtonBoyutu.collectAsState()
    val geceKarartma by CarLauncherSettings.geceKarartmaEtkin.collectAsState()
    val geceKarartmaSeviyesi by CarLauncherSettings.geceKarartmaSeviyesi.collectAsState()

    val preferredMusic by CarLauncherSettings.tercihEdilenMuzikUygulamasi.collectAsState()
    val otomatikOynat by CarLauncherSettings.otomatikOynat.collectAsState()
    val gorsellestiriciTipi by CarLauncherSettings.gorsellestiriciTipi.collectAsState()
    val largeVisualizer by CarLauncherSettings.largeVisualizer.collectAsState()
    val visualizerFps by CarLauncherSettings.visualizerFps.collectAsState()
    val ambiyansGorsellestirici by CarLauncherSettings.ambiyansGorsellestirici.collectAsState()

    BackHandler(onBack = onKapat)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 680.dp
            val sidebarWidth = if (isCompact) 140.dp else 220.dp

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* alttaki harita ve bilesenlere dokunmatik gecisini engelle */ }
            ) {
                // ═══════════════════════════════════════════════════════════════
                // SOL KOLON: KATEGORI LISTESI & GERI BUTONU
                // ═══════════════════════════════════════════════════════════════
                Surface(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Ust Bar: Geri Butonu ve Baslik
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onKapat, modifier = Modifier.size(38.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.settings_close),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!isCompact) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.car_internal_settings),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Kategori Secim Butonlari
                        KategoriNavButton(
                            title = stringResource(R.string.car_settings_category_appearance),
                            icon = Icons.Default.DisplaySettings,
                            selected = aktifKategori == "GORUNUM",
                            compact = isCompact,
                            onClick = { aktifKategori = "GORUNUM" }
                        )

                        KategoriNavButton(
                            title = stringResource(R.string.car_settings_dock_title),
                            icon = Icons.Default.ViewSidebar,
                            selected = aktifKategori == "DOCK",
                            compact = isCompact,
                            onClick = { aktifKategori = "DOCK" }
                        )

                        KategoriNavButton(
                            title = stringResource(R.string.car_settings_music_title),
                            icon = Icons.Default.MusicNote,
                            selected = aktifKategori == "MUZIK",
                            compact = isCompact,
                            onClick = { aktifKategori = "MUZIK" }
                        )

                        KategoriNavButton(
                            title = stringResource(R.string.car_settings_system_title),
                            icon = Icons.Default.Tune,
                            selected = aktifKategori == "SISTEM",
                            compact = isCompact,
                            onClick = { aktifKategori = "SISTEM" }
                        )

                        Spacer(Modifier.weight(1f))

                        // Arac Modundan Cik Butonu (Altta Zarif Buton)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    CarLauncherSettings.setCarModeEtkin(false)
                                    onCarModeKapatildi()
                                    onKapat()
                                },
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.car_settings_exit_car_mode_btn),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // SAG KOLON: SECILI KATEGORI AYARLARI
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (aktifKategori) {
                        "GORUNUM" -> {
                            SettingsGroup(title = stringResource(R.string.car_settings_category_appearance)) {
                                ToggleRow(
                                    label = stringResource(R.string.car_settings_status_bar),
                                    checked = durumCubugu,
                                    onCheckedChange = { CarLauncherSettings.setDurumCubuguGoster(it) },
                                    hint = stringResource(R.string.car_settings_status_bar_desc)
                                )

                                GroupDivider()

                                ChoicePillRow(
                                    label = stringResource(R.string.car_settings_panel_pos),
                                    hint = stringResource(R.string.car_settings_panel_pos_desc),
                                    options = listOf(
                                        stringResource(R.string.car_settings_panel_left) to (panelKonumu == "left"),
                                        stringResource(R.string.car_settings_panel_right) to (panelKonumu == "right")
                                    ),
                                    onSelect = { index ->
                                        CarLauncherSettings.setPanelKonumu(if (index == 0) "left" else "right")
                                    }
                                )

                                GroupDivider()

                                SliderRow(
                                    label = stringResource(R.string.car_settings_panel_width_ratio),
                                    valueLabel = "%${(panelGenislikYuzdesi * 100).toInt()}",
                                    value = panelGenislikYuzdesi,
                                    valueRange = 0.20f..0.50f,
                                    onValueChange = { CarLauncherSettings.setWidgetPanelWidthPercent(it, persist = true) },
                                    hint = stringResource(R.string.car_settings_panel_width_ratio_desc)
                                )

                                GroupDivider()

                                SliderRow(
                                    label = stringResource(R.string.car_panel_height),
                                    valueLabel = "%${(panelHeight * 100).toInt()}",
                                    value = panelHeight,
                                    valueRange = 0.15f..0.65f,
                                    onValueChange = { CarLauncherSettings.setWidgetPanelHeightPortrait(it, persist = true) }
                                )

                                GroupDivider()

                                ChoicePillRow(
                                    label = stringResource(R.string.car_panel_expansion),
                                    options = listOf(
                                        stringResource(R.string.car_panel_swap) to (expansion == "swap"),
                                        stringResource(R.string.car_panel_overlay) to (expansion == "overlay")
                                    ),
                                    onSelect = { index ->
                                        CarLauncherSettings.setPanelGenislemeDavranisi(if (index == 0) "swap" else "overlay")
                                    }
                                )

                                GroupDivider()

                                ToggleRow(
                                    label = stringResource(R.string.car_settings_desktop_mode),
                                    checked = desktopModu,
                                    onCheckedChange = { CarLauncherSettings.setDesktopModu(it) },
                                    hint = stringResource(R.string.car_settings_desktop_mode_desc)
                                )

                                GroupDivider()

                                ToggleRow(
                                    label = stringResource(R.string.car_settings_desktop_loop),
                                    checked = desktopDongudeEtkin,
                                    onCheckedChange = { CarLauncherSettings.setDesktopDongudeEtkin(it) },
                                    hint = stringResource(R.string.car_settings_desktop_loop_desc)
                                )

                                GroupDivider()

                                ChoicePillRow(
                                    label = stringResource(R.string.car_settings_startup_page),
                                    hint = stringResource(R.string.car_settings_startup_page_desc),
                                    options = listOf(
                                        stringResource(R.string.car_settings_startup_normal) to (baslangicEkrani == "normal"),
                                        stringResource(R.string.car_settings_startup_map_only) to (baslangicEkrani == "map_only"),
                                        stringResource(R.string.car_settings_startup_desktop) to (baslangicEkrani == "desktop")
                                    ),
                                    onSelect = { index ->
                                        val secim = when (index) {
                                            0 -> "normal"
                                            1 -> "map_only"
                                            else -> "desktop"
                                        }
                                        CarLauncherSettings.setBaslangicEkrani(secim)
                                    }
                                )
                            }

                            // Yuzen Buton Grubu
                            SettingsGroup(title = stringResource(R.string.car_settings_floating_btn_title)) {
                                ChoicePillRow(
                                    label = stringResource(R.string.car_settings_floating_btn_mode),
                                    hint = stringResource(R.string.car_settings_floating_btn_mode_desc),
                                    options = listOf(
                                        stringResource(R.string.car_settings_btn_always) to (floatingButtonModu == "always"),
                                        stringResource(R.string.car_settings_btn_background) to (floatingButtonModu == "background_only"),
                                        stringResource(R.string.car_settings_btn_never) to (floatingButtonModu == "never")
                                    ),
                                    onSelect = { index ->
                                        val secim = when (index) {
                                            0 -> "always"
                                            1 -> "background_only"
                                            else -> "never"
                                        }
                                        CarLauncherSettings.setFloatingButtonModu(secim)
                                        CarFloatingButtonManager.getInstance(context).updateButtonState()
                                    }
                                )

                                if (floatingButtonModu != "never") {
                                    GroupDivider()

                                    SliderRow(
                                        label = stringResource(R.string.car_settings_btn_size),
                                        valueLabel = "${floatingButtonBoyutu} dp",
                                        value = floatingButtonBoyutu.toFloat(),
                                        valueRange = 60f..110f,
                                        onValueChange = {
                                            CarLauncherSettings.setFloatingButtonBoyutu(it.toInt())
                                        }
                                    )

                                    val overlayIzniVar = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Settings.canDrawOverlays(context)
                                    } else true

                                    if (!overlayIzniVar) {
                                        GroupDivider()
                                        ActionRow(
                                            label = stringResource(R.string.car_settings_overlay_permission),
                                            hint = stringResource(R.string.car_settings_overlay_permission_desc),
                                            actionText = stringResource(R.string.car_settings_grant_permission),
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = android.content.Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        android.net.Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Gece Filtresi Grubu
                            SettingsGroup(title = stringResource(R.string.car_settings_night_mode)) {
                                ToggleRow(
                                    label = stringResource(R.string.car_settings_night_filter),
                                    checked = geceKarartma,
                                    onCheckedChange = { CarLauncherSettings.setGeceKarartmaEtkin(it) },
                                    hint = stringResource(R.string.car_settings_night_filter_desc)
                                )

                                if (geceKarartma) {
                                    GroupDivider()
                                    SliderRow(
                                        label = stringResource(R.string.car_settings_dimming_level),
                                        valueLabel = "%${(geceKarartmaSeviyesi * 100).toInt()}",
                                        value = geceKarartmaSeviyesi,
                                        valueRange = 0.1f..0.8f,
                                        onValueChange = { CarLauncherSettings.setGeceKarartmaSeviyesi(it) }
                                    )
                                }
                            }
                        }

                        "DOCK" -> {
                            SettingsGroup(title = stringResource(R.string.car_settings_dock_pos_size)) {
                                ChoicePillRow(
                                    label = stringResource(R.string.car_settings_dock_screen_pos),
                                    hint = stringResource(R.string.car_settings_dock_screen_pos_desc),
                                    options = listOf(
                                        stringResource(R.string.car_settings_dock_left) to (dockKonumu == "left"),
                                        stringResource(R.string.car_settings_dock_bottom) to (dockKonumu == "bottom"),
                                        stringResource(R.string.car_settings_dock_right) to (dockKonumu == "right")
                                    ),
                                    onSelect = { index ->
                                        val secim = when (index) {
                                            0 -> "left"
                                            1 -> "bottom"
                                            else -> "right"
                                        }
                                        CarLauncherSettings.setDockKonumu(secim)
                                    }
                                )

                                GroupDivider()

                                SliderRow(
                                    label = stringResource(R.string.car_settings_dock_scale),
                                    valueLabel = "%$dockBoyutu",
                                    value = dockBoyutu.toFloat(),
                                    valueRange = 20f..100f,
                                    onValueChange = { CarLauncherSettings.setDockBoyutu(it.toInt()) },
                                    hint = stringResource(R.string.car_settings_dock_scale_desc)
                                )
                            }
                        }

                        "MUZIK" -> {
                            SmartFocusPreferences()

                            SettingsGroup(title = stringResource(R.string.car_settings_playback_visualizer)) {
                                ActionRow(
                                    label = stringResource(R.string.car_music_preferred),
                                    hint = if (preferredMusic == null) stringResource(R.string.car_music_internal) else
                                        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(preferredMusic!!, 0)).toString() }
                                            .getOrDefault(stringResource(R.string.car_app_launch_failed)),
                                    actionText = stringResource(R.string.car_settings_change_btn),
                                    onClick = {
                                        scope.launch {
                                            val apps = CarAppManager.getInstance(context).yukluUygulamalariGetir().filter { !it.isInternal }
                                            val labels = arrayOf(context.getString(R.string.car_music_internal)) + apps.map { it.ad }
                                            val selected = apps.indexOfFirst { it.paketAdi == preferredMusic } + 1
                                            android.app.AlertDialog.Builder(context)
                                                .setTitle(R.string.car_music_preferred)
                                                .setSingleChoiceItems(labels, selected) { dialog, which ->
                                                    CarLauncherSettings.setTercihEdilenMuzikUygulamasi(if (which == 0) null else apps[which - 1].paketAdi)
                                                    dialog.dismiss()
                                                }.setNegativeButton(android.R.string.cancel, null).show()
                                        }
                                    }
                                )

                                GroupDivider()

                                ToggleRow(
                                    label = stringResource(R.string.car_settings_auto_play),
                                    checked = otomatikOynat,
                                    onCheckedChange = { CarLauncherSettings.setOtomatikOynat(it) },
                                    hint = stringResource(R.string.car_settings_auto_play_desc)
                                )

                                GroupDivider()

                                val modes = context.resources.getStringArray(R.array.car_visualizer_modes)

                                ActionRow(
                                    label = stringResource(R.string.car_visualizer_small),
                                    hint = modes[gorsellestiriciTipi.coerceIn(0, 7)],
                                    actionText = stringResource(R.string.car_settings_change_btn),
                                    onClick = {
                                        android.app.AlertDialog.Builder(context).setTitle(R.string.car_visualizer_small)
                                            .setSingleChoiceItems(modes, gorsellestiriciTipi) { dialog, which ->
                                                CarLauncherSettings.setGorsellestiriciTipi(which); dialog.dismiss()
                                            }.setNegativeButton(android.R.string.cancel, null).show()
                                    }
                                )

                                GroupDivider()

                                ActionRow(
                                    label = stringResource(R.string.car_visualizer_large),
                                    hint = modes[largeVisualizer.coerceIn(0, 7)],
                                    actionText = stringResource(R.string.car_settings_change_btn),
                                    onClick = {
                                        android.app.AlertDialog.Builder(context).setTitle(R.string.car_visualizer_large)
                                            .setSingleChoiceItems(modes, largeVisualizer) { dialog, which ->
                                                CarLauncherSettings.setLargeVisualizer(which); dialog.dismiss()
                                            }.setNegativeButton(android.R.string.cancel, null).show()
                                    }
                                )

                                GroupDivider()

                                ChoicePillRow(
                                    label = stringResource(R.string.car_visualizer_rate),
                                    options = listOf(15, 30, 60).map { fps ->
                                        "$fps fps" to (visualizerFps == fps)
                                    },
                                    onSelect = { index ->
                                        val fpsList = listOf(15, 30, 60)
                                        CarLauncherSettings.setVisualizerFps(fpsList[index])
                                    }
                                )

                                GroupDivider()

                                ToggleRow(
                                    label = stringResource(R.string.car_settings_ambient_visualizer),
                                    checked = ambiyansGorsellestirici,
                                    onCheckedChange = { CarLauncherSettings.setAmbiyansGorsellestirici(it) },
                                    hint = stringResource(R.string.car_settings_ambient_visualizer_desc)
                                )

                                GroupDivider()

                                val bildirimIzniVar = MediaNotificationListener.bildirimIzniVerildiMi(context)
                                ActionRow(
                                    label = stringResource(R.string.car_settings_external_media),
                                    hint = stringResource(R.string.car_settings_external_media_desc),
                                    actionText = if (bildirimIzniVar) stringResource(R.string.car_settings_permission_active) else stringResource(R.string.car_settings_grant_permission),
                                    onClick = {
                                        MediaNotificationListener.bildirimAyarlariniAc(context)
                                    }
                                )

                                GroupDivider()

                                ActionRow(
                                    label = stringResource(R.string.car_settings_dsp_launch),
                                    actionText = stringResource(R.string.car_settings_dsp_open),
                                    onClick = {
                                        CarHardwareManager.dspAc(context)
                                    }
                                )
                            }
                        }

                        "SISTEM" -> {
                            // Baglantili Ekranlar Grubu (Izinler ve Yedekleme)
                            SettingsGroup(title = stringResource(R.string.car_settings_system_title)) {
                                ActionRow(
                                    label = stringResource(R.string.car_permissions_title),
                                    hint = stringResource(R.string.car_permissions_subtitle),
                                    actionText = stringResource(R.string.car_settings_open_btn),
                                    onClick = onPermissions
                                )

                                GroupDivider()

                                ActionRow(
                                    label = stringResource(R.string.backup_title),
                                    hint = stringResource(R.string.backup_hub_sub),
                                    actionText = stringResource(R.string.car_settings_open_btn),
                                    onClick = onBackup
                                )

                                GroupDivider()

                                ActionRow(
                                    label = stringResource(R.string.car_settings_clean_ram),
                                    hint = stringResource(R.string.car_settings_clean_ram_desc),
                                    actionText = stringResource(R.string.car_settings_clean_ram_btn),
                                    onClick = { CarHardwareManager.getInstance(context).cleanRam(context) }
                                )
                            }

                            // Sistem Araclari ve Anten Ayarlari
                            app.vela.carlauncher.tools.LauncherToolsSettings()

                            // Dil Ayarlari Grubu
                            SettingsGroup(title = stringResource(R.string.car_pref_category_language)) {
                                val guncelDil = AppLocale.language.value
                                val sistemDiliniTakipEt = guncelDil.isBlank()

                                ToggleRow(
                                    label = stringResource(R.string.car_settings_language_follow_system),
                                    checked = sistemDiliniTakipEt,
                                    onCheckedChange = { takipEt ->
                                        if (takipEt) {
                                            AppLocale.set(context, "")
                                        } else {
                                            val varsayilan = AppLocale.deviceDefaultSupported()
                                            AppLocale.set(context, varsayilan)
                                        }
                                    },
                                    hint = stringResource(R.string.car_pref_language_desc)
                                )

                                if (!sistemDiliniTakipEt) {
                                    GroupDivider()
                                    val seciliDilAdi = AppLocale.endonym(guncelDil)
                                    ActionRow(
                                        label = stringResource(R.string.car_pref_language_title),
                                        hint = seciliDilAdi,
                                        actionText = stringResource(R.string.car_settings_change_btn),
                                        onClick = {
                                            val diller = AppLocale.SUPPORTED
                                            val etiketler = diller.map { AppLocale.endonym(it) }.toTypedArray()
                                            val seciliIndex = diller.indexOf(guncelDil).coerceAtLeast(0)
                                            android.app.AlertDialog.Builder(context)
                                                .setTitle(R.string.car_pref_language_title)
                                                .setSingleChoiceItems(etiketler, seciliIndex) { dialog, which ->
                                                    AppLocale.set(context, diller[which])
                                                    dialog.dismiss()
                                                }
                                                .setNegativeButton(android.R.string.cancel, null)
                                                .show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sol Gezinme Kolonundaki Kategori Butonu */
@Composable
private fun KategoriNavButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            if (!compact) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }
        }
    }
}

/** Slider Iceren Ayar Satiri */
@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    hint: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

/** Yan yana secim etiketleri (Pill Chip Row) */
@Composable
private fun ChoicePillRow(
    label: String,
    hint: String? = null,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            options.forEachIndexed { index, (title, selected) ->
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

/** Tiklanabilir Aksiyon ve Gecis Satiri */
@Composable
private fun ActionRow(
    label: String,
    hint: String? = null,
    actionText: String? = null,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (actionText != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
