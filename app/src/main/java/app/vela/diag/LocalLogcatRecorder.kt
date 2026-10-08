package app.vela.diag

import android.content.Context
import app.vela.util.FileLogger
import app.vela.util.ProcessIdentity
import java.io.File
import java.io.FileOutputStream

/** Best-effort logcat under the app UID. Never claims access to privileged system logs. */
internal object LocalLogcatRecorder {
    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread({
            var process: Process? = null
            try {
                val directory = File(app.getExternalFilesDir(null) ?: app.filesDir, "logs").apply { mkdirs() }
                val file = File(directory, "logcat${ProcessIdentity.fileSuffix(app)}.log")
                val old = File(directory, "${file.name}.old")
                fun rotate() {
                    if (old.exists()) check(old.delete()) { "Cannot remove old logcat" }
                    if (file.exists()) check(file.renameTo(old)) { "Cannot preserve previous logcat" }
                }
                rotate()
                var output = FileOutputStream(file)
                var size = 0L
                fun line(value: String) {
                    val bytes = (value + "\n").toByteArray(Charsets.UTF_8)
                    if (size + bytes.size > 2 * 1024 * 1024) {
                        output.close(); rotate(); output = FileOutputStream(file); size = 0
                    }
                    output.write(bytes); output.flush(); size += bytes.size
                }
                try {
                    line("Vela logcat: pid=${android.os.Process.myPid()}, uid=${android.os.Process.myUid()}, time=${System.currentTimeMillis()}")
                    line("Scope: app-accessible logcat only; not proof of full system/crash-buffer access. Permission errors and logcat output follow.")
                    line("Diagnostic music isolation: service, saved playback preparation and internal cover extraction disabled.")
                    // Read retained entries as well as new ones: a previous process may have died
                    // before its recorder could write the final messages. No global buffer clearing.
                    process = ProcessBuilder("/system/bin/logcat", "-b", "all", "-v", "threadtime", "-T", "400")
                        .redirectErrorStream(true).start()
                    process!!.inputStream.bufferedReader().use { reader ->
                        while (true) { val value = reader.readLine() ?: break; line(value) }
                    }
                    line("Logcat stopped: exit=${process!!.waitFor()}")
                } finally { output.close() }
            } catch (error: Exception) {
                FileLogger.w("LocalLogcatRecorder", "Logcat capture unavailable: ${error.message}", error)
            } finally { process?.destroy() }
        }, "VelaLogcatRecorder").apply { isDaemon = true }.start()
    }
    private var started = false
}
