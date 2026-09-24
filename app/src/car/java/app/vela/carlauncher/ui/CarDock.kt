package app.vela.carlauncher.ui

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.apps.AppDockManager
import app.vela.carlauncher.model.AppShortcut
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.model.MedyaParcasi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CoMaps / OsmAnd Car Launcher Dock Bileseni.
 * Dikey sidebar (fragment_app_dock_sidebar) veya yatay dock (fragment_app_dock) sisirir.
 * AppDockManager ile dinamik kisayollari senkronize eder.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarDock(
    medya: MedyaParcasi,
    isVertical: Boolean = true,
    onDesktopToggle: () -> Unit,
    onAppListClick: () -> Unit,
    onMuzikOynatDuraklat: () -> Unit,
    onMuzikSonraki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onDashboardTiklandi: () -> Unit,
    onNeonDashboardTiklandi: () -> Unit,
    onAntenTiklandi: () -> Unit,
    onAsistanTiklandi: () -> Unit,
    onAyarlarTiklandi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dockManager = remember { AppDockManager.getInstance(context) }
    val kisayollar by dockManager.kisayollar.collectAsState()

    var saatMetni by remember { mutableStateOf("12\n00") }

    LaunchedEffect(isVertical) {
        val format = if (isVertical) SimpleDateFormat("HH\nmm", Locale.getDefault()) else SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            saatMetni = format.format(Date())
            delay(1000L)
        }
    }

    AndroidView(
        factory = { ctx ->
            val themedContext = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            val layoutId = if (isVertical) R.layout.fragment_app_dock_sidebar else R.layout.fragment_app_dock
            val view = LayoutInflater.from(themedContext).inflate(layoutId, null)

            val btnDesktop = view.findViewById<ImageButton>(R.id.btn_desktop_mode)
            val btnAppList = view.findViewById<ImageButton>(R.id.btn_app_list)
            val miniContainer = view.findViewById<LinearLayout>(R.id.mini_music_container)
            val miniBtnPlay = view.findViewById<ImageButton>(R.id.mini_btn_play)
            val miniBtnNext = view.findViewById<ImageButton>(R.id.mini_btn_next)
            val btnAssistant = view.findViewById<ImageButton>(R.id.btn_assistant)
            val clockContainer = view.findViewById<LinearLayout>(R.id.clock_settings_container)
            val recycler = view.findViewById<RecyclerView>(R.id.dock_recycler)

            btnDesktop?.setOnClickListener { onDesktopToggle() }
            btnAppList?.setOnClickListener { onAppListClick() }
            miniContainer?.setOnClickListener { onMuzikPaneliAc() }
            miniBtnPlay?.setOnClickListener { onMuzikOynatDuraklat() }
            miniBtnNext?.setOnClickListener { onMuzikSonraki() }
            btnAssistant?.setOnClickListener { onAsistanTiklandi() }
            clockContainer?.setOnClickListener { onAyarlarTiklandi() }

            if (recycler != null) {
                val orient = if (isVertical) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL
                recycler.layoutManager = LinearLayoutManager(ctx, orient, false)
            }

            if (isVertical) {
                miniContainer?.visibility = View.GONE
            }

            view
        },
        update = { view ->
            val miniTitle = view.findViewById<TextView>(R.id.mini_music_title)
            val miniBtnPlay = view.findViewById<ImageButton>(R.id.mini_btn_play)
            val dockClock = view.findViewById<TextView>(R.id.dock_clock)
            val recycler = view.findViewById<RecyclerView>(R.id.dock_recycler)

            miniTitle?.text = if (medya.baslik.isNotBlank() && medya.baslik != "Müzik Seçilmedi") {
                medya.baslik
            } else {
                "Müzik"
            }

            miniBtnPlay?.setImageResource(
                if (medya.caliyorMu) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            dockClock?.text = saatMetni

            if (recycler != null) {
                recycler.adapter = DynamicDockShortcutAdapter(
                    items = kisayollar,
                    onItemClick = { shortcut ->
                        when {
                            shortcut.paketAdi == "internal://neon_dashboard" -> onNeonDashboardTiklandi()
                            shortcut.paketAdi == "internal://dashboard" -> onDashboardTiklandi()
                            shortcut.paketAdi == "internal://music" -> onMuzikPaneliAc()
                            shortcut.paketAdi == "internal://settings" -> onAyarlarTiklandi()
                            shortcut.paketAdi == "internal://antenna" -> onAntenTiklandi()
                            InternalApp.isInternalUri(shortcut.paketAdi) -> onAyarlarTiklandi()
                            else -> {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage(shortcut.paketAdi)
                                    if (intent != null) {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                )
            }
        },
        modifier = modifier
    )
}

class DynamicDockShortcutAdapter(
    private var items: List<AppShortcut>,
    private val onItemClick: (AppShortcut) -> Unit
) : RecyclerView.Adapter<DynamicDockShortcutAdapter.ViewHolder>() {

    private var itemSizePx = 0
    fun setItemSize(size: Int) {
        if (itemSizePx == size) return
        itemSizePx = size
        notifyDataSetChanged()
    }

    var onItemLongClick: ((AppShortcut, View) -> Unit)? = null

    fun submitItems(newItems: List<AppShortcut>) {
        if (items == newItems) return
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val button: ImageButton) : RecyclerView.ViewHolder(button)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val density = parent.context.resources.displayMetrics.density
        val size = (48 * density).toInt()

        val button = ImageButton(parent.context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                topMargin = (2 * density).toInt()
                bottomMargin = (2 * density).toInt()
                leftMargin = (2 * density).toInt()
                rightMargin = (2 * density).toInt()
            }
            setBackgroundResource(R.drawable.bg_dock_item_ripple)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val padding = (8 * density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        return ViewHolder(button)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (itemSizePx > 0) holder.button.layoutParams = holder.button.layoutParams.apply {
            width = itemSizePx
            height = itemSizePx
        }
        val item = items[position]
        if (item.ikon != null) {
            holder.button.setImageDrawable(item.ikon)
        } else {
            holder.button.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        holder.button.contentDescription = item.ad
        holder.button.setOnClickListener { onItemClick(item) }
        holder.button.setOnLongClickListener { view ->
            onItemLongClick?.let { it(item, view); true } ?: false
        }
    }

    override fun getItemCount(): Int = items.size
}
