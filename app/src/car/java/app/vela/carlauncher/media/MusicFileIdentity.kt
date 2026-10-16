package app.vela.carlauncher.media

import java.io.File

/** Resolve storage symlinks without changing saved playlist references or merging real copies. */
internal fun musicFileIdentity(file: File): String =
    runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)


/** Saved-index identity must not stat every media file during startup. Scans retain canonical checks. */
internal fun cachedMusicFileIdentity(path: String): String {
    val normalized = File(path).normalize().invariantSeparatorsPath
    for (prefix in listOf("/sdcard", "/mnt/sdcard", "/storage/self/primary", "/storage/emulated/legacy")) {
        if (normalized.startsWith("$prefix/")) return "/storage/emulated/0/" + normalized.removePrefix("$prefix/")
    }
    return normalized
}
