package app.vela.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AtomicFilesTest {
    @Test fun failedWriteKeepsThePreviousFileAndRemovesStaging() {
        val directory = Files.createTempDirectory("vela-atomic").toFile()
        try {
            val target = File(directory, "index.json")
            target.writeAtomicText("old")
            assertThrows(java.io.IOException::class.java) {
                target.writeAtomically { it.write("partial".toByteArray()); throw java.io.IOException("disk failure") }
            }
            assertEquals("old", target.readText())
            assertEquals(listOf("index.json"), directory.list()!!.toList())
        } finally { directory.deleteRecursively() }
    }

    @Test fun concurrentReadersOnlySeeCompleteReplacements() {
        val directory = Files.createTempDirectory("vela-reader").toFile()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val target = File(directory, "index.json")
            val old = "a".repeat(32000); val updated = "b".repeat(32000)
            target.writeAtomicText(old)
            val reader = executor.submit {
                repeat(100) { val text = target.readText(); assertTrue(text == old || text == updated) }
            }
            repeat(30) { target.writeAtomicText(if (it % 2 == 0) updated else old) }
            reader.get(10, TimeUnit.SECONDS)
        } finally { executor.shutdownNow(); directory.deleteRecursively() }
    }

    @Test fun glyphActivationAndInterruptedActivationPreserveARecoverableDirectory() {
        val directory = Files.createTempDirectory("vela-glyph").toFile()
        try {
            val root = File(directory, "glyphs").apply { mkdirs() }
            File(root, "old.pbf").writeText("old")
            assertTrue(root.renameTo(File(directory, "glyphs.previous")))
            recoverGlyphDirectory(root)
            assertEquals("old", File(root, "old.pbf").readText())
            val staging = File(directory, "staging").apply { mkdirs() }
            File(staging, "new.pbf").writeText("new")
            installGlyphDirectory(staging, root)
            assertEquals("new", File(root, "new.pbf").readText())
            assertFalse(File(directory, "glyphs.previous").exists())
            assertThrows(IllegalStateException::class.java) { installGlyphDirectory(staging, root) }
            assertEquals("new", File(root, "new.pbf").readText())
        } finally { directory.deleteRecursively() }
    }
}
