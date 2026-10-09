package app.vela.core.voice

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGuideStartupTest {
    @Test fun googleSelectionDoesNotStartSpeechOrLoadNeuralVoice() {
        val synth = RecordingSynth()
        val guide = VoiceGuide(ContextWrapper(null)).apply { neural = synth }

        guide.useSystemEngineLazily("com.google.android.tts")
        guide.useSystemEngineLazily("com.google.android.tts")

        assertEquals(0, synth.loads)
        assertEquals(0, synth.utterances)
        assertEquals("idle", guide.speechStatus)
        assertTrue(guide.working == null)
    }

    @Test fun unavailableSystemServiceReportsFailureWithoutFallingBackToNeuralVoice() {
        val synth = RecordingSynth()
        val guide = VoiceGuide(ContextWrapper(null)).apply { neural = synth }
        var failures = 0
        guide.onFailure = { failures++ }
        guide.useSystemEngineLazily("com.google.android.tts")

        // The JVM context has no PackageManager/TTS service. A catchable platform failure must
        // become a failed voice operation, never an exception escaping into the launcher.
        guide.init("vela.piper")
        guide.speak("test", ignoreMute = true)

        assertEquals(1, failures)
        assertTrue(guide.working == false)
        assertTrue(guide.speechStatus.startsWith("error:"))
        assertEquals(0, synth.loads)
        assertEquals(0, synth.utterances)
    }

    @Test fun savedNeuralEngineDoesNotLoadModelBeforeItIsNeeded() {
        val synth = RecordingSynth()
        val guide = VoiceGuide(ContextWrapper(null)).apply { neural = synth }

        guide.init("vela.piper")
        guide.init("vela.piper") // Activity recreation must also stay lazy.

        assertEquals(0, synth.loads)
        assertEquals(0, synth.utterances)
        assertTrue(guide.working == true)
    }

    private class RecordingSynth : NeuralSynth {
        override val ready = false
        var loads = 0
        var utterances = 0
        override fun warmUp() { loads++ }
        override fun speak(text: String, interrupt: Boolean, onDone: () -> Unit) {
            utterances++
            onDone()
        }
        override fun stop() = Unit
        override fun release() = Unit
    }
}
