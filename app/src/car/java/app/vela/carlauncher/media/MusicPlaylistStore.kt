package app.vela.carlauncher.media

import android.content.Context
import android.util.Log
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.*

data class MusicPlaylist(val id: String, val name: String, val trackKeys: List<String>)

fun SesParcasi.libraryKey(): String = dosyaYolu.ifBlank { contentUri.ifBlank { "media:$id" } }

/** Keeps unavailable USB tracks in saved playlists until the user removes them. */
class MusicPlaylistStore private constructor(context: Context) {
    companion object {
        const val FAVORITES = "favorites"
        const val RECENT = "recent"
        const val MOST_PLAYED = "most_played"
        const val RECENTLY_ADDED = "recently_added"
        @Volatile private var instance: MusicPlaylistStore? = null
        fun getInstance(context: Context): MusicPlaylistStore = instance ?: synchronized(this) {
            instance ?: MusicPlaylistStore(context.applicationContext).also { instance = it }
        }
    }
    private val prefs by lazy { context.getSharedPreferences("vela_music_playlists", Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storage = Dispatchers.IO.limitedParallelism(1)
    private val loaded = CompletableDeferred<Unit>()
    private val state = MutableStateFlow<List<MusicPlaylist>>(emptyList())
    val playlists = state.asStateFlow()

    private val historyState = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val history = historyState.asStateFlow()
    init {
        scope.launch {
            val saved = withContext(storage) { load() to readHistory() }
            state.value = saved.first
            historyState.value = saved.second
            loaded.complete(Unit)
        }
    }
    private fun mutate(action: () -> Unit) {
        scope.launch { loaded.await(); action() }
    }

    private fun readHistory(): List<Pair<String, Int>> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).take(200).map { index ->
            val item = array.getJSONObject(index)
            item.getString("key") to item.getInt("count").coerceAtLeast(1)
        }.distinctBy { it.first }
    }.getOrDefault(emptyList())

    fun recordPlay(track: SesParcasi) = mutate {
        val key = track.libraryKey()
        val count = historyState.value.firstOrNull { it.first == key }?.second ?: 0
        val items = (listOf(key to (count.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) +
            historyState.value.filterNot { it.first == key }).take(200)
        scope.launch(storage) { prefs.edit().putString("history", JSONArray().apply {
            items.forEach { (path, plays) -> put(JSONObject().put("key", path).put("count", plays)) }
        }.toString()).apply() }
        historyState.value = items
    }

    fun displayedPlaylists(): List<MusicPlaylist> = listOf(
        state.value.firstOrNull { it.id == FAVORITES } ?: MusicPlaylist(FAVORITES, "", emptyList()),
        MusicPlaylist(RECENT, "", historyState.value.map { it.first }),
        MusicPlaylist(MOST_PLAYED, "", historyState.value.sortedByDescending { it.second }.map { it.first }),
        MusicPlaylist(RECENTLY_ADDED, "", emptyList())
    ) + state.value.filterNot { it.id == FAVORITES }

    fun reload() {
        scope.launch {
            loaded.await()
            val saved = withContext(storage) { load() to readHistory() }
            state.value = saved.first; historyState.value = saved.second
        }
    }

    private fun load(): List<MusicPlaylist> = try {
        val document = JSONObject(prefs.getString("library", null) ?: "{\"version\":1,\"playlists\":[]}")
        require(document.getInt("version") == 1)
        val array = document.getJSONArray("playlists")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val tracks = item.getJSONArray("tracks")
            MusicPlaylist(item.getString("id"), item.getString("name"),
                (0 until tracks.length()).map { tracks.getString(it) }.distinct())
        }.distinctBy { it.id }
    } catch (error: Exception) {
        // Retain the original payload for recovery rather than silently overwriting it.
        prefs.getString("library", null)?.let { prefs.edit().putString("unreadable_backup", it).apply() }
        Log.w("MusicPlaylistStore", "Unable to read playlists", error)
        emptyList()
    }

    private fun save(items: List<MusicPlaylist>) {
        state.value = items
        scope.launch(storage) {
            val array = JSONArray()
            items.forEach { item -> array.put(JSONObject().apply {
                put("id", item.id); put("name", item.name); put("tracks", JSONArray(item.trackKeys))
            }) }
            prefs.edit().putString("library", JSONObject().put("version", 1).put("playlists", array).toString()).apply()
        }
    }

    fun create(name: String): MusicPlaylist {
        require(name.isNotBlank())
        val item = MusicPlaylist(UUID.randomUUID().toString(), name.trim(), emptyList())
        mutate { save(state.value + item) }
        return item
    }

    fun rename(id: String, name: String) = mutate {
        if (name.isBlank() || id == FAVORITES) return@mutate
        save(state.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun delete(id: String) = mutate {
        if (id != FAVORITES) save(state.value.filterNot { it.id == id })
    }

    fun setTracks(id: String, keys: List<String>) = mutate {
        if (id == FAVORITES && state.value.none { it.id == FAVORITES })
            save(state.value + MusicPlaylist(FAVORITES, "", emptyList()))
        save(state.value.map { if (it.id == id) it.copy(trackKeys = keys.distinct()) else it })
    }

    fun addTrack(id: String, track: SesParcasi) = mutate {
        state.value.firstOrNull { it.id == id }?.let { setTracks(id, it.trackKeys + track.libraryKey()) }
    }

    fun removeTrack(id: String, key: String) = mutate {
        state.value.firstOrNull { it.id == id }?.let { setTracks(id, it.trackKeys - key) }
    }

    fun removeTrackFromAll(key: String) = mutate {
        save(state.value.map { it.copy(trackKeys = it.trackKeys - key) })
    }

    fun isFavorite(track: SesParcasi): Boolean {
        val favKeys = state.value.firstOrNull { it.id == FAVORITES }?.trackKeys ?: return false
        val currentKey = track.libraryKey()
        if (currentKey in favKeys) return true
        return favKeys.any { MusicTrackIdentity.matchesReference(it, track) }
    }

    fun toggleFavorite(track: SesParcasi) = mutate {
        val favorites = state.value.firstOrNull { it.id == FAVORITES }
            ?: MusicPlaylist(FAVORITES, "", emptyList()).also { save(state.value + it) }
        val currentKey = track.libraryKey()
        val existingMatch = favorites.trackKeys.firstOrNull { it == currentKey || MusicTrackIdentity.matchesReference(it, track) }
        if (existingMatch != null) {
            setTracks(FAVORITES, favorites.trackKeys - existingMatch)
        } else {
            setTracks(FAVORITES, favorites.trackKeys + currentKey)
        }
    }
}
