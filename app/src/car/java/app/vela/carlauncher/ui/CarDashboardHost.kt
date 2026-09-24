package app.vela.carlauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import app.vela.R
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.widgets.FuturisticSpeedometerView

/**
 * OsmAnd activity_neon_dashboard.xml Layoutunu Dogrudan Sisen ve Yoneten Sinif.
 * - FuturisticSpeedometerView ile gercek canli cift halkali ibre
 * - Navigasyon manevra kutusu
 * - OBD RPM, Sicaklik ve Aku voltaji metrikleri
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarDashboardHost(
    private val context: Context,
    private val onCloseClick: () -> Unit
) {

    val rootView: View = LayoutInflater.from(context).inflate(R.layout.activity_neon_dashboard, null, false)

    private val btnClose: ImageButton? = rootView.findViewById(R.id.btn_close)
    private val futuristicSpeed: FuturisticSpeedometerView? = rootView.findViewById(R.id.futuristic_speed)

    init {
        btnClose?.setOnClickListener {
            onCloseClick()
        }
    }

    fun updateTelemetri(telemetri: HizTelemetrisi) {
        futuristicSpeed?.setSpeed(telemetri.anlikHizKmh.toFloat())
    }
}
