# Smart Focus playback rules

Smart Focus separates automatic playback tracking, source locking, and per-source takeover permissions. Explicit selection consumes existing playback and preparation events; timestamped Android session events from before that selection cannot take over. A stopped source must begin a fresh playback transition before it can reclaim control. Automatic idle fallback changes controls to internal without starting playback. Radio frequency metadata alone is not playback evidence; radios without a real playback session remain manually selectable with an unknown-state label. Selected icons have an outline, while confirmed playing sources have a separate play indicator.

| Trigger | Result |
|---|---|
| Startup, no music playing | Internal controls; playback only according to the existing startup autoplay setting |
| Bluetooth connection without media | No source change |
| New permitted phone/projection/application music | Follow that source and release the previous music owner |
| Explicit library track | Select internal, release previous owner, and prepare/play that track |
| Source icon | Request playback from that source without changing tracking mode |
| Keep current source | Persist automatic tracking off; explicit source changes still work |
| Automatic tracking | Unlock and reconcile current audible sessions immediately |
| Paused external source | Keep controls for 2 seconds, then show internal without issuing play |
| Buffering or explicitly selected paused source | Existing bounded 8-second grace; internal preparation retains its source |
| Call or TTS | Keep the music selection and consume interruption-time transitions |
| Frequency-only radio update | Update metadata, never assert audible playback or automatically take over |
| Radio explicitly selected without playback state | Keep its controls until another permitted source starts or the user selects another source |

Selection suppression uses source identifiers and Android session last-position update timestamps in the monotonic elapsed-realtime domain. Existing playing/preparing sources are suppressed until a fresh start. Returning to automatic mode clears suppression and follows currently playing eligible sources. Sources with unknown playback state never display a fabricated playing badge. A radio with no supported pause confirmation reports a conflict rather than toggling an unknown playback state while changing owners.

Controls are available through the visible settings icon in the player source dock, the existing source dialog and Smart Focus settings. Follow permissions do not prevent explicit selection. Long press on source icons remains an alternate route to the same dialog.

Validation includes SmartFocusPolicy regression tests for stale callbacks, suppressed former owners, fresh phone restarts, unknown and confirmed radio states, unlocking, call interruption, Bluetooth-only connections, buffering and silent fallback. APK compilation does not prove OEM radio protocol compatibility; physical-head-unit acceptance remains required. No connected device was available for this implementation.
