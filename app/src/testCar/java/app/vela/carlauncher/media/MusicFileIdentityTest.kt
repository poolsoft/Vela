package app.vela.carlauncher.media

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class MusicFileIdentityTest {
    @Test fun storageAliasAndMediaStorePathIdentifyTheSameFile() {
        val root = Files.createTempDirectory("vela-music-alias").toFile()
        try {
            val storage = root.resolve("storage").apply { mkdir() }
            val track = storage.resolve("song.wav").apply { writeText("audio") }
            val alias = root.resolve("sdcard")
            val linked = runCatching { Files.createSymbolicLink(alias.toPath(), storage.toPath()) }.isSuccess
            assumeTrue("Symlinks must be supported", linked)
            assertEquals(musicFileIdentity(track), musicFileIdentity(alias.resolve("song.wav")))
            assertEquals(1, listOf(track, alias.resolve("song.wav")).map(::musicFileIdentity).toSet().size)
        } finally {
            Files.deleteIfExists(root.resolve("sdcard").toPath())
            root.deleteRecursively()
        }
    }

    @Test fun distinctFilesWithTheSameNameRemainSeparate() {
        val root = Files.createTempDirectory("vela-music-copies").toFile()
        try {
            val a = root.resolve("a").apply { mkdir() }.resolve("song.wav").apply { writeText("audio") }
            val b = root.resolve("b").apply { mkdir() }.resolve("song.wav").apply { writeText("audio") }
            assertNotEquals(musicFileIdentity(a), musicFileIdentity(b))
        } finally { root.deleteRecursively() }
    }
}
