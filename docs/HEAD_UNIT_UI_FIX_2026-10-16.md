# Head-unit UI responsiveness fixes

Runtime scope: TTS UI blocking, initial dock and drawer sizing, delayed cached icons and expensive cached-music startup. The prior head-unit audit remains the baseline; no runtime claim is inferred from successful compilation alone.

- TTS calls run on a bounded worker. The speech deadline starts before queuing, so initialization or an occupied worker cannot hide the timeout. Timeout status/focus updates do not wait for engine stop. Queue invalidation and engine-binding guards reject stale work. One fault-injection JVM test blocks the worker, checks that the caller remains responsive, rejects excess work and drops stale queued work.
- Drawer columns and dock holder dimensions use the first measure, with matching creation and binding dimensions. Pure dimension checks cover small/large widths and density changes; emulator screenshots before scrolling remain required.
- Icon cache hits are synchronous. Same-package binds do not flash a placeholder. Shared per-package tasks limit misses to two concurrent loads, and cached external artwork is reduced to 64 dp (maximum 192 pixels). Dock metadata reuses this cache.
- Saved music metadata restores without file existence, size, canonical-path or last-modified calls for each track. Known primary-storage aliases still deduplicate. Files are checked when played or explicitly scanned. The cache can show unavailable tracks until a user scan; this avoids silently scanning mounted storage at startup.

Validation is in progress. General Compose/workspace drawing cost and device GPU stalls require separate measurements; the map renderer and offline restore are unchanged by this patch.
