package app.vela.carlauncher.layout

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import app.vela.R
import app.vela.carlauncher.settings.CarLauncherSettings

/**
 * OsmAnd CarLauncher mimarisinin birebir eslenigi olan Duzen ve Ekran Yoneticisi.
 * - Android Auto tarzi bolunmus ekran (Buyuk Panel ve Kucuk Panel takasi)
 * - Acilan uygulama buyuk panele gecer, harita kucuk panele gecer.
 * - Kapatildiginda harita tekrar buyuk panel olur.
 * - widget_handle (suruklenebilir ayirici) ile canli boyutlandirma ve yuzde kaydi.
 * - Dikey / Yatay Dinamik Dock Konumlandirma (left, right, bottom) ve olcekleme.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarLayoutManager(
    private val context: Context,
    private val rootLayout: ConstraintLayout,
    private val mapContainer: View,
    private val widgetPanel: View,
    private val appDock: View,
    private val widgetHandle: ImageButton?,
    private val appDrawerContainer: View?
) {

    private val carSettings = CarLauncherSettings.getInstance(context)

    // Acilan uygulamanin Buyuk Panele gecip gecmedigi durumu
    // false = Harita Buyuk, Muzik/Widget Kucuk
    // true = Muzik/Uygulama Buyuk, Harita Kucuk
    var isContentFullScreen: Boolean = false
        private set

    // Masaustu modunda sadece widget_panel ekrani kaplar
    var isDesktopMode: Boolean = false
        private set

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialPanelPercent = 0.35f
    private var isDragging = false
    private val touchSlop = 10f

    init {
        setupWidgetHandleTouchListener()
        val isPortrait = isPortraitLayout()
        applyWidgetHandleStyle(isPortrait)
    }

    fun setContentFullScreen(fullScreen: Boolean) {
        this.isContentFullScreen = fullScreen
    }

    fun setDesktopModeState(desktop: Boolean) {
        this.isDesktopMode = desktop
    }

    private fun setupWidgetHandleTouchListener() {
        widgetHandle?.setOnTouchListener { _, event ->
            val isPortrait = isPortraitLayout()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialPanelPercent = if (isPortrait) {
                        carSettings.getWidgetPanelHeightPortrait()
                    } else {
                        carSettings.getWidgetPanelWidthPercent()
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val primaryDelta = if (isPortrait) dy else dx
                    if (!isDragging && Math.abs(primaryDelta) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        updateWidgetPanelSize(dx, dy, initialPanelPercent, isPortrait)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        persistWidgetPanelSize(isPortrait)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun updateWidgetPanelSize(dx: Float, dy: Float, initialPercent: Float, isPortrait: Boolean) {
        if (isPortrait) {
            val availableHeight = getAvailablePanelHeight(isPortrait)
            if (availableHeight <= 0) return
            val expandUp = carSettings.getPortraitExpansion() == "expand_up"
            var smallViewOnTop = !expandUp
            if (isContentFullScreen) {
                smallViewOnTop = !smallViewOnTop
            }
            val direction = if (smallViewOnTop) 1.0f else -1.0f
            val rawPercent = initialPercent + direction * (dy / availableHeight.toFloat())
            val percent = rawPercent.coerceIn(0.15f, 0.65f)
            carSettings.setWidgetPanelHeightPortrait(percent, false)
        } else {
            val availableWidth = getAvailablePanelWidth(isPortrait)
            if (availableWidth <= 0) return
            val expandRight = carSettings.getLandscapeExpansion() == "expand_right"
            var smallViewOnLeft = expandRight
            if (isContentFullScreen) {
                smallViewOnLeft = !smallViewOnLeft
            }
            val direction = if (smallViewOnLeft) 1.0f else -1.0f
            val rawPercent = initialPercent + direction * (dx / availableWidth.toFloat())
            val percent = rawPercent.coerceIn(0.15f, 0.65f)
            carSettings.setWidgetPanelWidthPercent(percent, false)
        }
        applyLayout(isWidgetPanelOpen = true)
    }

    private fun persistWidgetPanelSize(isPortrait: Boolean) {
        if (isPortrait) {
            val p = carSettings.getWidgetPanelHeightPortrait()
            carSettings.setWidgetPanelHeightPortrait(p, true)
        } else {
            val p = carSettings.getWidgetPanelWidthPercent()
            carSettings.setWidgetPanelWidthPercent(p, true)
        }
    }

    private fun isPortraitLayout(): Boolean = if (rootLayout.width > 0 && rootLayout.height > 0)
        rootLayout.height > rootLayout.width else context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun dockExtent(portrait: Boolean): Int {
        val density = context.resources.displayMetrics.density
        val scale = 0.3f + carSettings.getEffectiveDockSize(portrait) / 100f * 1.4f
        val base = if (carSettings.getEffectiveDockPosition(portrait) == "bottom")
            context.resources.getDimension(R.dimen.dock_height) else 64f * density
        return (base * scale).toInt().coerceIn((64 * density).toInt(), (104 * density).toInt())
    }
    private fun getAvailablePanelWidth(portrait: Boolean): Int {
        val size = rootLayout.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        return (size - if (carSettings.getEffectiveDockPosition(portrait) != "bottom") dockExtent(portrait) else 0).coerceAtLeast(0)
    }
    private fun getAvailablePanelHeight(portrait: Boolean): Int {
        val size = rootLayout.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        return (size - if (carSettings.getEffectiveDockPosition(portrait) == "bottom") dockExtent(portrait) else 0).coerceAtLeast(0)
    }
    private fun smallPanelSize(available: Int, percent: Float, portrait: Boolean): Int {
        val density = context.resources.displayMetrics.density
        val usable = (available - 28 * density).toInt().coerceAtLeast(2)
        val maximum = usable / 2
        val minimum = ((if (portrait) 176 else 208) * density).toInt().coerceAtMost(maximum)
        return (usable * percent).toInt().coerceIn(minimum, maximum)
    }

    /**
     * OsmAnd CarLayoutManager mimarisinin ConstraintSet ile yerlesim uygulamasi.
     */
    fun applyLayout(isWidgetPanelOpen: Boolean) {
        val isPortrait = isPortraitLayout()
        val dockPos = carSettings.getEffectiveDockPosition(isPortrait)
        val density = context.resources.displayMetrics.density
        val gapSize = (6 * density).toInt() // OsmAnd ince 6dp panel araligi
        val sideMargin = (8 * density).toInt()
        val topMargin = (8 * density).toInt()
        val bottomMargin = (8 * density).toInt()

        val cs = ConstraintSet()
        cs.clone(rootLayout)

        // 1. Bolgeleri temizle
        val ids = intArrayOf(R.id.app_dock, R.id.widget_panel, R.id.map_container, R.id.app_drawer_container, R.id.widget_handle)
        for (id in ids) {
            cs.clear(id, ConstraintSet.LEFT)
            cs.clear(id, ConstraintSet.RIGHT)
            cs.clear(id, ConstraintSet.TOP)
            cs.clear(id, ConstraintSet.BOTTOM)
            cs.clear(id, ConstraintSet.START)
            cs.clear(id, ConstraintSet.END)
        }

        // 2. Dock Olcekleme ve Konumlandirma
        val dockHeightPx = dockExtent(isPortrait)
        val sidebarWidthPx = dockHeightPx

        cs.setVisibility(R.id.app_dock, View.VISIBLE)
        cs.setVisibility(R.id.widget_handle, if (isWidgetPanelOpen && !isDesktopMode) View.VISIBLE else View.GONE)

        when (dockPos) {
            "left" -> {
                cs.connect(R.id.app_dock, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                cs.connect(R.id.app_dock, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                cs.constrainWidth(R.id.app_dock, sidebarWidthPx)
                cs.constrainHeight(R.id.app_dock, 0)
            }
            "right" -> {
                cs.connect(R.id.app_dock, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                cs.connect(R.id.app_dock, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                cs.constrainWidth(R.id.app_dock, sidebarWidthPx)
                cs.constrainHeight(R.id.app_dock, 0)
            }
            else -> { // bottom
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                cs.connect(R.id.app_dock, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                cs.connect(R.id.app_dock, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                cs.constrainHeight(R.id.app_dock, dockHeightPx)
                cs.constrainWidth(R.id.app_dock, 0)
            }
        }

        // 3. Desktop Modu (Sadece widget_panel tum ekrani kaplar)
        if (isDesktopMode) {
            cs.setVisibility(R.id.map_container, View.GONE)
            cs.setVisibility(R.id.widget_handle, View.GONE)
            cs.setVisibility(R.id.widget_panel, View.VISIBLE)

            cs.connect(R.id.widget_panel, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            when (dockPos) {
                "left" -> {
                    cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    cs.connect(R.id.widget_panel, ConstraintSet.START, R.id.app_dock, ConstraintSet.END)
                    cs.connect(R.id.widget_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                }
                "right" -> {
                    cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    cs.connect(R.id.widget_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    cs.connect(R.id.widget_panel, ConstraintSet.END, R.id.app_dock, ConstraintSet.START)
                }
                else -> { // bottom
                    cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, R.id.app_dock, ConstraintSet.TOP)
                    cs.connect(R.id.widget_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    cs.connect(R.id.widget_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                }
            }
            cs.constrainWidth(R.id.widget_panel, 0)
            cs.constrainHeight(R.id.widget_panel, 0)
            cs.applyTo(rootLayout)
            return
        }

        // 4. Panel Kapaliysa (Sadece Harita Ekranda)
        else if (!isWidgetPanelOpen) {
            cs.setVisibility(R.id.map_container, View.VISIBLE)
            cs.setVisibility(R.id.widget_panel, View.GONE)
            // Harita tum ekrani kaplar (dock haric)
            cs.connect(R.id.map_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            cs.setMargin(R.id.map_container, ConstraintSet.TOP, topMargin)
            if (isPortrait) {
                cs.connect(R.id.map_container, ConstraintSet.BOTTOM, if ("bottom" == dockPos) R.id.app_dock else ConstraintSet.PARENT_ID, if ("bottom" == dockPos) ConstraintSet.TOP else ConstraintSet.BOTTOM)
                cs.setMargin(R.id.map_container, ConstraintSet.BOTTOM, bottomMargin)
                cs.connect(R.id.map_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                cs.setMargin(R.id.map_container, ConstraintSet.START, sideMargin)
                cs.connect(R.id.map_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                cs.setMargin(R.id.map_container, ConstraintSet.END, sideMargin)
            } else {
                cs.connect(R.id.map_container, ConstraintSet.START, if ("left" == dockPos) R.id.app_dock else ConstraintSet.PARENT_ID, if ("left" == dockPos) ConstraintSet.END else ConstraintSet.START)
                cs.setMargin(R.id.map_container, ConstraintSet.START, sideMargin)
                cs.connect(R.id.map_container, ConstraintSet.END, if ("right" == dockPos) R.id.app_dock else ConstraintSet.PARENT_ID, if ("right" == dockPos) ConstraintSet.START else ConstraintSet.END)
                cs.setMargin(R.id.map_container, ConstraintSet.END, sideMargin)
                cs.connect(R.id.map_container, ConstraintSet.BOTTOM, if ("bottom" == dockPos) R.id.app_dock else ConstraintSet.PARENT_ID, if ("bottom" == dockPos) ConstraintSet.TOP else ConstraintSet.BOTTOM)
                cs.setMargin(R.id.map_container, ConstraintSet.BOTTOM, bottomMargin)
            }
            cs.constrainWidth(R.id.map_container, 0)
            cs.constrainHeight(R.id.map_container, 0)
        } else {
            // Dual Panel Modu (Harita + Widget Panel)
            cs.setVisibility(R.id.map_container, View.VISIBLE)
            cs.setVisibility(R.id.widget_panel, View.VISIBLE)

            val largeViewId = if (isContentFullScreen) R.id.widget_panel else R.id.map_container
            val smallViewId = if (isContentFullScreen) R.id.map_container else R.id.widget_panel

            if (isPortrait) {
                val expandUp = carSettings.getPortraitExpansion() == "expand_up"
                val topViewId = if (expandUp) R.id.map_container else R.id.widget_panel
                val bottomViewId = if (expandUp) R.id.widget_panel else R.id.map_container

                val panelHeightPercent = carSettings.getWidgetPanelHeightPortrait()
                val availableHeight = getAvailablePanelHeight(isPortrait)
                val smallHeightPx = smallPanelSize(availableHeight, panelHeightPercent, true)

                val leftBorder = if (dockPos == "left") R.id.app_dock else ConstraintSet.PARENT_ID
                val rightBorder = if (dockPos == "right") R.id.app_dock else ConstraintSet.PARENT_ID
                val leftSide = if (dockPos == "left") ConstraintSet.END else ConstraintSet.START
                val rightSide = if (dockPos == "right") ConstraintSet.START else ConstraintSet.END

                cs.connect(topViewId, ConstraintSet.START, leftBorder, leftSide)
                cs.setMargin(topViewId, ConstraintSet.START, sideMargin)
                cs.connect(topViewId, ConstraintSet.END, rightBorder, rightSide)
                cs.setMargin(topViewId, ConstraintSet.END, sideMargin)
                cs.constrainWidth(topViewId, 0)

                cs.connect(bottomViewId, ConstraintSet.START, leftBorder, leftSide)
                cs.setMargin(bottomViewId, ConstraintSet.START, sideMargin)
                cs.connect(bottomViewId, ConstraintSet.END, rightBorder, rightSide)
                cs.setMargin(bottomViewId, ConstraintSet.END, sideMargin)
                cs.constrainWidth(bottomViewId, 0)

                cs.connect(topViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.setMargin(topViewId, ConstraintSet.TOP, topMargin)
                cs.connect(topViewId, ConstraintSet.BOTTOM, bottomViewId, ConstraintSet.TOP, gapSize)

                cs.connect(bottomViewId, ConstraintSet.TOP, topViewId, ConstraintSet.BOTTOM, gapSize)
                val bottomBorder = if (dockPos == "bottom") R.id.app_dock else ConstraintSet.PARENT_ID
                val bottomSide = if (dockPos == "bottom") ConstraintSet.TOP else ConstraintSet.BOTTOM
                cs.connect(bottomViewId, ConstraintSet.BOTTOM, bottomBorder, bottomSide)
                cs.setMargin(bottomViewId, ConstraintSet.BOTTOM, bottomMargin)

                // Ayirici tutamac (widget_handle) dikey iki panelin ortasina
                cs.connect(R.id.widget_handle, ConstraintSet.TOP, topViewId, ConstraintSet.BOTTOM)
                cs.connect(R.id.widget_handle, ConstraintSet.BOTTOM, bottomViewId, ConstraintSet.TOP)
                cs.connect(R.id.widget_handle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                cs.connect(R.id.widget_handle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                val handleW = (48 * density).toInt()
                val handleH = (24 * density).toInt()
                cs.constrainWidth(R.id.widget_handle, handleW)
                cs.constrainHeight(R.id.widget_handle, handleH)

                cs.constrainHeight(largeViewId, 0) // BUYUK panel kalan alani doldurur
                cs.constrainHeight(smallViewId, smallHeightPx) // KUCUK panel yuzdelik yukseklik alir
            } else {
                // Landscape (Yatay) Yerlesim
                val leftBorder = if (dockPos == "left") R.id.app_dock else ConstraintSet.PARENT_ID
                val rightBorder = if (dockPos == "right") R.id.app_dock else ConstraintSet.PARENT_ID
                val leftSide = if (dockPos == "left") ConstraintSet.END else ConstraintSet.START
                val rightSide = if (dockPos == "right") ConstraintSet.START else ConstraintSet.END

                val expandRight = carSettings.getLandscapeExpansion() == "expand_right"
                val leftViewId = if (expandRight) R.id.widget_panel else R.id.map_container
                val rightViewId = if (expandRight) R.id.map_container else R.id.widget_panel

                val panelWidthPercent = carSettings.getWidgetPanelWidthPercent()
                val availableWidth = getAvailablePanelWidth(isPortrait)
                val smallWidthPx = smallPanelSize(availableWidth, panelWidthPercent, false)

                val bottomBorder = if (dockPos == "bottom") R.id.app_dock else ConstraintSet.PARENT_ID
                val bottomSide = if (dockPos == "bottom") ConstraintSet.TOP else ConstraintSet.BOTTOM

                cs.connect(leftViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.setMargin(leftViewId, ConstraintSet.TOP, topMargin)
                cs.connect(leftViewId, ConstraintSet.BOTTOM, bottomBorder, bottomSide)
                cs.setMargin(leftViewId, ConstraintSet.BOTTOM, bottomMargin)
                cs.constrainHeight(leftViewId, 0)

                cs.connect(rightViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.setMargin(rightViewId, ConstraintSet.TOP, topMargin)
                cs.connect(rightViewId, ConstraintSet.BOTTOM, bottomBorder, bottomSide)
                cs.setMargin(rightViewId, ConstraintSet.BOTTOM, bottomMargin)
                cs.constrainHeight(rightViewId, 0)

                cs.connect(leftViewId, ConstraintSet.START, leftBorder, leftSide)
                cs.setMargin(leftViewId, ConstraintSet.START, sideMargin)
                cs.connect(leftViewId, ConstraintSet.END, rightViewId, ConstraintSet.START, gapSize)

                cs.connect(rightViewId, ConstraintSet.START, leftViewId, ConstraintSet.END, gapSize)
                cs.connect(rightViewId, ConstraintSet.END, rightBorder, rightSide)
                cs.setMargin(rightViewId, ConstraintSet.END, sideMargin)

                // Ayirici tutamac (widget_handle) iki panelin ortasina ince dikey tutamac
                cs.connect(R.id.widget_handle, ConstraintSet.START, leftViewId, ConstraintSet.END)
                cs.connect(R.id.widget_handle, ConstraintSet.END, rightViewId, ConstraintSet.START)
                cs.connect(R.id.widget_handle, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                cs.connect(R.id.widget_handle, ConstraintSet.BOTTOM, bottomBorder, bottomSide)
                val handleW = (24 * density).toInt()
                val handleH = (48 * density).toInt()
                cs.constrainWidth(R.id.widget_handle, handleW)
                cs.constrainHeight(R.id.widget_handle, handleH)

                cs.constrainWidth(largeViewId, 0) // BUYUK panel kalan alani doldurur
                cs.constrainWidth(smallViewId, smallWidthPx) // KUCUK panel yuzdelik genislik alir
            }
        }

        // Overlay expands the content over an unchanged map; swap keeps both visible.
        if (isWidgetPanelOpen && isContentFullScreen && !isDesktopMode &&
            carSettings.panelGenislemeDavranisi.value == "overlay") {
            val left = if (dockPos == "left") R.id.app_dock else ConstraintSet.PARENT_ID
            val right = if (dockPos == "right") R.id.app_dock else ConstraintSet.PARENT_ID
            val bottom = if (dockPos == "bottom") R.id.app_dock else ConstraintSet.PARENT_ID
            val leftSide = if (dockPos == "left") ConstraintSet.END else ConstraintSet.START
            val rightSide = if (dockPos == "right") ConstraintSet.START else ConstraintSet.END
            val bottomSide = if (dockPos == "bottom") ConstraintSet.TOP else ConstraintSet.BOTTOM
            for (id in intArrayOf(R.id.map_container, R.id.widget_panel)) {
                cs.clear(id, ConstraintSet.START)
                cs.clear(id, ConstraintSet.END)
                cs.clear(id, ConstraintSet.TOP)
                cs.clear(id, ConstraintSet.BOTTOM)
                cs.connect(id, ConstraintSet.START, left, leftSide, sideMargin)
                cs.connect(id, ConstraintSet.END, right, rightSide, sideMargin)
                cs.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, topMargin)
                cs.connect(id, ConstraintSet.BOTTOM, bottom, bottomSide, bottomMargin)
                cs.constrainWidth(id, 0)
                cs.constrainHeight(id, 0)
            }
            if (isPortrait) {
                cs.clear(R.id.widget_panel, ConstraintSet.TOP)
                cs.constrainHeight(R.id.widget_panel,
                    ((getAvailablePanelHeight(true) - topMargin - bottomMargin) *
                        (1f - carSettings.getWidgetPanelHeightPortrait())).toInt().coerceAtLeast(1))
            } else {
                val freeSide = if (carSettings.getLandscapeExpansion() == "expand_right") ConstraintSet.END else ConstraintSet.START
                cs.clear(R.id.widget_panel, freeSide)
                cs.constrainWidth(R.id.widget_panel,
                    ((getAvailablePanelWidth(false) - 2 * sideMargin) *
                        (1f - carSettings.getWidgetPanelWidthPercent())).toInt().coerceAtLeast(1))
            }
            cs.setVisibility(R.id.widget_handle, View.GONE)
        }

        // 6. Uygulama Cekmecesi Overlay Baglantilari
        if (appDrawerContainer != null) {
            cs.connect(R.id.app_drawer_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            cs.connect(R.id.app_drawer_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            cs.connect(R.id.app_drawer_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            val bottomBorder = if (dockPos == "bottom") R.id.app_dock else ConstraintSet.PARENT_ID
            val bottomSide = if (dockPos == "bottom") ConstraintSet.TOP else ConstraintSet.BOTTOM
            cs.connect(R.id.app_drawer_container, ConstraintSet.BOTTOM, bottomBorder, bottomSide)
            cs.constrainWidth(R.id.app_drawer_container, 0)
            cs.constrainHeight(R.id.app_drawer_container, 0)
        }

        cs.applyTo(rootLayout)

        // OsmAnd Orijinal Ayirac: Seffaf arka plan, dikey 3 beyaz nokta (ic_more_vert)
        applyWidgetHandleStyle(isPortrait)
        widgetHandle?.bringToFront()
    }

    private fun applyWidgetHandleStyle(isPortrait: Boolean) {
        if (widgetHandle == null) return
        widgetHandle.translationX = 0f
        widgetHandle.translationY = 0f
        widgetHandle.background = null
        widgetHandle.setPadding(0, 0, 0, 0)
        widgetHandle.setImageResource(R.drawable.ic_more_vert)
        widgetHandle.setColorFilter(0xCCFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        if (isPortrait) {
            widgetHandle.rotation = 90f
        } else {
            widgetHandle.rotation = 0f
        }
    }
}
