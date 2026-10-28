# On-demand Map app

- Internal Map appears in the app drawer and existing dock shortcut picker.
- Drawer long press > add to dock uses the existing shortcut menu.
- Opening Map in mapless mode starts the map for the current session and returns to split view.
- Saved mapless startup, custom map settings and music state are unchanged.
- Forced settings refresh does not undo manual session activation; a new app process honors saved startup.
- Repeated taps do not request a reload. Explicit opening errors remain retryable rather than persisting another startup choice.

Validation: focused session-preference/idempotence unit test and release compilation. Cold GLES2 hardware validation remains required. The previously reported Chrome/Home activity replacement issue is a separate open defect.
