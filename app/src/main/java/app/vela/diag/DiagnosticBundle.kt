package app.vela.diag

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.vela.BuildConfig
import app.vela.R
import app.vela.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit local export. No maps, media, preferences or unrestricted system logs. */
internal object DiagnosticBundle {
    /** Keep the previous bundle if exporting is interrupted by another process death. */
    fun saveAfterProcessLoss(context: Context) {
        val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        directory.mkdirs()
        val target = android.util.AtomicFile(File(directory, "vela-diagnostics-last-session.zip"))
        val stream = target.startWrite()
        try {
            // AtomicFile owns the stream; closing the ZIP must not close it before finishWrite.
            val wrapper = object : java.io.FilterOutputStream(stream) {
                override fun close() { flush() }
            }
            write(context, wrapper)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }
    fun write(context: Context, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            fun text(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            text("README.txt", "Vela diagnostic bundle\nVersion: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}, ABI ${Build.SUPPORTED_ABIS.joinToString()}\n" +
                "Created: ${System.currentTimeMillis()}\n" +
                "Existing application logs, process journals and renderer graphics probes only.\n" +
                "A process-ended report is not proof of an exception. System/native exit reason may be unavailable.\n" +
                "Logs may contain file paths or place names. No automatic upload.\n" +
                "Newest 40 files; maximum 2 MiB per file and 16 MiB of file contents.\n")
            text("current-process.json", ProcessDiagnostics.snapshot())
            val roots = listOf(
                "diag" to File(context.filesDir, "diag"),
                "logs" to File(context.getExternalFilesDir(null) ?: context.filesDir, "logs"),
            )
            val candidates = roots.flatMap { (prefix, root) ->
                if (!root.isDirectory) emptyList() else root.walkTopDown().maxDepth(2).filter { file ->
                    file.isFile && file.canonicalPath.startsWith(root.canonicalPath + File.separator) &&
                        (if (prefix == "logs") file.name.endsWith(".log") || file.name.endsWith(".log.old") || file.name.startsWith("crash-")
                         else file.name.startsWith("process-session") || file.name.startsWith("graphics") || file.name.startsWith("crash-"))
                }.map { file -> "$prefix/${file.relativeTo(root).invariantSeparatorsPath}" to file }.toList()
            }.sortedByDescending { it.second.lastModified() }.take(40)
            var remaining = 16L * 1024 * 1024
            val notes = StringBuilder()
            candidates.forEach { (name, file) ->
                val limit = minOf(2L * 1024 * 1024, remaining).toInt()
                if (limit <= 0) { notes.append("Omitted (size limit): $name\n"); return@forEach }
                // Read before opening an entry: missing/unreadable files never invalidate the ZIP.
                val bytes = runCatching { file.inputStream().use { it.readBytesBounded(limit) } }
                    .getOrElse { notes.append("Unreadable: $name (${it.javaClass.simpleName})\n"); return@forEach }
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                remaining -= bytes.size
                if (file.length() > bytes.size) notes.append("Truncated: $name\n")
            }
            text("export-notes.txt", notes.toString().ifEmpty { "All selected files were copied.\n" })
        }
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

@Composable
internal fun DiagnosticBundleSaveButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open document")
                    output.use { DiagnosticBundle.write(context, it) }
                }
            }
            busy = false
            result.exceptionOrNull()?.let { FileLogger.w("DiagnosticBundle", "Export failed", it) }
            Toast.makeText(context, if (result.isSuccess) R.string.settings_diag_save_success else R.string.settings_diag_save_failed,
                Toast.LENGTH_LONG).show()
        }
    }
    FilledTonalButton(enabled = !busy, onClick = { save.launch("vela-diagnostics-${System.currentTimeMillis()}.zip") }) {
        Text(stringResource(if (busy) R.string.settings_diag_saving else R.string.settings_diag_save))
    }
    Text(stringResource(R.string.settings_diag_save_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
}
