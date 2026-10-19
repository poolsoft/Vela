# Soft clock palettes and radio return follow-up

## Supplied evidence

The newly supplied API 27 head-unit logs identify version 2256, rather than the newer clock builds. At 07:23 and 07:24 repeated HOME entries create temporary tasks while MainActivity receives onNewIntent in task 2178 or 2185. These records do not prove two simultaneous live UI owners. Between sessions the main PID changes (5856 to 6498), and both renderer and diagnostic processes also record previous process loss. API 27 exposes no definitive system exit reason; process loss must not be labeled a confirmed Java crash.

The floating-button bridge only marked the launcher as background onStop. A translucent radio window can leave it paused and visible, explaining an absent background-only floating button. Main-thread watchdog samples also contain Compose work and a PackageManager.hasSystemFeature call from D-pad detection. Those stalls are separate evidence and are not resolved by the task-return changes.

## Implementation

- Retained MainActivity AppTask is targeted before cold task creation, with CLEAR_TOP and SINGLE_TOP delivery. Runtime failure falls back to the existing launch path and records a warning.
- The forwarding Activity has empty task affinity, is excluded from recents, and removes its root task after forwarding. The UI keeps its normal task affinity and singleTask declaration.
- A 500 ms paused-only visibility transition enables the floating button according to its saved mode. Resume and stop cancel the pending callback; no polling or window-focus inference is added.
- Soft clock presets use low-saturation colors. Material You mode uses system accents on API 31+ and cream/lavender on older devices. Background mode samples the displayed panel artwork into a temporary 12x12 bitmap when applying the mode, then discards it. Explicit custom RGB selections are preserved.

## Acceptance boundary

Local build, packaged manifest, and emulator task-return behavior are checked separately from physical radio behavior. The head-unit firmware, its radio Activity/task flags, and unexplained process reclamation require a fresh head-unit log after installing this build.
