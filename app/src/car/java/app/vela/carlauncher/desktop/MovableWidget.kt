package app.vela.carlauncher.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
    onRemove: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onCommit: (WidgetPlacement) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    var dx by remember { mutableFloatStateOf(placement.dx) }
    var dy by remember { mutableFloatStateOf(placement.dy) }
    var scale by remember { mutableFloatStateOf(placement.scale) }
    var widgetWidth by remember { mutableFloatStateOf(0f) }
    var widgetHeight by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(placement) {
        dx = placement.dx
        dy = placement.dy
        scale = placement.scale
    }

    fun commitSnapped() {
        val snappedDx = ((dx / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP)
            .coerceIn(0f, (workspaceWidth - widgetWidth * scale).coerceAtLeast(0f))
        val snappedDy = ((dy / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP)
            .coerceIn(0f, (workspaceHeight - widgetHeight * scale).coerceAtLeast(0f))
        dx = snappedDx
        dy = snappedDy
        onCommit(placement.copy(dx = snappedDx, dy = snappedDy, scale = scale))
    }

    Box(modifier = modifier.offset(
        dx.coerceIn(0f, (workspaceWidth - widgetWidth * scale).coerceAtLeast(0f)).dp,
        dy.coerceIn(0f, (workspaceHeight - widgetHeight * scale).coerceAtLeast(0f)).dp
    )) {
        Box(
            modifier = Modifier
                .onSizeChanged {
                    widgetWidth = it.width / density
                    widgetHeight = it.height / density
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            content()
            if (showControls) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                )
            }
        }

        if (showControls) {
            if (dragViaHandle) {
                HandleSurface(
                    icon = Icons.Default.OpenWith,
                    description = "Tasi",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset((-6).dp, (-6).dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { commitSnapped() }) { change, drag ->
                                change.consume()
                                dx += drag.x.toDp().value
                                dy += drag.y.toDp().value
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
                    description = "Boyutlandir",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 6.dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { commitSnapped() }) { change, drag ->
                                change.consume()
                                val delta = (drag.x + drag.y).toDp().value / 180f
                                scale = (scale + delta).coerceIn(MIN_SCALE, MAX_SCALE)
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
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
