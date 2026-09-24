package app.vela.carlauncher.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import app.vela.carlauncher.settings.CarLauncherSettings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * OsmAnd ve CoMaps_Auto_V2 Uyumlu 8 Modlu Gercek Muzik Gorsellestirici (Visualizer).
 * Kesinlikle simule/dummy/sahte veri kullanmaz; yalnizca gercek calan muzigin
 * Audio Session FFT ses dalgasi geldiginde cizim yapar.
 * - TYPE_CLASSIC: Klasik Dikey Bar Spektrumu
 * - TYPE_GLOW_PEAK: Isiltili Tepe Noktali Barlar
 * - TYPE_NEON_MODERN: Yuvarlatilmis Neon Barlar ve Faded Ayna Yansimasi
 * - TYPE_WAVE: Akici Bezier Ses Dalgasi
 * - TYPE_RADIAL: Dairesel Patlama Spektrumu
 * - TYPE_CENTER_MIRRORED: Ortadan Iki Yana Simetrik Barlar
 * - TYPE_PARTICLE: Yercekimi Ivmesiyle Dusen Parcaciklar
 * - TYPE_RINGS: Ic Ice Ritmik Daireler
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class MusicVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val TYPE_CLASSIC = 0
        const val TYPE_GLOW_PEAK = 1
        const val TYPE_NEON_MODERN = 2
        const val TYPE_WAVE = 3
        const val TYPE_RADIAL = 4
        const val TYPE_CENTER_MIRRORED = 5
        const val TYPE_PARTICLE = 6
        const val TYPE_RINGS = 7
    }

    private var visualizerType = TYPE_NEON_MODERN
    private var dominantColor = 0
    private var isSmallPanel = true

    private var isPlaying = false
    private var capture: android.media.audiofx.Visualizer? = null
    private var capturedSession = 0
    private var failedSession = 0
    private var mBytes: ByteArray? = null

    private val mRect = RectF()
    private val mForePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeWidth = 8f
    }
    private val mPeakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val mReflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val mReflectionDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f
        color = Color.argb(72, 255, 255, 255)
    }

    private var mSpectrumNum = 48
    private var mFirst = true
    private var mirrorReflectionRatio = 0.18f
    private var reflectionQualityScale = 1f
    private var frameDelayMs = 33L
    private var lastFrameInvalidate = 0L

    private var mPeaks: FloatArray? = null
    private var mPeakTimes: LongArray? = null
    private val mWavePath = Path()

    init {
        val tip = CarLauncherSettings.getInstance(context).gorsellestiriciTipi.value
        visualizerType = tip.coerceIn(0, 7)
    }

    fun setFrameRate(fps: Int) {
        frameDelayMs = 1000L / fps.coerceIn(15, 60)
    }

    fun setVisualizerContext(isSmall: Boolean) {
        this.isSmallPanel = isSmall
        visualizerType = if (isSmall) CarLauncherSettings.gorsellestiriciTipi.value else CarLauncherSettings.largeVisualizer.value
        setFrameRate(CarLauncherSettings.visualizerFps.value)
        this.mSpectrumNum = if (isSmall) 28 else 48
        this.mPeaks = null
        this.mPeakTimes = null
        this.mFirst = true
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        syncCapture()
        if (!playing) {
            clear()
        }
    }

    private fun releaseCapture() {
        capture?.let { runCatching { it.enabled = false }; runCatching { it.release() } }
        capture = null
        capturedSession = 0
        clear()
    }

    private fun syncCapture() {
        if (!isPlaying || !isAttachedToWindow || !isShown || windowVisibility != VISIBLE) {
            releaseCapture()
            return
        }
        // Android does not expose another app's session ID; never fabricate external FFT.
        val session = app.vela.carlauncher.media.MusicManager.getInstance(context).audioSessionId()
        if (session <= 0) { releaseCapture(); return }
        if (capturedSession == session && capture != null) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        releaseCapture()
        if (failedSession == session) return
        try {
            val visualizer = android.media.audiofx.Visualizer(session)
            capture = visualizer
            visualizer.captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
            visualizer.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer?, data: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(v: android.media.audiofx.Visualizer?, data: ByteArray?, rate: Int) {
                    if (v === capture && isPlaying) updateVisualizer(data)
                }
            }, android.media.audiofx.Visualizer.getMaxCaptureRate().coerceAtMost(30000), false, true)
            visualizer.enabled = true
            capturedSession = session
        } catch (error: Exception) {
            failedSession = session
            releaseCapture()
            android.util.Log.w("MusicVisualizer", "Audio capture unavailable", error)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); failedSession = 0; syncCapture() }
    override fun onDetachedFromWindow() { releaseCapture(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncCapture()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncCapture()
    }

    fun isPlaying(): Boolean = isPlaying

    fun setVisualizerType(type: Int) {
        this.visualizerType = type.coerceIn(0, 7)
        this.mFirst = true
        invalidate()
    }

    fun getVisualizerTypeName(type: Int): String {
        return when (type) {
            TYPE_CLASSIC -> "Klasik"
            TYPE_GLOW_PEAK -> "Glow Peak"
            TYPE_NEON_MODERN -> "Neon Modern"
            TYPE_WAVE -> "Akıcı Dalga"
            TYPE_RADIAL -> "Dairesel Patlama"
            TYPE_CENTER_MIRRORED -> "Ortadan Simetrik"
            TYPE_PARTICLE -> "Noktacık Particle"
            TYPE_RINGS -> "İç İçe Halkalar"
            else -> "Neon Modern"
        }
    }

    fun cycleVisualizerType() {
        visualizerType = (visualizerType + 1) % 8
        if (isSmallPanel) CarLauncherSettings.setGorsellestiriciTipi(visualizerType)
        else CarLauncherSettings.setLargeVisualizer(visualizerType)
        Toast.makeText(context, "Görselleştirici: ${getVisualizerTypeName(visualizerType)}", Toast.LENGTH_SHORT).show()
        mFirst = true
        invalidate()
    }

    fun setDominantColor(color: Int) {
        if (color != 0 && CarLauncherSettings.isAmbianceVisualizerEnabled()) {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            if (hsv[1] > 0.05f) hsv[1] = max(hsv[1], 0.85f)
            hsv[2] = max(hsv[2], 0.90f)
            this.dominantColor = Color.HSVToColor(hsv)
        } else {
            this.dominantColor = 0
        }
        this.mFirst = true
        postInvalidate()
    }

    /**
     * Sadece gercek muzigin ses dalgasi (FFT) yakalandiginda cagirilir.
     * Kesinlikle dummy veya simule edilmis sahte veri kullanilmaz.
     */
    fun updateVisualizer(fft: ByteArray?) {
        if (fft == null || fft.size < 2) return
        val count = max(1, min(mSpectrumNum, fft.size / 2 + 1))
        val model = ByteArray(count)
        model[0] = abs(fft[0].toInt()).coerceAtMost(127).toByte()
        var i = 2
        var j = 1
        while (j < model.size && i + 1 < fft.size) {
            model[j] = hypot(fft[i].toDouble(), fft[i + 1].toDouble()).toInt().coerceAtMost(127).toByte()
            i += 2
            j++
        }
        mBytes = model
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFrameInvalidate >= frameDelayMs) {
            lastFrameInvalidate = now
            invalidate()
        }
    }

    fun clear() {
        mBytes = null
        invalidate()
    }

    private fun getLighterColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = min(255, (Color.red(color) + (255 - Color.red(color)) * factor).toInt())
        val g = min(255, (Color.green(color) + (255 - Color.green(color)) * factor).toInt())
        val b = min(255, (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt())
        return Color.argb(a, r, g, b)
    }

    private fun drawFadedReflection(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, cornerRadius: Float) {
        val fadeSplit = top + ((bottom - top) * 0.58f)
        mReflectionPaint.alpha = 58
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(left, top, right, fadeSplit, cornerRadius, cornerRadius, mReflectionPaint)
        } else {
            canvas.drawRect(left, top, right, fadeSplit, mReflectionPaint)
        }
        mReflectionPaint.alpha = 20
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(left, fadeSplit, right, bottom, cornerRadius, cornerRadius, mReflectionPaint)
        } else {
            canvas.drawRect(left, fadeSplit, right, bottom, mReflectionPaint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mFirst = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Kural: Kesinlikle simule/dummy veri yok, sadece gercek muzikten yakalanan mBytes varsa cizilir!
        val safeBytes = mBytes ?: return

        mRect.set(0f, 0f, width.toFloat(), height.toFloat())
        mPeakPaint.color = if (dominantColor != 0) getLighterColor(dominantColor, 0.5f) else Color.WHITE

        if (mFirst) {
            val heightVal = if (height > 0) height.toFloat() else 100f
            if (dominantColor != 0) {
                val startColor = Color.argb(38, Color.red(dominantColor), Color.green(dominantColor), Color.blue(dominantColor))
                val shader = LinearGradient(0f, heightVal, 0f, 0f, intArrayOf(startColor, dominantColor), null, Shader.TileMode.CLAMP)
                mForePaint.shader = shader
            } else if (visualizerType == TYPE_NEON_MODERN) {
                val shader = LinearGradient(0f, heightVal, 0f, 0f, intArrayOf(Color.parseColor("#0044FF"), Color.parseColor("#00FFFF")), null, Shader.TileMode.CLAMP)
                mForePaint.shader = shader
            } else {
                val colors = intArrayOf(
                    Color.parseColor("#FF0000"), Color.parseColor("#FFFF00"),
                    Color.parseColor("#00FF00"), Color.parseColor("#00FFFF"),
                    Color.parseColor("#0000FF"), Color.parseColor("#FF00FF")
                )
                val shader = LinearGradient(0f, heightVal, 0f, 0f, colors, null, Shader.TileMode.CLAMP)
                mForePaint.shader = shader
            }
            mReflectionPaint.shader = mForePaint.shader
            mFirst = false
        }

        val spectrumNum = min(mSpectrumNum, safeBytes.size)
        val barWidth = width.toFloat() / spectrumNum.toFloat()

        val gapRatio = if (visualizerType == TYPE_NEON_MODERN || visualizerType == TYPE_PARTICLE) 0.25f else 0.16f
        val gap = barWidth * gapRatio
        val effectiveBarWidth = barWidth - gap

        if (visualizerType == TYPE_GLOW_PEAK || visualizerType == TYPE_PARTICLE) {
            if (mPeaks == null || mPeaks?.size != spectrumNum) {
                mPeaks = FloatArray(spectrumNum) { 0f }
                val now = System.currentTimeMillis()
                mPeakTimes = LongArray(spectrumNum) { now }
            }
        }

        val now = System.currentTimeMillis()

        when (visualizerType) {
            TYPE_WAVE -> {
                mWavePath.reset()
                mWavePath.moveTo(0f, height.toFloat())
                var prevX = 0f
                var prevY = height.toFloat()
                for (i in 0 until spectrumNum) {
                    val magnitude = abs(safeBytes[i].toInt()) * 4f
                    val h = (magnitude / 128f) * height * 0.8f
                    val currentX = i * barWidth + (barWidth / 2f)
                    val currentY = height - h
                    mWavePath.quadTo(prevX, prevY, (prevX + currentX) / 2f, (prevY + currentY) / 2f)
                    prevX = currentX
                    prevY = currentY
                }
                mWavePath.lineTo(width.toFloat(), prevY)
                mWavePath.lineTo(width.toFloat(), height.toFloat())
                mWavePath.close()
                canvas.drawPath(mWavePath, mForePaint)
            }
            TYPE_RADIAL -> {
                val centerX = width / 2f
                val centerY = height / 2f
                val baseRadius = min(centerX, centerY) * 0.24f
                for (i in 0 until spectrumNum) {
                    val magnitude = abs(safeBytes[i].toInt()) * 4f
                    val h = (magnitude / 128f) * min(centerX, centerY) * 0.86f
                    val angle = (i * 2 * Math.PI / spectrumNum).toFloat()
                    val startX = centerX + cos(angle.toDouble()).toFloat() * baseRadius
                    val startY = centerY + sin(angle.toDouble()).toFloat() * baseRadius
                    val endX = centerX + cos(angle.toDouble()).toFloat() * (baseRadius + h)
                    val endY = centerY + sin(angle.toDouble()).toFloat() * (baseRadius + h)
                    mForePaint.strokeWidth = effectiveBarWidth * 1.2f
                    mForePaint.style = Paint.Style.STROKE
                    mForePaint.strokeCap = Paint.Cap.ROUND
                    canvas.drawLine(startX, startY, endX, endY, mForePaint)
                }
                mForePaint.style = Paint.Style.FILL
            }
            TYPE_RINGS -> {
                val centerX = width / 2f
                val centerY = height / 2f
                mForePaint.style = Paint.Style.STROKE
                for (r in 0 until 4) {
                    val index = (r * spectrumNum) / 5
                    if (index < spectrumNum) {
                        val magnitude = abs(safeBytes[index].toInt()) * 4f
                        val extraRadius = (magnitude / 128f) * min(centerX, centerY) * 0.5f
                        mForePaint.strokeWidth = 10f - (r * 2f)
                        canvas.drawCircle(centerX, centerY, 50f + (r * 40f) + extraRadius, mForePaint)
                    }
                }
                mForePaint.style = Paint.Style.FILL
            }
            else -> {
                val effectiveReflectionRatio = mirrorReflectionRatio * reflectionQualityScale
                val drawReflection = effectiveReflectionRatio > 0f &&
                        visualizerType != TYPE_CENTER_MIRRORED &&
                        visualizerType != TYPE_PARTICLE
                val reflectionGap = if (drawReflection) max(1f, height * 0.012f) else 0f
                val reflectionSpace = if (drawReflection) height * effectiveReflectionRatio else 0f
                val mainBottom = height - reflectionSpace - reflectionGap
                val mainHeight = max(1f, mainBottom)

                if (drawReflection) {
                    canvas.drawLine(0f, mainBottom + (reflectionGap * 0.5f), width.toFloat(), mainBottom + (reflectionGap * 0.5f), mReflectionDividerPaint)
                }

                val peaks = mPeaks
                val peakTimes = mPeakTimes

                for (i in 0 until spectrumNum) {
                    val magnitude = abs(safeBytes[i].toInt()) * 4f
                    var h = max((magnitude / 128f) * mainHeight, mainHeight * 0.035f)
                    if (h > mainHeight) h = mainHeight
                    if (h < 0) h = 0f

                    val left = i * barWidth + (gap / 2)
                    val top = mainBottom - h
                    val right = left + effectiveBarWidth
                    val bottom = mainBottom

                    when (visualizerType) {
                        TYPE_NEON_MODERN -> {
                            canvas.drawRoundRect(left, top, right, bottom, effectiveBarWidth / 2f, effectiveBarWidth / 2f, mForePaint)
                        }
                        TYPE_CENTER_MIRRORED -> {
                            val midY = height / 2f
                            val halfHeight = h / 2f
                            canvas.drawRoundRect(left, midY - halfHeight, right, midY + halfHeight, effectiveBarWidth / 2f, effectiveBarWidth / 2f, mForePaint)
                        }
                        TYPE_PARTICLE -> {
                            if (peaks != null && peakTimes != null) {
                                if (h >= peaks[i]) {
                                    peaks[i] = h
                                    peakTimes[i] = now
                                } else {
                                    val elapsed = (now - peakTimes[i]) / 1000f
                                    val decay = elapsed * elapsed * height * 1.5f
                                    peaks[i] = max(0f, peaks[i] - decay)
                                }
                                val peakTop = mainBottom - peaks[i]
                                canvas.drawRect(left, peakTop - effectiveBarWidth, right, peakTop, mPeakPaint)
                            }
                        }
                        TYPE_GLOW_PEAK -> {
                            canvas.drawRect(left, top, right, bottom, mForePaint)
                            if (peaks != null && peakTimes != null) {
                                if (h >= peaks[i]) {
                                    peaks[i] = h
                                    peakTimes[i] = now
                                } else {
                                    val elapsed = (now - peakTimes[i]) / 1000f
                                    val decay = elapsed * height * 0.6f
                                    peaks[i] = max(0f, peaks[i] - decay)
                                    peakTimes[i] = now
                                }
                                val peakTop = mainBottom - peaks[i]
                                canvas.drawRect(left, peakTop, right, peakTop + 6f, mPeakPaint)
                            }
                        }
                        else -> {
                            canvas.drawRect(left, top, right, bottom, mForePaint)
                        }
                    }

                    if (drawReflection) {
                        val reflectedHeight = min(reflectionSpace, h * effectiveReflectionRatio)
                        val reflectionTop = mainBottom + reflectionGap
                        val reflectionBottom = reflectionTop + reflectedHeight
                        drawFadedReflection(canvas, left, reflectionTop, right, reflectionBottom, if (visualizerType == TYPE_NEON_MODERN) effectiveBarWidth / 2f else 0f)
                    }
                }
            }
        }
    }
}
