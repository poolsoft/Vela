# 11. Car launcher clock

The small player menu and Car settings hub open Clock customization. The real small player stays visible on the left while Appearance, Colors and Time format controls scroll in the large right panel. Portrait stacks the real player above controls. Opening the editor reparents the existing player host and keeps the map composition attached underneath; closing restores the saved panel layout.

Changes apply and save immediately. The real clock receives saved appearance changes immediately through its attached preference listener. Size ranges from 60 to 150 percent, digit spacing from -26 to 12 percent of an em, colon spacing from 0 to 20 percent, outline thickness from 0.5 to 3 dp and fill opacity from 0 to 100 percent. The actual clock fits within the panel and retains its menu-button clearance. Outlines stay visible when fill is transparent.

Three typefaces, separate hour/minute colors, a customizable outline, pastel and background palettes, embossed rendering, system/12-hour/24-hour formats and optional seconds update the visible real clock. Reset affects clock appearance only. Launcher backups carry small_music_panel preferences through an explicit typed allowlist; older backups remain readable.

Exact keys, defaults, validation and panel sizing are defined in SPEC under Small player clock appearance.
