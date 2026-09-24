package app.vela.carlauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import app.vela.R
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.widgets.InterlockingClockView
import app.vela.carlauncher.widgets.MusicVisualizerView

/**
 * OsmAnd fragment_unified_panel.xml layoutunu dogrudan sisen ve yoneten sinif.
 * - Konturlu Füturistik Dijital Saat (InterlockingClockView)
 * - 3 Nokta Acilir Menu (PopupMenu)
 * - Canli Muzik Gorsellestirici (MusicVisualizerView)
 * - Parca ve Sanatci Bilgisi, Medya Tuslari
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarUnifiedPanelHost(
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

    val rootView: View = LayoutInflater.from(context).inflate(R.layout.fragment_unified_panel, null, false)

    private val panelMenuBtn: ImageButton? = rootView.findViewById(R.id.panel_menu_btn)
    private val musicArea: View? = rootView.findViewById(R.id.music_area)
    private val clockView: InterlockingClockView? = rootView.findViewById(R.id.music_panel_clock)
    private val trackTitle: TextView? = rootView.findViewById(R.id.music_track_title)
    private val trackArtist: TextView? = rootView.findViewById(R.id.music_track_artist)
    private val btnPlay: ImageButton? = rootView.findViewById(R.id.music_btn_play)
    private val btnPrev: ImageButton? = rootView.findViewById(R.id.music_btn_prev)
    private val btnNext: ImageButton? = rootView.findViewById(R.id.music_btn_next)
    private val visualizer: MusicVisualizerView? = rootView.findViewById(R.id.music_visualizer)

    private val musicControls: View? = rootView.findViewById(R.id.music_controls)

    private val clockTick = object : Runnable {
        override fun run() {
            clockView?.text = android.text.format.DateFormat.format(
                if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm",
                java.util.Date())
            rootView.postDelayed(this, 1000)
        }
    }

    init {
        visualizer?.setVisualizerContext(true)
        setupViews()
        rootView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> resizeContents(r - l, b - t) }
        rootView.post(clockTick)
    }

    fun release() {
        rootView.removeCallbacks(clockTick)
        visualizer?.setPlaying(false)
    }

    private fun resizeContents(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val w = width / density
        val h = height / density
        val clockHeight = (h * 0.22f).coerceIn(32f, 60f)
        clockView?.textSize = minOf(w / 6f, clockHeight * 0.8f).coerceIn(22f, 48f)
        clockView?.let { if (it.layoutParams.height != (clockHeight * density).toInt())
            it.layoutParams = it.layoutParams.apply { this.height = (clockHeight * density).toInt() } }
        val size = minOf((w - 48) / 3f, h * 0.25f).coerceIn(40f, 64f)
        listOf(btnPrev, btnPlay, btnNext).forEach { button ->
            button ?: return@forEach
            val target = (size * density).toInt()
            val lp = button.layoutParams as android.widget.LinearLayout.LayoutParams
            val margin = (4 * density).toInt()
            if (lp.width != target || lp.height != target || lp.leftMargin != margin) {
                lp.width = target; lp.height = target; lp.leftMargin = margin; lp.rightMargin = margin
                button.layoutParams = lp
            }
        }
        trackTitle?.textSize = if (w < 240) 16f else 18f
        trackArtist?.textSize = if (w < 240) 12f else 14f
    }

    private fun setupViews() {
        musicArea?.setOnClickListener { onMuzikPaneliAc() }
        clockView?.setOnClickListener { onMuzikPaneliAc() }
        trackTitle?.setOnClickListener { onMuzikPaneliAc() }
        trackArtist?.setOnClickListener { onMuzikPaneliAc() }
        visualizer?.setOnClickListener { onMuzikPaneliAc() }

        btnPlay?.setOnClickListener {
            onOynatDuraklat()
        }

        btnPrev?.setOnClickListener {
            onOnceki()
        }

        btnNext?.setOnClickListener {
            onSonraki()
        }

        panelMenuBtn?.setOnClickListener { v ->
            showPopupMenu(v)
        }

        updateMedia(medya)
    }

    private fun showPopupMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, "Müzik Çalar")
        popup.menu.add(0, 2, 1, "Gösterge / Telemetri")
        popup.menu.add(0, 3, 2, "DSP / Ekolayzır")
        popup.menu.add(0, 4, 3, "Ayarlar")
        popup.menu.add(0, 5, 4, "Bellek Temizle")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    onMuzikPaneliAc()
                    true
                }
                2 -> {
                    onDashboardAc()
                    true
                }
                3 -> {
                    CarHardwareManager.dspAc(context)
                    true
                }
                4 -> {
                    onAyarlarAc()
                    true
                }
                5 -> {
                    CarHardwareManager.bellekTemizle(context)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private var appliedVisualizerType: Int? = null

    fun updateVisualizerType(type: Int, fps: Int) {
        visualizer?.setFrameRate(fps)
        if (appliedVisualizerType != type) {
            appliedVisualizerType = type
            visualizer?.setVisualizerType(type)
        }
    }

    fun updateMedia(m: MedyaParcasi) {
        trackTitle?.text = if (m.baslik.isNotBlank() && m.baslik != "Müzik Çalınmıyor") {
            m.baslik
        } else {
            "Parça Adı"
        }

        trackArtist?.text = if (m.sanatci.isNotBlank() && m.sanatci != "Dokunun veya Çalın") {
            m.sanatci
        } else {
            "Sanatçı"
        }

        btnPlay?.setImageResource(
            if (m.caliyorMu) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        visualizer?.setPlaying(m.caliyorMu)
    }
}
