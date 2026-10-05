package app.vela.carlauncher.map

import android.content.*
import android.os.*
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.vela.ui.map.*
import app.vela.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID

@Composable
fun IsolatedMap(scene: MapRenderScene, callbacks: MapRenderCallbacks, dpad: MapDpadController?, modifier: Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val owner = LocalLifecycleOwner.current
    val latestCallbacks by rememberUpdatedState(callbacks)
    val client = remember(context) { IsolatedMapClient(context) { name, args -> latestCallbacks.dispatch(name, args) } }
    val state by client.state.collectAsState()
    val settings = rendererSettings(context)
    SideEffect { client.update(scene.copy(settings = settings, density = density.density, fontScale = density.fontScale,
        tuning = app.vela.core.config.CalibrationStore.latest.tuning,
        mapPalette = app.vela.ui.MapColors.current(), pipActive = app.vela.ui.PipMode.active.value,
        hostWidthDp = configuration.screenWidthDp, hostHeightDp = configuration.screenHeightDp)) }
    DisposableEffect(client, owner, dpad) {
        dpad?.remote = client::key
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) client.pause()
            if (event == Lifecycle.Event.ON_START) client.resume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); dpad?.remote = null; client.close() }
    }
    var surfaceGeneration by remember { mutableIntStateOf(0) }
    val failureHost = LocalMapRendererFailureHost.current
    val retry = { client.retry(); surfaceGeneration++; Unit }
    LaunchedEffect(state, failureHost) {
        failureHost?.failure = state.takeIf { it.failed }
        failureHost?.retry = retry
    }
    DisposableEffect(failureHost, client) {
        onDispose { failureHost?.failure = null; failureHost?.retry = {} }
    }
    Box(modifier) {
        key(surfaceGeneration) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            client.surface(holder.surface, width, height, (density.density * 160).toInt())
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) { client.surface(null, 0, 0, 0) }
                    })
                    setOnTouchListener { _, event -> client.touch(event); true }
                }
            })
        }
        if (!state.ready && (!state.failed || failureHost == null)) MapRendererStatusPanel(state, retry)
    }
}

