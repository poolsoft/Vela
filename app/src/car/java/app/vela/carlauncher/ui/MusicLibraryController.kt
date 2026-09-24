package app.vela.carlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.media.MusicManager
import app.vela.carlauncher.media.MusicPlaylist
import app.vela.carlauncher.media.MusicPlaylistStore
import app.vela.carlauncher.media.MusicRepository
import app.vela.carlauncher.media.libraryKey
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

/** Binds the existing music XML: real queue, folder browser and persistent playlists. */
class MusicLibraryController(private val context: Context, private val root: View) {
    private enum class Tab { QUEUE, TRACKS, FOLDERS, PLAYLISTS }
    private val repository = MusicRepository.getInstance(context)
    private val manager = MusicManager.getInstance(context)
    private val store = MusicPlaylistStore.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recycler: RecyclerView = root.findViewById(R.id.music_recycler)
    private val search: EditText = root.findViewById(R.id.search_input)
    private val header: View = root.findViewById(R.id.folder_header_container)
    private val title: TextView = root.findViewById(R.id.folder_header_title)
    private val adapter = LibraryAdapter()
    private val viewPrefs = context.getSharedPreferences("vela_music_library_view", Context.MODE_PRIVATE)
    private var tab = runCatching { Tab.valueOf(viewPrefs.getString("tab", "TRACKS")!!) }.getOrDefault(Tab.TRACKS)
    private var sortOrder = viewPrefs.getString("sort", "title") ?: "title"
    private var folder: String? = null
    private var playlistId: String? = null
    private var tracks = repository.parcalar.value
    private val dialogs = mutableSetOf<AlertDialog>()
    private var scanJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scanJob?.cancel()
            scanJob = scope.launch { delay(400); repository.muzikKutuphanesiniTara() }
        }
    }

    private val searchWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: Editable?) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            root.findViewById<View>(R.id.search_clear_btn).visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            render()
        }
    }

    init {
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        tabViews().forEach { (item, view) -> view.setOnClickListener {
            tab = item
            viewPrefs.edit().putString("tab", item.name).apply()
            folder = null; playlistId = null; search.setText(""); render()
        } }
        root.findViewById<View>(R.id.tab_btn_search).setOnClickListener {
            root.findViewById<View>(R.id.search_bar_container).apply {
                visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
                val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                if (visibility == View.VISIBLE) {
                    search.requestFocus()
                    keyboard.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } else {
                    search.setText("")
                    keyboard.hideSoftInputFromWindow(search.windowToken, 0)
                }
            }
        }
        search.addTextChangedListener(searchWatcher)
        root.findViewById<View>(R.id.search_clear_btn).setOnClickListener { search.setText("") }
        root.findViewById<View>(R.id.btn_back_folder).setOnClickListener {
            folder = null; playlistId = null; search.setText(""); render()
        }
        root.findViewById<View>(R.id.btn_folder_play_all).setOnClickListener { playGroup(false) }
        root.findViewById<View>(R.id.btn_folder_shuffle_all).setOnClickListener { playGroup(true) }
        root.findViewById<View>(R.id.tab_btn_menu).setOnClickListener { anchor -> libraryMenu(anchor) }
        scope.launch { repository.parcalar.collectLatest { tracks = it; render() } }
        scope.launch { repository.taraniyorMu.collectLatest { render() } }
        scope.launch { repository.lastError.collectLatest { render() } }
        scope.launch { store.playlists.collectLatest { render() } }
        scope.launch { store.history.collectLatest { render() } }
        scope.launch { manager.internalPlayer.kuyruk.collectLatest { render() } }
        scope.launch { manager.internalPlayer.anlikParca.collectLatest { render() } }
        scope.launch {
            var lastSource: Boolean? = null
            manager.medyaDurumu.collectLatest {
                val internal = manager.isInternalPlayback()
                if (lastSource != internal) { lastSource = internal; render() }
            }
        }
        runCatching { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer) }
        render()
        refresh()
    }

    fun refresh() {
        render()
        val permission = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
            else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            scanJob?.cancel()
            scanJob = scope.launch { repository.muzikKutuphanesiniTara() }
        }
    }

    fun openPlaylists() {
        tab = Tab.PLAYLISTS
        viewPrefs.edit().putString("tab", Tab.PLAYLISTS.name).apply()
        folder = null
        playlistId = null
        search.setText("")
        render()
    }

    private fun tabViews() = listOf(
        Tab.QUEUE to root.findViewById<TextView>(R.id.tab_queue),
        Tab.TRACKS to root.findViewById<TextView>(R.id.tab_all_tracks),
        Tab.FOLDERS to root.findViewById<TextView>(R.id.tab_folders),
        Tab.PLAYLISTS to root.findViewById<TextView>(R.id.tab_playlists)
    )

    private fun playlist(): MusicPlaylist? = store.displayedPlaylists().firstOrNull { it.id == playlistId }
    private fun name(item: MusicPlaylist): String = when (item.id) {
        MusicPlaylistStore.FAVORITES -> context.getString(R.string.car_music_favorites)
        MusicPlaylistStore.RECENT -> context.getString(R.string.car_music_recent)
        MusicPlaylistStore.MOST_PLAYED -> context.getString(R.string.car_music_most_played)
        else -> item.name
    }
    private fun readOnly(item: MusicPlaylist) = item.id == MusicPlaylistStore.RECENT || item.id == MusicPlaylistStore.MOST_PLAYED

    private fun folderOf(track: SesParcasi): String = track.folderPath.ifBlank { File(track.dosyaYolu).parent.orEmpty() }
    private fun sortedTracks(items: List<SesParcasi>): List<SesParcasi> = when (sortOrder) {
        "artist" -> items.sortedBy { it.sanatci.lowercase(java.util.Locale.ROOT) }
        "album" -> items.sortedBy { it.album.lowercase(java.util.Locale.ROOT) }
        "added" -> items.sortedByDescending { it.eklenmeTarihi }
        "plays" -> {
            val counts = store.history.value.toMap()
            items.sortedByDescending { counts[it.libraryKey()] ?: 0 }
        }
        else -> items.sortedBy { it.baslik.lowercase(java.util.Locale.ROOT) }
    }
    private fun sourceTracks(): List<SesParcasi> = when (tab) {
        Tab.QUEUE -> manager.internalPlayer.kuyruk.value
        Tab.TRACKS -> sortedTracks(tracks)
        Tab.FOLDERS -> sortedTracks(tracks.filter { folderOf(it) == folder })
        Tab.PLAYLISTS -> {
            val byKey = tracks.associateBy { it.libraryKey() }
            playlist()?.trackKeys?.mapNotNull { byKey[it] }.orEmpty()
        }
    }
    private fun matches(text: String): Boolean = text.contains(search.text.toString().trim(), ignoreCase = true)

    private fun render() {
        if (playlistId != null && playlist() == null) playlistId = null
        tabViews().forEach { (item, view) ->
            view.isSelected = item == tab
            view.isFocusable = true
            view.setBackgroundResource(if (item == tab) R.drawable.bg_tab_active else 0)
            view.setTextColor(if (item == tab) 0xFF00FFFF.toInt() else 0xFF888888.toInt())
        }
        val inside = folder != null || playlistId != null
        header.visibility = if (inside) View.VISIBLE else View.GONE
        title.text = folder?.let { File(it).name.ifBlank { it } } ?: playlist()?.let(::name).orEmpty()
        val rows = mutableListOf<LibraryRow>()
        when {
            tab == Tab.FOLDERS && folder == null -> tracks.groupBy(::folderOf).toSortedMap().forEach { (key, items) ->
                if (matches(key)) rows += LibraryRow(File(key).name.ifBlank { context.getString(R.string.car_music_unknown_folder) },
                    context.getString(R.string.car_music_track_count, items.size),
                    onClick = { folder = key; search.setText(""); render() })
            }
            tab == Tab.PLAYLISTS && playlistId == null -> {
                rows += LibraryRow(context.getString(R.string.car_music_new_playlist), onClick = { createPlaylist() })
                store.displayedPlaylists().filter { matches(name(it)) }.forEach { item ->
                    rows += LibraryRow(name(item), context.getString(R.string.car_music_track_count, item.trackKeys.size),
                        onClick = { playlistId = item.id; search.setText(""); render() },
                        onLongClick = if (readOnly(item)) null else { anchor -> playlistMenu(anchor, item) })
                }
            }
            else -> {
                val source = sourceTracks()
                source.filter { matches(it.baslik + " " + it.sanatci) }.forEach { track ->
                    rows += LibraryRow(track.baslik, track.sanatci,
                        playing = manager.isInternalPlayback() && manager.internalPlayer.anlikParca.value?.libraryKey() == track.libraryKey(),
                        favorite = store.isFavorite(track), onFavorite = { store.toggleFavorite(track) },
                        onClick = {
                            if (tab == Tab.QUEUE) {
                                // Selecting a filtered queue row must not replace or reshuffle the queue.
                                manager.playQueueTrack(track)
                            } else manager.oynatDahiliParca(track, source)
                        }, onLongClick = { trackMenu(it, track) })
                }
                if (tab == Tab.PLAYLISTS) {
                    val available = tracks.map { it.libraryKey() }.toSet()
                    playlist()?.takeUnless { readOnly(it) }?.trackKeys?.filter { it !in available && matches(it) }?.forEach { key ->
                        rows += LibraryRow(File(key).name, context.getString(R.string.car_music_unavailable),
                            onLongClick = { anchor -> PopupMenu(context, anchor).apply {
                                menu.add(R.string.car_music_remove_track).setOnMenuItemClickListener {
                                    playlistId?.let { store.removeTrack(it, key) }; true
                                }; show()
                            } })
                    }
                }
            }
        }
        val status = when {
            repository.taraniyorMu.value -> context.getString(R.string.car_music_scanning)
            repository.lastError.value != null -> repository.lastError.value
            rows.isEmpty() -> context.getString(if (tab == Tab.QUEUE) R.string.car_music_queue_empty else R.string.car_music_empty)
            else -> null
        }
        if (status != null) rows.add(0, LibraryRow(status))
        adapter.submit(rows)
    }

    private fun playGroup(shuffle: Boolean) {
        val items = sourceTracks()
        if (items.isEmpty()) return
        manager.setKarisikCal(shuffle)
        manager.oynatDahiliParca(if (shuffle) items.random() else items.first(), items)
    }

    private fun libraryMenu(anchor: View) {
        PopupMenu(context, anchor).apply {
            if (tab == Tab.TRACKS || tab == Tab.FOLDERS) {
                val keys = listOf("title", "artist", "album", "added", "plays")
                val labels = context.resources.getStringArray(R.array.car_music_sort_orders)
                val submenu = menu.addSubMenu(R.string.car_music_sort)
                keys.forEachIndexed { index, key ->
                    submenu.add(labels[index]).apply {
                        isCheckable = true; isChecked = sortOrder == key
                        setOnMenuItemClickListener {
                            sortOrder = key; viewPrefs.edit().putString("sort", key).apply(); render(); true
                        }
                    }
                }
            }
            menu.add(R.string.car_music_new_playlist).setOnMenuItemClickListener { createPlaylist(); true }
            val selected = playlist()
            if (selected != null && !readOnly(selected)) {
                menu.add(R.string.car_music_edit_tracks).setOnMenuItemClickListener { editTracks(selected); true }
                addPlaylistActions(this, selected)
            }
            show()
        }
    }
    private fun playlistMenu(anchor: View, item: MusicPlaylist) {
        PopupMenu(context, anchor).apply {
            menu.add(R.string.car_music_edit_tracks).setOnMenuItemClickListener { editTracks(item); true }
            addPlaylistActions(this, item); show()
        }
    }
    private fun addPlaylistActions(menu: PopupMenu, item: MusicPlaylist) {
        if (item.id == MusicPlaylistStore.FAVORITES) return
        menu.menu.add(R.string.car_music_rename_playlist).setOnMenuItemClickListener {
            nameDialog(R.string.car_music_rename_playlist, item.name) { store.rename(item.id, it) }; true
        }
        menu.menu.add(R.string.car_music_delete_playlist).setOnMenuItemClickListener {
            show(AlertDialog.Builder(context).setTitle(R.string.car_music_delete_playlist).setMessage(item.name)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> store.delete(item.id) }.create()); true
        }
    }
    private fun createPlaylist(track: SesParcasi? = null) {
        nameDialog(R.string.car_music_new_playlist, "") { text ->
            val item = store.create(text)
            if (track != null) store.addTrack(item.id, track) else editTracks(item)
        }
    }
    private fun nameDialog(title: Int, value: String, accept: (String) -> Unit) {
        val input = EditText(context).apply { setSingleLine(true); setText(value); hint = context.getString(R.string.car_music_playlist_name) }
        val dialog = AlertDialog.Builder(context).setTitle(title).setView(input)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, null).create()
        show(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) input.error = context.getString(R.string.car_music_name_required)
            else { dialog.dismiss(); accept(text) }
        }
    }
    private fun editTracks(item: MusicPlaylist) {
        val available = tracks.toList()
        val checked = BooleanArray(available.size) { available[it].libraryKey() in item.trackKeys }
        show(AlertDialog.Builder(context).setTitle(R.string.car_music_edit_tracks)
            .setMultiChoiceItems(available.map { it.baslik + " — " + it.sanatci }.toTypedArray(), checked) { _, index, value -> checked[index] = value }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val keys = available.map { it.libraryKey() }.toSet()
                val retained = item.trackKeys.filter { it !in keys }
                store.setTracks(item.id, retained + available.filterIndexed { index, _ -> checked[index] }.map { it.libraryKey() })
            }.create())
    }
    private fun trackMenu(anchor: View, track: SesParcasi) {
        PopupMenu(context, anchor).apply {
            menu.add(R.string.car_music_add_playlist).setOnMenuItemClickListener {
                val lists = store.playlists.value
                val names = arrayOf(context.getString(R.string.car_music_new_playlist)) + lists.map(::name)
                show(AlertDialog.Builder(context).setTitle(R.string.car_music_add_playlist).setItems(names) { _, index ->
                    if (index == 0) createPlaylist(track) else store.addTrack(lists[index - 1].id, track)
                }.create()); true
            }
            if (tab == Tab.PLAYLISTS && playlist()?.let { !readOnly(it) } == true) menu.add(R.string.car_music_remove_track).setOnMenuItemClickListener {
                playlistId?.let { store.removeTrack(it, track.libraryKey()) }; true
            }
            show()
        }
    }
    private fun show(dialog: AlertDialog) {
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }
    fun release() {
        scope.cancel()
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        search.removeTextChangedListener(searchWatcher)
        dialogs.toList().forEach { it.dismiss() }
        recycler.adapter = null
    }
}

