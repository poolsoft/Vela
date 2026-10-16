# Dock startup resource loading

The diagnostic-134 cold-start report samples the main thread in `ApplicationPackageManager.getApplicationIcon`, called by `AppDockManager.cozAppShortcut` during its constructor in the first `CarLauncherHostView` composition. The watchdog reports roughly three seconds without a main-thread heartbeat. This is a sampled wait, not proof of a system ANR or of every later stall having the same cause.

The dock now publishes shortcut identity/order/mode first, then resolves labels and icons on an IO worker. Completion enriches current items; it preserves edits made during loading and discards superseded reloads. Missing apps disappear without deleting unrelated shortcuts. Default persistence waits for metadata resolution. The drawer cache and renderer lifecycle remain unchanged.

Validation pending: local compile, metadata merge regression tests, CI APK and offline emulator cold-start comparison. Raw reports stay in the ignored `.logs` directory.
