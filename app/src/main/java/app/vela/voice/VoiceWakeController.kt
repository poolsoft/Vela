package app.vela.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.vela.core.voice.VoiceGuide
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Controller for hands-free wake word detection (Open-Vocabulary KWS).
 *
 * Implements hardware Acoustic Echo Cancellation (AEC), Noise Suppression (NS),
 * and automatic TTS audio collision protection (pausing/discarding mic input while
 * VoiceGuide is speaking) to prevent false-triggers from speaker audio.
 *
 * Uses Sherpa-ONNX KeywordSpotter with dynamic Turkish BPE tokenization.
 * Operates independently from the active ASR engine (Whisper, SenseVoice, Mihuai).
 */
@Singleton
class VoiceWakeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceGuide: VoiceGuide,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _wakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakeEvents: SharedFlow<Unit> = _wakeEvents.asSharedFlow()

    private val isListening = AtomicBoolean(false)
    private var workerJob: Job? = null

    // Spotter and stream lifecycle management
    private val spotterLock = Any()
    @Volatile private var activeSpotter: KeywordSpotter? = null
    @Volatile private var activeStream: OnlineStream? = null
    @Volatile private var activeTokenizer: TurkishBpeTokenizer? = null

    @Volatile private var ttsQuietUntilMs = 0L

    init {
        voiceGuide.onSpeakingStateChanged = { speaking ->
            if (!speaking) {
                ttsQuietUntilMs = SystemClock.elapsedRealtime() + TTS_COOLDOWN_MS
            }
        }
    }

    fun isEnabled(): Boolean = app.vela.BuildConfig.NATIVE_VOICE_ENABLED && prefs.getBoolean(KEY_WAKE_ENABLED, DEFAULT_WAKE_ENABLED)

    fun setEnabled(enabled: Boolean) {
        if (enabled && !app.vela.BuildConfig.NATIVE_VOICE_ENABLED) return
        prefs.edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun isSilentMode(): Boolean = prefs.getBoolean(KEY_WAKE_SILENT_MODE, true)

    fun setSilentMode(silent: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_SILENT_MODE, silent).apply()
    }

    fun getWakePhrase(): String = prefs.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE) ?: DEFAULT_WAKE_PHRASE

    fun setWakePhrase(phrase: String) {
        val clean = phrase.trim().ifBlank { DEFAULT_WAKE_PHRASE }
        prefs.edit().putString(KEY_WAKE_PHRASE, clean).apply()
        updateKeywordsAndRefreshStream()
    }

    fun getWakeThreshold(): Float = prefs.getFloat(KEY_WAKE_THRESHOLD, DEFAULT_WAKE_THRESHOLD)

    fun setWakeThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.20f, 0.80f)
        prefs.edit().putFloat(KEY_WAKE_THRESHOLD, clamped).apply()
        updateKeywordsAndRefreshStream()
    }

    fun getWakeBoost(): Float = prefs.getFloat(KEY_WAKE_BOOST, DEFAULT_WAKE_BOOST)

    fun setWakeBoost(boost: Float) {
        val clamped = boost.coerceIn(1.0f, 3.0f)
        prefs.edit().putFloat(KEY_WAKE_BOOST, clamped).apply()
        updateKeywordsAndRefreshStream()
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Checks if KWS model files exist either in filesDir/kws/ or in the downloaded streaming ASR folder.
     */
    fun isWakeModelAvailable(): Boolean {
        return findModelFiles() != null
    }

    private data class KwsModelFiles(
        val encoder: File,
        val decoder: File,
        val joiner: File,
        val tokens: File,
        val modelType: String,
    )

    private fun findModelFiles(): KwsModelFiles? {
        val kwsDir = File(context.filesDir, "kws")
        val streamingEngine = AsrEngine.installed(context).firstOrNull { it.isStreaming }

        // Priority 1: dedicated kws folder; Priority 2: streaming ASR engine folder (shared weights)
        val searchDirs = listOfNotNull(
            kwsDir,
            streamingEngine?.dir(context),
        )

        for (dir in searchDirs) {
            val enc = File(dir, "encoder.int8.onnx")
            val dec = File(dir, "decoder.int8.onnx")
            val jn = File(dir, "joiner.int8.onnx")
            val tok = File(dir, "tokens.txt")
            if (enc.exists() && dec.exists() && jn.exists() && tok.exists()) {
                val mType = if (streamingEngine != null && dir == streamingEngine.dir(context)) {
                    streamingEngine.modelType
                } else {
                    "zipformer2"
                }
                return KwsModelFiles(enc, dec, jn, tok, mType)
            }
        }
        return null
    }

    @Synchronized
    fun startListening() {
        if (!isEnabled() || !hasMicPermission()) return
        if (isListening.getAndSet(true)) return

        workerJob = scope.launch(Dispatchers.IO) {
            runAudioLoop()
        }
    }

    @Synchronized
    fun stopListening() {
        if (!isListening.getAndSet(false)) return
        workerJob?.cancel()
        workerJob = null
        releaseSpotter()
    }

    fun pauseForAsr() {
        isListening.set(false)
        workerJob?.cancel()
        workerJob = null
    }

    fun resumeAfterAsr() {
        if (isEnabled() && hasMicPermission()) {
            startListening()
        }
    }

    fun getWakePhrases(): List<String> {
        val raw = getWakePhrase()
        return raw.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Updates keywords.txt with BPE tokenization and refreshes the spotter stream without reloading ONNX weights.
     */
    private fun updateKeywordsAndRefreshStream() {
        writeKeywordsFile(getWakePhrase())
        synchronized(spotterLock) {
            val spotter = activeSpotter ?: return
            activeStream?.release()
            activeStream = runCatching { spotter.createStream() }.getOrNull()
        }
    }

    fun writeKeywordsFile(phrase: String): File {
        val dir = File(context.filesDir, "kws").apply { mkdirs() }
        val file = File(dir, "keywords.txt")
        val boost = getWakeBoost()
        val threshold = getWakeThreshold()

        val modelFiles = findModelFiles()
        val tokenizer = modelFiles?.let { TurkishBpeTokenizer(it.tokens) }
        activeTokenizer = tokenizer

        val phrases = phrase.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf(DEFAULT_WAKE_PHRASE) }

        val lines = mutableListOf<String>()
        for (p in phrases) {
            val formatted = tokenizer?.formatKeywordLine(p, boost, threshold)
            if (formatted != null) {
                lines.add(formatted)
            } else {
                // Fallback: raw words with boost and threshold
                lines.add("${p.lowercase()} : $boost # $threshold")
            }
        }

        file.writeText(lines.joinToString("\n") + "\n")
        return file
    }

    private fun ensureSpotter(): Boolean {
        synchronized(spotterLock) {
            if (activeSpotter != null && activeStream != null) return true

            val modelFiles = findModelFiles() ?: return false
            val kwsDir = File(context.filesDir, "kws")
            val keywordsFile = File(kwsDir, "keywords.txt")
            if (!keywordsFile.exists() || keywordsFile.length() == 0L) {
                writeKeywordsFile(getWakePhrase())
            }

            val spotter = runCatching {
                KeywordSpotter(
                    assetManager = null, // Mutlak dosya yollari (filesDir) icin null verilmelidir; aksi halde C++ assetManager bekler ve coker
                    config = KeywordSpotterConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig = OnlineModelConfig(
                            transducer = OnlineTransducerModelConfig(
                                encoder = modelFiles.encoder.absolutePath,
                                decoder = modelFiles.decoder.absolutePath,
                                joiner = modelFiles.joiner.absolutePath,
                            ),
                            tokens = modelFiles.tokens.absolutePath,
                            modelType = modelFiles.modelType,
                            numThreads = 2,
                        ),
                        keywordsFile = keywordsFile.absolutePath,
                        keywordsScore = getWakeBoost(),
                        keywordsThreshold = getWakeThreshold(),
                        numTrailingBlanks = 1,
                    ),
                )
            }.onFailure {
                android.util.Log.e("VoiceWake", "KeywordSpotter native init failed: ${it.message}", it)
            }.getOrNull() ?: return false

            val stream = runCatching { spotter.createStream() }.getOrNull()
            if (stream == null) {
                spotter.release()
                return false
            }

            activeSpotter = spotter
            activeStream = stream
            return true
        }
    }

    private fun releaseSpotter() {
        synchronized(spotterLock) {
            runCatching { activeStream?.release() }
            runCatching { activeSpotter?.release() }
            activeStream = null
            activeSpotter = null
        }
    }

    private suspend fun runAudioLoop() {
        if (!ensureSpotter()) {
            isListening.set(false)
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
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
            audio?.release()
            isListening.set(false)
            return
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

        val buf = ShortArray(CHUNK_SIZE)
        try {
            audio.startRecording()
            while (scope.isActive && isListening.get()) {
                val read = audio.read(buf, 0, CHUNK_SIZE)
                if (read <= 0) continue

                // 1. Microphone Collision / Echo Protection:
                // If TTS is currently speaking or in cooldown, drop samples!
                val now = SystemClock.elapsedRealtime()
                if (voiceGuide.isSpeaking || now < ttsQuietUntilMs) {
                    continue
                }

                // 2. Feed audio into KeywordSpotter
                var spotted = false
                synchronized(spotterLock) {
                    val spotter = activeSpotter
                    val stream = activeStream
                    if (spotter != null && stream != null) {
                        val floats = FloatArray(read) { buf[it] / 32768f }
                        stream.acceptWaveform(floats, SAMPLE_RATE)
                        while (spotter.isReady(stream)) {
                            spotter.decode(stream)
                        }
                        val result = spotter.getResult(stream)
                        if (result.keyword.isNotBlank()) {
                            spotter.reset(stream)
                            spotted = true
                        }
                    }
                }

                if (spotted) {
                    onWakeWordSpotted()
                }
            }
        } catch (_: Throwable) {
            // Background listening error handled gracefully
        } finally {
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { audio.stop() }
            runCatching { audio.release() }
            isListening.set(false)
        }
    }

    /**
     * Called when the wake word is confirmed.
     * Plays a pleasant rising earcon and signals listeners.
     */
    fun onWakeWordSpotted() {
        playWakeChime()
        _wakeEvents.tryEmit(Unit)
    }

    /**
     * Two-tone pleasant wake earcon chime (D5 -> A5, 587 Hz -> 880 Hz).
     * Synthesized in-process with soft envelopes so no external media asset is needed.
     */
    fun playWakeChime() {
        Thread {
            runCatching {
                val sr = 22050
                fun tone(hz: Double, ms: Int): ShortArray {
                    val n = sr * ms / 1000
                    return ShortArray(n) { i ->
                        val fade = min(1.0, min(i / (sr * 0.012), (n - 1 - i) / (sr * 0.012)))
                        (sin(2.0 * PI * hz * i / sr) * 11000 * fade).toInt().toShort()
                    }
                }
                val pcm = tone(587.33, 90) + ShortArray(sr * 25 / 1000) + tone(880.0, 150)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(400)
                track.release()
            }
        }.start()
    }

    companion object {
        private const val PREFS_NAME = "vela_settings"
        const val KEY_WAKE_ENABLED = "wake_word_enabled"
        const val KEY_WAKE_SILENT_MODE = "wake_word_silent_mode"
        const val KEY_WAKE_PHRASE = "wake_word_phrase"
        const val KEY_WAKE_THRESHOLD = "wake_word_threshold"
        const val KEY_WAKE_BOOST = "wake_word_boost"

        const val DEFAULT_WAKE_ENABLED = false
        const val DEFAULT_WAKE_PHRASE = "Hey Kar"
        const val DEFAULT_WAKE_THRESHOLD = 0.45f
        const val DEFAULT_WAKE_BOOST = 1.6f

        const val KWS_MODEL_URL = "https://github.com/poolsoft/Vela/releases/download/asr-models/vela-kws-turkish.tar.gz"

        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 512
        // Cooldown after TTS stops talking before microphone listening resumes
        private const val TTS_COOLDOWN_MS = 350L
    }
}
