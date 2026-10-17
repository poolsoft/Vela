# Smart Focus V2 implementation

The existing large-player selector provides Select, Select and play, Keep this source, per-source Smart Focus inclusion and Return to automatic tracking. Select does not send Play. Normal playback commands no longer create a permanent manual lock. Automatic handoff pauses only the previous music owner when supported, not all active system sessions.

One policy consumes actual playback edges across native adapters and MediaSessions. Connection and metadata alone cannot take over. Already-playing boot snapshots preserve the current playing source. Pauses wait 2 seconds; buffering waits up to 8 seconds. A confirmed disconnect can fall back immediately. Bluetooth/disconnected-source silent fallback displays the internal player without starting it. A normal paused application retains its resume controls when no other eligible source is playing. Explicit paused selection remains until a new eligible playback start. Source decisions do not launch activities.

Dedicated Bluetooth and OEM MediaSession endpoints are paired when identifiable. A2DP sink playback is music; HFP connection is not. Known call audio modes and optional phone-state events suspend source changes and disable playback controls. READ_PHONE_STATE is optional, available only on the permissions page; no call numbers or history are collected. Phone-state permission does not guarantee a proprietary head unit reports remote HFP calls correctly. TTS has a separate interruption callback that does not replace the voice-recognition echo guard or initialize a player at startup.

ZLink, SpeedPlay, AutoKit and Android Auto projection sessions are followed when they expose real playing state through MediaSession. Foreground app, USB connection, projection connection or a phone-call UI is not playback evidence. A proprietary bridge without public media state cannot be claimed as supported merely because its package is installed; actual head-unit protocol acceptance remains open.

The large-player source button shows the active source icon. Both small-panel implementations show external icons only and add no new selector. Bluetooth uses a Bluetooth icon, never an inferred phone app icon. Application icons are cached. Model identity is independent of artwork and track metadata. Controls follow published session capabilities; external shuffle/repeat do not manipulate the internal queue.

Validation pending: focused policy tests, packaged release check, offline emulator MediaSession handoff, selection and icon inspection. Real BT/HFP/MCU radio and opaque projection bridges require the head unit.

The playback owner is retained separately from the displayed selection. Passive selection sends no play/pause; a later Play releases the retained owner before starting the target, including a pending internal preparation. Ownership is updated before commands to prevent duplicate releases from reentrant callbacks.
