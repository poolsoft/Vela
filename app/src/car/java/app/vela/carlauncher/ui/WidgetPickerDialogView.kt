package app.vela.carlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import app.vela.carlauncher.widgets.BaseWidget
import app.vela.carlauncher.widgets.WidgetManager
import app.vela.carlauncher.widgets.WidgetRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DialogBg = Color(0xFA17181F)
private val CardBg = Color(0x1AFFFFFF)
private val CardBorder = Color(0x22FFFFFF)
private val TextSecondary = Color(0x99FFFFFF)
private val ButtonBg = Color(0x33FFFFFF)

data class LocalWidgetModel(
    val typeId: String,
    val title: String,
    val sizeText: String,
    val defaultSize: BaseWidget.WidgetSize,
    val spanX: Int,
    val spanY: Int,
    val previewType: String
)

data class InstalledAppShortcut(
    val packageName: String,
    val label: String,
    val iconDrawable: Drawable?
)

data class SystemWidgetGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val widgets: List<AppWidgetProviderInfo>
)

/**
 * Kullanici gorseline birebir uygun modern "Widget Kutuphanesi" Secicisi.
 * - Car Launcher Widget'lari (Canvas tabanli renkli ikonlar)
 * - Car Launcher Kisayollari
 * - Uygulama Kisayollari
 * - Sistem Uygulama Widget'lari
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun WidgetPickerDialogView(
    activePageIndex: Int,
    onDismiss: () -> Unit,
    onWidgetAdded: () -> Unit
) {
    val context = LocalContext.current
    val widgetManager = remember { WidgetManager.getInstance(context) }
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }

    var pendingAppWidgetId by remember { mutableIntStateOf(-1) }
    var pendingProviderInfo by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    // Sistem Widget Config Launcher
    val configLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingAppWidgetId != -1) {
            val provider = pendingProviderInfo
            val spanX = calculateSpan(provider?.minWidth ?: 100, 80)
            val spanY = calculateSpan(provider?.minHeight ?: 100, 80)
            widgetManager.addSystemWidget(
                appWidgetId = pendingAppWidgetId,
                providerTitle = provider?.loadLabel(context.packageManager) ?: "Sistem Widget",
                packageName = provider?.provider?.packageName ?: "",
                pageIndex = activePageIndex,
                spanX = spanX,
                spanY = spanY
            )
            onWidgetAdded()
            onDismiss()
        } else if (pendingAppWidgetId != -1) {
            widgetManager.appWidgetHost.deleteAppWidgetId(pendingAppWidgetId)
        }
        pendingAppWidgetId = -1
        pendingProviderInfo = null
    }

    // Sistem Widget Bind Izin Launcher
    val bindLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        val provider = pendingProviderInfo
        if (result.resultCode == Activity.RESULT_OK && id != -1 && provider != null) {
            if (provider.configure != null) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = provider.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                configLauncher.launch(intent)
            } else {
                val spanX = calculateSpan(provider.minWidth, 80)
                val spanY = calculateSpan(provider.minHeight, 80)
                widgetManager.addSystemWidget(
                    appWidgetId = id,
                    providerTitle = provider.loadLabel(context.packageManager) ?: "Sistem Widget",
                    packageName = provider.provider.packageName,
                    pageIndex = activePageIndex,
                    spanX = spanX,
                    spanY = spanY
                )
                onWidgetAdded()
                onDismiss()
                pendingAppWidgetId = -1
                pendingProviderInfo = null
            }
        } else if (id != -1) {
            widgetManager.appWidgetHost.deleteAppWidgetId(id)
            pendingAppWidgetId = -1
            pendingProviderInfo = null
        }
    }

    // Yerel Car Launcher Widget Tanımları (Görseldeki gibi)
    val localWidgets = remember {
        listOf(
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_CLOCK,
                title = "Dijital Saat",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "clock"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_SPEED,
                title = "Hız & Limit",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "speed"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_COMPASS,
                title = "Pusula & Yön",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "direction"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_WEATHER,
                title = "Hava Durumu",
                sizeText = "2x1",
                defaultSize = BaseWidget.WidgetSize.MEDIUM,
                spanX = 2,
                spanY = 1,
                previewType = "weather"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_MUSIC,
                title = "Medya Çalar",
                sizeText = "2x2",
                defaultSize = BaseWidget.WidgetSize.LARGE,
                spanX = 2,
                spanY = 2,
                previewType = "music"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_COMBINED,
                title = "Dashboard (Saat + Hız)",
                sizeText = "4x2",
                defaultSize = BaseWidget.WidgetSize.LARGE,
                spanX = 4,
                spanY = 2,
                previewType = "navigation"
            ),
            LocalWidgetModel(
                typeId = WidgetRegistry.TYPE_OBD,
                title = "OBD2 / Araç",
                sizeText = "2x2",
                defaultSize = BaseWidget.WidgetSize.LARGE,
                spanX = 2,
                spanY = 2,
                previewType = "obd"
            )
        )
    }

    // Car Launcher Kısayolları
    val internalShortcuts = remember {
        listOf(
            LocalWidgetModel(
                typeId = "shortcut_settings",
                title = "Car Launcher Ayarları",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "settings"
            ),
            LocalWidgetModel(
                typeId = "shortcut_music",
                title = "Müzik Çalıcı",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "music"
            ),
            LocalWidgetModel(
                typeId = "shortcut_radio",
                title = "Anten / Radyo",
                sizeText = "1x1",
                defaultSize = BaseWidget.WidgetSize.SMALL,
                spanX = 1,
                spanY = 1,
                previewType = "antenna"
            )
        )
    }

    var appShortcuts by remember { mutableStateOf<List<InstalledAppShortcut>>(emptyList()) }
    var systemWidgetGroups by remember { mutableStateOf<List<SystemWidgetGroup>>(emptyList()) }

    // Asenkron yuklu uygulamalari ve sistem widget'larini cek
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager

            // Uygulama Kisayollari
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
            val appsList = resolvedApps.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)
                InstalledAppShortcut(pkg, label, icon)
            }.sortedBy { it.label }
            appShortcuts = appsList

            // Sistem Widget'lari (AppWidgetManager)
            try {
                val providers = appWidgetManager.installedProviders
                val grouped = providers.groupBy { it.provider.packageName }.mapNotNull { (pkg, list) ->
                    val appLabel = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    val appIcon = try {
                        pm.getApplicationIcon(pkg)
                    } catch (e: Exception) {
                        null
                    }
                    SystemWidgetGroup(pkg, appLabel, appIcon, list)
                }.sortedBy { it.appLabel }
                systemWidgetGroups = grouped
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(18.dp)),
            color = DialogBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                // Header (Baslik & X Butonu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Widget Kütüphanesi",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Scrollable Icerik
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    // 1. CAR LAUNCHER WIDGET'LARI
                    item {
                        Text(
                            text = "Car Launcher Widget'ları",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(localWidgets) { item ->
                                WidgetCardItem(
                                    title = item.title,
                                    sizeText = item.sizeText,
                                    previewType = item.previewType,
                                    onAddClick = {
                                        widgetManager.addWidget(
                                            typeId = item.typeId,
                                            pageIndex = activePageIndex,
                                            size = item.defaultSize,
                                            spanX = item.spanX,
                                            spanY = item.spanY
                                        )
                                        onWidgetAdded()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)
                    }

                    // 2. CAR LAUNCHER KISAYOLLARI
                    item {
                        Text(
                            text = "Car Launcher Kısayolları",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(internalShortcuts) { item ->
                                WidgetCardItem(
                                    title = item.title,
                                    sizeText = item.sizeText,
                                    previewType = item.previewType,
                                    onAddClick = {
                                        widgetManager.addWidget(
                                            typeId = item.typeId,
                                            pageIndex = activePageIndex,
                                            size = item.defaultSize,
                                            spanX = item.spanX,
                                            spanY = item.spanY
                                        )
                                        onWidgetAdded()
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)
                    }

                    // 3. UYGULAMA KISAYOLLARI
                    if (appShortcuts.isNotEmpty()) {
                        item {
                            Text(
                                text = "Uygulama Kısayolları",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(appShortcuts) { app ->
                                    AppShortcutCardItem(
                                        app = app,
                                        onAddClick = {
                                            widgetManager.addShortcut(
                                                packageName = app.packageName,
                                                label = app.label,
                                                pageIndex = activePageIndex
                                            )
                                            onWidgetAdded()
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }

                        item {
                            Divider(color = Color(0x1AFFFFFF), thickness = 1.dp)
                        }
                    }

                    // 4. SISTEM UYGULAMA WIDGET'LARI
                    item {
                        Text(
                            text = "Sistem Uygulama Widget'ları",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (systemWidgetGroups.isEmpty()) {
                        item {
                            Text(
                                text = "Yüklü sistem widget'ı bulunamadı.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    } else {
                        items(systemWidgetGroups) { group ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                // Uygulama Baslik & Ikonu
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                ) {
                                    val iconBmp = remember(group.appIcon) {
                                        try { group.appIcon?.toBitmap(48, 48)?.asImageBitmap() } catch (e: Exception) { null }
                                    }
                                    if (iconBmp != null) {
                                        Image(
                                            bitmap = iconBmp,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = group.appLabel,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Sag kaydirmali widget listesi
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(group.widgets) { provider ->
                                        val pm = context.packageManager
                                        val label = provider.loadLabel(pm) ?: "Widget"
                                        val spanX = calculateSpan(provider.minWidth, 80)
                                        val spanY = calculateSpan(provider.minHeight, 80)

                                        SystemWidgetCardItem(
                                            label = label,
                                            sizeText = "${spanX}x${spanY}",
                                            provider = provider,
                                            onAddClick = {
                                                try {
                                                    val host = widgetManager.appWidgetHost
                                                    val appWidgetId = host.allocateAppWidgetId()
                                                    pendingAppWidgetId = appWidgetId
                                                    pendingProviderInfo = provider

                                                    val options = Bundle().apply {
                                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, provider.minWidth)
                                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, provider.minHeight)
                                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, provider.minWidth)
                                                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, provider.minHeight)
                                                    }

                                                    val allowed = try {
                                                        appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider, options)
                                                    } catch (e: Exception) {
                                                        false
                                                    }

                                                    if (allowed) {
                                                        if (provider.configure != null) {
                                                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                                                component = provider.configure
                                                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                                            }
                                                            configLauncher.launch(intent)
                                                        } else {
                                                            widgetManager.addSystemWidget(
                                                                appWidgetId = appWidgetId,
                                                                providerTitle = label,
                                                                packageName = group.packageName,
                                                                pageIndex = activePageIndex,
                                                                spanX = spanX,
                                                                spanY = spanY
                                                            )
                                                            onWidgetAdded()
                                                            onDismiss()
                                                        }
                                                    } else {
                                                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                                                        }
                                                        bindLauncher.launch(intent)
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Sistem widgeti eklenemedi: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
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
}

private fun calculateSpan(sizePx: Int, cellSizePx: Int): Int {
    if (cellSizePx <= 0) return 1
    val span = Math.round(sizePx.toFloat() / cellSizePx.toFloat())
    return span.coerceIn(1, 4)
}

/**
 * Car Launcher standart widget karti
 */
