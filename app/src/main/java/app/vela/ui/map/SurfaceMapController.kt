package app.vela.ui.map

import android.view.Surface

/** Android Auto's host keeps the template, navigation and controls in its original process. */
interface SurfaceMapController {
    fun surface(surface: Surface?, width: Int, height: Int, dpi: Int)
    fun update(scene: MapRenderScene)
    fun key(name: String, x: Double, y: Double = 0.0)
    fun close()
}
