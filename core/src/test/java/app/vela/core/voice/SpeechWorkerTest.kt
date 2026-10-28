package app.vela.core.voice

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SpeechWorkerTest {
    @Test fun blockedEngineDoesNotBlockCallerAndHasBoundedPendingWork() {
        val worker = SpeechWorker(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stale = AtomicBoolean(false)
        try {
            assertTrue(worker.execute { entered.countDown(); release.await() })
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertTrue(worker.execute { stale.set(true) })
            assertFalse(worker.execute { error("Queue must reject excess work") })
            worker.clearPending()
            val cleaned = CountDownLatch(1)
            assertTrue(worker.execute { cleaned.countDown() })
            assertEquals(1L, cleaned.count) // Cleanup waits on the worker, never on the caller.
            release.countDown()
            assertTrue(cleaned.await(2, TimeUnit.SECONDS))
            assertFalse(stale.get())
        } finally { release.countDown(); worker.close() }
    }
}