@Composable
fun WidgetCardItem(
    title: String,
    sizeText: String,
    previewType: String,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Canvas Tabanli Vektorel Onizleme Kutusu (Gorseldeki gibi)
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x15FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            WidgetVectorCanvas(type = previewType)
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = sizeText,
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Ekle Butonu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ButtonBg)
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ekle",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Uygulama Kisayol Karti
 */
@Composable
fun AppShortcutCardItem(
    app: InstalledAppShortcut,
    onAddClick: () -> Unit
) {
    val bitmap = remember(app.iconDrawable) {
        try { app.iconDrawable?.toBitmap(72, 72)?.asImageBitmap() } catch (e: Exception) { null }
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x15FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = app.label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Kısayol",
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ButtonBg)
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ekle",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Sistem Widget Karti (Izgara cizimli Canvas onizleme)
 */
@Composable
fun SystemWidgetCardItem(
    label: String,
    sizeText: String,
    provider: AppWidgetProviderInfo,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Izgara Deseni Cizilen Kutu (Gorseldeki gibi)
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x221E1F29)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val stepX = size.width / 3f
                val stepY = size.height / 3f
                val gridColor = Color(0x33FFFFFF)
                for (i in 1..2) {
                    drawLine(gridColor, Offset(i * stepX, 0f), Offset(i * stepX, size.height), 1f)
                    drawLine(gridColor, Offset(0f, i * stepY), Offset(size.width, i * stepY), 1f)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = sizeText,
            color = TextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ButtonBg)
                .clickable { onAddClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ekle",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Gorseldeki birebir Canvas tabanli vektorel onizleme cizimi
 */
@Composable
fun WidgetVectorCanvas(type: String) {
    Canvas(modifier = Modifier.size(46.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val strokeWidth = 2.5.dp.toPx()

        when (type) {
            "clock" -> {
                // Turuncu Saat
                val color = Color(0xFFFF9800)
                drawCircle(color = color, radius = 16.dp.toPx(), style = Stroke(width = strokeWidth))
                drawLine(color = color, start = Offset(cx, cy), end = Offset(cx, cy - 8.dp.toPx()), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx, cy), end = Offset(cx + 6.dp.toPx(), cy), strokeWidth = strokeWidth)
            }
            "speed" -> {
                // Yesil Hiz Gostergesi Yayi
                val color = Color(0xFF4CAF50)
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - 16.dp.toPx(), cy - 14.dp.toPx()),
                    size = Size(32.dp.toPx(), 28.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
                drawLine(color = color, start = Offset(cx, cy + 2.dp.toPx()), end = Offset(cx + 10.dp.toPx(), cy - 10.dp.toPx()), strokeWidth = strokeWidth)
            }
            "direction" -> {
                // Neon Turkuaz Pusula & Kirmizi-Beyaz Ok
                val ringColor = Color(0xFF00E5FF)
                drawCircle(color = ringColor, radius = 16.dp.toPx(), style = Stroke(width = strokeWidth))

                val pathN = Path().apply {
                    moveTo(cx, cy - 11.dp.toPx())
                    lineTo(cx - 4.dp.toPx(), cy)
                    lineTo(cx + 4.dp.toPx(), cy)
                    close()
                }
                drawPath(pathN, color = Color(0xFFFF3333))

                val pathS = Path().apply {
                    moveTo(cx, cy + 11.dp.toPx())
                    lineTo(cx - 4.dp.toPx(), cy)
                    lineTo(cx + 4.dp.toPx(), cy)
                    close()
                }
                drawPath(pathS, color = Color.White)
            }
            "weather" -> {
                // Mavi Gunes & Beyaz Bulut
                drawCircle(color = Color(0xFF2196F3), radius = 8.dp.toPx(), center = Offset(cx - 5.dp.toPx(), cy - 5.dp.toPx()), style = Stroke(width = strokeWidth))
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cx + 3.dp.toPx(), cy + 2.dp.toPx()))
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(cx - 10.dp.toPx(), cy),
                    size = Size(20.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
            "music" -> {
                // Pembe Nota
                val color = Color(0xFFE91E63)
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(cx - 4.dp.toPx(), cy + 5.dp.toPx()))
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(cx + 6.dp.toPx(), cy + 3.dp.toPx()))
                drawLine(color = color, start = Offset(cx - 2.dp.toPx(), cy + 5.dp.toPx()), end = Offset(cx - 2.dp.toPx(), cy - 6.dp.toPx()), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx + 8.dp.toPx(), cy + 3.dp.toPx()), end = Offset(cx + 8.dp.toPx(), cy - 8.dp.toPx()), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx - 2.dp.toPx(), cy - 6.dp.toPx()), end = Offset(cx + 8.dp.toPx(), cy - 8.dp.toPx()), strokeWidth = strokeWidth)
            }
            "navigation" -> {
                // Mor Yon Oku
                val color = Color(0xFF9C27B0)
                val navPath = Path().apply {
                    moveTo(cx - 8.dp.toPx(), cy + 8.dp.toPx())
                    lineTo(cx - 8.dp.toPx(), cy - 4.dp.toPx())
                    quadraticBezierTo(cx - 8.dp.toPx(), cy - 8.dp.toPx(), cx - 4.dp.toPx(), cy - 8.dp.toPx())
                    lineTo(cx + 6.dp.toPx(), cy - 8.dp.toPx())
                }
                drawPath(navPath, color = color, style = Stroke(width = strokeWidth))
                val head = Path().apply {
                    moveTo(cx + 5.dp.toPx(), cy - 12.dp.toPx())
                    lineTo(cx + 11.dp.toPx(), cy - 8.dp.toPx())
                    lineTo(cx + 5.dp.toPx(), cy - 4.dp.toPx())
                    close()
                }
                drawPath(head, color = color)
            }
            "obd" -> {
                // Amber Motor
                val color = Color(0xFFFFD54F)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - 8.dp.toPx(), cy - 6.dp.toPx()),
                    size = Size(16.dp.toPx(), 12.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
            }
            "settings" -> {
                // Mavi Dişli Çark
                drawCircle(color = Color(0xFF0A84FF), radius = 12.dp.toPx(), style = Stroke(width = strokeWidth))
                drawCircle(color = Color(0xFF0A84FF), radius = 4.dp.toPx())
            }
            "antenna" -> {
                // Yeşil Radyo Çubukları
                val color = Color(0xFF34C759)
                drawLine(color = color, start = Offset(cx - 8.dp.toPx(), cy + 8.dp.toPx()), end = Offset(cx - 8.dp.toPx(), cy + 4.dp.toPx()), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx - 3.dp.toPx(), cy + 8.dp.toPx()), end = Offset(cx - 3.dp.toPx(), cy), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx + 2.dp.toPx(), cy + 8.dp.toPx()), end = Offset(cx + 2.dp.toPx(), cy - 4.dp.toPx()), strokeWidth = strokeWidth)
                drawLine(color = color, start = Offset(cx + 7.dp.toPx(), cy + 8.dp.toPx()), end = Offset(cx + 7.dp.toPx(), cy - 8.dp.toPx()), strokeWidth = strokeWidth)
            }
        }
    }
}
