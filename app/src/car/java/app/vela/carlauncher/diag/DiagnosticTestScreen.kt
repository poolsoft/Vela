package app.vela.carlauncher.diag

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vela.carlauncher.map.*
import app.vela.carlauncher.media.CarMediaService
import app.vela.carlauncher.media.MusicRepository
import app.vela.diag.ProcessDiagnostics
import app.vela.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All probes are explicit, cancellable and independent; nothing resumes on app startup. */
@Composable
internal fun DiagnosticTestScreen(
    onClose: () -> Unit,
    onSettings: () -> Unit,
    setMapLevel: (Int) -> Unit,
    mapContent: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val report = remember { TestReport(context) }
    var results by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var audio by remember { mutableStateOf<Uri?>(null) }
    var mapAttempt by remember { mutableStateOf<String?>(null) }
    val host = remember { MapRendererFailureHost() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { audio = it }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        FileLogger.i("DiagnosticTests", "Audio scan permission granted=$granted")
    }
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
    LaunchedEffect(report) { results = report.read(); loaded = true }
    val tests = remember {
        linkedMapOf(
            "ipc" to "1. Ayrı süreç bağlantısı (harita motoru yüklenmez)",
            "scan" to "2. Müzik dosyalarını tara (disk / MediaStore)",
            "prepare" to "3. Seçilen parçayı hazırla (ses çalmaz)",
            "play" to "4. Seçilen parçayı 5 saniye çal",
            "cover" to "5. Parça bilgisi ve kapak oku",
            "service" to "6. Vela müzik servisi ve bildirimi (ses çalmaz)",
            "empty" to "7. Boş harita: EGL / SDK / ilk kare",
            "style" to "8. Harita stili (yerel arşivler hariç)",
            "map" to "9. Mevcut ayarlarla harita (dosyaları önce ekle)",
        )
    }
    suspend fun save(id: String, text: String) {
        results = results + (id to text)
        report.write(id, text, results)
    }
    suspend fun execute(id: String): String = when (id) {
        "ipc" -> {
            val probe = MapProcessProbe(context)
            try {
                probe.start()
                val state = withTimeout(8_000) { probe.state.first {
                    it.status == MapProcessProbe.Status.READY || it.status == MapProcessProbe.Status.FAILED
                } }
                check(state.status == MapProcessProbe.Status.READY) { "Ayrı süreç bağlantısı kurulamadı" }
                "Harita süreci yanıt verdi; PID=${state.remotePid}"
            } finally { probe.close() }
        }
        "scan" -> {
            check(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) {
                "Önce müzik okuma iznini ver"
            }
            val repo = MusicRepository.getInstance(context)
            check(!repo.taraniyorMu.value) { "Başka bir tarama sürüyor; bitmesini bekle" }
            withTimeout(300_000) { repo.muzikKutuphanesiniTara(zorla = true) }
            check(repo.lastError.value == null) { repo.lastError.value.orEmpty() }
            "Tarama tamamlandı: ${repo.parcalar.value.size} parça"
        }
        "prepare", "play" -> {
            val uri = checkNotNull(audio) { "Önce bir ses dosyası seç" }
            testAudio(context, uri, id == "play")
        }
        "cover" -> {
            val uri = checkNotNull(audio) { "Önce bir ses dosyası seç" }
            withContext(Dispatchers.IO) {
                val reader = MediaMetadataRetriever()
                try {
                    ProcessDiagnostics.checkpointAndFlush("test cover: setDataSource")
                    reader.setDataSource(context, uri)
                    val title = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ProcessDiagnostics.checkpointAndFlush("test cover: embeddedPicture")
                    val cover = reader.embeddedPicture
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    if (cover != null) android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size, bounds)
                    if (cover != null) {
                        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Kapak biçimi okunamadı" }
                        val options = android.graphics.BitmapFactory.Options()
                        options.inSampleSize = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
                        ProcessDiagnostics.checkpointAndFlush("test cover: decodeBitmap")
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size, options)
                        checkNotNull(bitmap) { "Kapak çözülemedi" }.recycle()
                    }
                    "Bilgi okundu: ${title ?: "başlık yok"}; " + if (cover == null) "dosyada kapak yok"
                        else "kapak ${cover.size} bayt, ${bounds.outWidth}×${bounds.outHeight}"
                } finally { reader.release() }
            }
        }
        "service" -> {
            check(app.vela.BuildConfig.DIAGNOSTIC_MUSIC_DISABLED) { "Servis izolasyon testi tanı APK'sı gerektirir" }
            check(!CarMediaService.diagnosticReady.value) { "Müzik servisi zaten açık" }
            CarMediaService.diagnosticTestAllowed = true
            try {
                ProcessDiagnostics.checkpointAndFlush("test service: start")
                CarMediaService.baslat(context)
                withTimeout(15_000) { CarMediaService.diagnosticReady.first { it } }
                delay(5_000)
                check(CarMediaService.diagnosticReady.value) { "Servis gözlem sırasında kapandı" }
                "Vela servisi, MediaSession ve bildirim 5 saniye çalıştı"
            } finally {
                CarMediaService.diagnosticTestAllowed = false
                context.stopService(Intent(context, CarMediaService::class.java))
            }
        }
        else -> {
            host.state = IsolatedMapClient.State()
            setMapLevel(when (id) { "empty" -> 1; "style" -> 2; else -> 3 })
            mapAttempt = UUID.randomUUID().toString()
            try {
                val state = withTimeout(75_000) { snapshotFlow { host.state }.first { it.ready || it.failed } }
                check(state.ready && !state.failed) { state.stage }
                delay(5_000)
                check(!host.state.failed) { host.state.stage }
                "Stil ve ilk kare geldi; 5 saniye gözlendi"
            } finally {
                mapAttempt = null
                // Let Compose dispose the old Surface and its process before the next test.
                withContext(NonCancellable) { delay(500) }
            }
        }
    }
    fun start(ids: List<String>) {
        if (!loaded || job?.isActive == true) return
        job = scope.launch {
            runDiagnosticSequence(ids) { id ->
                running = id
                try {
                    // Persist BEFORE touching native code, not after the operation returns.
                    save(id, "ÇALIŞIYOR")
                    ProcessDiagnostics.checkpointAndFlush("test $id: begin")
                    val detail = execute(id)
                    save(id, "BAŞARILI: $detail")
                    true
                } catch (timeout: TimeoutCancellationException) {
                    save(id, "HATA: Süre aşıldı; son aşama için logu kontrol et")
                    false
                } catch (cancelled: CancellationException) {
                    withContext(NonCancellable) { runCatching { save(id, "DURDURULDU") } }
                    throw cancelled
                } catch (error: Exception) {
                    FileLogger.e("DiagnosticTests", "Test failed: $id", error)
                    val message = "HATA: ${error.message ?: error.javaClass.simpleName}"
                    runCatching { save(id, message) }.onFailure {
                        results = results + (id to "$message; sonuç dosyası yazılamadı")
                        FileLogger.e("DiagnosticTests", "Could not persist test result", it)
                    }
                    false
                } finally { running = null }
            }
        }
    }
    fun close() { job?.cancel(); onClose() }
    BackHandler { close() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("Adım adım tanı", style = MaterialTheme.typography.titleLarge)
            Text("Her adım ayrı çalışır. Hata olursa sıra durur; yeniden açılışta devam etmez. Sonuç: files/logs/diagnostic-tests.json",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = ::close) { Text("Kapat") }
                TextButton(onClick = { job?.cancel() }, enabled = running != null) { Text("Durdur") }
                TextButton(onClick = onSettings, enabled = running == null) { Text("Ayarlar") }
                TextButton(onClick = { start(tests.keys.toList()) }, enabled = loaded && running == null) { Text("Sırayla çalıştır") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, enabled = running == null) { Text("Ses dosyası seç") }
                OutlinedButton(onClick = { permission.launch(audioPermission) }, enabled = running == null) { Text("Tarama izni") }
            }
            Text(audio?.lastPathSegment ?: "Müzik için dosya seç. 3–5 temel Android oynatıcı/kapak testidir; 6 gerçek Vela servisidir. Oynatma testi sesli çalışır; çalma listen değişmez.",
                style = MaterialTheme.typography.bodySmall)
            if (mapAttempt != null) {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    key(mapAttempt) { CompositionLocalProvider(LocalMapRendererFailureHost provides host) { mapContent() } }
                }
                Text("Harita aşaması: ${host.state.stage}", style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tests.forEach { (id, title) ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text(title)
                            Text(results[id] ?: "Çalıştırılmadı", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { start(listOf(id)) }, enabled = loaded && running == null) { Text("Çalıştır") }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun testAudio(context: Context, uri: Uri, play: Boolean): String {
    ProcessDiagnostics.checkpointAndFlush("test audio: create")
    val player = MediaPlayer()
    val audioManager = context.getSystemService(android.media.AudioManager::class.java)
    val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { }
    var focusGranted = false
    try {
        ProcessDiagnostics.checkpointAndFlush("test audio: setDataSource")
        withContext(Dispatchers.IO) { player.setDataSource(context, uri) }
        withTimeout(30_000) {
            suspendCancellableCoroutine<Unit> { continuation ->
                player.setOnPreparedListener { if (continuation.isActive) continuation.resume(Unit) }
                player.setOnErrorListener { _, what, extra ->
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("MediaPlayer: $what/$extra"))
                    true
                }
                ProcessDiagnostics.checkpointAndFlush("test audio: prepareAsync")
                player.prepareAsync()
            }
        }
        if (!play) return "MediaPlayer hazır; süre=${player.duration} ms"
        var playbackError: String? = null
        player.setOnErrorListener { _, what, extra -> playbackError = "$what/$extra"; true }
        @Suppress("DEPRECATION")
        val focus = audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC,
            android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        focusGranted = focus == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        check(focusGranted) { "Ses odağı alınamadı" }
        ProcessDiagnostics.checkpointAndFlush("test audio: start")
        player.start()
        val start = player.currentPosition
        delay(5_000)
        check(playbackError == null) { "Oynatma hatası: $playbackError" }
        check(player.currentPosition > start) { "Oynatma konumu ilerlemedi" }
        return "MediaPlayer oynatma konumu ilerledi (sesi ayrıca dinleyerek doğrula)"
    } finally {
        ProcessDiagnostics.checkpointAndFlush("test audio: release")
        player.release()
        if (focusGranted) {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }
}

/** One bounded report, atomically replaced and fsynced. Kept next to the user's existing logs. */
private class TestReport(context: Context) {
    private val file = AtomicFile(File(context.getExternalFilesDir(null) ?: context.filesDir, "logs/diagnostic-tests.json"))
    private val run = UUID.randomUUID().toString()
    suspend fun read(): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            val values = JSONObject(String(file.readFully(), Charsets.UTF_8)).getJSONObject("results")
            values.keys().asSequence().associateWith {
                values.getString(it).let { result -> if (result == "ÇALIŞIYOR") "YARIM KALDI: önceki oturum bu adımda bitti" else result }
            }
        }.getOrDefault(emptyMap())
    }
    suspend fun write(id: String, status: String, results: Map<String, String>) = withContext(Dispatchers.IO) {
        val value = JSONObject().put("run", run).put("pid", android.os.Process.myPid())
            .put("time", System.currentTimeMillis()).put("test", id).put("status", status)
            .put("results", JSONObject(results)).toString(2)
        file.baseFile.parentFile?.mkdirs()
        val stream = file.startWrite()
        try { stream.write(value.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        FileLogger.i("DiagnosticTests", "run=$run test=$id $status")
        ProcessDiagnostics.checkpointAndFlush("test $id: $status")
    }
}
