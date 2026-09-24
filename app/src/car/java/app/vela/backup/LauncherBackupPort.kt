package app.vela.backup

import android.content.Context
import app.vela.carlauncher.tools.LauncherBackup
import org.json.JSONObject

/** Optional launcher portion of the portable backup; map data stays in :app/main. */
internal object LauncherBackupPort {
    val schema get() = LauncherBackup.schema
    fun export(context: Context): String = LauncherBackup.export(context)
    fun validate(text: String): JSONObject {
        val root = JSONObject(text)
        val groups = root.getJSONObject("groups")
        return if (groups.length() == 0) {
            require(root.getString("format") == "vela-launcher" && root.getInt("version") == 1)
            groups
        } else LauncherBackup.validate(text)
    }
}
