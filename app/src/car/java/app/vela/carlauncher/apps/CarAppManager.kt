package app.vela.carlauncher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.InternalApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Arac Uygulama Yoneticisi.
 * Cihazda yuklu baslatilabilir uygulamalari listeler ve acar.
 */
class CarAppManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CarAppManager"

        @Volatile
        private var instance: CarAppManager? = null

        fun getInstance(context: Context): CarAppManager {
            return instance ?: synchronized(this) {
                instance ?: CarAppManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val packageManager: PackageManager = context.packageManager

    /**
     * Cihazdaki tum baslatilabilir uygulamalari asenkron olarak yukler.
     */
    suspend fun yukluUygulamalariGetir(): List<AracUygulamasi> = withContext(Dispatchers.IO) {
        val uygulamaListesi = mutableListOf<AracUygulamasi>()

        try {
            val anaIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val cozumlenenler = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    anaIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                packageManager.queryIntentActivities(anaIntent, 0)
            }

            for (bilgi in cozumlenenler) {
                val paketAdi = bilgi.activityInfo.packageName
                // Kendimizi uygulama cekmecesinden gizleyebiliriz veya gosteririz
                if (paketAdi == context.packageName) continue

                val ad = bilgi.loadLabel(packageManager).toString()
                val aktiviteAdi = bilgi.activityInfo.name
                val ikon = bilgi.loadIcon(packageManager)

                uygulamaListesi.add(
                    AracUygulamasi(
                        ad = ad,
                        paketAdi = paketAdi,
                        aktiviteAdi = aktiviteAdi,
                        ikon = ikon
                    )
                )
            }

            uygulamaListesi.sortBy { it.ad.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Uygulama listesi alinamadi: ${e.message}")
        }

        InternalApp.values().map { internal ->
            AracUygulamasi(internal.getAd(context), internal.uri, "", internal.getIkon(context), true)
        } + uygulamaListesi.distinctBy { it.paketAdi }
    }

    /**
     * Belirtilen uygulamayi acar.
     */
    fun uygulamayiBaslat(paketAdi: String, onInternal: ((InternalApp) -> Unit)? = null): Boolean {
        if (InternalApp.isInternalUri(paketAdi)) {
            val internal = InternalApp.fromUri(paketAdi) ?: return false
            val open = onInternal ?: return false
            open(internal)
            return true
        }
        return try {
            val baslatIntent = packageManager.getLaunchIntentForPackage(paketAdi)
            if (baslatIntent != null) {
                baslatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(baslatIntent)
                true
            } else {
                Log.w(TAG, "Uygulama intent'i bulunamadi: $paketAdi")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uygulama baslatilamadi ($paketAdi): ${e.message}")
            false
        }
    }

    /**
     * Arac telefon / arama ekranini acar.
     */
    fun telefonArayuzunuAc() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Telefon arayuzu acilamadi: ${e.message}")
        }
    }

    /**
     * Arac radyo uygulamasini tespit edip acar.
     */
    fun radyoUygulamasiniAc(): Boolean {
        val olasiRadyoPaketleri = listOf(
            "com.acloud.stub.localradio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.ts.radio",
            "com.android.fmradio"
        )
        for (paket in olasiRadyoPaketleri) {
            if (uygulamayiBaslat(paket)) return true
        }
        return false
    }

    /**
     * Arac muzik uygulamasini tespit edip acar.
     */
    fun muzikUygulamasiniAc(): Boolean {
        val preferred = app.vela.carlauncher.settings.CarLauncherSettings.tercihEdilenMuzikUygulamasi.value
        if (!preferred.isNullOrBlank() && uygulamayiBaslat(preferred)) return true
        val olasiMuzikPaketleri = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.acloud.stub.localmusic",
            "com.syu.music",
            "com.microntek.music",
            "com.ts.music"
        )
        for (paket in olasiMuzikPaketleri) {
            if (uygulamayiBaslat(paket)) return true
        }
        return false
    }
}
