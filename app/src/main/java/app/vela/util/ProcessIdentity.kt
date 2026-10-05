package app.vela.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File

/** Never assume an unknown process is the main process (API 26/27 included). */
object ProcessIdentity {
    fun name(context: Context): String? {
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { Application.getProcessName() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        runCatching { File("/proc/self/cmdline").inputStream().use { it.readBytes().toString(Charsets.UTF_8).substringBefore('\u0000') } }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        return runCatching {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }.getOrNull()
    }

    fun isMain(context: Context): Boolean {
        val process = name(context) ?: return false
        return process == context.applicationInfo.processName
    }
    fun fileSuffix(context: Context): String = if (isMain(context)) "" else
        "-" + (name(context)?.substringAfter(':', "unknown") ?: "unknown").replace(Regex("[^a-zA-Z0-9_-]"), "_")
}
