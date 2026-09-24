package app.vela.carlauncher.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Only launcher preferences are portable; Android widget IDs belong to the device. */
object LauncherBackup {
    private const val MAX_BYTES = 1024 * 1024
    // Filled from the launcher's declared preference keys; never accepts arbitrary preference files.
    val schema: Map<String, Map<String, String>> = mapOf("vela_car_launcher_prefs" to mapOf("car_launcher_enabled" to "Boolean", "car_launcher_immersive_mode" to "Boolean", "car_launcher_status_bar" to "Boolean", "car_launcher_dock_position" to "String", "car_launcher_dock_size" to "Int", "widget_panel_position" to "String", "car_launcher_expansion_behavior" to "String", "widget_panel_width_percent" to "Float", "widget_panel_height_portrait" to "Float", "car_launcher_auto_play_music" to "Boolean", "car_launcher_visualizer_type" to "Int", "car_launcher_visualizer_type_large" to "Int", "car_launcher_visualizer_fps" to "Int", "car_launcher_ambiance_visualizer" to "Boolean", "car_launcher_music_app" to "String", "car_launcher_night_dim_mode" to "Boolean", "car_launcher_night_dim_level" to "Float", "car_launcher_desktop_mode" to "Boolean", "car_launcher_desktop_in_mode_cycle" to "Boolean", "car_launcher_startup_screen" to "String", "car_launcher_floating_button_mode" to "String", "car_launcher_floating_button_size" to "Int", "car_launcher_weather_enabled" to "Boolean", "car_launcher_equalizer_app" to "String", "car_launcher_floating_button_x" to "Int", "car_launcher_floating_button_y" to "Int"),
        "car_launcher_app_dock" to mapOf("shortcuts" to "String"),
        "vela_car_launcher_widgets" to mapOf("widget_config" to "String"),
        "vela_music_focus" to mapOf("auto_follow" to "Boolean", "startup_source" to "String", "bt_wait_seconds" to "Int"),
        "vela_music_playlists" to mapOf("library" to "String"),
        "vela_launcher_tools" to mapOf("startup_app" to "String", "text_scale" to "Float", "antenna_from_lat" to "String", "antenna_from_lon" to "String", "antenna_from_alt" to "String", "antenna_to_lat" to "String", "antenna_to_lon" to "String", "antenna_to_alt" to "String"))

    fun export(context: Context): String {
        val groups = JSONObject()
        schema.forEach { (file, keys) ->
            val values = JSONObject()
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            prefs.all.forEach entry@ { (key, value) ->
                val type = keys[key] ?: return@entry
                if (value != null) values.put(key, JSONObject().put("type", type).put("value", value))
            }
            // Bound widget identities cannot be transferred to another device.
            if (file == "vela_car_launcher_widgets" && values.has("widget_config")) {
                val entry = values.getJSONObject("widget_config")
                val array = JSONArray(entry.getString("value"))
                val portable = JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    if (!item.getString("typeId").startsWith("system:")) portable.put(item)
                }
                entry.put("value", portable.toString())
            }
            groups.put(file, values)
        }
        return JSONObject().put("format", "vela-launcher").put("version", 1).put("groups", groups).toString(2)
    }

    fun validate(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getString("format") == "vela-launcher" && root.get("version") == 1)
        val groups = root.getJSONObject("groups")
        val groupNames = groups.keys().asSequence().toSet()
        require(groupNames == schema.keys || groupNames == schema.keys - "vela_music_focus")
        // Version 1 backups written before Smart Focus had no focus preference group.
        if (!groups.has("vela_music_focus")) groups.put("vela_music_focus", JSONObject())
        schema.forEach { (file, keys) ->
            val values = groups.getJSONObject(file)
            values.keys().forEach { key ->
                val type = keys[key] ?: error("Unknown launcher setting")
                val entry = values.getJSONObject(key)
                require(entry.getString("type") == type)
                val value = entry.get("value")
                require(when (type) {
                    "Boolean" -> value is Boolean
                    "Int" -> value is Number && value.toDouble() == value.toInt().toDouble()
                    "Long" -> value is Number && value.toDouble().isFinite()
                    "Float" -> value is Number && value.toFloat().isFinite()
                    "String" -> value is String && value.length <= MAX_BYTES
                    else -> false
                })
                if (file == "vela_music_focus") {
                    if (key == "startup_source") require(value in listOf("last", "internal", "bluetooth", "hcn_radio", "xy_radio"))
                    if (key == "bt_wait_seconds") require((value as Number).toInt() in 3..15)
                }
                if (key == "shortcuts" || key == "widget_config") {
                    val array = JSONArray(value as String)
                    require(array.length() <= 200)
                    val identities = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (key == "shortcuts") require(item.getString("package").isNotBlank() && identities.add(item.getString("package")))
                        else {
                            require(item.getString("id").isNotBlank() && item.getString("id").length <= 200)
                            require(identities.add(item.getString("id")))
                            require(item.getString("title").length <= 500)
                            require(!item.getString("typeId").startsWith("system:"))
                            require(item.getString("size") in listOf("SMALL", "MEDIUM", "LARGE"))
                            require(item.getInt("pageIndex") in 0..19)
                        }
                    }
                }
                if (file == "vela_music_playlists" && key == "library") {
                    val library = JSONObject(value as String)
                    require(library.get("version") == 1)
                    val lists = library.getJSONArray("playlists")
                    require(lists.length() <= 200)
                    for (i in 0 until lists.length()) {
                        val list = lists.getJSONObject(i)
                        require(list.getString("id").isNotBlank())
                        list.getString("name")
                        val tracks = list.getJSONArray("tracks")
                        require(tracks.length() <= 10000)
                        for (j in 0 until tracks.length()) tracks.getString(j)
                    }
                }
            }
        }
        return groups
    }

    @Synchronized
    fun restore(context: Context, text: String) {
        val groups = validate(text) // Complete validation before touching any live settings.
        val before = schema.keys.associateWith { context.getSharedPreferences(it, 0).all.toMap() }
        try {
            schema.forEach { (file, keys) ->
                val prefs = context.getSharedPreferences(file, 0)
                val editor = prefs.edit()
                keys.keys.forEach { editor.remove(it) }
                val values = groups.getJSONObject(file)
                values.keys().forEach { key ->
                    val entry = values.getJSONObject(key)
                    when (keys[key]) {
                        "Boolean" -> editor.putBoolean(key, entry.getBoolean("value"))
                        "Int" -> editor.putInt(key, entry.getInt("value"))
                        "Long" -> editor.putLong(key, entry.getLong("value"))
                        "Float" -> editor.putFloat(key, entry.getDouble("value").toFloat())
                        "String" -> editor.putString(key, entry.getString("value"))
                    }
                }
                if (file == "vela_car_launcher_widgets") {
                    // Keep the current device's already-bound widgets.
                    val existing = JSONArray(prefs.getString("widget_config", "[]"))
                    val restored = JSONArray(values.optJSONObject("widget_config")?.optString("value") ?: "[]")
                    for (i in 0 until existing.length()) {
                        val item = existing.getJSONObject(i)
                        if (item.getString("typeId").startsWith("system:")) restored.put(item)
                    }
                    editor.putString("widget_config", restored.toString())
                }
                check(editor.commit())
            }
        } catch (error: Exception) {
            before.forEach { (file, values) ->
                val editor = context.getSharedPreferences(file, 0).edit().clear()
                values.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                editor.commit()
            }
            throw error
        }
    }

    fun read(context: Context, uri: android.net.Uri): String =
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_BYTES)
                out.write(buffer, 0, count)
            }
            out.toString("UTF-8")
        }
}
