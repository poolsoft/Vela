package app.vela.carlauncher.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import app.vela.R
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.ui.VelaPopupMenu as PopupMenu
import app.vela.carlauncher.widgets.InterlockingClockView
import app.vela.carlauncher.widgets.MusicVisualizerView

/**
 * Genis / Ultra-genis ekranlar icin (w800dp+) Ustte Telemetri, Altta Muzik Calar iceren
 * bagimsiz cift kartli host yoneticisi.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarDualCardPanelHost(
    private val context: Context,
    private val telemetri: HizTelemetrisi,
    private val medya: MedyaParcasi,
    private val onOynatDuraklat: () -> Unit,
    private val onSonraki: () -> Unit,
    private val onOnceki: () -> Unit,
    private val onMuzikPaneliAc: () -> Unit,
    private val onDashboardAc: () -> Unit,
    private val onAyarlarAc: () -> Unit
) {
    val rootView: View = LayoutInflater.from(
        ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
    ).inflate(R.layout.fragment_dual_card_panel, null, false)

    // Telemetri Karti Elemanlari
    private val cardTelemetry: View = rootView.findViewById(R.id.card_telemetry)
    private val compassText: TextView = rootView.findViewById(R.id.telemetry_compass)
    private val speedValueText: TextView = rootView.findViewById(R.id.telemetry_speed_value)
    private val speedLimitContainer: FrameLayout = rootView.findViewById(R.id.telemetry_speed_limit_container)
    private val speedLimitText: TextView = rootView.findViewById(R.id.telemetry_speed_limit_text)
    private val menuBtn: ImageButton = rootView.findViewById(R.id.dual_panel_menu_btn)

    // Muzik Karti Elemanlari
    private val cardMusic: View = rootView.findViewById(R.id.card_music)
    private val musicArt: ImageView = rootView.findViewById(R.id.dual_music_art)
    private val musicTitle: TextView = rootView.findViewById(R.id.dual_music_title)
    private val musicArtist: TextView = rootView.findViewById(R.id.dual_music_artist)
    private val musicVisualizer: MusicVisualizerView = rootView.findViewById(R.id.dual_music_visualizer)
    private val musicProgress: ProgressBar = rootView.findViewById(R.id.dual_music_progress)
    private val btnPlay: ImageButton = rootView.findViewById(R.id.dual_music_btn_play)
    private val btnNext: ImageButton = rootView.findViewById(R.id.dual_music_btn_next)
    private val btnPrev: ImageButton = rootView.findViewById(R.id.dual_music_btn_prev)

    init {
        cardTelemetry.setOnClickListener { onDashboardAc() }
        cardMusic.setOnClickListener { onMuzikPaneliAc() }

        btnPlay.setOnClickListener { onOynatDuraklat() }
        btnNext.setOnClickListener { onSonraki() }
        btnPrev.setOnClickListener { onOnceki() }

        menuBtn.setOnClickListener { v ->
            PopupMenu(context, v).apply {
                menu.add(0, 1, 0, R.string.car_nav_dashboard)
                menu.add(0, 2, 1, R.string.car_nav_music)
                menu.add(0, 3, 2, R.string.car_nav_settings)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onDashboardAc()
                        2 -> onMuzikPaneliAc()
                        3 -> onAyarlarAc()
                    }
                    true
                }
                show()
            }
        }

        updateTelemetri(telemetri)
        updateMedia(medya)
    }

    fun updateTelemetri(t: HizTelemetrisi) {
        val speed = t.anlikHizKmh.toInt().coerceAtLeast(0)
        speedValueText.text = speed.toString()

        val speedLimit = t.hizSiniriKmh
        if (speedLimit != null && speedLimit > 0) {
            speedLimitContainer.visibility = View.VISIBLE
            speedLimitText.text = speedLimit.toString()
            if (speed > speedLimit) {
                speedValueText.setTextColor(Color.parseColor("#FF5252"))
            } else {
                speedValueText.setTextColor(Color.WHITE)
            }
        } else {
            speedLimitContainer.visibility = View.GONE
            speedValueText.setTextColor(Color.WHITE)
        }

        compassText.text = pusulaMetni(t.pusulaDerece)
    }

    fun updateMedia(m: MedyaParcasi) {
        musicTitle.text = if (m.baslik.isNotBlank()) m.baslik else context.getString(R.string.car_music_no_track)
        musicArtist.text = if (m.sanatci.isNotBlank()) m.sanatci else context.getString(R.string.car_music_idle)

        if (m.albumKapagiUri != null) {
            try {
                musicArt.setImageURI(android.net.Uri.parse(m.albumKapagiUri))
                musicArt.imageTintList = null
            } catch (ignored: Exception) {
                musicArt.setImageResource(R.drawable.ic_default_album_art)
                musicArt.setColorFilter(Color.parseColor("#88FFFFFF"))
            }
        } else {
            musicArt.setImageResource(R.drawable.ic_default_album_art)
            musicArt.setColorFilter(Color.parseColor("#88FFFFFF"))
        }

        btnPlay.setImageResource(
            if (m.oynatiliyor) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        if (m.sureMs > 0) {
            val progress = ((m.pozisyonMs.toFloat() / m.sureMs.toFloat()) * 1000).toInt()
            musicProgress.progress = progress.coerceIn(0, 1000)
        } else {
            musicProgress.progress = 0
        }
    }

    fun updateVisualizerType(tip: Int, fps: Int) {
        musicVisualizer.setVisualizerType(tip)
        musicVisualizer.setTargetFps(fps)
    }

    fun release() {
        musicVisualizer.release()
    }

    private fun pusulaMetni(derece: Float): String {
        val d = ((derece % 360) + 360) % 360
        return when {
            d >= 337.5 || d < 22.5 -> "K"
            d < 67.5 -> "KD"
            d < 112.5 -> "D"
            d < 157.5 -> "GD"
            d < 202.5 -> "G"
            d < 247.5 -> "GB"
            d < 292.5 -> "B"
            else -> "KB"
        }
    }
}
