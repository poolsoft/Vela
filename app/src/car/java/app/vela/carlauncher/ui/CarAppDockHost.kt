package app.vela.carlauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.model.AppShortcut
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.model.MedyaParcasi

/**
 * OsmAnd fragment_app_dock.xml (yatay) ve fragment_app_dock_sidebar.xml (dikey) layoutlarini sisen host.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarAppDockHost(
    private val context: Context,
    val isVertical: Boolean,
    private val onAppDrawerClick: () -> Unit,
    private val onDesktopClick: () -> Unit,
    private val onShortcutClick: (AppShortcut) -> Unit,
    private val onMiniMusicClick: () -> Unit,
    private val onPlayPauseClick: () -> Unit,
    private val onNextClick: () -> Unit,
    private val onAssistantClick: () -> Unit = {},
    private val onSettingsClick: () -> Unit
) {

    val rootView: View = LayoutInflater.from(context).inflate(
        if (isVertical) R.layout.fragment_app_dock_sidebar else R.layout.fragment_app_dock,
        null,
        false
    )

    private val btnAppList: ImageButton? = rootView.findViewById(R.id.btn_app_list)
    private val btnDesktopMode: ImageButton? = rootView.findViewById(R.id.btn_desktop_mode)
    private val btnAssistant: ImageButton? = rootView.findViewById(R.id.btn_assistant)
    private val dockRecycler: RecyclerView? = rootView.findViewById(R.id.dock_recycler)
    private val miniMusicContainer: View? = rootView.findViewById(R.id.mini_music_container)
    private val miniMusicTitle: TextView? = rootView.findViewById(R.id.mini_music_title)
    private val miniMusicPlay: ImageButton? = rootView.findViewById(R.id.mini_btn_play)

    private val dockManager = AppDockManager.getInstance(context)
    private val shortcutAdapter = DynamicDockShortcutAdapter(emptyList(), onShortcutClick).apply {
        onItemLongClick = { shortcut, anchor ->
            PopupMenu(context, anchor).apply {
                menu.add(0, 1, 0, R.string.car_dock_move_previous)
                menu.add(0, 2, 1, R.string.car_dock_move_next)
                menu.add(0, 3, 2, R.string.car_dock_remove)
                if (!InternalApp.isInternalUri(shortcut.paketAdi)) {
                    menu.add(0, 4, 3, R.string.car_app_mode_full)
                    menu.add(0, 5, 4, R.string.car_app_mode_split)
                    menu.add(0, 6, 5, R.string.car_app_mode_freeform).isEnabled =
                        app.vela.carlauncher.apps.CarAppManager.getInstance(context).supportsFreeform()
                }
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> dockManager.kisayolTasi(shortcut.paketAdi, -1)
                        2 -> dockManager.kisayolTasi(shortcut.paketAdi, 1)
                        3 -> dockManager.kisayolSil(shortcut.paketAdi)
                        4 -> dockManager.setLaunchMode(shortcut.paketAdi, app.vela.carlauncher.model.LaunchMode.FULL_SCREEN)
                        5 -> dockManager.setLaunchMode(shortcut.paketAdi, app.vela.carlauncher.model.LaunchMode.SPLIT_SCREEN)
                        6 -> dockManager.setLaunchMode(shortcut.paketAdi, app.vela.carlauncher.model.LaunchMode.OVERLAY)
                    }
                    true
                }
                show()
            }
        }
    }

    init {
        btnAppList?.setOnClickListener { onAppDrawerClick() }
        btnDesktopMode?.setOnClickListener { onDesktopClick() }
        btnAssistant?.setOnClickListener { onAssistantClick() }
        rootView.findViewById<View>(R.id.clock_settings_container)?.setOnClickListener { onSettingsClick() }
        miniMusicContainer?.setOnClickListener { onMiniMusicClick() }
        miniMusicPlay?.setOnClickListener { onPlayPauseClick() }
        rootView.findViewById<View>(R.id.mini_btn_next)?.setOnClickListener { onNextClick() }
        rootView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> adaptToSize(r - l, b - t) }

        // OsmAnd Orijinal Kurali: Dikey dock (isVertical == true) iken mini muzik bari tamamen GONE
        val dockClock = rootView.findViewById<android.widget.TextClock>(R.id.dock_clock)
        if (isVertical) {
            miniMusicContainer?.visibility = View.GONE
            dockClock?.format24Hour = "HH\nmm"
            dockClock?.format12Hour = "h\nmm"
        } else {
            miniMusicContainer?.visibility = View.VISIBLE
        }

        dockRecycler?.let { recycler ->
            recycler.layoutManager = LinearLayoutManager(
                context,
                if (isVertical) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL,
                false
            )
            recycler.adapter = shortcutAdapter
            if (isVertical) {
                // Reserve whole rows, centered between the clock and navigation controls.
                (recycler.parent as View).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    fitShortcutRows()
                }
                recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(view: RecyclerView, state: Int) {
                        if (state != RecyclerView.SCROLL_STATE_IDLE) return
                        val manager = view.layoutManager as LinearLayoutManager
                        val first = manager.findViewByPosition(manager.findFirstVisibleItemPosition()) ?: return
                        val bounds = android.graphics.Rect()
                        manager.getDecoratedBoundsWithMargins(first, bounds)
                        val top = bounds.top
                        val row = bounds.height()
                        val offset = if (-top > row / 2) top + row else top
                        if (offset != 0) view.smoothScrollBy(0, offset)
                    }
                })
            }
            updateShortcuts(dockManager.kisayollar.value)
        }
    }

    private fun adaptToSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val cross = if (isVertical) width else height
        val size = (cross - (if (isVertical) 12 else 8) * density).toInt()
            .coerceIn((40 * density).toInt(), (64 * density).toInt())
        for (button in listOf(btnAppList, btnDesktopMode, rootView.findViewById<ImageButton>(R.id.btn_assistant))) {
            button ?: continue
            val buttonHeight = if (isVertical) (40 * density).toInt() else size
            if (button.layoutParams.width != size || button.layoutParams.height != buttonHeight)
                button.layoutParams = button.layoutParams.apply { this.width = size; this.height = buttonHeight }
            if (isVertical) {
                val padding = (8 * density).toInt()
                button.setPadding(padding, padding, padding, padding)
            }
        }
        shortcutAdapter.setItemSize(size)
        fitShortcutRows()
        miniMusicContainer?.visibility = if (!isVertical && width / density >= 640) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.btn_assistant)?.visibility =
            if (isVertical || width / density >= 480) View.VISIBLE else View.GONE
    }

    private fun fitShortcutRows() {
        if (!isVertical) return
        val recycler = dockRecycler ?: return
        val parent = recycler.parent as View
        val density = context.resources.displayMetrics.density
        val size = (rootView.width - 12 * density).toInt()
            .coerceIn((40 * density).toInt(), (64 * density).toInt())
        val row = size + 2 * (2 * density).toInt()
        val available = parent.height
        if (available < row) return
        val height = minOf(available / row, shortcutAdapter.itemCount) * row
        if (recycler.layoutParams.height != height) {
            recycler.layoutParams = recycler.layoutParams.apply { this.height = height }
        }
    }

    private var isListeningVoice = false
    private var voicePulseAnimator: android.animation.ValueAnimator? = null
    private val originalBackground = btnAssistant?.background
    private val originalImageTint = btnAssistant?.imageTintList

    fun updateShortcuts(shortcuts: List<AppShortcut>) {
        shortcutAdapter.submitItems(shortcuts)
        fitShortcutRows()
    }

    fun release() {
        voicePulseAnimator?.cancel()
        voicePulseAnimator = null
        dockRecycler?.adapter = null
    }

    fun setVoiceListening(listening: Boolean, level: Float = 0f) {
        val btn = btnAssistant ?: return
        if (listening) {
            val baseScale = 1.0f + (level.coerceIn(0f, 1f) * 0.18f)
            btn.scaleX = baseScale
            btn.scaleY = baseScale
            btn.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))

            if (!isListeningVoice) {
                isListeningVoice = true
                val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(android.graphics.Color.parseColor("#2600E5FF"))
                    setStroke(
                        (3 * context.resources.displayMetrics.density).toInt(),
                        android.graphics.Color.parseColor("#00E5FF")
                    )
                }
                btn.background = glowDrawable

                voicePulseAnimator?.cancel()
                voicePulseAnimator = android.animation.ValueAnimator.ofFloat(0.45f, 1.0f).apply {
                    duration = 550
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    addUpdateListener { anim ->
                        btn.alpha = anim.animatedValue as Float
                    }
                    start()
                }
            }
        } else {
            if (isListeningVoice) {
                isListeningVoice = false
                voicePulseAnimator?.cancel()
                voicePulseAnimator = null
                btn.alpha = 1.0f
                btn.scaleX = 1.0f
                btn.scaleY = 1.0f
                btn.background = originalBackground
                btn.imageTintList = originalImageTint
            }
        }
    }

    private var appliedMedia: MedyaParcasi? = null
    private var appliedModeIcon: Int? = null

    fun updateMedia(medya: MedyaParcasi) {
        val previous = appliedMedia
        if (previous != null && previous.baslik == medya.baslik && previous.caliyorMu == medya.caliyorMu) return
        appliedMedia = medya
        miniMusicTitle?.text = if (medya.baslik.isNotBlank()) medya.baslik else "Müzik"
        miniMusicPlay?.setImageResource(
            if (medya.caliyorMu) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    fun updateModeButton(isDesktopMode: Boolean, isFullScreenMap: Boolean) {
        val iconRes = when {
            isDesktopMode -> R.drawable.ic_layout_split
            isFullScreenMap -> R.drawable.ic_desktop_mode
            else -> R.drawable.ic_layout_full
        }
        if (appliedModeIcon != iconRes) {
            appliedModeIcon = iconRes
            btnDesktopMode?.setImageResource(iconRes)
        }
    }
}
