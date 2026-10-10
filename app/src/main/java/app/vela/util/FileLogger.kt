package app.vela.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dosya tabanli Gunluk Kayit Sistemi (FileLogger).
 * Logcat'e erisimin mumkun olmadigi arac teypleri (Head Unit) icin loglari
 * dogrudan kullanici tarafindan erisilebilen harici hafiza dizinine yazar:
 * Android/data/app.vela/files/logs/vela_app.log
 *
 * Ozellikler:
 * - Thread-safe ve arka plan HandlerThread ile asenkron calisir (UI thread'i asla bloke etmez).
 * - 2 MB dosya boyutu siniri (dosya doldugunda .old olarak arsivlenir).
 * - Logcat'e de eszamanli yazar.
 * - Tum unhandled crash durumlarini dosyaya aninda flush eder.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object FileLogger {

    private const val DEFAULT_TAG = "VelaApp"
    private const val MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "vela_app.log"
    private var processLabel = "unknown"
    private var oldLogFileName = "vela_app.log.old"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logDir: File? = null
    private var logFile: File? = null

    private var handlerThread: HandlerThread? = null
    private var logHandler: Handler? = null

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return

            try {
                // getExternalFilesDir(null) -> /storage/emulated/0/Android/data/app.vela/files/
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val dir = File(baseDir, LOG_DIR_NAME)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                logDir = dir
                processLabel = "${ProcessIdentity.name(context) ?: "unknown"} pid=${android.os.Process.myPid()}"
                val suffix = ProcessIdentity.fileSuffix(context)
                val fileName = if (suffix.isEmpty()) LOG_FILE_NAME else "vela${suffix.replace('-', '_')}.log"
                logFile = File(dir, fileName)
                oldLogFileName = "$fileName.old"

                val thread = HandlerThread("VelaFileLoggerThread", android.os.Process.THREAD_PRIORITY_BACKGROUND)
                thread.start()
                handlerThread = thread
                logHandler = Handler(thread.looper)

                isInitialized = true

                // Baslangic cihazi bilgisi logu
                val baslangicMesaji = buildString {
                    append("==================================================\n")
                    append("Vela Baslatildi: ").append(synchronized(dateFormat) { dateFormat.format(Date()) }).append("\n")
                    append("Cihaz: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n")
                    val sdkInt = Build.VERSION.SDK_INT
                    val release = Build.VERSION.RELEASE
                    val name = androidName(sdkInt)
                    append("Android Surumu: ")
                    if (name == null) append(release) else append(name)
                    append(" (API ").append(sdkInt).append(")")
                    // The teyp reports API 27 while RELEASE claims "10.0"; keep the raw value visible.
                    if (name != null && !release.startsWith(name)) append(", rom report=").append(release)
                    append("\n")
                    append("Uygulama Dizini: ").append(logFile?.absolutePath).append("\n")
                    append("==================================================")
                }
                i(DEFAULT_TAG, baslangicMesaji)

                // Yakalanmayan hatalar (Uncaught Exception) icin dinleyici kur
                val eskiHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler { t, e ->
                    try {
                        val sw = StringWriter()
                        e.printStackTrace(PrintWriter(sw))
                        val crashLog = "FATAL CRASH on thread [${t.name}]: ${e.message}\n$sw"
                        Log.e("VelaCrash", crashLog)
                        dosyayaSenkronYaz("FATAL", "CrashHandler", crashLog)
                    } catch (ignored: Exception) {
                    }
                    eskiHandler?.uncaughtException(t, e)
                }
            } catch (e: Exception) {
                Log.e(DEFAULT_TAG, "FileLogger baslatilamadi: ${e.message}", e)
            }
        }
    }

    fun v(tag: String, msg: String) = log("V", tag, msg)
    fun d(tag: String, msg: String) = log("D", tag, msg)
    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log("W", tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log("E", tag, msg, tr)

    private fun log(seviye: String, tag: String, mesaj: String, tr: Throwable? = null) {
        val tamMesaj = if (tr != null) {
            val sw = StringWriter()
            tr.printStackTrace(PrintWriter(sw))
            "$mesaj\n$sw"
        } else {
            mesaj
        }

        // Logcat'e yaz
        when (seviye) {
            "V" -> Log.v(tag, tamMesaj)
            "D" -> Log.d(tag, tamMesaj)
            "I" -> Log.i(tag, tamMesaj)
            "W" -> Log.w(tag, tamMesaj)
            "E" -> Log.e(tag, tamMesaj)
            else -> Log.i(tag, tamMesaj)
        }

        if (!isInitialized) return

        // Arka plan thread'e gonder
        logHandler?.post {
            dosyayaYaz(seviye, tag, tamMesaj)
        }
    }

    private fun dosyayaYaz(seviye: String, tag: String, mesaj: String) {
        val dosya = logFile ?: return
        try {
            // Boyut kontrolu ve rotasyon
            if (dosya.exists() && dosya.length() > MAX_FILE_SIZE_BYTES) {
                val dir = logDir
                if (dir != null) {
                    val oldFile = File(dir, oldLogFileName)
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                    dosya.renameTo(oldFile)
                }
            }

            val zaman = synchronized(dateFormat) { dateFormat.format(Date()) }
            val satir = "$zaman [$seviye] [$tag] [$processLabel] $mesaj\n"

            FileWriter(dosya, true).use { writer ->
                writer.write(satir)
                writer.flush()
            }
        } catch (e: Exception) {
            // Dosyaya yazarken hata olursa donguye girmemek icin sessiz kal
        }
    }

    /** Marketing Android version for an SDK level; the teyp reports API 27 with RELEASE "10.0". */
    private fun androidName(sdk: Int): String? = when (sdk) {
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31, 32 -> "12"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        else -> null
    }

    private fun dosyayaSenkronYaz(seviye: String, tag: String, mesaj: String) {
        val dosya = logFile ?: return
        try {
            val zaman = synchronized(dateFormat) { dateFormat.format(Date()) }
            val satir = "$zaman [$seviye] [$tag] [$processLabel] $mesaj\n"
            FileWriter(dosya, true).use { writer ->
                writer.write(satir)
                writer.flush()
            }
        } catch (ignored: Exception) {
        }
    }
}
