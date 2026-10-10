package app.vela.offline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipInputStream

/**
 * The map's fonts and sprite on disk, so the basemap renders with no signal. A labeled vector tile
 * only completes once every glyph range it needs and the style's sprite have RESOLVED; with a
 * remote host that never answers, every such tile stays incomplete and the map is blank. Serving
 * them from `file://` fixes that (`asset://` hung the same way as the network on this engine).
 *
 * The glyph pack is the `map-fonts` release (the Roboto-over-Noto set Vela already serves from its
 * GitHub Pages online), unzipped into `files/glyphs/<stack>/<range>.pbf`; it rides along with the
 * first basemap download. The sprite is bundled in the APK and copied into `files/sprites/` once.
 */
object GlyphPackStore {
    private const val PACK_URL = "https://github.com/PimpinPumpkin/Vela/releases/download/map-fonts/map-fonts.zip"
    private val mutex = Mutex()

    private fun glyphRoot(context: Context) = File(context.filesDir, "glyphs")
    private fun spriteRoot(context: Context) = File(context.filesDir, "sprites")

    /** The bundled Liberty asset asks for Roboto stacks; the pack (and the live style) carry the
     *  composited set under the Noto names. */
    fun stackName(font: String): String = when (font) {
        "Roboto Regular" -> "Noto Sans Regular"
        "Roboto Italic" -> "Noto Sans Italic"
        "Roboto Medium", "Roboto Bold" -> "Noto Sans Bold"
        else -> font
    }

    fun installed(context: Context): Boolean = missingRequiredFiles(context).isEmpty()

    /** Latin and Latin Extended cover Turkish labels; check all stacks used by Liberty. */
    fun missingRequiredFiles(context: Context): List<String> = missingRequiredFiles(glyphRoot(context))
    private fun missingRequiredFiles(root: File): List<String> = buildList {
        for (stack in listOf("Noto Sans Regular", "Noto Sans Italic", "Noto Sans Bold")) {
            for (range in listOf("0-255", "256-511")) {
                val path = "$stack/$range.pbf"
                if (File(root, path).let { !it.isFile || it.length() == 0L }) add(path)
            }
        }
    }

    fun localGlyphUrl(context: Context) = "file://${glyphRoot(context).absolutePath}/{fontstack}/{range}.pbf"

    /** The style `glyphs` template for the installed pack, or null when it is not on disk. */
    fun glyphUrl(context: Context): String? =
        if (installed(context)) localGlyphUrl(context) else null

    /** The style `sprite` base for the copied sprite, or null if the copy failed. */
    fun spriteUrl(context: Context): String? {
        ensureSprite(context)
        val required = listOf("ofm.json", "ofm.png", "ofm@2x.json", "ofm@2x.png")
        return if (required.all { File(spriteRoot(context), it).let { f -> f.isFile && f.length() > 0L } })
            "file://${spriteRoot(context).absolutePath}/ofm" else null
    }

    /** Copy the bundled sprite (OpenFreeMap's, 4 files, ~230 KB) into files/ once. */
    fun ensureSprite(context: Context) {
        val root = spriteRoot(context)
        if (listOf("ofm.json", "ofm.png", "ofm@2x.json", "ofm@2x.png").all {
                File(root, it).let { f -> f.isFile && f.length() > 0L }
            }) return
        runCatching {
            root.mkdirs()
            for (name in listOf("ofm.json", "ofm.png", "ofm@2x.json", "ofm@2x.png")) {
                context.assets.open("sprites/$name").use { input -> File(root, name).outputStream().use { input.copyTo(it) } }
            }
        }
    }

    /** Download and unzip the glyph pack if it is not installed. True when installed afterwards. */
    suspend fun ensureInstalled(context: Context, http: OkHttpClient): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (installed(context)) return@withLock true
            if (!app.vela.ui.OfflineMode.networkAllowed) return@withLock false
            val root = glyphRoot(context)
            val tmp = File(context.cacheDir, "map-fonts.zip.tmp")
            val client = http.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).readTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
            runCatching {
                client.newCall(Request.Builder().url(PACK_URL).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                }
                val staging = File(context.cacheDir, "glyphs-staging").apply { deleteRecursively(); mkdirs() }
                ZipInputStream(tmp.inputStream().buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.isDirectory || !e.name.endsWith(".pbf")) { zip.closeEntry(); continue }
                        // "<stack>/<range>.pbf", possibly under a top folder in the archive
                        val parts = e.name.split('/').filter { it.isNotBlank() }
                        if (parts.size < 2) { zip.closeEntry(); continue }
                        val out = File(staging, "${parts[parts.size - 2]}/${parts.last()}")
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                        zip.closeEntry()
                    }
                }
                val missing = missingRequiredFiles(staging)
                check(missing.isEmpty()) { "Incomplete glyph pack: $missing" }
                root.deleteRecursively()
                check(staging.renameTo(root)) { "rename failed" }
                tmp.delete()
                android.util.Log.i("VelaBasemap", "glyph pack installed")
                true
            }.getOrElse { tmp.delete(); android.util.Log.w("VelaBasemap", "glyph pack install failed", it); false }
        }
    }

    fun delete(context: Context) {
        glyphRoot(context).deleteRecursively()
    }
}
