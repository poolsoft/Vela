package app.vela.carlauncher.media

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceHandoffTest {
    @Test fun switchingFromInternalAlwaysPausesInternalPlayer() {
        assertEquals(HandoffAction.PAUSE_INTERNAL, SourceHandoff.decide(
            previous = "internal", target = "hcn_music",
            controllerCanPause = false, hasAdapter = true,
            isRadio = false, radioManuallyRequested = false,
        ))
    }

    @Test fun truthlessOemPlayerIsPausedWhenReturningToInternal() {
        assertEquals(HandoffAction.PAUSE_ADAPTER, SourceHandoff.decide(
            previous = "hcn_music", target = "internal",
            controllerCanPause = false, hasAdapter = true,
            isRadio = false, radioManuallyRequested = false,
        ))
        assertEquals(HandoffAction.PAUSE_ADAPTER, SourceHandoff.decide(
            previous = "xy_music", target = "internal",
            controllerCanPause = false, hasAdapter = true,
            isRadio = false, radioManuallyRequested = false,
        ))
    }

    @Test fun nullStateControllerWithVendorAdapterUsesTheAdapter() {
        assertEquals(HandoffAction.PAUSE_ADAPTER, SourceHandoff.decide(
            previous = "bluetooth", target = "internal",
            controllerCanPause = false, hasAdapter = true,
            isRadio = false, radioManuallyRequested = false,
        ))
    }

    @Test fun pausableMediaSessionIsPausedThroughTransportControls() {
        assertEquals(HandoffAction.PAUSE_CONTROLLER, SourceHandoff.decide(
            previous = "session:com.zjinnova.zlink", target = "internal",
            controllerCanPause = true, hasAdapter = false,
            isRadio = false, radioManuallyRequested = false,
        ))
    }

    @Test fun projectionSessionWithoutPauseSupportReportsConflict() {
        assertEquals(HandoffAction.WARN_CONFLICT, SourceHandoff.decide(
            previous = "session:com.zjinnova.zlink", target = "internal",
            controllerCanPause = false, hasAdapter = false,
            isRadio = false, radioManuallyRequested = false,
        ))
    }

    @Test fun radioNeverReceivesAToggleDisguisedAsPause() {
        assertEquals(HandoffAction.WARN_CONFLICT, SourceHandoff.decide(
            previous = "xy_radio", target = "internal",
            controllerCanPause = false, hasAdapter = true,
            isRadio = true, radioManuallyRequested = true,
        ))
        assertEquals(HandoffAction.NONE, SourceHandoff.decide(
            previous = "xy_radio", target = "internal",
            controllerCanPause = false, hasAdapter = true,
            isRadio = true, radioManuallyRequested = false,
        ))
    }

    @Test fun sameSourceDoesNothing() {
        assertEquals(HandoffAction.NONE, SourceHandoff.decide(
            previous = "internal", target = "internal",
            controllerCanPause = true, hasAdapter = true,
            isRadio = false, radioManuallyRequested = false,
        ))
    }
}
