package app.vela.ui

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import app.vela.variant.CarIntegration

/** Native popup windows own their system bars independently of the activity window. */
@Suppress("DEPRECATION")
private fun applyPopupSystemBars(view: View) {
    val immersive = CarIntegration.immersive.value
    val showStatus = !immersive && CarIntegration.statusBarVisible.value
    applyLegacySystemBarVisibility(view, immersive, showStatus)
    if (Build.VERSION.SDK_INT >= 30) {
        view.windowInsetsController?.apply {
            systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) hide(android.view.WindowInsets.Type.systemBars())
            else {
                show(android.view.WindowInsets.Type.navigationBars())
                if (showStatus) show(android.view.WindowInsets.Type.statusBars())
                else hide(android.view.WindowInsets.Type.statusBars())
            }
        }
    }
}

fun bindVelaSystemBars(window: Window) = bindNativeSystemBars(window.decorView) { applyVelaSystemBars(window) }

fun bindVelaPopupSystemBars(view: View) = bindNativeSystemBars(view.rootView) { applyPopupSystemBars(view.rootView) }

private fun bindNativeSystemBars(view: View, apply: () -> Unit) {
    if (view.getTag(app.vela.R.id.native_system_bar_binding) == true) { apply(); return }
    view.setTag(app.vela.R.id.native_system_bar_binding, true)
    val task = Runnable { apply() }
    val observer = view.viewTreeObserver
    val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) view.post(task) }
    observer.addOnWindowFocusChangeListener(focus)
    view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { v.post(task) }
        override fun onViewDetachedFromWindow(v: View) {
            v.setTag(app.vela.R.id.native_system_bar_binding, null)
            v.removeCallbacks(task)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            v.removeOnAttachStateChangeListener(this)
        }
    })
    apply()
    view.post(task)
}
