package app.vela.carlauncher.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.vela.MainActivity

/** Cosmetic launcher aliases forward to the same Activity used by HOME and notifications. */
class CarLauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivity.returnToApp(this, intent)
        finish()
    }
}
