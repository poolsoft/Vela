package app.vela.carlauncher.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.vela.MainActivity

/** Cosmetic launcher aliases forward to the same Activity used by HOME and notifications. */
class CarLauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(intent).setClass(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
