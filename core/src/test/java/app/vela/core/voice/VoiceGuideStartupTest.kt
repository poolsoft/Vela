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

    @Test fun unavailableSystemServiceSetsFailureStatusWithoutFallingBackToNeuralVoice() {
        val synth = RecordingSynth()
        val guide = VoiceGuide(ContextWrapper(null)).apply { neural = synth }
        guide.useSystemEngineLazily("com.google.android.tts")

        // The JVM context has no PackageManager/TTS service. A catchable platform failure must
        // become a failed voice operation, never an exception escaping into the launcher.
        guide.init("vela.piper")
        guide.speak("test", ignoreMute = true)

        awaitWorker { guide.speechStatus.startsWith("error:") }
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
        awaitWorker { guide.working == true }
        assertTrue(guide.working == true)
    }

    // Plain JVM Android stubs do not execute Handler callbacks. Observe the worker's volatile
    // health state; UI callback dispatch needs an Android runtime.
    private fun awaitWorker(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("Speech worker did not reach the expected state", condition())
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
