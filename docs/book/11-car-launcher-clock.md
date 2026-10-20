# 11. Car launcher clock

The small player menu and Car settings hub open Clock customization. The preview stays visible while Appearance, Colors and Time format controls scroll. Widths of 600 dp and above use two columns; portrait layouts stack preview and controls.

Changes apply and save immediately. The real panel and preview use one renderer and preference model. Size ranges from 60 to 150 percent, digit spacing from -26 to 12 percent of an em, colon spacing from 0 to 20 percent, outline thickness from 0.5 to 3 dp and fill opacity from 0 to 100 percent. The actual clock fits within the panel and retains its menu-button clearance. Outlines stay visible when fill is transparent.

Three typefaces, separate hour/minute colors, a customizable outline, pastel and background palettes, embossed rendering, system/12-hour/24-hour formats and optional seconds share the same preview. Reset affects clock appearance only. Launcher backups carry small_music_panel preferences through an explicit typed allowlist; older backups remain readable.

Exact keys, defaults, validation and panel sizing are defined in SPEC under Small player clock appearance.
