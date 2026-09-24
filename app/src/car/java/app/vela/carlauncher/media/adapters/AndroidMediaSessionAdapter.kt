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

    fun setController(c: MediaController?) {
        if (controller == c) return

        controller?.unregisterCallback(callback)
        controller = c
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

    override fun albumKapagi(): Bitmap? {
        val meta = controller?.metadata ?: return null
        return meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
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
    }
}
