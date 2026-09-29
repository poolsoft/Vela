package app.vela.carlauncher.ui

import android.appwidget.AppWidgetManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.zIndex
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
import kotlin.math.roundToInt

private val ClCardBg = Color(0xF0141624)
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)
private val ClDanger = Color(0xFFFF453A)
private val ClSuccess = Color(0xFF30D158)

/**
 * Tam Sayfa (Full-Screen) Launcher 2/3 12x6 Masaustu Workspace.
 * - Ust bar tamamen kaldirilip tam ekran yapildi.
 * - Sayfa gosterge noktalari altta standart launcher tarzi yerlesime alindi.
 * - Duzenleme modunda ve widget ekleme butonlari yuzen zarif pill tasariminda sunulur.
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

    // Sayfa degisimi veya kaydirma sirasinda gorunup sonra kaybolan sayfa gostergesi
    var isIndicatorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            isIndicatorVisible = true
        } else {
            isIndicatorVisible = true
            delay(1200L)
            isIndicatorVisible = false
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
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { 
                        // Masaustu bos alana uzun basildiginda dogrudan Widget Secici acilir
                        showWidgetPicker = true 
                    },
                    onTap = { 
                        if (isEditMode) isEditMode = false 
                    }
                )
            }
    ) {
        // ═══════════════════════════════════════════════════════════════
        // TAM SAYFA 12x6 HUCRESEL MASAUSTU (PAGER)
        // ═══════════════════════════════════════════════════════════════
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isEditMode,
            modifier = Modifier.fillMaxSize()
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

        // ═══════════════════════════════════════════════════════════════
        // NORMAL SAYFALAR GIBI ALTA SAYFA DEGISTIRIRKEN GORUNEN NOKTALAR
        // ═══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = isIndicatorVisible || isEditMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .zIndex(100f)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x77000000))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (p in 0 until pageCount) {
                        val aktif = pagerState.currentPage == p
                        Box(
                            modifier = Modifier
                                .size(if (aktif) 8.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (aktif) ClPrimary else Color(0x66FFFFFF))
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(p) }
                                }
                        )
                    }
                }
            }
        }

        // Widget Secici Kutuphanesi Dialogu
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
 * 12 Sütun x 6 Satır Launcher 2/3 Hücresel Grid Çizim Alanı
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
    val spacingDp = 6.dp

    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var dragCellX by remember { mutableIntStateOf(-1) }
    var dragCellY by remember { mutableIntStateOf(-1) }
    var isDragValid by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x04FFFFFF))
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val totalHeightPx = constraints.maxHeight.toFloat()
        val spacingPx = with(density) { spacingDp.toPx() }

        val cellWidthPx = (totalWidthPx - (colCount - 1) * spacingPx) / colCount
        val cellHeightPx = (totalHeightPx - (rowCount - 1) * spacingPx) / rowCount

        // 1. Duzenleme Modu Izgara Kilavuz Cizgileri (Grid Lines)
        if (isEditMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0x14FFFFFF)
                for (r in 0 until rowCount) {
                    for (c in 0 until colCount) {
                        val left = c * (cellWidthPx + spacingPx)
                        val top = r * (cellHeightPx + spacingPx)
                        drawRoundRect(
                            color = gridColor,
                            topLeft = Offset(left, top),
                            size = Size(cellWidthPx, cellHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
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
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
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

            val isShortcut = widget.typeId == "shortcut" || (safeSpanX <= 2 && safeSpanY <= 2 && widget.packageName != null)

            // Titreme (Jiggle/Shake) Animasyonu
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = -1.2f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(130, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            // Canli Surukleme Ofseti (Widget'in parmakla birlikte akmasi)
            var liveDragOffset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp, height = heightDp)
                    .zIndex(if (isCurrentDragging) 20f else 1f)
                    .graphicsLayer {
                        if (isCurrentDragging) {
                            translationX = liveDragOffset.x
                            translationY = liveDragOffset.y
                            scaleX = 1.05f
                            scaleY = 1.05f
                            alpha = 0.88f
                        } else if (isEditMode) {
                            rotationZ = rotation
                        }
                    }
                    .pointerInput(widget.id, isEditMode) {
                        detectDragGestures(
                            onDragStart = {
                                if (isEditMode) {
                                    draggingWidgetId = widget.id
                                    liveDragOffset = Offset.Zero
                                    dragCellX = widget.cellX
                                    dragCellY = widget.cellY
                                    isDragValid = true
                                }
                            },
                            onDragCancel = {
                                draggingWidgetId = null
                                liveDragOffset = Offset.Zero
                                dragCellX = -1
                                dragCellY = -1
                            },
                            onDragEnd = {
                                if (draggingWidgetId == widget.id && isDragValid && dragCellX >= 0 && dragCellY >= 0) {
                                    onMoveWidget(widget.id, dragCellX, dragCellY)
                                }
                                draggingWidgetId = null
                                liveDragOffset = Offset.Zero
                                dragCellX = -1
                                dragCellY = -1
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                liveDragOffset += dragAmount

                                val absoluteX = leftPx + liveDragOffset.x + (widthPx / 2f)
                                val absoluteY = topPx + liveDragOffset.y + (heightPx / 2f)

                                val targetCol = ((absoluteX - (widthPx / 2f)) / (cellWidthPx + spacingPx))
                                    .roundToInt()
                                    .coerceIn(0, colCount - widget.spanX)
                                val targetRow = ((absoluteY - (heightPx / 2f)) / (cellHeightPx + spacingPx))
                                    .roundToInt()
                                    .coerceIn(0, rowCount - widget.spanY)

                                dragCellX = targetCol
                                dragCellY = targetRow

                                val wm = WidgetManager.getInstance(context)
                                isDragValid = wm.isRegionVacant(
                                    pageIndex = pageIndex,
                                    cellX = targetCol,
                                    cellY = targetRow,
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
                                if (!isEditMode && widget.typeId == "shortcut" && widget.packageName != null) {
                                    onLaunchApp(widget.packageName!!)
                                }
                            }
                        )
                    }
            ) {
                // Widget Kart Cercevesi (Kısayollar için kaba kart yerine temiz ikon layout)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isShortcut) Color.Transparent else ClCardBg)
                        .border(
                            width = if (isEditMode) 1.5.dp else if (isShortcut) 0.dp else 1.dp,
                            color = if (isEditMode) Color.White.copy(alpha = 0.8f) else if (isShortcut) Color.Transparent else ClBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(if (isShortcut) 2.dp else 6.dp)
                ) {
                    RenderWidgetContent(
                        widget = widget,
                        isShortcut = isShortcut,
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
                // DUZENLEME MODU: SIL BUTONU VE GENIS DOKUNMA ALANLI TUTAMACLAR
                // ═══════════════════════════════════════════════════════════
                if (isEditMode) {
                    // Sag Ust Sil Butonu (Delete Badge ✕)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(36.dp)
                            .clickable { onDeleteWidget(widget.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(ClDanger),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sil",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Kısayollar harici widget'lar icin Boyutlandirma Tutamacları
                    if (!isShortcut) {
                        // Sag Kenar Boyut Tutamaci (Genis 44dp Touch Target)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 18.dp)
                                .size(44.dp)
                                .clickable {
                                    val minSpanX = if (widget.typeId == WidgetRegistry.TYPE_COMBINED) 4 else 2
                                    val maxSpanX = minOf(colCount - safeCellX, 8)
                                    val nextSpanX = if (safeSpanX >= maxSpanX) minSpanX else (safeSpanX + 2).coerceAtMost(maxSpanX)
                                    onResizeWidget(widget.id, nextSpanX, safeSpanY)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.5.dp, Color.Black, CircleShape)
                            )
                        }

                        // Alt Kenar Boyut Tutamaci (Genis 44dp Touch Target)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 18.dp)
                                .size(44.dp)
                                .clickable {
                                    val minSpanY = 2
                                    val maxSpanY = minOf(rowCount - safeCellY, 4)
                                    val nextSpanY = if (safeSpanY >= maxSpanY) minSpanY else (safeSpanY + 1).coerceAtMost(maxSpanY)
                                    onResizeWidget(widget.id, safeSpanX, nextSpanY)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(1.5.dp, Color.Black, CircleShape)
                            )
                        }
                    }
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
    isShortcut: Boolean,
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
        // 1. UYGULAMA KISAYOLU (Zarif Launcher İkonu Formatı)
        isShortcut && widget.packageName != null -> {
            val pm = context.packageManager
            val appIcon = remember(widget.packageName) {
                try { pm.getApplicationIcon(widget.packageName!!) } catch (e: Exception) { null }
            }
            val bmp = remember(appIcon) {
                try { appIcon?.toBitmap(96, 96)?.asImageBitmap() } catch (e: Exception) { null }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = widget.title.take(1),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = widget.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2. SISTEM WIDGETI (AppWidgetHostView)
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

        // 3. DASHBOARD (BIRLESIK WIDGET: Saat + Hiz)
        widget.typeId == WidgetRegistry.TYPE_COMBINED -> {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = saatMetni, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(text = tarihMetni, color = Color.LightGray, fontSize = 11.sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = 38.sp, fontWeight = FontWeight.Black)
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
                Text(text = "${telemetri.anlikHizKmh}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text(text = "KM / S", color = ClPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 5. DIJITAL SAAT
        widget.typeId == WidgetRegistry.TYPE_CLOCK -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = saatMetni, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(text = tarihMetni, color = Color.LightGray, fontSize = 10.sp, maxLines = 1)
            }
        }

        // 6. MEDYA CALAR
        widget.typeId == WidgetRegistry.TYPE_MUSIC -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF232536)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(color = Color(0xFFFF375F), radius = size.minDimension / 2.5f)
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = medya.baslik.ifBlank { "Müzik Çalınmıyor" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOnceki() }
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ClPrimary,
                        modifier = Modifier.size(28.dp).clickable { onOynatDuraklat() }
                    )
                    Text(
                        text = "▶▶",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onSonraki() }
                    )
                }
            }
        }

        // 7. HAVA DURUMU
        widget.typeId == WidgetRegistry.TYPE_WEATHER -> {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(color = Color(0xFFFFCC00), radius = size.minDimension / 2.5f)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "22°C", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Açık", color = Color.LightGray, fontSize = 10.sp)
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
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(
                        color = Color(0xFF32ADE6),
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "KUZEY (${telemetri.pusulaYonu.toInt()}°)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 9. OBD2 / TELEMETRI
        widget.typeId == WidgetRegistry.TYPE_OBD -> {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "RPM", color = Color.Gray, fontSize = 9.sp)
                    Text(text = "2400", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "HARARET", color = Color.Gray, fontSize = 9.sp)
                    Text(text = "90°C", color = ClSuccess, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "VOLTAJ", color = Color.Gray, fontSize = 9.sp)
                    Text(text = "14.2V", color = ClPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                Text(text = widget.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
