package app.vela.carlauncher.ui

import android.appwidget.AppWidgetManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
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

private val ClCardBg = Color(0xF0141624)
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)
private val ClDanger = Color(0xFFFF453A)
private val ClSuccess = Color(0xFF30D158)

/**
 * Launcher 2/3 Mimarili Hucre Tabanli Gelismis Masaustu Alani (Workspace).
 * - 8 Sütun x 4 Satır hücre koordinat sistemi (cellX, cellY, spanX, spanY).
 * - Uzun basma ile "Duzenleme Modu" (Edit Mode), iOS/Launcher3 tarzi titreme animasyonu.
 * - 4 kenarda boyutlandirma tutamaçlari (Resize handles) ve silme/ayar butonlari.
 * - Surukle-birak sirasinda hedef hucre maskesi ve kilavuz cizgileri.
 * - Sistem widget'lari (AppWidgetHostView) ve dinamik kisayollar.
 * - Ekran goruntusundeki Widget Kutuphanesi entegrasyonu.
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
    val widgetManager = remember { WidgetManager.getInstance(context) }
    val widgets by widgetManager.widgetsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    val pageCount = widgetManager.getPageCount()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    var isEditMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }

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
                    colors = listOf(Color(0xFF090A0F), Color(0xFF10121C), Color(0xFF131524))
                )
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { isEditMode = true },
                    onTap = { if (isEditMode) isEditMode = false }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════════════════════════════
            // UST BAR: SAYFA GOSTERGESI, DUZENLEME DURUMU & WIDGET EKLE BUTONU
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol: Sayfa Noktalari
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (p in 0 until pageCount) {
                        val aktif = pagerState.currentPage == p
                        Box(
                            modifier = Modifier
                                .size(if (aktif) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (aktif) ClPrimary else Color(0x44FFFFFF))
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(p) }
                                }
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sayfa ${pagerState.currentPage + 1} / $pageCount",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (isEditMode) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x33FF9500))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Düzenleme Modu (Bitirmek için boş alana dokunun)",
                                color = Color(0xFFFF9500),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Sag: + Widget Ekle ve Mod Kontrol Butonlari
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode) {
                        // Tamam Butonu
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClSuccess.copy(alpha = 0.25f))
                                .border(1.dp, ClSuccess, RoundedCornerShape(8.dp))
                                .clickable { isEditMode = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ClSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Tamam",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // + Widget Ekle Butonu
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x220A84FF))
                            .border(1.dp, ClPrimary, RoundedCornerShape(8.dp))
                            .clickable { showWidgetPicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ClPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Widget Ekle",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onKapat != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onKapat() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // SAYFALAR & LAUNCHER 2/3 HUCRESEL CELL LAYOUT (8x4 GRID)
            // ═══════════════════════════════════════════════════════════════
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isEditMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val pageWidgets = widgets.filter { it.pageIndex == pageIndex && it.isVisible }

                WorkspaceCellPageLayout(
                    pageIndex = pageIndex,
                    widgets = pageWidgets,
                    isEditMode = isEditMode,
                    saatMetni = saatMetni,
                    tarihMetni = tarihMetni,
                    telemetri = telemetri,
                    medya = medya,
                    onOynatDuraklat = onOynatDuraklat,
                    onSonraki = onSonraki,
                    onOnceki = onOnceki,
                    onLaunchApp = onLaunchApp,
                    onEnterEditMode = { isEditMode = true },
                    onDeleteWidget = { widgetId -> widgetManager.removeWidget(widgetId) },
                    onMoveWidget = { id, cellX, cellY ->
                        val w = pageWidgets.find { it.id == id }
                        if (w != null) {
                            widgetManager.updateWidgetPlacement(id, pageIndex, cellX, cellY, w.spanX, w.spanY)
                        }
                    },
                    onResizeWidget = { id, spanX, spanY ->
                        val w = pageWidgets.find { it.id == id }
                        if (w != null) {
                            widgetManager.updateWidgetPlacement(id, pageIndex, w.cellX, w.cellY, spanX, spanY)
                        }
                    }
                )
            }
        }

        // Widget Secici Kutuphanesi Dialogu (Gorseldeki gibi)
        if (showWidgetPicker) {
            WidgetPickerDialogView(
                activePageIndex = pagerState.currentPage,
                onDismiss = { showWidgetPicker = false },
                onWidgetAdded = {
                    showWidgetPicker = false
                }
            )
        }
    }
}

/**
 * 8 Sütun x 4 Satır Launcher 2/3 Hücresel Grid Çizim Alanı
 */
