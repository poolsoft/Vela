package app.vela.carlauncher.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.desktop.WidgetIds
import app.vela.carlauncher.model.AracUygulamasi

private data class DesktopWidgetChoice(val id: String, val title: String, val symbol: String, val size: String)

@Composable
fun DesktopWidgetPicker(
    activeWidgets: Set<String>,
    onDismiss: () -> Unit,
    onAddWidget: (String) -> Unit,
    onAddShortcut: (AracUygulamasi) -> Unit,
    onAddSystemWidget: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AracUygulamasi>>(emptyList()) }
    LaunchedEffect(Unit) { apps = CarAppManager.getInstance(context).yukluUygulamalariGetir() }
    val providers = remember {
        runCatching { AppWidgetManager.getInstance(context).installedProviders }
            .getOrDefault(emptyList())
            .groupBy { it.provider.packageName }
            .toSortedMap(compareBy { pkg ->
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            })
    }
    val localWidgets = remember {
        listOf(
            DesktopWidgetChoice(WidgetIds.COMBINED, "Saat + Hız", "◴", "340 × 120"),
            DesktopWidgetChoice(WidgetIds.SPEEDOMETER, "Hız göstergesi", "◉", "240 × 160"),
            DesktopWidgetChoice(WidgetIds.CLOCK, "Dijital saat", "22:55", "260 × 100"),
            DesktopWidgetChoice(WidgetIds.MUSIC, "Müzik çalar", "♫", "300 × 160"),
            DesktopWidgetChoice(WidgetIds.WEATHER, "Hava durumu", "☀", "170 × 90"),
            DesktopWidgetChoice(WidgetIds.COMPASS, "Pusula", "◇", "170 × 90"),
            DesktopWidgetChoice(WidgetIds.OBD, "OBD2 verileri", "OBD", "280 × 90"),
            DesktopWidgetChoice(WidgetIds.STATUS, "Sistem durumu", "▥", "280 × 140"),
            DesktopWidgetChoice(WidgetIds.DOCK, "Uygulama dock'u", "•••", "340 × 76")
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color(0xFF15161A),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Widget Kütüphanesi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Masaüstüne eklemek için bir öğe seçin", color = Color(0xFF9A9CA5), fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Kapat", tint = Color.White) }
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    item {
                        PickerSection("Vela araç widget’ları") {
                            items(localWidgets) { widget ->
                                WidgetChoiceCard(
                                    title = widget.title,
                                    subtitle = widget.size,
                                    disabled = false,
                                    preview = {
                                        Text(widget.symbol, color = Color(0xFF00E5FF), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                    },
                                    onAdd = { onAddWidget(widget.id) }
                                )
                            }
                        }
                    }
                    if (apps.isNotEmpty()) {
                        item {
                            PickerSection("Uygulama kısayolları") {
                                items(apps) { app ->
                                    val bitmap = remember(app.paketAdi) { app.ikon?.toBitmap(72, 72)?.asImageBitmap() }
                                    WidgetChoiceCard(
                                        title = app.ad,
                                        subtitle = "Kısayol",
                                        disabled = false,
                                        preview = {
                                            if (bitmap != null) Image(bitmap, app.ad, Modifier.size(56.dp))
                                            else Text(app.ad.take(1), color = Color.White, fontSize = 28.sp)
                                        },
                                        onAdd = { onAddShortcut(app) }
                                    )
                                }
                            }
                        }
                    }
                    providers.forEach { (packageName, widgets) ->
                        item {
                            val appLabel = remember(packageName) {
                                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
                                    .getOrDefault(packageName)
                            }
                            PickerSection(appLabel) {
                                items(widgets) { info ->
                                    val title = remember(info) { info.loadLabel(pm) ?: "Widget" }
                                    val preview = remember(info) {
                                        runCatching {
                                            (info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
                                                ?: info.loadIcon(context, context.resources.displayMetrics.densityDpi))
                                                ?.toBitmap(240, 130)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    WidgetChoiceCard(
                                        title = title,
                                        subtitle = "${info.minWidth.coerceAtLeast(1)} × ${info.minHeight.coerceAtLeast(1)}",
                                        preview = {
                                            if (preview != null) Image(preview, title, Modifier.fillMaxWidth().height(82.dp))
                                            else Icon(Icons.Default.Widgets, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(44.dp))
                                        },
                                        onAdd = { onAddSystemWidget(info) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSection(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun WidgetChoiceCard(
    title: String,
    subtitle: String,
    disabled: Boolean = false,
    preview: @Composable () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        color = Color(0xFF24252A),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.width(190.dp).height(205.dp)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF33343A)),
                contentAlignment = Alignment.Center
            ) { preview() }
            Spacer(Modifier.height(8.dp))
            Text(title, color = if (disabled) Color.Gray else Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF9A9CA5), fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onAdd,
                enabled = !disabled,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF45464C)),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) { Text(if (disabled) "Eklendi" else "Ekle") }
        }
    }
}
