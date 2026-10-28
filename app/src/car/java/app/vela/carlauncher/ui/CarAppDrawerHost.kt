package app.vela.carlauncher.ui

import android.content.Context
import android.content.res.Configuration
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import app.vela.carlauncher.apps.AppDockManager
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.LaunchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

/**
 * OsmAnd fragment_app_drawer.xml layoutunu buyuk panelde sisen ve yoneten host sinif.
 * - Ustte arama cubugu ve Kapat (X) butonu
 * - Ortada 6 veya 4 sutunlu uygulama izgarasi (apps_recycler_view)
 * - Uygulama baslatildiginda otomatik kapanir ve buyuk harita geri gelir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarAppDrawerHost(
    private val context: Context,
    private val onCloseClick: () -> Unit,
    private val onAppClick: (AracUygulamasi) -> Unit
) {

    val rootView: View = LayoutInflater.from(androidx.appcompat.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)).inflate(R.layout.fragment_app_drawer, null, false)

    private val recyclerView: RecyclerView? = rootView.findViewById(R.id.apps_recycler_view)
    private val loadingProgress: ProgressBar? = rootView.findViewById(R.id.loading_progress)
    private val searchInput: EditText? = rootView.findViewById(R.id.search_input)
    private val btnClose: ImageButton? = rootView.findViewById(R.id.btn_close_drawer)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tumUygulamalar: List<AracUygulamasi> = emptyList()
    private val appManager = CarAppManager.getInstance(context)
    private val adapter = DrawerAppAdapter(
        onItemClick = onAppClick,
        onLongClick = { app, anchor -> showAppMenu(app, anchor) },
        scope = scope,
        appManager = appManager,
    )

    init {
        setupViews()
        uygulamalariYukle()
    }

    fun release() {
        scope.cancel()
        recyclerView?.adapter = null
    }

    private fun setupViews() {
        val grid = object : GridLayoutManager(context, 2) {
            override fun onMeasure(recycler: RecyclerView.Recycler, state: RecyclerView.State,
                widthSpec: Int, heightSpec: Int) {
                val usable = View.MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight
                val columns = drawerColumns(usable, context.resources.displayMetrics.density)
                if (spanCount != columns) spanCount = columns
                super.onMeasure(recycler, state, widthSpec, heightSpec)
            }
        }
        recyclerView?.layoutManager = grid
        recyclerView?.adapter = adapter

        btnClose?.setOnClickListener {
            onCloseClick()
        }

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrele(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun uygulamalariYukle() {
        loadingProgress?.visibility = if (appManager.apps.value == null) View.VISIBLE else View.GONE
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            appManager.apps.collect { liste ->
                if (liste == null) return@collect
                tumUygulamalar = liste.items
                loadingProgress?.visibility = View.GONE
                filtrele(searchInput?.text?.toString().orEmpty())
            }
        }
    }

    private fun showAppMenu(app: AracUygulamasi, anchor: View) {
        VelaPopupMenu(context, anchor).apply {
            menu.add(0, 1, 0, R.string.car_app_add_dock)
            if (!app.paketAdi.startsWith("internal://")) {
                menu.add(0, 4, 1, R.string.car_app_dock_split)
                menu.add(0, 5, 2, R.string.car_app_dock_freeform).isEnabled = appManager.supportsFreeform()
                menu.add(0, 2, 1, R.string.car_app_info)
                menu.add(0, 3, 2, R.string.car_app_uninstall)
            }
            setOnMenuItemClickListener { item ->
                if (item.itemId == 1 || item.itemId == 4 || item.itemId == 5) {
                    val mode = when (item.itemId) {
                        4 -> LaunchMode.SPLIT_SCREEN
                        5 -> LaunchMode.OVERLAY
                        else -> LaunchMode.FULL_SCREEN
                    }
                    val manager = AppDockManager.getInstance(context)
                    if (!manager.kisayolEkle(app.paketAdi, mode)) manager.setLaunchMode(app.paketAdi, mode)
                    Toast.makeText(context, R.string.car_app_mode_saved, Toast.LENGTH_SHORT).show()
                } else {
                    val action = if (item.itemId == 2) android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS else android.content.Intent.ACTION_DELETE
                    runCatching { context.startActivity(android.content.Intent(action, android.net.Uri.parse("package:${app.paketAdi}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(context, R.string.car_app_launch_failed, Toast.LENGTH_SHORT).show() }
                }
                true
            }
            show()
        }
    }

    private fun filtrele(query: String) {
        val filtrelenmis = if (query.isBlank()) {
            tumUygulamalar
        } else {
            val q = query.lowercase().trim()
            tumUygulamalar.filter { it.ad.lowercase().contains(q) || it.paketAdi.lowercase().contains(q) }
        }
        adapter.guncelle(filtrelenmis)
    }

    private class DrawerAppAdapter(
        private val onItemClick: (AracUygulamasi) -> Unit,
        private val onLongClick: (AracUygulamasi, View) -> Unit,
        private val scope: CoroutineScope,
        private val appManager: CarAppManager,
    ) : RecyclerView.Adapter<DrawerAppAdapter.ViewHolder>() {

        private val items = mutableListOf<AracUygulamasi>()

        fun guncelle(yeniListe: List<AracUygulamasi>) {
            if (items == yeniListe) return
            items.clear()
            items.addAll(yeniListe)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app_drawer, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val cached = item.ikon ?: appManager.peekIcon(item.paketAdi)
            holder.bind(item, cached, onItemClick)
            holder.iconJob?.cancel()
            holder.iconJob = null
            if (cached == null) holder.iconJob = scope.launch {
                val icon = appManager.loadIcon(item.paketAdi)
                if (holder.boundPackage == item.paketAdi && icon != null) holder.setIcon(icon)
            }
            holder.itemView.setOnLongClickListener { onLongClick(item, it); true }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.iconJob?.cancel()
            holder.boundPackage = null
            holder.hasIcon = false
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var iconJob: Job? = null
            var boundPackage: String? = null
            var hasIcon = false
            private val appIcon: ImageView? = itemView.findViewById(R.id.app_icon)
            private val appLabel: TextView? = itemView.findViewById(R.id.app_label)

            fun bind(app: AracUygulamasi, icon: android.graphics.drawable.Drawable?, onClick: (AracUygulamasi) -> Unit) {
                val samePackage = boundPackage == app.paketAdi
                boundPackage = app.paketAdi
                appLabel?.text = app.ad
                if (icon != null) setIcon(icon)
                else if (!samePackage || !hasIcon) {
                    hasIcon = false
                    appIcon?.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                itemView.setOnClickListener { onClick(app) }
            }
            fun setIcon(icon: android.graphics.drawable.Drawable) { hasIcon = true; appIcon?.setImageDrawable(icon) }
        }
    }
}
