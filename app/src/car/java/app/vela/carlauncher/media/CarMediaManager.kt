package app.vela.carlauncher.media

import android.content.Context
import app.vela.carlauncher.model.MedyaParcasi
import kotlinx.coroutines.flow.StateFlow

/**
 * Arac Medya Yoneticisi (CarMediaManager).
 * MusicManager uzerinden calisan geriye donuk uyumlu cephe (Facade).
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarMediaManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: CarMediaManager? = null

        fun getInstance(context: Context): CarMediaManager {
            return instance ?: synchronized(this) {
                instance ?: CarMediaManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val musicManager = MusicManager.getInstance(context)

    val medyaDurumu: StateFlow<MedyaParcasi> = musicManager.medyaDurumu

    fun oynatVeyaDuraklat() {
        musicManager.oynatDuraklat()
    }

    fun sonrakiParca() {
        musicManager.sonraki()
    }

    fun oncekiParca() {
        musicManager.onceki()
    }

    fun konumaGit(konumMs: Long) {
        musicManager.konumaGit(konumMs)
    }

    fun getMusicManager(): MusicManager = musicManager
}
