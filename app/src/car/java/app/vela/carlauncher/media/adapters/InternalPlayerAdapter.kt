package app.vela.carlauncher.media.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import app.vela.carlauncher.media.InternalMusicPlayer

/**
 * Dahili Muzik Calar Adaptoru (InternalPlayerAdapter).
 * InternalMusicPlayer motorunu BaseMediaAdapter arayuzune baglar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class InternalPlayerAdapter(
    private val context: Context,
    private val player: InternalMusicPlayer
) : BaseMediaAdapter() {

    private var sonKapak: Bitmap? = null
    private var sonKapakYolu: String? = null

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
        val parca = player.anlikParca.value ?: return null
        val key = parca.contentUri.ifBlank { parca.dosyaYolu }
        if (key == sonKapakYolu) {
            return sonKapak
        }

        val retriever = MediaMetadataRetriever()
        sonKapak = try {
            if (parca.contentUri.isNotBlank()) retriever.setDataSource(context, android.net.Uri.parse(parca.contentUri))
            else retriever.setDataSource(parca.dosyaYolu)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        runCatching { retriever.release() }
        sonKapakYolu = key
        return sonKapak
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
