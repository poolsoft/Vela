# Dock startup resource loading

The diagnostic-134 cold-start report samples the main thread in `ApplicationPackageManager.getApplicationIcon`, called by `AppDockManager.cozAppShortcut` during its constructor in the first `CarLauncherHostView` composition. The watchdog reports roughly three seconds without a main-thread heartbeat. This is a sampled wait, not proof of a system ANR or of every later stall having the same cause.

The dock now publishes shortcut identity/order/mode first, then resolves labels and icons on an IO worker. Completion enriches current items; it preserves edits made during loading and discards superseded reloads. Missing apps disappear without deleting unrelated shortcuts. Default persistence waits for metadata resolution. The drawer cache and renderer lifecycle remain unchanged.

## Validation

- Source commit `d76f897dc` is pushed on `codex/maplibre-10.3.7`. Build APK run `38907363544` succeeded and published `diagnostic-135`; x86 `0.4.135-diagnostic (2235)` is installed on the API-30 emulator without clearing data.
- Local Kotlin compilation and `DockMetadataTest` passed (two tests, zero failures). CI core and renderer boundary tests also passed.
- The first post-install startup still recorded a roughly three-second wait while initializing the Compose theme, before dock metadata began loading. A later startup with Wi-Fi enabled sampled `HardwareRenderer.nSetStopped`. Neither sample contains the old dock `getApplicationIcon` call on the main thread. These are different observed waits, not proof of a common cause.
- A subsequent explicitly offline cold launch produced a full map frame, one style load, and no main-thread stall report during the captured startup window. Dock metadata loaded on a dispatcher worker and completed in about 1.85 seconds. Renderer binding followed activity creation by about 2.7 seconds in that run.
- The older diagnostic-134 run recorded about 13.8 seconds to renderer binding. These are individual runs with different runtime/cache/network conditions, not a controlled benchmark or a general speedup claim.
- Raw reports and screenshots remain in ignored `.logs/dock-startup-test/` and `.logs/map-camera-bootstrap-test/`. Target head-unit performance remains unverified.

The remaining work is to measure initial graphics/theme waits and surface-resize latency independently, then harden offline index writes and glyph-pack replacement. No watchdog threshold was raised and diagnostic reporting was not disabled.

- Offline drawer, large-player and background/return checks retained the launcher and renderer PIDs and kept the style load count at one. Drawer and player contents were also inspected in screenshots. The captured offline session still had no stall report after these transitions.
