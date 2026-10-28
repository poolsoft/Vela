package app.vela.carlauncher.map

import android.content.Context
import android.os.*
import app.vela.ui.map.MapRenderJson
import app.vela.ui.map.MapRenderScene
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPInputStream

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
            // GZIPOutputStream does not expose the deflate level, and the level-6 default costs
            // real CPU on low-end units where this runs per scene update. Same gzip framing the
            // JDK uses (raw deflater + header + CRC32/ISIZE trailer) with BEST_SPEED compression.
            val crc = CRC32()
            var count = 0L
            FileOutputStream(file).use { out ->
                out.write(GZIP_HEADER)
                val deflater = Deflater(Deflater.BEST_SPEED, true)
                try {
                    val deflated = DeflaterOutputStream(out, deflater, 512)
                    val bounded = object : OutputStream() {
                        override fun write(value: Int) {
                            require(++count <= MAX_BYTES) { "scene exceeds limit" }
                            crc.update(value)
                            deflated.write(value)
                        }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            require(length <= MAX_BYTES - count) { "scene exceeds limit" }
                            count += length
                            crc.update(bytes, offset, length)
                            deflated.write(bytes, offset, length)
                        }
                    }
                    MapRenderJson.json.encodeToStream(scene, bounded)
                    deflated.finish()
                    deflated.flush()
                    writeLe32(out, crc.value.toInt())
                    writeLe32(out, (count and 0xFFFFFFFFL).toInt())
                } finally {
                    deflater.end()
                }
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } finally { file.delete() }
    }

    /** Little-endian gzip CRC32/ISIZE field, as GZIPInputStream expects. */
    private fun writeLe32(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    /** 1f 8b 08 00 00000000 04 ff: deflate stream with no optional fields. */
    private val GZIP_HEADER = byteArrayOf(
        0x1f.toByte(), 0x8b.toByte(), 0x08.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x04.toByte(), 0xff.toByte()
    )

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
