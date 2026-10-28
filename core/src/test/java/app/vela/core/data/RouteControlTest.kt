package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import kotlinx.coroutines.*
import net.osmand.router.RouteCalculationProgress
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RouteControlTest {
    @Test fun heapBudgetLeavesRoomForTheLauncherOnA96MiBDevice() {
        val mb = 1024L * 1024
        assertEquals(18, RoutingHeapBudget.routeMb(96 * mb, 36 * mb))
        assertEquals(0, RoutingHeapBudget.routeMb(96 * mb, 60 * mb))
        assertEquals(96, RoutingHeapBudget.routeMb(1024 * mb, 100 * mb))
        assertEquals(4, RoutingHeapBudget.lookupMb(96 * mb))
        assertTrue(RoutingHeapBudget.pressure(96 * mb, 85 * mb))
        assertFalse(RoutingHeapBudget.pressure(96 * mb, 60 * mb))
    }

    @Test fun canceledAwaitSetsTheBlockingRoutersProgressFlag() = runBlocking {
        val entered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val progress = RouteCalculationProgress()
        val engine = object : RouteEngine {
            override fun isReady(mode: TravelMode) = true
            override fun route(origin: LatLng, destination: LatLng, mode: TravelMode,
                avoidTolls: Boolean, avoidHighways: Boolean, avoidFerries: Boolean,
                departBearingDeg: Double?): List<Route> = error("Controlled path required")
            override fun routeControlled(origin: LatLng, destination: LatLng, mode: TravelMode,
                avoidTolls: Boolean, avoidHighways: Boolean, avoidFerries: Boolean,
                departBearingDeg: Double?, control: RouteControl): List<Route> {
                control.onCancel = { progress.isCancelled = true }
                entered.countDown()
                try {
                    while (!control.isCanceled) Thread.sleep(1)
                    return emptyList()
                } finally { stopped.countDown() }
            }
        }
        val job = launch(Dispatchers.Default) {
            engine.routeAwait(LatLng(38.54, -121.74), LatLng(38.55, -121.75), TravelMode.DRIVE)
        }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        job.cancel()
        assertTrue(stopped.await(3, TimeUnit.SECONDS))
        job.join()
        assertTrue(progress.isCancelled)
    }

    @Test fun deadlineAndCancellationAreSticky() {
        val control = RouteControl(0)
        assertTrue(control.isCanceled)
        assertEquals("deadline", control.reason)
        control.cancel("caller")
        assertEquals("deadline", control.reason)
    }
}
