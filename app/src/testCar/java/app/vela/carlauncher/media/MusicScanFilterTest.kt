package app.vela.carlauncher.media

import org.junit.Assert.*
import org.junit.Test

class MusicScanFilterTest {
    @Test fun legacyAndScopedMessagingMediaAreExcluded() {
        listOf(
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Audio/message.mp3",
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/note.opus",
            "/storage/emulated/0/WhatsApp Business/Media/sound.mp3",
            "/storage/emulated/0/Telegram/Telegram Audio/message.mp3",
            "/storage/emulated/0/Android/media/org.telegram.messenger/audio/message.mp3",
            "/sdcard/Signal/audio/message.mp3", "/sdcard/Android/media/com.viber.voip/sound.mp3",
            "/sdcard/Android/media/com.facebook.orca/message.mp3",
        ).forEach { assertTrue(it, MusicScanFilter.excludes(it)) }
    }
    @Test fun systemSoundFoldersAreExcludedOnAnyStorageVolume() {
        listOf("Ringtones", "Notifications", "Alarms", "Voice Notes", "Recordings", "Zil Sesleri", "Alarmlar")
            .forEach { assertTrue(it, MusicScanFilter.excludes("/storage/ABCD-1234/$it/long-sound.mp3")) }
    }
    @Test fun mediaFlagsRejectSoundsEvenInAnOrdinaryMusicFolder() {
        val path = "/storage/emulated/0/Music/custom.mp3"
        assertTrue(MusicScanFilter.excludes(path, isRingtone = true))
        assertTrue(MusicScanFilter.excludes(path, isAlarm = true))
        assertTrue(MusicScanFilter.excludes(path, isNotification = true))
        assertFalse(MusicScanFilter.excludes(path))
    }
    @Test fun directoryBoundariesPreserveOrdinaryAlbumsAndMusicPlayers() {
        listOf("/sdcard/Music/Alarms of Love/song.mp3", "/sdcard/Music/Telegram Song.mp3",
            "/sdcard/Winamp/Music/song.mp3", "/sdcard/Music/Android Dreams/song.mp3",
            "/storage/ABCD-1234/Music/album/song.flac")
            .forEach { assertFalse(it, MusicScanFilter.excludes(it)) }
    }
    @Test fun caseAndPathSeparatorsDoNotBypassExclusions() {
        assertTrue(MusicScanFilter.excludes("/STORAGE/USB/NOTIFICATIONS/sound.mp3"))
        assertTrue(MusicScanFilter.excludes("/storage/USB/ZİL SESLERİ/sound.mp3"))
        assertTrue(MusicScanFilter.excludes("/storage/USB/BİLDİRİMLER/sound.mp3"))
        assertTrue(MusicScanFilter.excludes("C:\\USB\\WhatsApp\\Media\\sound.mp3"))
    }
}
