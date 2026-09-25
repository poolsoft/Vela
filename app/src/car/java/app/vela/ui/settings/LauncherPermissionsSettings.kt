package app.vela.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.vela.R
import app.vela.ui.settings.PageIntro

private val ClPrimary = Color(0xFF0A84FF)
private val ClCardBg = Color(0xFF1C1C1E)
private val ClDivider = Color(0xFF2C2C2E)
private val ClSuccess = Color(0xFF34C759)
private val ClTextSecondary = Color(0xFF8E8E93)

/** Modern kart ve status rozetli Launcher İzinler ve Cihaz Ekranı */
@Composable
internal fun LauncherPermissionsSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val systemScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { revision++ }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun open(intent: Intent) {
        runCatching { systemScreen.launch(intent) }.onFailure {
            android.widget.Toast.makeText(context, R.string.car_tools_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val appUri = Uri.parse("package:${context.packageName}")

    // İzin durumları
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val micPermission = Manifest.permission.RECORD_AUDIO
    val btPermission = if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else null
    val notifPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null

    val isAudioGranted = remember(revision) { ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED }
    val isMicGranted = remember(revision) { ContextCompat.checkSelfPermission(context, micPermission) == PackageManager.PERMISSION_GRANTED }
    val isBtGranted = remember(revision) { btPermission == null || ContextCompat.checkSelfPermission(context, btPermission) == PackageManager.PERMISSION_GRANTED }
    val isNotifGranted = remember(revision) { notifPermission == null || ContextCompat.checkSelfPermission(context, notifPermission) == PackageManager.PERMISSION_GRANTED }

    val isOverlayGranted = remember(revision) { Settings.canDrawOverlays(context) }
    val isWriteSettingsGranted = remember(revision) { Settings.System.canWrite(context) }
    val isNotificationAccessGranted = remember(revision) {
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    val isHomeRoleHeld = remember(revision) {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true && roles.isRoleHeld(RoleManager.ROLE_HOME)
        } else false
    }

    SettingsScaffold(title = stringResource(R.string.car_permissions_title), onBack = onBack) { topRow ->
        Spacer(Modifier.height(4.dp))
        PageIntro(stringResource(R.string.car_permissions_sub))
        Spacer(Modifier.height(10.dp))

        // KART 1: Medya ve Donanım İzinleri
        PermissionCategoryCard(
            title = "Medya ve Donanım İzinleri",
            icon = Icons.Default.MusicNote,
            modifier = topRow
        ) {
            // Müzik Kütüphanesi
            PermissionRow(
                title = stringResource(R.string.car_permission_audio),
                desc = "Yerel ses dosyalarını ve albüm kapaklarını taramak için gereklidir.",
                icon = Icons.Default.LibraryMusic,
                granted = isAudioGranted,
                onClick = {
                    if (isAudioGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                    else permission.launch(audioPermission)
                }
            )

            // Mikrofon & Görselleştirici
            PermissionRow(
                title = stringResource(R.string.car_permission_microphone),
                desc = "Müzik çalarken ses dalgaları ve ritim efektlerini çizmek için.",
                icon = Icons.Default.Mic,
                granted = isMicGranted,
                onClick = {
                    if (isMicGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                    else permission.launch(micPermission)
                }
            )

            // Bluetooth
            if (btPermission != null) {
                PermissionRow(
                    title = stringResource(R.string.car_permission_bt),
                    desc = "Araç içi Bluetooth medya cihazlarına bağlanmak ve durumu izlemek için.",
                    icon = Icons.Default.Bluetooth,
                    granted = isBtGranted,
                    onClick = {
                        if (isBtGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                        else permission.launch(btPermission)
                    }
                )
            }

            // Bildirimler
            if (notifPermission != null) {
                PermissionRow(
                    title = stringResource(R.string.car_permission_notifications),
                    desc = "Navigasyon dönüş yönergelerini ve medya kontrollerini bildirimde göstermek için.",
                    icon = Icons.Default.Notifications,
                    granted = isNotifGranted,
                    showDivider = false,
                    onClick = {
                        if (isNotifGranted) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri))
                        else permission.launch(notifPermission)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // KART 2: Gelişmiş Sistem İzinleri
        PermissionCategoryCard(
            title = "Gelişmiş Sistem İzinleri",
            icon = Icons.Default.Tune
        ) {
            // Yüzen Pencere
            PermissionRow(
                title = stringResource(R.string.car_permission_overlay),
                desc = "Yüzen hız göstergesi ve haritayı diğer uygulamaların üzerinde göstermek için.",
                icon = Icons.Default.Layers,
                granted = isOverlayGranted,
                onClick = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri)) }
            )

            // Harici Oynatıcı / Bildirim Dinleyici
            PermissionRow(
                title = stringResource(R.string.car_permission_listener),
                desc = "Spotify vb. harici oynatıcıların parça bilgilerini dock çubuğunda göstermek için.",
                icon = Icons.Default.QueueMusic,
                granted = isNotificationAccessGranted,
                onClick = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            )

            // Sistem Ayarlarını Değiştirme
            PermissionRow(
                title = stringResource(R.string.car_permission_settings),
                desc = "Ekran parlaklığı ve sistem ses düzeylerini doğrudan launcher'dan ayarlamak için.",
                icon = Icons.Default.Tune,
                granted = isWriteSettingsGranted,
                showDivider = false,
                onClick = { open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, appUri)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // KART 3: Sistem ve Başlatıcı Yapılandırması
        PermissionCategoryCard(
            title = "Sistem Entegrasyonu",
            icon = Icons.Default.Home
        ) {
            // Varsayılan Ana Ekran
            ActionRow(
                title = stringResource(R.string.car_permission_home),
                desc = "Home butonuna basıldığında veya araç açıldığında Vela'yı başlatmak için.",
                icon = Icons.Default.Home,
                badgeText = if (isHomeRoleHeld) "✓ Seçili" else "Seç →",
                isPositive = isHomeRoleHeld,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val roles = context.getSystemService(RoleManager::class.java)
                        if (roles?.isRoleAvailable(RoleManager.ROLE_HOME) == true && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                            open(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        } else {
                            open(Intent(Settings.ACTION_HOME_SETTINGS))
                        }
                    } else {
                        open(Intent(Settings.ACTION_HOME_SETTINGS))
                    }
                }
            )

            // Android Uygulama Ayarları
            ActionRow(
                title = stringResource(R.string.car_permission_app_settings),
                desc = "Pil optimizasyonu, arka plan kısıtlamaları ve detaylı izinler.",
                icon = Icons.Default.Settings,
                badgeText = "Yönet →",
                isPositive = false,
                showDivider = false,
                onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)) }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Başlıklı ve ikonlu modern ayar kategorisi kartı */
@Composable
private fun PermissionCategoryCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ClCardBg)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x1F0A84FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ClPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(color = ClDivider)
        content()
    }
}

/** Kart içi tıklanabilir ve rozetli izin satırı */
@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    granted: Boolean,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (granted) Color(0x1A34C759) else Color(0x1A0A84FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) ClSuccess else ClPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = ClTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (granted) Color(0x2234C759) else Color(0x220A84FF))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (granted) "✓ Verildi" else "İzin Ver →",
                    color = if (granted) ClSuccess else ClPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp),
                color = ClDivider
            )
        }
    }
}

