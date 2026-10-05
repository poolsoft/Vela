package app.vela.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import app.vela.BuildConfig
import org.json.JSONObject
import java.io.File

/** Local bounded journal; an unfinished session is evidence of process loss, not proof of a crash. */
object ProcessDiagnostics {
    private lateinit var context: Context
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    private val started = System.currentTimeMillis()
    @Volatile private var operation = "application create"
    @Volatile private var lifecycle = "application"
    @Volatile private var foreground = false
    @Volatile private var explicitlyClosed = false
    @Volatile private var heartbeat = SystemClock.uptimeMillis()
    private val heartbeatPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private var stallReported = false
    private var writePending = false

    @Synchronized fun install(app: Context) {
        if (::worker.isInitialized) return
        context = app.applicationContext
        worker = Handler(HandlerThread("VelaProcessDiagnostics").apply { start() }.looper)
        worker.post {
            runCatching {
                val previous = JSONObject(journal().openRead().bufferedReader().use { it.readText() })
                if (!previous.optBoolean("explicitClose")) {
                    val exit = historicalExit(previous)
                    CrashCatcher.saveDiagnosticReport(context, "Previous process ended", buildString {
                        append("Previous process ended without an explicit close. Cause unknown unless confirmed below.\n")
                        append("This can include normal Android/OEM process reclamation; it is not proof of a crash.\n")
                        append("=== Previous session ===\n").append(previous.toString(2)).append('\n')
                        append("=== System exit information ===\n").append(exit)
                    })
                }
            }
            persist()
            tick.run()
        }
    }

    fun checkpoint(label: String) {
        operation = label
        requestWrite()
    }

    /** Startup-only barrier: persist the last native-entry stage before entering EGL/JNI. */
    fun checkpointAndFlush(label: String) {
        operation = label
        if (!::worker.isInitialized) return
        if (Looper.myLooper() == worker.looper) { persist(); return }
        val done = java.util.concurrent.CountDownLatch(1)
        worker.post { try { persist() } finally { done.countDown() } }
        runCatching { done.await(1, java.util.concurrent.TimeUnit.SECONDS) }
    }

    fun activity(state: String, visible: Boolean? = null) {
        lifecycle = state
        if (visible != null) foreground = visible
        if (state == "create" || state == "start") explicitlyClosed = false
        checkpoint("activity: $state")
    }

    fun beginSession(label: String) {
        explicitlyClosed = false
        checkpoint(label)
    }

    fun explicitClose() {
        explicitlyClosed = true
        checkpoint("settings: explicit close")
    }

    private fun journal() = AtomicFile(File(context.filesDir, "diag/process-session${app.vela.util.ProcessIdentity.fileSuffix(context)}.json").apply {
        parentFile?.mkdirs()
    })

    @Synchronized private fun requestWrite() {
        if (!::worker.isInitialized || writePending) return
        writePending = true
        worker.postDelayed({
            synchronized(this) { writePending = false }
            persist()
        }, 250)
    }

    fun snapshot(): String = runCatching { session().toString(2) }.getOrElse { "Snapshot unavailable: $it" }

    private fun session(): JSONObject {
        val runtime = Runtime.getRuntime()
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return JSONObject().apply {
            put("pid", Process.myPid())
            put("process", app.vela.util.ProcessIdentity.name(context))
            put("started", started)
            put("recorded", System.currentTimeMillis())
            put("uptimeMs", SystemClock.uptimeMillis())
            put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}")
            put("abis", Build.SUPPORTED_ABIS.joinToString())
            put("operation", operation)
            put("activity", lifecycle)
            put("foreground", foreground)
            put("explicitClose", explicitlyClosed)
            put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory())
            put("javaMaxBytes", runtime.maxMemory())
            put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            put("availableMemoryBytes", memory.availMem)
            put("lowMemory", memory.lowMemory)
            put("mainHeartbeatAgeMs", SystemClock.uptimeMillis() - heartbeat)
        }
    }

    private fun persist() {
        runCatching {
            val file = journal()
            val stream = file.startWrite()
            try {
                stream.write(snapshot().toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    private fun historicalExit(previous: JSONObject): String {
        if (Build.VERSION.SDK_INT < 30) return "API < 30: Android does not expose process exit reasons to this app.\n"
        return runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = manager.getHistoricalProcessExitReasons(context.packageName, previous.optInt("pid"), 8)
            val exit = exits.firstOrNull { it.timestamp >= previous.optLong("started") && it.timestamp <= started }
            if (exit == null) "No matching system exit record.\n"
            else "reason=${exit.reason}, status=${exit.status}, time=${exit.timestamp}, pssKb=${exit.pss}, rssKb=${exit.rss}, description=${exit.description}\n"
        }.getOrElse { "System exit information unavailable: $it\n" }
    }

    private val tick = object : Runnable {
        override fun run() {
            val age = SystemClock.uptimeMillis() - heartbeat
            if (foreground && age >= 15_000 && !Debug.isDebuggerConnected() && !stallReported) {
                stallReported = true
                runCatching {
                    CrashCatcher.saveDiagnosticReport(context, "Main thread stall", buildString {
                        append("Main thread has not answered for ").append(age).append(" ms. This is a watchdog observation, not a confirmed system ANR.\n")
                        append(snapshot()).append("\n=== Main thread ===\n")
                        Looper.getMainLooper().thread.stackTrace.forEach { append(it).append('\n') }
                        append("=== Other threads (bounded) ===\n")
                        Thread.getAllStackTraces().entries.take(40).forEach { (thread, frames) ->
                            append(thread.name).append(" [").append(thread.state).append("]\n")
                            frames.take(30).forEach { append("  ").append(it).append('\n') }
                        }
                    })
                }
            }
            if (!foreground || age < 15_000) stallReported = false
            // One outstanding heartbeat avoids filling a stalled main-thread queue.
            if (heartbeatPending.compareAndSet(false, true)) main.post {
                heartbeat = SystemClock.uptimeMillis()
                heartbeatPending.set(false)
            }
            persist()
            worker.postDelayed(this, 5_000)
        }
    }
}
