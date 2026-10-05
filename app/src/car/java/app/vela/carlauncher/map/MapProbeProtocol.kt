package app.vela.carlauncher.map

internal object MapProbeProtocol {
    const val VERSION = 1
    const val PING = 1
    const val PONG = 2

    fun validReply(version: Int, session: String?, expectedSession: String, mainPid: Int,
                   remotePid: Int, process: String?, expectedProcess: String): Boolean =
        version == VERSION && session == expectedSession && remotePid > 0 &&
            remotePid != mainPid && process == expectedProcess
}
