package app.vela.carlauncher.diag

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fail closed: never advance after a failed or cancelled probe. No implicit retry. */
internal suspend fun runDiagnosticSequence(ids: List<String>, execute: suspend (String) -> Boolean) {
    for (id in ids) {
        currentCoroutineContext().ensureActive()
        if (!execute(id)) return
    }
}
