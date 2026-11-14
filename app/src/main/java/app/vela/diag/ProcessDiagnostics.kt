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
    // Bagimsiz ana-looper sondasi: heartbeat ayni kuyrugu paylastigi icin mesgul bir
    // cihazi kilitlenmeden ayirt edemez. Sonda yalnizca "ana looper bos bir isi son
    // birkac saniyede calistirabildi mi" diye sorar ve sistem ANR ile sureci oldurmeden
    // once yigini dosyaya yazar; boylece kanit kaybolmaz.
    private val probePending = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var probePostedAt = 0L
    private var lastProbeDump = -60_000L
    // JVM birim testlerinde Looper yoktur; bu yuzden tembel ve null'a dayanikli.
    private val mainThreadId: Long by lazy { Looper.getMainLooper()?.thread?.id ?: -1L }
    private val clockTicksPerSecond = runCatching {
        android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
    }.getOrDefault(100L).coerceAtLeast(1L)
    private var lastPersist = 0L
    private val recentStages = java.util.ArrayDeque<String>()
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
            probe.run()
        }
    }

    private fun recordStage(label: String) {
        synchronized(recentStages) {
            if (recentStages.size >= 32) recentStages.removeFirst()
            recentStages.addLast("${System.currentTimeMillis()} [${Thread.currentThread().name}] $label")
        }
    }

    fun checkpoint(label: String) {
        recordStage(label)
        if (app.vela.BuildConfig.DIAGNOSTIC_BUILD) app.vela.util.FileLogger.d("ProcessDiagnostics", label)
        operation = label
        requestWrite()
    }

    /** Startup-only barrier: persist the last native-entry stage before entering EGL/JNI. */
    fun checkpointAndFlush(label: String) {
        recordStage(label)
        if (app.vela.BuildConfig.DIAGNOSTIC_BUILD) app.vela.util.FileLogger.d("ProcessDiagnostics", label)
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
            put("advertisedGles", context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.glEsVersion)
            put("operation", operation)
            put("recentStages", synchronized(recentStages) { org.json.JSONArray(recentStages.toList()) })
            put("activity", lifecycle)
            put("foreground", foreground)
            put("explicitClose", explicitlyClosed)
            put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory())
            put("javaMaxBytes", runtime.maxMemory())
            put("nativeHeapBytes", Debug.getNativeHeapAllocatedSize())
            put("availableMemoryBytes", memory.availMem)
            put("lowMemory", memory.lowMemory)
            put("mainHeartbeatAgeMs", SystemClock.uptimeMillis() - heartbeat)
            put("mainThreadCpuMs", mainThreadCpuMs())
            put("importance", importance())
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

    private fun historicalExit(previous: JSONObject): String = exitInformation(
        context, previous.optInt("pid"), previous.optString("process"), previous.optLong("started"), started,
    )

    /** Filter by PID, process and time; never attribute our later cleanup kill to the original failure. */
    fun exitInformation(context: Context, pid: Int, processName: String, since: Long, until: Long): String {
        if (Build.VERSION.SDK_INT < 30) return "API < 30: Android does not expose process exit reasons to this app.\n"
        if (pid <= 0) return "Renderer PID unknown; no exit record can be matched.\n"
        return runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, pid, 8).firstOrNull {
                it.processName == processName && it.timestamp in since..until
            }
            if (exit == null) "No matching system exit record; cause remains unknown.\n"
            else "process=${exit.processName}, pid=${exit.pid}, reason=${exit.reason} (${exitReasonName(exit.reason)}), status=${exit.status}, time=${exit.timestamp}, pssKb=${exit.pss}, rssKb=${exit.rss}, description=${exit.description}\n"
        }.getOrElse { "System exit information unavailable: $it\n" }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        1 -> "exit self"; 2 -> "signal"; 3 -> "low memory"; 4 -> "Java crash"
        5 -> "native crash"; 6 -> "ANR"; 7 -> "initialization failure"
        8 -> "permission change"; 9 -> "excessive resource use"; 10 -> "user requested"
        11 -> "user stopped"; 12 -> "dependency died"; 13 -> "other"; else -> "unknown"
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            // Tek bekleyen heartbeat, kilitlenmis ana kuyrugu doldurmaz.
            if (heartbeatPending.compareAndSet(false, true)) main.post {
                heartbeat = SystemClock.uptimeMillis()
                heartbeatPending.set(false)
            }
            if (now - lastPersist >= 5_000) { persist(); lastPersist = now }
            worker.postDelayed(this, if (BuildConfig.DIAGNOSTIC_BUILD) 1_000 else 5_000)
        }
    }

    private val probe = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (!probePending.get()) {
                probePostedAt = now
                probePending.set(true)
                main.post { probePending.set(false) }
            } else {
                val age = now - probePostedAt
                if (age >= PROBE_STALL_MS && now - lastProbeDump >= PROBE_COOLDOWN_MS) {
                    lastProbeDump = now
                    dumpMainThreadStall(age)
                }
            }
            worker.postDelayed(this, PROBE_PERIOD_MS)
        }
    }

    /**
     * Ana thread yigini once vela_app.log'a yazilir: rapor dosyalari cihazda en fazla
     * 40 tane tutulur ve kullanici yalnizca gunlugu kopyalasa bile yigin kaybolmaz.
     */
    private fun dumpMainThreadStall(ageMs: Long) {
        val text = runCatching {
            buildString {
                append("Ana looper sondasi ").append(ageMs).append(" ms yanit vermedi.\n")
                append("Ana thread CPU (toplam): ").append(mainThreadCpuMs()).append(" ms\n")
                append(snapshot()).append('\n')
                append("=== Main thread ===\n")
                Looper.getMainLooper().thread.stackTrace?.forEach { append(it).append('\n') }
                append("=== Threads (bounded) ===\n")
                Thread.getAllStackTraces().entries.take(40).forEach { (thread, frames) ->
                    append(thread.name).append(" [").append(thread.state).append("]\n")
                    frames.take(20).forEach { append("  ").append(it).append('\n') }
                }
            }
        }.getOrElse { "Ana looper sondasi $ageMs ms yanit vermedi; anlik goruntu alinamadi: $it" }
        runCatching {
            text.lineSequence().take(140).forEach { app.vela.util.FileLogger.e("MainThreadStall", it) }
        }
        runCatching { CrashCatcher.saveDiagnosticReport(context, "Main thread stall", text) }
    }

    /** /proc uzerinden ana thread'in toplam CPU suresi; -1 okunamadi demektir. */
    private fun mainThreadCpuMs(): Long = runCatching {
        val stat = File("/proc/self/task/$mainThreadId/stat").readText()
        val fields = stat.substringAfter(") ").trim().split(' ')
        if (fields.size < 13) return@runCatching -1L
        val ticks = fields[11].toLong() + fields[12].toLong()
        ticks * 1000L / clockTicksPerSecond
    }.getOrDefault(-1L)

    /** Sistemin bu surece verdigi onem: cached/background ise oldurulme riski yuksektir. */
    private fun importance(): Int = runCatching {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance
    }.getOrDefault(-1)

    private const val PROBE_PERIOD_MS = 1_000L
    private const val PROBE_STALL_MS = 3_000L
    private const val PROBE_COOLDOWN_MS = 20_000L
}
