package app.vela.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-device speech-to-text for voice search (tier-1). Loads whichever [AsrEngine] the user has
 * active - **Whisper tiny** (multilingual default), **SenseVoice** (en/zh/ja/ko/yue), **Moonshine**
 * (English), or **Seda** (Turkish streaming Zipformer) - through the bundled sherpa-onnx runtime.
 *
 * For offline models (Whisper/SenseVoice/Moonshine), runs [OfflineRecognizer].
 * For streaming models (Seda Tiny/Seda), runs [OnlineRecognizer] with chunked decode.
 */
@Singleton
class AsrRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceGuide: app.vela.core.voice.VoiceGuide,
) {
    private val loadLock = Any()
    @Volatile private var offlineRecognizer: OfflineRecognizer? = null
    @Volatile private var onlineRecognizer: OnlineRecognizer? = null
    @Volatile private var loadedKey: String? = null

    /** Non-zero while a [listen] is inside the native recognizer. [release] refuses to free the
     *  model while this is set to prevent use-after-free native crashes. */
    private val inFlight = java.util.concurrent.atomic.AtomicInteger(0)

    /** Idle-reap timer to drop models after a quiet period. */
    private val reaper by lazy {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "asr-reaper").apply { isDaemon = true }
        }
    }
    @Volatile private var reapTask: java.util.concurrent.ScheduledFuture<*>? = null

    init {
        app.vela.ui.MemoryPressure.register { level ->
            if (app.vela.ui.MemoryPressure.isSevere(level)) release()
        }
    }

    private fun armIdleReap() {
        reapTask?.cancel(false)
        reapTask = runCatching {
            reaper.schedule({ release() }, REAP_IDLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }.getOrNull()
    }

    /**
     * Free the native recognizer (both offline and online engines).
     */
    fun release() {
        if (inFlight.get() > 0) {
            android.util.Log.i(TAG, "release skipped, listen in flight")
            return
        }
        synchronized(loadLock) {
            offlineRecognizer?.let { runCatching { it.release() } }
            onlineRecognizer?.let { runCatching { it.release() } }
            offlineRecognizer = null
            onlineRecognizer = null
            loadedKey = null
            android.util.Log.i(TAG, "recognizers released")
        }
    }

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    @Volatile private var focusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        focusRequest = req
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        focusRequest?.let { req -> runCatching { am.abandonAudioFocusRequest(req) } }
        focusRequest = null
    }

    private companion object {
        const val TAG = "VELAASR"
        const val KEY_LOAD_STRIKES = "asr_load_strikes_"
        const val KEY_MODEL_BAD = "asr_model_bad_"
        const val SAMPLE_RATE = 16000
        const val VAD_WINDOW = 512
        const val MAX_SECONDS = 15
        const val REAP_IDLE_MS = 120_000L
    }

    private fun prefs() = context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE)
    private fun isQuarantined(engine: AsrEngine) = prefs().getBoolean(KEY_MODEL_BAD + engine.id, false)

    fun isInstalled(): Boolean = AsrEngine.installed(context).any { !isQuarantined(it) }

    fun clearQuarantine(engine: AsrEngine = AsrEngine.DEFAULT) {
        prefs().edit()
            .putBoolean(KEY_MODEL_BAD + engine.id, false)
            .putInt(KEY_LOAD_STRIKES + engine.id, 0).apply()
    }

    fun clearAllQuarantines() {
        val editor = prefs().edit()
        AsrEngine.entries.forEach { engine ->
            editor.putBoolean(KEY_MODEL_BAD + engine.id, false)
            editor.putInt(KEY_LOAD_STRIKES + engine.id, 0)
        }
        editor.apply()
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun appLang(): String =
        app.vela.ui.AppLocale.effective().language.let { if (it == "iw") "he" else it }

    private fun engineForNow(): AsrEngine = AsrEngine.forRecognition(context, appLang())

    private fun pinnedLang(engine: AsrEngine): String {
        val l = appLang()
        return when (engine) {
            AsrEngine.WHISPER_TINY -> l.takeIf { it in app.vela.ui.AppLocale.SUPPORTED } ?: ""
            AsrEngine.SENSE_VOICE -> l.takeIf { it in AsrEngine.SENSE_VOICE_LANGS } ?: "auto"
            AsrEngine.MOONSHINE -> ""
            AsrEngine.SEDA_TINY, AsrEngine.SEDA -> "tr"
        }
    }

    fun warmUp() {
        if (!app.vela.BuildConfig.NATIVE_VOICE_ENABLED || !AsrEngine.anyInstalled(context)) return
        if (app.vela.ui.MemoryPressure.lowRam) {
            android.util.Log.i(TAG, "skipping ASR warm-up on a low-RAM device, will load on first listen")
            return
        }
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runCatching {
                val engine = engineForNow()
                if (engine.isStreaming) ensureOnlineRecognizer() else ensureOfflineRecognizer()
            }
        }, "asr-warmup").start()
    }

    private fun ensureOfflineRecognizer(): OfflineRecognizer? {
        if (!app.vela.BuildConfig.NATIVE_VOICE_ENABLED) return null
        val engine = engineForNow()
        if (engine.isStreaming) return null
        val lang = pinnedLang(engine)
        val key = "${engine.id}|$lang"
        offlineRecognizer?.let { if (loadedKey == key) return it }
        synchronized(loadLock) {
            offlineRecognizer?.let { if (loadedKey == key) return it else runCatching { it.release() } }
            offlineRecognizer = null
            onlineRecognizer?.let { runCatching { it.release() } }
            onlineRecognizer = null
            if (!engine.isInstalled(context)) return null

            val prefs = prefs()
            val strikesKey = KEY_LOAD_STRIKES + engine.id
            val badKey = KEY_MODEL_BAD + engine.id
            val strikes = prefs.getInt(strikesKey, 0)
            if (strikes >= 2) {
                android.util.Log.e(TAG, "two ASR loads never returned (native crash) - quarantining ${engine.id}")
                prefs.edit().putInt(strikesKey, 0).putBoolean(badKey, true).apply()
                runCatching { engine.dir(context).deleteRecursively() }
                return null
            }
            if (prefs.getBoolean(badKey, false)) return null
            prefs.edit().putInt(strikesKey, strikes + 1).apply()

            val dir = engine.dir(context)
            fun p(name: String) = File(dir, name).absolutePath
            val modelConfig = when (engine) {
                AsrEngine.WHISPER_TINY -> OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = p("tiny-encoder.int8.onnx"),
                        decoder = p("tiny-decoder.int8.onnx"),
                        language = lang,
                        task = "transcribe",
                        tailPaddings = -1,
                    ),
                    tokens = p("tiny-tokens.txt"),
                    numThreads = 2,
                    modelType = engine.modelType,
                )
                AsrEngine.SENSE_VOICE -> OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = p("model.int8.onnx"),
                        language = lang,
                        useInverseTextNormalization = true,
                    ),
                    tokens = p("tokens.txt"),
                    numThreads = 2,
                    modelType = engine.modelType,
                )
                AsrEngine.MOONSHINE -> OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        preprocessor = p("preprocess.onnx"),
                        encoder = p("encode.int8.onnx"),
                        uncachedDecoder = p("uncached_decode.int8.onnx"),
                        cachedDecoder = p("cached_decode.int8.onnx"),
                    ),
                    tokens = p("tokens.txt"),
                    numThreads = 2,
                    modelType = engine.modelType,
                )
                AsrEngine.SEDA_TINY, AsrEngine.SEDA -> null
            } ?: return null

            val r = runCatching {
                OfflineRecognizer(
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = modelConfig,
                    ),
                )
            }.onFailure {
                android.util.Log.e(TAG, "native offline ASR load failed (${engine.id}): ${it::class.java.simpleName}: ${it.message}", it)
            }.getOrNull()

            prefs.edit().putInt(strikesKey, 0).apply()
            offlineRecognizer = r
            loadedKey = key
            if (r != null) armIdleReap()
            return r
        }
    }

    private fun ensureOnlineRecognizer(): OnlineRecognizer? {
        if (!app.vela.BuildConfig.NATIVE_VOICE_ENABLED) return null
        val engine = engineForNow()
        if (!engine.isStreaming) return null
        val lang = pinnedLang(engine)
        val key = "${engine.id}|$lang"
        onlineRecognizer?.let { if (loadedKey == key) return it }
        synchronized(loadLock) {
            onlineRecognizer?.let { if (loadedKey == key) return it else runCatching { it.release() } }
            onlineRecognizer = null
            offlineRecognizer?.let { runCatching { it.release() } }
            offlineRecognizer = null
            if (!engine.isInstalled(context)) return null

            val prefs = prefs()
            val strikesKey = KEY_LOAD_STRIKES + engine.id
            val badKey = KEY_MODEL_BAD + engine.id
            val strikes = prefs.getInt(strikesKey, 0)
            if (strikes >= 2) {
                android.util.Log.e(TAG, "two ASR loads never returned (native crash) - quarantining ${engine.id}")
                prefs.edit().putInt(strikesKey, 0).putBoolean(badKey, true).apply()
                runCatching { engine.dir(context).deleteRecursively() }
                return null
            }
            if (prefs.getBoolean(badKey, false)) return null
            prefs.edit().putInt(strikesKey, strikes + 1).apply()

            val dir = engine.dir(context)
            fun p(name: String) = File(dir, name).absolutePath
            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = p("encoder.int8.onnx"),
                    decoder = p("decoder.onnx"),
                    joiner = p("joiner.int8.onnx"),
                ),
                tokens = p("tokens.txt"),
                numThreads = 2,
                modelType = engine.modelType,
            )

            val r = runCatching {
                OnlineRecognizer(
                    context.assets,
                    OnlineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = modelConfig,
                        enableEndpoint = true,
                    ),
                )
            }.onFailure {
                android.util.Log.e(TAG, "native online ASR load failed (${engine.id}): ${it::class.java.simpleName}: ${it.message}", it)
            }.getOrNull()

            prefs.edit().putInt(strikesKey, 0).apply()
            onlineRecognizer = r
            loadedKey = key
            if (r != null) armIdleReap()
            return r
        }
    }

    suspend fun listen(
        onLevel: (Float) -> Unit,
        onListening: () -> Unit,
        canceled: () -> Boolean,
    ): VoiceResult = withContext(Dispatchers.Default) {
        inFlight.incrementAndGet()
        try {
            val engine = engineForNow()
            if (engine.isStreaming) {
                listenStreaming(onLevel, onListening, canceled)
            } else {
                listenBatch(onLevel, onListening, canceled)
            }
        } finally {
            inFlight.decrementAndGet()
            armIdleReap()
        }
    }

    /**
     * Streaming recognition loop for Seda (Zipformer Transducer) models.
     * Feeds audio chunks into [OnlineRecognizer] continuously and ends as soon as speech finishes.
     */
    private suspend fun listenStreaming(
        onLevel: (Float) -> Unit,
        onListening: () -> Unit,
        canceled: () -> Boolean,
    ): VoiceResult = withContext(Dispatchers.Default) {
        fun fail(reason: VoiceResult.Reason, detail: String? = null): VoiceResult.Failed {
            android.util.Log.e(TAG, "streaming listen failed: $reason${detail?.let { " ($it)" } ?: ""}")
            return VoiceResult.Failed(reason, detail)
        }
        val rec = ensureOnlineRecognizer()
            ?: return@withContext fail(VoiceResult.Reason.MODEL, "model absent or native load failed")
        if (!hasMicPermission()) return@withContext fail(VoiceResult.Reason.PERMISSION)

        val vad = runCatching {
            Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = File(engineForNow().dir(context), AsrEngine.VAD).absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.6f,
                        minSpeechDuration = 0.25f,
                        windowSize = VAD_WINDOW,
                        maxSpeechDuration = MAX_SECONDS.toFloat(),
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                ),
            )
        }.getOrNull() ?: return@withContext fail(VoiceResult.Reason.VAD, "silero VAD would not construct")

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val audio = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE * 2),
            )
        }.getOrNull()
        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) {
            val detail = if (audio == null) "constructor threw" else "state=${audio.state} minBuf=$minBuf"
            audio?.release(); vad.release()
            return@withContext fail(VoiceResult.Reason.AUDIO_INIT, detail)
        }

        val audioSessionId = audio.audioSessionId
        val aec = runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val ns = runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()

        val stream = runCatching { rec.createStream() }.getOrNull()
        if (stream == null) {
            vad.release(); audio.release()
            return@withContext fail(VoiceResult.Reason.MODEL, "could not create online stream")
        }

        val buf = ShortArray(VAD_WINDOW)
        var total = 0
        var sawSpeech = false
        var speechEnded = false

        try {
            voiceGuide.stop()
            requestAudioFocus()
            audio.startRecording()
            onListening()
            while (!canceled() && !speechEnded && total < SAMPLE_RATE * MAX_SECONDS) {
                val n = audio.read(buf, 0, VAD_WINDOW)
                if (n <= 0) continue
                if (voiceGuide.isSpeaking) {
                    onLevel(0f)
                    continue
                }
                val f = FloatArray(n) { buf[it] / 32768f }
                onLevel(rms(f))
                total += n

                stream.acceptWaveform(f, SAMPLE_RATE)
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }

                if (n == VAD_WINDOW) vad.acceptWaveform(f)
                if (vad.isSpeechDetected()) sawSpeech = true
                if (!vad.empty() || (sawSpeech && rec.isEndpoint(stream))) {
                    speechEnded = true
                }
            }
        } catch (t: Throwable) {
            runCatching { stream.release() }
            runCatching { vad.release() }
            return@withContext fail(VoiceResult.Reason.RECORDING, t.message ?: t::class.java.simpleName)
        } finally {
            abandonAudioFocus()
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { audio.stop() }
            runCatching { audio.release() }
            vad.release()
        }

        if (!sawSpeech && total < SAMPLE_RATE / 2) {
            runCatching { stream.release() }
            return@withContext VoiceResult.NoSpeech
        }

        val text = runCatching {
            stream.inputFinished()
            while (rec.isReady(stream)) {
                rec.decode(stream)
            }
            val t = rec.getResult(stream).text
            stream.release()
            t
        }.getOrNull().orEmpty()

        val query = app.vela.core.voice.SpeechText.cleanSearchTranscript(text).ifBlank { null }
        if (query == null) VoiceResult.NoSpeech else VoiceResult.Text(query)
    }

    /**
     * Batch recognition loop for Whisper / SenseVoice / Moonshine models.
     */
    private suspend fun listenBatch(
        onLevel: (Float) -> Unit,
        onListening: () -> Unit,
        canceled: () -> Boolean,
    ): VoiceResult = withContext(Dispatchers.Default) {
        fun fail(reason: VoiceResult.Reason, detail: String? = null): VoiceResult.Failed {
            android.util.Log.e(TAG, "batch listen failed: $reason${detail?.let { " ($it)" } ?: ""}")
            return VoiceResult.Failed(reason, detail)
        }
        val rec = ensureOfflineRecognizer()
            ?: return@withContext fail(VoiceResult.Reason.MODEL, "model absent or native load failed")
        if (!hasMicPermission()) return@withContext fail(VoiceResult.Reason.PERMISSION)

        val vad = runCatching {
            Vad(
                config = VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(
                        model = File(engineForNow().dir(context), AsrEngine.VAD).absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.6f,
                        minSpeechDuration = 0.25f,
                        windowSize = VAD_WINDOW,
                        maxSpeechDuration = MAX_SECONDS.toFloat(),
                    ),
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                ),
            )
        }.getOrNull() ?: return@withContext fail(VoiceResult.Reason.VAD, "silero VAD would not construct")

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val audio = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE * 2),
            )
        }.getOrNull()
        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) {
            val detail = if (audio == null) "constructor threw" else "state=${audio.state} minBuf=$minBuf"
            audio?.release(); vad.release()
            return@withContext fail(VoiceResult.Reason.AUDIO_INIT, detail)
        }

        val audioSessionId = audio.audioSessionId
        val aec = runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()
        val ns = runCatching {
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            } else null
        }.getOrNull()

        val buf = ShortArray(VAD_WINDOW)
        val chunks = ArrayList<FloatArray>()
        var total = 0
        var sawSpeech = false
        var segment: FloatArray? = null
        try {
            voiceGuide.stop()
            requestAudioFocus()
            audio.startRecording()
            onListening()
            while (!canceled() && segment == null && total < SAMPLE_RATE * MAX_SECONDS) {
                val n = audio.read(buf, 0, VAD_WINDOW)
                if (n <= 0) continue
                if (voiceGuide.isSpeaking) {
                    onLevel(0f)
                    continue
                }
                val f = FloatArray(n) { buf[it] / 32768f }
                onLevel(rms(f))
                chunks.add(f)
                total += n
                if (n == VAD_WINDOW) vad.acceptWaveform(f)
                if (vad.isSpeechDetected()) sawSpeech = true
                if (!vad.empty()) {
                    segment = vad.front().samples
                    vad.pop()
                }
            }
        } catch (t: Throwable) {
            runCatching { vad.release() }
            return@withContext fail(VoiceResult.Reason.RECORDING, t.message ?: t::class.java.simpleName)
        } finally {
            abandonAudioFocus()
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { audio.stop() }
            runCatching { audio.release() }
        }

        val samples = segment ?: run {
            if (!sawSpeech && total < SAMPLE_RATE / 2) { vad.release(); return@withContext VoiceResult.NoSpeech }
            val out = FloatArray(total)
            var off = 0
            for (c in chunks) { c.copyInto(out, off, 0, min(c.size, out.size - off)); off += c.size }
            out
        }
        vad.release()

        val text = runCatching {
            val stream = rec.createStream()
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            val t = rec.getResult(stream).text
            stream.release()
            t
        }.getOrNull().orEmpty()

        val query = app.vela.core.voice.SpeechText.cleanSearchTranscript(text).ifBlank { null }
        if (query == null) VoiceResult.NoSpeech else VoiceResult.Text(query)
    }

    private fun rms(f: FloatArray): Float {
        if (f.isEmpty()) return 0f
        var sum = 0.0
        for (v in f) sum += v.toDouble() * v
        return (sqrt(sum / f.size) * 8.5f).toFloat().coerceIn(0f, 1f)
    }
}
