package app.vela.carlauncher.media

import android.content.Context
import android.util.Log
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class MusicPlaylist(val id: String, val name: String, val trackKeys: List<String>)

fun SesParcasi.libraryKey(): String = dosyaYolu.ifBlank { contentUri.ifBlank { "media:$id" } }

/** Keeps unavailable USB tracks in saved playlists until the user removes them. */
class MusicPlaylistStore private constructor(context: Context) {
    companion object {
        const val FAVORITES = "favorites"
        const val RECENT = "recent"
        const val MOST_PLAYED = "most_played"
        @Volatile private var instance: MusicPlaylistStore? = null
        fun getInstance(context: Context): MusicPlaylistStore = instance ?: synchronized(this) {
            instance ?: MusicPlaylistStore(context.applicationContext).also { instance = it }
        }
    }
    private val prefs = context.getSharedPreferences("vela_music_playlists", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val playlists = state.asStateFlow()

    private val historyState = MutableStateFlow(readHistory())
    val history = historyState.asStateFlow()

    private fun readHistory(): List<Pair<String, Int>> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).take(200).map { index ->
            val item = array.getJSONObject(index)
            item.getString("key") to item.getInt("count").coerceAtLeast(1)
        }.distinctBy { it.first }
    }.getOrDefault(emptyList())

    fun recordPlay(track: SesParcasi) {
        val key = track.libraryKey()
        val count = historyState.value.firstOrNull { it.first == key }?.second ?: 0
        val items = (listOf(key to (count.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) +
            historyState.value.filterNot { it.first == key }).take(200)
        prefs.edit().putString("history", JSONArray().apply {
            items.forEach { (path, plays) -> put(JSONObject().put("key", path).put("count", plays)) }
        }.toString()).apply()
        historyState.value = items
    }

    fun displayedPlaylists(): List<MusicPlaylist> = listOf(
        state.value.firstOrNull { it.id == FAVORITES } ?: MusicPlaylist(FAVORITES, "", emptyList()),
        MusicPlaylist(RECENT, "", historyState.value.map { it.first }),
        MusicPlaylist(MOST_PLAYED, "", historyState.value.sortedByDescending { it.second }.map { it.first })
    ) + state.value.filterNot { it.id == FAVORITES }

    fun reload() { state.value = load(); historyState.value = readHistory() }

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
        val array = JSONArray()
        items.forEach { item -> array.put(JSONObject().apply {
            put("id", item.id); put("name", item.name); put("tracks", JSONArray(item.trackKeys))
        }) }
        prefs.edit().putString("library", JSONObject().put("version", 1).put("playlists", array).toString()).apply()
        state.value = items
    }

    fun create(name: String): MusicPlaylist {
        require(name.isNotBlank())
        val item = MusicPlaylist(UUID.randomUUID().toString(), name.trim(), emptyList())
        save(state.value + item)
        return item
    }

    fun rename(id: String, name: String) {
        if (name.isBlank() || id == FAVORITES) return
        save(state.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun delete(id: String) {
        if (id != FAVORITES) save(state.value.filterNot { it.id == id })
    }

    fun setTracks(id: String, keys: List<String>) {
        if (id == FAVORITES && state.value.none { it.id == FAVORITES })
            save(state.value + MusicPlaylist(FAVORITES, "", emptyList()))
        save(state.value.map { if (it.id == id) it.copy(trackKeys = keys.distinct()) else it })
    }

    fun addTrack(id: String, track: SesParcasi) {
        state.value.firstOrNull { it.id == id }?.let { setTracks(id, it.trackKeys + track.libraryKey()) }
    }

    fun removeTrack(id: String, key: String) {
        state.value.firstOrNull { it.id == id }?.let { setTracks(id, it.trackKeys - key) }
    }

    fun removeTrackFromAll(key: String) {
        save(state.value.map { it.copy(trackKeys = it.trackKeys - key) })
    }

    fun isFavorite(track: SesParcasi): Boolean = state.value.firstOrNull { it.id == FAVORITES }
        ?.trackKeys?.contains(track.libraryKey()) == true

    fun toggleFavorite(track: SesParcasi) {
        val favorites = state.value.firstOrNull { it.id == FAVORITES }
            ?: MusicPlaylist(FAVORITES, "", emptyList()).also { save(state.value + it) }
        val key = track.libraryKey()
        setTracks(FAVORITES, if (key in favorites.trackKeys) favorites.trackKeys - key else favorites.trackKeys + key)
    }
}
