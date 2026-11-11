package app.vela.carlauncher.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private const val SNAP_STEP_DP = 16f
private const val MIN_WIDTH_DP = 120f
private const val MIN_HEIGHT_DP = 60f

/**
 * UmainLauncher tabanli serbest tasinabilir ve boyutlandirilabilir widget saricisi (MovableWidget).
 *
 * - dragViaHandle: true yapildiginda widget govdesi tum dokunmalari icerige birakir
 *   (muzik dugmeleri, tiklamalar, harita vb. kisisel dokunmalarini kaybetmez).
 * - Edit modunda: sol ust tutamac tasi, sag ust X kaldirir, sol alt gear ayarlari acar;
 *   boyutlandirma tutamaclari (kose/sag kenar/alt kenar) varsayilan gorunurdur.
 * - Widget'a dokunus veya uzun basma: Launcher tarzi popup menu (Boyutlandir ac/kapa,
 *   Ayarlar, Kaldir).
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun MovableWidget(
    placement: WidgetPlacement,
    resizable: Boolean = true,
    dragViaHandle: Boolean = true,
    showControls: Boolean = true,
    workspaceWidth: Float = Float.MAX_VALUE,
    workspaceHeight: Float = Float.MAX_VALUE,
    snapToGrid: Boolean = true,
    // Hucre grid modu: grid null degilse ve snap aciksa Launcher tarzi hucre yerlesimi
    // kullanilir; aksi halde serbest float mod (eski davranis) gecerlidir.
    grid: CellGrid.Grid? = null,
    minSpanX: Int = 1,
    minSpanY: Int = 1,
    onEnterEdit: () -> Unit = {},
    onDragging: (Boolean) -> Unit = {},
    onPageEdge: (Int) -> Int = { placement.page },
    onRemove: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onCommit: (WidgetPlacement) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val currentPlacement by rememberUpdatedState(placement)
    val currentSnapToGrid by rememberUpdatedState(snapToGrid)
    val editing by rememberUpdatedState(showControls)
    val enterEdit by rememberUpdatedState(onEnterEdit)
    var dragging by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(0) }
    var dragPage by remember { mutableStateOf(placement.page) }
    // Edit modunda widget'a dokununca acilan Launcher tarzi popup menu. Boyutlandirma
    // tutamaclari edit modunda VARSAYILAN gorunurdur (arayuz kesfedilebilir olsun);
    // menuden kapatilabilir.
    var menuOpen by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(true) }
    val pageEdge by rememberUpdatedState(onPageEdge)
    val draggingCallback by rememberUpdatedState(onDragging)
    var dx by remember { mutableFloatStateOf(placement.dx) }
    var dy by remember { mutableFloatStateOf(placement.dy) }
    var widthScale by remember { mutableFloatStateOf(placement.widthScale) }
    var heightScale by remember { mutableFloatStateOf(placement.heightScale) }
    // Hucre modu span'i; resize tutamacindan canli guncellenir.
    var spanX by remember { mutableIntStateOf(placement.spanX.coerceAtLeast(1)) }
    var spanY by remember { mutableIntStateOf(placement.spanY.coerceAtLeast(1)) }
    var widgetWidth by remember { mutableFloatStateOf(0f) }
    var widgetHeight by remember { mutableFloatStateOf(0f) }
    var initialBaseWidth by remember { mutableFloatStateOf(0f) }
    var initialBaseHeight by remember { mutableFloatStateOf(0f) }
    val latestGrid by rememberUpdatedState(grid)
    val latestMinSpanX by rememberUpdatedState(minSpanX)
    val latestMinSpanY by rememberUpdatedState(minSpanY)

    // Edit moda giriste boyutlandirma tutamaclari yeniden acilir; cikista menu kapatilir.
    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatorY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(showControls) {
        if (showControls) {
            resizing = true
        } else {
            menuOpen = false
            resizing = true
        }
    }

    /** Grid modu: snap ayari aciksa ve grid bilgisi geldiyse hucre modeli kullanilir. */
    fun activeGrid(): CellGrid.Grid? = if (currentSnapToGrid) latestGrid else null

    LaunchedEffect(edge, dragging) {
        // Tek atisli sayfa flip'i: kenarda 650ms beklenir, sonra sayfa cevrilir ve edge sifirlanir.
        // Eski while(true) dongusu edge==0 iken de pageEdge(0) cagirip bos yere donuyordu.
        if (!dragging || edge == 0) return@LaunchedEffect
        val dir = edge
        delay(650)
        if (dragging && edge == dir) {
            dragPage = pageEdge(dir)
            edge = 0
        }
    }

    LaunchedEffect(placement) {
        if (dragging) return@LaunchedEffect
        dragPage = placement.page
        dx = placement.dx
        dy = placement.dy
        widthScale = placement.widthScale
        heightScale = placement.heightScale
        spanX = placement.spanX.takeIf { it > 0 } ?: 1
        spanY = placement.spanY.takeIf { it > 0 } ?: 1
    }

    val baseWidth = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
    val baseHeight = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
    val gridForCompose = activeGrid()
    val currentPixelWidth = if (gridForCompose != null) {
        (spanX * gridForCompose.cellWidth).coerceAtLeast(MIN_WIDTH_DP)
    } else {
        (baseWidth * widthScale).coerceAtLeast(MIN_WIDTH_DP)
    }
    val currentPixelHeight = if (gridForCompose != null) {
        (spanY * gridForCompose.cellHeight).coerceAtLeast(MIN_HEIGHT_DP)
    } else {
        (baseHeight * heightScale).coerceAtLeast(MIN_HEIGHT_DP)
    }
    // Non-uniform uzamayi/sundurulmeyi onlemek icin icerige uygulanan en-boy dengeli olcek
    val contentScale = if (gridForCompose != null && initialBaseWidth > 0f && initialBaseHeight > 0f) {
        minOf(currentPixelWidth / initialBaseWidth, currentPixelHeight / initialBaseHeight)
    } else {
        minOf(widthScale, heightScale)
    }

    fun commitSnapped() {
        val g = activeGrid()
        if (g != null) {
            // Hucre modu: konumu ve boyutu hucre agina oturt; span'i kaydet.
            val sx = spanX.coerceIn(latestMinSpanX, g.cols)
            val sy = spanY.coerceIn(latestMinSpanY, g.rows)
            val cx = (dx / g.cellWidth).roundToInt().coerceIn(0, g.cols - sx)
            val cy = (dy / g.cellHeight).roundToInt().coerceIn(0, g.rows - sy)
            val snappedDx = cx * g.cellWidth
            val snappedDy = cy * g.cellHeight
            dx = snappedDx
            dy = snappedDy
            spanX = sx
            spanY = sy
            onCommit(currentPlacement.copy(
                page = dragPage,
                dx = snappedDx,
                dy = snappedDy,
                scale = 1f,
                widthScale = 1f,
                heightScale = 1f,
                cellX = cx,
                cellY = cy,
                spanX = sx,
                spanY = sy
            ))
        } else {
            val snappedDx = (if (currentSnapToGrid) (dx / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dx)
                .coerceIn(0f, (workspaceWidth - currentPixelWidth).coerceAtLeast(0f))
            val snappedDy = (if (currentSnapToGrid) (dy / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dy)
                .coerceIn(0f, (workspaceHeight - currentPixelHeight).coerceAtLeast(0f))
            dx = snappedDx
            dy = snappedDy
            onCommit(currentPlacement.copy(
                page = dragPage,
                dx = snappedDx,
                dy = snappedDy,
                scale = minOf(widthScale, heightScale),
                widthScale = widthScale,
                heightScale = heightScale
            ))
        }
    }

    fun finishDrag() {
        dragging = false
        edge = 0
        commitSnapped()
        draggingCallback(false)
    }

    fun moveBy(x: Float, y: Float) {
        dx = (dx + x / density).coerceIn(0f, (workspaceWidth - currentPixelWidth).coerceAtLeast(0f))
        dy = (dy + y / density).coerceIn(0f, (workspaceHeight - currentPixelHeight).coerceAtLeast(0f))
        edge = when {
            dx < 12f -> -1
            dx + currentPixelWidth > workspaceWidth - 12f -> 1
            else -> 0
        }
    }

    Box(
        modifier = modifier
            .offset(
                dx.coerceIn(0f, (workspaceWidth - currentPixelWidth).coerceAtLeast(0f)).dp,
                dy.coerceIn(0f, (workspaceHeight - currentPixelHeight).coerceAtLeast(0f)).dp
            )
            .requiredSize(currentPixelWidth.dp, currentPixelHeight.dp)
            .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!editing) {
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            val held = pointer != null && pointer.pressed &&
                                (pointer.position - down.position).getDistance() < viewConfiguration.touchSlop
                        } while (held)
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    enterEdit()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        val held = event.changes.any { it.pressed }
                    } while (held)
                }
            }
        }
    }) {
        Box(
            modifier = Modifier
                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .onSizeChanged {
                    if (it.width > 0 && it.height > 0) {
                        val w = it.width / density
                        val h = it.height / density
                        if (initialBaseWidth <= 0f) {
                            initialBaseWidth = w
                            widgetWidth = w
                        }
                        if (initialBaseHeight <= 0f) {
                            initialBaseHeight = h
                            widgetHeight = h
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            content()
        }

        if (showControls) {
            // Hucre modunda surukleme sirasinda hedef hucre bolgesi vurgulanir (Launcher davranisi).
            val previewGrid = activeGrid()
            if (previewGrid != null && dragging) {
                val previewSpanX = spanX.coerceIn(1, previewGrid.cols)
                val previewSpanY = spanY.coerceIn(1, previewGrid.rows)
                val previewCellX = (dx / previewGrid.cellWidth).roundToInt()
                    .coerceIn(0, previewGrid.cols - previewSpanX)
                val previewCellY = (dy / previewGrid.cellHeight).roundToInt()
                    .coerceIn(0, previewGrid.rows - previewSpanY)
                Box(
                    Modifier
                        .offset((previewCellX * previewGrid.cellWidth - dx).dp, (previewCellY * previewGrid.cellHeight - dy).dp)
                        .requiredSize(
                            (previewSpanX * previewGrid.cellWidth).dp,
                            (previewSpanY * previewGrid.cellHeight).dp
                        )
                        .background(Color(0x2200E5FF), RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0x8800E5FF), RoundedCornerShape(10.dp))
                )
            }
            Box(
                Modifier
                    .requiredSize(currentPixelWidth.dp, currentPixelHeight.dp)
                    .border(1.dp, Color(0xAA00E5FF), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true; draggingCallback(true) },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() }
                        ) { change, drag ->
                            change.consume()
                            moveBy(drag.x, drag.y)
                        }
                    }
                    .pointerInput(editing) {
                        // Edit modunda kisa dokunus veya uzun basma popup menuyu acar;
                        // surukleme drag dedektoru tarafindan tuketildigi icin tap olarak algilanmaz.
                        if (editing) {
                            detectTapGestures(
                                onTap = { menuOpen = true },
                                onLongPress = { menuOpen = true }
                            )
                        }
                    }
            )
            if (dragViaHandle) {
                HandleSurface(
                    icon = Icons.Default.OpenWith,
                    description = "Tasi",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset((-6).dp, (-6).dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = true; draggingCallback(true) },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() }
                            ) { change, drag ->
                                change.consume()
                                moveBy(drag.x, drag.y)
                            }
                        }
                )
            }

            if (onRemove != null) {
                HandleSurface(
                    icon = Icons.Default.Close,
                    description = "Kaldir",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(6.dp, (-6).dp)
                        .zIndex(10f)
                        .clickable { onRemove() }
                )
            }

            if (onRemove != null || onSettings != null || resizable) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (resizable) {
                        DropdownMenuItem(
                            text = { Text(if (resizing) "Boyutlandirmayi bitir" else "Boyutlandir") },
                            onClick = { resizing = !resizing; menuOpen = false }
                        )
                    }
                    if (onSettings != null) {
                        DropdownMenuItem(
                            text = { Text("Ayarlar") },
                            onClick = { menuOpen = false; onSettings() }
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Kaldir") },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }

            if (resizable && resizing) {
                fun baseDims(): Pair<Float, Float> {
                    val baseW = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
                    val baseH = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
                    return baseW to baseH
                }
                // Serbest mod: olcek yerine resulting boyut 16dp'nin katina yuvarlanir.
                // Sinir asiminda clamp devreye girer.
                fun snapScale(base: Float, raw: Float, minS: Float, maxS: Float): Float {
                    val snapped = ((base * raw / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP) / base
                    return snapped.coerceIn(minS, maxS)
                }
                // Hucre modunda span degisimi; sinirlar min span ve kalan hucre sayisidir.
                fun resizeSpan(axis: ResizeAxis, dragX: Float, dragY: Float) {
                    val g = activeGrid() ?: return
                    val cellXNow = (dx / g.cellWidth).roundToInt().coerceIn(0, g.cols - 1)
                    val cellYNow = (dy / g.cellHeight).roundToInt().coerceIn(0, g.rows - 1)
                    val maxSpanX = (g.cols - cellXNow).coerceAtLeast(latestMinSpanX)
                    val maxSpanY = (g.rows - cellYNow).coerceAtLeast(latestMinSpanY)

                    dragAccumulatorX += dragX / density
                    dragAccumulatorY += dragY / density

                    val stepX = (g.cellWidth * 0.25f).coerceIn(16f, 32f)
                    val stepY = (g.cellHeight * 0.25f).coerceIn(16f, 32f)

                    when (axis) {
                        ResizeAxis.WIDTH -> {
                            if (kotlin.math.abs(dragAccumulatorX) >= stepX) {
                                val change = if (dragAccumulatorX > 0) 1 else -1
                                spanX = (spanX + change).coerceIn(latestMinSpanX, maxSpanX)
                                dragAccumulatorX = 0f
                            }
                        }
                        ResizeAxis.HEIGHT -> {
                            if (kotlin.math.abs(dragAccumulatorY) >= stepY) {
                                val change = if (dragAccumulatorY > 0) 1 else -1
                                spanY = (spanY + change).coerceIn(latestMinSpanY, maxSpanY)
                                dragAccumulatorY = 0f
                            }
                        }
                        ResizeAxis.BOTH -> {
                            if (kotlin.math.abs(dragAccumulatorX) >= stepX) {
                                val changeX = if (dragAccumulatorX > 0) 1 else -1
                                spanX = (spanX + changeX).coerceIn(latestMinSpanX, maxSpanX)
                                dragAccumulatorX = 0f
                            }
                            if (kotlin.math.abs(dragAccumulatorY) >= stepY) {
                                val changeY = if (dragAccumulatorY > 0) 1 else -1
                                spanY = (spanY + changeY).coerceIn(latestMinSpanY, maxSpanY)
                                dragAccumulatorY = 0f
                            }
                        }
                    }
                }
                // Sag alt kose: en + boy birlikte (Launcher tarzi dolu daire / fill circle tutamac).
                ResizeHandle(
                    description = "Boyutlandir",
                    axis = ResizeAxis.BOTH,
                    sizeDp = 24.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(12.dp, 12.dp)
                        .zIndex(20f),
                    onResize = { axis, dragX, dragY ->
                        val g = activeGrid()
                        if (g != null) {
                            resizeSpan(axis, dragX, dragY)
                        } else {
                            val (baseW, baseH) = baseDims()
                            val minWScale = maxOf(PlacementBounds.MIN_SCALE, MIN_WIDTH_DP / baseW)
                            val minHScale = maxOf(PlacementBounds.MIN_SCALE, MIN_HEIGHT_DP / baseH)
                            val minCommonScale = maxOf(minWScale, minHScale)
                            val maxWScale = minOf(PlacementBounds.MAX_SCALE, (workspaceWidth - dx).coerceAtLeast(baseW) / baseW)
                            val maxHScale = minOf(PlacementBounds.MAX_SCALE, (workspaceHeight - dy).coerceAtLeast(baseH) / baseH)
                            val maxCommonScale = minOf(maxWScale, maxHScale).coerceAtLeast(minCommonScale)
                            when (axis) {
                                ResizeAxis.WIDTH ->
                                    widthScale = snapScale(baseW, widthScale + (dragX / density) / baseW, minWScale, maxWScale)
                                ResizeAxis.HEIGHT ->
                                    heightScale = snapScale(baseH, heightScale + (dragY / density) / baseH, minHScale, maxHScale)
                                ResizeAxis.BOTH -> {
                                    val delta = ((dragX / density) / baseW + (dragY / density) / baseH) / 2f
                                    val newScale = snapScale(baseW, widthScale + delta, minCommonScale, maxCommonScale)
                                    widthScale = newScale
                                    heightScale = newScale
                                }
                            }
                        }
                    },
                    onEnd = { commitSnapped() }
                )
                // Sag kenar ortasi: yalnizca genislik (Launcher tarzi dolu daire).
                ResizeHandle(
                    description = "Genislik",
                    axis = ResizeAxis.WIDTH,
                    sizeDp = 20.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(10.dp, 0.dp)
                        .zIndex(20f),
                    onResize = { axis, dragX, dragY ->
                        val g = activeGrid()
                        if (g != null) {
                            resizeSpan(axis, dragX, dragY)
                        } else {
                            val (baseW, _) = baseDims()
                            val minWScale = maxOf(PlacementBounds.MIN_SCALE, MIN_WIDTH_DP / baseW)
                            val maxWScale = minOf(PlacementBounds.MAX_SCALE, (workspaceWidth - dx).coerceAtLeast(baseW) / baseW)
                            if (axis == ResizeAxis.WIDTH) {
                                widthScale = snapScale(baseW, widthScale + (dragX / density) / baseW, minWScale, maxWScale)
                            }
                        }
                    },
                    onEnd = { commitSnapped() }
                )
                // Alt kenar ortasi: yalnizca yukseklik (Launcher tarzi dolu daire).
                ResizeHandle(
                    description = "Yukseklik",
                    axis = ResizeAxis.HEIGHT,
                    sizeDp = 20.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, 10.dp)
                        .zIndex(20f),
                    onResize = { axis, dragX, dragY ->
                        val g = activeGrid()
                        if (g != null) {
                            resizeSpan(axis, dragX, dragY)
                        } else {
                            val (_, baseH) = baseDims()
                            val minHScale = maxOf(PlacementBounds.MIN_SCALE, MIN_HEIGHT_DP / baseH)
                            val maxHScale = minOf(PlacementBounds.MAX_SCALE, (workspaceHeight - dy).coerceAtLeast(baseH) / baseH)
                            if (axis == ResizeAxis.HEIGHT) {
                                heightScale = snapScale(baseH, heightScale + (dragY / density) / baseH, minHScale, maxHScale)
                            }
                        }
                    },
                    onEnd = { commitSnapped() }
                )
            }

            if (onSettings != null) {
                HandleSurface(
                    icon = Icons.Default.Settings,
                    description = "Widget ayarlari",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset((-6).dp, 6.dp)
                        .zIndex(10f)
                        .clickable { onSettings() }
                )
            }
        }
    }
}

