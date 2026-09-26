package app.vela.carlauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import app.vela.R
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.media.MusicManager
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.widgets.MusicVisualizerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * OsmAnd fragment_music_player.xml Layoutunu Dogrudan Sisen ve Yoneten Sinif.
 * - Sol Dikey Muzik Dock (72dp): Kapat, Playlist, Tara, Ekolayzir
 * - Merkez: Dev Album Kapagi Karti, Parca Bilgisi ve Canli Visualizer
 * - 4 Sekme: Sira, Parcalar, Klasorler, Listeler + Canli Arama
 * - Alt Oynatici Bari: SeekBar, Sureler, Prev, Neon Cyan Buyuk Play/Pause, Next, Shuffle, Repeat
 * - Dahili calma motoru (InternalMusicPlayer / MusicManager) entegrasyonu.
 * - Kullanici istegi: Buyuk muzik calar acildiginda kapak ve gorsellestirici acilir; kutuphane dugmeyle acilir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarMusicPlayerHost(
    private val context: Context,
    private val onCloseClick: () -> Unit,
    private val onPlayPauseClick: () -> Unit,
    private val onPrevClick: () -> Unit,
    private val onNextClick: () -> Unit,
    private val onScanMusic: () -> Unit,
    private val onVisualizerPermission: () -> Unit
) {

    val rootView: View = LayoutInflater.from(context).inflate(R.layout.fragment_music_player, null, false)

    private val musicManager = MusicManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // Sol Dock
    private val btnClose: ImageButton? = rootView.findViewById(R.id.btn_close)
    private val btnScanMusic: ImageButton? = rootView.findViewById(R.id.btn_scan_music)
    private val btnDockEqualizer: ImageButton? = rootView.findViewById(R.id.btn_dock_equalizer)
    private val btnDockPlaylist: ImageButton? = rootView.findViewById(R.id.btn_dock_playlist)

    // Merkez Panel
    private val nowPlayingCenterPanel: View? = rootView.findViewById(R.id.now_playing_center_panel)
    private val trackListPanel: View? = rootView.findViewById(R.id.track_list_panel)
    private val nowPlayingCenterArt: ImageView? = rootView.findViewById(R.id.now_playing_center_art)
    private val nowPlayingCenterTitle: TextView? = rootView.findViewById(R.id.now_playing_center_title)
    private val nowPlayingCenterArtist: TextView? = rootView.findViewById(R.id.now_playing_center_artist)
    private val playerVisualizer: MusicVisualizerView? = rootView.findViewById(R.id.player_visualizer)
    private val btnChangeVisualizer: View? = rootView.findViewById(R.id.btn_change_visualizer)

    private val library = MusicLibraryController(context, rootView)
    private var isSeeking = false

    // Alt Bar Kontrolleri
    private val playerProgressContainer: View? = rootView.findViewById(R.id.player_progress_container)
    private val btnPlay: ImageButton? = rootView.findViewById(R.id.btn_play)
    private val btnPrev: ImageButton? = rootView.findViewById(R.id.btn_prev)
    private val btnNext: ImageButton? = rootView.findViewById(R.id.btn_next)
    private val btnShuffle: ImageButton? = rootView.findViewById(R.id.btn_shuffle)
    private val btnRepeat: ImageButton? = rootView.findViewById(R.id.btn_repeat)
    private val seekbar: SeekBar? = rootView.findViewById(R.id.seekbar)
    private val timeCurrent: TextView? = rootView.findViewById(R.id.time_current)
    private val timeTotal: TextView? = rootView.findViewById(R.id.time_total)

    private var isPlaylistVisible = false

    init {
        playerVisualizer?.setVisualizerContext(false)
        setupPanelsVisibility()
        setupListeners()
        setupGestures()
        setupStateFlows()
        rootView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> resizePlayer(r - l, b - t) }
    }

    private var lastPlayerSize: Pair<Int, Int>? = null

    private fun resizePlayer(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || lastPlayerSize == (width to height)) return
        lastPlayerSize = width to height
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val isPortrait = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val dockHorizontal = isPortrait && (height > width)
        val dock = rootView.findViewById<android.widget.LinearLayout>(R.id.music_side_dock)
        dock.orientation = if (dockHorizontal) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        if (dockHorizontal) {
            dock.setPadding(dp(4), dp(4), dp(4), dp(4))
        } else {
            dock.setPadding(0, dp(8), 0, dp(16))
        }
        val cs = androidx.constraintlayout.widget.ConstraintSet()
        cs.clone(rootView as androidx.constraintlayout.widget.ConstraintLayout)
        cs.clear(R.id.music_side_dock)
        cs.clear(R.id.music_content_column)
        cs.connect(R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
        cs.connect(R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
        if (dockHorizontal) {
            cs.connect(R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
            cs.constrainWidth(R.id.music_side_dock, 0)
            cs.constrainHeight(R.id.music_side_dock, dp(56))
            cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.START, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.START)
            cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.TOP, R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
        } else {
            cs.connect(R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
            cs.constrainWidth(R.id.music_side_dock, dp(60))
            cs.constrainHeight(R.id.music_side_dock, 0)
            cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.START, R.id.music_side_dock, androidx.constraintlayout.widget.ConstraintSet.END)
            cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.TOP, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP)
        }
        cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END)
        cs.connect(R.id.music_content_column, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
        cs.constrainWidth(R.id.music_content_column, 0)
        cs.constrainHeight(R.id.music_content_column, 0)
        cs.applyTo(rootView)
        // Source selection is available in both narrow and wide layouts.
        rootView.findViewById<View>(R.id.app_selector_launch).visibility = View.VISIBLE
        for (i in 0 until dock.childCount) {
            val child = dock.getChildAt(i)
            val lp = child.layoutParams as android.widget.LinearLayout.LayoutParams
            if (lp.weight > 0) {
                lp.width = if (dockHorizontal) 0 else dp(1)
                lp.height = if (dockHorizontal) dp(1) else 0
            } else {
                val buttonSize = if (dockHorizontal) (width / 5 - dp(2)).coerceAtLeast(dp(28)) else dp(48)
                lp.width = buttonSize
                lp.height = dp(48)
                lp.gravity = if (dockHorizontal) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER_HORIZONTAL
                val vMargin = if (dockHorizontal) 0 else dp(4)
                lp.setMargins(0, vMargin, 0, vMargin)
            }
            child.layoutParams = lp
        }
        val contentWidth = width / density - if (dockHorizontal) 0 else 60
        val center = rootView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.now_playing_center_panel)
        val artSize = (contentWidth * 0.28f).coerceIn(56f, 130f).toInt()
        androidx.constraintlayout.widget.ConstraintSet().apply {
            clone(center)
            constrainWidth(R.id.now_playing_center_art_card, dp(artSize))
            constrainHeight(R.id.now_playing_center_art_card, dp(artSize))
            setDimensionRatio(R.id.now_playing_center_art_card, null)
            clear(R.id.now_playing_center_art_card, androidx.constraintlayout.widget.ConstraintSet.BOTTOM)
            connect(R.id.now_playing_center_art_card, androidx.constraintlayout.widget.ConstraintSet.TOP,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.TOP, dp(6))
            applyTo(center)
        }
        val controls = rootView.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.playback_controls_layout)
        val buttons = androidx.constraintlayout.widget.ConstraintSet().apply { clone(controls) }
        val size = ((contentWidth - 56) / 5f).coerceIn(32f, 64f).toInt()
        for (id in intArrayOf(R.id.btn_shuffle, R.id.btn_prev, R.id.btn_play_container, R.id.btn_next, R.id.btn_repeat)) {
            buttons.constrainWidth(id, dp(size)); buttons.constrainHeight(id, dp(size))
            val padding = if (id == R.id.btn_play_container) 0 else dp(8)
            rootView.findViewById<View>(id).setPadding(padding, padding, padding, padding)
        }
        btnPlay?.setPadding(dp(12), dp(12), dp(12), dp(12))
        buttons.applyTo(controls)
    }

    private fun setupPanelsVisibility() {
        setPlaylistVisible(false)
    }

    private fun setPlaylistVisible(visible: Boolean, openPlaylistTab: Boolean = false) {
        isPlaylistVisible = visible
        trackListPanel?.visibility = if (visible) View.VISIBLE else View.GONE
        nowPlayingCenterPanel?.visibility = if (visible) View.GONE else View.VISIBLE
        playerProgressContainer?.visibility = if (visible) View.GONE else View.VISIBLE
        btnDockPlaylist?.isSelected = visible
        btnDockPlaylist?.setColorFilter(if (visible) 0xFF00FFFF.toInt() else 0xFFFFFFFF.toInt())
        if (visible) {
            if (openPlaylistTab) {
                library.openPlaylists()
            }
            library.refresh()
        }
        rootView.post {
            rootView.requestLayout()
            rootView.invalidate()
        }
    }

    private fun setupGestures() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        val gestureDetector = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 50
            private val SWIPE_VELOCITY_THRESHOLD = 50

            override fun onDown(e: android.view.MotionEvent): Boolean = true

            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                onPlayPauseClick()
                return true
            }

            override fun onFling(
                e1: android.view.MotionEvent?,
                e2: android.view.MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val absDiffX = Math.abs(diffX)
                val absDiffY = Math.abs(diffY)

                if (absDiffX > absDiffY) {
                    if (absDiffX > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onPrevClick()
                        } else {
                            onNextClick()
                        }
                        return true
                    }
                } else {
                    if (absDiffY > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) {
                            // Yukari kaydirma: Ses artir
                            audioManager?.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_RAISE,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                        } else {
                            // Asagi kaydirma: Ses azalt
                            audioManager?.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                android.media.AudioManager.ADJUST_LOWER,
                                android.media.AudioManager.FLAG_SHOW_UI
                            )
                        }
                        return true
                    }
                }
                return false
            }
        })

        nowPlayingCenterPanel?.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                true
            } else {
                v.performClick()
                false
            }
        }
    }

    private fun setupListeners() {
        rootView.findViewById<View>(R.id.app_selector_launch).apply {
            contentDescription = context.getString(R.string.car_music_source)
            setOnClickListener { anchor ->
                android.widget.PopupMenu(context, anchor).apply {
                    musicManager.availableSources().forEach { (key, label) ->
                        menu.addSubMenu(label).apply {
                            add(R.string.car_source_select).setOnMenuItemClickListener { musicManager.selectSource(key); true }
                            add(R.string.car_source_play).setOnMenuItemClickListener { musicManager.selectSource(key, play = true); true }
                        }
                    }
                    menu.add(R.string.car_source_auto).setOnMenuItemClickListener {
                        musicManager.allowAutomaticSourceTracking(); true
                    }
                    show()
                }
            }
        }

        btnClose?.setOnClickListener {
            onCloseClick()
        }

        btnPlay?.setOnClickListener {
            onPlayPauseClick()
        }

        btnPrev?.setOnClickListener {
            onPrevClick()
        }

        btnNext?.setOnClickListener {
            onNextClick()
        }

        btnDockEqualizer?.setOnClickListener {
            CarHardwareManager.dspAc(context)
        }

        btnScanMusic?.setOnClickListener { onScanMusic() }

        btnChangeVisualizer?.setOnClickListener {
            onVisualizerPermission()
            playerVisualizer?.cycleVisualizerType()
        }

        btnDockPlaylist?.setOnClickListener {
            val targetVisible = !isPlaylistVisible
            setPlaylistVisible(targetVisible, openPlaylistTab = targetVisible)
            if (!targetVisible) onVisualizerPermission()
        }

        btnShuffle?.setOnClickListener {
            val yeniKarisik = !musicManager.internalPlayer.karisikCal.value
            musicManager.setKarisikCal(yeniKarisik)
            val msg = if (yeniKarisik) "Karışık çalma açık" else "Karışık çalma kapalı"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        btnRepeat?.setOnClickListener {
            val yeniMod = (musicManager.internalPlayer.tekrarModu.value + 1) % 3
            musicManager.setTekrarModu(yeniMod)
            val msg = when (yeniMod) {
                1 -> "Tek parça tekrarı"
                2 -> "Tüm liste tekrarı"
                else -> "Tekrar kapalı"
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        seekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    timeCurrent?.text = formatSure(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                sb?.let {
                    musicManager.konumaGit(it.progress.toLong())
                }
            }
        })

    }

    private fun setupStateFlows() {
        val scanAnim = android.view.animation.RotateAnimation(
            0f, 360f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = android.view.animation.Animation.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        scope.launch {
            app.vela.carlauncher.media.MusicRepository.getInstance(context).taraniyorMu.collectLatest { isScanning ->
                if (isScanning) {
                    btnScanMusic?.setColorFilter(0xFF00FFFF.toInt())
                    btnScanMusic?.startAnimation(scanAnim)
                } else {
                    btnScanMusic?.clearAnimation()
                    btnScanMusic?.setColorFilter(0xFFFFFFFF.toInt())
                }
            }
        }

        scope.launch {
            musicManager.sourceStatus.collectLatest { message ->
                if (message != null) android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
        scope.launch {
            musicManager.internalPlayer.karisikCal.collectLatest { karisik ->
                btnShuffle?.setColorFilter(if (karisik) 0xFF00FFFF.toInt() else 0xFF888888.toInt())
            }
        }

        scope.launch {
            musicManager.internalPlayer.tekrarModu.collectLatest { mod ->
                when (mod) {
                    1 -> {
                        btnRepeat?.setColorFilter(0xFF00FFFF.toInt())
                        btnRepeat?.setImageResource(R.drawable.ic_music_repeat_one)
                    }
                    2 -> {
                        btnRepeat?.setColorFilter(0xFF00FFFF.toInt())
                        btnRepeat?.setImageResource(R.drawable.ic_music_repeat)
                    }
                    else -> {
                        btnRepeat?.setColorFilter(0xFF888888.toInt())
                        btnRepeat?.setImageResource(R.drawable.ic_music_repeat)
                    }
                }
            }
        }

        scope.launch {
            musicManager.internalPlayer.playbackError.collectLatest { message ->
                if (message != null) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    musicManager.internalPlayer.clearPlaybackError()
                }
            }
        }
        scope.launch {
            while (true) {
                updateMediaState(musicManager.currentPlayback())
                kotlinx.coroutines.delay(250)
            }
        }
    }

    private fun formatSure(ms: Long): String {
        val toplamSaniye = ms / 1000
        val dakika = toplamSaniye / 60
        val saniye = toplamSaniye % 60
        return String.format("%02d:%02d", dakika, saniye)
    }

    fun release() {
        scope.cancel()
        playerVisualizer?.setPlaying(false)
        library.release()
    }

    private var appliedVisualizerType: Int? = null

    fun updateVisualizerType(type: Int, fps: Int) {
        playerVisualizer?.setFrameRate(fps)
        if (appliedVisualizerType != type) {
            appliedVisualizerType = type
            playerVisualizer?.setVisualizerType(type)
        }
    }

    fun updateMediaState(ignoredSnapshot: MedyaParcasi) {
        val medya = musicManager.currentPlayback()
        nowPlayingCenterTitle?.text = if (medya.baslik.isNotBlank()) medya.baslik else "Parça Adı"
        nowPlayingCenterArtist?.text = if (medya.sanatci.isNotBlank()) medya.sanatci else "Sanatçı"

        if (medya.albumKapagi != null) {
            nowPlayingCenterArt?.setImageBitmap(medya.albumKapagi)
            rootView.findViewById<ImageView>(R.id.now_playing_art_blur).setImageBitmap(medya.albumKapagi)
            nowPlayingCenterArt?.visibility = View.VISIBLE
        } else {
            nowPlayingCenterArt?.setImageResource(R.drawable.bg_playlist_default_art)
            rootView.findViewById<ImageView>(R.id.now_playing_art_blur).setImageResource(R.drawable.bg_default_music_art)
            nowPlayingCenterArt?.visibility = View.VISIBLE
        }

        btnPlay?.setImageResource(
            if (medya.caliyorMu) android.R.drawable.ic_media_pause else R.drawable.ic_music_play
        )

        playerVisualizer?.setPlaying(medya.caliyorMu)

        btnPlay?.isEnabled = musicManager.canControl()
        btnPrev?.isEnabled = musicManager.canControl()
        btnNext?.isEnabled = musicManager.canControl()
        btnShuffle?.isEnabled = true
        btnRepeat?.isEnabled = true
        val duration = medya.toplamSureMs.coerceIn(0L, Int.MAX_VALUE.toLong())
        seekbar?.isEnabled = duration > 0 && musicManager.canSeek()
        if (!isSeeking) {
            seekbar?.max = duration.toInt()
            seekbar?.progress = medya.anlikKonumMs.coerceIn(0L, duration).toInt()
            timeCurrent?.text = formatSure(medya.anlikKonumMs.coerceIn(0L, duration))
        }
        timeTotal?.text = formatSure(duration)
    }
}
