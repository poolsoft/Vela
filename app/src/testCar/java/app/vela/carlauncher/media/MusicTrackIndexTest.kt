package app.vela.carlauncher.media

import app.vela.carlauncher.model.SesParcasi
import org.junit.Assert.*
import org.junit.Test

class MusicTrackIndexTest {
    @Test fun largeRestoredListPreservesOrderDuplicatesAndUsbRelinking() {
        val tracks = (0 until 1200).map { id ->
            SesParcasi(id.toLong(), "Song $id", "", "", 1000, "/storage/usb1/Music/$id.mp3", contentUri = "content://media/$id")
        }
        val index = MusicTrackIndex(tracks)
        val keys = listOf("/storage/usb0/Music/1199.mp3", "file:usb0:Music/20.mp3",
            "content://media/30", "/missing/no-song.mp3", "/storage/usb0/Music/1199.mp3")
        assertEquals(listOf(1199L, 20L, 30L, 1199L), keys.mapNotNull(index::find).map { it.id })
        assertSame(tracks[1], index.find(tracks[1].libraryKey()))
        assertNull(index.find(null))
    }
    @Test fun relativePathWinsOverDuplicateFilename() {
        val first = SesParcasi(1, "", "", "", 1, "/storage/usb1/A/song.mp3")
        val second = first.copy(id = 2, dosyaYolu = "/storage/usb1/B/song.mp3")
        val index = MusicTrackIndex(listOf(first, second))
        assertSame(second, index.find("/storage/usb0/B/song.mp3"))
        assertSame(first, index.find("/storage/usb0/Other/song.mp3"))
    }
}
