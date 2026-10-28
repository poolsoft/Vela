# Search space and TTS cancellation

The offline state occupies only an icon in the map search bar, with its localized accessibility label retained.

System TTS initialization callbacks previously shared the speech cancellation generation and pending worker queue. Stopping speech or muting while initialization was pending could discard the only onInit result. The engine stayed bound but unready, so future prompts queued until timeout. Binding callbacks now survive speech stop; engine replacement still invalidates them via binding generation. System synthesis failures include the engine, language and platform error code.

No native engine is loaded at launcher startup. Saved mute and engine preferences remain authoritative. Missing offline language data or a crashing native engine is a separate failure and must not be described as fixed by this cancellation change.

Validation: all four VoiceGuideStartupTest cases passed. The new release APK was installed on emulator-5554 without clearing data. With networking still disabled, the explicit Google TTS test was stopped and retried successfully, followed by another completed test in the same process (PID 18491). The engine reported ready and both requests reported completed. This verifies service completion, not audible speaker output on the head unit. Screenshot inspection confirms the search bar displays only the accessible offline icon. The prior head-unit native-process failure is separate and has not been reproduced or declared fixed here.
