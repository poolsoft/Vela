package app.vela.carlauncher.telemetry

import android.content.Context
import android.location.Location
import app.vela.carlauncher.model.HizTelemetrisi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Arac Hiz ve Telemetri Yoneticisi.
 * Anlik GPS hizi, hiz siniri, asim tespiti, ortalama hiz ve surus suresi hesaplamalarini yonetir.
 */
class CarTelemetryManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: CarTelemetryManager? = null

        fun getInstance(context: Context): CarTelemetryManager {
            return instance ?: synchronized(this) {
                instance ?: CarTelemetryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _telemetriDurumu = MutableStateFlow(HizTelemetrisi())
    val telemetriDurumu: StateFlow<HizTelemetrisi> = _telemetriDurumu.asStateFlow()

    private var surusBaslangicZamaniMs: Long = 0L
    private var toplamHizOrnekleri: Long = 0L
    private var hizOrnekSayisi: Long = 0L
    private var sonKonumZamaniMs: Long = 0L

    init {
        surusBaslangicZamaniMs = System.currentTimeMillis()
        baslatSurusZamanlayicisi()
    }

    private fun baslatSurusZamanlayicisi() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                val simdi = System.currentTimeMillis()
                val gecenSaniye = (simdi - surusBaslangicZamaniMs) / 1000L

                // GPS verisi 3 saniyeden eskiyse hizi sifirla
                val sonHiz = if (simdi - sonKonumZamaniMs > 3000L && sonKonumZamaniMs > 0L) {
                    0
                } else {
                    _telemetriDurumu.value.anlikHizKmh
                }

                _telemetriDurumu.value = _telemetriDurumu.value.copy(
                    anlikHizKmh = sonHiz,
                    surusSuresiSaniye = gecenSaniye
                )
            }
        }
    }

    /**
     * Vela MapViewModel veya LocationProvider'dan gelen yeni GPS ve hiz verisiyle guncelleme yapar.
     */
    fun guncelleGpsVerisi(
        hizMs: Float?,
        hizSiniriKmhGelen: Double?,
        irtifaMetre: Double = 0.0,
        pusulaYonu: Float = 0f
    ) {
        sonKonumZamaniMs = System.currentTimeMillis()

        val anlikKmh = if (hizMs != null && hizMs > 0.5f) {
            (hizMs * 3.6f).roundToInt()
        } else {
            0
        }

        val limitKmh = hizSiniriKmhGelen?.roundToInt() ?: 0
        val asildiMi = limitKmh > 0 && anlikKmh > (limitKmh + 3) // 3 km/h tolerans

        if (anlikKmh > 2) {
            toplamHizOrnekleri += anlikKmh
            hizOrnekSayisi++
        }

        val ortalamaHiz = if (hizOrnekSayisi > 0) {
            (toplamHizOrnekleri / hizOrnekSayisi).toInt()
        } else {
            0
        }

        _telemetriDurumu.value = HizTelemetrisi(
            anlikHizKmh = anlikKmh,
            hizSiniriKmh = limitKmh,
            hizSiniriAsildiMi = asildiMi,
            ortalamaHizKmh = ortalamaHiz,
            surusSuresiSaniye = _telemetriDurumu.value.surusSuresiSaniye,
            irtifaMetre = irtifaMetre,
            pusulaYonu = pusulaYonu
        )
    }

    /**
     * Bas unitesinden (CAN-Bus / OBD) gelen donanimsal arac hizini isler.
     */
    fun guncelleDonanimHizi(hizKmh: Float) {
        sonKonumZamaniMs = System.currentTimeMillis()
        val anlikKmh = hizKmh.roundToInt().coerceAtLeast(0)
        val limitKmh = _telemetriDurumu.value.hizSiniriKmh
        val asildiMi = limitKmh > 0 && anlikKmh > (limitKmh + 3)

        if (anlikKmh > 2) {
            toplamHizOrnekleri += anlikKmh
            hizOrnekSayisi++
        }

        val ortalamaHiz = if (hizOrnekSayisi > 0) {
            (toplamHizOrnekleri / hizOrnekSayisi).toInt()
        } else {
            0
        }

        _telemetriDurumu.value = _telemetriDurumu.value.copy(
            anlikHizKmh = anlikKmh,
            hizSiniriAsildiMi = asildiMi,
            ortalamaHizKmh = ortalamaHiz
        )
    }

    /**
     * Surus istatistiklerini sifirlar.
     */
    fun sifirlaSurusIstatistikleri() {
        surusBaslangicZamaniMs = System.currentTimeMillis()
        toplamHizOrnekleri = 0L
        hizOrnekSayisi = 0L
        _telemetriDurumu.value = _telemetriDurumu.value.copy(
            ortalamaHizKmh = 0,
            surusSuresiSaniye = 0L
        )
    }
}
