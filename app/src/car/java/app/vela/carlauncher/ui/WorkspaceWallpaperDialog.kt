package app.vela.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vela.carlauncher.tools.LauncherStartup

private val ClDialogBg = Color(0xF5161826)
private val ClCardBg = Color(0x33FFFFFF)
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF0A84FF)
private val ClDanger = Color(0xFFFF453A)

/**
 * Masaustu bos alana basildiginda acilan Masaustu Secenekleri ve Duvar Kagidi Dialogu.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun WorkspaceWallpaperDialog(
    onDismiss: () -> Unit,
    onOpenWidgetPicker: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("vela_launcher_tools", Context.MODE_PRIVATE)

    val wallpaperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            prefs.edit().putString("wallpaper_uri", uri.toString()).apply()
            LauncherStartup.preferencesChanged.value++
            onDismiss()
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Baslik ve Kapat Butonu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Masaüstü Seçenekleri",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
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

                // Secenek 1: Widget Ekle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClPrimary.copy(alpha = 0.2f))
                        .border(1.dp, ClPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable {
                            onDismiss()
                            onOpenWidgetPicker()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = ClPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Yeni Widget Ekle",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Masaüstüne saat, hız veya uygulama widget'ı ekleyin",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Secenek 2: Galeriden Duvar Kagidi Sec
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ClCardBg)
                        .border(1.dp, ClBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            wallpaperLauncher.launch(arrayOf("image/*"))
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            text = "Duvar Kâğıdı Değiştir",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cihaz galerisinden veya dosyalardan özel resim seçin",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Secenek 3: Duvar Kagidini Kaldir (Varsayilana Don)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x15FF453A))
                        .border(1.dp, ClDanger.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .clickable {
                            prefs.edit().remove("wallpaper_uri").apply()
                            LauncherStartup.preferencesChanged.value++
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            text = "Duvar Kâğıdını Kaldır",
                            color = ClDanger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Varsayılan koyu gradyan otomotiv temasına dön",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
