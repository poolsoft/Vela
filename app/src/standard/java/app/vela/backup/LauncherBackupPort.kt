package app.vela.backup

import android.content.Context
import org.json.JSONObject

/** A normal Vela build ignores launcher settings but can load the map data from a car backup. */
internal object LauncherBackupPort {
    val schema: Map<String, Map<String, String>> = emptyMap()
    fun export(context: Context): String = JSONObject().put("format", "vela-launcher")
        .put("version", 1).put("groups", JSONObject()).toString()
    fun validate(text: String): JSONObject {
        require(text.toByteArray().size <= 1024 * 1024)
        val root = JSONObject(text)
        require(root.getString("format") == "vela-launcher" && root.getInt("version") == 1)
        return root.getJSONObject("groups")
    }
}
