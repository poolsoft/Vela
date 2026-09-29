package app.vela.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads a Vela neural-voice model (a sherpa-onnx `.tar.bz2` from the `tts-models` GitHub release)
 * into a target dir under `filesDir`, extracting it, reporting 0f..1f progress. Best-effort: any
 * failure wipes the partial model. (Named for the original Kokoro voice; today it fetches the Piper
 * catalog models, and the multi-part Kokoro/Matcha plumbing is gone with those voices.)
 */
@Singleton
class KokoroInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    http: OkHttpClient,
) {
    // The shared client caps a whole CALL at 12 s to bound a hung scrape — but that also kills a
    // multi-tens-of-MB model download (it can't finish the body in 12 s). Derive a download client with
    // NO overall call timeout; a generous per-read socket timeout still catches a truly stalled connection.
    private val downloadHttp: OkHttpClient = http.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    /** Download [url] into [destDir] (extracting the archive's single top-level folder into it).
     *  [onProgress] is 0f..1f. */
    suspend fun download(
        url: String,
        destDir: File,
        sizeEst: Long,
        fallbackUrl: String? = null,
        onExtracting: () -> Unit = {},
        active: () -> Boolean = { true },
        onProgress: (Float) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(context.filesDir, "voice.download.tmp")
        val staging = File(context.filesDir, "voice.staging")
        try {
            var streamed = stream(url, tmp, sizeEst, 0f, 1f, active, onProgress)
            if (!streamed && !fallbackUrl.isNullOrBlank() && active()) {
                android.util.Log.i("KokoroInstaller", "primary download failed, trying fallback: $fallbackUrl")
                streamed = stream(fallbackUrl, tmp, sizeEst, 0f, 1f, active, onProgress)
            }
            if (!streamed) return@withContext false

            onExtracting()
            destDir.parentFile?.mkdirs()
            staging.deleteRecursively(); staging.mkdirs()
            extractTar(tmp, staging)
            val inner = staging.listFiles()?.firstOrNull { it.isDirectory } ?: staging
            destDir.deleteRecursively()
            destDir.mkdirs()
            if (!inner.renameTo(destDir)) inner.copyRecursively(destDir, overwrite = true)

            onProgress(1f)
            true
        } catch (t: Throwable) {
            android.util.Log.e("KokoroInstaller", "download failed for $url", t)
            destDir.deleteRecursively()
            false
        } finally {
            tmp.delete()
            staging.deleteRecursively()
        }
    }

    /** Stream [url] to [out], reporting progress mapped into the [base, base+span] slice of the bar.
     *  [active] is checked per chunk - a user cancel aborts the read within ~64 KB. */
    private fun stream(url: String, out: File, sizeEst: Long, base: Float, span: Float, active: () -> Boolean, onProgress: (Float) -> Unit): Boolean = runCatching {
        downloadHttp.newCall(Request.Builder().url(url).header("User-Agent", "VelaMaps").build()).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) {
                android.util.Log.w("KokoroInstaller", "HTTP error ${resp.code} for $url")
                return@use false
            }
            val total = body.contentLength().takeIf { it > 0 } ?: sizeEst
            body.byteStream().use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    var read = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        if (!active()) return@use false
                        o.write(buf, 0, n)
                        read += n
                        onProgress((base + span * (read.toFloat() / total)).coerceIn(0f, base + span))
                    }
                }
            }
            true
        }
    }.getOrDefault(false)

    private fun extractTar(src: File, destDir: File) {
        // Pick the decompressor by magic bytes: Vela's own archives are gzip (bzip2 unpacked at
        // ~15 MB/s on a phone core, a visible half-minute hang for a model; gzip is ~10x that for a
        // slightly larger file), while the upstream Piper voice tarballs are still bzip2.
        val magic = ByteArray(2).also { m -> src.inputStream().use { it.read(m) } }
        val gzip = magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()
        src.inputStream().buffered().use { fin ->
            (if (gzip) GzipCompressorInputStream(fin) else BZip2CompressorInputStream(fin)).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    var entry = tar.nextEntry
                    while (entry != null) {
                        val out = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { tar.copyTo(it) }
                        }
                        entry = tar.nextEntry
                    }
                }
            }
        }
    }
}
