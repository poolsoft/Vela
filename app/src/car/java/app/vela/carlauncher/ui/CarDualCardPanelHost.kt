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
import app.vela.carlauncher.media.libraryKey
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.ui.VelaPopupMenu as PopupMenu
import app.vela.carlauncher.widgets.InterlockingClockView
import app.vela.carlauncher.widgets.MusicVisualizerView
import app.vela.carlauncher.widgets.FuturisticSpeedometerView
import kotlinx.coroutines.launch

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

    private val artCard: View? = rootView.findViewById(R.id.dual_music_art_card)
    private val infoContainer: View? = rootView.findViewById(R.id.dual_music_info)
    private val progressGroup: View? = rootView.findViewById(R.id.dual_music_progress_group)
    private val controlsPill: View? = rootView.findViewById(R.id.dual_music_controls_pill)
    private val queueContainer: View? = rootView.findViewById(R.id.dual_music_queue_container)
    private val queueRecycler: androidx.recyclerview.widget.RecyclerView? = rootView.findViewById(R.id.dual_music_queue_recycler)
    private val queueEmptyText: TextView? = rootView.findViewById(R.id.dual_music_queue_empty)
    private var queueAdapter: MiniQueueAdapter? = null
    private var isCompanionQueueMode = false
    private var queueJob: kotlinx.coroutines.Job? = null

    private var appliedVisualizerType: Int? = null

    init {
        cardTelemetry.setOnClickListener { onDashboardAc() }
        cardMusic.setOnClickListener { onMuzikPaneliAc() }

        rootView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) {
                val density = context.resources.displayMetrics.density
                val w = (r - l) / density
                val qTitleSize = (13f + (w - 200f) * 0.02f).coerceIn(12f, 18f)
                val qSubSize = (10f + (w - 200f) * 0.015f).coerceIn(10f, 15f)
                val qIndexSize = (11f + (w - 200f) * 0.015f).coerceIn(11f, 16f)
                val qDurationSize = (10f + (w - 200f) * 0.015f).coerceIn(10f, 15f)
                queueAdapter?.setTextSizes(qTitleSize, qSubSize, qIndexSize, qDurationSize)
            }
        }

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

        if (isCompanionQueueMode && m.baslik.isNotBlank() && m.baslik != "Müzik Çalınmıyor") {
            queueAdapter?.updatePlaying(null, m.baslik)
        }

        if (!isCompanionQueueMode) {
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
        } else {
            musicVisualizer.setPlaying(false)
        }
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
        queueJob?.cancel()
        queueJob = null
        musicVisualizer.setPlaying(false)
    }

    fun setCompanionQueueMode(enabled: Boolean) {
        if (isCompanionQueueMode == enabled) return
        isCompanionQueueMode = enabled
        applyCompanionQueueMode()
    }

    private fun applyCompanionQueueMode() {
        if (isCompanionQueueMode) {
            artCard?.visibility = View.GONE
            infoContainer?.visibility = View.GONE
            musicSourceIcon?.visibility = View.GONE
            musicVisualizer.visibility = View.GONE
            progressGroup?.visibility = View.GONE
            controlsPill?.visibility = View.GONE
            queueContainer?.visibility = View.VISIBLE
            setupQueueObserver()
        } else {
            queueContainer?.visibility = View.GONE
            artCard?.visibility = View.VISIBLE
            infoContainer?.visibility = View.VISIBLE
            musicVisualizer.visibility = View.VISIBLE
            progressGroup?.visibility = View.VISIBLE
            controlsPill?.visibility = View.VISIBLE
            queueJob?.cancel()
            queueJob = null
            musicVisualizer.setPlaying(medya.caliyorMu)
        }
    }

    private fun setupQueueObserver() {
        if (queueAdapter == null) {
            val mm = app.vela.carlauncher.media.MusicManager.getInstance(context)
            queueAdapter = MiniQueueAdapter { track ->
                queueAdapter?.updatePlaying(track.libraryKey(), track.baslik)
                mm.playQueueTrack(track)
            }
            queueRecycler?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            queueRecycler?.adapter = queueAdapter
        }

        val mm = app.vela.carlauncher.media.MusicManager.getInstance(context)
        queueJob?.cancel()
        queueJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).let { s ->
            s.launch {
                kotlinx.coroutines.flow.combine(
                    mm.internalPlayer.kuyruk,
                    mm.internalPlayer.anlikParca,
                    mm.medyaDurumu
                ) { list, current, media ->
                    Triple(list, current, media)
                }.collect { (list, current, media) ->
                    val playingKey = current?.libraryKey()
                    val playingTitle = current?.baslik?.ifBlank { null } ?: media.baslik.ifBlank { null }
                    if (list.isEmpty()) {
                        queueAdapter?.updateQueue(list, playingKey, playingTitle)
                        queueEmptyText?.visibility = View.VISIBLE
                        queueRecycler?.visibility = View.GONE
                    } else {
                        queueEmptyText?.visibility = View.GONE
                        queueRecycler?.visibility = View.VISIBLE
                        queueAdapter?.updateQueue(list, playingKey, playingTitle)
                    }
                }
            }
        }
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
