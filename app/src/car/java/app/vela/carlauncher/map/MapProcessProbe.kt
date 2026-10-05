package app.vela.carlauncher.map

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import app.vela.util.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Bound only by an explicit button. Disconnect and timeout never automatically reconnect. */
class MapProcessProbe(context: Context) {
    enum class Status { IDLE, CONNECTING, READY, FAILED }
    data class State(val status: Status = Status.IDLE, val remotePid: Int = 0)
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var connection: ServiceConnection? = null
    private var session = ""
    private val timeout = Runnable { if (mutableState.value.status == Status.CONNECTING) fail("timeout") }

    fun start() {
        disconnect()
        session = UUID.randomUUID().toString()
        val attempt = session
        mutableState.value = State(Status.CONNECTING)
        val receiver = Messenger(Handler(Looper.getMainLooper()) { reply ->
            if (attempt == session && connection != null && mutableState.value.status == Status.CONNECTING &&
                reply.what == MapProbeProtocol.PONG) {
                val data = reply.data
                if (MapProbeProtocol.validReply(data.getInt("version"), data.getString("session"), attempt,
                        Process.myPid(), data.getInt("pid"), data.getString("process"), "${context.packageName}:map_renderer")) {
                    handler.removeCallbacks(timeout)
                    mutableState.value = State(Status.READY, data.getInt("pid"))
                    FileLogger.i("MapProbeClient", "Connected session=$attempt mainPid=${Process.myPid()} remotePid=${data.getInt("pid")} process=${data.getString("process")}")
                } else fail("invalid process handshake")
            }
            true
        })
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this || attempt != session) return
                try {
                    Messenger(binder).send(Message.obtain(null, MapProbeProtocol.PING).apply {
                        replyTo = receiver
                        data = Bundle().apply {
                            putInt("version", MapProbeProtocol.VERSION)
                            putString("session", attempt)
                            putInt("mainPid", Process.myPid())
                        }
                    })
                } catch (error: Exception) { fail("ping failed", error) }
            }
            override fun onServiceDisconnected(name: ComponentName) { if (connection === this) fail("service disconnected") }
            override fun onBindingDied(name: ComponentName) { if (connection === this) fail("binding died") }
            override fun onNullBinding(name: ComponentName) { if (connection === this) fail("null binding") }
        }
        connection = next
        FileLogger.i("MapProbeClient", "Bind requested session=$attempt mainPid=${Process.myPid()}")
        try {
            if (context.bindService(Intent(context, MapRendererProbeService::class.java), next, Context.BIND_AUTO_CREATE)) {
                handler.postDelayed(timeout, 5_000)
            } else fail("bind rejected")
        } catch (error: Exception) { fail("bind failed", error) }
    }

    private fun fail(reason: String, error: Exception? = null) {
        FileLogger.w("MapProbeClient", "Failed session=$session: $reason", error)
        disconnect()
        mutableState.value = State(Status.FAILED)
    }

    private fun disconnect() {
        handler.removeCallbacks(timeout)
        val previous = connection
        connection = null
        previous?.let { runCatching { context.unbindService(it) } }
    }

    fun close() {
        if (connection != null) FileLogger.i("MapProbeClient", "Unbind requested session=$session mainPid=${Process.myPid()}")
        disconnect()
        mutableState.value = State()
    }
}
