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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    val rootView: View = LayoutInflater.from(context).inflate(R.layout.fragment_app_drawer, null, false)

    private val recyclerView: RecyclerView? = rootView.findViewById(R.id.apps_recycler_view)
    private val loadingProgress: ProgressBar? = rootView.findViewById(R.id.loading_progress)
    private val searchInput: EditText? = rootView.findViewById(R.id.search_input)
    private val btnClose: ImageButton? = rootView.findViewById(R.id.btn_close_drawer)

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tumUygulamalar: List<AracUygulamasi> = emptyList()
    private val adapter = DrawerAppAdapter(
        onItemClick = onAppClick,
        onLongClick = { app ->
            val added = AppDockManager.getInstance(context).kisayolEkle(app.paketAdi)
            Toast.makeText(context, if (added) R.string.car_dock_added else R.string.car_dock_already_added, Toast.LENGTH_SHORT).show()
        }
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
        val grid = GridLayoutManager(context, 2)
        recyclerView?.layoutManager = grid
        recyclerView?.addOnLayoutChangeListener { view, l, _, r, _, _, _, _, _ ->
            val usable = r - l - view.paddingLeft - view.paddingRight
            val columns = (usable / (88 * context.resources.displayMetrics.density)).toInt().coerceIn(2, 8)
            if (grid.spanCount != columns) grid.spanCount = columns
        }
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
        loadingProgress?.visibility = View.VISIBLE
        scope.launch {
            val appManager = CarAppManager.getInstance(context)
            val liste = appManager.yukluUygulamalariGetir()
            tumUygulamalar = liste
            withContext(Dispatchers.Main) {
                loadingProgress?.visibility = View.GONE
                filtrele(searchInput?.text?.toString().orEmpty())
            }
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
        private val onLongClick: (AracUygulamasi) -> Unit
    ) : RecyclerView.Adapter<DrawerAppAdapter.ViewHolder>() {

        private val items = mutableListOf<AracUygulamasi>()

        fun guncelle(yeniListe: List<AracUygulamasi>) {
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
            holder.bind(item, onItemClick)
            holder.itemView.setOnLongClickListener { onLongClick(item); true }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: ImageView? = itemView.findViewById(R.id.app_icon)
            private val appLabel: TextView? = itemView.findViewById(R.id.app_label)

            fun bind(app: AracUygulamasi, onClick: (AracUygulamasi) -> Unit) {
                appLabel?.text = app.ad
                appIcon?.setImageDrawable(app.ikon)
                itemView.setOnClickListener { onClick(app) }
            }
        }
    }
}
