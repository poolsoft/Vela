package app.vela.carlauncher.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.util.Log
import android.widget.Toast
import app.vela.carlauncher.settings.CarLauncherSettings

/**
 * Evrensel Arac Donanim Yoneticisi (CarHardwareManager).
 * XYAuto, HCN ve Standart Android otomotiv teyp platformlarini algilar
 * ve donanimsal eylemleri (DSP/Ekolayzir, Ekran Kapatma, RAM Temizleme) yonetir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarHardwareManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CarHardwareManager"

        @Volatile
        private var instance: CarHardwareManager? = null

        fun getInstance(context: Context): CarHardwareManager {
            return instance ?: synchronized(this) {
                instance ?: CarHardwareManager(context.applicationContext).also { instance = it }
            }
        }

        fun dspAc(context: Context) = getInstance(context).openEqualizer(context)
        fun bellekTemizle(context: Context) = getInstance(context).cleanRam(context)
        fun ekranKapat(context: Context) = getInstance(context).turnOffScreen(context)
    }

    enum class Platform {
        XY_AUTO,
        HCN,
        STANDARD
    }

    private var platform: Platform = Platform.STANDARD

    init {
        algilaPlatform()
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun algilaPlatform() {
        platform = when {
            isPackageInstalled("sys.xy.tumu.app") || isPackageInstalled("com.acloud.stub.localmusic") -> {
                Log.d(TAG, "Algilanan Donanim Platformu: XYAuto")
                Platform.XY_AUTO
            }
            isPackageInstalled("com.hcn.AutoMediaPlayer") || isPackageInstalled("com.hcn.AutoSettings") -> {
                Log.d(TAG, "Algilanan Donanim Platformu: HCN")
                Platform.HCN
            }
            else -> {
                Log.d(TAG, "Algilanan Donanim Platformu: Standart Android")
                Platform.STANDARD
            }
        }
    }

    fun getPlatform(): Platform = platform

    /**
     * Donanimsal Ekolayzir / DSP ses efektlerini acar.
     */
    fun openEqualizer(activityContext: Context) {
        val secilenPaket = CarLauncherSettings.ekolayzirPaketi.value
        if (!secilenPaket.isNullOrBlank() && launchApp(activityContext, secilenPaket)) {
            return
        }

        // Platforma ozel paket denemeleri
        when (platform) {
            Platform.XY_AUTO -> {
                if (launchApp(activityContext, "sys.xy.tumu.app") || launchApp(activityContext, "com.xy.eq")) {
                    return
                }
            }
            Platform.HCN -> {
                if (launchApp(activityContext, "com.hcn.AutoSettings") || launchApp(activityContext, "com.hcn.dsp")) {
                    return
                }
            }
            Platform.STANDARD -> {
                // Standart Android DSP Paneli Intent'i
                try {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activityContext.startActivity(intent)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Standart audio effect kontrol paneli bulunamadi", e)
                }
            }
        }

        Toast.makeText(activityContext, "Ekolayzır uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
    }

    /**
     * Gece surusunde ekrani karartir veya kapatir.
     */
    fun turnOffScreen(activityContext: Context) {
        // Gece karartma katmanini etkinlestir
        val simdiki = CarLauncherSettings.geceKarartmaEtkin.value
        CarLauncherSettings.setGeceKarartmaEtkin(!simdiki)
        val mesaj = if (!simdiki) "Ekran karartma modu aktif" else "Ekran normale döndü"
        Toast.makeText(activityContext, mesaj, Toast.LENGTH_SHORT).show()
    }

    /**
     * Arka plandaki gereksiz bellek (RAM) tuketen uygulamalari temizler.
     */
    fun cleanRam(activityContext: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningApps = am.runningAppProcesses ?: emptyList()
            var killedCount = 0
            val myPkg = context.packageName

            for (proc in runningApps) {
                for (pkg in proc.pkgList) {
                    if (pkg != myPkg && !pkg.startsWith("android") && !pkg.startsWith("com.android.systemui")) {
                        am.killBackgroundProcesses(pkg)
                        killedCount++
                    }
                }
            }
            Toast.makeText(activityContext, "Bellek temizlendi ($killedCount süreç sonlandırıldı)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "RAM temizleme hatasi: ${e.message}")
            Toast.makeText(activityContext, "Bellek temizleme işlemi tamamlandı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(ctx: Context, pkg: String): Boolean {
        return try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
