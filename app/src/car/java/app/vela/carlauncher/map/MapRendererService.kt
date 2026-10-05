package app.vela.carlauncher.map

import android.app.Presentation
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.*
import android.view.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.*
import androidx.savedstate.*
import app.vela.ui.map.*
import app.vela.util.FileLogger
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Only renderer values and a Surface enter here. No Activity, VM, music or GPS repository. */
open class MapRendererService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session = ""
    private var reply: Messenger? = null
    private var display: VirtualDisplay? = null
    private var presentation: MapPresentation? = null
    private var output: Surface? = null
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDpi = 0
    private var scene by mutableStateOf<MapRenderScene?>(null)
    private val sceneQueue = kotlinx.coroutines.channels.Channel<Pair<Int, ParcelFileDescriptor>>(
        kotlinx.coroutines.channels.Channel.CONFLATED, onUndeliveredElement = { it.second.close() },
    )
    private var generation = 0
    private var graphicsCaptured = false
    private val dpad = MapDpadController()
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        val data = msg.data
        @Suppress("DEPRECATION")
        val fd = data.getParcelable<ParcelFileDescriptor>("scene")
        if (data.getInt("version") != MapProbeProtocol.VERSION) { fd?.close(); return@Handler true }
        if (msg.what == MapRendererTransport.OPEN) {
            if (session.isNotEmpty() && session != data.getString("session")) { fd?.close(); return@Handler true }
            session = data.getString("session").orEmpty()
            reply = msg.replyTo
            @Suppress("DEPRECATION")
            val surface = data.getParcelable<Surface>("surface")
            try {
                require(surface != null && surface.isValid) { "invalid surface" }
                val width = data.getInt("width").coerceIn(1, 4096)
                val height = data.getInt("height").coerceIn(1, 4096)
                val dpi = data.getInt("dpi").coerceIn(72, 640)
                stage("display")
                if (display == null) {
                    output = surface
                    display = getSystemService(DisplayManager::class.java).createVirtualDisplay(
                        "vela-isolated-map", width, height, dpi, surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                    ) ?: error("virtual display unavailable")
                    presentation = MapPresentation(this, display!!.display).also { it.show() }
                } else {
                    val resized = displayWidth != width || displayHeight != height || displayDpi != dpi
                    if (resized) {
                        // Presentation cancels itself when its display metrics change. Replace its
                        // lifecycle/Compose/MapView tree explicitly instead of retaining a dead window.
                        presentation?.dismiss()
                        presentation = null
                        display!!.resize(width, height, dpi)
                    }
                    display!!.surface = surface
                    output?.release()
                    output = surface
                    if (resized) presentation = MapPresentation(this, display!!.display).also { it.show() }
                }
                displayWidth = width; displayHeight = height; displayDpi = dpi
            } catch (error: Exception) { fd?.close(); fail("display", error); return@Handler true }
        }
        if (data.getString("session") != session || session.isEmpty()) { fd?.close(); return@Handler true }
        when (msg.what) {
            MapRendererTransport.OPEN, MapRendererTransport.SCENE -> if (fd != null) readScene(fd)
            MapRendererTransport.TOUCH -> {
                @Suppress("DEPRECATION")
                val event = data.getParcelable<MotionEvent>("touch")
                try { event?.let { presentation?.window?.decorView?.dispatchTouchEvent(it) } }
                catch (error: Exception) { fail("touch", error) }
                finally { event?.recycle() }
            }
            MapRendererTransport.KEY -> when (data.getString("key")) {
                "pan" -> dpad.panBy(data.getFloat("x"), data.getFloat("y"))
                "zoom" -> dpad.zoomBy(data.getDouble("delta"))
                "select" -> dpad.selectAtCenter()
                "long" -> dpad.longPressAtCenter()
            }
            MapRendererTransport.HEARTBEAT -> stage("heartbeat")
            MapRendererTransport.CLOSE -> shutdown()
        }
        true
    }
    private val messenger = Messenger(handler)

    override fun onCreate() {
        super.onCreate()
        app.vela.diag.ProcessDiagnostics.beginSession("renderer service")
        app.vela.ui.MemoryPressure.init(this)
        app.vela.ui.map.MapFonts.loadCached(this)
        MapRendererEvents.listener = ::stage
        scope.launch {
            for ((next, fd) in sceneQueue) {
                try {
                    val value = withContext(Dispatchers.IO) { MapRendererTransport.read(fd) }
                    if (next != generation || session.isEmpty()) continue
                    if (!graphicsCaptured) {
                        stage("graphics-probe")
                        val compatible = withContext(Dispatchers.IO) { RendererGraphicsDiagnostics.capture(applicationContext) }
                        check(compatible != false) { getString(app.vela.R.string.car_map_gles_incompatible) }
                        graphicsCaptured = true
                    }
                    applySettings(value.settings)
                    app.vela.core.config.CalibrationStore.applyRendererTuning(value.tuning, value.mapPalette)
                    app.vela.ui.MapColors.remoteDefault.value = value.mapPalette
                    app.vela.ui.PipMode.active.value = value.pipActive
                    scene = value
                } catch (error: Exception) { if (next == generation) fail("scene", error) }
                finally { runCatching { fd.close() } }
            }
        }
    }
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onUnbind(intent: Intent): Boolean {
        shutdown()
        // A native render thread may hang in EGL even after the window is released.
        // This service owns ONLY :map_renderer; explicitly finish its process, never the launcher.
        Process.killProcess(Process.myPid())
        return false
    }
    override fun onDestroy() { shutdown(); sceneQueue.cancel(); scope.cancel(); super.onDestroy() }

    private fun readScene(fd: ParcelFileDescriptor) {
        val item = ++generation to fd
        if (sceneQueue.trySend(item).isFailure) fd.close()
    }
    private var appliedSettings: JsonObject? = null
    private fun applySettings(settings: JsonObject) {
        if (appliedSettings == settings) return
        appliedSettings = settings
        val prefs = applicationContext.getSharedPreferences("vela_settings", MODE_PRIVATE)
        val inflight = prefs.getBoolean("map_init_inflight", false)
        val crashes = prefs.getInt("map_init_crashes", 0)
        val autoTexture = prefs.getLong("texture_render_auto_ms", 0)
        val edit = prefs.edit().clear().putBoolean("map_init_inflight", inflight).putInt("map_init_crashes", crashes)
        if (autoTexture > 0) edit.putLong("texture_render_auto_ms", autoTexture)
        for ((key, item) in settings) {
            val entry = item.jsonObject
            val value = entry.getValue("value")
            when (entry.getValue("type").jsonPrimitive.content) {
                "boolean" -> edit.putBoolean(key, value.jsonPrimitive.boolean)
                "int" -> edit.putInt(key, value.jsonPrimitive.int)
                "long" -> edit.putLong(key, value.jsonPrimitive.long)
                "float" -> edit.putFloat(key, value.jsonPrimitive.float)
                "string" -> edit.putString(key, value.jsonPrimitive.content)
                "set" -> edit.putStringSet(key, value.jsonArray.map { it.jsonPrimitive.content }.toSet())
            }
        }
        edit.apply()
        app.vela.ui.AppLocale.init(applicationContext)
        app.vela.ui.Buildings3d.init(applicationContext)
        app.vela.ui.GoogleFree.init(applicationContext)
        app.vela.ui.HouseNumbers.init(applicationContext)
        app.vela.ui.LayersButton.init(applicationContext)
        app.vela.ui.MapColors.init(applicationContext)
        app.vela.ui.MapScreenPosition.init(applicationContext)
        app.vela.ui.RouteTrail.init(applicationContext)
        app.vela.ui.PuckStyle.init(applicationContext)
    }
    private fun send(what: Int, data: Bundle) {
        if (session.isEmpty()) return
        data.putString("session", session)
        data.putInt("version", MapProbeProtocol.VERSION)
        data.putInt("pid", Process.myPid())
        data.putString("process", app.vela.util.ProcessIdentity.name(this))
        runCatching { reply?.send(Message.obtain(null, what).apply { this.data = data }) }
    }
    private fun stage(name: String) {
        if (name != "heartbeat") FileLogger.i("MapRenderer", "session=$session stage=$name")
        send(MapRendererTransport.STAGE, Bundle().apply { putString("stage", name) })
    }
    private fun fail(stage: String, error: Exception) {
        FileLogger.e("MapRenderer", "session=$session stage=$stage", error)
        val report = "session=$session stage=$stage\n" + app.vela.diag.ProcessDiagnostics.snapshot() + "\n" + error.stackTraceToString()
        scope.launch(Dispatchers.IO) {
            runCatching { app.vela.diag.CrashCatcher.saveDiagnosticReport(this@MapRendererService, "Handled renderer error", report) }
        }
        send(MapRendererTransport.STAGE, Bundle().apply { putString("stage", "error: $stage") })
    }
    private var lastPuckEvent = 0L
    private fun event(name: String, args: JsonArray) {
        if (name == "onPuckScreen") {
            val now = SystemClock.elapsedRealtime()
            if (now - lastPuckEvent < 50) return
            lastPuckEvent = now
        }
        send(MapRendererTransport.EVENT, Bundle().apply {
            putString("event", name); putString("args", args.toString())
        })
    }
    private fun shutdown() {
        generation++
        session = ""
        reply = null
        scene = null
        runCatching { presentation?.dismiss() }
        presentation = null
        runCatching { display?.release() }
        display = null
        output?.release(); output = null
        MapRendererEvents.listener = null
        app.vela.diag.ProcessDiagnostics.explicitClose()
        app.vela.diag.ProcessDiagnostics.checkpointAndFlush("renderer: explicit close")
    }

    private inner class MapPresentation(context: Context, display: Display) :
        Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner {
        private val life = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = life
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        private lateinit var compose: ComposeView
        override fun onCreate(bundle: Bundle?) {
            saved.performAttach(); saved.performRestore(null)
            super.onCreate(bundle)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            life.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            // Presentation/Service ContextImpl would otherwise bypass Application's prefs override.
            val renderContext = object : android.content.ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    applicationContext.getSharedPreferences(name, mode)
            }
            compose = ComposeView(renderContext)
            compose.setViewTreeLifecycleOwner(this)
            compose.setViewTreeSavedStateRegistryOwner(this)
            setContentView(compose)
            window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            compose.setContent {
                scene?.let { current ->
                    val mapped = remember(current.navMode) {
                        // Return synchronously for the map's compass handling; actual action is IPC.
                        MapRenderCallbacks.sending(current.navMode, ::event)
                    }
                    val hostConfiguration = android.content.res.Configuration(context.resources.configuration).apply {
                        if (current.hostWidthDp > 0 && current.hostHeightDp > 0) {
                            screenWidthDp = current.hostWidthDp; screenHeightDp = current.hostHeightDp
                            orientation = if (screenWidthDp > screenHeightDp) android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                else android.content.res.Configuration.ORIENTATION_PORTRAIT
                        }
                    }
                    CompositionLocalProvider(LocalDensity provides Density(current.density, current.fontScale),
                        androidx.compose.ui.platform.LocalConfiguration provides hostConfiguration) {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                            current.Draw(mapped, dpad, Modifier.fillMaxSize())
                            if (current.autoSurface) {
                                androidx.compose.foundation.layout.Column(
                                    Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(
                                        start = (current.cameraLeftInsetPx / current.density + 8).dp,
                                        bottom = (current.cameraBottomInsetPx / current.density + 8).dp),
                                ) {
                                    if (current.navMode) androidx.compose.material3.Text(
                                        "${current.autoSpeedKmh.toInt()} km/h" + (current.autoLimitKmh?.let { " · ${it.toInt()}" } ?: ""),
                                        color = androidx.compose.ui.graphics.Color.White,
                                    )
                                    androidx.compose.material3.Text("© OpenStreetMap", color = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        override fun onStart() {
            super.onStart()
            life.handleLifecycleEvent(Lifecycle.Event.ON_START)
            life.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        override fun onStop() {
            life.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            life.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            if (::compose.isInitialized) compose.disposeComposition()
            life.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            super.onStop()
        }
    }
}
