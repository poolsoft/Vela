package app.vela.carlauncher.media

import org.junit.Assert.*
import org.junit.Test

class SmartFocusPolicyTest {
    private fun s(id: String, playing: Boolean = false, eligible: Boolean = true, available: Boolean = true, preparing: Boolean = false) =
        SmartFocusPolicy.Source(id, playing, eligible, available, preparing)
    private fun choose(p: SmartFocusPolicy, sources: List<SmartFocusPolicy.Source>, current: String = "internal",
        now: Long = 0, pin: Boolean = false, call: Boolean = false, auto: Boolean = true) =
        p.choose(sources, current, auto, pin, call, now)

    @Test fun manualTrackSelectionSuppressesThePreviousContinuousPlaybackEvenAfterPauseGrace() {
        val policy = SmartFocusPolicy()
        val before = listOf(s("internal"), s("bluetooth", true))
        policy.selected("internal", before, now = 100)
        choose(policy, listOf(s("internal", true), s("bluetooth", true)), now = 101)
        val paused = listOf(s("internal"), s("bluetooth", true))
        assertEquals("internal", choose(policy, paused, now = 102))
        assertEquals("internal", choose(policy, paused, now = 10_000))
    }
    @Test fun latePlaybackSnapshotFromBeforeSelectionCannotTakeOver() {
        val policy = SmartFocusPolicy()
        policy.selected("internal", listOf(s("internal"), s("bluetooth")), now = 100)
        val stale = listOf(s("internal", true), SmartFocusPolicy.Source("bluetooth", true, updatedAt = 99))
        assertEquals("internal", choose(policy, stale, now = 101))
        assertEquals("internal", choose(policy, stale, now = 10_000))
    }
    @Test fun actualPhoneRestartAfterManualTrackSelectionCanTakeOver() {
        val policy = SmartFocusPolicy()
        policy.selected("internal", listOf(s("internal"), s("bluetooth", true)), now = 100)
        choose(policy, listOf(s("internal", true), s("bluetooth")), now = 110)
        val resumed = listOf(s("internal", true), SmartFocusPolicy.Source("bluetooth", true, updatedAt = 120))
        assertEquals("bluetooth", choose(policy, resumed, now = 121))
    }
    @Test fun radioMetadataWithoutPlaybackProofCannotTakeOverAtStartup() {
        val radio = SmartFocusPolicy.Source("xy_radio", true, playbackKnown = false)
        assertEquals("internal", choose(SmartFocusPolicy(), listOf(s("internal"), radio)))
    }
    @Test fun manualUnknownRadioSelectionStaysUntilRealMusicStarts() {
        val policy = SmartFocusPolicy()
        val sources = listOf(s("internal"), SmartFocusPolicy.Source("xy_radio", false, playbackKnown = false), s("bluetooth"))
        policy.selected("xy_radio", sources, now = 100)
        assertEquals("xy_radio", choose(policy, sources, current = "xy_radio", now = 101))
        assertEquals("xy_radio", choose(policy, sources, current = "xy_radio", now = 20_000))
        assertEquals("bluetooth", choose(policy, sources.dropLast(1) + s("bluetooth", true), current = "xy_radio", now = 20_001))
    }
    @Test fun realRadioPlaybackSessionCanTakeOver() {
        val policy = SmartFocusPolicy()
        choose(policy, listOf(s("internal", true), s("xy_radio")))
        assertEquals("xy_radio", choose(policy, listOf(s("internal", true), s("xy_radio", true)), now = 1))
    }
    @Test fun unlockDiscardsSuppressionAndFollowsAlreadyPlayingMusic() {
        val policy = SmartFocusPolicy()
        val sources = listOf(s("internal", true), s("bluetooth", true))
        policy.selected("internal", sources, now = 100)
        assertEquals("internal", choose(policy, sources, pin = true, now = 101))
        policy.resumeAutomatic()
        assertEquals("bluetooth", choose(policy, sources, now = 102))
    }

    @Test fun selectedInternalTrackStaysSelectedWhileSlowStoragePrepares() {
        val policy = SmartFocusPolicy()
        val sources = listOf(s("internal", preparing = true), s("xy_radio", playing = true))
        policy.selected("internal", sources)
        assertEquals("internal", choose(policy, sources, now = 0))
        assertEquals("internal", choose(policy, sources, now = 20_000))
        assertEquals("internal", choose(policy, listOf(s("internal", true), s("xy_radio", true)), now = 20_001))
    }

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
    @Test fun explicitPausedSelectionHasBoundedBrowsingGrace() {
        val p = SmartFocusPolicy()
        val sources = listOf(s("internal", true), s("session:chosen"))
        p.selected("session:chosen", sources)
        assertEquals("session:chosen", choose(p, sources, "session:chosen", now = 0))
        assertEquals("internal", choose(p, sources, "session:chosen", now = 8000))
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
    @Test fun bootSnapshotFollowsAlreadyPlayingExternalMusic() {
        assertEquals("bluetooth", choose(SmartFocusPolicy(), listOf(s("internal", true), s("bluetooth", true))))
    }
    @Test fun projectionAndBluetoothIdentityDoNotDependOnTrackMetadata() {
        assertEquals("projection", MediaSourceIdentity.kind("session:com.zjinnova.zlink", "com.zjinnova.zlink"))
        assertEquals("projection", MediaSourceIdentity.kind("session:com.google.android.projection.gearhead", "com.google.android.projection.gearhead"))
        assertEquals("bluetooth", MediaSourceIdentity.kind("bluetooth", null))
        assertEquals("internal", MediaSourceIdentity.kind("internal", null))
    }
    @Test fun pausingAnApplicationReturnsToInternalControls() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("session:music", true)), "session:music")
        val paused = listOf(s("internal"), s("session:music"))
        choose(p, paused, "session:music", now = 1)
        assertEquals("internal", choose(p, paused, "session:music", now = 2001))
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
    @Test fun latestPlayingApplicationIsControlledWithoutStartingInternal() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("session:music")))
        assertEquals("session:music", choose(p, listOf(s("internal"), s("session:music", true)), now = 1))
        val stopped = listOf(s("internal"), s("session:music"))
        choose(p, stopped, "session:music", now = 2)
        assertEquals("internal", choose(p, stopped, "session:music", now = 2002))
    }
    @Test fun pausedProjectionReturnsToInternalWithoutDisconnectingPhone() {
        val p = SmartFocusPolicy()
        choose(p, listOf(s("internal"), s("session:projection", true)), "session:projection")
        val stopped = listOf(s("internal"), s("session:projection"))
        choose(p, stopped, "session:projection", now = 5)
        assertEquals("internal", choose(p, stopped, "session:projection", now = 2005))
    }
    @Test fun returningToAutomaticImmediatelyFollowsExistingExternalPlayback() {
        val p = SmartFocusPolicy()
        val sources = listOf(s("internal"), s("session:selected"), s("bluetooth", true))
        p.selected("session:selected", sources)
        choose(p, sources, "session:selected", pin = true)
        p.resumeAutomatic()
        assertEquals("bluetooth", choose(p, sources, "session:selected", now = 1))
    }
    @Test fun returningToAutomaticWithNoExternalPlaybackShowsInternalImmediately() {
        val p = SmartFocusPolicy()
        val sources = listOf(s("internal"), s("session:selected"))
        p.selected("session:selected", sources)
        p.resumeAutomatic()
        assertEquals("internal", choose(p, sources, "session:selected", now = 1))
    }
}
