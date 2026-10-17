# Launcher task duplication and return

## Evidence

Head-unit diagnostic-142 logs show an already-rendering navigation session entering small/PiP dimensions at 15:22:24. A second MainActivity is created at 15:22:37 within the same main PID. It binds another session at 15:22:41 and times out at 15:23:40 with rendererPid=0. The renderer still belongs to the original session. Earlier overlap also shows old onStop after new onResume, re-exposing the floating button.

The open x86 emulator independently contained two live MainActivity records: a standard task and a HOME task, both in one PID. Focusing the original task restored its existing map immediately. Music continuing by itself is normal foreground-service behavior; the independent Activity records establish the duplication.

## Change

- One weakly held interactive launcher owner per app process.
- A duplicate forwards the intent and finishes before navigation ViewModel, Compose/media UI or renderer setup.
- Alias and floating-button returns restore the exact owner task. Cold MAIN entry is normalized to HOME when Vela holds the role.
- Late non-owner lifecycle/PiP callbacks cannot override foreground state.
- Default Home navigation does not automatically enter PiP. Standalone navigation retains PiP and task-bound restoration.
- No map files are deleted, startup preferences changed or service-based navigation/music intentionally stopped.

## Validation

Release compilation and live emulator task-return checks are recorded after execution. scripts/test_launcher_task_reuse.py exercises HOME, notification-style direct entry and the actual enabled launcher alias without clearing data or changing network settings. Screenshots and system dumps remain local under .logs/activity-return-audit and .logs/task-return-regression because map data can be private.

Actual Android Auto/head-unit takeover still requires a test on the target ROM. Separate old TTS timeouts and watchdog observations are not evidence that TTS caused this duplicate-task failure.

## Platform reference

[ActivityManager.AppTask](https://developer.android.com/reference/android/app/ActivityManager.AppTask) provides task-bound restoration and activity delivery; no hidden window-management API is used.
