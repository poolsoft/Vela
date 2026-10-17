# Search space and TTS cancellation

The offline state occupies only an icon in the map search bar, with its localized accessibility label retained.

System TTS initialization callbacks previously shared the speech cancellation generation and pending worker queue. Stopping speech or muting while initialization was pending could discard the only onInit result. The engine stayed bound but unready, so future prompts queued until timeout. Binding callbacks now survive speech stop; engine replacement still invalidates them via binding generation. System synthesis failures include the engine, language and platform error code.

No native engine is loaded at launcher startup. Saved mute and engine preferences remain authoritative. Missing offline language data or a crashing native engine is a separate failure and must not be described as fixed by this cancellation change.

Validation pending: the regression blocks the speech worker, queues the system initialization callback, stops speech, then verifies the result is processed. Offline emulator voice output will be checked through the explicit settings test.