@Composable
private fun HandleSurface(
    icon: ImageVector,
    description: String,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xCC1A1D2E),
        contentColor = Color(0xFF00E5FF),
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

/** Resize tutamacinin hangi eksen(ler)i etkiledigi. */
private enum class ResizeAxis { WIDTH, HEIGHT, BOTH }

/**
 * Suruklenerek boyutlandirma tutamacı (Launcher tarzi dolu daire / fill circle).
 * Drag px cinsinden onResize'a iletilir.
 * Birakildiginda onEnd ile grid snap'li commit yapilir.
 */
@Composable
private fun ResizeHandle(
    description: String,
    axis: ResizeAxis,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    onResize: (ResizeAxis, Float, Float) -> Unit,
    onEnd: () -> Unit
) {
    // Dokunma alanini artirmak icin en az 44dp'lik dokunma hedefi (touch target)
    val touchTargetSize = maxOf(44.dp, sizeDp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touchTargetSize)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onEnd() }, onDragCancel = { onEnd() }) { change, drag ->
                    change.consume()
                    onResize(axis, drag.x, drag.y)
                }
            }
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF141624)),
            modifier = Modifier.size(sizeDp)
        ) {
            // Beyaz Dolu daire (Fill circle): Icine ikon konulmaz, konumu yonunu bildirir.
            Box(modifier = Modifier.size(sizeDp))
        }
    }
}
