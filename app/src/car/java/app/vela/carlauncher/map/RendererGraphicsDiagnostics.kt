package app.vela.carlauncher.map

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.util.AtomicFile
import app.vela.diag.ProcessDiagnostics
import app.vela.util.ProcessIdentity
import org.json.JSONObject
import java.io.File

/** Renderer-only ES2 pbuffer probe; never loads the map library or runs in the launcher. */
internal object RendererGraphicsDiagnostics {
    fun capture(context: Context): Boolean? {
        var mapConfigAvailable: Boolean? = null
        val file = AtomicFile(File(context.filesDir, "diag/graphics${ProcessIdentity.fileSuffix(context)}.json"))
        fun persist(report: JSONObject) {
            file.baseFile.parentFile?.mkdirs()
            val stream = file.startWrite()
            try { stream.write(report.toString(2).toByteArray()); file.finishWrite(stream) }
            catch (error: Exception) { file.failWrite(stream); throw error }
        }
        val report = JSONObject().apply {
            put("pid", android.os.Process.myPid())
            put("recorded", System.currentTimeMillis())
            put("advertisedGles", context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.glEsVersion)
            put("scope", "Separate ES2 test context, not the map engine context")
            put("stage", "egl-display")
        }
        persist(report)
        ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe egl-display")
        var display = EGL14.EGL_NO_DISPLAY
        var eglContext = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay error=0x${EGL14.eglGetError().toString(16)}" }
            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) { "eglInitialize error=0x${EGL14.eglGetError().toString(16)}" }
            report.put("eglVersion", "${versions[0]}.${versions[1]}")
            report.put("eglVendor", EGL14.eglQueryString(display, EGL14.EGL_VENDOR))
            report.put("eglExtensions", EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS)?.take(8192))
            val configs = arrayOfNulls<android.opengl.EGLConfig>(256)
            val count = IntArray(1)
            check(EGL14.eglGetConfigs(display, configs, 0, configs.size, count, 0)) { "eglGetConfigs failed" }
            var es2 = 0; var es3 = 0
            val value = IntArray(1)
            configs.take(count[0].coerceAtMost(configs.size)).filterNotNull().forEach { config ->
                if (EGL14.eglGetConfigAttrib(display, config, EGL14.EGL_RENDERABLE_TYPE, value, 0)) {
                    if (value[0] and EGL14.EGL_OPENGL_ES2_BIT != 0) es2++
                    if (value[0] and 0x40 != 0) es3++ // EGL_OPENGL_ES3_BIT_KHR
                }
            }
            report.put("enumeratedConfigs", count[0].coerceAtMost(configs.size))
            report.put("es2Configs", es2).put("es3Configs", es3)
            // MapLibre 11.8.8 EGLConfigChooser asks for ES3-capable WINDOW configs,
            // even though its TextureView requests client version 2. Do not infer support
            // from Android's advertised GLES string or our unrelated ES2 pbuffer alone.
            val mapAttributes = intArrayOf(EGL14.EGL_CONFIG_CAVEAT, EGL14.EGL_NONE,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT, EGL14.EGL_BUFFER_SIZE, 16,
                EGL14.EGL_RED_SIZE, 5, EGL14.EGL_GREEN_SIZE, 6, EGL14.EGL_BLUE_SIZE, 5,
                EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_NONE)
            if (EGL14.eglChooseConfig(display, mapAttributes, 0, null, 0, 0, count, 0)) {
                report.put("mapLibreEs3WindowCandidates", count[0])
                mapConfigAvailable = count[0] > 0
            } else report.put("mapConfigQueryError", EGL14.eglGetError())
            val chosen = arrayOfNulls<android.opengl.EGLConfig>(1)
            val attributes = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE)
            check(EGL14.eglChooseConfig(display, attributes, 0, chosen, 0, 1, count, 0) && count[0] > 0) { "No ES2 pbuffer config" }
            report.put("stage", "egl-context"); persist(report)
            ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe egl-context")
            eglContext = EGL14.eglCreateContext(display, chosen[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext error=0x${EGL14.eglGetError().toString(16)}" }
            report.put("stage", "egl-pbuffer"); persist(report)
            ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe egl-pbuffer")
            surface = EGL14.eglCreatePbufferSurface(display, chosen[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface error=0x${EGL14.eglGetError().toString(16)}" }
            report.put("stage", "egl-make-current"); persist(report)
            ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe egl-make-current")
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "eglMakeCurrent error=0x${EGL14.eglGetError().toString(16)}" }
            report.put("glVersion", GLES20.glGetString(GLES20.GL_VERSION))
            report.put("glVendor", GLES20.glGetString(GLES20.GL_VENDOR))
            report.put("glRenderer", GLES20.glGetString(GLES20.GL_RENDERER))
            report.put("glExtensions", GLES20.glGetString(GLES20.GL_EXTENSIONS)?.take(8192))
            report.put("stage", "es2-context-ready")
        } catch (error: Exception) {
            report.put("stage", "probe-failed").put("error", error.stackTraceToString().take(8192))
        } finally {
            // Persist BEFORE cleanup too: a broken vendor driver may die inside native cleanup.
            persist(report)
            ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe cleanup")
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
                // The default display may also belong to Presentation/HWUI. Destroy only our
                // context/surface; terminating the shared display could disrupt the real map.
            }
            EGL14.eglReleaseThread()
            ProcessDiagnostics.checkpointAndFlush("renderer: graphics probe complete")
        }
        return mapConfigAvailable
    }
}
