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
        val selected = CarLauncherSettings.ekolayzirPaketi.value
        if (!selected.isNullOrBlank()) {
            val intent = activityContext.packageManager.getLaunchIntentForPackage(selected)
                ?: Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).setPackage(selected)
            try {
                activityContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                Log.w(TAG, "Selected equalizer is unavailable: $selected", e)
                CarLauncherSettings.setEkolayzirPaketi(null)
                Toast.makeText(activityContext, "Seçilen ekolayzır açılamadı. Yeniden seçin.", Toast.LENGTH_SHORT).show()
            }
        }
        chooseEqualizer(activityContext)
    }

    fun equalizerLabel(): String = CarLauncherSettings.ekolayzirPaketi.value?.let {
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
    } ?: "Uygulama seçilmedi"

    fun equalizerIcon(): android.graphics.drawable.Drawable? = CarLauncherSettings.ekolayzirPaketi.value?.let {
        runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull()
    }

    fun chooseEqualizer(activityContext: Context) {
        val pm = activityContext.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val panels = pm.queryIntentActivities(Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL), 0)
        val apps = (panels + launchers).filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (apps.isEmpty()) {
            Toast.makeText(activityContext, "Seçilebilir uygulama bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = CarLauncherSettings.ekolayzirPaketi.value
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val app = apps[position]
                val dp = activityContext.resources.displayMetrics.density
                return android.widget.LinearLayout(activityContext).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
                    addView(android.widget.ImageView(activityContext).apply { setImageDrawable(app.loadIcon(pm)) },
                        android.widget.LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
                    addView(android.widget.TextView(activityContext).apply {
                        text = (if (app.activityInfo.packageName == selected) "✓  " else "") + app.loadLabel(pm)
                        textSize = 18f
                        setPadding((16 * dp).toInt(), 0, 0, 0)
                    })
                }
            }
        }
        android.app.AlertDialog.Builder(activityContext)
            .setTitle("Ekolayzır uygulamasını seç")
            .setAdapter(adapter) { _, index -> CarLauncherSettings.setEkolayzirPaketi(apps[index].activityInfo.packageName) }
            .setNeutralButton("Seçimi temizle") { _, _ -> CarLauncherSettings.setEkolayzirPaketi(null) }
            .setNegativeButton("İptal", null)
            .show()
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

}
