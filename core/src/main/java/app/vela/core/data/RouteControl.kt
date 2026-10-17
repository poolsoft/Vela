package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Cancellation belongs to the blocking engine, not just to the coroutine awaiting it. */
class RouteControl(timeoutMs: Long = 60_000) {
    private val canceled = AtomicBoolean()
    private val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0) * 1_000_000
    @Volatile var reason: String? = null
        private set
    @Volatile internal var onCancel: (() -> Unit)? = null
    val isCanceled: Boolean get() {
        if (!canceled.get() && System.nanoTime() >= deadline) cancel("deadline")
        return canceled.get()
    }
    fun cancel(why: String = "caller") {
        if (canceled.compareAndSet(false, true)) {
            reason = why
            onCancel?.invoke()
        }
    }
}

/** Detached fallback calculations must be canceled on success, timeout and parent cancellation. */
internal suspend fun <T> Deferred<T>.awaitAndCancel(timeoutMs: Long): T? =
    try { withTimeoutOrNull(timeoutMs) { await() } } finally { cancel() }

/** Reserve both UI/music headroom and uncounted route allocations; never budget the whole heap. */
internal object RoutingHeapBudget {
    private const val MB = 1024L * 1024
    fun routeMb(maxBytes: Long, usedBytes: Long): Int {
        val reserve = maxOf(24 * MB, maxBytes / 4)
        val available = (maxBytes - usedBytes - reserve).coerceAtLeast(0)
        val budget = minOf(available / 2, maxBytes / 3, 96 * MB) / MB
        return budget.toInt().takeIf { it >= 8 } ?: 0
    }
    fun pressure(maxBytes: Long, usedBytes: Long): Boolean =
        maxBytes - usedBytes < maxOf(12 * MB, maxBytes / 8)
    fun lookupMb(maxBytes: Long): Int = (maxBytes / MB / 24).toInt().coerceIn(2, 8)
}

/** A cancellable bridge for every coroutine route caller, including fallback and multi-stop legs. */
suspend fun RouteEngine.routeAwait(
    origin: LatLng, destination: LatLng, mode: TravelMode,
    avoidTolls: Boolean = false, avoidHighways: Boolean = false, avoidFerries: Boolean = false,
    departBearingDeg: Double? = null,
): List<Route> = withContext(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation ->
        val control = RouteControl()
        continuation.invokeOnCancellation { control.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try {
            val result = routeControlled(origin, destination, mode, avoidTolls, avoidHighways,
                avoidFerries, departBearingDeg, control)
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        }
    }
}
