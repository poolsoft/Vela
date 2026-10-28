package app.vela.carlauncher.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.ui.VelaWindowDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import app.vela.carlauncher.widgets.BaseWidget
import app.vela.carlauncher.widgets.WidgetManager
import app.vela.carlauncher.widgets.WidgetRegistry
import org.json.JSONObject

private val ClDialogBg = Color(0xF5161826)
private val ClCardBg = Color(0x33FFFFFF)
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)
private val ClDanger = Color(0xFFFF453A)
private val ClSuccess = Color(0xFF30D158)

/**
 * Bir widget'in uzerine uzun basildiginda acilan Aksiyon Menusu ve Ayarlar Dialogu.
 * - Duzenle (Widget'a ozel ayarlar: saat formati, hiz birimi, vb.)
 * - Degistir (Baska bir widget ile ayni hucrede degistirme)
 * - Boyutlandir (Hazir boyut butonlari veya serbest tutamac modu)
 * - Kaldir (Masaustunden silme)
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun WidgetActionMenuDialog(
    widget: BaseWidget,
    onDismiss: () -> Unit,
    onOpenChangePicker: () -> Unit,
    onEnterResizeMode: () -> Unit,
    onDeleteWidget: () -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember { WidgetManager.getInstance(context) }
    var currentSubView by remember { mutableStateOf<String>("MENU") } // "MENU" veya "SETTINGS"

    // Mevcut widget config JSON
    val configJson = remember(widget.customConfig) {
        try {
            if (!widget.customConfig.isNullOrBlank()) JSONObject(widget.customConfig!!) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ClDialogBg)
                .border(1.dp, ClBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            if (currentSubView == "MENU") {
                // ═══════════════════════════════════════════════════════════
                // ANA AKSIYON MENUSU (DUZENLE, DEGISTIR, BOYUTLANDIR, KALDIR)
                // ═══════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Baslik ve Kapat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = widget.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${widget.spanX}x${widget.spanY} Hücre (Sayfa ${widget.pageIndex + 1})",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 1. Duzenle (Widget Ayarlari)
                    val hasSettings = widget.appWidgetId != -1 ||
                            widget.typeId == WidgetRegistry.TYPE_SPEED ||
                            widget.typeId == WidgetRegistry.TYPE_CLOCK ||
                            widget.typeId == WidgetRegistry.TYPE_WEATHER ||
                            widget.typeId == WidgetRegistry.TYPE_COMPASS ||
                            widget.typeId == WidgetRegistry.TYPE_COMBINED

                    if (hasSettings) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ClPrimary.copy(alpha = 0.15f))
                                .border(1.dp, ClPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable { currentSubView = "SETTINGS" }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = ClPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Düzenle (Widget Ayarları)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Birimler, saat formatı veya widget özellikleri",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // 2. Degistir (Baska bir widget ile)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ClCardBg)
                            .border(1.dp, ClBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onOpenChangePicker()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Başka Widget ile Değiştir",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Aynı hücre konumuna başka bir widget yerleştirin",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 3. Boyutlandir Moduna Gir
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ClCardBg)
                            .border(1.dp, ClBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onEnterResizeMode()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Boyutlandır",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Hücre tutamaçları ile genişletin veya küçültün",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 4. Kaldir (Sil)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x18FF453A))
                            .border(1.dp, ClDanger.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable {
                                onDismiss()
                                onDeleteWidget()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = ClDanger,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Masaüstünden Kaldır",
                                color = ClDanger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Widget'ı masaüstünden siler ve hücreyi boşaltır",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // ═══════════════════════════════════════════════════════════
                // WIDGET OZEL AYARLAR SAYFASI (SETTINGS SUB-VIEW)
                // ═══════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${widget.title} Ayarları",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { currentSubView = "MENU" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✕", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // Sistem Widget'i ise: Android Resmi ACTION_APPWIDGET_CONFIGURE
                    if (widget.appWidgetId != -1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClPrimary.copy(alpha = 0.2f))
                                .border(1.dp, ClPrimary, RoundedCornerShape(12.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.appWidgetId)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                        onDismiss()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sistem Uygulama Ayarlarını Aç",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Saat Widget Ayarlari: 24h/12h formati
                    if (widget.typeId == WidgetRegistry.TYPE_CLOCK || widget.typeId == WidgetRegistry.TYPE_COMBINED) {
                        val is24h = configJson.optBoolean("is24h", true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "24 Saat Formatı", color = Color.White, fontSize = 13.sp)
                                Text(text = if (is24h) "24 Saat (21:30)" else "12 Saat AM/PM (09:30 PM)", color = Color.Gray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = is24h,
                                onCheckedChange = { checked ->
                                    configJson.put("is24h", checked)
                                    widgetManager.updateWidgetConfig(widget.id, configJson.toString())
                                }
                            )
                        }
                    }

                    // Hiz Widget Ayarlari: km/h veya mph
                    if (widget.typeId == WidgetRegistry.TYPE_SPEED || widget.typeId == WidgetRegistry.TYPE_COMBINED) {
                        val unit = configJson.optString("speedUnit", "KMH")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Hız Birimi", color = Color.White, fontSize = 13.sp)
                                Text(text = if (unit == "KMH") "Kilometre / Saat (km/h)" else "Mil / Saat (mph)", color = Color.Gray, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val isKmh = unit == "KMH"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isKmh) ClPrimary else Color(0x22FFFFFF))
                                        .clickable {
                                            configJson.put("speedUnit", "KMH")
                                            widgetManager.updateWidgetConfig(widget.id, configJson.toString())
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "KM/H", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isKmh) ClPrimary else Color(0x22FFFFFF))
                                        .clickable {
                                            configJson.put("speedUnit", "MPH")
                                            widgetManager.updateWidgetConfig(widget.id, configJson.toString())
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "MPH", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Hava Durumu Widget Ayarlari: °C veya °F
                    if (widget.typeId == WidgetRegistry.TYPE_WEATHER) {
                        val tempUnit = configJson.optString("tempUnit", "C")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Sıcaklık Birimi", color = Color.White, fontSize = 13.sp)
                                Text(text = if (tempUnit == "C") "Celsius (°C)" else "Fahrenheit (°F)", color = Color.Gray, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val isC = tempUnit == "C"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isC) ClPrimary else Color(0x22FFFFFF))
                                        .clickable {
                                            configJson.put("tempUnit", "C")
                                            widgetManager.updateWidgetConfig(widget.id, configJson.toString())
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "°C", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isC) ClPrimary else Color(0x22FFFFFF))
                                        .clickable {
                                            configJson.put("tempUnit", "F")
                                            widgetManager.updateWidgetConfig(widget.id, configJson.toString())
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = "°F", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bitti / Kaydet Butonu
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ClSuccess)
                            .clickable {
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tamam",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
