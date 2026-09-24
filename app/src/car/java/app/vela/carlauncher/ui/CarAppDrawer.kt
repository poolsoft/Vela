package app.vela.carlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.InternalApp

private val ClCardBg = Color(0xCC1A1B22)
private val ClPrimary = Color(0xFF0A84FF)

/**
 * OsmAnd fragment_app_drawer.xml Uyumlu Gelismis Arac Uygulama Cekmecesi.
 * - Arama cubugu ile anlik filtreleme
 * - Dahili (Internal) ve Harici uygulamalar
 * - Uzun basma ile Dock'a kisayol ekleme
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarAppDrawer(
    onKapat: () -> Unit,
    onUygulamaSecildi: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appManager = remember { CarAppManager.getInstance(context) }
    val dockManager = remember { AppDockManager.getInstance(context) }

    var tumUygulamalar by remember { mutableStateOf<List<AracUygulamasi>>(emptyList()) }
    var aramaMetni by remember { mutableStateOf("") }
    var yukleniyor by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        tumUygulamalar = appManager.yukluUygulamalariGetir()
        yukleniyor = false
    }

    val filtrelenmisUygulamalar = remember(tumUygulamalar, aramaMetni) {
        if (aramaMetni.isBlank()) {
            tumUygulamalar
        } else {
            tumUygulamalar.filter { it.ad.contains(aramaMetni, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF50D0E12))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Ust Bar: Baslik, Arama Kutusu ve Kapatma Butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uygulama Çekmecesi (${filtrelenmisUygulamalar.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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

            // Arama Kutusu
            TextField(
                value = aramaMetni,
                onValueChange = { aramaMetni = it },
                placeholder = { Text("Uygulama ara...", color = Color.Gray) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ClCardBg,
                    unfocusedContainerColor = ClCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ClPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            if (yukleniyor) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ClPrimary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtrelenmisUygulamalar) { uygulama ->
                        UygulamaGridKarti(
                            uygulama = uygulama,
                            onClick = {
                                if (uygulama.isInternal) {
                                    onUygulamaSecildi(uygulama.paketAdi)
                                } else {
                                    appManager.uygulamayiBaslat(uygulama.paketAdi)
                                    onKapat()
                                }
                            },
                            onLongClick = {
                                val eklendi = dockManager.kisayolEkle(uygulama.paketAdi)
                                val mesaj = if (eklendi) "${uygulama.ad} Dock'a eklendi" else "${uygulama.ad} zaten Dock'ta var"
                                Toast.makeText(context, mesaj, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UygulamaGridKarti(
    uygulama: AracUygulamasi,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val bitmap = remember(uygulama.ikon) {
            uygulama.ikon?.let { drawableToBitmap(it) }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2C34)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = uygulama.ad,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = uygulama.ad,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
