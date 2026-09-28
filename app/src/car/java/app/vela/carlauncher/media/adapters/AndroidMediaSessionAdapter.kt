package app.vela.carlauncher.media.adapters

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/**
 * Android MediaSession Adaptoru (AndroidMediaSessionAdapter).
 * Harici uygulamalarin (Spotify, YouTube Music, Deezer, Apple Music)
 * MediaController oturumunu BaseMediaAdapter arayuzune baglar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class AndroidMediaSessionAdapter(
    private val onDurumDegisti: () -> Unit
) : BaseMediaAdapter() {

    private val anaHandler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            onDurumDegisti()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            onDurumDegisti()
        }

        override fun onSessionDestroyed() {
            setController(null)
        }
    }

    private var onbellekBaslik: String? = null
    private var onbellekSanatci: String? = null
    private var onbellekKapak: Bitmap? = null

    fun setController(c: MediaController?) {
        if (controller == c) return

        controller?.unregisterCallback(callback)
        controller = c
        onbellekBaslik = null
        onbellekSanatci = null
        onbellekKapak = null
        controller?.registerCallback(callback, anaHandler)
        onDurumDegisti()
    }

    fun getController(): MediaController? = controller

    fun getPackageName(): String = controller?.packageName ?: ""

    override fun oynat() {
        controller?.transportControls?.play()
    }

    override fun duraklat() {
        controller?.transportControls?.pause()
    }

    override fun sonraki() {
        controller?.transportControls?.skipToNext()
    }

    override fun onceki() {
        controller?.transportControls?.skipToPrevious()
    }

    override fun konumaGit(konumMs: Long) {
        controller?.transportControls?.seekTo(konumMs)
    }

    override fun aktifMi(): Boolean {
        return controller != null
    }

    override fun kaynakAdi(): String {
        return controller?.packageName ?: "Harici Oynatıcı"
    }

    override fun baslik(): String {
        val meta = controller?.metadata ?: return ""
        return meta.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
    }

    override fun sanatci(): String {
        val meta = controller?.metadata ?: return ""
        return meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
    }

    private fun guvenliOlcekleBitmap(kaynak: Bitmap?, maxBoyut: Int = 512): Bitmap? {
        if (kaynak == null) return null
        return try {
            val genislik = kaynak.width
            val yukseklik = kaynak.height
            if (genislik <= maxBoyut && yukseklik <= maxBoyut) {
                kaynak
            } else {
                val oran = genislik.toFloat() / yukseklik.toFloat()
                val yeniGenislik: Int
                val yeniYukseklik: Int
                if (oran > 1f) {
                    yeniGenislik = maxBoyut
                    yeniYukseklik = (maxBoyut / oran).toInt().coerceAtLeast(1)
                } else {
                    yeniYukseklik = maxBoyut
                    yeniGenislik = (maxBoyut * oran).toInt().coerceAtLeast(1)
                }
                Bitmap.createScaledBitmap(kaynak, yeniGenislik, yeniYukseklik, true)
            }
        } catch (e: Throwable) {
            null
        }
    }

    override fun albumKapagi(): Bitmap? {
        val meta = controller?.metadata ?: return null
        val b = baslik()
        val s = sanatci()
        if (b.isNotBlank() && b == onbellekBaslik && s == onbellekSanatci && onbellekKapak != null) {
            return onbellekKapak
        }
        val raw = try {
            meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        } catch (e: Throwable) {
            null
        }
        val olcekli = guvenliOlcekleBitmap(raw, 512)
        onbellekBaslik = b
        onbellekSanatci = s
        onbellekKapak = olcekli
        return olcekli
    }

    override fun toplamSureMs(): Long {
        return controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    }

    override fun anlikKonumMs(): Long {
        val state = controller?.playbackState ?: return 0L
        val elapsed = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0)
            ((android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong() else 0L
        val position = (state.position + elapsed).coerceAtLeast(0L)
        return if (toplamSureMs() > 0) position.coerceAtMost(toplamSureMs()) else position
    }

    override fun caliyorMu(): Boolean {
        return controller?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    override fun serbestBirak() {
        controller?.unregisterCallback(callback)
        controller = null
        onbellekBaslik = null
        onbellekSanatci = null
        onbellekKapak = null
    }
}
