package app.vela.offline

import com.mapbox.mapboxsdk.module.http.HttpRequestUtil
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import org.json.JSONObject

/** v10 has no native PMTiles protocol. Intercept only our reserved URLs; ordinary map HTTP
 * requests still use OkHttp. Local archives never need a network connection or a TCP server. */
object PmtilesMapBridge {
    private const val HOST = "vela-pmtiles.invalid"
    private val network = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val archives = object : LinkedHashMap<String, PmtilesTileSource>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PmtilesTileSource>?) = size > 8
    }
    private var installed = false

    @Synchronized fun install() {
        if (installed) return
        // Native HTTP requests are otherwise suspended before reaching OkHttp when offline.
        // Our reserved HTTP host reads disk; keep that dispatcher enabled even without Wi-Fi.
        // Real remote URLs still fail normally through OkHttp's bounded call timeout.
        com.mapbox.mapboxsdk.Mapbox.setConnected(true)
        app.vela.util.FileLogger.i("PmtilesBridge", "Local HTTP dispatcher enabled independently of network connectivity")
        HttpRequestUtil.setOkHttpClient(network.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != HOST) return@addInterceptor chain.proceed(request)
            val started = System.nanoTime()
            try {
                val parts = request.url.pathSegments
                val uri = String(Base64.getUrlDecoder().decode(parts.first()), Charsets.UTF_8)
                val stamp = if (uri.startsWith("file:")) File(URI(uri)).let { "${it.length()}:${it.lastModified()}" } else "remote"
                val key = "$uri|$stamp"
                val source = synchronized(archives) {
                    archives[key]?.takeUnless { it.expired() } ?: PmtilesTileSource { offset, length ->
                        readRange(uri, offset, length)
                    }.also {
                        archives[key] = it
                        app.vela.util.FileLogger.i("PmtilesBridge", "Archive opened: $uri bytes=$stamp zoom=${it.header.minZoom}..${it.header.maxZoom}")
                    }
                }
                val body: ByteArray?
                val type: String
                if (parts.size == 1) {
                    val json = if (source.header.metaLength > 0) JSONObject(String(source.metadata(), Charsets.UTF_8)) else JSONObject()
                    json.put("tilejson", "2.2.0").put("scheme", "xyz")
                        .put("minzoom", source.header.minZoom).put("maxzoom", source.header.maxZoom)
                        .put("tiles", org.json.JSONArray().put("${request.url.newBuilder().query(null).build()}/{z}/{x}/{y}.pbf"))
                    body = json.toString().toByteArray(); type = "application/json"
                } else {
                    require(parts.size == 4) { "Invalid PMTiles tile URL" }
                    body = source.tile(parts[1].toInt(), parts[2].toInt(), parts[3].removeSuffix(".pbf").toInt())
                    type = "application/vnd.mapbox-vector-tile"
                }
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                if (elapsedMs >= 250) app.vela.util.FileLogger.w("PmtilesBridge",
                    "Slow local resource: ${elapsedMs}ms bytes=${body?.size ?: 0} path=${request.url.encodedPath}")
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(if (body == null) 404 else 200).message(if (body == null) "Not Found" else "OK")
                    .header("Cache-Control", "max-age=300")
                    .body((body ?: ByteArray(0)).toResponseBody(type.toMediaType())).build()
            } catch (error: Exception) {
                app.vela.util.FileLogger.e("PmtilesBridge", "Local resource failed: ${request.url}", error)
                throw IOException("PMTiles resource could not be read", error)
            }
        }.build())
        installed = true
    }

    fun sourceUrl(uri: String): String {
        if (!uri.startsWith("pmtiles://")) return uri
        val archive = uri.removePrefix("pmtiles://")
        require(archive.startsWith("file:") || archive.startsWith("https://") || archive.startsWith("http://"))
        return "https://$HOST/" + Base64.getUrlEncoder().withoutPadding().encodeToString(archive.toByteArray())
    }

    private fun readRange(uri: String, offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length > 0 && length <= PmtilesTileSource.MAX_BYTES && offset <= Long.MAX_VALUE - length)
        if (uri.startsWith("file:")) {
            return RandomAccessFile(File(URI(uri)), "r").use { file ->
                require(offset + length <= file.length()) { "PMTiles range outside archive" }
                file.seek(offset); ByteArray(length).also { file.readFully(it) }
            }
        }
        val end = offset + length - 1
        val request = Request.Builder().url(uri).header("Range", "bytes=$offset-$end")
            .header("Accept-Encoding", "identity").build()
        return network.newCall(request).execute().use { response ->
            if (response.code != 206 || !response.header("Content-Range").orEmpty().startsWith("bytes $offset-$end/")) {
                throw IOException("PMTiles server did not honor byte range: HTTP ${response.code}")
            }
            val input = response.body?.byteStream() ?: throw IOException("Empty PMTiles response")
            val bytes = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(bytes, read, length - read)
                if (count < 0) throw IOException("Truncated PMTiles range")
                read += count
            }
            bytes
        }
    }
}

/** Small bounded directory cache; tile bodies remain in the map SDK's own cache. */
internal class PmtilesTileSource(private val range: (Long, Int) -> ByteArray) {
    companion object { const val MAX_BYTES = 4 * 1024 * 1024 }
    private val created = System.nanoTime()
    val header = PmtilesReader.header(range(0, 127)) ?: throw IOException("Invalid PMTiles v3 header")
    private val directories = object : LinkedHashMap<Pair<Long, Long>, List<PmtilesReader.Entry>>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Long>, List<PmtilesReader.Entry>>?) = size > 8
    }
    fun expired() = System.nanoTime() - created > TimeUnit.MINUTES.toNanos(5)
    fun metadata() = inflate(read(header.metaOffset, header.metaLength), header.internalCompression)

    fun tile(z: Int, x: Int, y: Int): ByteArray? {
        require(z in 0..30 && x >= 0 && y >= 0 && x.toLong() < 1L.shl(z) && y.toLong() < 1L.shl(z))
        if (z !in header.minZoom..header.maxZoom) return null
        val wanted = PmtilesReader.tileId(z, x, y)
        var offset = header.rootOffset; var length = header.rootLength
        repeat(4) {
            val key = offset to length
            // Serialize only directory cache misses; independent tile reads/decompression run in parallel.
            val directory = synchronized(directories) {
                directories[key] ?: (PmtilesReader.decodeDirectory(
                    inflate(read(offset, length), header.internalCompression), 1) ?: throw IOException("Invalid PMTiles directory")
                    ).also { directories[key] = it }
            }
            val entry = PmtilesReader.find(directory, wanted) ?: return null
            if (entry.runLength > 0) return inflate(read(header.tileDataOffset + entry.offset, entry.length), header.tileCompression)
            offset = header.leafOffset + entry.offset; length = entry.length
        }
        throw IOException("PMTiles directory nesting exceeds limit")
    }

    private fun read(offset: Long, length: Long): ByteArray {
        require(offset >= 0 && length in 1..MAX_BYTES.toLong() && offset <= Long.MAX_VALUE - length)
        return range(offset, length.toInt())
    }
    private fun inflate(raw: ByteArray, compression: Int): ByteArray = when (compression) {
        1 -> raw
        2 -> GZIPInputStream(raw.inputStream()).use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > MAX_BYTES) throw IOException("PMTiles decoded data exceeds limit")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        else -> throw IOException("Unsupported PMTiles compression $compression")
    }
}