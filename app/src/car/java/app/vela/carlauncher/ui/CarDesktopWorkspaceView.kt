package app.vela.carlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private val ClAccent = Color(0xFF0A84FF)

/**
 * UmainLauncher Tabanli Kararli Desktop Modu (CarDesktopWorkspaceView).
 * - Govde dokunmalari %100 ozgurdur (dragViaHandle). Muzik dugmeleri, tiklamalar sifir cakisma ile calisir.
 * - Her widget'in dx, dy ve scale degerleri DesktopWidgetLayoutStore icinde bagimsiz saklanir.
 * - Bir widget boyutlandirildiginda veya tasindiginda diger widget'larin boyutu kesinlikle bozulmaz/sifirlanmaz.
 * - 3. Parti Android AppWidget destegi (AppWidgetHost).
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

    val hostController = remember { CarAppWidgetHostController(context) }
    DisposableEffect(Unit) {
        hostController.startListening()
        onDispose {
            hostController.stopListening()
        }
    }

    var isEditMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }

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

    // AppWidgetHost secici launcher'i
    var pendingAppWidgetId by remember { mutableStateOf(-1) }
    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (id != -1) {
            if (result.resultCode == Activity.RESULT_OK) {
                val widgetKey = "${WidgetIds.AW_PREFIX}$id"
                layoutStore.addWidget(widgetKey, WidgetPlacement(dx = 100f, dy = 100f, scale = 1.0f))
            } else {
                hostController.deleteId(id)
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        val configure = if (id != -1) hostController.info(id)?.configure else null
        when {
            result.resultCode != Activity.RESULT_OK || id == -1 -> {
                if (id != -1) hostController.deleteId(id)
            }
            configure != null -> {
                pendingAppWidgetId = id
                configureLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                )
            }
            else -> {
                val widgetKey = "${WidgetIds.AW_PREFIX}$id"
                layoutStore.addWidget(widgetKey, WidgetPlacement(dx = 100f, dy = 100f, scale = 1.0f))
            }
        }
    }

    CompositionLocalProvider(LocalCarAppWidgetHost provides hostController) {
        Box(
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
            // 0. MASAUSTU DUVAR KAGIDI
            DesktopWallpaperView(modifier = Modifier.fillMaxSize())

            // 1. MASAUSTU SERBEST WIDGET KATMANI (UmainLauncher MovableWidget)
            Box(modifier = Modifier.fillMaxSize()) {
                activeWidgets.forEach { widgetId ->
                    val placement = layout[widgetId]
                        ?: DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS[widgetId]
                        ?: WidgetPlacement()

                    MovableWidget(
                        placement = placement,
                        resizable = true,
                        dragViaHandle = isEditMode,
                        showControls = isEditMode,
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

            // 2. YUZEN KONTROL CUBUGU (Edit / Lock / Add Widget / Reset)
            FloatingDesktopControls(
                isEditMode = isEditMode,
                onToggleEditMode = { isEditMode = !isEditMode },
                onOpenWidgetPicker = { showWidgetPicker = true },
                onOpenWallpaperPicker = { showWallpaperDialog = true },
                onResetLayout = { layoutStore.resetLayout() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .zIndex(200f)
            )

            // 3. WIDGET EKLEME DIYALOGU
            if (showWidgetPicker) {
                AddWidgetDialog(
                    activeWidgets = activeWidgets,
                    onDismiss = { showWidgetPicker = false },
                    onAddWidget = { id ->
                        layoutStore.addWidget(id)
                        showWidgetPicker = false
                    },
                    onLaunchAppWidgetPick = {
                        val id = hostController.allocateId()
                        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        }
                        pickLauncher.launch(pickIntent)
                        showWidgetPicker = false
                    }
                )
            }

            // 4. DUVAR KAGIDI DIYALOGU
            if (showWallpaperDialog) {
                WallpaperDialog(
                    onDismiss = { showWallpaperDialog = false }
                )
            }
        }
    }
}

/**
 * Masaustundeki her widget'in gorsel cizimi.
 */
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
        widgetId == WidgetIds.CLOCK -> {
            ClockWidgetCard(saatMetni = saatMetni, tarihMetni = tarihMetni)
        }
        widgetId == WidgetIds.STATUS -> {
            StatusWidgetView()
        }
        widgetId == WidgetIds.SPEEDOMETER -> {
            SpeedometerWidgetCard(telemetri = telemetri)
        }
        widgetId == WidgetIds.MUSIC -> {
            MusicPlayerWidgetCard(
                medya = medya,
                onOynatDuraklat = onOynatDuraklat,
                onSonraki = onSonraki,
                onOnceki = onOnceki,
                onMuzikPaneliAc = onMuzikPaneliAc
            )
        }
        widgetId == WidgetIds.DOCK -> {
            DockWidgetCard(onLaunchApp = onLaunchApp)
        }
        widgetId.startsWith(WidgetIds.AW_PREFIX) -> {
            val awId = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
            if (awId != null) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(width = 240.dp, height = 140.dp)
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
// WIDGET BILESENLERI
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ClockWidgetCard(saatMetni: String, tarihMetni: String) {
    Surface(
        color = ClCardBg,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
        shadowElevation = 8.dp,
        modifier = Modifier.size(width = 270.dp, height = 100.dp)
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
                    Text(
                        text = "♪",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
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
private fun DockWidgetCard(onLaunchApp: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    // Sık kullanılan araç uygulamaları listesi
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

@Composable
private fun AddWidgetDialog(
    activeWidgets: Set<String>,
    onDismiss: () -> Unit,
    onAddWidget: (String) -> Unit,
    onLaunchAppWidgetPick: () -> Unit
) {
    val items = listOf(
        WidgetIds.MUSIC to "🎵 Müzik Çalar Widget'ı",
        WidgetIds.SPEEDOMETER to "⏱️ Canlı Hız Göstergesi",
        WidgetIds.CLOCK to "🕒 Dijital Saat & Tarih",
        WidgetIds.STATUS to "📊 Sistem / Pil / RAM Durumu",
        WidgetIds.DOCK to "🚀 Uygulama Kısayolları Dock'u"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { (id, title) ->
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
                                Text("Eklendi", color = Color(0xFF30D158), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xFF0072FF),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunchAppWidgetPick() }
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Text("+ Android Uygulama Widget'ı Ekle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
private fun WallpaperDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_desktop_wallpaper", Context.MODE_PRIVATE) }
    val options = listOf(
        "system" to "Sistem Duvar Kağıdı",
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
    val type = remember { prefs.getString("wallpaper_type", "carbon") ?: "carbon" }

    when (type) {
        "system" -> {
            val systemWallpaper = remember {
                try {
                    val wm = android.app.WallpaperManager.getInstance(context)
                    wm.drawable?.toBitmap()?.asImageBitmap()
                } catch (e: Exception) { null }
            }
            if (systemWallpaper != null) {
                Image(
                    bitmap = systemWallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                )
            } else {
                Box(modifier = modifier.background(Color(0xFF0D0F18)))
            }
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
