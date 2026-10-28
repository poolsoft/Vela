# Music player dock layout

The large music player uses its existing 60 dp vertical dock for a scrolling source-icon list, with close/library controls above and source settings below. There is no source toolbar above the cover or playlist. The equalizer is the sixth and final adaptive playback control, after repeat, and scanning sits after the playlist tabs before the overflow menu. Source selection, playing badges, tracking permissions and equalizer application dispatch keep their existing behavior.

The dock stays vertical in portrait and landscape, preserving the entire content height. Native vertical scrolling exposes sources beyond the available dock height. Existing scroll position is retained when playback badges or selection change. The active source outline and truthful playing marker remain distinct. Source settings remain visible outside the scrolling list and are also available through long press.

The scan action keeps its existing listener and progress animation. It appears in the horizontal playlist tab scroller; it does not consume the always-visible dock. Equalizer selection/launch and selected-application icon binding use the original button ID and listener after moving into the playback chain. All six playback controls resize together using the available row width.

Validation: XML parsing and car release assembly passed. APK 2267 installed over the existing x86 emulator data. Landscape player and playlist screenshots confirm source icons in the dock, equalizer after repeat and scanning after the tabs; source settings remain pinned at the dock bottom. No new playback-policy changes are included in this layout revision.

The player dock uses the existing playlist vector with fit-center scaling and 6 dp padding for a full-size glyph. Source settings reuse the launcher cyan gear asset without a monochrome tint; the equalizer also uses fit-center scaling so its built-in glyph or selected application icon fills the adaptive playback button. All controls retain their existing touch targets and actions.
