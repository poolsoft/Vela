package app.vela.carlauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.carlauncher.model.HizTelemetrisi

private val ClCardBg = Color(0xDD14151C)
private val ClBorder = Color(0x24FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)
private val ClNeonBlue = Color(0xFF00E5FF)

/**
 * OsmAnd DashboardFragment ve NeonDashboardActivity Birebir Karsiligi.
 * Spor ve Füturistik Dijital Hız Göstergesi, Telemetri ve Sürüş İstatistikleri.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarDashboardView(
    telemetri: HizTelemetrisi,
    onKapat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedHiz by animateFloatAsState(targetValue = telemetri.anlikHizKmh.toFloat())

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08090D))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Ust Bar: Baslik ve Kapat Butonu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DİJİTAL SPOR GÖSTERGE",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onKapat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ORTA KISIM: BUYUK NEON / SPOR KADRAN
            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Kadran Cizimi (Canvas Arc)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val sweepAngle = (animatedHiz / 240f).coerceIn(0f, 1f) * 260f

                    // Arka Plan Yay
                    drawArc(
                        color = Color(0x22FFFFFF),
                        startAngle = 140f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Neon Hiz Yayi
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(ClPrimary, ClNeonBlue, Color(0xFFFF5252))
                        ),
                        startAngle = 140f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Hiz Metni
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${animatedHiz.toInt()}",
                        color = if (telemetri.hizSiniriAsildiMi) Color(0xFFFF5252) else Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "KM/H",
                        color = ClNeonBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            // ALT TELEMETRI KARTLARI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetriKutusu(
                    baslik = "Ortalama",
                    deger = "${telemetri.ortalamaHizKmh} km/h",
                    ikon = Icons.Default.Timer,
                    modifier = Modifier.weight(1f)
                )

                TelemetriKutusu(
                    baslik = "İrtifa",
                    deger = "${telemetri.irtifaMetre.toInt()} m",
                    ikon = Icons.Default.Thermostat,
                    modifier = Modifier.weight(1f)
                )

                TelemetriKutusu(
                    baslik = "Pusula",
                    deger = "${telemetri.pusulaYonu.toInt()}°",
                    ikon = Icons.Default.ElectricMeter,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetriKutusu(
    baslik: String,
    deger: String,
    ikon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ClCardBg)
            .border(1.dp, ClBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = ikon, contentDescription = null, tint = ClPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = baslik, color = Color.Gray, fontSize = 11.sp)
            Text(text = deger, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
