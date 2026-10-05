package app.vela.carlauncher.map

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import app.vela.diag.ProcessDiagnostics
import app.vela.util.FileLogger
import app.vela.util.ProcessIdentity

/** P1 only: no MapLibre classes, render surface, location, audio or native initialization. */
class MapRendererProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        if (request.what == MapProbeProtocol.PING && request.data.getInt("version") == MapProbeProtocol.VERSION) {
            val session = request.data.getString("session")
            ProcessDiagnostics.checkpoint("map probe: ping session=$session")
            FileLogger.i("MapProbeService", "Ping session=$session mainPid=${request.data.getInt("mainPid")} remotePid=${Process.myPid()}")
            val reply = Message.obtain(null, MapProbeProtocol.PONG).apply {
                data = Bundle().apply {
                    putInt("version", MapProbeProtocol.VERSION)
                    putString("session", session)
                    putInt("pid", Process.myPid())
                    putString("process", ProcessIdentity.name(this@MapRendererProbeService))
                }
            }
            try { request.replyTo?.send(reply) } catch (error: RemoteException) {
                FileLogger.w("MapProbeService", "Client disconnected session=$session", error)
            }
        }
        true
    })

    override fun onCreate() {
        super.onCreate()
        ProcessDiagnostics.beginSession("map probe: service create")
        FileLogger.i("MapProbeService", "Created: empty renderer process; no map engine")
    }
    override fun onBind(intent: Intent?): IBinder = messenger.binder
    override fun onDestroy() {
        ProcessDiagnostics.explicitClose()
        FileLogger.i("MapProbeService", "Unbound: empty service destroyed")
        super.onDestroy()
    }
}
