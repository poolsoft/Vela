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
    private val onNextClick: () -> Unit
) {

    val rootView: View = LayoutInflater.from(context).inflate(
        if (isVertical) R.layout.fragment_app_dock_sidebar else R.layout.fragment_app_dock,
        null,
        false
    )

    private val btnAppList: ImageButton? = rootView.findViewById(R.id.btn_app_list)
    private val btnDesktopMode: ImageButton? = rootView.findViewById(R.id.btn_desktop_mode)
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
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> dockManager.kisayolTasi(shortcut.paketAdi, -1)
                        2 -> dockManager.kisayolTasi(shortcut.paketAdi, 1)
                        3 -> dockManager.kisayolSil(shortcut.paketAdi)
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
        miniMusicContainer?.setOnClickListener { onMiniMusicClick() }
        miniMusicPlay?.setOnClickListener { onPlayPauseClick() }
        rootView.findViewById<View>(R.id.mini_btn_next)?.setOnClickListener { onNextClick() }
        rootView.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ -> adaptToSize(r - l, b - t) }

        // OsmAnd Orijinal Kurali: Dikey dock (isVertical == true) iken mini muzik bari tamamen GONE
        if (isVertical) {
            miniMusicContainer?.visibility = View.GONE
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
            updateShortcuts(dockManager.kisayollar.value)
        }
    }

    private fun adaptToSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val density = context.resources.displayMetrics.density
        val cross = if (isVertical) width else height
        val size = (cross - 8 * density).toInt().coerceIn((40 * density).toInt(), (64 * density).toInt())
        for (button in listOf(btnAppList, btnDesktopMode, rootView.findViewById<ImageButton>(R.id.btn_assistant))) {
            button ?: continue
            if (button.layoutParams.width != size || button.layoutParams.height != size)
                button.layoutParams = button.layoutParams.apply { this.width = size; this.height = size }
        }
        shortcutAdapter.setItemSize(size)
        miniMusicContainer?.visibility = if (!isVertical && width / density >= 640) View.VISIBLE else View.GONE
        rootView.findViewById<View>(R.id.btn_assistant)?.visibility =
            if (isVertical || width / density >= 480) View.VISIBLE else View.GONE
    }

    fun updateShortcuts(shortcuts: List<AppShortcut>) = shortcutAdapter.submitItems(shortcuts)

    fun release() {
        dockRecycler?.adapter = null
    }

    fun updateMedia(medya: MedyaParcasi) {
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
        btnDesktopMode?.setImageResource(iconRes)
    }
}
