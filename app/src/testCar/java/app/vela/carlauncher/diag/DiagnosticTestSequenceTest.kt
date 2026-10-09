package app.vela.carlauncher.diag

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DiagnosticTestSequenceTest {
    @Test fun `first failure prevents subsequent native tests`() = runBlocking {
        val called = mutableListOf<String>()
        runDiagnosticSequence(listOf("ipc", "prepare", "play")) {
            called += it
            it != "prepare"
        }
        assertEquals(listOf("ipc", "prepare"), called)
    }

    @Test fun `successful probes keep requested order`() = runBlocking {
        val called = mutableListOf<String>()
        runDiagnosticSequence(listOf("service", "empty", "style")) { called += it; true }
        assertEquals(listOf("service", "empty", "style"), called)
    }

    @Test fun `cancellation is propagated without starting next test`() = runBlocking {
        val called = mutableListOf<String>()
        try {
            runDiagnosticSequence(listOf("prepare", "play")) { called += it; throw CancellationException("stop") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf("prepare"), called)
        }
    }
}
