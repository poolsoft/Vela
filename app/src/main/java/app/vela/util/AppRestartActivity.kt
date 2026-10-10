package app.vela.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import app.vela.MainActivity

/** Foreground hand-off: survives the old process without an alarm or background launch. */
class AppRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val oldPid = intent.getIntExtra("oldPid", 0)
        val manager = getSystemService(ActivityManager::class.java)
        val old = manager.runningAppProcesses?.firstOrNull {
            it.pid == oldPid && it.uid == Process.myUid() &&
                it.processName == applicationInfo.processName
        }
        if (old != null && oldPid != Process.myPid()) Process.killProcess(oldPid)
        val handler = Handler(Looper.getMainLooper())
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000
        val reopen = object : Runnable {
            override fun run() {
                val alive = manager.runningAppProcesses?.any {
                    it.pid == oldPid && it.uid == Process.myUid() && it.processName == applicationInfo.processName
                } == true
                if (alive && android.os.SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed(this, 100)
                    return
                }
                if (alive) {
                    FileLogger.e("AppRestart", "Old launcher process did not exit; restart aborted")
                    finish()
                    return
                }
                FileLogger.i("AppRestart", "Opening fresh launcher after pid=$oldPid")
                startActivity(Intent(this@AppRestartActivity, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                finish()
            }
        }
        handler.postDelayed(reopen, 100)
    }
}
