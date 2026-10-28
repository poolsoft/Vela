package app.vela.carlauncher.media.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.launch
import app.vela.carlauncher.media.InternalMusicPlayer

/**
 * Dahili Muzik Calar Adaptoru (InternalPlayerAdapter).
 * InternalMusicPlayer motorunu BaseMediaAdapter arayuzune baglar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class InternalPlayerAdapter(
    private val context: Context,
    private val player: InternalMusicPlayer,
    private val onCoverLoaded: () -> Unit
) : BaseMediaAdapter() {

    private var sonKapak: Bitmap? = null
    private var sonKapakYolu: String? = null
    private val coverScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun oynat() {
        player.oynat()
    }

    override fun duraklat() {
        player.duraklat()
    }

    override fun sonraki() {
        player.sonraki()
    }

    override fun onceki() {
        player.onceki()
    }

    override fun konumaGit(konumMs: Long) {
        player.konumaGit(konumMs)
    }

    override fun aktifMi(): Boolean {
        return player.anlikParca.value != null
    }

    override fun kaynakAdi(): String {
        return "Dahili Oynatıcı"
    }

    override fun baslik(): String {
        return player.anlikParca.value?.baslik ?: "Müzik Seçilmedi"
    }

    override fun sanatci(): String {
        return player.anlikParca.value?.sanatci ?: "Sanatçı"
    }

    override fun albumKapagi(): Bitmap? {
        if (app.vela.BuildConfig.DIAGNOSTIC_MUSIC_DISABLED) return null
        val parca = player.anlikParca.value ?: return null
        val key = parca.contentUri.ifBlank { parca.dosyaYolu }
        if (key == sonKapakYolu) {
            return sonKapak
        }

        sonKapakYolu = key
        sonKapak = null
        coverScope.launch {
            val bitmap = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    if (parca.contentUri.isNotBlank()) retriever.setDataSource(context, android.net.Uri.parse(parca.contentUri))
                    else retriever.setDataSource(parca.dosyaYolu)
                    retriever.embeddedPicture?.let { bytes ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        options.inSampleSize = 1
                        while (options.outWidth / options.inSampleSize > 512 || options.outHeight / options.inSampleSize > 512)
                            options.inSampleSize *= 2
                        options.inJustDecodeBounds = false
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                } finally { runCatching { retriever.release() } }
            }.getOrNull()
            mainHandler.post {
                // An old USB read must not overwrite the next track's cover.
                val current = player.anlikParca.value
                if (sonKapakYolu == key && current?.let { it.contentUri.ifBlank { it.dosyaYolu } } == key) {
                    sonKapak = bitmap
                    onCoverLoaded()
                }
            }
        }
        return null
    }

    override fun toplamSureMs(): Long {
        return player.toplamSureMs.value
    }

    override fun anlikKonumMs(): Long {
        return player.anlikKonumMs.value
    }

    override fun caliyorMu(): Boolean {
        return player.caliyorMu.value
    }
}
