package app.vela.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Docs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Versioned, bounded portable format. Parts stay below FAT32's single-file limit. */
internal object BackupFiles {
    const val PART_BYTES = 256L * 1024 * 1024
    const val MAX_JSON = 16 * 1024 * 1024
    const val MANIFEST = "vela-backup.json"
    val roots = setOf("basemap", "places", "obf", "overlays", "poipacks", "glyphs", "sprites", "trips", "piper", "asr")
    val metadata = setOf("personal.json", "launcher.json")
    data class Entry(val path: String, val size: Long, val sha256: String, val parts: List<String>)
    data class Manifest(val roots: List<String>, val entries: List<Entry>, val created: String, val version: String) {
        val bytes: Long get() = entries.fold(0L) { sum, e -> Math.addExact(sum, e.size) }
        fun json(): String = JSONObject().put("format", "vela-offline-transfer").put("version", 1)
            .put("created", created).put("appVersion", version).put("roots", JSONArray(roots))
            .put("entries", JSONArray().also { a -> entries.forEach { e ->
                a.put(JSONObject().put("path", e.path).put("size", e.size).put("sha256", e.sha256).put("parts", JSONArray(e.parts)))
            } }).toString(2)
    }
    fun allowed(path: String): Boolean {
        if (path in metadata) return true
        if (path.length > 512 || '\\' in path || ':' in path || path.startsWith('/')) return false
        val pieces = path.split('/')
        return pieces.size in 2..12 && pieces.first() in roots && pieces.all {
            it.isNotBlank() && it != "." && it != ".." && it.none { c -> c.code < 32 }
        } && pieces.none { it.endsWith(".tmp") || it.endsWith(".staging") || it.endsWith(".compact") || it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith("-journal") }
    }
    fun parse(text: String): Manifest {
        require(text.toByteArray().size <= MAX_JSON)
        val o = JSONObject(text)
        require(o.getString("format") == "vela-offline-transfer" && o.get("version") == 1)
        val dirs = o.getJSONArray("roots").strings()
        require(dirs.isNotEmpty() || o.getJSONArray("entries").length() > 0)
        require(dirs.size == dirs.toSet().size && dirs.all { it in roots })
        val a = o.getJSONArray("entries")
        require(a.length() in 1..100000)
        val paths = hashSetOf<String>()
        val parts = hashSetOf<String>()
        val entries = (0 until a.length()).map { i ->
            val e = a.getJSONObject(i)
            val path = e.getString("path")
            val size = e.getLong("size")
            require(allowed(path) && paths.add(path) && size in 0L..(1024L * 1024 * 1024 * 1024))
            require(path in metadata || path.substringBefore('/') in dirs)
            require(path !in metadata || size <= MAX_JSON)
            val hash = e.getString("sha256")
            require(hash.matches(Regex("[0-9a-f]{64}")))
            val pieces = e.getJSONArray("parts").strings()
            require(pieces.size.toLong() == (size + PART_BYTES - 1) / PART_BYTES)
            require(pieces.all { it.matches(Regex("part-[0-9]{7}\\.bin")) && parts.add(it) })
            Entry(path, size, hash, pieces)
        }
        require(paths.isNotEmpty())
        // Reject a file used as another file's directory, independent of archive order.
        require(paths.none { path -> path.split('/').dropLast(1).runningReduce { a, b -> "$a/$b" }.any { it in paths } })
        val result = Manifest(dirs, entries, o.getString("created"), o.getString("appVersion"))
        require(result.bytes <= 1024L * 1024 * 1024 * 1024)
        return result
    }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    fun readBounded(input: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= MAX_JSON)
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }
    fun tree(uri: Uri): Uri = Docs.buildDocumentUriUsingTree(uri, Docs.getTreeDocumentId(uri))
    fun children(context: Context, folder: Uri): Map<String, Uri> {
        val result = linkedMapOf<String, Uri>()
        val uri = Docs.buildChildDocumentsUriUsingTree(folder, Docs.getDocumentId(folder))
        context.contentResolver.query(uri, arrayOf(Docs.Document.COLUMN_DOCUMENT_ID, Docs.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                require(!result.containsKey(name))
                result[name] = Docs.buildDocumentUriUsingTree(folder, c.getString(0))
            }
        }
        return result
    }
    fun create(context: Context, parent: Uri, name: String, directory: Boolean = false): Uri =
        checkNotNull(Docs.createDocument(context.contentResolver, parent, if (directory) Docs.Document.MIME_TYPE_DIR else "application/octet-stream", name))
    fun local(parent: File, relative: String): File {
        val out = File(parent, relative)
        require(out.canonicalPath.startsWith(parent.canonicalPath + File.separator))
        return out
    }
}
