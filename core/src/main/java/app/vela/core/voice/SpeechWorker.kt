package app.vela.core.voice

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** A stuck OEM engine can occupy one daemon worker, never the UI or unbounded new threads. */
internal class SpeechWorker(capacity: Int = 8) : AutoCloseable {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { task -> Thread(task, "VelaSpeechWorker").apply { isDaemon = true } })
    fun execute(task: () -> Unit): Boolean = try { executor.execute(task); true }
        catch (_: RejectedExecutionException) { false }
    fun clearPending() { executor.queue.clear() }
    override fun close() { executor.shutdownNow() }
}
