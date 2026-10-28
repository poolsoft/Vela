# Small-player clock side editor, 2026-10-20

## Delivered behavior

Clock customization opens controls in the existing large side panel. The real small music player is visible on the left in landscape, with its original Cross Boxed typeface as the default. Portrait launcher layouts place it above the controls. The earlier full-page editor with a duplicate preview is superseded.

The existing CarUnifiedPanelHost is reparented for editing and restored on exit. Its media controls, title, background and clock remain real, rather than a sample. Appearance, Colors and Time format controls save immediately, and the attached clock preference listener applies them live. The map composition stays attached but its view is invisible during editing, preventing translucent panel backgrounds revealing mapless gauges underneath. Saved panel position preferences are not overwritten.

Both the small player menu and Car settings entry use the same launcher-panel route. Closing the editor restores the normal launcher layout. No Activity, service or dependency is added. Clock preferences and the typed backup allowlist remain compatible.

## Validation

- 12 selected JVM tests passed: four clock defaults/bounds/backup-schema checks, six PMTiles source checks and two dock metadata checks.
- Final diagnostic x86 APK versionCode 2273 built successfully in 3m 17s; local-only init configuration disables R8 for faster checks, while committed CI retains R8.
- Installed with adb install -r, preserving emulator application data. Packaged manifest check passed: singleTask launcher and PiP disabled.
- The Car settings entry opened the real player at the left and controls at the right.
- The prior emulator test had left the rounded font selected. Restored the original futuristic Cross Boxed choice through the visible editor; the real left clock changed immediately.

- Changing white/pastel palettes and moving the size slider updated the real left clock immediately. Restored the pastel palette, original font and prior size after checking.
- The direct player-menu entry reopened the side editor. Back restored the mapless gauge and large player, with hidden system navigation.
- The final screenshot confirms the underlying gauge no longer shows through the small player. One MainActivity owner record remained and the recent crash buffer contained no Vela crash.

## Implementation

- ClockCustomizationScreen contains only editing controls, without a second clock or timer.
- CarLauncherLayout routes clock requests into CLOCK mode and closes the settings overlay through the existing home-screen request.
- CarLauncherHostView retains/reparents the real player and restores its map view on exit.
- CarLayoutManager applies a temporary left/top player order without changing saved layout preferences.
- SmallClockAppearance and InterlockingClockView retain their existing validated customization and defaults.

## Device follow-up

Verify editing and repeated slider gestures on the physical API-27 head unit. Emulator checks do not establish head-unit performance. This layout correction does not claim to resolve remaining map or music cache bottlenecks.
