# Offline routing and low-end map fixes

## Runtime changes

- All coroutine route callers, including fallback, avoid, bicycle and multi-stop paths, use a cancellable bridge into the blocking engine. Detached fallback work is canceled on every exit.
- New OBF requests cancel obsolete work and wait on an interruptible/timed lock; canceled waiters do not start a search. The engine gets its own default 60-second deadline and a 100 ms pressure/deadline watchdog.
- OBF budgets derive from the real process heap and reserve launcher/music headroom. Low-headroom requests fail safely rather than using a fixed 256 MB budget on a 96 MiB process. Tile contexts unload on exit and before route planning; speed-limit lookups use a small adaptive context and skip a busy router.
- Car routes enable Java HH when indexes exist, and base-road two-phase routing when base subregions exist. No new native ABI or dependency is required. The installed data decides the available hierarchy.
- The no-file OsmandRegions adapter now returns empty downloadable-region suggestions. Merely setting CALCULATE_MISSING_MAPS=false was insufficient: HH still queried that uninitialized reader and failed with Reader == null. Coverage and actual route computation still use installed OBF readers.
- Standard remains Light on low-end devices after the first frame; Custom is the explicit user override. GLES classification reads packed reqGlEsVersion instead of trying to parse the display string 2.0 as hexadecimal. This retains 30 FPS, a flat camera and the existing reduced layer budget on weak devices.
- HOME intent filters/default selection are not rewritten and no crash restart loop is introduced.

## Reproducible validation

The optional ObfRoutePlanningProbeTest uses the public Delaware fixture from the upstream obf-regions release, not a private trip. A Gradle init file sets the test worker maxHeapSize to 96m:

```groovy
allprojects {
    tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { maxHeapSize = "96m" }
}
```

Run core:testDebugUnitTest with tests app.vela.core.data.ObfRoutePlanningProbeTest and app.vela.core.data.RouteControlTest, -DvelaObf=<directory containing delaware.obf and index.json>, and -I <init file>.

At 96 MiB, the public Wilmington-to-Delmar fixture completed a 161.6 km road route with full directions in about 5.3 seconds (test duration includes setup/conversion, not a head-unit benchmark). A second real-router test waited for the old search to enter the engine, replaced it, verified superseded cancellation and completed the replacement route. Three additional checks cover the adaptive budget, actual cancellation callback propagation to the router progress flag, and deadline behavior. All five checks passed. Fixture scenarios collect prior test objects between cases; production does not force GC or retry allocations in a GC loop. This is not a sustained-drive memory soak test.

Initial testing found and fixed the HH world-regions reader failure; the final accepted run uses the installed HH indexes successfully. The earlier classic-engine memory failure must not be presented as evidence that the data lacks HH.

Full debug/release core tests, car profile test, CI APK and emulator verification are pending at this checkpoint.

## Limits and follow-up

A safe budget does not guarantee every route fits every OBF package. HH data, graph size, road restrictions and actual launcher headroom matter. Native Valhalla has no equivalent cooperative cancellation implementation in this patch; late native work can still finish into a discarded result. The fixed guards and hierarchy selection target the OBF path observed in the head-unit crash.

Repeat the user's long route on the actual head unit, with music playing and offline, including cancel/replacement and drawer/panel/background transitions. Confirm heap pressure does not become sustained GC and that the launcher survives failures. A desktop JVM or stronger emulator is not a substitute for that validation.
