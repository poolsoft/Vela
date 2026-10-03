package app.vela.carlauncher.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Other copies of the launcher bridge must not become an external music source. */
internal object LauncherMediaPeers {
    @Volatile private var packages: Set<String>? = null

    @Synchronized fun contains(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        val peers = packages ?: runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(
                Intent("android.media.browse.MediaBrowserService"), PackageManager.GET_META_DATA,
            ).mapNotNull { result ->
                result.serviceInfo?.takeIf { it.name.endsWith(".carlauncher.media.CarMediaService") }?.packageName
            }.toSet()
        }.getOrDefault(emptySet()).also { packages = it }
        return packageName in peers
    }
}
