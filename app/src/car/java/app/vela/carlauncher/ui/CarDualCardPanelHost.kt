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
import app.vela.carlauncher.widgets.FuturisticSpeedometerView

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
    private val futuristicSpeed: FuturisticSpeedometerView? = rootView.findViewById(R.id.futuristic_speed)
    private val compassText: TextView = rootView.findViewById(R.id.telemetry_compass)
    private val speedValueText: TextView? = rootView.findViewById(R.id.telemetry_speed_value)
    private val speedLimitContainer: FrameLayout = rootView.findViewById(R.id.telemetry_speed_limit_container)
    private val speedLimitText: TextView = rootView.findViewById(R.id.telemetry_speed_limit_text)
    private val menuBtn: ImageButton = rootView.findViewById(R.id.dual_panel_menu_btn)

    // Muzik Karti Elemanlari
    private val cardMusic: View = rootView.findViewById(R.id.card_music)
    private val musicArt: ImageView = rootView.findViewById(R.id.dual_music_art)
    private val musicAlbumBg: ImageView = rootView.findViewById(R.id.dual_music_album_bg)
    private val musicTitle: TextView = rootView.findViewById(R.id.dual_music_title)
    private val musicArtist: TextView = rootView.findViewById(R.id.dual_music_artist)
    private val musicSourceIcon: ImageView? = rootView.findViewById(R.id.dual_music_source_icon)
    private val musicVisualizer: MusicVisualizerView = rootView.findViewById(R.id.dual_music_visualizer)
    private val musicProgress: ProgressBar = rootView.findViewById(R.id.dual_music_progress)
    private val timeCurrentText: TextView? = rootView.findViewById(R.id.dual_music_time_current)
    private val timeTotalText: TextView? = rootView.findViewById(R.id.dual_music_time_total)
    private val btnPlay: ImageButton = rootView.findViewById(R.id.dual_music_btn_play)
    private val btnNext: ImageButton = rootView.findViewById(R.id.dual_music_btn_next)
    private val btnPrev: ImageButton = rootView.findViewById(R.id.dual_music_btn_prev)
    private val btnShuffle: ImageButton? = rootView.findViewById(R.id.dual_music_btn_shuffle)
    private val btnRepeat: ImageButton? = rootView.findViewById(R.id.dual_music_btn_repeat)

    private var appliedVisualizerType: Int? = null

    init {
        cardTelemetry.setOnClickListener { onDashboardAc() }
        cardMusic.setOnClickListener { onMuzikPaneliAc() }

        btnPlay.setOnClickListener { onOynatDuraklat() }
        btnNext.setOnClickListener { onSonraki() }
        btnPrev.setOnClickListener { onOnceki() }
        btnShuffle?.setOnClickListener {
            val mm = app.vela.carlauncher.media.MusicManager.getInstance(context)
            mm.setKarisikCal(!mm.internalPlayer.karisikCal.value)
        }
        btnRepeat?.setOnClickListener {
            val mm = app.vela.carlauncher.media.MusicManager.getInstance(context)
            val yeniMod = (mm.internalPlayer.tekrarModu.value + 1) % 3
            mm.setTekrarModu(yeniMod)
        }

        menuBtn.setOnClickListener { v ->
            PopupMenu(context, v).apply {
                menu.add(0, 1, 0, "Gösterge / Telemetri")
                menu.add(0, 2, 1, "Müzik Çalar")
                menu.add(0, 3, 2, "Ayarlar")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onDashboardAc(); true }
                        2 -> { onMuzikPaneliAc(); true }
                        3 -> { onAyarlarAc(); true }
                        else -> false
                    }
                }
                show()
            }
        }

        updateTelemetri(telemetri)
        updateMedia(medya)
    }

    fun updateTelemetri(t: HizTelemetrisi) {
        val speed = t.anlikHizKmh.coerceAtLeast(0)
        futuristicSpeed?.setSpeed(speed.toFloat())
        speedValueText?.text = speed.toString()

        val speedLimit = t.hizSiniriKmh
        if (speedLimit > 0) {
            speedLimitContainer.visibility = View.VISIBLE
            speedLimitText.text = speedLimit.toString()
            if (speed > speedLimit) {
                speedValueText?.setTextColor(Color.parseColor("#FF5252"))
            } else {
                speedValueText?.setTextColor(Color.WHITE)
            }
        } else {
            speedLimitContainer.visibility = View.GONE
            speedValueText?.setTextColor(Color.WHITE)
        }

        compassText.text = pusulaMetni(t.pusulaYonu)
    }

    fun updateMedia(m: MedyaParcasi) {
        musicTitle.text = if (m.baslik.isNotBlank() && m.baslik != "Müzik Çalınmıyor") {
            m.baslik
        } else {
            "Parça Adı"
        }

        musicArtist.text = if (m.sanatci.isNotBlank() && m.sanatci != "Dokunun veya Çalın") {
            m.sanatci
        } else {
            "Sanatçı"
        }

        if (m.albumKapagi != null) {
            musicArt.setImageBitmap(m.albumKapagi)
            musicArt.imageTintList = null
            musicAlbumBg.setImageBitmap(m.albumKapagi)
        } else {
            musicArt.setImageResource(R.drawable.ic_default_album_art)
            musicArt.setColorFilter(Color.parseColor("#88FFFFFF"))
            musicAlbumBg.setImageResource(R.drawable.bg_default_music_art)
        }

        MediaSourceIcon.bind(musicSourceIcon, m)

        btnPlay.setImageResource(
            if (m.caliyorMu) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        if (m.toplamSureMs > 0) {
            val progress = ((m.anlikKonumMs.coerceIn(0, m.toplamSureMs).toDouble() / m.toplamSureMs) * 1000).toInt()
            musicProgress.progress = progress.coerceIn(0, 1000)
            musicProgress.visibility = View.VISIBLE
            timeCurrentText?.text = formatSure(m.anlikKonumMs)
            timeTotalText?.text = formatSure(m.toplamSureMs)
            timeCurrentText?.visibility = View.VISIBLE
            timeTotalText?.visibility = View.VISIBLE
        } else {
            musicProgress.progress = 0
            musicProgress.visibility = View.INVISIBLE
            timeCurrentText?.visibility = View.INVISIBLE
            timeTotalText?.visibility = View.INVISIBLE
        }

        musicVisualizer.setPlaying(m.caliyorMu)
    }

    private fun formatSure(sureMs: Long): String {
        val totalSec = (sureMs / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%d:%02d", min, sec)
    }

    fun updateVisualizerType(tip: Int, fps: Int) {
        musicVisualizer.setFrameRate(fps)
        if (appliedVisualizerType != tip) {
            appliedVisualizerType = tip
            musicVisualizer.setVisualizerType(tip)
        }
    }

    fun release() {
        musicVisualizer.setPlaying(false)
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
