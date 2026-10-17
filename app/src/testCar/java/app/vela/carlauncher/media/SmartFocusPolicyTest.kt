package app.vela.carlauncher.media

import org.junit.Assert.*
import org.junit.Test

class SmartFocusPolicyTest {
    private fun s(id: String, playing: Boolean = false, eligible: Boolean = true, available: Boolean = true, preparing: Boolean = false) =
        SmartFocusPolicy.Source(id, playing, eligible, available, preparing)
    private fun choose(p: SmartFocusPolicy, sources: List<SmartFocusPolicy.Source>, current: String = "internal",
        now: Long = 0, pin: Boolean = false, call: Boolean = false, auto: Boolean = true) =
        p.choose(sources, current, auto, pin, call, now)

    @Test fun bluetoothConnectionWithoutMusicDoesNotTakeOver() {
        assertEquals("internal", choose(SmartFocusPolicy(), listOf(s("internal", true), s("bluetooth"))))
    }
    @Test fun newBluetoothPlaybackTakesOverEvenWhileInternalStillPlays() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true), s("bluetooth")))
        assertEquals("bluetooth", choose(p, listOf(s("internal", true), s("bluetooth", true)), now = 1))
    }
    @Test fun unapprovedApplicationDoesNotTakeOver() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true), s("session:other", eligible = false)))
        assertEquals("internal", choose(p, listOf(s("internal", true), s("session:other", true, false)), now = 1))
    }
    @Test fun approvedProjectionSessionCanTakeOver() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true), s("session:com.zjinnova.zlink")))
        assertEquals("session:com.zjinnova.zlink", choose(p, listOf(s("internal", true), s("session:com.zjinnova.zlink", true)), now = 1))
    }
    @Test fun explicitLockBlocksAutomaticTakeover() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true)))
        assertEquals("internal", choose(p, listOf(s("internal", true), s("bluetooth", true)), now = 1, pin = true))
    }
    @Test fun callOrSpeechInterruptionDoesNotSwitchMusicSources() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true)))
        assertEquals("internal", choose(p, listOf(s("internal"), s("bluetooth", true)), now = 1, call = true))
    }
    @Test fun metadataRefreshCannotRetakeFromNewPlayingSource() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal", true), s("bluetooth")))
        val both = listOf(s("internal", true), s("bluetooth", true))
        assertEquals("bluetooth", choose(p, both, now = 1))
        assertEquals("bluetooth", choose(p, both.reversed(), "bluetooth", now = 2))
    }
    @Test fun bluetoothPauseWaitsThenShowsSilentInternalFallback() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("bluetooth", true)), "bluetooth")
        val paused = listOf(s("internal"), s("bluetooth"))
        assertEquals("bluetooth", choose(p, paused, "bluetooth", now = 1))
        assertEquals("internal", choose(p, paused, "bluetooth", now = 2001))
    }
    @Test fun bufferingPreservesSourceForEightSeconds() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("bluetooth", true)), "bluetooth")
        val buffering = listOf(s("internal"), s("bluetooth", preparing = true))
        assertEquals("bluetooth", choose(p, buffering, "bluetooth", now = 1))
        assertEquals("bluetooth", choose(p, buffering, "bluetooth", now = 7000))
        assertEquals("internal", choose(p, buffering, "bluetooth", now = 8001))
    }
    @Test fun disconnectedBluetoothFallsBackWithoutStartingPlayback() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("bluetooth", true)), "bluetooth")
        assertEquals("internal", choose(p, listOf(s("internal"), s("bluetooth", available = false)), "bluetooth", now = 1))
    }
    @Test fun explicitPausedSelectionDoesNotJumpBackToOldPlayingSession() {
        val p = SmartFocusPolicy()
        val sources = listOf(s("internal", true), s("session:chosen"))
        p.selected("session:chosen", sources)
        assertEquals("session:chosen", choose(p, sources, "session:chosen", now = 10000))
    }
    @Test fun pausedSelectionIsNotPermanentLockAgainstNewPlayback() {
        val p = SmartFocusPolicy()
        val before = listOf(s("internal"), s("session:chosen"), s("bluetooth"))
        p.selected("session:chosen", before)
        choose(p, before, "session:chosen")
        assertEquals("bluetooth", choose(p, listOf(s("internal"), s("session:chosen"), s("bluetooth", true)), "session:chosen", now = 1))
    }
    @Test fun disabledAutomaticTrackingPreservesChosenSource() {
        assertEquals("internal", choose(SmartFocusPolicy(), listOf(s("internal"), s("bluetooth", true)), auto = false))
    }
    @Test fun bootSnapshotDoesNotTreatExistingSessionsAsNewUserCommands() {
        assertEquals("internal", choose(SmartFocusPolicy(), listOf(s("internal", true), s("bluetooth", true))))
    }
    @Test fun projectionAndBluetoothIdentityDoNotDependOnTrackMetadata() {
        assertEquals("projection", MediaSourceIdentity.kind("session:com.zjinnova.zlink", "com.zjinnova.zlink"))
        assertEquals("projection", MediaSourceIdentity.kind("session:com.google.android.projection.gearhead", "com.google.android.projection.gearhead"))
        assertEquals("bluetooth", MediaSourceIdentity.kind("bluetooth", null))
        assertEquals("internal", MediaSourceIdentity.kind("internal", null))
    }
    @Test fun pausingAnApplicationKeepsItsResumeControls() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("session:music", true)), "session:music")
        val paused = listOf(s("internal"), s("session:music"))
        choose(p, paused, "session:music", now = 1)
        assertEquals("session:music", choose(p, paused, "session:music", now = 2001))
    }
    @Test fun selectThenPlayReleasesTheAudibleOwnerNotTheBrowsedSource() {
        val ownership = PlaybackOwnership()
        ownership.observedPlaying("session:previous")
        val released = mutableListOf<String>()
        ownership.start("session:browsed", "session:browsed", released::add)
        assertEquals(listOf("session:previous"), released)
    }
    @Test fun repeatedStartDoesNotPauseThePreviousOwnerTwice() {
        val ownership = PlaybackOwnership()
        ownership.observedPlaying("session:previous")
        val released = mutableListOf<String>()
        ownership.start("session:next", "session:previous", released::add)
        ownership.start("session:next", "session:next", released::add)
        assertEquals(listOf("session:previous"), released)
    }
    @Test fun preparingInternalOwnerIsReleasedOnExternalTakeover() {
        val ownership = PlaybackOwnership()
        val released = mutableListOf<String>()
        ownership.start("internal", "internal", released::add)
        ownership.start("bluetooth", "internal", released::add)
        assertEquals(listOf("internal"), released)
    }
    @Test fun reentrantCallbackCannotReleaseAnOwnerTwice() {
        val ownership = PlaybackOwnership()
        ownership.observedPlaying("session:previous")
        var releases = 0
        ownership.start("bluetooth", "session:previous") {
            releases++
            ownership.start("bluetooth", "session:previous") { releases++ }
        }
        assertEquals(1, releases)
    }
}
