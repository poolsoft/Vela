package app.vela.carlauncher.media.adapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.File

/**
 * Cin Teyp Otomotiv Muzik Adaptoru (XyAutoMusicAdapter).
 * XYAuto / TopWay / FYT tabanli Android teyplerin yerel radyo ve muzik
 * servis yayinlarini dinler ve kontrol komutlarini gonderir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class XyAutoMusicAdapter(
    private val context: Context,
    private val onDurumDegisti: () -> Unit
) : BaseMediaAdapter() {

    private var baslikMetni: String = ""
    private var sanatciMetni: String = ""
    private var kapakBitmap: Bitmap? = null
    private var caliyor: Boolean = false
    private var aktif: Boolean = false

    private val alici = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return

            when (action) {
                "update.widget.playbtnstate" -> {
                    caliyor = intent.getBooleanExtra("PlayState", false)
                    aktif = true
                    onDurumDegisti()
                }
                "update.widget.songname" -> {
                    val curSong = intent.getStringExtra("curplaysong")
                    val picPath = intent.getStringExtra("artistPicPath")

                    if (!curSong.isNullOrBlank()) {
                        if (curSong.contains(" - ")) {
                            val parts = curSong.split(" - ", limit = 2)
                            baslikMetni = parts[0].trim()
                            sanatciMetni = parts.getOrElse(1) { "" }.trim()
                        } else {
                            baslikMetni = curSong.trim()
                            sanatciMetni = "Bilinmeyen Sanatçı"
                        }
                    }

                    if (!picPath.isNullOrBlank()) {
                        val file = File(picPath)
                        if (file.exists()) {
                            kapakBitmap = BitmapFactory.decodeFile(file.absolutePath)
                        }
                    }

                    aktif = true
                    onDurumDegisti()
                }
            }
        }
    }

    init {
        val filtre = IntentFilter().apply {
            addAction("update.widget.playbtnstate")
            addAction("update.widget.songname")
            addAction("com.xyauto.music.action.UPDATE_STATE")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(alici, filtre, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(alici, filtre)
            }
        } catch (e: Exception) {
            // Sessizce gec
        }
    }

    override fun oynat() {
        context.sendBroadcast(Intent("com.xyauto.music.action.CLICK_PLAY"))
    }

    override fun duraklat() {
        context.sendBroadcast(Intent("com.xyauto.music.action.CLICK_PAUSE"))
    }

    override fun sonraki() {
        context.sendBroadcast(Intent("com.xyauto.music.action.CLICK_NEXT"))
    }

    override fun onceki() {
        context.sendBroadcast(Intent("com.xyauto.music.action.CLICK_PREV"))
    }

    override fun konumaGit(konumMs: Long) {}

    override fun aktifMi(): Boolean = aktif

    override fun kaynakAdi(): String = "Araç Teyp Medya"

    override fun baslik(): String = baslikMetni

    override fun sanatci(): String = sanatciMetni

    override fun albumKapagi(): Bitmap? = kapakBitmap

    override fun toplamSureMs(): Long = 0L

    override fun anlikKonumMs(): Long = 0L

    override fun caliyorMu(): Boolean = caliyor

    override fun serbestBirak() {
        try {
            context.unregisterReceiver(alici)
        } catch (e: Exception) {
            // Sessizce gec
        }
    }
}
