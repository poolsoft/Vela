package app.vela.carlauncher.hardware

import android.content.Context
import app.vela.carlauncher.hardware.adapters.HcnAdapter
import app.vela.carlauncher.hardware.adapters.XyAutoAdapter
import app.vela.carlauncher.telemetry.CarTelemetryManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Otomobil Bas Unitesi Yoneticisi (HeadUnitManager).
 * XyAuto, HCN ve diger Cin Android teypleri icin CAN-Bus ve donanim entegrasyonunu yonetir.
 * Donanimdan gelen medya tuslarini HardwareMediaKeyRouter uzerinden rotalar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class HeadUnitManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: HeadUnitManager? = null

        fun getInstance(context: Context): HeadUnitManager {
            return instance ?: synchronized(this) {
                instance ?: HeadUnitManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var aktifAdaptor: HeadUnitAdapter? = null
    private val dinleyiciler = CopyOnWriteArrayList<HeadUnitListener>()

    init {
        val mevcutAdaptorler = listOf(
            XyAutoAdapter(),
            HcnAdapter()
        )

        for (adaptor in mevcutAdaptorler) {
            if (adaptor.destekleniyorMu(context)) {
                aktifAdaptor = adaptor
                break
            }
        }

        aktifAdaptor?.dinlemeyiBaslat(context, object : HeadUnitListener {
            override fun onHizGuncellendi(hizKmh: Float) {
                CarTelemetryManager.getInstance(context).guncelleDonanimHizi(hizKmh)
                for (d in dinleyiciler) d.onHizGuncellendi(hizKmh)
            }

            override fun onAküVoltajGuncellendi(voltaj: Float) {
                for (d in dinleyiciler) d.onAküVoltajGuncellendi(voltaj)
            }

            override fun onFarDurumuDegisti(farAcikMi: Boolean) {
                for (d in dinleyiciler) d.onFarDurumuDegisti(farAcikMi)
            }

            override fun onCalmaDurumuDegisti(caliyorMu: Boolean) {
                for (d in dinleyiciler) d.onCalmaDurumuDegisti(caliyorMu)
            }

            override fun onParcaBilgisiDegisti(baslik: String, sanatci: String, albumKapakYolu: String?) {
                for (d in dinleyiciler) d.onParcaBilgisiDegisti(baslik, sanatci, albumKapakYolu)
            }

            override fun onRadyoFrekansDegisti(band: String, frekansMhz: Float) {
                for (d in dinleyiciler) d.onRadyoFrekansDegisti(band, frekansMhz)
            }

            override fun onMedyaTusuBasildi(tusKodu: Int) {
                HardwareMediaKeyRouter.getInstance(context).route(
                    HardwareMediaKeyRouter.Source.HEAD_UNIT_ADAPTER,
                    tusKodu
                )
                for (d in dinleyiciler) d.onMedyaTusuBasildi(tusKodu)
            }
        })
    }

    fun addListener(listener: HeadUnitListener) {
        if (!dinleyiciler.contains(listener)) {
            dinleyiciler.add(listener)
        }
    }

    fun removeListener(listener: HeadUnitListener) {
        dinleyiciler.remove(listener)
    }

    fun aktifAdaptorVarMi(): Boolean = aktifAdaptor != null

    fun getAktifAdaptor(): HeadUnitAdapter? = aktifAdaptor
}
