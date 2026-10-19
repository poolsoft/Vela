# Music source strip, USB recovery and radio seeking

Music sources use a horizontal icon strip in the large player. Selecting a source requests playback; selecting an internal library track retains internal control while slow storage prepares. Legacy radio next/previous use single-station seek commands instead of continuous scan. Cached detached USB tracks are excluded from the active library without deleting the saved index. Failed queue entries are attempted once per failure sequence, and pending recovery is canceled by pause or a new track.

The strip contains the internal player, available music sessions, and installed dedicated Bluetooth/radio players. Icons include accessibility labels, selected-state outlines, and horizontal scrolling. Long press opens advanced source options. A source without an active internal track opens the library. Merely connecting Bluetooth is not a playback signal. Source and selection changes refresh the strip; position ticks do not rebuild it.

Radio uses the existing OsmAnd RadioManager seek_next/seek_prev protocol. Hardware firmware compatibility still requires physical-device acceptance; this is not evidence of a successful head-unit test. USB availability refresh reads only the saved index and does not initiate a scan. Saved playlist references remain available for a returning USB volume.

Validation: car release assembly and focused SmartFocusPolicy/MusicRepositoryCache tests. Physical radio behavior, build 156 reproduction and visual strip sizing require device verification. No new device logs were available for this report.
