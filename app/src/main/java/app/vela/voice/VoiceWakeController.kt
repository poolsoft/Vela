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
import kotlin.math.sqrt

/**
 * Controller for hands-free wake word detection (KWS - Keyword Spotting).
 *
 * Implements hardware Acoustic Echo Cancellation (AEC), Noise Suppression (NS),
 * and automatic TTS audio collision protection (pausing/discarding mic input while
 * VoiceGuide is speaking) to prevent false-triggers from speaker audio.
 *
 * The wake phrase is customizable by the user (persisted in vela_settings).
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

    @Volatile private var ttsQuietUntilMs = 0L

    init {
        // Wire into VoiceGuide's speaking state: when TTS finishes, add a quiet cooldown
        // window so room reflections / vehicle speaker reverb completely die down.
        voiceGuide.onSpeakingStateChanged = { speaking ->
            if (!speaking) {
                ttsQuietUntilMs = SystemClock.elapsedRealtime() + TTS_COOLDOWN_MS
            }
        }
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_WAKE_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun getWakePhrase(): String = prefs.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE) ?: DEFAULT_WAKE_PHRASE

    fun setWakePhrase(phrase: String) {
        val clean = phrase.trim().ifBlank { DEFAULT_WAKE_PHRASE }
        prefs.edit().putString(KEY_WAKE_PHRASE, clean).apply()
        writeKeywordsFile(clean)
        if (isListening.get()) {
            restartListening()
        }
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun startListening() {
        if (!isEnabled() || !hasMicPermission()) return
        if (isListening.getAndSet(true)) return

        writeKeywordsFile(getWakePhrase())
        workerJob = scope.launch(Dispatchers.IO) {
            runAudioLoop()
        }
    }

    @Synchronized
    fun stopListening() {
        if (!isListening.getAndSet(false)) return
        workerJob?.cancel()
        workerJob = null
    }

    private fun restartListening() {
        stopListening()
        startListening()
    }

    private fun writeKeywordsFile(phrase: String): File {
        val dir = File(context.filesDir, "kws").apply { mkdirs() }
        val file = File(dir, "keywords.txt")
        // Keywords format for sherpa-onnx: phrase with score threshold
        file.writeText("${phrase.lowercase()} : 1.0\n")
        return file
    }

    private suspend fun runAudioLoop() {
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

        // Prepare sherpa-onnx KeywordSpotter if model exists
        val spotterBundle = createSpotterBundle()
        val spotter = spotterBundle?.spotter
        var stream: OnlineStream? = spotterBundle?.stream

        val buf = ShortArray(CHUNK_SIZE)
        try {
            audio.startRecording()
            while (scope.isActive && isListening.get()) {
                val read = audio.read(buf, 0, CHUNK_SIZE)
                if (read <= 0) continue

                // 1. Microphone Collision / Echo Protection:
                // If TTS is currently speaking or just finished within cooldown, drop samples!
                val now = SystemClock.elapsedRealtime()
                if (voiceGuide.isSpeaking || now < ttsQuietUntilMs) {
                    continue
                }

                // 2. Feed audio into KeywordSpotter if present
                if (spotter != null && stream != null) {
                    val floats = FloatArray(read) { buf[it] / 32768f }
                    stream.acceptWaveform(floats, SAMPLE_RATE)
                    while (spotter.isReady(stream)) {
                        spotter.decode(stream)
                    }
                    val result = spotter.getResult(stream)
                    if (result.keyword.isNotBlank()) {
                        spotter.reset(stream)
                        onWakeWordSpotted()
                    }
                }
            }
        } catch (_: Throwable) {
            // Background listening error handled gracefully
        } finally {
            runCatching { stream?.release() }
            runCatching { spotter?.release() }
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { audio.stop() }
            runCatching { audio.release() }
            isListening.set(false)
        }
    }

    private data class SpotterBundle(val spotter: KeywordSpotter, val stream: OnlineStream)

    private fun createSpotterBundle(): SpotterBundle? {
        val kwsDir = File(context.filesDir, "kws")
        val keywordsFile = File(kwsDir, "keywords.txt")
        if (!keywordsFile.exists()) {
            writeKeywordsFile(getWakePhrase())
        }

        // Look for model in filesDir/kws/ (or downloaded zipformer models)
        val encoder = File(kwsDir, "encoder.int8.onnx")
        val decoder = File(kwsDir, "decoder.int8.onnx")
        val joiner = File(kwsDir, "joiner.int8.onnx")
        val tokens = File(kwsDir, "tokens.txt")

        if (!encoder.exists() || !decoder.exists() || !joiner.exists() || !tokens.exists()) {
            return null
        }

        return runCatching {
            val spotter = KeywordSpotter(
                assetManager = context.assets,
                config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath,
                            joiner = joiner.absolutePath,
                        ),
                        tokens = tokens.absolutePath,
                        modelType = "zipformer",
                        numThreads = 2,
                    ),
                    keywordsFile = keywordsFile.absolutePath,
                    keywordsScore = 1.0f,
                    keywordsThreshold = 0.25f,
                    numTrailingBlanks = 1,
                ),
            )
            val stream = spotter.createStream()
            SpotterBundle(spotter, stream)
        }.getOrNull()
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
        const val KEY_WAKE_PHRASE = "wake_word_phrase"
        const val DEFAULT_WAKE_PHRASE = "Hey Vela"

        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 512
        // Cooldown after TTS stops talking before microphone listening resumes
        private const val TTS_COOLDOWN_MS = 350L
    }
}
