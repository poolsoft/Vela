package app.vela.carlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import app.vela.carlauncher.desktop.CarAppWidgetHostController
import app.vela.carlauncher.desktop.DesktopWidgetLayoutStore
import app.vela.carlauncher.desktop.HostedAppWidgetView
import app.vela.carlauncher.desktop.LocalCarAppWidgetHost
import app.vela.carlauncher.desktop.MovableWidget
import app.vela.carlauncher.desktop.StatusWidgetView
import app.vela.carlauncher.desktop.WidgetIds
import app.vela.carlauncher.desktop.WidgetPlacement
import app.vela.carlauncher.desktop.WorkspaceGrid
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val ClCardBg = Color(0xF0141624)
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF00E5FF)
private val ClSuccess = Color(0xFF30D158)

/**
 * UmainLauncher Tabanli Tam Kapsamli Masaustu (CarDesktopWorkspaceView).
 * - Yalnizca tutamac ile tasima (govde dokunmalari %100 serbest, kazara tasima yok).
 * - Sistem Duvar Kagidi destegi (windowShowWallpaper ve transparan zemin).
 * - Kullanicinin 7-8 araba widget'inin hepsi (Hiz, Saat, Dashboard, Muzik, Hava, Pusula, OBD, Durum, Dock).
 * - Sistem AppWidget secici ("Choose widget" - cihazdaki tum widget'lari gorsel listeleyen secici).
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarDesktopWorkspaceView(
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onKapat: (() -> Unit)? = null,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutStore = remember { DesktopWidgetLayoutStore.getInstance(context) }
    val layout by layoutStore.layout.collectAsState()
    val activeWidgets by layoutStore.activeWidgets.collectAsState()

    // Sistem Duvar Kagidinin arkadan gorunebilmesi icin Activity pencere bayragi
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    val hostController = remember { CarAppWidgetHostController(context) }
    DisposableEffect(Unit) {
        hostController.startListening()
        onDispose {
            hostController.stopListening()
        }
    }

    var isEditMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showSystemWidgetPicker by remember { mutableStateOf(false) }
    var showShortcutPicker by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }

    var saatMetni by remember { mutableStateOf("12:00") }
    var tarihMetni by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val saatFormati = SimpleDateFormat("HH:mm", Locale.getDefault())
        val tarihFormati = SimpleDateFormat("d MMMM EEEE", Locale.getDefault())
        while (true) {
            val simdi = Date()
            saatMetni = saatFormati.format(simdi)
            tarihMetni = tarihFormati.format(simdi)
            delay(1000L)
        }
    }

    // AppWidgetHost yapilandirma launcher'i
    var pendingAppWidgetId by remember { mutableStateOf(-1) }
    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (id != -1) {
            if (result.resultCode == Activity.RESULT_OK) {
                val widgetKey = "${WidgetIds.AW_PREFIX}$id"
                layoutStore.addWidget(widgetKey, WidgetPlacement(dx = 60f, dy = 160f, scale = 1.0f, page = currentPage))
            } else {
                hostController.deleteId(id)
            }
        }
    }
    fun finishWidgetBinding(id: Int) {
        val info = hostController.info(id)
        if (info == null) {
            hostController.deleteId(id)
        } else if (info.configure != null) {
            pendingAppWidgetId = id
            configureLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        } else {
            layoutStore.addWidget("${WidgetIds.AW_PREFIX}$id", WidgetPlacement(60f, 160f, 1f, currentPage))
        }
    }
    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (id >= 0 && result.resultCode == Activity.RESULT_OK) finishWidgetBinding(id)
        else if (id >= 0) hostController.deleteId(id)
    }

    CompositionLocalProvider(LocalCarAppWidgetHost provides hostController) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isEditMode = true
                        }
                    )
                }
        ) {
            val workspaceWidth = maxWidth.value
            val workspaceHeight = maxHeight.value
            val resolvedLayout = WorkspaceGrid.resolve(activeWidgets, layout, workspaceWidth, workspaceHeight)
            val lastPage = resolvedLayout.values.maxOfOrNull { it.page } ?: 0
            LaunchedEffect(lastPage, isEditMode) {
                if (!isEditMode && currentPage > lastPage) currentPage = lastPage
            }
            // 0. MASAUSTU ARKA PLANI (Sistem Duvar Kagidi veya Tema)
            DesktopWallpaperView(modifier = Modifier.fillMaxSize())

            // 1. MASAUSTU WIDGETLARI (UmainLauncher MovableWidget Mimarisi)
            Box(modifier = Modifier.fillMaxSize().pointerInput(currentPage, lastPage, isEditMode) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (!isEditMode && dragDistance < -80f && currentPage < lastPage) currentPage++
                        if (!isEditMode && dragDistance > 80f && currentPage > 0) currentPage--
                        dragDistance = 0f
                    },
                    onHorizontalDrag = { _, amount -> dragDistance += amount }
                )
            }) {
                activeWidgets.filter { resolvedLayout[it]?.page == currentPage }.forEach { widgetId ->
                    val placement = resolvedLayout[widgetId] ?: return@forEach

                    MovableWidget(
                        placement = placement,
                        resizable = true,
                        dragViaHandle = true,
                        showControls = isEditMode,
                        workspaceWidth = workspaceWidth,
                        workspaceHeight = workspaceHeight,
                        onRemove = {
                            if (widgetId.startsWith(WidgetIds.AW_PREFIX)) {
                                val awId = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
                                if (awId != null) hostController.deleteId(awId)
                            }
                            layoutStore.removePlacement(widgetId)
                        },
                        onCommit = { newPlacement ->
                            layoutStore.setPlacement(widgetId, newPlacement)
                        }
                    ) {
                        RenderDesktopWidgetContent(
                            widgetId = widgetId,
                            saatMetni = saatMetni,
                            tarihMetni = tarihMetni,
                            telemetri = telemetri,
                            medya = medya,
                            onOynatDuraklat = onOynatDuraklat,
                            onSonraki = onSonraki,
                            onOnceki = onOnceki,
                            onMuzikPaneliAc = onMuzikPaneliAc,
                            onLaunchApp = onLaunchApp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowBack, contentDescription = "Önceki sayfa",
                    tint = if (currentPage > 0) Color.White else Color.Gray,
                    modifier = Modifier.size(36.dp).clickable(enabled = currentPage > 0) { currentPage-- }
                )
                Text("${currentPage + 1} / ${maxOf(lastPage + 1, currentPage + 1)}", color = Color.White)
                Icon(
                    Icons.Default.ArrowForward, contentDescription = "Sonraki sayfa",
                    tint = if (currentPage < lastPage || (isEditMode && currentPage == lastPage)) Color.White else Color.Gray,
                    modifier = Modifier.size(36.dp).clickable(enabled = currentPage < lastPage || (isEditMode && currentPage == lastPage)) { currentPage++ }
                )
            }

            // 2. YUZEN KONTROL CUBUGU (Edit / Lock / Add / Reset)
            FloatingDesktopControls(
                isEditMode = isEditMode,
                onToggleEditMode = { isEditMode = !isEditMode },
                onOpenWidgetPicker = { showWidgetPicker = true },
                onOpenWallpaperPicker = { showWallpaperDialog = true },
                onResetLayout = { layoutStore.resetLayout(); currentPage = 0 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .zIndex(200f)
            )

            // 3. WIDGET EKLEME DIYALOGU (Kullanicinin 7-8 Widget'i)
            if (showWidgetPicker) {
                AddWidgetDialog(
                    activeWidgets = activeWidgets,
                    onDismiss = { showWidgetPicker = false },
                    onAddWidget = { id ->
                        layoutStore.addWidget(id, (layout[id] ?: DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS[id] ?: WidgetPlacement()).copy(page = currentPage))
                        showWidgetPicker = false
                    },
                    onOpenSystemWidgetPicker = {
                        showWidgetPicker = false
                        showSystemWidgetPicker = true
                    },
                    onOpenShortcutPicker = {
                        showWidgetPicker = false
                        showShortcutPicker = true
                    }
                )
            }

            if (showShortcutPicker) {
                ChooseShortcutDialog(
                    onDismiss = { showShortcutPicker = false },
                    onSelect = { app ->
                        layoutStore.addWidget("${WidgetIds.APP_PREFIX}${app.paketAdi}", WidgetPlacement(page = currentPage))
                        showShortcutPicker = false
                    }
                )
            }

            // 4. SISTEM WIDGET SECICI DIYALOGU ("Choose widget" - 3. Ekran Goruntusu)
            if (showSystemWidgetPicker) {
                ChooseSystemWidgetDialog(
                    onDismiss = { showSystemWidgetPicker = false },
                    onSelectProvider = { providerInfo ->
                        showSystemWidgetPicker = false
                        val appWidgetId = hostController.allocateId()
                        val canBind = runCatching {
                            hostController.manager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
                        }.getOrDefault(false)

                        if (!canBind) {
                            pendingAppWidgetId = appWidgetId
                            bindLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                            })
                        } else {
                            finishWidgetBinding(appWidgetId)
                        }
                    }
                )
            }

            // 5. DUVAR KAGIDI DIYALOGU
            if (showWallpaperDialog) {
                WallpaperDialog(
                    onDismiss = { showWallpaperDialog = false }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WIDGET CERIK RENDERLARI (KULLANICININ 7-8 WIDGET'I)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RenderDesktopWidgetContent(
    widgetId: String,
    saatMetni: String,
    tarihMetni: String,
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    when {
        // 1. DIJITAL SAAT & TARIH
        widgetId == WidgetIds.CLOCK -> {
            ClockWidgetCard(saatMetni = saatMetni, tarihMetni = tarihMetni)
        }
        // 2. SISTEM / CIHAZ DURUMU (PIL, RAM, HAFIZA)
        widgetId == WidgetIds.STATUS -> {
            StatusWidgetView()
        }
        // 3. CANLI HIZ GOSTERGESI
        widgetId == WidgetIds.SPEEDOMETER -> {
            SpeedometerWidgetCard(telemetri = telemetri)
        }
        // 4. MUZIK CALAR KARTI
        widgetId == WidgetIds.MUSIC -> {
            MusicPlayerWidgetCard(
                medya = medya,
                onOynatDuraklat = onOynatDuraklat,
                onSonraki = onSonraki,
                onOnceki = onOnceki,
                onMuzikPaneliAc = onMuzikPaneliAc
            )
        }
        // 5. BIRLESIK DASHBOARD (SAAT + HIZ)
        widgetId == WidgetIds.COMBINED -> {
            CombinedDashboardCard(saatMetni = saatMetni, tarihMetni = tarihMetni, telemetri = telemetri)
        }
        // 6. HAVA DURUMU
        widgetId == WidgetIds.WEATHER -> {
            WeatherWidgetCard()
        }
        // 7. PUSULA & YON
        widgetId == WidgetIds.COMPASS -> {
            CompassWidgetCard(telemetri = telemetri)
        }
        // 8. OBD2 / TELEMETRI VERILERI
        widgetId == WidgetIds.OBD -> {
            ObdWidgetCard()
        }
        // 9. UYGULAMA KISAYOLLARI DOCK'U
        widgetId == WidgetIds.DOCK -> {
            DockWidgetCard(onLaunchApp = onLaunchApp)
        }
        widgetId.startsWith(WidgetIds.APP_PREFIX) -> {
            DesktopAppShortcut(widgetId.removePrefix(WidgetIds.APP_PREFIX), onLaunchApp)
        }
        // 10. ANDROID 3. PARTI APP WIDGETLARI
        widgetId.startsWith(WidgetIds.AW_PREFIX) -> {
            val awId = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
            if (awId != null) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(width = 280.dp, height = 140.dp)
                ) {
                    HostedAppWidgetView(appWidgetId = awId, modifier = Modifier.fillMaxSize())
                }
            }
        }
        else -> {
            Surface(
                color = ClCardBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
                modifier = Modifier.size(160.dp, 100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = widgetId, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WIDGET KART DETAYLARI
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ClockWidgetCard(saatMetni: String, tarihMetni: String) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 260.dp, height = 100.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = saatMetni,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
            Text(
                text = tarihMetni.replaceFirstChar { it.uppercase() },
                color = Color(0xFFB0B3C6),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SpeedometerWidgetCard(telemetri: HizTelemetrisi) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 240.dp, height = 160.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${telemetri.anlikHizKmh}",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "KM / S",
                color = ClPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pusula: ${telemetri.pusulaYonu.roundToInt()}°",
                    color = Color(0xFF8E92A8),
                    fontSize = 11.sp
                )
                Text(
                    text = "İrtifa: ${telemetri.irtifaMetre.roundToInt()}m",
                    color = Color(0xFF8E92A8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CombinedDashboardCard(saatMetni: String, tarihMetni: String, telemetri: HizTelemetrisi) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 340.dp, height = 120.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = saatMetni, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(text = tarihMetni, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text(text = "KM / S", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MusicPlayerWidgetCard(
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit
) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier
            .size(width = 300.dp, height = 160.dp)
            .clickable { onMuzikPaneliAc() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0072FF)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "♪", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medya.baslik.ifBlank { "Müzik Çalınmıyor" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = medya.sanatci.ifBlank { "Vela Audio" },
                        color = Color(0xFFB0B3C6),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp).clickable { onOnceki() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "◀◀", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = ClPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp).clickable { onOynatDuraklat() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (medya.caliyorMu) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Surface(
                    color = Color(0x33FFFFFF),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp).clickable { onSonraki() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "▶▶", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherWidgetCard() {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 170.dp, height = 90.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                drawCircle(color = Color(0xFFFFCC00), radius = size.minDimension / 2.2f)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "22°C", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(text = "Güneşli", color = Color.LightGray, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CompassWidgetCard(telemetri: HizTelemetrisi) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 170.dp, height = 90.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                drawCircle(
                    color = Color(0xFF32ADE6),
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "YÖN: ${telemetri.pusulaYonu.roundToInt()}°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ObdWidgetCard() {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 280.dp, height = 90.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "RPM", color = Color.Gray, fontSize = 10.sp)
                Text(text = "2200", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "HARARET", color = Color.Gray, fontSize = 10.sp)
                Text(text = "90°C", color = ClSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "VOLTAJ", color = Color.Gray, fontSize = 10.sp)
                Text(text = "14.2V", color = ClPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DockWidgetCard(onLaunchApp: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    val dockApps = listOf(
        "com.google.android.apps.maps" to "Harita",
        "net.osmand.plus" to "OsmAnd",
        "com.spotify.music" to "Spotify",
        "com.android.settings" to "Ayarlar"
    )

    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 340.dp, height = 76.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockApps.forEach { (pkg, fallbackLabel) ->
                val icon = remember(pkg) {
                    try { pm.getApplicationIcon(pkg).toBitmap(64, 64).asImageBitmap() } catch (e: Exception) { null }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { onLaunchApp(pkg) },
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = fallbackLabel, modifier = Modifier.size(36.dp))
                    } else {
                        Text(text = fallbackLabel.take(1), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopAppShortcut(packageName: String, onLaunchApp: (String) -> Unit) {
    val context = LocalContext.current
    val label = remember(packageName) {
        InternalApp.fromUri(packageName)?.getAd(context) ?: runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }
    val icon = remember(packageName) {
        runCatching {
            (InternalApp.fromUri(packageName)?.getIkon(context)
                ?: context.packageManager.getApplicationIcon(packageName))
                .toBitmap(64, 64).asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier.size(width = 80.dp, height = 96.dp).clickable { onLaunchApp(packageName) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) Image(icon, contentDescription = label, modifier = Modifier.size(56.dp))
        else Text(label.take(1), color = Color.White, fontSize = 30.sp)
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChooseShortcutDialog(onDismiss: () -> Unit, onSelect: (AracUygulamasi) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AracUygulamasi>>(emptyList()) }
    LaunchedEffect(Unit) { apps = CarAppManager.getInstance(context).yukluUygulamalariGetir() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uygulama seç") },
        text = {
            LazyColumn {
                items(apps) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(app) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = remember(app.paketAdi) { app.ikon?.toBitmap(48, 48)?.asImageBitmap() }
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.ad, color = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// KONTROLLER VE DIYALOGLAR
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingDesktopControls(
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEditMode) {
            FloatingActionButton(
                onClick = onResetLayout,
                containerColor = Color(0xFF2A2D40),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sıfırla", modifier = Modifier.size(18.dp))
            }

            FloatingActionButton(
                onClick = onOpenWallpaperPicker,
                containerColor = Color(0xFF2A2D40),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = "Duvar Kağıdı", modifier = Modifier.size(18.dp))
            }

            FloatingActionButton(
                onClick = onOpenWidgetPicker,
                containerColor = Color(0xFF00E5FF),
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Widget Ekle", modifier = Modifier.size(24.dp))
            }
        }

        FloatingActionButton(
            onClick = onToggleEditMode,
            containerColor = if (isEditMode) Color(0xFF30D158) else Color(0x991E2132),
            contentColor = if (isEditMode) Color.Black else Color.White,
            shape = CircleShape,
            modifier = Modifier.size(46.dp)
        ) {
            Icon(
                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                contentDescription = if (isEditMode) "Kaydet" else "Düzenle",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Kullanicinin tum 7-8 widget'ini eklemesini saglayan ana dialog.
 */
