# Equalizer application selection

Settings > Music and sound > Equalizer application opens a shared application picker. Launcher applications and Android audio-effect panel handlers are listed once per package, with icons and the current selection marked. Selecting a row persists the existing backup-compatible car_launcher_equalizer_app key without launching the application. Clear selection removes the preference.

All equalizer entry points use CarHardwareManager. Player buttons launch the selected package directly; an unset selection opens the picker. A failed launch clears the unavailable selection and opens the picker instead of silently opening another DSP application. The XML and Compose large players show the selected package icon without a monochrome tint.

Validation: car release assembly passed in 6 minutes 17 seconds. Diagnostic x86 version 2255 was installed on emulator-5554 without clearing Vela data. The packaged launcher manifest check passed. UI tests verified the unset picker, passive selection, selected application icon, direct launch on the next press, settings opening the picker despite an existing selection, and clearing the selection. Calendar was used only to verify package dispatch, then the test selection was cleared. Actual head-unit DSP operation and its audio-session requirements remain device acceptance items.
