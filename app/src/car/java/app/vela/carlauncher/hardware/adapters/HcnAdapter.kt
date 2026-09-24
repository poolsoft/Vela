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
 * HCN tabanli multimedya bas uniteleri adaptoru.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class HcnAdapter : HeadUnitAdapter {

    companion object {
        private const val ACTION_NEXT = "com.auto.apimediaplayer.notification.NEXT"
        private const val ACTION_PREVIOUS = "com.auto.apimediaplayer.notification.PREV"
        private const val ACTION_PLAY_PAUSE = "com.auto.apimediaplayer.notification.PLAYPAUSE"
    }

    private var dinleyici: HeadUnitListener? = null
    private var alici: BroadcastReceiver? = null

    override fun getUreticiAdi(): String = "HCN"

    override fun destekleniyorMu(context: Context): Boolean {
        val paketler = arrayOf(
            "com.hcn.AutoMediaPlayer",
            "com.hcn.autoradio",
            "com.hcn.mediaservice"
        )
        for (paket in paketler) {
            try {
                context.packageManager.getPackageInfo(paket, 0)
                return true
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        val cihazKimligi = ("${Build.MANUFACTURER} ${Build.BRAND} ${Build.DEVICE} ${Build.PRODUCT}").lowercase(Locale.US)
        return cihazKimligi.contains("hcn")
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
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_PLAY_PAUSE)
            addAction("com.txznet.extra.next")
            addAction("com.txznet.extra.pre")
            addAction("com.txznet.extra.play")
            addAction("com.txznet.extra.pause")
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

    override fun muzikOynat(context: Context) {
        context.sendBroadcast(Intent(ACTION_PLAY_PAUSE))
    }

    override fun muzikDuraklat(context: Context) {
        context.sendBroadcast(Intent(ACTION_PLAY_PAUSE))
    }

    override fun sonrakiParca(context: Context) {
        context.sendBroadcast(Intent(ACTION_NEXT))
    }

    override fun oncekiParca(context: Context) {
        context.sendBroadcast(Intent(ACTION_PREVIOUS))
    }

    private fun intentiIsle(intent: Intent) {
        when (intent.action) {
            ACTION_NEXT, "com.txznet.extra.next" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            ACTION_PREVIOUS, "com.txznet.extra.pre" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            ACTION_PLAY_PAUSE, "com.txznet.extra.play", "com.txznet.extra.pause" -> {
                dinleyici?.onMedyaTusuBasildi(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
    }
}