@Composable
private fun AddWidgetDialog(
    activeWidgets: Set<String>,
    onDismiss: () -> Unit,
    onAddWidget: (String) -> Unit,
    onOpenSystemWidgetPicker: () -> Unit,
    onOpenShortcutPicker: () -> Unit
) {
    val items = listOf(
        WidgetIds.COMBINED to "⏱️ Dashboard (Saat + Hız)",
        WidgetIds.SPEEDOMETER to "🏎️ Canlı Hız Göstergesi",
        WidgetIds.CLOCK to "🕒 Dijital Saat & Tarih",
        WidgetIds.MUSIC to "🎵 Müzik Çalar Kartı",
        WidgetIds.WEATHER to "⛅ Hava Durumu",
        WidgetIds.COMPASS to "🧭 Pusula & Yön",
        WidgetIds.OBD to "🚗 OBD2 / Araç Telemetrisi",
        WidgetIds.STATUS to "📊 Sistem / Pil / RAM Durumu",
        WidgetIds.DOCK to "🚀 Uygulama Kısayolları Dock'u"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(color = Color(0xFF0072FF), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenShortcutPicker() }) {
                        Text("Uygulama kısayolu ekle", color = Color.White, modifier = Modifier.padding(14.dp))
                    }
                }
                items(items) { (id, title) ->
                    val isAlreadyActive = id in activeWidgets
                    Surface(
                        color = if (isAlreadyActive) Color(0x22FFFFFF) else Color(0x442A2D40),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAlreadyActive) { onAddWidget(id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isAlreadyActive) Color.Gray else Color.White,
                                fontSize = 14.sp
                            )
                            if (isAlreadyActive) {
                                Text("Masaüstünde", color = Color(0xFF30D158), fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0072FF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSystemWidgetPicker() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📱 Android Sistem Widget'ı Ekle (Choose Widget)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1A1D2E)
    )
}

/**
 * UmainLauncher 3. Ekran Goruntusundeki "Choose widget" Dialogunun Birebir Karsiligi.
 * Cihazdaki tum yuklu Android AppWidget'larini uygulama ikonu ve adiyla listeler.
 */
@Composable
private fun ChooseSystemWidgetDialog(
    onDismiss: () -> Unit,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val providers = remember {
        try {
            appWidgetManager.installedProviders.sortedBy { it.loadLabel(context.packageManager) }
        } catch (e: Exception) {
            emptyList<AppWidgetProviderInfo>()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose widget", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            if (providers.isEmpty()) {
                Text("Cihazda kullanılabilir widget bulunamadı.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(providers) { info ->
                        val pm = context.packageManager
                        val label = remember(info) { info.loadLabel(pm) }
                        val iconBmp = remember(info) {
                            try {
                                val d: Drawable? = info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                                    ?: pm.getApplicationIcon(info.provider.packageName)
                                d?.toBitmap(64, 64)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Surface(
                            color = Color(0x332A2D40),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProvider(info) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (iconBmp != null) {
                                    Image(
                                        bitmap = iconBmp,
                                        contentDescription = label,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.provider.packageName,
                                        color = Color(0xFF8E92A8),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF161824)
    )
}

@Composable
private fun WallpaperDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_desktop_wallpaper", Context.MODE_PRIVATE) }
    val options = listOf(
        "system" to "Sistem Duvar Kağıdı (Canlı)",
        "carbon" to "Karbon Fiber",
        "cockpit" to "Gece Kokpiti (Koyu Mavi)",
        "pure_black" to "Saf Siyah (OLED)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duvar Kağıdı Seçimi", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (type, label) ->
                    Surface(
                        color = Color(0x442A2D40),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.edit().putString("wallpaper_type", type).apply()
                                onDismiss()
                            }
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            Text(text = label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1A1D2E)
    )
}

@Composable
private fun DesktopWallpaperView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_desktop_wallpaper", Context.MODE_PRIVATE) }
    val type = remember { prefs.getString("wallpaper_type", "system") ?: "system" }

    when (type) {
        "system" -> {
            // Pencere transparan oldugu icin arkadaki Spider-Man gibi sistem duvar kagidi dogrudan gorunur!
            Box(modifier = modifier.background(Color.Transparent))
        }
        "pure_black" -> {
            Box(modifier = modifier.background(Color.Black))
        }
        "cockpit" -> {
            Box(
                modifier = modifier.background(
                    Brush.verticalGradient(listOf(Color(0xFF0A0E1A), Color(0xFF020408)))
                )
            )
        }
        else -> {
            // Carbon fiber gradyan
            Box(
                modifier = modifier.background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF181B28), Color(0xFF0A0C14)),
                        radius = 1200f
                    )
                )
            )
        }
    }
}
