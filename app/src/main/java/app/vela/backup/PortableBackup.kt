package app.vela.backup

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import app.vela.BuildConfig
import app.vela.R
import app.vela.variant.CarIntegration
import app.vela.download.DownloadService
import app.vela.download.DownloadWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

/** SAF folder transport: bounded-memory streaming; no network and no broad storage permission. */
object PortableBackup {
    data class State(
        val busy: Boolean = false, val message: Int = 0, val detail: String = "",
        val done: Long = 0, val total: Long = 0, val review: Boolean = false, val ready: Boolean = false,
    )
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var reviewed: Pair<Uri, BackupFiles.Manifest>? = null
    private var lastProgress = 0L
    fun cancel() { job?.cancel() }
    fun dismissReview() { reviewed = null; mutable.value = State() }

    private class Failure(val resource: Int) : Exception()
    private fun checkStorage(context: Context, size: Long) {
        if (StatFs(context.filesDir.path).availableBytes < size + 64L * 1024 * 1024) throw Failure(R.string.backup_no_space)
    }
    private fun run(context: Context, work: suspend (Context) -> Unit) {
        if (job?.isActive == true || BackupRestore.pending(context)) return
        val app = context.applicationContext
        job = DownloadWork.scope.launch {
            mutable.value = State(busy = true, message = R.string.backup_working)
            val label = app.getString(R.string.backup_title)
            if (DownloadService.hasActiveWork()) {
                mutable.value = State(message = R.string.backup_wait_downloads)
                return@launch
            }
            DownloadService.begin(app, label)
            try {
                withContext(Dispatchers.IO) { work(app) }
            } catch (e: CancellationException) {
                mutable.value = State(message = R.string.backup_cancelled)
            } catch (e: Exception) {
                android.util.Log.w("PortableBackup", "Transfer failed", e)
                mutable.value = State(message = (e as? Failure)?.resource ?: R.string.backup_failed)
            } finally {
                DownloadService.end(app, label)
            }
        }
    }
    private fun progress(path: String, done: Long, total: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastProgress > 150 || done == total) {
            lastProgress = now
            mutable.value = State(true, R.string.backup_working, path, done, total)
        }
    }
    private data class Source(val path: String, val file: File?, val data: ByteArray?, val size: Long, val modified: Long)
    private fun inventory(context: Context): List<Source> = BackupFiles.roots.sorted().flatMap { root ->
        val dir = File(context.filesDir, root)
        if (!dir.exists()) emptyList() else {
            require(dir.isDirectory && dir.canonicalFile == dir.absoluteFile)
            dir.walkTopDown().filter { it.isFile }.mapNotNull { file ->
                val relative = "$root/" + file.relativeTo(dir).invariantSeparatorsPath
                if (!BackupFiles.allowed(relative)) null else {
                    require(file.canonicalFile == file.absoluteFile)
                    Source(relative, file, null, file.length(), file.lastModified())
                }
            }.toList()
        }
    }.sortedBy { it.path }
    fun export(context: Context, parentTree: Uri) = run(context) { app ->
        val revision = DownloadService.workRevision()
        val original = inventory(app)
        val dirs = BackupFiles.roots.filter { File(app.filesDir, it).isDirectory }.sorted()
        val personal = BackupPreferences.export(app).also { BackupPreferences.validate(it) }
        val launcher = LauncherBackupPort.export(app).also { LauncherBackupPort.validate(it) }
        val meta = mapOf("personal.json" to personal, "launcher.json" to launcher).map { (path, text) ->
            val bytes = text.toByteArray(Charsets.UTF_8)
            Source(path, null, bytes, bytes.size.toLong(), 0)
        }
        val sources = original + meta
        require(sources.size <= 100000)
        val total = sources.sumOf { it.size }
        val name = "Vela-backup-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now())
        val folder = BackupFiles.create(app, BackupFiles.tree(parentTree), name, directory = true)
        try {
            val entries = mutableListOf<BackupFiles.Entry>()
            val buffer = ByteArray(256 * 1024)
            var partId = 0
            var done = 0L
            for (source in sources) {
                currentCoroutineContext().ensureActive()
                val hash = MessageDigest.getInstance("SHA-256")
                val parts = mutableListOf<String>()
                (source.file?.inputStream() ?: ByteArrayInputStream(source.data!!)).use { input ->
                    var remaining = source.size
                    while (remaining > 0) {
                        val part = String.format(Locale.ROOT, "part-%07d.bin", ++partId)
                        require(partId <= 9999999)
                        val uri = BackupFiles.create(app, folder, part)
                        val chunk = minOf(remaining, BackupFiles.PART_BYTES)
                        app.contentResolver.openOutputStream(uri, "wt")!!.buffered().use { output ->
                            var left = chunk
                            while (left > 0) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                                check(n > 0)
                                hash.update(buffer, 0, n)
                                output.write(buffer, 0, n)
                                left -= n; done += n
                                progress(source.path, done, total)
                            }
                        }
                        remaining -= chunk
                        parts += part
                    }
                    check(input.read() == -1) // a growing recording is not a stable backup
                }
                entries += BackupFiles.Entry(source.path, source.size, hex(hash.digest()), parts)
            }
            currentCoroutineContext().ensureActive()
            if (DownloadService.workRevision() != revision || inventory(app) != original ||
                BackupPreferences.export(app) != personal || LauncherBackupPort.export(app) != launcher) throw Failure(R.string.backup_source_changed)
            val manifest = BackupFiles.Manifest(dirs, entries, Instant.now().toString(), BuildConfig.VERSION_NAME)
            val json = manifest.json().also { BackupFiles.parse(it) }
            val uri = BackupFiles.create(app, folder, BackupFiles.MANIFEST)
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            // Completion marker is last. Verify the provider actually persisted its contents.
            check(app.contentResolver.openInputStream(uri)!!.use { BackupFiles.readBounded(it) } == json)
            mutable.value = State(message = R.string.backup_exported, detail = name, done = total, total = total)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(app.contentResolver, folder) }
            throw e
        }
    }
    fun inspect(context: Context, tree: Uri) = run(context) { app ->
        reviewed = null
        val folder = BackupFiles.tree(tree)
        val docs = BackupFiles.children(app, folder)
        val uri = docs[BackupFiles.MANIFEST] ?: throw Failure(R.string.backup_wrong_folder)
        val manifest = app.contentResolver.openInputStream(uri)!!.use { BackupFiles.parse(BackupFiles.readBounded(it)) }
        require(manifest.entries.flatMap { it.parts }.all { it in docs })
        checkStorage(app, manifest.bytes)
        reviewed = folder to manifest
        val counts = manifest.entries.groupingBy { it.path.substringBefore('/') }.eachCount()
        mutable.value = State(message = R.string.backup_review, detail = buildString {
            append(manifest.created).append("\nVela ").append(manifest.version)
            append("\n").append(app.getString(R.string.backup_summary, manifest.entries.size, manifest.bytes / (1024 * 1024)))
            append("\n").append(counts.entries.joinToString { (root, count) ->
                val label = when (root) {
                    "basemap", "glyphs", "sprites", "overlays" -> app.getString(R.string.settings_map)
                    "places", "poipacks" -> app.getString(R.string.settings_places)
                    "obf" -> app.getString(R.string.settings_navigation)
                    "trips" -> app.getString(R.string.settings_save_trips)
                    "piper", "asr" -> app.getString(R.string.settings_voice)
                    "launcher.json" -> app.getString(CarIntegration.settingsTitle)
                    else -> app.getString(R.string.settings_saved_places)
                }
                "$label: $count"
            })
        }, total = manifest.bytes, review = true)
    }
    fun restore(context: Context) {
        val (folder, manifest) = reviewed ?: return
        reviewed = null
        run(context) { app ->
            val work = BackupRestore.work(app)
            try {
                BackupRestore.discard(app)
                val stage = File(work, "staged").apply { check(mkdirs()) }
                checkStorage(app, manifest.bytes)
                manifest.roots.forEach { check(File(stage, "files/$it").mkdirs()) }
                val docs = BackupFiles.children(app, folder)
                val buffer = ByteArray(256 * 1024)
                var done = 0L
                for (entry in manifest.entries) {
                    currentCoroutineContext().ensureActive()
                    val path = if (entry.path in BackupFiles.metadata) entry.path else "files/${entry.path}"
                    val out = BackupFiles.local(stage, path)
                    out.parentFile!!.mkdirs()
                    val hash = MessageDigest.getInstance("SHA-256")
                    FileOutputStream(out).use { output ->
                        var remaining = entry.size
                        for (part in entry.parts) {
                            val uri = docs[part] ?: throw Failure(R.string.backup_invalid)
                            app.contentResolver.openInputStream(uri)!!.use { input ->
                                var left = minOf(remaining, BackupFiles.PART_BYTES)
                                val chunk = left
                                while (left > 0) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                                    if (n <= 0) throw Failure(R.string.backup_invalid)
                                    hash.update(buffer, 0, n); output.write(buffer, 0, n)
                                    left -= n; done += n
                                    progress(entry.path, done, manifest.bytes)
                                }
                                if (input.read() != -1) throw Failure(R.string.backup_invalid)
                                remaining -= chunk
                            }
                        }
                        output.fd.sync()
                    }
                    if (hex(hash.digest()) != entry.sha256) throw Failure(R.string.backup_invalid)
                }
                BackupPreferences.validate(File(stage, "personal.json").readText())
                LauncherBackupPort.validate(File(stage, "launcher.json").readText())
                currentCoroutineContext().ensureActive()
                // Only verified, complete staging is eligible for activation on the next cold start.
                BackupRestore.atomic(File(work, "ready.json"), manifest.json())
                File(app.filesDir, "portable-restore-result").delete()
                mutable.value = State(message = R.string.backup_ready, ready = true, done = done, total = done)
            } catch (e: Exception) {
                BackupRestore.discard(app)
                throw e
            }
        }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
