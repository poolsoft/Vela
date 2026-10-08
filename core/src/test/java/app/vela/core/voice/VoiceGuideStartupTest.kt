package app.vela.core.voice

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGuideStartupTest {
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