@Composable
fun WorkspaceCellPageLayout(
    pageIndex: Int,
    widgets: List<BaseWidget>,
    isEditMode: Boolean,
    saatMetni: String,
    tarihMetni: String,
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onEnterEditMode: () -> Unit,
    onDeleteWidget: (String) -> Unit,
    onMoveWidget: (String, Int, Int) -> Unit,
    onResizeWidget: (String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colCount = WidgetManager.COL_COUNT
    val rowCount = WidgetManager.ROW_COUNT
    val spacingDp = 8.dp

    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var dragCellX by remember { mutableIntStateOf(-1) }
    var dragCellY by remember { mutableIntStateOf(-1) }
    var isDragValid by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x08FFFFFF))
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val totalHeightPx = constraints.maxHeight.toFloat()
        val spacingPx = with(density) { spacingDp.toPx() }

        val cellWidthPx = (totalWidthPx - (colCount - 1) * spacingPx) / colCount
        val cellHeightPx = (totalHeightPx - (rowCount - 1) * spacingPx) / rowCount

        // 1. Duzenleme Modu Izgara Kilavuz Cizgileri (Grid Lines)
        if (isEditMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0x1FFFFFFF)
                for (r in 0 until rowCount) {
                    for (c in 0 until colCount) {
                        val left = c * (cellWidthPx + spacingPx)
                        val top = r * (cellHeightPx + spacingPx)
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(left, top),
                            size = Size(cellWidthPx, cellHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Sürükleme Hedef Alan Maskesi (Yeşil / Kırmızı)
                if (draggingWidgetId != null && dragCellX >= 0 && dragCellY >= 0) {
                    val draggingWidget = widgets.find { it.id == draggingWidgetId }
                    if (draggingWidget != null) {
                        val targetLeft = dragCellX * (cellWidthPx + spacingPx)
                        val targetTop = dragCellY * (cellHeightPx + spacingPx)
                        val targetWidth = draggingWidget.spanX * cellWidthPx + (draggingWidget.spanX - 1) * spacingPx
                        val targetHeight = draggingWidget.spanY * cellHeightPx + (draggingWidget.spanY - 1) * spacingPx

                        drawRoundRect(
                            color = if (isDragValid) Color(0x4430D158) else Color(0x44FF453A),
                            topLeft = Offset(targetLeft, targetTop),
                            size = Size(targetWidth, targetHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }
                }
            }
        }

        // 2. Widget Elemanlari
        widgets.forEach { widget ->
            val isCurrentDragging = draggingWidgetId == widget.id

            val safeCellX = widget.cellX.coerceIn(0, colCount - 1)
            val safeCellY = widget.cellY.coerceIn(0, rowCount - 1)
            val safeSpanX = widget.spanX.coerceIn(1, colCount - safeCellX)
            val safeSpanY = widget.spanY.coerceIn(1, rowCount - safeCellY)

            val leftPx = safeCellX * (cellWidthPx + spacingPx)
            val topPx = safeCellY * (cellHeightPx + spacingPx)
            val widthPx = safeSpanX * cellWidthPx + (safeSpanX - 1) * spacingPx
            val heightPx = safeSpanY * cellHeightPx + (safeSpanY - 1) * spacingPx

            val leftDp = with(density) { leftPx.toDp() }
            val topDp = with(density) { topPx.toDp() }
            val widthDp = with(density) { widthPx.toDp() }
            val heightDp = with(density) { heightPx.toDp() }

            // Titreme (Jiggle/Shake) Animasyonu (Launcher3 ve iOS tarzi)
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = -1.2f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(130, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .graphicsLayer {
                        if (isEditMode && !isCurrentDragging) {
                            rotationZ = rotation
                        }
                    }
                    .pointerInput(widget.id, isEditMode) {
                        detectDragGestures(
                            onDragStart = {
                                if (isEditMode) {
                                    draggingWidgetId = widget.id
                                    dragCellX = widget.cellX
                                    dragCellY = widget.cellY
                                    isDragValid = true
                                }
                            },
                            onDragCancel = {
                                draggingWidgetId = null
                                dragCellX = -1
                                dragCellY = -1
                            },
                            onDragEnd = {
                                if (draggingWidgetId == widget.id && isDragValid && dragCellX >= 0 && dragCellY >= 0) {
                                    onMoveWidget(widget.id, dragCellX, dragCellY)
                                }
                                draggingWidgetId = null
                                dragCellX = -1
                                dragCellY = -1
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentX = leftPx + change.position.x
                                val currentY = topPx + change.position.y
                                val calculatedCol = ((currentX / (cellWidthPx + spacingPx)).toInt()).coerceIn(0, colCount - widget.spanX)
                                val calculatedRow = ((currentY / (cellHeightPx + spacingPx)).toInt()).coerceIn(0, rowCount - widget.spanY)

                                dragCellX = calculatedCol
                                dragCellY = calculatedRow

                                // Cakisma kontrolu
                                val widgetManager = WidgetManager.getInstance(context)
                                isDragValid = widgetManager.isRegionVacant(
                                    pageIndex = pageIndex,
                                    cellX = calculatedCol,
                                    cellY = calculatedRow,
                                    spanX = widget.spanX,
                                    spanY = widget.spanY,
                                    ignoreWidgetId = widget.id
                                )
                            }
                        )
                    }
                    .pointerInput(widget.id) {
                        detectTapGestures(
                            onLongPress = { onEnterEditMode() },
                            onTap = {
                                if (isEditMode) {
                                    // Edit modundayken dokunma
                                } else if (widget.typeId == "shortcut" && widget.packageName != null) {
                                    onLaunchApp(widget.packageName!!)
                                }
                            }
                        )
                    }
            ) {
                // Widget Kart Cercevesi
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClCardBg)
                        .border(
                            width = if (isEditMode) 1.5.dp else 1.dp,
                            color = if (isEditMode) Color.White else ClBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(8.dp)
                ) {
                    // Icerik Renderi
                    RenderWidgetContent(
                        widget = widget,
                        saatMetni = saatMetni,
                        tarihMetni = tarihMetni,
                        telemetri = telemetri,
                        medya = medya,
                        onOynatDuraklat = onOynatDuraklat,
                        onSonraki = onSonraki,
                        onOnceki = onOnceki,
                        onLaunchApp = onLaunchApp
                    )
                }

                // ═══════════════════════════════════════════════════════════
                // DUZENLEME MODU AKSİYONLARI: SIL BUTONU VE BOYUT TUTAMACLARI
                // ═══════════════════════════════════════════════════════════
                if (isEditMode) {
                    // Sag Ust Sil Butonu (Delete Badge ✕)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ClDanger)
                            .clickable { onDeleteWidget(widget.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sil",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Sag Kenar Boyut Tutamaci (Genislik SpanX Artir / Azalt)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.Black, CircleShape)
                            .clickable {
                                val nextSpanX = if (safeSpanX >= 4) 1 else safeSpanX + 1
                                onResizeWidget(widget.id, nextSpanX, safeSpanY)
                            }
                    )

                    // Alt Kenar Boyut Tutamaci (Yukseklik SpanY Artir / Azalt)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.Black, CircleShape)
                            .clickable {
                                val nextSpanY = if (safeSpanY >= 4) 1 else safeSpanY + 1
                                onResizeWidget(widget.id, safeSpanX, nextSpanY)
                            }
                    )
                }
            }
        }
    }
}

