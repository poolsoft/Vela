package app.vela.carlauncher.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import app.vela.R

/**
 * Arac Medya Parca Bilgisi.
 * Calan sarki, sanatci, album kapagi ve calma durumunu tutar.
 */
data class MedyaParcasi(
    val baslik: String = "",
    val sanatci: String = "",
    val albumKapagi: Bitmap? = null,
    val caliyorMu: Boolean = false,
    val toplamSureMs: Long = 0L,
    val anlikKonumMs: Long = 0L,
    val paketAdi: String = ""
)

/**
 * Hiz ve Telemetri Bilgisi.
 * GPS hizi, OSM hiz siniri, asim durumu ve surus istatistiklerini tutar.
 */
data class HizTelemetrisi(
    val anlikHizKmh: Int = 0,
    val hizSiniriKmh: Int = 0, // 0 ise hiz siniri verisi yok demektir
    val hizSiniriAsildiMi: Boolean = false,
    val ortalamaHizKmh: Int = 0,
    val surusSuresiSaniye: Long = 0L,
    val irtifaMetre: Double = 0.0,
    val pusulaYonu: Float = 0f
)

/**
 * OBD-II Arac Verileri (RPM, Sicaklik, Voltaj).
 */
data class ObdTelemetri(
    val rpm: Int = 0,
    val motorSicakligiC: Int = 0,
    val akuVoltaji: Float = 0f,
    val bagliMi: Boolean = false
)

/**
 * Dahili Uygulamalar (InternalApp URI).
 * OsmAnd Car Launcher dahili kisayol ekosistemi ile birebir uyumlu.
 */
enum class InternalApp(
    val uri: String,
    val varsayilanAd: String,
    val ikonResId: Int
) {
    SETTINGS("internal://settings", "Ayarlar", R.drawable.ic_internal_settings),
    MUSIC("internal://music", "Müzik", R.drawable.ic_internal_music),
    ANTENNA("internal://antenna", "Anten", R.drawable.ic_internal_antenna),
    DASHBOARD("internal://dashboard", "Dashboard", R.drawable.ic_internal_dashboard),
    NEON_DASHBOARD("internal://neon_dashboard", "Dijital Gösterge", R.drawable.ic_internal_neon_dashboard);

    fun getAd(context: Context): String = context.getString(when (this) {
        SETTINGS -> R.string.car_internal_settings
        MUSIC -> R.string.car_internal_music
        ANTENNA -> R.string.car_internal_antenna
        DASHBOARD -> R.string.car_internal_dashboard
        NEON_DASHBOARD -> R.string.car_internal_neon_dashboard
    })

    fun getIkon(context: Context): Drawable? {
        return ContextCompat.getDrawable(context, ikonResId)
    }

    companion object {
        fun isInternalUri(uri: String?): Boolean {
            return uri != null && uri.startsWith("internal://")
        }

        fun fromUri(uri: String?): InternalApp? {
            if (uri == null) return null
            return values().firstOrNull { it.uri == uri }
        }
    }
}

/**
 * Uygulama Baslatma Modu.
 */
enum class LaunchMode {
    FULL_SCREEN,
    PANEL,
    POPUP
}

/**
 * Dock ve Hizli Erisim Kisayolu Modeli.
 */
data class AppShortcut(
    val paketAdi: String,
    val ad: String,
    val ikon: Drawable? = null,
    val sira: Int = 0,
    val launchMode: LaunchMode = LaunchMode.FULL_SCREEN
)

/**
 * Yerel Depolama ve USB Muzik Parca Modeli.
 */
data class SesParcasi(
    val id: Long,
    val baslik: String,
    val sanatci: String,
    val album: String,
    val sureMs: Long,
    val dosyaYolu: String,
    val albumArtUri: String? = null,
    val eklenmeTarihi: Long = 0L,
    val contentUri: String = "",
    val folderPath: String = ""
)

/**
 * Muzik Klasor Modeli (Dahili Hafiza ve USB).
 */
data class SesKlasoru(
    val yol: String,
    val ad: String,
    val parcaSayisi: Int,
    val isUsb: Boolean = false
)

/**
 * Calma Listesi Modeli.
 */
data class CalmaListesi(
    val id: Long,
    val ad: String,
    val parcaSayisi: Int
)

/**
 * Hava Durumu Veri Modelleri.
 */
data class HavaDurumuVerisi(
    val sehir: String = "",
    val anlikSicaklikC: Int = 0,
    val hissedilenC: Int = 0,
    val durumMetni: String = "",
    val durumIkonuRes: Int = 0,
    val ruzgarHiziKmh: Int = 0,
    val yagisOlasiligiYuzde: Int = 0,
    val saatlikTahmin: List<SaatlikHavaDurumu> = emptyList(),
    val gunlukTahmin: List<GunlukHavaDurumu> = emptyList()
)

data class SaatlikHavaDurumu(
    val saat: String,
    val sicaklikC: Int,
    val ikonRes: Int
)

data class GunlukHavaDurumu(
    val gunAdi: String,
    val enYuksekC: Int,
    val enDusukC: Int,
    val ikonRes: Int
)

/**
 * Yuklu Arac Uygulamasi Modeli.
 * App Drawer ve hizli baslatma icin kullanilir.
 */
data class AracUygulamasi(
    val ad: String,
    val paketAdi: String,
    val aktiviteAdi: String,
    val ikon: Drawable? = null,
    val isInternal: Boolean = false
)
