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

    @Test fun unchangedLocalArchivesDoNotExpireButRemoteArchivesStillDo() {
        val header = ByteArray(127)
        "PMTiles".toByteArray().copyInto(header); header[7] = 3
        header[97] = 1; header[98] = 1
        val source = PmtilesTileSource { _, _ -> header }
        assertFalse(source.expired(local = true, nowNanos = Long.MAX_VALUE))
        assertTrue(source.expired(local = false, nowNanos = Long.MAX_VALUE))
        assertFalse(source.expired(local = false))
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

    @Test fun independentTileBodiesAreReadConcurrentlyWithOneCachedDirectory() {
        val body = "tile".toByteArray()
        val directory = byteArrayOf(1, 1, 1, body.size.toByte(), 1)
        val header = ByteArray(127)
        "PMTiles".toByteArray().copyInto(header); header[7] = 3
        fun le(at: Int, value: Long) { repeat(8) { header[at + it] = (value ushr (8 * it)).toByte() } }
        val tileOffset = 127L + directory.size
        le(8, 127); le(16, directory.size.toLong()); le(56, tileOffset)
        header[97] = 1; header[98] = 1; header[100] = 1; header[101] = 1
        val bothReading = java.util.concurrent.CountDownLatch(2)
        val directoryReads = java.util.concurrent.atomic.AtomicInteger()
        val source = PmtilesTileSource { offset, _ ->
            when (offset) {
                0L -> header
                127L -> { directoryReads.incrementAndGet(); directory }
                tileOffset -> {
                    bothReading.countDown()
                    check(bothReading.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Tile body reads serialized" }
                    body
                }
                else -> error("Unexpected offset")
            }
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val reads = (1..2).map { pool.submit<ByteArray?> { source.tile(1, 0, 0) } }
            reads.forEach { assertArrayEquals(body, it.get(8, java.util.concurrent.TimeUnit.SECONDS)) }
            assertEquals(1, directoryReads.get())
        } finally { pool.shutdownNow() }
    }

    @Test fun slowLeafDoesNotBlockAnotherLeafAndFailuresCanRetry() {
        val firstId = PmtilesReader.tileId(1, 0, 0).toInt()
        val secondId = PmtilesReader.tileId(1, 1, 1).toInt()
        val leaf1 = byteArrayOf(1, firstId.toByte(), 1, 4, 1)
        val leaf2 = byteArrayOf(1, secondId.toByte(), 1, 4, 1)
        val root = byteArrayOf(2, firstId.toByte(), (secondId - firstId).toByte(), 0, 0, 5, 5, 1, 0)
        val header = ByteArray(127)
        "PMTiles".toByteArray().copyInto(header); header[7] = 3
        fun le(at: Int, value: Long) { repeat(8) { header[at + it] = (value ushr (8 * it)).toByte() } }
        val leafOffset = 127L + root.size
        le(8, 127); le(16, root.size.toLong()); le(40, leafOffset); le(56, leafOffset + 10)
        header[97] = 1; header[98] = 1; header[100] = 1; header[101] = 1
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val source = PmtilesTileSource { offset, _ ->
            when (offset) {
                0L -> header
                127L -> root
                leafOffset -> {
                    if (attempts.incrementAndGet() == 1) {
                        entered.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        throw java.io.IOException("simulated read failure")
                    }
                    leaf1
                }
                leafOffset + 5 -> leaf2
                leafOffset + 10 -> "tile".toByteArray()
                else -> error("Unexpected offset $offset")
            }
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val slow = pool.submit<ByteArray?> { source.tile(1, 0, 0) }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val independent = pool.submit<ByteArray?> { source.tile(1, 1, 1) }
            assertArrayEquals("tile".toByteArray(), independent.get(3, java.util.concurrent.TimeUnit.SECONDS))
            release.countDown()
            try { slow.get(5, java.util.concurrent.TimeUnit.SECONDS); fail("Expected read failure") }
            catch (error: java.util.concurrent.ExecutionException) { assertTrue(error.cause is java.io.IOException) }
            assertArrayEquals("tile".toByteArray(), source.tile(1, 0, 0))
            assertEquals(2, attempts.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
    }.toByteArray()
}