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
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private const val SNAP_STEP_DP = 16f
private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 2.2f

/**
 * UmainLauncher tabanli serbest tasinabilir ve boyutlandirilabilir widget saricisi (MovableWidget).
 *
 * - dragViaHandle: true yapildiginda widget govdesi tum dokunmalari icerige birakir
 *   (muzik dugmeleri, tiklamalar, harita vb. kisisel dokunmalarini kaybetmez).
 * - Sol ust tutamac: Surukleyerek serbest tasima.
 * - Sag alt tutamac: Boyutlandirma (scale).
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

    LaunchedEffect(edge, dragging) {
        if (dragging && edge != 0) {
            while (true) {
                delay(650)
                val next = pageEdge(edge)
                if (next != dragPage) {
                    dragPage = next
                    edge = 0
                }
            }
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

    fun commitSnapped() {
        val snappedDx = (if (currentSnapToGrid) (dx / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dx)
            .coerceIn(0f, (workspaceWidth - widgetWidth * widthScale).coerceAtLeast(0f))
        val snappedDy = (if (currentSnapToGrid) (dy / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dy)
            .coerceIn(0f, (workspaceHeight - widgetHeight * heightScale).coerceAtLeast(0f))
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
        commitSnapped()
        dragging = false
        edge = 0
        draggingCallback(false)
    }

    fun moveBy(x: Float, y: Float) {
        dx = (dx + x / density).coerceIn(0f, (workspaceWidth - widgetWidth * widthScale).coerceAtLeast(0f))
        dy = (dy + y / density).coerceIn(0f, (workspaceHeight - widgetHeight * heightScale).coerceAtLeast(0f))
        edge = when {
            dx < 12f -> -1
            dx + widgetWidth * widthScale > workspaceWidth - 12f -> 1
            else -> 0
        }
    }

    Box(modifier = modifier.offset(
        dx.coerceIn(0f, (workspaceWidth - widgetWidth * widthScale).coerceAtLeast(0f)).dp,
        dy.coerceIn(0f, (workspaceHeight - widgetHeight * heightScale).coerceAtLeast(0f)).dp
    ).pointerInput(Unit) {
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
                .onSizeChanged {
                    widgetWidth = it.width / density
                    widgetHeight = it.height / density
                }
                .graphicsLayer {
                    scaleX = widthScale
                    scaleY = heightScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            content()
        }

        if (showControls) {
            if (widgetWidth > 0f && widgetHeight > 0f) {
                Box(
                    Modifier
                        .size((widgetWidth * widthScale).dp, (widgetHeight * heightScale).dp)
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
            }
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
                HandleSurface(
                    icon = Icons.Default.OpenInFull,
                    description = "Yatay boyutlandir",
                    rotation = 45f,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(6.dp, 0.dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { commitSnapped() }) { change, drag ->
                                change.consume()
                                widthScale = (widthScale + drag.x.toDp().value / 180f)
                                    .coerceIn(MIN_SCALE, MAX_SCALE)
                            }
                        }
                )
                HandleSurface(
                    icon = Icons.Default.OpenInFull,
                    description = "Dikey boyutlandir",
                    rotation = 45f,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, 6.dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { commitSnapped() }) { change, drag ->
                                change.consume()
                                heightScale = (heightScale + drag.y.toDp().value / 180f)
                                    .coerceIn(MIN_SCALE, MAX_SCALE)
                            }
                        }
                )
                HandleSurface(
                    icon = Icons.Default.OpenInFull,
                    description = "Orantili boyutlandir",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 6.dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { commitSnapped() }) { change, drag ->
                                change.consume()
                                val delta = (drag.x + drag.y).toDp().value / 180f
                                widthScale = (widthScale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
                                heightScale = (heightScale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
                            }
                        }
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
