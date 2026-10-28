package app.vela.carlauncher.diag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import app.vela.BuildConfig
import app.vela.MainActivity
import app.vela.diag.CrashCatcher
import app.vela.diag.LocalLogcatRecorder
import app.vela.diag.ProcessDiagnostics
import app.vela.util.FileLogger
import java.io.File
import org.json.JSONObject

/** Diagnostic APK only. Same UID, separate process: no READ_LOGS or root privileges. */
class DiagnosticService : Service() {
    private lateinit var worker: Handler
    private lateinit var messenger: Messenger
    private var watched: IBinder? = null
    private var death: IBinder.DeathRecipient? = null
    private var generation = 0
    private var journalStale = false
    private var watchedPid = 0

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DIAGNOSTIC_BUILD) { stopSelf(); return }
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Vela tanı kaydı", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1907, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Vela tanı kaydı açık")
            .setContentText("Launcher kapanırsa son işlem kaydedilir")
            .setContentIntent(open).setOngoing(true).build())
        worker = Handler(HandlerThread("VelaDeathObserver").apply { start() }.looper)
        messenger = Messenger(Handler(worker.looper) { message ->
            if (message.what == WATCH) watch(message)
            true
        })
        LocalLogcatRecorder.start(this)
        worker.post(health)
        FileLogger.i(TAG, "Independent recorder started; pid=${Process.myPid()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = if (::messenger.isInitialized) messenger.binder else null

    private fun watch(message: Message) {
        val token = message.replyTo?.binder ?: return
        val pid = message.data.getInt("pid")
        if (pid <= 0 || message.sendingUid != Process.myUid()) return
        val processName = message.data.getString("process") ?: return
        val started = message.data.getLong("started")
        watched?.let { old -> death?.let { runCatching { old.unlinkToDeath(it, 0) } } }
        val session = ++generation
        watchedPid = pid
        val recipient = IBinder.DeathRecipient {
            worker.post {
                val ended = System.currentTimeMillis()
                FileLogger.w(TAG, "Launcher binder died: pid=$pid; this confirms process loss, not its cause")
                val journal = runCatching {
                    val text = readJournal()
                    if (JSONObject(text).optInt("pid") == pid) text
                    else "Journal already belongs to another launcher PID; not attributed to pid=$pid."
                }.getOrElse { "Last session unavailable: $it" }
                // Android may publish the exit record a little after binder death.
                worker.postDelayed({
                    runCatching {
                        CrashCatcher.saveDiagnosticReport(this, "Launcher process lost (independent observer)",
                            "Observed binder death: pid=$pid, process=$processName, time=$ended\n" +
                            "This confirms process loss, not Java/native crash or an OEM kill reason.\n" +
                            "=== Last main-process journal ===\n$journal\n" +
                            "=== System exit information ===\n" +
                            ProcessDiagnostics.exitInformation(this, pid, processName, started, System.currentTimeMillis()))
                    }.onFailure { FileLogger.e(TAG, "Cannot save process-loss report", it) }
                }, 2_000)
                // Capture trailing native messages without keeping a dead launcher alive forever.
                worker.postDelayed({ if (generation == session) stopSelf() }, 60_000)
            }
        }
        watched = token
        death = recipient
        try { token.linkToDeath(recipient, 0) }
        catch (_: RemoteException) { recipient.binderDied() }
        FileLogger.i(TAG, "Watching launcher: pid=$pid, process=$processName")
    }

    // Read without AtomicFile.openRead(): on old Android it can restore a .bak while
    // the other process is writing. Prefer that complete previous snapshot without mutating it.
    private fun readJournal(): String {
        val base = File(filesDir, "diag/process-session.json")
        val backup = File("${base.path}.bak")
        return (if (backup.isFile) backup else base).readText(Charsets.UTF_8)
    }

    private val health = object : Runnable {
        override fun run() {
            if (watched?.isBinderAlive == true) runCatching {
                val journal = readJournal()
                val state = JSONObject(journal)
                if (state.optInt("pid") != watchedPid) return@runCatching
                val age = SystemClock.uptimeMillis() - state.optLong("uptimeMs")
                if (age >= 15_000 && !journalStale) {
                    journalStale = true
                    CrashCatcher.saveDiagnosticReport(this@DiagnosticService, "Main journal stopped updating",
                        "Main binder is still alive, but its journal is $age ms old.\n" +
                        "Possible process/thread stall or storage delay; not a confirmed ANR.\n$journal")
                } else if (age < 15_000) journalStale = false
            }
            worker.postDelayed(this, 5_000)
        }
    }

    override fun onDestroy() {
        watched?.let { token -> death?.let { runCatching { token.unlinkToDeath(it, 0) } } }
        LocalLogcatRecorder.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (::worker.isInitialized) { worker.removeCallbacksAndMessages(null); worker.looper.quitSafely() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "vela_diagnostics"
        private const val TAG = "DiagnosticService"
        private const val WATCH = 1
        private val started = System.currentTimeMillis()
        private val token = Messenger(Handler(Looper.getMainLooper()))
        private var bound = false
        private lateinit var appContext: Context
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) return
                runCatching {
                    Messenger(binder).send(Message.obtain().apply {
                        what = WATCH
                        replyTo = token
                        data = Bundle().apply {
                            putInt("pid", Process.myPid())
                            putString("process", app.vela.util.ProcessIdentity.name(appContext))
                            putLong("started", started)
                        }
                    })
                }.onFailure { FileLogger.w(TAG, "Observer registration failed", it) }
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        fun start(context: Context) {
            if (!BuildConfig.DIAGNOSTIC_BUILD || bound) return
            val app = context.applicationContext
            appContext = app
            val intent = Intent(app, DiagnosticService::class.java)
            runCatching {
                app.startForegroundService(intent)
                bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.onFailure {
                FileLogger.w(TAG, "Independent observer unavailable; using in-process logcat", it)
                LocalLogcatRecorder.start(app)
            }
        }

        fun stop(context: Context) {
            if (bound) { context.applicationContext.unbindService(connection); bound = false }
            context.stopService(Intent(context, DiagnosticService::class.java))
        }
    }
}
