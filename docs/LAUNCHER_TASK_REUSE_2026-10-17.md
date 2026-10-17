# Launcher task duplication and return

## Evidence

Head-unit diagnostic-142 logs show an already-rendering navigation session entering small/PiP dimensions at 15:22:24. A second MainActivity is created at 15:22:37 within the same main PID. It binds another session at 15:22:41 and times out at 15:23:40 with rendererPid=0. The renderer still belongs to the original session. Earlier overlap also shows old onStop after new onResume, re-exposing the floating button.

The open x86 emulator independently contained two live MainActivity records: a standard task and a HOME task, both in one PID. Focusing the original task restored its existing map immediately. Music continuing by itself is normal foreground-service behavior; the independent Activity records establish the duplication.

## Change

- One weakly held interactive launcher owner per app process.
- A duplicate forwards the intent and finishes before navigation ViewModel, Compose/media UI or renderer setup.
- Alias and floating-button returns restore the exact owner task. Cold MAIN entry is normalized to HOME when Vela holds the role.
- Late non-owner lifecycle/PiP callbacks cannot override foreground state.
- The car variant disables PiP, including normal-app mode and projection takeover. Background navigation and the floating return button remain available. The standard maps variant retains PiP.
- A standalone-PiP regression exposed Android 11 rejecting AppTask.startActivity for the singleTask root; restoring its alias did not reliably expand the retained map. Car PiP is therefore excluded rather than relying on role detection or hidden window APIs.
- No map files are deleted, startup preferences changed or service-based navigation/music intentionally stopped.

## Validation

Diagnostic-143 passed the automated HOME, notification-style and alias task-reuse checks on an x86 API-30 emulator: one owner task, one process and a visibly retained map. Local release navigation testing also retained the route across the floating-button return with one style load. However, a separate normal-app PiP test failed to expand the singleTask owner reliably. Diagnostic-143 is therefore superseded for PiP/projection validation by the car-PiP-disabled follow-up. scripts/test_launcher_task_reuse.py exercises HOME, notification-style direct entry and the actual enabled launcher alias without clearing data or changing network settings. Screenshots and system dumps remain local under .logs/activity-return-audit and .logs/task-return-regression because map data can be private.

Actual Android Auto/head-unit takeover still requires a test on the target ROM. Separate old TTS timeouts and watchdog observations are not evidence that TTS caused this duplicate-task failure.

## Platform reference

[ActivityManager.AppTask](https://developer.android.com/reference/android/app/ActivityManager.AppTask) provides task-bound restoration and activity delivery; no hidden window-management API is used.

The build workflow verifies the packaged MainActivity launch mode and disabled car PiP with scripts/check_car_launcher_manifest.py, rather than checking only source XML. That test correctly rejects diagnostic-143.
