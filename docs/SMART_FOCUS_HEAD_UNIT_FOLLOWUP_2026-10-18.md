# Active music tracking and head-unit follow-up

## Evidence

The head-unit logs identify version 2255 on an API-27 L9211B. HOME return at 09:59 uses task 2151 repeatedly; the later process uses task 2165. These records do not show a duplicate live launcher owner for those returns. A later previous-process-ended report has no Android exit reason on API 27, so it does not establish the cause. Main-thread watchdog observations include expensive Compose layout, rather than a confirmed system ANR. The supplied app-accessible logcat contains no media-listener connection, session list, or SmartFocus handoff for the reported projection test. Absence of a record does not prove whether access was disabled or the bridge exposed no session.

## Changes

External MediaSessions are followed by default; explicit source exclusions and locks remain available. Idle external sources return to internal controls after 2 seconds, with 8 seconds for buffering or browsing a selected paused source. Returning to internal controls never auto-plays it. Initial observations prefer already-playing external music over internal playback. Rechecking is transition-driven; idle internal controls have no repeated timer.

Initialization and foreground return query active sessions off the main thread. With notification access granted, a disconnected listener requests rebind. Missing access and session states are written to local logs. Connection, foreground activity and call UI are not fabricated music signals.

Legacy radio tuning events are no longer discarded when another player is selected. Initial sticky events are metadata only; fresh tuning is the legacy protocol playback signal inherited from OsmAnd. Public labels are Radio and Local music rather than hardware brands. Control commands still use hardware-specific adapters internally. This does not guarantee support for an undocumented radio protocol.

The HOME entry uses a translucent window and forwards after resume, with focus as an earlier trigger instead of launching and finishing immediately from NoDisplay onCreate. The retained main UI remains singleTask and car PiP remains disabled. This addresses the Android app-switch timing path; the reported radio behavior still requires head-unit acceptance.

## Validation

Validation completed locally: 22 SmartFocusPolicy tests passed with zero failures. Diagnostic x86 version 2257 assembled successfully and was installed on emulator-5554 without clearing Vela data. The packaged manifest check confirmed the singleTask launcher, unique HOME forwarder and disabled car PiP. Real MediaSession test fixtures verified default tracking of an unconfigured external application, silent internal-control fallback after pause, projection-session tracking, handoff to another player, and pause delivery to the previous projection session. Four successive Settings-to-HOME returns reached the same MainActivity; the activity task dump contained one launcher owner. Both temporary probe applications were uninstalled. These fixtures publish state and commands, not physical BT or projection audio. Physical ZLink audio, radio controls and the reported radio-to-HOME return still need acceptance on the head unit.
