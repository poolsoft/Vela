# Offline file safety

The PMTiles, overlay and OBF index writers and revision/dead-space writers now replace complete synced temporary files instead of truncating active JSON files. The previous file remains visible to readers until the atomic move. File paths and backup formats are unchanged. This does not replace the stores' existing read-modify-write locks.

Sprite copies use atomic file replacement. Glyph ZIP extraction stages beside the installed directory, rejects output outside staging, and validates required ranges before activation. The previous glyph directory remains recoverable until the validated replacement activates. A later lookup restores `glyphs.previous` when activation stopped with the active directory missing. Explicit deletion clears the recovery copy.

Runnable checks: abort a partially written replacement and retain the old file; concurrent readers see only complete versions; restore an interrupted glyph activation; install validated staging and reject a missing stage without damaging the active directory. 

## Verification

- Runtime source commit `1f8ce290c`, successful Build APK run `38935209193`, release `diagnostic-136`. The x86 APK `0.4.136-diagnostic` is installed on the emulator without clearing data.
- The Linux CI application regression tests passed. Local compilation passed; two file/glyph tests passed on Windows. The Linux/Android concurrent-reader test is excluded on Windows because Windows denies replacement of open targets; it still runs on Linux CI. This host-only test adjustment does not change APK behavior.
- The emulator test stopped the app, renamed the installed glyph directory to its recovery name, and moved one sprite to a safe test copy. Offline startup recovered the glyph directory and rebuilt the missing sprite through the atomic writer, including replacement of the three existing sprite files. All six required glyph hashes and all four sprite hashes matched their pre-test values. The test restored its saved sprite and left no missing active file.
- A full offline map frame arrived with one style load. Evidence is in ignored `.logs/offline-file-safety-test/result.json` and `.logs/map-camera-bootstrap-test/offline136-recovery-*`.
- A startup watchdog sample still occurred in Android `HardwareRenderer.nSetStopped`, not in the atomic writer or synchronous dock package lookup. Some road shields appear black in the inspected recovery screenshot despite matching asset hashes; visual rendering remains a separate unresolved check. Target head-unit and power-loss durability are unverified.

- The following normal offline cold launch rendered expected white road shields and colored icons, with one style load and no startup stall report in the captured window. This does not establish that first-render texture behavior is fixed; the recovery-startup visual difference remains reproducible evidence to investigate. Normal-launch evidence is in `.logs/map-camera-bootstrap-test/offline136-normal-*`.