/** No automatic reconnect. One descriptor writer and one pending latest scene. */
internal class IsolatedMapClient(
    context: Context,
    private val serviceClass: Class<out android.app.Service> = MapRendererService::class.java,
    private val processSuffix: String = "map_renderer",
    private val event: (String, JsonArray) -> Unit,
) {
    data class State(val ready: Boolean = false, val failed: Boolean = false, val stage: String = "connecting")
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = Channel<MapRenderScene>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private var scene: MapRenderScene? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var dpi = 160
    private var session = ""
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null
    private var remotePid = 0
    private var active = true
    private var heartbeatAt = 0L
    private var bindAt = 0L
    private var attemptStartedAt = 0L
    private var openAt = 0L
    private var opened = false
    private var terminalStage = "connecting"
    private var hasStyle = false
    private var hasFrame = false
    private var disconnectedAt = 0L
    private val monitor = object : Runnable {
        override fun run() {
            if (connection == null) return
            val now = SystemClock.elapsedRealtime()
            when {
                service == null && now - bindAt > 5_000 -> fail("bind timeout")
                service != null && now - heartbeatAt > 15_000 -> fail("renderer not responding: $terminalStage")
                opened && !mutableState.value.ready && now - openAt > 60_000 -> fail("render timeout: $terminalStage")
                else -> { send(MapRendererTransport.HEARTBEAT); handler.postDelayed(this, 5_000) }
            }
        }
    }
    init {
        scope.launch {
            for (value in pending) {
                if (service == null || !opened || !active || mutableState.value.failed) continue
                val attempt = session
                var fd: ParcelFileDescriptor? = null
                try {
                    withContext(Dispatchers.IO) { fd = MapRendererTransport.write(context, value) }
                    if (session == attempt && active && !mutableState.value.failed) {
                        send(MapRendererTransport.SCENE, Bundle().apply { putParcelable("scene", fd) })
                    }
                } catch (error: Exception) { if (error !is CancellationException) fail("scene transfer", error) }
                finally { fd?.close() }
                delay(100) // bound CPU/disk work during rapid location/UI changes
            }
        }
    }
    fun update(value: MapRenderScene) {
        if (scene == value) return
        scene = value
        pending.trySend(value)
    }
    fun surface(value: Surface?, w: Int, h: Int, density: Int) {
        val changed = width != w || height != h || dpi != density
        surface = value; width = w; height = h; dpi = density
        if (changed && opened && value != null) {
            hasStyle = false; hasFrame = false; openAt = SystemClock.elapsedRealtime()
            mutableState.value = State(stage = "resizing")
        }
        if (value == null) { disconnect(); return }
        if (!active || mutableState.value.failed) return
        if (connection == null) connect() else if (service != null) openSurface()
    }
    private fun connect() {
        if (!active || surface?.isValid != true || width <= 0 || height <= 0) return
        val grace = 250 - (SystemClock.elapsedRealtime() - disconnectedAt)
        if (grace > 0) {
            handler.postDelayed({ if (connection == null && active && !state.value.failed) connect() }, grace)
            return
        }
        session = UUID.randomUUID().toString()
        attemptStartedAt = System.currentTimeMillis()
        val attempt = session
        bindAt = SystemClock.elapsedRealtime(); heartbeatAt = bindAt
        mutableState.value = State()
        val receiver = Messenger(Handler(Looper.getMainLooper()) { msg ->
            val data = msg.data
            if (attempt == session && connection != null &&
                MapProbeProtocol.validReply(data.getInt("version"), data.getString("session"), session,
                    Process.myPid(), data.getInt("pid"), data.getString("process"), "${context.packageName}:$processSuffix")) {
                remotePid = data.getInt("pid")
                heartbeatAt = SystemClock.elapsedRealtime()
                when (msg.what) {
                    MapRendererTransport.STAGE -> {
                        val stage = data.getString("stage").orEmpty()
                        if (stage.startsWith("error:")) fail(stage)
                        else if (stage != "heartbeat") {
                            terminalStage = stage
                            if (stage == "style-loading") { hasStyle = false; hasFrame = false }
                            if (stage == "style-ready") hasStyle = true
                            if (stage == "frame") hasFrame = true
                            mutableState.value = State(hasStyle && hasFrame, stage = stage)
                            FileLogger.i("MapRendererClient", "session=$session pid=$remotePid stage=$stage")
                        }
                    }
                    MapRendererTransport.EVENT -> runCatching {
                        event(data.getString("event").orEmpty(), MapRenderJson.json.parseToJsonElement(data.getString("args") ?: "[]").jsonArray)
                    }.onFailure { FileLogger.w("MapRendererClient", "callback rejected", it) }
                }
            }
            true
        })
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (connection !== this || attempt != session) return
                service = Messenger(binder)
                replyTo = receiver
                heartbeatAt = SystemClock.elapsedRealtime()
                FileLogger.i("MapRendererClient", "session=$session launcherPid=${Process.myPid()} binding ready")
                openSurface()
            }
            override fun onServiceDisconnected(name: ComponentName) { if (connection === this) fail("renderer process ended: $terminalStage") }
            override fun onBindingDied(name: ComponentName) { if (connection === this) fail("renderer binding died: $terminalStage") }
            override fun onNullBinding(name: ComponentName) { if (connection === this) fail("null renderer binding") }
        }
        connection = next
        try {
            if (!context.bindService(Intent(context, serviceClass), next, Context.BIND_AUTO_CREATE)) fail("bind rejected")
            else handler.postDelayed(monitor, 5_000)
        } catch (error: Exception) { fail("bind failed", error) }
    }
    private var replyTo: Messenger? = null
    private fun openSurface() {
        val target = surface ?: return
        if (!target.isValid || service == null) return
        if (!opened) { opened = true; openAt = SystemClock.elapsedRealtime() }
        send(MapRendererTransport.OPEN, Bundle().apply {
            putParcelable("surface", target); putInt("width", width); putInt("height", height); putInt("dpi", dpi)
        })
        scene?.let { pending.trySend(it) }
    }
    private fun send(what: Int, data: Bundle = Bundle()) {
        val remote = service ?: return
        data.putString("session", session); data.putInt("version", MapProbeProtocol.VERSION)
        try { remote.send(Message.obtain(null, what).apply { this.data = data; replyTo = this@IsolatedMapClient.replyTo }) }
        catch (error: Exception) { fail("remote send: $terminalStage", error) }
    }
    fun touch(event: MotionEvent) { if (state.value.ready) send(MapRendererTransport.TOUCH, Bundle().apply { putParcelable("touch", event) }) }
    fun key(name: String, x: Double, y: Double) = send(MapRendererTransport.KEY, Bundle().apply {
        putString("key", name); putFloat("x", x.toFloat()); putFloat("y", y.toFloat()); putDouble("delta", x)
    })
    private fun fail(reason: String, error: Exception? = null) {
        if (mutableState.value.failed) return
        val failedAt = System.currentTimeMillis()
        val failedPid = remotePid
        val startedAt = attemptStartedAt
        // ActivityManager timestamps death asynchronously, sometimes just AFTER Binder's
        // callback. Allow a bounded registration lag only when Binder already reported death.
        val exitUntil = failedAt + if (reason.startsWith("renderer process ended:") ||
            reason.startsWith("renderer binding died:")) 5_000L else 0L
        FileLogger.w("MapRendererClient", "session=$session failure=$reason", error)
        val details = "session=$session launcherPid=${Process.myPid()} rendererPid=$remotePid stage=$terminalStage reason=$reason\n" +
            "failedAt=$failedAt exitUntil=$exitUntil\n" + app.vela.diag.ProcessDiagnostics.snapshot() + "\n" + (error?.stackTraceToString() ?: "No Java exception received; native cause is unknown.")
        scope.launch(Dispatchers.IO) {
            runCatching {
                val journal = java.io.File(context.filesDir, "diag/process-session-$processSuffix.json")
                val remoteSnapshot = if (journal.isFile) journal.inputStream().bufferedReader().use { reader ->
                    val buffer = CharArray(16_384)
                    val count = reader.read(buffer)
                    if (count > 0) String(buffer, 0, count) else "Empty renderer journal"
                } else "Renderer journal unavailable"
                val graphics = java.io.File(context.filesDir, "diag/graphics-$processSuffix.json")
                val gpuSnapshot = if (graphics.isFile) graphics.bufferedReader().use {
                    val buffer = CharArray(24_576)
                    val count = it.read(buffer)
                    if (count > 0) String(buffer, 0, count) else "Empty graphics report"
                } else "GPU probe did not finish or start; see renderer journal"
                // Registration can lag Binder death; timeout/send failures still use failedAt
                // strictly so our later recovery kill is not mistaken for the original cause.
                delay(500)
                val exit = app.vela.diag.ProcessDiagnostics.exitInformation(context, failedPid,
                    "${context.packageName}:$processSuffix", startedAt, exitUntil)
                app.vela.diag.CrashCatcher.saveDiagnosticReport(context, "Map renderer failure",
                    details + "\n=== Renderer journal ===\n" + remoteSnapshot +
                        "\n=== Renderer graphics (check PID/recorded for freshness) ===\n" + gpuSnapshot +
                        "\n=== System exit information ===\n" + exit)
            }
        }
        mutableState.value = State(failed = true, stage = reason)
        disconnect()
    }
    private fun disconnect() {
        handler.removeCallbacks(monitor)
        val old = connection
        val pid = remotePid
        connection = null; service = null; replyTo = null; session = ""; remotePid = 0; opened = false; hasStyle = false; hasFrame = false
        if (old != null) disconnectedAt = SystemClock.elapsedRealtime()
        old?.let { runCatching { context.unbindService(it) } }
        // Bound-service death/unbind may be queued behind a stuck EGL call. Check UID/name/PID
        // before ending ONLY this app's renderer, never the Home/music process.
        if (pid > 0 && pid != Process.myPid()) runCatching {
            val record = context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == pid && it.uid == Process.myUid() && it.processName == "${context.packageName}:$processSuffix" }
            if (record != null) Process.killProcess(pid)
        }
    }
    fun retry() { disconnect(); mutableState.value = State(); surface = null }
    fun pause() { active = false; disconnect() }
    fun resume() { active = true; if (!state.value.failed && connection == null) connect() }
    fun close() { active = false; disconnect(); pending.close(); scope.cancel() }
}
