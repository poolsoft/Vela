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
    enum class ModularCategory(
        val id: String,
        val titleRes: Int,
        val descRes: Int,
        val roots: Set<String>,
        val isPersonalMeta: Boolean = false,
        val isLauncherMeta: Boolean = false
    ) {
        MAPS("maps", R.string.car_backup_cat_maps, R.string.car_backup_cat_maps_desc, setOf("basemap", "glyphs", "sprites", "overlays")),
        ROUTING("routing", R.string.car_backup_cat_routing, R.string.car_backup_cat_routing_desc, setOf("obf")),
        ROUTES("routes", R.string.car_backup_cat_routes, R.string.car_backup_cat_routes_desc, setOf("trips")),
        PLACES("places", R.string.car_backup_cat_places, R.string.car_backup_cat_places_desc, setOf("places", "poipacks"), isPersonalMeta = true),
        LAUNCHER("launcher", R.string.car_backup_cat_launcher, R.string.car_backup_cat_launcher_desc, emptySet(), isLauncherMeta = true),
        VOICE("voice", R.string.settings_voice, R.string.car_backup_cat_nav_settings_desc, setOf("piper", "asr"));

        companion object {
            fun fromId(id: String) = values().firstOrNull { it.id == id }
            fun forEntryPath(path: String): ModularCategory? {
                if (path == "launcher.json") return LAUNCHER
                if (path == "personal.json") return PLACES
                val root = path.substringBefore('/')
                return values().firstOrNull { root in it.roots }
            }
        }
    }

    data class CategoryStat(
        val category: ModularCategory,
        val fileCount: Int,
        val totalBytes: Long
    )

    data class State(
        val busy: Boolean = false, val message: Int = 0, val detail: String = "",
        val done: Long = 0, val total: Long = 0, val review: Boolean = false, val ready: Boolean = false,
        val availableRestoreCategories: List<CategoryStat> = emptyList()
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
            mutable.value = mutable.value.copy(busy = true, message = R.string.backup_working, detail = path, done = done, total = total)
        }
    }

    fun scanDeviceCategories(context: Context): List<CategoryStat> {
        val app = context.applicationContext
        return ModularCategory.values().map { cat ->
            var count = 0
            var bytes = 0L
            for (root in cat.roots) {
                val dir = File(app.filesDir, root)
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = "$root/" + file.relativeTo(dir).invariantSeparatorsPath
                        if (BackupFiles.allowed(relative)) {
                            count++
                            bytes += file.length()
                        }
                    }
                }
            }
            if (cat.isPersonalMeta) {
                val text = runCatching { BackupPreferences.export(app) }.getOrNull()
                if (text != null) {
                    count++
                    bytes += text.toByteArray(Charsets.UTF_8).size
                }
            }
            if (cat.isLauncherMeta) {
                val text = runCatching { LauncherBackupPort.export(app) }.getOrNull()
                if (text != null) {
                    count++
                    bytes += text.toByteArray(Charsets.UTF_8).size
                }
            }
            CategoryStat(cat, count, bytes)
        }
    }

    private data class Source(val path: String, val file: File?, val data: ByteArray?, val size: Long, val modified: Long)
    private fun inventory(context: Context, rootsToScan: Set<String> = BackupFiles.roots): List<Source> = rootsToScan.sorted().flatMap { root ->
        val dir = File(context.filesDir, root)
        if (!dir.exists()) emptyList() else {
            val baseDir = context.filesDir.canonicalFile
            require(dir.isDirectory && dir.canonicalFile.startsWith(baseDir))
            dir.walkTopDown().filter { it.isFile }.mapNotNull { file ->
                val relative = "$root/" + file.relativeTo(dir).invariantSeparatorsPath
                if (!BackupFiles.allowed(relative)) null else {
                    require(file.canonicalFile.startsWith(dir.canonicalFile))
                    Source(relative, file, null, file.length(), file.lastModified())
                }
            }.toList()
        }
    }.sortedBy { it.path }

    fun export(
        context: Context,
        parentTree: Uri,
        selectedCategories: Set<ModularCategory> = ModularCategory.values().toSet()
    ) = run(context) { app ->
        val revision = DownloadService.workRevision()
        val targetRoots = selectedCategories.flatMap { it.roots }.toSet()
        val original = inventory(app, targetRoots)
        val dirs = targetRoots.filter { File(app.filesDir, it).isDirectory }.sorted()
        
        val meta = mutableListOf<Source>()
        var personalText: String? = null
        var launcherText: String? = null
        if (ModularCategory.PLACES in selectedCategories) {
            personalText = BackupPreferences.export(app).also { BackupPreferences.validate(it) }
            val bytes = personalText.toByteArray(Charsets.UTF_8)
            meta += Source("personal.json", null, bytes, bytes.size.toLong(), 0)
        }
        if (ModularCategory.LAUNCHER in selectedCategories) {
            launcherText = LauncherBackupPort.export(app).also { LauncherBackupPort.validate(it) }
            val bytes = launcherText.toByteArray(Charsets.UTF_8)
            meta += Source("launcher.json", null, bytes, bytes.size.toLong(), 0)
        }
        val sources = original + meta
        require(sources.isNotEmpty() && sources.size <= 100000)
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
            if (DownloadService.workRevision() != revision || inventory(app, targetRoots) != original) throw Failure(R.string.backup_source_changed)
            if (personalText != null && BackupPreferences.export(app) != personalText) throw Failure(R.string.backup_source_changed)
            if (launcherText != null && LauncherBackupPort.export(app) != launcherText) throw Failure(R.string.backup_source_changed)

            val manifest = BackupFiles.Manifest(dirs, entries, Instant.now().toString(), BuildConfig.VERSION_NAME)
            val json = manifest.json().also { BackupFiles.parse(it) }
            val uri = BackupFiles.create(app, folder, BackupFiles.MANIFEST)
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
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

        val categoryStats = manifest.entries.groupBy { ModularCategory.forEntryPath(it.path) }
            .filterKeys { it != null }
            .map { (cat, entries) ->
                CategoryStat(cat!!, entries.size, entries.sumOf { it.size })
            }.sortedBy { it.category.ordinal }

        val detailStr = buildString {
            append(manifest.created).append("\nVela ").append(manifest.version)
            append("\n").append(app.getString(R.string.backup_summary, manifest.entries.size, manifest.bytes / (1024 * 1024)))
        }
        mutable.value = State(
            message = R.string.backup_review,
            detail = detailStr,
            total = manifest.bytes,
            review = true,
            availableRestoreCategories = categoryStats
        )
    }

    fun restore(
        context: Context,
        selectedCategories: Set<ModularCategory> = ModularCategory.values().toSet()
    ) {
        val (folder, manifest) = reviewed ?: return
        reviewed = null
        run(context) { app ->
            val work = BackupRestore.work(app)
            try {
                BackupRestore.discard(app)
                val stage = File(work, "staged").apply { check(mkdirs()) }

                val targetEntries = manifest.entries.filter { entry ->
                    val cat = ModularCategory.forEntryPath(entry.path)
                    cat != null && cat in selectedCategories
                }
                val totalBytes = targetEntries.sumOf { it.size }
                checkStorage(app, totalBytes)

                val targetRoots = selectedCategories.flatMap { it.roots }.filter { it in manifest.roots }
                targetRoots.forEach { check(File(stage, "files/$it").mkdirs()) }

                val docs = BackupFiles.children(app, folder)
                val buffer = ByteArray(256 * 1024)
                var done = 0L
                for (entry in targetEntries) {
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
                                    progress(entry.path, done, totalBytes)
                                }
                                if (input.read() != -1) throw Failure(R.string.backup_invalid)
                                remaining -= chunk
                            }
                        }
                        output.fd.sync()
                    }
                    if (hex(hash.digest()) != entry.sha256) throw Failure(R.string.backup_invalid)
                }

                val personalFile = File(stage, "personal.json")
                if (personalFile.exists()) BackupPreferences.validate(personalFile.readText())
                val launcherFile = File(stage, "launcher.json")
                if (launcherFile.exists()) LauncherBackupPort.validate(launcherFile.readText())

                currentCoroutineContext().ensureActive()
                val partialManifest = BackupFiles.Manifest(targetRoots, targetEntries, manifest.created, manifest.version)
                BackupRestore.atomic(File(work, "ready.json"), partialManifest.json())
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
