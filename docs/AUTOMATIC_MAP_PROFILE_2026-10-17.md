# Automatic map profile

Settings > Map > Automatic selects the effective budget without rewriting custom FPS, tilt, building or topography preferences. Existing profile selections remain unchanged until Automatic is selected.

- Low-RAM, heap class below 128 MiB or GLES2: Light, 30 FPS, flat map, no 3D buildings or terrain shading.
- Other devices: Standard after the existing Light startup phase.
- Rendering callbacks measure actual frame duration. Three consecutive windows of at least 10 seconds, at least 20 frames and at least 50% frames taking 80 ms trigger a session-only Light budget. Sparse or fast windows break the streak. No session-time upward switching.
- Settings show hardware heap/GLES and the effective reason. A validated renderer stage updates the launcher after runtime downgrade. A new connection clears the old session report.
- Custom settings remain independently selectable. Traffic and transit choices remain unchanged. Navigation and satellite restrictions continue to apply.

Validation: focused profile and sustained-frame tests; release compilation. Physical head-unit performance and thermal behavior require device testing. The previously observed Chrome/Home activity replacement issue is separate and remains open.
