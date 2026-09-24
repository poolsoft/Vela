package app.vela.carlauncher.media.adapters

import android.graphics.Bitmap

/**
 * Tum Medya Kaynaklari Icin Evrensel Soyut Adaptor (BaseMediaAdapter).
 * Dahili player, Android MediaSession, Bluetooth A2DP ve teyp donanim adaptorleri
 * bu arayuzu uygular.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
abstract class BaseMediaAdapter {

    abstract fun oynat()

    abstract fun duraklat()

    abstract fun sonraki()

    abstract fun onceki()

    abstract fun konumaGit(konumMs: Long)

    abstract fun aktifMi(): Boolean

    abstract fun kaynakAdi(): String

    abstract fun baslik(): String

    abstract fun sanatci(): String

    abstract fun albumKapagi(): Bitmap?

    abstract fun toplamSureMs(): Long

    abstract fun anlikKonumMs(): Long

    abstract fun caliyorMu(): Boolean

    open fun serbestBirak() {}
}
