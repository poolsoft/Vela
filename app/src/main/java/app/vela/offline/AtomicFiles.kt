package app.vela.offline

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Readers keep seeing the previous complete file until the synced replacement is ready. */
internal fun File.writeAtomically(write: (FileOutputStream) -> Unit) {
    val target = absoluteFile
    val parent = requireNotNull(target.parentFile).apply { mkdirs() }
    val staging = File.createTempFile(".${target.name}.", ".tmp", parent)
    try {
        FileOutputStream(staging).use { stream -> write(stream); stream.fd.sync() }
        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
    } finally {
        staging.delete()
    }
}

internal fun File.writeAtomicText(text: String) = writeAtomically { it.write(text.toByteArray(Charsets.UTF_8)) }

/** Restore the old glyph directory after a process stopped between activation renames. */
internal fun recoverGlyphDirectory(root: File) {
    val previous = File(root.parentFile, "${root.name}.previous")
    if (!root.exists() && previous.isDirectory) check(previous.renameTo(root) || root.isDirectory) { "Glyph recovery failed" }
}

internal fun installGlyphDirectory(staging: File, root: File) {
    check(staging.isDirectory) { "Glyph staging missing" }
    recoverGlyphDirectory(root)
    val previous = File(root.parentFile, "${root.name}.previous")
    check(!previous.exists() || previous.deleteRecursively()) { "Glyph backup cleanup failed" }
    if (root.exists()) check(root.renameTo(previous)) { "Glyph backup failed" }
    try {
        check(staging.renameTo(root)) { "Glyph activation failed" }
    } catch (error: Exception) {
        recoverGlyphDirectory(root)
        throw error
    }
    previous.deleteRecursively()
}
