package app.vela.carlauncher.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DesktopAppearance(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val revision by LauncherStartup.preferencesChanged.collectAsState()
    val prefs = remember { context.getSharedPreferences("vela_launcher_tools", 0) }
    val uri = remember(revision) { prefs.getString("wallpaper_uri", null) }
    val scale = remember(revision) { prefs.getFloat("text_scale", 1f).coerceIn(0.8f, 1.4f) }
    val image by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val source = Uri.parse(uri ?: return@runCatching null)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
                options.inJustDecodeBounds = false
                context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        image?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale)) { content() }
    }
}
