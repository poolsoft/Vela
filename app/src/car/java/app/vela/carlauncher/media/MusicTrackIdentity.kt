package app.vela.carlauncher.media

import app.vela.carlauncher.model.SesParcasi
import java.io.File
import java.util.Locale

/**
 * Otomotiv port degisikliklerine dayanikli parca kimligi yoneticisi (MusicTrackIdentity).
 * USB bellek farkli bir porta takildiginda (usb0 yerine usb1 vb.) mutlak dosya yolu
 * degisse bile bagil yol (relativePath), dosya adi ve hacim ID uzerinden eslestirme yapar.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object MusicTrackIdentity {

    private const val MEDIA_STORE_PREFIX = "mediastore:"
    private const val FILE_PREFIX = "file:"

    fun create(
        id: Long,
        contentUri: String?,
        volumeId: String?,
        relativePath: String?,
        path: String?
    ): String {
        if (!contentUri.isNullOrBlank() && contentUri.startsWith("content://", ignoreCase = true)) {
            return MEDIA_STORE_PREFIX + contentUri
        }
        val normVolume = normalize(volumeId)
        val normRel = normalize(relativePath)
        if (normRel.isNotEmpty()) {
            return "$FILE_PREFIX$normVolume:$normRel"
        }
        if (!path.isNullOrBlank()) {
            return FILE_PREFIX + normalize(path)
        }
        return "$FILE_PREFIX$normVolume:$id"
    }

    fun extractVolumeId(path: String?): String {
        if (path.isNullOrBlank()) return "INTERNAL"
        val lower = path.lowercase(Locale.ROOT).replace('\\', '/')
        if (lower.startsWith("/storage/emulated/") || lower.startsWith("/data/")) {
            return "INTERNAL"
        }
        val parts = lower.split("/").filter { it.isNotBlank() }
        if (parts.size >= 2) {
            // Ornek: /storage/usb0 veya /storage/1A2B-3C4D
            return parts[1]
        }
        return "USB_GENERIC"
    }

    fun extractRelativePath(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val normalized = path.replace('\\', '/').trim()
        val lower = normalized.lowercase(Locale.ROOT)

        if (lower.startsWith("/storage/emulated/0/")) {
            return normalized.substring("/storage/emulated/0/".length)
        }
        // USB path: /storage/XXXX-XXXX/Music/song.mp3 -> Music/song.mp3
        val parts = normalized.split("/").filter { it.isNotBlank() }
        if (parts.size > 2 && (lower.startsWith("/storage/") || lower.startsWith("/mnt/media_rw/"))) {
            return parts.drop(2).joinToString("/")
        }
        return File(normalized).name
    }

    fun matchesReference(reference: String?, track: SesParcasi?): Boolean {
        if (reference.isNullOrBlank() || track == null) return false

        val key = track.libraryKey()
        if (reference == key || reference == track.dosyaYolu || reference == track.contentUri) {
            return true
        }

        // Port degisimi kontrolu (Port-Agnostic Re-linking)
        val savedRelative = if (reference.startsWith(FILE_PREFIX)) {
            extractFileRelativePath(reference)
        } else {
            extractRelativePath(reference)
        }

        val trackRelative = extractRelativePath(track.dosyaYolu)
        if (savedRelative.isNotEmpty() && trackRelative.isNotEmpty()) {
            if (savedRelative.equals(trackRelative, ignoreCase = true)) {
                return true
            }
            // Sadece dosya adi eslesmesi kontrolu (ayni dosya adi)
            val savedFileName = savedRelative.substringAfterLast('/')
            val trackFileName = trackRelative.substringAfterLast('/')
            if (savedFileName.isNotEmpty() && savedFileName.equals(trackFileName, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun extractFileRelativePath(mediaId: String): String {
        val volumeSeparator = mediaId.indexOf(':', FILE_PREFIX.length)
        if (volumeSeparator < 0 || volumeSeparator + 1 >= mediaId.length) {
            return ""
        }
        return mediaId.substring(volumeSeparator + 1)
    }

    private fun normalize(value: String?): String {
        return value?.replace('\\', '/')?.trim()?.lowercase(Locale.ROOT) ?: ""
    }
}
