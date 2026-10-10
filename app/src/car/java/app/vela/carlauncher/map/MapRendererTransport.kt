package app.vela.carlauncher.map

import android.content.Context
import android.os.*
import app.vela.ui.map.MapRenderJson
import app.vela.ui.map.MapRenderScene
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Geometry travels via a descriptor, never a multi-megabyte Binder parcel. */
internal object MapRendererTransport {
    const val OPEN = 10
    const val SCENE = 11
    const val TOUCH = 12
    const val CLOSE = 13
    const val STAGE = 14
    const val EVENT = 15
    const val KEY = 16
    const val HEARTBEAT = 17
    const val SUSPEND = 18
    const val MAX_BYTES = 16 * 1024 * 1024

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun write(context: Context, scene: MapRenderScene): ParcelFileDescriptor {
        val file = File.createTempFile("map-scene-", ".gz", context.cacheDir)
        try {
            GZIPOutputStream(file.outputStream()).use { compressed ->
                var count = 0
                val bounded = object : java.io.OutputStream() {
                    override fun write(value: Int) {
                        require(++count <= MAX_BYTES) { "scene exceeds limit" }
                        compressed.write(value)
                    }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        require(length <= MAX_BYTES - count) { "scene exceeds limit" }
                        count += length
                        compressed.write(bytes, offset, length)
                    }
                }
                MapRenderJson.json.encodeToStream(scene, bounded)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun read(fd: ParcelFileDescriptor): MapRenderScene =
        GZIPInputStream(ParcelFileDescriptor.AutoCloseInputStream(fd)).use { input ->
            var count = 0
            val bounded = object : java.io.InputStream() {
                override fun read(): Int {
                    val result = input.read()
                    if (result >= 0) require(++count <= MAX_BYTES) { "scene exceeds limit" }
                    return result
                }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    val result = input.read(bytes, offset, length)
                    if (result > 0) {
                        require(result <= MAX_BYTES - count) { "scene exceeds limit" }
                        count += result
                    }
                    return result
                }
            }
            MapRenderJson.json.decodeFromStream<MapRenderScene>(bounded)
        }

}

internal object MapRendererEvents {
    var listener: ((String) -> Unit)? = null
    fun report(stage: String) {
        if (stage in setOf("library", "map-create", "style-loading"))
            app.vela.diag.ProcessDiagnostics.checkpointAndFlush("renderer: $stage")
        else app.vela.diag.ProcessDiagnostics.checkpoint("renderer: $stage")
        app.vela.util.FileLogger.i("MapRenderer", stage)
        listener?.invoke(stage)
    }
}
