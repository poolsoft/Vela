package app.vela.carlauncher.ui

import android.content.Context
import android.graphics.Color
import android.widget.ProgressBar
import app.vela.carlauncher.media.MusicManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import app.vela.carlauncher.ui.VelaPopupMenu as PopupMenu
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
    private val dateView: TextView? = rootView.findViewById(R.id.music_panel_date)
    private val trackTitle: TextView? = rootView.findViewById(R.id.music_track_title)
    private val trackArtist: TextView? = rootView.findViewById(R.id.music_track_artist)
    private val btnPlay: ImageButton? = rootView.findViewById(R.id.music_btn_play)
    private val btnPrev: ImageButton? = rootView.findViewById(R.id.music_btn_prev)
    private val btnNext: ImageButton? = rootView.findViewById(R.id.music_btn_next)
    private val visualizer: MusicVisualizerView? = rootView.findViewById(R.id.music_visualizer)
    private val albumArtView: ImageView? = rootView.findViewById(R.id.unified_album_art)

    private val musicControls: View? = rootView.findViewById(R.id.music_controls)

    private val preferences = context.getSharedPreferences("small_music_panel", Context.MODE_PRIVATE)
    private var clockStyle = SmallClockAppearance.read(preferences)
    private val scope = MainScope()
    private var sourceDialog: android.app.AlertDialog? = null
    private val progress: ProgressBar? = rootView.findViewById(R.id.music_progress)
    private var showVisualizer = preferences.getBoolean("visualizer", true)

    private val styleUpdate = Runnable {
        applyClockColor()
        resizeContents(rootView.width, rootView.height)
        updateClockNow()
        mainHandler.removeCallbacks(clockTick)
        if (active) mainHandler.post(clockTick)
    }
    private val appearanceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("clock_") == true) {
            rootView.removeCallbacks(styleUpdate)
            rootView.post(styleUpdate)
        }
    }

    private fun applyClockColor() {
        clockStyle = SmallClockAppearance.read(preferences)
        clockView?.let { clockStyle.apply(it, context, backgroundClockColors()) }
        dateView?.visibility = if (clockStyle.showDate) View.VISIBLE else View.GONE
    }

    private fun backgroundClockColors() = SmallClockAppearance.backgroundColors(
        albumArtView?.drawable)

    private var appliedMedia: MedyaParcasi? = null
    private var lifecycle: Lifecycle? = null
    private var active = false
    private val observer = LifecycleEventObserver { _, _ -> syncLifecycle() }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            preferences.registerOnSharedPreferenceChangeListener(appearanceListener)
            rootView.post(styleUpdate)
            lifecycle?.removeObserver(observer)
            lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
            lifecycle?.addObserver(observer)
            syncLifecycle()
            updateClockNow()
        }
        override fun onViewDetachedFromWindow(view: View) {
            preferences.unregisterOnSharedPreferenceChangeListener(appearanceListener)
            rootView.removeCallbacks(styleUpdate)
            lifecycle?.removeObserver(observer)
            lifecycle = null
            setActive(false)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            updateClockNow()
        }
    }
    private var isTimeReceiverRegistered = false

    private fun registerTimeReceiver() {
        if (!isTimeReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            try {
                context.registerReceiver(timeTickReceiver, filter)
                isTimeReceiverRegistered = true
            } catch (e: Exception) {
                // Ignore receiver registration error
            }
        }
    }

    private fun unregisterTimeReceiver() {
        if (isTimeReceiverRegistered) {
            try {
                context.unregisterReceiver(timeTickReceiver)
            } catch (e: Exception) {
                // Ignore receiver unregistration error
            }
            isTimeReceiverRegistered = false
        }
    }

    private fun updateClockNow() {
        val now = java.util.Date()
        val value = android.text.format.DateFormat.format(
            clockStyle.timePattern(context),
            now)
        if (clockView?.text?.toString() != value.toString()) {
            clockView?.text = value
            clockView?.postInvalidate()
        }
        if (clockStyle.showDate && dateView != null) {
            val dateValue = android.text.format.DateFormat.format(
                clockStyle.datePattern(),
                now)
            if (dateView.text?.toString() != dateValue.toString()) {
                dateView.text = dateValue
            }
        }
    }

    private fun syncLifecycle() {
        if (lifecycle == null && rootView.isAttachedToWindow) {
            lifecycle = rootView.findViewTreeLifecycleOwner()?.lifecycle
            lifecycle?.addObserver(observer)
        }
        val isStarted = lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
        setActive(rootView.isAttachedToWindow && isStarted)
    }

    private fun setActive(value: Boolean) {
        if (active == value) {
            if (value) {
                updateClockNow()
                mainHandler.removeCallbacks(clockTick)
                mainHandler.post(clockTick)
            }
            return
        }
        active = value
        mainHandler.removeCallbacks(clockTick)
        rootView.removeCallbacks(clockTick)
        if (value) {
            registerTimeReceiver()
            updateClockNow()
            mainHandler.post(clockTick)
        } else {
            unregisterTimeReceiver()
        }
        visualizer?.setPlaying(value && showVisualizer && (appliedMedia?.caliyorMu == true))
        if (!value) { sourceDialog?.dismiss(); sourceDialog = null }
    }

    private val clockTick = object : Runnable {
        override fun run() {
            updateClockNow()
            mainHandler.removeCallbacks(this)
            if (active) {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        visualizer?.setVisualizerContext(true)
        setupViews()
        rootView.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) resizeContents(r - l, b - t)
        }
        rootView.addOnAttachStateChangeListener(attachment)
        if (rootView.isAttachedToWindow) {
            attachment.onViewAttachedToWindow(rootView)
        }
        updateClockNow()
        rootView.post {
            if (rootView.width > 0 && rootView.height > 0) {
                resizeContents(rootView.width, rootView.height)
            }
            syncLifecycle()
            updateClockNow()
        }
    }

    fun release() {
        preferences.unregisterOnSharedPreferenceChangeListener(appearanceListener)
        rootView.removeCallbacks(styleUpdate)
        sourceDialog?.dismiss()
        scope.cancel()
        lifecycle?.removeObserver(observer)
        lifecycle = null
        rootView.removeOnAttachStateChangeListener(attachment)
        setActive(false)
        unregisterTimeReceiver()
        mainHandler.removeCallbacks(clockTick)
        rootView.removeCallbacks(clockTick)
        visualizer?.setPlaying(false)
    }

    private fun resizeContents(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val w = width / density
        val h = height / density
        val clockHeight = ((h * 0.28f).coerceIn(36f, 100f) * clockStyle.scale).coerceAtMost(h * 0.45f)
        val targetPx = clockHeight * density * 0.94f
        clockView?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, targetPx)
        val targetH = (clockHeight * density).toInt()
        clockView?.let {
            if (it.layoutParams.height != targetH) {
                it.layoutParams = it.layoutParams.apply { this.height = targetH }
                it.requestLayout()
                musicArea?.requestLayout()
            }
        }
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
        dateView?.setOnClickListener { onMuzikPaneliAc() }
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

        popup.menu.add(0, 33, 5, R.string.car_clock_customize)
        popup.menu.add(0, 30, 6, R.string.car_small_visualizer).apply {
            isCheckable = true; isChecked = showVisualizer
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                33 -> {
                    app.vela.carlauncher.settings.CarLauncherSettings.requestClockSettings()
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
