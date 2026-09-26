package app.vela.carlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.core.model.ManeuverType
import app.vela.core.model.RoundaboutGeometry
import app.vela.ui.formatArrivalClock
import app.vela.ui.formatDistance
import app.vela.ui.formatDuration
import app.vela.ui.nav.isRoundabout
import app.vela.ui.nav.maneuverIcon
import app.vela.ui.nav.rememberRoundaboutGlyph

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import app.vela.ui.formatSpeed
import app.vela.ui.formatSpeedLimit

/**
 * Otomotiv ve bolunmus ekran (split-screen) icin kompakt yatay manevra karti.
 * Haritanin ust alanini kapatmaz, dikey FAB butonlarina carpmaz.
 */
@Composable
fun CarCompactManeuverBanner(
    text: String,
    distanceMeters: Double,
    type: ManeuverType,
    roundabout: RoundaboutGeometry? = null,
    nextText: String? = null,
    nextType: ManeuverType? = null,
    nextRoundabout: RoundaboutGeometry? = null,
    nextDistanceMeters: Double? = null,
    offRoute: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasNext = nextType != null && nextDistanceMeters != null && nextDistanceMeters <= 600.0

    Surface(
        color = Color(0xE614161D),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x33448AFF)),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (hasNext) 7.dp else 9.dp)
        ) {
            // 1. Satir: Gecerli Manevra
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Yonlendirme Oku (Neon Vurgulu)
                val icon = if (isRoundabout(type)) rememberRoundaboutGlyph(roundabout) else maneuverIcon(type)
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (offRoute) Color(0xFFFFB300) else Color(0xFF00FFC4),
                    modifier = Modifier.size(36.dp)
                )

                Spacer(Modifier.width(10.dp))

                // Mesafe
                Text(
                    if (offRoute) "Yeniden hesaplanıyor…" else formatDistance(distanceMeters),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(Modifier.width(10.dp))

                // Cadde / Manevra Yonergesi
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E4E9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            // 2. Satir: Sonraki Manevra (sol altta kompakt gosterim)
            if (hasNext && nextType != null && nextDistanceMeters != null) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        "Sonra",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF90CAF9)
                    )
                    Spacer(Modifier.width(6.dp))
                    val nextIcon = if (isRoundabout(nextType)) rememberRoundaboutGlyph(nextRoundabout) else maneuverIcon(nextType)
                    Icon(
                        nextIcon,
                        contentDescription = null,
                        tint = Color(0xFF90CAF9),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatDistance(nextDistanceMeters),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!nextText.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            nextText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB0B3C0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Otomotiv ve bolunmus ekran (split-screen) icin sag ust hiz ve hiz limiti widget'i.
 */
@Composable
fun CarSpeedWidget(
    speedMps: Float?,
    limitKmh: Double?,
    imperial: Boolean,
    modifier: Modifier = Modifier
) {
    val shownSpeed by animateFloatAsState(
        targetValue = (speedMps ?: 0f).let { if (it < 0.4f) 0f else it },
        animationSpec = tween(durationMillis = 600),
        label = "car_speed",
    )
    val (value, unit) = formatSpeed(shownSpeed)
    val speedDisp = if (imperial) shownSpeed * 2.236936f else shownSpeed * 3.6f
    val limitDisp = limitKmh?.let { formatSpeedLimit(it).first }
    val tol = if (imperial) 3f else 5f
    val over = limitDisp != null && speedDisp > limitDisp + tol
    val overColor = Color(0xFFE8514A)

    Surface(
        color = Color(0xE614161D),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (over) Color(0x99E8514A) else Color(0x33448AFF)),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Hiz Limiti Tabelasi (Avrupa / Turkiye standardi: Kirmizi daire icinde siyah sayi, beyaz arkaplan)
            if (limitDisp != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(3.5.dp, Color(0xFFD32F2F)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "$limitDisp",
                            color = if (over) overColor else Color(0xFF202124),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Anlik Hiz Gostergesi
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$value",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp,
                    color = if (over) overColor else Color.White
                )
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFB0B3C0),
                    lineHeight = 11.sp
                )
            }
        }
    }
}

/**
 * Otomotiv ve bolunmus ekran (split-screen) icin kompakt alt ETA bilgi cubugu.
 * En alta yapisik, tek satir ve zarif bir surus telemetrisi sunar.
 */
@Composable
fun CarCompactEtaBar(
    remainingDistanceMeters: Double,
    remainingSeconds: Double,
    offRoute: Boolean,
    paused: Boolean = false,
    onStop: () -> Unit,
    onPause: (() -> Unit)? = null,
    onSteps: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = {
            android.util.Log.d("CarCompactEtaBar", "Surface clicked! onSteps: $onSteps")
            onSteps?.invoke()
        },
        enabled = onSteps != null,
        color = Color(0xE614161D),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0x33448AFF)),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rotayi Bitir Butonu (Kirmizi Kapsul)
            Surface(
                onClick = onStop,
                shape = CircleShape,
                color = Color(0xFFD32F2F),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Rotayı Bitir",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Kalan Sure, Mesafe ve Varis Saati
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    formatDuration(remainingSeconds),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (offRoute) Color(0xFFFFB300) else Color(0xFF00FF9D)
                )

                Text(
                    " • ",
                    color = Color(0x66FFFFFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Text(
                    formatDistance(remainingDistanceMeters),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    " • ",
                    color = Color(0x66FFFFFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Text(
                    formatArrivalClock(remainingSeconds),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = Color(0xFFB0B3C0)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Duraklat Butonu
            if (onPause != null) {
                Surface(
                    onClick = onPause,
                    shape = CircleShape,
                    color = Color(0x22FFFFFF),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Duraklat / Sürdür",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }

            // Yol Tarifi / Adimlar Acma Butonu (Yukari Ok)
            if (onSteps != null) {
                Surface(
                    onClick = onSteps,
                    shape = CircleShape,
                    color = Color(0x22FFFFFF),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Yol Tarifi ve Adımlar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
