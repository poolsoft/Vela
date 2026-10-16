package app.vela.offline

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PmtilesContainerTest {
    private fun archive(change: (ByteArray) -> Unit = {}): File {
        val bytes = ByteArray(132)
        "PMTiles".toByteArray().copyInto(bytes); bytes[7] = 3
        le(bytes, 8, 127); le(bytes, 16, 1)
        le(bytes, 24, 128); le(bytes, 40, 128)
        le(bytes, 56, 128); le(bytes, 64, 4)
        bytes[100] = 0; bytes[101] = 14
        change(bytes)
        return File.createTempFile("vela-import-", ".pmtiles").apply { writeBytes(bytes) }
    }

    private fun le(bytes: ByteArray, at: Int, value: Long) {
        repeat(8) { bytes[at + it] = (value ushr (8 * it)).toByte() }
    }

    private fun rejected(file: File) {
        try {
            PmtilesReader.validateContainer(file)
            fail("Expected invalid map to be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue("Failure must identify the file", error.message.orEmpty().contains(file.name))
        } finally { file.delete() }
    }

    @Test fun validSectionsAndEmptyOptionalSectionsAreAccepted() {
        val file = archive()
        try { PmtilesReader.validateContainer(file) } finally { file.delete() }
    }

    @Test fun alreadyTruncatedSourceIsRejectedEvenIfTransferHashMatches() {
        val file = archive()
        file.writeBytes(file.readBytes().copyOf(130))
        rejected(file)
    }

    @Test fun shortHeaderIsRejectedWithFileName() {
        val file = archive(); file.writeBytes(ByteArray(80)); rejected(file)
    }

    @Test fun invalidMagicIsRejected() = rejected(archive { it[0] = 0 })

    @Test fun metadataOutsideFileIsRejected() = rejected(archive { le(it, 32, 10) })

    @Test fun overflowingLeafRangeIsRejected() = rejected(archive { le(it, 48, Long.MAX_VALUE) })

    @Test fun reversedZoomRangeIsRejected() = rejected(archive { it[100] = 15 })

    @Test fun rootOverlappingHeaderIsRejected() = rejected(archive { le(it, 8, 1) })
}
