package app.vela.carlauncher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.vela.MainActivity
import app.vela.R
import app.vela.carlauncher.settings.CarLauncherSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * OsmAnd ve CoMaps_Auto_V2 Uyumlu Yuvarlak Canli Hiz ve Asistan Butonu (Floating Button).
 * - Ekranda serbestce suruklenebilir ve son pozisyonunu hafizada saklar.
 * - Buton icinde anlik GPS hizi yazar (orn. 120 km/s).
 * - Hiz limitine yaklasildiginda Turuncu, asildiginda Kirmizi dinamik neon cerceve cizer.
 * - Arka plandayken tıklandıgında tek dokunusla uygulamayi on plana getirir.
 * - On plandayken tiklandiginda modu degistirir, uzun basildiginda acilir asistan menusu acar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarFloatingButtonManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: CarFloatingButtonManager? = null

        fun getInstance(context: Context): CarFloatingButtonManager {
            return instance ?: synchronized(this) {
                instance ?: CarFloatingButtonManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val mainHandler = Handler(Looper.getMainLooper())

    private var floatingView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var speedText: TextView? = null
    private var buttonBg: GradientDrawable? = null

    private var menuOverlayView: FrameLayout? = null
    private var menuParams: WindowManager.LayoutParams? = null

    private var isAdded = false
    private var isAppInForeground = false

    // Surukleme ve Dokunma Durumlari
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLongClickTriggered = false
    private var longClickRunnable: Runnable? = null

    private var lastSpeedKmh = 0f
    private var lastMaxSpeedKmh = 0f

    fun setAppInForeground(foreground: Boolean) {
        this.isAppInForeground = foreground
        updateButtonState()
    }

    fun updateSpeed(speedKmh: Float, maxSpeedKmh: Float = 0f) {
        this.lastSpeedKmh = speedKmh
        this.lastMaxSpeedKmh = maxSpeedKmh

        mainHandler.post {
            val text = speedText ?: return@post
            val bg = buttonBg ?: return@post

            val displaySpeed = if (speedKmh <= 3f) 0 else speedKmh.roundToInt()
            text.text = if (displaySpeed > 0) displaySpeed.toString() else "--"

            // Hiz Limiti Renk Yonetimi (Turkce karakter yok)
            if (maxSpeedKmh > 0) {
                val fark = speedKmh - maxSpeedKmh
                when {
                    fark > 5f -> bg.setStroke(dpToPx(4), Color.parseColor("#FF3B30")) // Kirmizi (Asiri Hiz)
                    fark > 0f -> bg.setStroke(dpToPx(3), Color.parseColor("#FF9500")) // Turuncu (Limit Siniri)
                    else -> bg.setStroke(dpToPx(2), Color.parseColor("#0A84FF"))      // Normal Neon Mavi
                }
            } else {
                bg.setStroke(dpToPx(2), Color.parseColor("#0A84FF"))
            }
        }
    }

    fun updateButtonState() {
        val shouldShow = CarLauncherSettings.shouldShowFloatingButton(isAppInForeground)
        if (shouldShow) {
            showButton()
        } else {
            hideButton()
        }
    }

    fun showButton() {
        if (isAdded) return

        // Overlay izni kontrolu (Android M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                return
            }
        }

        try {
            createFloatingView()

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val size = dpToPx(CarLauncherSettings.floatingButtonBoyutu.value)
            val p = WindowManager.LayoutParams(
                size,
                size,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            this.params = p

            restoreButtonPosition(size)

            floatingView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = p.x
                        initialY = p.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongClickTriggered = false

                        longClickRunnable = Runnable {
                            if (!isDragging) {
                                isLongClickTriggered = true
                                onButtonLongClicked()
                            }
                        }
                        mainHandler.postDelayed(longClickRunnable!!, 500)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                            longClickRunnable?.let { mainHandler.removeCallbacks(it) }
                        }

                        if (isDragging) {
                            p.x = initialX + dx
                            p.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(floatingView, p)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        longClickRunnable?.let { mainHandler.removeCallbacks(it) }

                        if (isDragging) {
                            CarLauncherSettings.saveFloatingButtonPosition(p.x, p.y)
                        } else if (!isLongClickTriggered) {
                            onButtonClicked()
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(floatingView, p)
            isAdded = true
            updateSpeed(lastSpeedKmh, lastMaxSpeedKmh)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideButton() {
        longClickRunnable?.let { mainHandler.removeCallbacks(it) }
        hideCustomOverlayMenu()

        if (isAdded && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isAdded = false
        floatingView = null
        params = null
        speedText = null
    }

    private fun createFloatingView() {
        val root = FrameLayout(context)
        val size = dpToPx(CarLauncherSettings.floatingButtonBoyutu.value)
        root.layoutParams = FrameLayout.LayoutParams(size, size)

        // Arka plan: Premium koyu daire ve neon mavi kenarlik
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xEE14141E.toInt())
            setStroke(dpToPx(2), Color.parseColor("#0A84FF"))
        }
        this.buttonBg = bg
        root.background = bg

        // Hiz yazisi
        val st = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = "--"
        }
        this.speedText = st

        val textLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        root.addView(st, textLp)

        this.floatingView = root
    }

    private fun restoreButtonPosition(size: Int) {
        val p = params ?: return
        val saved = CarLauncherSettings.getFloatingButtonPosition()
        val displaySize = getDisplaySize()

        if (saved != null) {
            p.x = saved.first.coerceIn(0, max(0, displaySize.x - size))
            p.y = saved.second.coerceIn(0, max(0, displaySize.y - size))
        } else {
            p.x = displaySize.x - size - dpToPx(16)
            p.y = (displaySize.y / 2) - (size / 2)
        }
    }

    private fun onButtonClicked() {
        if (!isAppInForeground) {
            bringAppToForeground()
        } else {
            // On planda kisa basim: Desktop modunu degistir
            val yeniMod = !CarLauncherSettings.desktopModu.value
            CarLauncherSettings.setDesktopModu(yeniMod)
        }
    }

    private fun onButtonLongClicked() {
        if (!isAppInForeground) {
            bringAppToForeground()
        } else {
            // On planda uzun basim: Acilir asistan menusu goster
            showOverlayMenu()
        }
    }

    private fun bringAppToForeground() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showOverlayMenu() {
        if (floatingView == null) return
        if (menuOverlayView != null) {
            hideCustomOverlayMenu()
            return
        }

        val overlay = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xF0101018.toInt())
                setStroke(dpToPx(1), Color.parseColor("#0A84FF"))
                cornerRadius = dpToPx(14).toFloat()
            }
            val pad = dpToPx(8)
            setPadding(pad, pad, pad, pad)
        }
        this.menuOverlayView = overlay

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        overlay.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Menü Öğeleri
        addMenuItem(content, "Bölünmüş Ekran (Layout)") {
            hideCustomOverlayMenu()
            // Ekran layoutunu degistir
            val desktop = CarLauncherSettings.desktopModu.value
            if (desktop) CarLauncherSettings.setDesktopModu(false)
        }

        addMenuItem(content, "Masaüstü Modu (Desktop)") {
            hideCustomOverlayMenu()
            val desktop = CarLauncherSettings.desktopModu.value
            CarLauncherSettings.setDesktopModu(!desktop)
        }

        addMenuItem(content, "Vela'yı Öne Getir") {
            hideCustomOverlayMenu()
            bringAppToForeground()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val menuWidth = dpToPx(220)
        val mp = WindowManager.LayoutParams(
            menuWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        this.menuParams = mp

        val currentP = params ?: return
        val displaySize = getDisplaySize()

        if (currentP.x > displaySize.x / 2) {
            mp.x = currentP.x - menuWidth - dpToPx(10)
        } else {
            mp.x = currentP.x + dpToPx(CarLauncherSettings.floatingButtonBoyutu.value) + dpToPx(10)
        }
        mp.y = currentP.y
        mp.x = mp.x.coerceIn(0, max(0, displaySize.x - menuWidth))

        overlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hideCustomOverlayMenu()
                true
            } else {
                false
            }
        }

        try {
            windowManager.addView(overlay, mp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addMenuItem(parent: LinearLayout, title: String, onClick: () -> Unit) {
        val item = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick() }
        }
        parent.addView(item)
    }

    private fun hideCustomOverlayMenu() {
        menuOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        menuOverlayView = null
        menuParams = null
    }

    private fun getDisplaySize(): Point {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        return size
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
