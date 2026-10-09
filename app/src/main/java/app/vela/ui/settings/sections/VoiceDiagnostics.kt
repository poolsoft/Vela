package app.vela.ui.settings.sections

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vela.diag.ProcessDiagnostics
import app.vela.ui.VoiceSearch
import app.vela.ui.map.MapViewModel
import app.vela.ui.settings.Hint
import app.vela.ui.settings.SettingsScaffold
import app.vela.util.FileLogger
import kotlinx.coroutines.*
import java.io.File

/** Explicit system-engine probes. Never loads sherpa-onnx or starts a background microphone. */
@Composable
internal fun VoiceDiagnosticsScreen(vm: MapViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Test seçin. Açılışta hiçbir ses testi çalışmaz.") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val reports = remember { mutableStateListOf<String>() }
    fun record(step: String, result: String) {
        status = result
        val entry = "${System.currentTimeMillis()} pid=${android.os.Process.myPid()} $step: $result"
        reports.add(entry)
        if (reports.size > 100) reports.removeAt(0)
        val text = reports.joinToString("\n", postfix = "\n")
        FileLogger.i("VoiceTests", entry)
        ProcessDiagnostics.checkpoint("voice test: $step $result")
        // Finish the write even if the user leaves this screen immediately afterwards.
        persistVoiceTest(context.applicationContext, text)
    }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        busy = false
        val heard = response.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.any { it.isNotBlank() } == true
        record("system recognition", when {
            response.resultCode != Activity.RESULT_OK -> "İptal edildi veya sağlayıcı sonuç döndürmedi."
            heard -> "BAŞARILI: Konuşma tanındı. Test sonucu arama veya rota başlatmaz."
            else -> "HATA: Sağlayıcı boş sonuç döndürdü."
        })
    }
    DisposableEffect(Unit) {
        onDispose { job?.cancel(); vm.stopVoiceTest() }
    }
    BackHandler { onBack() }
    SettingsScaffold("Sesli komut ve TTS testleri", onBack) { topRow ->
        Hint("Google TTS yalnız konuşma isteğinde açılır. Yerel ASR, Piper ve Hey Vela geçici olarak kapalıdır. Sistem ses motoru ayrı uygulama sürecinde çalışır.")
        FilledTonalButton(enabled = !busy, modifier = topRow, onClick = {
            busy = true
            job = scope.launch {
                try {
                    record("providers", "ÇALIŞIYOR")
                    val engines = withContext(Dispatchers.IO) { vm.voiceEngines() }
                    val providers = withContext(Dispatchers.IO) { VoiceSearch.providers(context) }
                    record("providers", "Google TTS: ${if (engines.any { it.packageName == "com.google.android.tts" }) "kurulu" else "bulunamadı"}; konuşma sağlayıcısı: ${providers.size}")
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    FileLogger.e("VoiceTests", "Provider query failed", error)
                    record("providers", "HATA: ${error.javaClass.simpleName}")
                } finally { busy = false }
            }
        }) { Text("1. Ses sağlayıcılarını kontrol et") }
        FilledTonalButton(enabled = !busy, onClick = {
            busy = true
            job = scope.launch {
                try {
                    record("google tts", "ÇALIŞIYOR")
                    // Persist the native/Binder-entry stage before speaking.
                    withContext(Dispatchers.IO) { ProcessDiagnostics.checkpointAndFlush("voice test: Google TTS begin") }
                    vm.testVoice()
                    withTimeout(30_000) {
                        while (!vm.voiceTestFinished()) delay(100)
                    }
                    record("google tts", if (vm.voiceWorking() == true) "BAŞARILI: Motor konuşmayı tamamladı; sesi ayrıca dinleyerek doğrulayın." else "HATA: ${vm.voiceTestStatus()}")
                } catch (error: Exception) {
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    FileLogger.e("VoiceTests", "Google TTS test failed", error)
                    record("google tts", if (error is TimeoutCancellationException) "HATA: 30 saniyede tamamlanmadı." else "HATA: ${error.javaClass.simpleName}")
                } finally { vm.stopVoiceTest(); busy = false }
            }
        }) { Text("2. Google TTS ile konuş") }
        FilledTonalButton(enabled = !busy, onClick = {
            record("system recognition", "ÇALIŞIYOR")
            try {
                check(VoiceSearch.hasProvider(context)) { "Konuşma tanıma sağlayıcısı kurulu değil" }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    VoiceSearch.launchComponent(context)?.let { component = it }
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, app.vela.ui.AppLocale.effective().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Sesli komut testi")
                }
                busy = true
                recognizer.launch(intent)
            } catch (error: Exception) {
                busy = false
                FileLogger.e("VoiceTests", "Recognition provider failed", error)
                record("system recognition", "HATA: ${error.message}")
            }
        }) { Text("3. Sistem mikrofonu ve konuşma tanıma") }
        FilledTonalButton(enabled = busy, onClick = {
            job?.cancel()
            vm.stopVoiceTest()
            busy = false
            record("stop", "DURDURULDU")
        }) { Text("Testi durdur") }
        Spacer(Modifier.height(12.dp))
        Text(status)
        Hint("Kayıt: Android/data/app.vela/files/logs/voice-tests.txt. Google TTS ile konuşma tanıma ayrı bileşenlerdir; birinin kurulu olması diğerinin var olduğunu göstermez.")
    }
}

// Serialize complete snapshots on one existing-style IO worker; no microphone or TTS here.
private val voiceReportWorker by lazy {
    java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VoiceTestReport").apply { isDaemon = true }
    }
}

private fun persistVoiceTest(context: android.content.Context, text: String) {
    voiceReportWorker.execute {
        for (directory in listOf(File(context.filesDir, "diag"), File(context.getExternalFilesDir(null) ?: context.filesDir, "logs"))) {
            runCatching {
                check(directory.isDirectory || directory.mkdirs())
                val file = android.util.AtomicFile(File(directory, "voice-tests.txt"))
                val stream = file.startWrite()
                try {
                    stream.write(text.toByteArray(Charsets.UTF_8))
                    stream.fd.sync()
                    file.finishWrite(stream)
                } catch (error: Exception) { file.failWrite(stream); throw error }
            }.onFailure { FileLogger.e("VoiceTests", "Could not save report", it) }
        }
    }
}
