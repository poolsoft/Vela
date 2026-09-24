package app.vela.carlauncher.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.widgets.BaseWidget
import app.vela.carlauncher.widgets.WidgetManager
import app.vela.carlauncher.widgets.WidgetRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ClCardBg = Color(0xDD14151E)
private val ClBorder = Color(0x28FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)

/**
 * OsmAnd fragment_widget_panel.xml Uyumlu Dinamik Cok Sayfali Masaustu Calisma Alani (Workspace).
 * - WidgetManager uzerinden dinamik widget ekleme, silme ve boyutlandirma destegi.
 * - Bos alanda "+ Widget Ekle" butonu veya uzun basim ile WidgetPickerDialog.
 * - Widget uzerine uzun basim ile boyut degistirme / silme dialogu.
 * - Cok sayfali yatay kaydirma ve sayfa gostergesi.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarDesktopWorkspaceView(
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onKapat: () -> Unit,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val widgetManager = remember { WidgetManager.getInstance(context) }
    val widgets by widgetManager.widgetsFlow.collectAsState()
    val dockManager = remember { AppDockManager.getInstance(context) }
    val kisayollar by dockManager.kisayollar.collectAsState()
    val scope = rememberCoroutineScope()

    val widgetBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val pageCount = widgetManager.getPageCount()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    var seciliDuzenlemeWidget by remember { mutableStateOf<BaseWidget?>(null) }
    var widgetEklemeDialogAcik by remember { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xAA090A0E), Color(0xAA10121A))
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ═══════════════════════════════════════════════════════════════
            // UST BAR: SAYFA GOSTERGESI, "+ WIDGET EKLE" & KAPAT
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sayfa Noktalari
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (p in 0 until pageCount) {
                        val aktif = pagerState.currentPage == p
                        Box(
                            modifier = Modifier
                                .size(if (aktif) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (aktif) ClPrimary else Color(0x44FFFFFF))
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(p) }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sayfa ${pagerState.currentPage + 1} / $pageCount",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Sag Butonlar: + Widget Ekle ve Kapat
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x220A84FF))
                            .border(1.dp, ClPrimary, RoundedCornerShape(8.dp))
                            .clickable { widgetEklemeDialogAcik = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = ClPrimary, modifier = Modifier.size(16.dp))
                            Text(text = "Widget Ekle", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                            .clickable { onKapat() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SAYFALAR & DINAMIK WIDGET GRID YERLESIMI
            // ═══════════════════════════════════════════════════════════════
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val pageWidgets = widgets.filter { it.pageIndex == pageIndex && it.isVisible }

                if (pageWidgets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { widgetEklemeDialogAcik = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Bu sayfada henüz widget yok", color = Color.Gray, fontSize = 14.sp)
                            Text(text = "Widget eklemek için dokunun", color = ClPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 3
                    ) {
                        pageWidgets.forEach { widget ->
                            // Widget genislik olceklemesi (Boyuta gore)
                            val widgetWidthModifier = when (widget.size) {
                                BaseWidget.WidgetSize.LARGE -> Modifier.fillMaxWidth()
                                BaseWidget.WidgetSize.MEDIUM -> Modifier.fillMaxWidth(0.64f)
                                BaseWidget.WidgetSize.SMALL -> Modifier.fillMaxWidth(0.32f)
                            }

                            Box(
                                modifier = widgetWidthModifier
                                    .onGloballyPositioned { if (draggingId != widget.id) widgetBounds[widget.id] = it.boundsInRoot() }
                                    .graphicsLayer {
                                        translationX = if (draggingId == widget.id) dragOffset.x else 0f
                                        translationY = if (draggingId == widget.id) dragOffset.y else 0f
                                    }
                                    .pointerInput(widget.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingId = widget.id; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                                            onDragCancel = { draggingId = null; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                                            onDragEnd = {
                                                val center = widgetBounds[widget.id]?.center?.plus(dragOffset)
                                                if (center != null) pageWidgets.firstOrNull { other ->
                                                    other.id != widget.id && widgetBounds[other.id]?.contains(center) == true
                                                }?.let { widgetManager.swapWidgets(widget.id, it.id) }
                                                draggingId = null
                                                dragOffset = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            onDrag = { change, amount -> change.consume(); dragOffset += amount }
                                        )
                                    }
                                    .height(if (widget.size == BaseWidget.WidgetSize.LARGE) 175.dp else 125.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ClCardBg)
                                    .border(1.dp, ClBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        seciliDuzenlemeWidget = widget
                                    }
                                    .padding(10.dp)
                            ) {
                                when (widget.typeId) {
                                    WidgetRegistry.TYPE_COMBINED -> {
                                        CombinedWidgetView(saatMetni, tarihMetni, telemetri, medya, onOynatDuraklat)
                                    }
                                    WidgetRegistry.TYPE_SPEED -> {
                                        SpeedWidgetView(telemetri)
                                    }
                                    WidgetRegistry.TYPE_CLOCK -> {
                                        ClockWidgetView(saatMetni, tarihMetni)
                                    }
                                    WidgetRegistry.TYPE_MUSIC -> {
                                        MusicWidgetView(medya, onOynatDuraklat, onOnceki, onSonraki, onMuzikPaneliAc)
                                    }
                                    WidgetRegistry.TYPE_WEATHER -> {
                                        WeatherWidgetView()
                                    }
                                    WidgetRegistry.TYPE_COMPASS -> {
                                        CompassWidgetView(telemetri)
                                    }
                                    WidgetRegistry.TYPE_OBD -> {
                                        ObdWidgetView(telemetri)
                                    }
                                    WidgetRegistry.TYPE_SHORTCUTS -> {
                                        ShortcutsWidgetView(kisayollar, onLaunchApp)
                                    }
                                    else -> {
                                        val systemId = widget.typeId.removePrefix("system:").toIntOrNull()
                                        if (widget.typeId.startsWith("system:") && systemId != null)
                                            app.vela.carlauncher.widgets.SystemWidgetView(systemId, Modifier.fillMaxSize())
                                        else Text(text = widget.title, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // WIDGET KONTROL / DUZENLEME DIALOGU (Boyut & Sil)
        // ═══════════════════════════════════════════════════════════════
        seciliDuzenlemeWidget?.let { widget ->
            AlertDialog(
                onDismissRequest = { seciliDuzenlemeWidget = null },
                title = { Text(text = "${widget.title} Ayarları", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "Widget Boyutu:", color = Color.Gray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    widgetManager.resizeWidget(widget.id, BaseWidget.WidgetSize.SMALL)
                                    seciliDuzenlemeWidget = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (widget.size == BaseWidget.WidgetSize.SMALL) ClPrimary else Color(0x33FFFFFF))
                            ) {
                                Text("Küçük")
                            }
                            Button(
                                onClick = {
                                    widgetManager.resizeWidget(widget.id, BaseWidget.WidgetSize.MEDIUM)
                                    seciliDuzenlemeWidget = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (widget.size == BaseWidget.WidgetSize.MEDIUM) ClPrimary else Color(0x33FFFFFF))
                            ) {
                                Text("Orta")
                            }
                            Button(
                                onClick = {
                                    widgetManager.resizeWidget(widget.id, BaseWidget.WidgetSize.LARGE)
                                    seciliDuzenlemeWidget = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (widget.size == BaseWidget.WidgetSize.LARGE) ClPrimary else Color(0x33FFFFFF))
                            ) {
                                Text("Büyük")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Sayfa Taşı:", color = Color.Gray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (p in 0 until pageCount) {
                                Button(
                                    onClick = {
                                        widgetManager.moveWidgetToPage(widget.id, p)
                                        seciliDuzenlemeWidget = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (widget.pageIndex == p) ClPrimary else Color(0x33FFFFFF))
                                ) {
                                    Text("Sayfa ${p + 1}")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            widgetManager.removeWidget(widget.id)
                            seciliDuzenlemeWidget = null
                        }
                    ) {
                        Text("Widget'ı Sil", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { seciliDuzenlemeWidget = null }) {
                        Text("Kapat", color = Color.White)
                    }
                },
                containerColor = Color(0xFF181924)
            )
        }

        // ═══════════════════════════════════════════════════════════════
        // WIDGET EKLEME KATALOG DIALOGU (WidgetPickerDialog)
        // ═══════════════════════════════════════════════════════════════
        if (widgetEklemeDialogAcik) {
            val available = WidgetRegistry.getAvailableWidgets()
            AlertDialog(
                onDismissRequest = { widgetEklemeDialogAcik = false },
                title = { Text(text = "Masaüstüne Widget Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item { app.vela.carlauncher.widgets.SystemWidgetAddButton(pagerState.currentPage) { widgetEklemeDialogAcik = false } }
                        items(available) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable {
                                        widgetManager.addWidget(entry.typeId, pagerState.currentPage, entry.defaultSize)
                                        widgetEklemeDialogAcik = false
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = entry.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = entry.description, color = Color.Gray, fontSize = 12.sp)
                                }
                                Text(text = "+ Ekle", color = ClPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { widgetEklemeDialogAcik = false }) {
                        Text("İptal", color = Color.White)
                    }
                },
                containerColor = Color(0xFF141520)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// WIDGET ARAYUZ PARCALARI
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CombinedWidgetView(
    saat: String,
    tarih: String,
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynat: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = saat, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(text = tarih, color = Color.Gray, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Text(text = " km/s", color = Color.Gray, fontSize = 14.sp)
            }
            Text(text = if (medya.caliyorMu) "Çalıyor: ${medya.baslik}" else "Vela Dashboard", color = Color(0xFF30D158), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpeedWidgetView(telemetri: HizTelemetrisi) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Text(text = " km/s", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (telemetri.hizSiniriKmh > 0) {
            Text(text = "Limit: ${telemetri.hizSiniriKmh} km/s", color = Color(0xFFFF9500), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ClockWidgetView(saat: String, tarih: String) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = saat, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(text = tarih, color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun MusicWidgetView(
    medya: MedyaParcasi,
    onPlay: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).clickable { onOpen() }) {
            Text(text = if (medya.baslik.isNotBlank()) medya.baslik else "Parça Seçin", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(text = if (medya.sanatci.isNotBlank()) medya.sanatci else "Vela Müzik", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White) }
            IconButton(onClick = onPlay) { Icon(if (medya.caliyorMu) Icons.Default.Close else Icons.Default.PlayArrow, contentDescription = null, tint = ClPrimary) }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White) }
        }
    }
}

@Composable
private fun WeatherWidgetView() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFFFCC00), modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "24°C", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = "Açık", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun CompassWidgetView(telemetri: HizTelemetrisi) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Explore, contentDescription = null, tint = ClPrimary, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${telemetri.pusulaYonu.toInt()}°", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = "Pusula", color = Color.Gray, fontSize = 11.sp)
    }
}

@Composable
private fun ObdWidgetView(telemetri: HizTelemetrisi) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Hız", color = Color.Gray, fontSize = 10.sp)
            Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Ort. Hız", color = Color.Gray, fontSize = 10.sp)
            Text(text = "${telemetri.ortalamaHizKmh} km/s", color = Color(0xFF30D158), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "İrtifa", color = Color.Gray, fontSize = 10.sp)
            Text(text = "${telemetri.irtifaMetre.toInt()}m", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShortcutsWidgetView(kisayollar: List<app.vela.carlauncher.model.AppShortcut>, onLaunchApp: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        kisayollar.take(4).forEach { item ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33FFFFFF))
                    .clickable {
                        onLaunchApp(item.paketAdi)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.ad.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
