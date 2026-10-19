package app.vela.carlauncher.ui

import android.content.Context
import android.graphics.Color
import android.widget.ProgressBar
import app.vela.carlauncher.media.MusicManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
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

    val rootView: View = LayoutInflater.from(androidx.appcompat.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)).inflate(R.layout.fragment_unified_panel, null, false)

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

    private val preferences = context.getSharedPreferences("small_music_panel", Context.MODE_PRIVATE)
    private val scope = MainScope()
    private var clockColorDialog: android.app.AlertDialog? = null
    private var sourceDialog: android.app.AlertDialog? = null
    private val progress: ProgressBar? = rootView.findViewById(R.id.music_progress)
    private var showVisualizer = preferences.getBoolean("visualizer", true)

    private fun applyClockColor() {
        val mode = preferences.getInt("clock_color", 6)
        val adaptive = if (mode == 7) backgroundClockColors() else null
        val soft = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getColor(android.R.color.system_accent1_100) to context.getColor(android.R.color.system_accent2_200)
        } else Color.rgb(235, 232, 219) to Color.rgb(215, 200, 231)
        val color = when (mode) {
            1 -> androidx.core.content.ContextCompat.getColor(context, R.color.cl_primary)
            2 -> Color.rgb(231, 213, 180)
            3 -> Color.TRANSPARENT
            4 -> Color.WHITE
            5 -> preferences.getInt("clock_custom_primary", Color.rgb(184, 221, 212))
            6 -> soft.first
            7 -> adaptive!!.first
            else -> Color.rgb(184, 221, 212)
        }
        val twoColors = preferences.getBoolean("clock_two_colors", mode in 6..7) && color != Color.TRANSPARENT
        val secondary = when (mode) {
            6 -> soft.second
            7 -> adaptive!!.second
            else -> preferences.getInt("clock_custom_secondary", Color.rgb(216, 205, 228))
        }
        clockView?.embossed = preferences.getBoolean("clock_embossed", false)
        val night = context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        clockView?.fillColor = if (night && color != Color.TRANSPARENT)
            (color and 0x00FFFFFF) or 0xCC000000.toInt() else color
        clockView?.minuteFillColor = if (twoColors) {
            if (night) (secondary and 0x00FFFFFF) or 0xCC000000.toInt() else secondary
        } else null
    }

    private fun backgroundClockColors(): Pair<Int, Int> {
        val drawable = rootView.findViewById<ImageView>(R.id.unified_album_art)?.drawable
            ?: return Color.rgb(235, 232, 219) to Color.rgb(215, 200, 231)
        val bitmap = android.graphics.Bitmap.createBitmap(12, 12, android.graphics.Bitmap.Config.ARGB_8888)
        val bounds = android.graphics.Rect(drawable.bounds)
        try {
            drawable.setBounds(0, 0, 12, 12)
            drawable.draw(android.graphics.Canvas(bitmap))
            var red = 0; var green = 0; var blue = 0
            for (y in 0 until 12) for (x in 0 until 12) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel); green += Color.green(pixel); blue += Color.blue(pixel)
            }
            val hsv = FloatArray(3)
            Color.colorToHSV(Color.rgb(red / 144, green / 144, blue / 144), hsv)
            return Color.HSVToColor(floatArrayOf(hsv[0], 0.12f, 0.94f)) to
                Color.HSVToColor(floatArrayOf((hsv[0] + 35f) % 360f, 0.18f, 0.87f))
        } finally {
            drawable.bounds = bounds
            bitmap.recycle()
        }
    }

    private var appliedMedia: MedyaParcasi? = null
    private var lifecycle: Lifecycle? = null
    private var active = false
    private val observer = LifecycleEventObserver { _, _ -> syncLifecycle() }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
            lifecycle?.addObserver(observer)
            syncLifecycle()
        }
        override fun onViewDetachedFromWindow(view: View) {
            lifecycle?.removeObserver(observer)
            lifecycle = null
            setActive(false)
        }
    }

    private fun syncLifecycle() = setActive(rootView.isAttachedToWindow &&
        (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false))

    private fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        rootView.removeCallbacks(clockTick)
        if (value) rootView.post(clockTick)
        visualizer?.setPlaying(value && showVisualizer && (appliedMedia?.caliyorMu == true))
        if (!value) { sourceDialog?.dismiss(); sourceDialog = null; clockColorDialog?.dismiss() }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            val value = android.text.format.DateFormat.format(
                if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm",
                java.util.Date())
            if (clockView?.text?.toString() != value.toString()) clockView?.text = value
            rootView.postDelayed(this, 1000)
        }
    }

    init {
        visualizer?.setVisualizerContext(true)
        setupViews()
        rootView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) resizeContents(r - l, b - t)
        }
        rootView.addOnAttachStateChangeListener(attachment)
    }

    fun release() {
        clockColorDialog?.dismiss()
        sourceDialog?.dismiss()
        scope.cancel()
        lifecycle?.removeObserver(observer)
        rootView.removeOnAttachStateChangeListener(attachment)
        setActive(false)
        rootView.removeCallbacks(clockTick)
        visualizer?.setPlaying(false)
    }

    private fun resizeContents(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val w = width / density
        val h = height / density
        val clockHeight = (h * 0.28f).coerceIn(32f, 100f)
        clockView?.textSize = clockHeight * 0.96f
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
        clockView?.fitTextToBounds = true
        applyClockColor()
        visualizer?.visibility = if (showVisualizer) View.VISIBLE else View.INVISIBLE
        rootView.findViewById<ImageView>(R.id.music_source_icon)?.setOnClickListener {
            sourceDialog?.dismiss()
            sourceDialog = MusicSourceDialog.show(context, MusicManager.getInstance(context), scope)
        }
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

        val colors = popup.menu.addSubMenu(R.string.car_small_clock_color)
        listOf(R.string.car_small_clock_turquoise, R.string.car_small_clock_theme,
            R.string.car_small_clock_amber, R.string.car_small_clock_transparent,
            R.string.car_small_clock_white, R.string.car_small_clock_custom,
            R.string.car_small_clock_soft, R.string.car_small_clock_background).forEachIndexed { index, label ->
            colors.add(1, 20 + index, index, label).apply {
                isCheckable = true
                isChecked = preferences.getInt("clock_color", 6) == index
            }
        }
        colors.add(0, 32, 6, R.string.car_small_clock_two_colors).apply {
            isCheckable = true; isChecked = preferences.getBoolean("clock_two_colors", preferences.getInt("clock_color", 6) in 6..7)
        }
        popup.menu.add(0, 31, 0, R.string.car_small_clock_embossed).apply {
            isCheckable = true; isChecked = preferences.getBoolean("clock_embossed", false)
        }
        popup.menu.add(0, 30, 6, R.string.car_small_visualizer).apply {
            isCheckable = true; isChecked = showVisualizer
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                25 -> {
                    clockColorDialog?.dismiss()
                    clockColorDialog = ClockColorDialog.show(context,
                        preferences.getInt("clock_custom_primary", Color.rgb(184, 221, 212)),
                        preferences.getInt("clock_custom_secondary", Color.rgb(216, 205, 228)),
                        preferences.getBoolean("clock_two_colors", preferences.getInt("clock_color", 6) in 6..7)) { primary, secondary, twoColors ->
                        preferences.edit().putInt("clock_color", 5)
                            .putInt("clock_custom_primary", primary).putInt("clock_custom_secondary", secondary)
                            .putBoolean("clock_two_colors", twoColors).apply()
                        applyClockColor()
                    }
                    true
                }
                32 -> {
                    preferences.edit().putBoolean("clock_two_colors", !preferences.getBoolean("clock_two_colors", preferences.getInt("clock_color", 6) in 6..7)).apply()
                    applyClockColor()
                    true
                }
                in 20..24, 26, 27 -> {
                    preferences.edit().putInt("clock_color", item.itemId - 20).apply()
                    applyClockColor()
                    true
                }
                31 -> {
                    preferences.edit().putBoolean("clock_embossed", !preferences.getBoolean("clock_embossed", false)).apply()
                    applyClockColor()
                    true
                }
                30 -> {
                    showVisualizer = !showVisualizer
                    preferences.edit().putBoolean("visualizer", showVisualizer).apply()
                    visualizer?.visibility = if (showVisualizer) View.VISIBLE else View.INVISIBLE
                    visualizer?.setPlaying(active && showVisualizer && appliedMedia?.caliyorMu == true)
                    true
                }
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
        progress?.visibility = if (m.toplamSureMs > 0) View.VISIBLE else View.INVISIBLE
        progress?.progress = if (m.toplamSureMs > 0)
            ((m.anlikKonumMs.coerceIn(0, m.toplamSureMs).toDouble() / m.toplamSureMs) * 1000).toInt() else 0
        val previous = appliedMedia
        if (previous != null && previous.baslik == m.baslik && previous.sanatci == m.sanatci && previous.caliyorMu == m.caliyorMu && previous.sourceId == m.sourceId && previous.sourceKind == m.sourceKind && previous.paketAdi == m.paketAdi && previous.canControl == m.canControl && previous.canSkipNext == m.canSkipNext && previous.canSkipPrevious == m.canSkipPrevious) return
        appliedMedia = m
        MediaSourceIcon.bind(rootView.findViewById(R.id.music_source_icon), m, externalOnly = true)
        btnPlay?.isEnabled = m.canControl
        btnPrev?.isEnabled = m.canSkipPrevious
        btnNext?.isEnabled = m.canSkipNext
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

        visualizer?.setPlaying(active && showVisualizer && m.caliyorMu)
    }
}
