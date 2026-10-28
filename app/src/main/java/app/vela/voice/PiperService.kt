package app.vela.voice

import android.app.Service
import android.content.Intent
import android.os.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Native model and playback live only in :voice_tts. Bound on demand, never started at boot. */
@AndroidEntryPoint
class PiperService : Service() {
    @Inject lateinit var synth: NativePiperSynth
    private val handler = Handler(Looper.getMainLooper())
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        val reply = msg.replyTo
        val id = msg.arg1
        val requestGeneration = msg.data.getInt("generation", -1)
        fun respond(code: Int, value: Int = 0, error: String? = null) {
            runCatching { reply?.send(Message.obtain(null, code, value, 0).apply {
                data = Bundle().apply {
                    putInt("generation", requestGeneration)
                    if (error != null) putString("error", error)
                }
            }) }
        }
        val prefs = applicationContext.getSharedPreferences("vela_settings", MODE_PRIVATE).edit()
        msg.data.getBundle("prefs")?.let { values -> values.keySet().forEach { key ->
            when (val value = values.get(key)) {
                is String -> prefs.putString(key, value)
                is Float -> prefs.putFloat(key, value)
                is Int -> prefs.putInt(key, value)
                is Boolean -> prefs.putBoolean(key, value)
            }
        } }
        prefs.apply()
        respond(PiperSynth.STARTED, Process.myPid())
        when (msg.what) {
            PiperSynth.WARM -> {
                synth.warmUp()
                // Uses the synth's serial worker; empty speech confirms load completion.
                synth.speak("", false) { handler.post {
                    if (synth.ready) respond(PiperSynth.READY, synth.numSpeakers)
                    else respond(PiperSynth.ERROR, error = "Ses modeli yüklenemedi")
                } }
            }
            PiperSynth.SPEAK -> synth.speak(msg.data.getString("text").orEmpty(), msg.data.getBoolean("interrupt")) {
                handler.post {
                    if (!synth.ready || synth.lastError != null) respond(PiperSynth.ERROR, error = synth.lastError ?: "Ses modeli yüklenemedi")
                    else { respond(PiperSynth.READY, synth.numSpeakers); respond(PiperSynth.DONE, id) }
                }
            }
            PiperSynth.STOP -> synth.stop()
            PiperSynth.DELETE -> {
                val idToDelete = msg.data.getString("text").orEmpty()
                if (app.vela.core.voice.PiperCatalog.byId(idToDelete) != null) {
                    synth.deleteModelDir(app.vela.core.voice.VelaPiper.modelDirFor(this, idToDelete))
                }
                respond(PiperSynth.ACK)
            }
        }
        true
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean {
        synth.release()
        return false
    }
}
