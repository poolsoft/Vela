package app.vela.carlauncher.media

import java.io.File

/** Resolve storage symlinks without changing saved playlist references or merging real copies. */
internal fun musicFileIdentity(file: File): String =
    runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
