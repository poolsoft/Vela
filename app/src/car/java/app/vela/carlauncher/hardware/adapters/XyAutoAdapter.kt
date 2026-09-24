package app.vela.carlauncher.hardware.adapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent
import app.vela.carlauncher.hardware.HeadUnitAdapter
import app.vela.carlauncher.hardware.HeadUnitListener
import java.util.Locale

/**
 * XyAuto tabanli Cin multimedya bas uniteleri adaptoru.
 * CAN-Bus hiz, voltaj, aydinlatma ve direksiyon/radyo tuslarini dinler.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class XyAutoAdapter : HeadUnitAdapter {

    private var dinleyici: HeadUnitListener? = null
    private var alici: BroadcastReceiver? = null

    override fun getUreticiAdi(): String = "XyAuto"

    override fun destekleniyorMu(context: Context): Boolean {
        val paketler = arrayOf(
            "com.xyauto.common",
            "com.acloud.stub.localmusic",
            "com.acloud.stub.extradio"
        )
        for (paket in paketler) {
            try {
                context.packageManager.getPackageInfo(paket, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        val cihazKimligi = ("${Build.MANUFACTURER} ${Build.BRAND} ${Build.DEVICE} ${Build.PRODUCT}").lowercase(Locale.US)
        return cihazKimligi.contains("xyauto") || cihazKimligi.contains("acloud")
    }

    override fun dinlemeyiBaslat(context: Context, listener: HeadUnitListener) {
        this.dinleyici = listener
        if (alici != null) return

        alici = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                intentiIsle(intent)
            }
        }

        val filtre = IntentFilter().apply {
            // Telemetri (CAN-Bus)
            addAction("xy.auto.canbus.speed")
            addAction("com.xygala.canbus.tata.speed")
            addAction("xy.auto.canbus.battery")
            addAction("xy.auto.canbus.light")
            addAction("xy.xygala.lamplet")

            // Muzik & Medya
            addAction("update.widget.playbtnstate")
            addAction("update.widget.songname")
            addAction("com.android.radio.widget.freq_volue")
            addAction("com.acloud.intent.play_status")
            addAction("xy.android.playpause")
            addAction("xy.android.nextmedia")
            addAction("xy.android.previousmedia")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(alici, filtre, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(alici, filtre)
            }
        } catch (_: Exception) {
            alici = null
        }
    }

    override fun dinlemeyiDurdur(context: Context) {
        alici?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
            alici = null
        }
        dinleyici = null
    }

    private fun intentiIsle(intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            "xy.auto.canbus.speed", "com.xygala.canbus.tata.speed" -> {
                val hiz = intent.getFloatExtra("speed", intent.getIntExtra("speed", 0).toFloat())
                dinleyici?.onHizGuncellendi(hiz)
            }
            "xy.auto.canbus.battery" -> {
                val voltaj = intent.getFloatExtra("voltage", 0f)
                dinleyici?.onAküVoltajGuncellendi(voltaj)
            }
            "xy.auto.canbus.light", "xy.xygala.lamplet" -> {
                val acik = intent.getBooleanExtra("state", false) || intent.getIntExtra("state", 0) > 0
                dinleyici?.onFarDurumuDegisti(acik)
            }
            "xy.android.playpause" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            "xy.android.nextmedia" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            "xy.android.previousmedia" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            "update.widget.songname" -> {
                val sarki = intent.getStringExtra("song_name") ?: ""
                val sanatci = intent.getStringExtra("singer_name") ?: ""
                dinleyici?.onParcaBilgisiDegisti(sarki, sanatci, null)
            }
            "update.widget.playbtnstate", "com.acloud.intent.play_status" -> {
                val caliyor = intent.getBooleanExtra("play_state", false) || intent.getIntExtra("play_state", 0) == 1
                dinleyici?.onCalmaDurumuDegisti(caliyor)
            }
        }
    }
}
