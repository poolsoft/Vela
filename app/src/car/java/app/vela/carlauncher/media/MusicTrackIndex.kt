package app.vela.carlauncher.media

import app.vela.carlauncher.model.SesParcasi
import java.util.Locale

/** One library pass; resolving a restored queue never scans the library per entry. */
internal class MusicTrackIndex(tracks: List<SesParcasi>) {
    private val exact = LinkedHashMap<String, SesParcasi>()
    private val relative = LinkedHashMap<String, SesParcasi>()
    private val names = LinkedHashMap<String, SesParcasi>()
    init {
        tracks.forEach { track ->
            listOf(track.libraryKey(), track.dosyaYolu, track.contentUri).filter { it.isNotBlank() }
                .forEach { exact.putIfAbsent(it, track) }
            val path = MusicTrackIdentity.extractRelativePath(track.dosyaYolu).lowercase(Locale.ROOT)
            if (path.isNotBlank()) {
                relative.putIfAbsent(path, track)
                names.putIfAbsent(path.substringAfterLast('/'), track)
            }
        }
    }
    fun find(reference: String?): SesParcasi? {
        if (reference.isNullOrBlank()) return null
        exact[reference]?.let { return it }
        val path = if (reference.startsWith("file:")) {
            val separator = reference.indexOf(':', 5)
            if (separator >= 0) reference.substring(separator + 1) else reference.removePrefix("file:")
        } else MusicTrackIdentity.extractRelativePath(reference)
        val normalized = path.lowercase(Locale.ROOT)
        return relative[normalized] ?: names[normalized.substringAfterLast('/')]
    }
}
