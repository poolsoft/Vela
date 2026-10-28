package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.TravelMode
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Optional real-file check using the repository's public Delaware fixture, never a private trip. */
class ObfRoutePlanningProbeTest {
    @Test fun longDriveUsesTheInstalledRoutingHierarchy() {
        val root = System.getProperty("velaObf")?.let(::File)
        assumeTrue("Supply -DvelaObf=<Delaware fixture directory>", root != null)
        // Each optional fixture scenario starts after collecting the previous scenario's objects.
        // Production routing does not force GC or retry allocation in a GC loop.
        System.gc()
        val engine = ObfRouteEngine(root!!)
        try {
            // Wilmington to Delmar exercises a statewide route rather than nearby road snapping.
            assertTrue("Fixture must be ready", engine.isReady(TravelMode.DRIVE))
            val reader = net.osmand.binary.BinaryMapIndexReader(java.io.RandomAccessFile(File(root, "delaware.obf"), "r"), File(root, "delaware.obf"))
            try {
                println("Delaware hierarchy: hh=${reader.hhRoutingIndexes.size}, base=${reader.routingIndexes.sumOf { it.baseSubregions.size }}")
            } finally { reader.close() }
            val control = RouteControl()
            val result = engine.routeControlled(LatLng(39.746, -75.547), LatLng(38.461, -75.577), TravelMode.DRIVE,
                false, false, false, null, control)
            assertTrue("Long-route fixture did not complete: ${control.reason}", result.isNotEmpty())
            assertTrue(result.first().distanceMeters > 150_000)
        } finally { engine.shutdown() }
    }

    @Test fun newerRequestCancelsAnActualRouterSearch() {
        val root = System.getProperty("velaObf")?.let(::File)
        assumeTrue("Supply -DvelaObf=<Delaware fixture directory>", root != null)
        // Each optional fixture scenario starts after collecting the previous scenario's objects.
        // Production routing does not force GC or retry allocation in a GC loop.
        System.gc()
        val engine = ObfRouteEngine(root!!)
        val old = RouteControl()
        val worker = Thread {
            engine.routeControlled(LatLng(39.746, -75.547), LatLng(38.461, -75.577), TravelMode.DRIVE,
                false, false, false, null, old)
        }.apply { isDaemon = true }
        try {
            worker.start()
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (old.onCancel == null && worker.isAlive && System.nanoTime() < deadline) Thread.sleep(1)
            assertTrue("The first request must enter the actual engine: ${old.reason}", old.onCancel != null)
            val replacement = RouteControl()
            val result = engine.routeControlled(LatLng(39.150, -75.494), LatLng(39.160, -75.500), TravelMode.DRIVE,
                false, false, false, null, replacement)
            worker.join(3_000)
            assertTrue("Obsolete engine work must finish", !worker.isAlive)
            assertTrue(old.reason == "superseded")
            assertTrue("Replacement route must complete: ${replacement.reason}", result.isNotEmpty())
        } finally {
            old.cancel()
            worker.join(3_000)
            engine.shutdown()
        }
    }
}
