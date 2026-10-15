package app.vela.voice

import android.content.*
import android.os.*
import app.vela.core.voice.NeuralSynth
import app.vela.core.voice.VelaPiper
import app.vela.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Launcher-side proxy. No ONNX classes or model initialization in this process. */
@Singleton
class PiperSynth @Inject constructor(@ApplicationContext private val context: Context) : NeuralSynth {
    private val handler = Handler(Looper.getMainLooper())
    private var remote: Messenger? = null
    private var connection: ServiceConnection? = null
    private val queued = ArrayList<Message>()
    private val completions = mutableMapOf<Int, () -> Unit>()
    private var nextId = 0
    private var failed = false
    private var remotePid = 0
    private var generation = 0
    override var ready = false
        private set
    var numSpeakers = 0
        private set
    var onFailure: ((String) -> Unit)? = null
    override val voiceLanguage: String?
        get() = VelaPiper.effectiveVoiceId(context)?.substringBefore('_')
    private val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.data.getInt("generation", -1) != generation) return@Handler true
        when (msg.what) {
            DONE -> completions.remove(msg.arg1)?.invoke()
            READY -> { ready = true; numSpeakers = msg.arg1 }
            STARTED -> remotePid = msg.arg1
            ACK -> if (completions.isEmpty()) handler.removeCallbacks(timeout)
            ERROR -> fail(msg.data.getString("error") ?: "TTS failed")
        }
        if (ready && completions.isEmpty()) handler.removeCallbacks(timeout)
        true
    })
    private val timeout = Runnable { fail("Vela TTS yanıt vermedi") }

    private fun fail(reason: String) {
        if (failed) return
        failed = true
        FileLogger.e("PiperIPC", reason)
        val pid = remotePid
        release()
        val process = context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.pid == pid && it.uid == Process.myUid() && it.processName == "${context.packageName}:voice_tts" }
        if (process != null) Process.killProcess(pid)
        onFailure?.invoke(reason)
    }

    private fun send(what: Int, text: String = "", interrupt: Boolean = false, done: (() -> Unit)? = null) {
        val requestGeneration = generation
        handler.post {
            if (failed || requestGeneration != generation) { done?.invoke(); return@post }
            val id = ++nextId
            done?.let { completions[id] = it }
            val data = Bundle().apply {
                putInt("generation", requestGeneration)
                putString("text", text)
                putBoolean("interrupt", interrupt)
                val prefs = Bundle()
                context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE).all.forEach { (key, value) ->
                    if (key.startsWith("voice_")) when (value) {
                        is String -> prefs.putString(key, value)
                        is Float -> prefs.putFloat(key, value)
                        is Int -> prefs.putInt(key, value)
                        is Boolean -> prefs.putBoolean(key, value)
                    }
                }
                putBundle("prefs", prefs)
            }
            val message = Message.obtain(null, what, id, 0).apply { this.data = data; replyTo = replies }
            if (remote != null) {
                try { remote!!.send(message) } catch (e: RemoteException) { fail("TTS bağlantısı kesildi: $e") }
            } else {
                queued.add(message)
                if (connection == null) bind()
            }
            handler.removeCallbacks(timeout)
            if (!ready || completions.isNotEmpty()) handler.postDelayed(timeout, 90_000)
        }
    }

    private fun bind() {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this) return
                remote = Messenger(binder)
                try { queued.forEach { remote!!.send(it) }; queued.clear() }
                catch (e: RemoteException) { fail("TTS gönderimi başarısız: $e") }
            }
            override fun onServiceDisconnected(name: ComponentName) = fail("Vela TTS süreci sonlandı")
            override fun onBindingDied(name: ComponentName) = fail("Vela TTS bağlantısı sonlandı")
            override fun onNullBinding(name: ComponentName) = fail("Vela TTS başlatılamadı")
        }
        connection = conn
        try {
            if (!context.bindService(Intent(context, PiperService::class.java), conn, Context.BIND_AUTO_CREATE)) fail("Vela TTS bağlanamadı")
        } catch (e: Exception) { fail("Vela TTS başlatılamadı: $e") }
    }
    override fun warmUp() { if (!ready) send(WARM) }
    fun allowRetry() { failed = false }
    fun reloadVoice() { failed = false; release(); send(WARM) }
    override fun speak(text: String, interrupt: Boolean, onDone: () -> Unit) = send(SPEAK, text, interrupt, onDone)
    override fun stop() {
        handler.post { runCatching { remote?.send(Message.obtain(null, STOP)) } }
    }
    override fun release() {
        generation++
        handler.removeCallbacks(timeout)
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null; remote = null; remotePid = 0; ready = false; queued.clear()
        val callbacks = completions.values.toList()
        completions.clear()
        callbacks.forEach { it() }
    }
    fun deleteModelDir(dir: java.io.File) { allowRetry(); send(DELETE, dir.name) }

    companion object {
        const val WARM = 1; const val SPEAK = 2; const val STOP = 3
        const val READY = 4; const val DONE = 5; const val ERROR = 6
        const val STARTED = 7; const val DELETE = 8
        const val ACK = 9
    }
}
