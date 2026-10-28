package app.vela.carlauncher.media

import org.junit.Assert.*
import org.junit.Test

class CachedMusicIdentityTest {
    @Test fun savedAliasesDeduplicateWithoutRequiringMountedFiles() {
        val expected = "/storage/emulated/0/Music/track.mp3"
        assertEquals(expected, cachedMusicFileIdentity("/sdcard/Music/track.mp3"))
        assertEquals(expected, cachedMusicFileIdentity("/storage/self/primary/Music/track.mp3"))
        assertNotEquals(expected, cachedMusicFileIdentity("/storage/ABCD/Music/track.mp3"))
    }
}
