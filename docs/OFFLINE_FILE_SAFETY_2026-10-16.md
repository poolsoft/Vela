# Offline file safety

The PMTiles, overlay and OBF index writers and revision/dead-space writers now replace complete synced temporary files instead of truncating active JSON files. The previous file remains visible to readers until the atomic move. File paths and backup formats are unchanged. This does not replace the stores' existing read-modify-write locks.

Sprite copies use atomic file replacement. Glyph ZIP extraction stages beside the installed directory, rejects output outside staging, and validates required ranges before activation. The previous glyph directory remains recoverable until the validated replacement activates. A later lookup restores `glyphs.previous` when activation stopped with the active directory missing. Explicit deletion clears the recovery copy.

Runnable checks: abort a partially written replacement and retain the old file; concurrent readers see only complete versions; restore an interrupted glyph activation; install validated staging and reject a missing stage without damaging the active directory. Build and emulator validation pending.
