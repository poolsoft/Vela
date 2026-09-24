package app.vela.carlauncher.hardware

import android.content.Context

/**
 * Otomobil bas unitesi (Head Unit / CAN-Bus donanim adaptoru) temel arayuzu.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
interface HeadUnitAdapter {
    fun getUreticiAdi(): String
    fun destekleniyorMu(context: Context): Boolean
    fun dinlemeyiBaslat(context: Context, listener: HeadUnitListener)
    fun dinlemeyiDurdur(context: Context)

    fun muzikOynat(context: Context) {}
    fun muzikDuraklat(context: Context) {}
    fun sonrakiParca(context: Context) {}
    fun oncekiParca(context: Context) {}
}
