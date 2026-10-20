# Live small-player clock customization, 2026-10-20

## Delivered behavior

The small player menu opens Clock customization directly. The same page is available in the Car settings hub. The old RGB dialog and nested clock color menu are replaced by one full settings page with Appearance, Colors and Time format tabs. The preview stays visible beside scrolling controls in landscape, or above them in narrow layouts.

Both the panel and preview use SmallClockAppearance and InterlockingClockView. Preferences apply immediately and save automatically. Controls include responsive size, digit and colon spacing, fill opacity, outline thickness/color, palette/custom hour and minute colors, dual colors, embossed rendering, three typefaces, system/12-hour/24-hour format and optional seconds. Geometry validation prevents invalid restored values reaching the Canvas. Reset changes only clock settings.

The typed launcher backup allowlist now includes small_music_panel in ZIP and legacy JSON inspection/export/restore. Existing palette and custom color keys remain compatible. No new Activity, service, dependency or renderer lifecycle is introduced.

## Validation

- 12 selected JVM tests passed: four clock defaults/bounds/backup-schema checks, six PMTiles source checks and two dock metadata checks.
- Final local diagnostic x86 APK versionCode 2272 built successfully in 2m 40s. A private init script disables minification for local UI checks only; the committed CI release configuration retains R8.
- The APK was installed with adb install -r; application data was preserved.
- Packaged manifest check passed: singleTask launcher and PiP disabled.
- The settings hub entry and direct small-player menu entry both opened the full clock page.
- Dragging size from 100 to approximately 128 percent and digit spacing from -16 to approximately -3 percent changed the live preview and persisted the values.
- White and pastel palettes, 24-hour selection, seconds on/off and rounded typeface were exercised. The real small player reflected the saved appearance after leaving the editor. Values survived the final APK update.
- Reopening through the clock menu kept PID 16002 and one MainActivity owner record; the editor uses the existing launcher window.
- The final editor screenshot shows hidden system navigation. No AndroidRuntime fatal exception was recorded during these checks.
- Landscape layout was visually verified. The narrow/portrait branch is implemented but was not separately exercised on a physical device.

## Changed paths

- ui/ClockCustomizationScreen.kt and ui/SmallClockAppearance.kt: full editor and shared validated appearance.
- ui/CarUnifiedPanelHost.kt and widgets/InterlockingClockView.kt: immediate panel updates and configurable drawing.
- ui/CarLauncherSettingsView.kt and settings/CarLauncherSettings.kt: settings/menu navigation.
- tools/LauncherBackup.kt: portable clock preference schema.
- Base/Turkish resources, clock unit tests and subsystem documentation.

The obsolete ClockColorDialog and its old native-submenu emulator driver are removed because those clock controls no longer exist. Common native popup safety fixes from 53bad73cc remain in place for other menus.

## Device follow-up

The physical API-27 head unit still needs verification for editing responsiveness and narrow panels. Existing remaining map and music cache performance work uses the stage timings documented in LOG_FIXES_2026-10-20.md; this clock change does not claim to resolve those remaining head-unit bottlenecks.