/**
 * Widget Icerik Rendersi
 */
@Composable
fun RenderWidgetContent(
    widget: BaseWidget,
    saatMetni: String,
    tarihMetni: String,
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember { WidgetManager.getInstance(context) }

    when {
        // 1. SISTEM WIDGETI (AppWidgetHostView)
        widget.typeId == "system" && widget.appWidgetId != -1 -> {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val appWidgetManager = AppWidgetManager.getInstance(ctx)
                    val providerInfo = appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
                    if (providerInfo != null) {
                        widgetManager.appWidgetHost.createView(ctx, widget.appWidgetId, providerInfo)
                    } else {
                        android.widget.TextView(ctx).apply {
                            text = widget.title
                            setTextColor(android.graphics.Color.WHITE)
                        }
                    }
                }
            )
        }

        // 2. UYGULAMA KISAYOLU
        widget.typeId == "shortcut" && widget.packageName != null -> {
            val pm = context.packageManager
            val appIcon = remember(widget.packageName) {
                try { pm.getApplicationIcon(widget.packageName!!) } catch (e: Exception) { null }
            }
            val bmp = remember(appIcon) {
                try { appIcon?.toBitmap(80, 80)?.asImageBitmap() } catch (e: Exception) { null }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(36.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = widget.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. DASHBOARD (BIRLESIK WIDGET)
        widget.typeId == WidgetRegistry.TYPE_COMBINED -> {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = saatMetni, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(text = tarihMetni, color = Color.LightGray, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text(text = "KM/S", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. HIZ & LIMIT
        widget.typeId == WidgetRegistry.TYPE_SPEED -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "${telemetri.anlikHizKmh}", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(text = "KM / S", color = ClPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 5. DIJITAL SAAT
        widget.typeId == WidgetRegistry.TYPE_CLOCK -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = saatMetni, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(text = tarihMetni, color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
            }
        }

        // 6. MEDYA CALAR
        widget.typeId == WidgetRegistry.TYPE_MUSIC -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF232536)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color(0xFFFF375F), radius = size.minDimension / 2.5f)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = medya.baslik.ifBlank { "Müzik Çalınmıyor" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(text = medya.sanatci.ifBlank { "Vela Medya" }, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◀◀",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOnceki() }
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ClPrimary,
                        modifier = Modifier.size(30.dp).clickable { onOynatDuraklat() }
                    )
                    Text(
                        text = "▶▶",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSonraki() }
                    )
                }
            }
        }

        // 7. HAVA DURUMU
        widget.typeId == WidgetRegistry.TYPE_WEATHER -> {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Canvas(modifier = Modifier.size(28.dp)) {
                    drawCircle(color = Color(0xFFFFCC00), radius = size.minDimension / 2.5f)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "22°C", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Açık", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }

        // 8. PUSULA & YON
        widget.typeId == WidgetRegistry.TYPE_COMPASS -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(26.dp)) {
                    drawCircle(color = Color(0xFF32ADE6), radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "KUZEY (${telemetri.pusulaYonu.toInt()}°)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 9. OBD2 / TELEMETRI
        widget.typeId == WidgetRegistry.TYPE_OBD -> {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "RPM", color = Color.Gray, fontSize = 10.sp)
                    Text(text = "2400", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

        // VARSAYILAN KART
        else -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = widget.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
