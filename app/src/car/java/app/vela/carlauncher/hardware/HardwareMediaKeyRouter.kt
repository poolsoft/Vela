package app.vela.carlauncher.hardware

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import app.vela.carlauncher.media.MusicManager

/**
 * Direksiyon kumandasi ve fiziksel medya tuslari yonlendiricisi (HardwareMediaKeyRouter).
 * Android Activity onKeyDown veya Bas Unitesi (Head Unit) tarafindan tetiklenen tuslari
 * birlestirir. 300ms filtreleme penceresi ile cift tetiklemeleri (duplicate) onler.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class HardwareMediaKeyRouter private constructor(private val appContext: Context) {

    enum class Source {
        ACTIVITY,
        MEDIA_SESSION,
        HEAD_UNIT_ADAPTER
    }

    companion object {
        private const val CROSS_SOURCE_DUPLICATE_WINDOW_MS = 300L

        @Volatile
        private var instance: HardwareMediaKeyRouter? = null

        fun getInstance(context: Context): HardwareMediaKeyRouter {
            return instance ?: synchronized(this) {
                instance ?: HardwareMediaKeyRouter(context.applicationContext).also { instance = it }
            }
        }
    }

    private var sonTusKodu = KeyEvent.KEYCODE_UNKNOWN
    private var sonKaynak: Source? = null
    private var sonOlayZamani: Long = 0L

    @Synchronized
    fun route(kaynak: Source, tusKodu: Int): Boolean {
        if (!desteklenenMedyaTusuMu(tusKodu)) {
            return false
        }

        val simdikiZaman = SystemClock.elapsedRealtime()
        if (tusKodu == sonTusKodu && sonKaynak != null && sonKaynak != kaynak &&
            simdikiZaman - sonOlayZamani in 0..CROSS_SOURCE_DUPLICATE_WINDOW_MS
        ) {
            // Ayni tus baska kaynaktan duplicate geldi, cift basmayi onle
            return true
        }

        sonTusKodu = tusKodu
        sonKaynak = kaynak
        sonOlayZamani = simdikiZaman

        val musicManager = MusicManager.getInstance(appContext)
        return musicManager.handleHardwareMediaKey(tusKodu)
    }

    private fun desteklenenMedyaTusuMu(tusKodu: Int): Boolean {
        return tusKodu == KeyEvent.KEYCODE_MEDIA_PLAY ||
                tusKodu == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                tusKodu == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                tusKodu == KeyEvent.KEYCODE_MEDIA_NEXT ||
                tusKodu == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                tusKodu == KeyEvent.KEYCODE_MEDIA_STOP ||
                tusKodu == KeyEvent.KEYCODE_HEADSETHOOK
    }
}
