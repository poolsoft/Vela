package app.vela.carlauncher.map

import android.content.Context
import android.view.Surface
import app.vela.ui.map.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

internal class AutoSurfaceMap(private val context: Context) : SurfaceMapController {
    private val client = IsolatedMapClient(context, MapAutoRendererService::class.java, "map_renderer_auto") { _, _ -> }
    private var output: Surface? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    init {
        scope.launch {
            client.state.collect { state ->
                if (state.failed) output?.takeIf { it.isValid }?.let { target ->
                    runCatching {
                        val canvas = target.lockCanvas(null)
                        try {
                            canvas.drawColor(android.graphics.Color.rgb(24, 28, 35))
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE; textSize = 20f; textAlign = android.graphics.Paint.Align.CENTER
                            }
                            canvas.drawText(context.getString(app.vela.R.string.car_map_renderer_failed), canvas.width / 2f, canvas.height / 2f, paint)
                        } finally { target.unlockCanvasAndPost(canvas) }
                    }
                }
            }
        }
    }
    override fun surface(surface: Surface?, width: Int, height: Int, dpi: Int) {
        output = surface
        if (surface != null && client.state.value.failed) client.retry()
        client.surface(surface, width, height, dpi)
    }
    override fun update(scene: MapRenderScene) = client.update(scene.copy(settings = rendererSettings(context),
        tuning = app.vela.core.config.CalibrationStore.latest.tuning, mapPalette = app.vela.ui.MapColors.current()))
    override fun key(name: String, x: Double, y: Double) = client.key(name, x, y)
    override fun close() { output = null; client.close(); scope.cancel() }
}
