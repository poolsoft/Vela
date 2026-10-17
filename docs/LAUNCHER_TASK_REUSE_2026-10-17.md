# Launcher task duplication and return

## Evidence

Head-unit diagnostic-142 logs show an already-rendering navigation session entering small/PiP dimensions at 15:22:24. A second MainActivity is created at 15:22:37 within the same main PID. It binds another session at 15:22:41 and times out at 15:23:40 with rendererPid=0. The renderer still belongs to the original session. Earlier overlap also shows old onStop after new onResume, re-exposing the floating button.

The open x86 emulator independently contained two live MainActivity records: a standard task and a HOME task, both in one PID. Focusing the original task restored its existing map immediately. Music continuing by itself is normal foreground-service behavior; the independent Activity records establish the duplication.

## Change

- HOME belongs to the lightweight CarLauncherEntryActivity, which forwards to MainActivity and finishes without creating map or media UI.
- MainActivity always receives a standard LAUNCHER intent, with the original command forwarded separately. It never registers HOME in the car manifest.
- Native car PiP is disabled. Background navigation, music and the floating return button remain available. Standard maps builds retain PiP.
- The owner guard prevents late non-owner callbacks from overwriting foreground state.
- Updating the HOME component may require selecting Vela as the default Home app once again; application data remains intact.
- Diagnostic-144 through diagnostic-147 did not pass the cold standard-entry to HOME regression. Matching categories, task moves and finishing a temporary duplicate do not reliably repair a HOME/standard split after creation. Separate entry components prevent this split at the manifest boundary.

## Validation

Diagnostic-143 passed the automated HOME, notification-style and alias task-reuse checks on an x86 API-30 emulator: one owner task, one process and a visibly retained map. Local release navigation testing also retained the route across the floating-button return with one style load. However, a separate normal-app PiP test failed to expand the singleTask owner reliably. Diagnostic-143 is therefore superseded for PiP/projection validation by the car-PiP-disabled follow-up. scripts/test_launcher_task_reuse.py exercises HOME, notification-style direct entry and the actual enabled launcher alias without clearing data or changing network settings. Screenshots and system dumps remain local under .logs/activity-return-audit and .logs/task-return-regression because map data can be private.

Actual Android Auto/head-unit takeover still requires a test on the target ROM. Separate old TTS timeouts and watchdog observations are not evidence that TTS caused this duplicate-task failure.

## Platform reference

[Android tasks and back stack](https://developer.android.com/guide/components/activities/tasks-and-back-stack) describes singleTask reuse and intent delivery. Task-category matching and the resumed redirect use public Activity APIs; no hidden window-management API is used.

The build workflow verifies the packaged MainActivity launch mode and disabled car PiP with scripts/check_car_launcher_manifest.py, rather than checking only source XML. That test correctly rejects diagnostic-143.


## Final local acceptance

The separate HOME entry release build passed all three task-return paths on the offline API-30 x86 emulator. Active navigation also survived normal-app floating return and default-HOME return: PID 15783, owner task 666, one renderer session, one style load, zero duplicate rejections, zero restore failures and zero fatal exceptions. Screenshots confirm the route and map after floating return. An early HOME-return screenshot captured a temporarily black renderer surface; a later capture confirms drawing resumes without a new session. This does not establish head-unit rendering performance or actual Android Auto takeover behavior. The emulator retains its application data and Vela Home role.