private data class LibraryRow(
    val title: String, val subtitle: String = "", val playing: Boolean = false,
    val favorite: Boolean = false, val onFavorite: (() -> Unit)? = null,
    val onClick: (() -> Unit)? = null, val onLongClick: ((View) -> Unit)? = null
)

private class LibraryAdapter : RecyclerView.Adapter<LibraryAdapter.Holder>() {
    private var rows = emptyList<LibraryRow>()
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.music_title)
        val subtitle: TextView = view.findViewById(R.id.music_artist)
        val playing: ImageView = view.findViewById(R.id.track_playing_indicator)
        val favorite: ImageButton = view.findViewById(R.id.btn_favorite)
    }
    fun submit(items: List<LibraryRow>) { rows = items; notifyDataSetChanged() }
    override fun getItemCount() = rows.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_music_track, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.title.text = row.title
        holder.title.setTextColor(if (row.playing) 0xFF00FFFF.toInt() else 0xFFFFFFFF.toInt())
        holder.subtitle.text = row.subtitle
        holder.playing.visibility = if (row.playing) View.VISIBLE else View.GONE
        holder.favorite.visibility = if (row.onFavorite != null) View.VISIBLE else View.GONE
        holder.favorite.setImageResource(if (row.favorite) android.R.drawable.star_on else android.R.drawable.star_off)
        holder.favorite.contentDescription = holder.itemView.context.getString(if (row.favorite) R.string.car_music_unfavorite else R.string.car_music_favorite)
        holder.favorite.setOnClickListener { row.onFavorite?.invoke() }
        holder.itemView.setOnClickListener { row.onClick?.invoke() }
        holder.itemView.setOnLongClickListener { view -> row.onLongClick?.let { it(view); true } ?: false }
    }
}