/** Kart içi tıklanabilir aksiyon satırı (Varsayılan Home / Ayarlar vb.) */
@Composable
private fun ActionRow(
    title: String,
    desc: String,
    icon: ImageVector,
    badgeText: String,
    isPositive: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPositive) Color(0x1A34C759) else Color(0x1AFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPositive) ClSuccess else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = ClTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPositive) Color(0x2234C759) else Color(0x220A84FF))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = if (isPositive) ClSuccess else ClPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 66.dp),
                color = ClDivider
            )
        }
    }
}

/** Smart focus preferences - music automatic tracking */
@Composable
fun SmartFocusPreferences() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_music_focus", Context.MODE_PRIVATE) }
    var follow by remember { mutableStateOf(prefs.getBoolean("auto_follow", true)) }
    var source by remember { mutableStateOf(prefs.getString("startup_source", "last") ?: "last") }
    var seconds by remember { mutableIntStateOf(prefs.getInt("bt_wait_seconds", 8).coerceIn(3, 15)) }
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(stringResource(R.string.car_smart_focus))
        Text(stringResource(R.string.car_smart_focus_desc))
        Switch(checked = follow, onCheckedChange = {
            follow = it
            prefs.edit().putBoolean("auto_follow", it).apply()
            if (it) app.vela.carlauncher.media.MusicManager.getInstance(context).allowAutomaticSourceTracking()
        })
        val keys = listOf("last", "internal", "bluetooth", "hcn_radio", "xy_radio")
        val labels = listOf(stringResource(R.string.car_source_last), stringResource(R.string.car_source_internal), "Bluetooth", "HCN Radio", "XYAuto Radio")
        Text(stringResource(R.string.car_startup_source))
        keys.forEachIndexed { index, key ->
            Button(onClick = { source = key; prefs.edit().putString("startup_source", key).apply() }, modifier = Modifier.fillMaxWidth()) {
                Text((if (source == key) "✓ " else "") + labels[index])
            }
        }
        Text(stringResource(R.string.car_bt_wait, seconds))
        androidx.compose.material3.Slider(value = seconds.toFloat(), onValueChange = {
            seconds = it.toInt()
            prefs.edit().putInt("bt_wait_seconds", seconds).apply()
        }, valueRange = 3f..15f, steps = 11)
    }
}
