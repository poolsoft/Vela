package app.vela.carlauncher.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * - Sol ust tutamac: Surukleyerek serbest tasima.
 * - Sag alt tutamac: Boyutlandirma (en + boy birlikte).
 * - Sag kenar ortasi tutamagi: Yalnizca genislik.
 * - Alt kenar ortasi tutamagi: Yalnizca yukseklik.
 * - Sag ust tutamac: Silme (kapatma).
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
    val pageEdge by rememberUpdatedState(onPageEdge)
    val draggingCallback by rememberUpdatedState(onDragging)
    var dx by remember { mutableFloatStateOf(placement.dx) }
    var dy by remember { mutableFloatStateOf(placement.dy) }
    var widthScale by remember { mutableFloatStateOf(placement.widthScale) }
    var heightScale by remember { mutableFloatStateOf(placement.heightScale) }
    var widgetWidth by remember { mutableFloatStateOf(0f) }
    var widgetHeight by remember { mutableFloatStateOf(0f) }
    var initialBaseWidth by remember { mutableFloatStateOf(0f) }
    var initialBaseHeight by remember { mutableFloatStateOf(0f) }

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
    }

    val baseWidth = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
    val baseHeight = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
    val currentPixelWidth = (baseWidth * widthScale).coerceAtLeast(MIN_WIDTH_DP)
    val currentPixelHeight = (baseHeight * heightScale).coerceAtLeast(MIN_HEIGHT_DP)
    // Non-uniform uzamayi/sundurulmeyi onlemek icin icerige uygulanan en-boy dengeli olcek
    val contentScale = minOf(widthScale, heightScale)

    fun commitSnapped() {
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

            if (resizable) {
                fun baseDims(): Pair<Float, Float> {
                    val baseW = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
                    val baseH = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
                    return baseW to baseH
                }
                // Sag alt kose: en + boy birlikte (capraz olcek, Launcher tarzi).
                ResizeHandle(
                    icon = Icons.Default.OpenInFull,
                    iconRotation = 0f,
                    description = "Boyutlandir",
                    axis = ResizeAxis.BOTH,
                    sizeDp = 28.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 6.dp)
                        .zIndex(10f),
                    onResize = { axis, dragX, dragY ->
                        val (baseW, baseH) = baseDims()
                        val minWScale = maxOf(PlacementBounds.MIN_SCALE, MIN_WIDTH_DP / baseW)
                        val minHScale = maxOf(PlacementBounds.MIN_SCALE, MIN_HEIGHT_DP / baseH)
                        val minCommonScale = maxOf(minWScale, minHScale)
                        val maxWScale = minOf(PlacementBounds.MAX_SCALE, (workspaceWidth - dx).coerceAtLeast(baseW) / baseW)
                        val maxHScale = minOf(PlacementBounds.MAX_SCALE, (workspaceHeight - dy).coerceAtLeast(baseH) / baseH)
                        val maxCommonScale = minOf(maxWScale, maxHScale).coerceAtLeast(minCommonScale)
                        when (axis) {
                            ResizeAxis.WIDTH ->
                                widthScale = (widthScale + (dragX / density) / baseW).coerceIn(minWScale, maxWScale)
                            ResizeAxis.HEIGHT ->
                                heightScale = (heightScale + (dragY / density) / baseH).coerceIn(minHScale, maxHScale)
                            ResizeAxis.BOTH -> {
                                // En ve boy birlikte; her eksenin kendi payiyla olceklendirilir.
                                val delta = ((dragX / density) / baseW + (dragY / density) / baseH) / 2f
                                val newScale = (widthScale + delta).coerceIn(minCommonScale, maxCommonScale)
                                widthScale = newScale
                                heightScale = newScale
                            }
                        }
                    },
                    onEnd = { commitSnapped() }
                )
                // Sag kenar ortasi: yalnizca genislik.
                ResizeHandle(
                    icon = Icons.Default.OpenInFull,
                    iconRotation = 90f,
                    description = "Genislik",
                    axis = ResizeAxis.WIDTH,
                    sizeDp = 22.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(6.dp, 0.dp)
                        .zIndex(10f),
                    onResize = { axis, dragX, dragY ->
                        val (baseW, baseH) = baseDims()
                        val minWScale = maxOf(PlacementBounds.MIN_SCALE, MIN_WIDTH_DP / baseW)
                        val maxWScale = minOf(PlacementBounds.MAX_SCALE, (workspaceWidth - dx).coerceAtLeast(baseW) / baseW)
                        when (axis) {
                            ResizeAxis.WIDTH ->
                                widthScale = (widthScale + (dragX / density) / baseW).coerceIn(minWScale, maxWScale)
                            else -> {}
                        }
                    },
                    onEnd = { commitSnapped() }
                )
                // Alt kenar ortasi: yalnizca yukseklik.
                ResizeHandle(
                    icon = Icons.Default.OpenInFull,
                    iconRotation = 45f,
                    description = "Yukseklik",
                    axis = ResizeAxis.HEIGHT,
                    sizeDp = 22.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, 6.dp)
                        .zIndex(10f),
                    onResize = { axis, dragX, dragY ->
                        val (baseW, baseH) = baseDims()
                        val minHScale = maxOf(PlacementBounds.MIN_SCALE, MIN_HEIGHT_DP / baseH)
                        val maxHScale = minOf(PlacementBounds.MAX_SCALE, (workspaceHeight - dy).coerceAtLeast(baseH) / baseH)
                        when (axis) {
                            ResizeAxis.HEIGHT ->
                                heightScale = (heightScale + (dragY / density) / baseH).coerceIn(minHScale, maxHScale)
                            else -> {}
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
 * Suruklenerek boyutlandirma tutamacı; drag px cinsinden onResize'a iletilir.
 * Birakildiginda onEnd ile grid snap'li commit yapilir.
 */
@Composable
private fun ResizeHandle(
    icon: ImageVector,
    iconRotation: Float,
    description: String,
    axis: ResizeAxis,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    onResize: (ResizeAxis, Float, Float) -> Unit,
    onEnd: () -> Unit
) {
    Surface(
        color = Color(0xCC1A1D2E),
        contentColor = Color(0xFF00E5FF),
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = modifier.size(sizeDp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.pointerInput(Unit) {
                detectDragGestures(onDragEnd = { onEnd() }, onDragCancel = { onEnd() }) { change, drag ->
                    change.consume()
                    onResize(axis, drag.x, drag.y)
                }
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = iconRotation }
            )
        }
    }
}
