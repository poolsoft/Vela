package app.vela.carlauncher.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import app.vela.ui.settings.ToggleRow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vela Hub-and-Spoke Tasarim Standardinda Arac ve Launcher Ayarlari Ekrani.
 * Vela harita ayarlari (SettingsScreen / SettingsHub) ile birebir ayni mimariye sahiptir.
 * Master-Detail (sol sidebar) kaldirilmis, tek sutun ferah sayfa gecisleri saglanmistir.
 * CoMaps ve OsmAnd launcher ayarlari (Immersive Mod, Widget Kontrolu vb.) eksiksiz dahil edilmistir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve metot isimlerinde).
 */
private enum class CarSettingsSection {
    HUB,
    APPEARANCE,
    DOCK_PANEL,
    WIDGETS,
    MUSIC,
    AUTOLAUNCH
}

@Composable
fun CarLauncherSettingsView(
    onKapat: () -> Unit,
    onCarModeKapatildi: () -> Unit = {},
    onPermissions: () -> Unit = {},
    onBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var section by rememberSaveable { mutableStateOf(CarSettingsSection.HUB) }

    BackHandler {
        if (section == CarSettingsSection.HUB) {
            onKapat()
        } else {
            section = CarSettingsSection.HUB
        }
    }

    when (section) {
        CarSettingsSection.HUB -> CarSettingsHubScreen(
            onOpenSection = { section = it },
            onBack = onKapat,
            modifier = modifier
        )
        CarSettingsSection.APPEARANCE -> CarAppearanceSettingsScreen(
            onBack = { section = CarSettingsSection.HUB },
            modifier = modifier
        )
        CarSettingsSection.DOCK_PANEL -> CarDockPanelSettingsScreen(
            onBack = { section = CarSettingsSection.HUB },
            modifier = modifier
        )
        CarSettingsSection.WIDGETS -> CarWidgetsSettingsScreen(
            onBack = { section = CarSettingsSection.HUB },
            modifier = modifier
        )
        CarSettingsSection.MUSIC -> CarMusicSettingsScreen(
            onBack = { section = CarSettingsSection.HUB },
            modifier = modifier
        )
        CarSettingsSection.AUTOLAUNCH -> CarAutolaunchSettingsScreen(
            onBack = { section = CarSettingsSection.HUB },
            modifier = modifier
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 1. ANA HUB SAYFASI (Vela SettingsHub Mimarisinde)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarSettingsHubScreen(
    onOpenSection: (CarSettingsSection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.car_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HubCategoryRow(
                icon = Icons.Default.DisplaySettings,
                title = "Görünüm ve Ekran",
                description = "Tam ekran (Immersive), durum çubuğu, tema, ekran yönü, yüzen buton",
                onClick = { onOpenSection(CarSettingsSection.APPEARANCE) }
            )

            HubCategoryRow(
                icon = Icons.Default.ViewSidebar,
                title = "Dock ve Panel Düzeni",
                description = "Dock konumu, simge boyutları, yan panel genişliği ve genişleme biçimi",
                onClick = { onOpenSection(CarSettingsSection.DOCK_PANEL) }
            )

            HubCategoryRow(
                icon = Icons.Default.Widgets,
                title = "Widget Yönetimi",
                description = "Hız kadranı, pusula, müzik, saat ve hava durumu widget görünürlükleri",
                onClick = { onOpenSection(CarSettingsSection.WIDGETS) }
            )

            HubCategoryRow(
                icon = Icons.Default.MusicNote,
                title = "Müzik ve Ses",
                description = "Varsayılan müzik uygulaması, görselleştirici tipi, ambiyans ve DSP",
                onClick = { onOpenSection(CarSettingsSection.MUSIC) }
            )

            HubCategoryRow(
                icon = Icons.Default.PlayCircleOutline,
                title = "Otomatik Başlatma",
                description = "Araç veya launcher açıldığında otomatik başlayacak favori uygulamalar",
                onClick = { onOpenSection(CarSettingsSection.AUTOLAUNCH) }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HubCategoryRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. GÖRÜNÜM VE EKRAN (APPEARANCE)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarAppearanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tamEkran by CarLauncherSettings.tamEkranModu.collectAsState()
    val durumCubugu by CarLauncherSettings.durumCubuguGoster.collectAsState()
    val ekranYonu by CarLauncherSettings.ekranYonu.collectAsState()
    val dikeydeSadeceHarita by CarLauncherSettings.dikeydeSadeceHarita.collectAsState()
    val floatingModu by CarLauncherSettings.floatingButtonModu.collectAsState()
    val floatingBoyut by CarLauncherSettings.floatingButtonBoyutu.collectAsState()
    val geceKarartma by CarLauncherSettings.geceKarartmaEtkin.collectAsState()
    val geceKarartmaSeviyesi by CarLauncherSettings.geceKarartmaSeviyesi.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Görünüm ve Ekran") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = "Ekran ve Sistem Barları") {
                ToggleRow(
                    label = "Tam Ekran (Immersive) Modu",
                    hint = "Navigasyon ve durum çubuklarını otomatik gizleyerek tüm ekranı kullanır",
                    checked = tamEkran,
                    onCheckedChange = { CarLauncherSettings.setTamEkranModu(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Durum Çubuğunu (Status Bar) Göster",
                    hint = "Ekranın en üstünde saat ve sistem bildirim simgelerini görünür kılar",
                    checked = durumCubugu,
                    onCheckedChange = { CarLauncherSettings.setDurumCubuguGoster(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Ekran Yönü ve Yerleşim") {
                ChoicePillRow(
                    label = "Ekran Yönlendirmesi",
                    detail = "Aracın baş ünitesine göre varsayılan ekran oryantasyonu",
                    selectedKey = ekranYonu,
                    options = listOf(
                        "landscape" to "Yatay",
                        "portrait" to "Dikey",
                        "sensor" to "Sensör"
                    ),
                    onSelect = { CarLauncherSettings.setEkranYonu(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Dikey Modda Sadece Harita",
                    hint = "Ekran dikey konumdayken widget panelini gizleyip haritayı tam ekran yapar",
                    checked = dikeydeSadeceHarita,
                    onCheckedChange = { CarLauncherSettings.setDikeydeSadeceHarita(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Yüzen Buton (Floating Button)") {
                ChoicePillRow(
                    label = "Yüzen Buton Görünürlüğü",
                    detail = "Haritaya veya ana ekrana hızlı dönüş sağlayan yüzen ikon",
                    selectedKey = floatingModu,
                    options = listOf(
                        "always" to "Her Zaman",
                        "background_only" to "Arka Planda",
                        "never" to "Kapalı"
                    ),
                    onSelect = { CarLauncherSettings.setFloatingButtonModu(it) }
                )
                if (floatingModu != "never") {
                    GroupDivider()
                    SliderRow(
                        label = "Buton Boyutu",
                        detail = "Ekranda duran yüzen butonun piksel ölçeği",
                        value = floatingBoyut.toFloat(),
                        valueRange = 50f..140f,
                        unit = " dp",
                        onValueChange = { CarLauncherSettings.setFloatingButtonBoyutu(it.toInt()) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Gece ve Karartma") {
                ToggleRow(
                    label = "Gece Karartma Modu",
                    hint = "Gece sürüşünde gözü yormamak için ekrana yumuşak karartma katmanı ekler",
                    checked = geceKarartma,
                    onCheckedChange = { CarLauncherSettings.setGeceKarartmaEtkin(it) }
                )
                if (geceKarartma) {
                    GroupDivider()
                    SliderRow(
                        label = "Karartma Yoğunluğu",
                        detail = "Ekranın üzerine binen siyah tonun opaklık seviyesi",
                        value = geceKarartmaSeviyesi * 100f,
                        valueRange = 10f..80f,
                        unit = "%",
                        onValueChange = { CarLauncherSettings.setGeceKarartmaSeviyesi(it / 100f) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. DOCK VE PANEL DÜZENİ (DOCK & PANEL)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarDockPanelSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dockKonumu by CarLauncherSettings.dockKonumu.collectAsState()
    val dockBoyutu by CarLauncherSettings.dockBoyutu.collectAsState()
    val panelKonumu by CarLauncherSettings.panelKonumu.collectAsState()
    val panelGenislik by CarLauncherSettings.panelGenislikYuzdesi.collectAsState()
    val panelYukseklik by CarLauncherSettings.panelYukseklikYuzdesi.collectAsState()
    val genislemeDavranisi by CarLauncherSettings.panelGenislemeDavranisi.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dock ve Panel Düzeni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = "Uygulama Dock Çubuğu") {
                ChoicePillRow(
                    label = "Dock Çubuğu Konumu",
                    detail = "Uygulama kısayollarını barındıran dock panelinin ekran kenarındaki yeri",
                    selectedKey = dockKonumu,
                    options = listOf(
                        "left" to "Sol Kenar",
                        "right" to "Sağ Kenar",
                        "bottom" to "Alt Kenar"
                    ),
                    onSelect = { CarLauncherSettings.setDockKonumu(it) }
                )
                GroupDivider()
                SliderRow(
                    label = "Dock Çubuğu Boyutu",
                    detail = "Dock ikonlarının ve çubuğun genişlik / yükseklik ölçeği",
                    value = dockBoyutu.toFloat(),
                    valueRange = 20f..100f,
                    unit = "%",
                    onValueChange = { CarLauncherSettings.setDockBoyutu(it.toInt()) }
                )
                GroupDivider()
                ActionRow(
                    label = "Dock Kısayollarını Sıfırla",
                    detail = "Dock üzerine sabitlenen özel uygulama kısayollarını varsayılana döndürür",
                    actionText = "Sıfırla",
                    onClick = {
                        scope.launch {
                            app.vela.carlauncher.apps.AppDockManager.getInstance(context).resetDockShortcuts()
                            android.widget.Toast.makeText(context, "Dock kısayolları sıfırlandı", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Yan Panel (Widget & Medya)") {
                ChoicePillRow(
                    label = "Panel Yerleşim Kenarı",
                    detail = "Küçük panelin haritaya göre bulunacağı taraf",
                    selectedKey = panelKonumu,
                    options = listOf(
                        "left" to "Sol Taraf",
                        "right" to "Sağ Taraf"
                    ),
                    onSelect = { CarLauncherSettings.setPanelKonumu(it) }
                )
                GroupDivider()
                SliderRow(
                    label = "Yatay Ekran Panel Genişliği",
                    detail = "Yatay modda panelin ekran genişliğine oranı",
                    value = panelGenislik * 100f,
                    valueRange = 20f..60f,
                    unit = "%",
                    onValueChange = { CarLauncherSettings.setPanelGenislikYuzdesi(it / 100f) }
                )
                GroupDivider()
                SliderRow(
                    label = "Dikey Ekran Panel Yüksekliği",
                    detail = "Dikey modda panelin ekran yüksekliğine oranı",
                    value = panelYukseklik * 100f,
                    valueRange = 20f..60f,
                    unit = "%",
                    onValueChange = { CarLauncherSettings.setPanelYukseklikYuzdesi(it / 100f) }
                )
                GroupDivider()
                ChoicePillRow(
                    label = "Genişleme Davranışı",
                    detail = "Panel büyütüldüğünde haritanın nasıl tepki vereceği",
                    selectedKey = genislemeDavranisi,
                    options = listOf(
                        "swap" to "Haritayı Küçült",
                        "overlay" to "Haritanın Üzerine Bin"
                    ),
                    onSelect = { CarLauncherSettings.setPanelGenislemeDavranisi(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. WIDGET YÖNETİMİ (WIDGETS & WORKSPACE) - CoMaps WidgetSettingsDialog Birebir
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarWidgetsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hizGoster by CarLauncherSettings.widgetHizGoster.collectAsState()
    val pusulaGoster by CarLauncherSettings.widgetPusulaGoster.collectAsState()
    val muzikGoster by CarLauncherSettings.widgetMuzikGoster.collectAsState()
    val saatGoster by CarLauncherSettings.widgetSaatGoster.collectAsState()
    val havaGoster by CarLauncherSettings.widgetHavaDurumuGoster.collectAsState()
    val sistemGoster by CarLauncherSettings.widgetSistemGoster.collectAsState()
    val desktopDongude by CarLauncherSettings.desktopDongudeEtkin.collectAsState()
    val swipeThreshold by CarLauncherSettings.workspaceSwipeThreshold.collectAsState()
    val indicatorSeconds by CarLauncherSettings.workspaceIndicatorSeconds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Widget Yönetimi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = "Aktif Widget'lar") {
                ToggleRow(
                    label = "Hız Göstergesi (Speedometer)",
                    hint = "Anlık araç hızını, birimini ve hız limit uyarılarını gösterir",
                    checked = hizGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetHizGoster(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Pusula ve Yön (Compass)",
                    hint = "Aracın hareket yönünü (N, S, E, W) ve anlık açısını gösterir",
                    checked = pusulaGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetPusulaGoster(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Mini Müzik Çalar",
                    hint = "Müzik parça bilgisi, albüm kapağı ve temel oynatma kontrolleri",
                    checked = muzikGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetMuzikGoster(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Analog / Dijital Saat",
                    hint = "Araca özel şık saat ve tarih görünümü",
                    checked = saatGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetSaatGoster(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Hava Durumu Kartı",
                    hint = "Konumun anlık hava sıcaklığı ve durum ikonunu sunar",
                    checked = havaGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetHavaDurumuGoster(it) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Sistem Uygulama Kısayolları",
                    hint = "Favori uygulamaların ve sistem panellerinin hızlı kısayol kartları",
                    checked = sistemGoster,
                    onCheckedChange = { CarLauncherSettings.setWidgetSistemGoster(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Masaüstü (Desktop) Modu") {
                ToggleRow(
                    label = "Masaüstü Modunu Döngüye Dahil Et",
                    hint = "Dock mod butonuna tıklandığında Harita ile Masaüstü arasında geçiş yapılmasını sağlar",
                    checked = desktopDongude,
                    onCheckedChange = { CarLauncherSettings.setDesktopDongudeEtkin(it) }
                )
                GroupDivider()
                SliderRow(
                    label = "Sayfa Geçiş Hassasiyeti",
                    detail = "Yüksek değer, sayfanın yanlışlıkla değişmesini zorlaştırır",
                    value = swipeThreshold * 100f,
                    valueRange = 20f..60f,
                    unit = "%",
                    onValueChange = { CarLauncherSettings.setWorkspaceSwipeThreshold(it / 100f) }
                )
                GroupDivider()
                SliderRow(
                    label = "Sayfa Noktalarını Göster",
                    detail = "Son dokunuştan sonra göstergenin ekranda kalma süresi",
                    value = indicatorSeconds.toFloat(),
                    valueRange = 1f..5f,
                    unit = " sn",
                    onValueChange = { CarLauncherSettings.setWorkspaceIndicatorSeconds(it.roundToInt()) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. MÜZİK VE SES (MUSIC & AUDIO)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarMusicSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tercihEdilenMuzik by CarLauncherSettings.tercihEdilenMuzikUygulamasi.collectAsState()
    val kucukGorsellestirici by CarLauncherSettings.gorsellestiriciTipi.collectAsState()
    val buyukGorsellestirici by CarLauncherSettings.largeVisualizer.collectAsState()
    val visualizerFps by CarLauncherSettings.visualizerFps.collectAsState()
    val ambiyans by CarLauncherSettings.ambiyansGorsellestirici.collectAsState()
    val otomatikOynat by CarLauncherSettings.otomatikOynat.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Müzik ve Ses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = "Oynatma ve Uygulama") {
                ActionRow(
                    label = "Tercih Edilen Müzik Uygulaması",
                    detail = tercihEdilenMuzik ?: "Dahili Oynatıcı / Sistem Medya",
                    actionText = "Değiştir",
                    onClick = {
                        val i = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                            addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        runCatching { context.startActivity(i) }
                    }
                )
                GroupDivider()
                ToggleRow(
                    label = "Açılışta Otomatik Müzik Çal",
                    hint = "Launcher açıldığında son çalınan parçayı otomatik olarak başlatır",
                    checked = otomatikOynat,
                    onCheckedChange = { CarLauncherSettings.setOtomatikOynat(it) }
                )
                GroupDivider()
                ActionRow(
                    label = "Müzik Kitaplığını Yeniden Tara",
                    detail = "Cihaz ve USB bellekteki yeni MP3/FLAC dosyalarını indeksler",
                    actionText = "Tara",
                    onClick = {
                        android.widget.Toast.makeText(context, "Müzikler taranıyor...", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Görselleştirici (Spectrum / Visualizer)") {
                VisualizerSelectorRow(
                    label = "Küçük Panel Görselleştirici",
                    detail = "Panel küçültülmüş durumdayken çalışan animasyon stili",
                    currentMode = kucukGorsellestirici,
                    onSelect = { CarLauncherSettings.setGorsellestiriciTipi(it) }
                )
                GroupDivider()
                VisualizerSelectorRow(
                    label = "Büyük Panel Görselleştirici",
                    detail = "Genişletilmiş müzik ekranında çalışan görsel efekt",
                    currentMode = buyukGorsellestirici,
                    onSelect = { CarLauncherSettings.setLargeVisualizer(it) }
                )
                GroupDivider()
                ChoicePillRow(
                    label = "Yenileme Hızı (FPS)",
                    detail = "Düşük güçlü teypler için 30 FPS, güçlü üniteler için 60 FPS önerilir",
                    selectedKey = visualizerFps.toString(),
                    options = listOf(
                        "30" to "30 FPS",
                        "60" to "60 FPS"
                    ),
                    onSelect = { CarLauncherSettings.setVisualizerFps(it.toIntOrNull() ?: 30) }
                )
                GroupDivider()
                ToggleRow(
                    label = "Ambiyans Görselleştirici",
                    hint = "Müziğin ritmine göre panel arka planına yumuşak ışık dalgası verir",
                    checked = ambiyans,
                    onCheckedChange = { CarLauncherSettings.setAmbiyansGorsellestirici(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(title = "Ses Geliştirme (DSP)") {
                ActionRow(
                    label = "Ekolayzır / DSP Ayarlarını Aç",
                    detail = "Cihazın dahili ses işlemcisi ve ekolayzır panelini başlatır",
                    actionText = "Aç",
                    onClick = {
                        app.vela.carlauncher.hardware.CarHardwareManager.getInstance(context).openEqualizer(context)
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 6. OTOMATİK BAŞLATMA (AUTOLAUNCH)
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarAutolaunchSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otomatik Başlatma") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SettingsGroup(title = "Başlangıç Uygulamaları") {
                Text(
                    text = "Araç kontağı açıldığında veya launcher başlatıldığında arka planda otomatik olarak tetiklenecek uygulamalar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                GroupDivider()
                ActionRow(
                    label = "1. Başlatma Uygulaması",
                    detail = "Seçilmedi (Örn: Radar Uyarı, TPMS)",
                    actionText = "Seç",
                    onClick = {}
                )
                GroupDivider()
                ActionRow(
                    label = "2. Başlatma Uygulaması",
                    detail = "Seçilmedi",
                    actionText = "Seç",
                    onClick = {}
                )
                GroupDivider()
                ActionRow(
                    label = "3. Başlatma Uygulaması",
                    detail = "Seçilmedi",
                    actionText = "Seç",
                    onClick = {}
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7. GÖRSELLEŞTİRİCİ SEÇİM DİYALOĞU VE BİLEŞENİ
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun VisualizerSelectorRow(
    label: String,
    detail: String? = null,
    currentMode: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val modes = listOf(
        0 to "Klasik Bar Spektrum",
        1 to "Işıltılı Tepe Noktaları",
        2 to "Neon Modern Barlar",
        3 to "Akıcı Bezier Dalgası",
        4 to "Radyal Patlama Spektrumu",
        5 to "Ortadan Simetrik Barlar",
        6 to "Düşen Ritmik Parçacıklar",
        7 to "İç İçe Ritmik Daireler"
    )
    val currentTitle = modes.find { it.first == currentMode }?.second ?: "Mod $currentMode"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                text = currentTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    modes.forEach { (modeId, modeTitle) ->
                        val isSelected = modeId == currentMode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(modeId)
                                    showDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelect(modeId)
                                    showDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = modeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Kapat")
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ORTAK YARDIMCI SATIR BİLEŞENLERİ (Vela Material3 Standartlarında)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChoicePillRow(
    label: String,
    detail: String? = null,
    selectedKey: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (key, optTitle) ->
                val isSelected = key == selectedKey
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(key) },
                    label = { Text(optTitle) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    detail: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String = "",
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${value.toInt()}$unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    detail: String? = null,
    actionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
