package app.vela.diag

import android.content.Context
import app.vela.BuildConfig
import app.vela.util.FileLogger
import app.vela.util.ProcessIdentity
import java.io.File
import java.io.FileOutputStream

/** Best-effort logcat under the app UID. Never claims access to privileged system logs. */
internal object LocalLogcatRecorder {
    @Synchronized fun start(context: Context) {
        if (started) return
        started = true
        val session = ++generation
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
                // Her satirda flush etmek yavas depolamali teypte uygulamanin kendi
                // gunlugunu de yavaslatiyordu; 8 KB / 1 saniye biriktirip yaziyoruz.
                // Surec sertce oldurulse bile kuyruk logcat tamponunda kalir ve bir
                // sonraki oturum `-T` ile geriye donuk okur.
                val pending = StringBuilder()
                var lastFlush = System.currentTimeMillis()
                fun flushPending() {
                    if (pending.isEmpty()) return
                    val bytes = pending.toString().toByteArray(Charsets.UTF_8)
                    pending.setLength(0)
                    if (size + bytes.size > 2 * 1024 * 1024) {
                        output.close(); rotate(); output = FileOutputStream(file); size = 0
                    }
                    output.write(bytes); output.flush(); size += bytes.size
                    lastFlush = System.currentTimeMillis()
                }
                fun line(value: String) {
                    pending.append(value).append('\n')
                    if (pending.length >= 8192 || System.currentTimeMillis() - lastFlush >= 1000) flushPending()
                }
                try {
                    line("Vela logcat: pid=${android.os.Process.myPid()}, uid=${android.os.Process.myUid()}, time=${System.currentTimeMillis()}")
                    line("Scope: app-accessible logcat only; not proof of full system/crash-buffer access. Permission errors and logcat output follow.")
                    line("Voice safety: nativeVoiceEnabled=${BuildConfig.NATIVE_VOICE_ENABLED}; nativeTtsEnabled=${BuildConfig.NATIVE_TTS_ENABLED}; musicDisabled=${BuildConfig.DIAGNOSTIC_MUSIC_DISABLED}")
                    // Read retained entries as well as new ones: a previous process may have died
                    // before its recorder could write the final messages. No global buffer clearing.
                    process = ProcessBuilder("/system/bin/logcat", "-b", "all", "-v", "threadtime", "-T", "400")
                        .redirectErrorStream(true).start()
                    synchronized(this) {
                        if (generation == session) capture = process else process?.destroy()
                    }
                    process!!.inputStream.bufferedReader().use { reader ->
                        while (true) { val value = reader.readLine() ?: break; line(value) }
                    }
                    line("Logcat stopped: exit=${process!!.waitFor()}")
                } finally {
                    runCatching { flushPending(); output.close() }
                }
            } catch (error: Exception) {
                if (synchronized(this) { generation == session })
                    FileLogger.w("LocalLogcatRecorder", "Logcat capture unavailable: ${error.message}", error)
            } finally {
                process?.destroy()
                synchronized(this) { if (generation == session) { started = false; capture = null } }
            }
        }, "VelaLogcatRecorder").apply { isDaemon = true }.start()
    }
    @Synchronized fun stop() {
        generation++
        started = false
        capture?.destroy()
        capture = null
    }
    private var capture: Process? = null
    private var generation = 0
    private var started = false
}
