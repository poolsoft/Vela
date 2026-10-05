package app.vela.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import app.vela.BuildConfig
import app.vela.core.diag.DiagEvent
import app.vela.util.FileLogger
import app.vela.util.ProcessIdentity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Catches an otherwise-fatal uncaught exception and **persists** a crash report -
 * stack trace, app/device versions, and whatever diagnostic breadcrumbs were in
 * memory - to a file under `filesDir/diag/crash/`, then chains to the system's
 * default handler so the normal crash flow is unchanged.
 *
 * Why this exists: when nav crashed on a phone that wasn't tethered, there was no
 * way to get the stack trace. This keeps it on disk so the user can **export the
 * crash report on the next launch** (Settings → Diagnostics) and hand it to a dev.
 *
 * The stack trace + device info are benign (no personal data), so a report is
 * written even if the opt-in diagnostics log is off - the breadcrumb section is
 * simply empty in that case (breadcrumbs are only recorded when the user opted in).
 * The report never leaves the phone unless the user exports + shares it.
 */
object CrashCatcher {

    fun install(context: Context, breadcrumbs: () -> List<DiagEvent>) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            // If the crash happened in TextureViewRenderer or EGL setup, immediately revert texture_render
            // to false in SharedPreferences so the next launch uses the reliable GLSurfaceView.
            runCatching {
                val isTextureOrEglCrash = thread.name.contains("TextureViewRenderer") ||
                    ex.stackTrace.any { it.className.contains("EGLImpl") || it.className.contains("TextureViewRenderThread") }
                if (isTextureOrEglCrash && ProcessIdentity.isMain(app)) {
                    val prefs = app.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("texture_render", false).remove("texture_render_auto_ms").putInt("map_init_crashes", 0).apply()
                    FileLogger.e("CrashCatcher", "TextureView/EGL crash detected! Reverted texture_render to false for next launch.")
                }
            }
            runCatching { writeReport(app, ex, breadcrumbs()) }
            prev?.uncaughtException(thread, ex)
        }
    }

    private fun dir(context: Context) = File(context.filesDir, "diag/crash${ProcessIdentity.fileSuffix(context)}").apply { mkdirs() }

    private fun writeReport(context: Context, ex: Throwable, crumbs: List<DiagEvent>) {
        val sw = StringWriter()
        ex.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("Vela crash report\n")
            append("when: ").append(System.currentTimeMillis()).append('\n')
            append("version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("android: API ").append(Build.VERSION.SDK_INT)
                .append(" - ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n")
            append("=== process snapshot ===\n").append(ProcessDiagnostics.snapshot()).append("\n\n")
            append("=== stack trace ===\n").append(sw.toString()).append('\n')
            append("=== breadcrumbs (").append(crumbs.size).append(") ===\n")
            crumbs.forEach { e ->
                append(e.epochMs).append(" [").append(e.kind).append("] ").append(e.summary)
                e.detail?.let { append(" :: ").append(it) }
                append('\n')
            }
        }
        saveDiagnosticReport(context, "Java exception", text)
    }

    fun saveDiagnosticReport(context: Context, kind: String, text: String) {
        val suffix = ProcessIdentity.fileSuffix(context)
        val name = "crash-${System.currentTimeMillis()}${suffix}-${System.nanoTime()}.txt"
        val report = "Diagnostic type: $kind\n$text"
        File(dir(context), name).writeText(report)
        runCatching {
            val extLogsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
            File(extLogsDir, name).writeText(report)
            extLogsDir.listFiles { f -> f.name.startsWith("crash-") &&
                (if (suffix.isEmpty()) !f.name.contains("-map_renderer") else f.name.contains("$suffix-")) }?.sortedByDescending { it.name }
                ?.drop(5)?.forEach { it.delete() }
            FileLogger.i("CrashCatcher", "Diagnostic report saved: $kind ($name)")
        }
        prune(context)
    }

    /** Keep only the few most recent reports so this can't grow unbounded. */
    private fun prune(context: Context, keep: Int = 5) {
        val files = reports(dir(context))
        if (files.size > keep) files.dropLast(keep).forEach { runCatching { it.delete() } }
    }

    /** Crash reports on disk, oldest first. */
    private fun reports(directory: File): List<File> =
        directory.listFiles { f -> f.isFile && f.name.startsWith("crash-") }?.sortedBy { it.name } ?: emptyList()

    fun pending(context: Context): List<File> {
        val own = reports(dir(context))
        return if (ProcessIdentity.isMain(context))
            (own + File(context.filesDir, "diag").listFiles { f ->
                f.isDirectory && f.name.startsWith("crash-map_renderer")
            }.orEmpty().flatMap { reports(it) }).sortedBy { it.name }
        else own
    }

    fun clear(context: Context) {
        pending(context).forEach { runCatching { it.delete() } }
    }

    /** Share the most recent crash report via the system sheet, or null if none. */
    fun shareIntent(context: Context): Intent? {
        val newest = pending(context).lastOrNull() ?: return null
        return shareFileIntent(
            context, newest,
            mime = "text/plain",
            subject = "Vela crash report",
            text = "Attached: a Vela crash report (stack trace + diagnostics).",
            title = "Share crash report",
        )
    }
}
