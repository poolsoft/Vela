package app.vela.carlauncher.tools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

object LauncherStartup {
    private var ran = false
    val preferencesChanged = MutableStateFlow(0)
    fun selected(context: Context): String? = context.getSharedPreferences("vela_launcher_tools", 0).getString("startup_app", null)
    fun select(context: Context, packageName: String?) {
        context.getSharedPreferences("vela_launcher_tools", 0).edit().putString("startup_app", packageName).apply()
        preferencesChanged.value++
    }
    /** Runs once in a foreground launcher session; never starts activities from a boot receiver. */
    fun run(context: Context): Boolean {
        if (ran) return true
        ran = true
        val pkg = selected(context) ?: return true
        if (pkg == context.packageName) return true
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
