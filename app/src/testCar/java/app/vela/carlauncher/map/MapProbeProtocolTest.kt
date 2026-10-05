package app.vela.carlauncher.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapProbeProtocolTest {
    @Test fun acceptsOnlyCurrentSessionInTheExpectedSeparateProcess() {
        fun accepts(version: Int = 1, session: String = "current", pid: Int = 202, process: String = "app.vela:map_renderer") =
            MapProbeProtocol.validReply(version, session, "current", 101, pid, process, "app.vela:map_renderer")
        assertTrue(accepts())
        assertFalse(accepts(pid = 101))
        assertFalse(accepts(pid = 0))
        assertFalse(accepts(session = "old"))
        assertFalse(accepts(process = "app.vela"))
        assertFalse(accepts(version = 2))
    }
}
