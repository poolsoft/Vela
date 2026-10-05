package app.vela.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PmtilesTileSourceTest {
    @Test fun localTileAndMissingTileAreServedWithoutReadingWholeArchive() {
        val tile = "vector-tile-body".toByteArray()
        val compressed = gzip(tile)
        // One directory entry: z1/x0/y0, one tile, offset zero.
        val directory = gzip(byteArrayOf(1, 1, 1, compressed.size.toByte(), 1))
        val header = ByteArray(127)
        "PMTiles".toByteArray().copyInto(header); header[7] = 3
        fun le(at: Int, value: Long) { repeat(8) { header[at + it] = (value ushr (8 * it)).toByte() } }
        le(8, 127); le(16, directory.size.toLong()); le(56, 127L + directory.size)
        header[97] = 2; header[98] = 2; header[100] = 1; header[101] = 1
        val archive = header + directory + compressed
        val calls = mutableListOf<Pair<Long, Int>>()
        val source = PmtilesTileSource { offset, length ->
            calls += offset to length
            archive.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
        assertArrayEquals(tile, source.tile(1, 0, 0))
        assertNull(source.tile(1, 1, 1))
        assertNull(source.tile(0, 0, 0))
        assertArrayEquals(tile, source.tile(1, 0, 0))
        assertEquals("directory is cached", 1, calls.count { it.first == 127L })
        assertTrue(calls.none { it.second == archive.size })
    }

    @Test fun malformedDirectoryCountCannotAllocateUnboundedArrays() {
        assertNull(PmtilesReader.decodeDirectory(byteArrayOf(127), 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedDirectoryIsRejectedBeforeRangeRead() {
        val header = ByteArray(127)
        "PMTiles".toByteArray().copyInto(header); header[7] = 3
        header[8] = 127; header[19] = 1 // 16 MB root directory
        header[97] = 1; header[98] = 1
        PmtilesTileSource { _, length -> assertEquals(127, length); header }.tile(0, 0, 0)
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
}