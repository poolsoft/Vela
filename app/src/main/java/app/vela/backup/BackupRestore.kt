package app.vela.backup

import android.content.Context
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Install before MapLibre/OBF/SharedPreferences readers exist. Interrupted installs roll back. */
object BackupRestore {
    internal fun work(context: Context) = File(context.filesDir, "portable-restore")
    fun pending(context: Context) = File(work(context), "ready.json").isFile
    fun result(context: Context): String? = File(context.filesDir, "portable-restore-result").takeIf { it.isFile }?.readText()
    internal fun atomic(file: File, text: String) {
        file.parentFile!!.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { it.write(text.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(temp.renameTo(file))
        syncDir(file.parentFile!!)
    }
    private fun syncDir(dir: File) {
        val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
    }
    private fun move(from: File, to: File) {
        to.parentFile!!.mkdirs()
        check(from.renameTo(to)) { "Cannot install ${from.name}" }
        syncDir(from.parentFile!!)
        syncDir(to.parentFile!!)
    }
    internal fun discard(context: Context) {
        val work = work(context)
        check(!File(work, "journal.json").exists())
        check(work.deleteRecursively())
    }
    private fun allowedTarget(path: String): Boolean =
        path.removePrefix("files/") in BackupFiles.roots && path.startsWith("files/") ||
            BackupPreferences.names.any { path == "shared_prefs/$it.xml" || path == "shared_prefs/$it.xml.bak" }

    /** Called from Application.attachBaseContext, before any settings singleton is initialized. */
    fun recover(context: Context) {
        val work = work(context)
        if (!work.exists()) return
        val journal = File(work, "journal.json")
        val ready = File(work, "ready.json")
        val committed = File(work, "committed")
        if (committed.exists()) {
            record(context, "success")
            cleanup(work)
            return
        }
        if (journal.exists()) {
            rollback(context, work)
            record(context, "rolled_back")
            cleanup(work)
            return
        }
        if (!ready.exists()) {
            // Process death during verification has never touched live files.
            check(work.deleteRecursively())
            return
        }
        try {
            val manifest = ready.inputStream().use { BackupFiles.parse(BackupFiles.readBounded(it)) }
            val stage = File(work, "staged")
            val paths = manifest.roots.map { "files/$it" } + BackupPreferences.stage(context, stage)
            require(paths.all(::allowedTarget))
            val appDir = File(context.applicationInfo.dataDir)
            val plan = JSONArray()
            paths.forEach { path ->
                plan.put(JSONObject().put("path", path).put("existed", BackupFiles.local(appDir, path).exists()))
            }
            atomic(journal, plan.toString()) // durable intent before the first destructive operation
            paths.forEach { path ->
                val live = BackupFiles.local(appDir, path)
                val old = BackupFiles.local(File(work, "old"), path)
                val replacement = BackupFiles.local(stage, path)
                if (live.exists()) move(live, old)
                if (replacement.exists()) move(replacement, live)
            }
            atomic(committed, "1")
        } catch (e: Exception) {
            // A committed install must never be undone. Next launch resumes result/cleanup.
            if (committed.exists()) throw e
            if (journal.exists()) rollback(context, work)
            record(context, "rolled_back")
            cleanup(work)
            return
        }
        record(context, "success")
        cleanup(work)
    }
    private fun rollback(context: Context, work: File) {
        val plan = JSONArray(File(work, "journal.json").readText())
        val appDir = File(context.applicationInfo.dataDir)
        for (i in plan.length() - 1 downTo 0) {
            val entry = plan.getJSONObject(i)
            val path = entry.getString("path")
            require(allowedTarget(path))
            val live = BackupFiles.local(appDir, path)
            val old = BackupFiles.local(File(work, "old"), path)
            if (old.exists()) {
                check(live.deleteRecursively())
                move(old, live)
            } else if (!entry.getBoolean("existed")) {
                check(live.deleteRecursively())
                if (live.parentFile!!.exists()) syncDir(live.parentFile!!)
            }
        }
    }
    private fun record(context: Context, result: String) = atomic(File(context.filesDir, "portable-restore-result"), result)
    private fun cleanup(work: File) {
        // Keep the committed marker until every journal/ready marker is gone: cleanup is restartable.
        check(File(work, "old").deleteRecursively())
        check(File(work, "staged").deleteRecursively())
        listOf("ready.json", "journal.json", "committed").forEach { name ->
            val f = File(work, name)
            check(!f.exists() || f.delete())
            syncDir(work)
        }
        check(work.deleteRecursively())
    }
}
