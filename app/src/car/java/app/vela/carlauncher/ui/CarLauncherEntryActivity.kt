package app.vela.carlauncher.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.vela.MainActivity

/** HOME resumes before it forwards to the retained standard task. */
class CarLauncherEntryActivity : Activity() {
    private var forwarded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!intent.hasCategory(Intent.CATEGORY_HOME)) forward()
    }

    override fun onResume() {
        super.onResume()
        if (window.decorView.hasWindowFocus()) forward()
    }

    override fun onPostResume() {
        super.onPostResume()
        // Some HOME windows never receive a focus callback after an OEM app.
        window.decorView.post { forward() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) forward()
    }

    private fun forward() {
        if (forwarded || isFinishing) return
        forwarded = true
        app.vela.util.FileLogger.i("LauncherTask", "Entry forward home=${intent.hasCategory(Intent.CATEGORY_HOME)} task=$taskId")
        MainActivity.returnToApp(this, intent)
        finish()
    }
}
