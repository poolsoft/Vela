package app.vela.backup

import android.content.Context
import android.util.Xml
import app.vela.core.data.RecentPlace
import app.vela.core.data.RecentQuery
import app.vela.core.model.ParkedSpot
import app.vela.core.model.PlaceList
import app.vela.core.model.SavedPlace
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Explicit portable data only. Never transfers permissions, tokens, device URIs or Android IDs. */
internal object BackupPreferences {
    val personalSchema = mapOf(
        "vela_saved" to setOf("places"), "vela_lists" to setOf("lists"),
        "vela_shortcuts" to setOf("HOME", "WORK"),
        "vela_recents" to setOf("queries", "queries2"),
        "vela_recent_places" to setOf("places", "places2"),
    )
    private const val SETTINGS = "vela_settings"
    private val portableSettings = mapOf(
        "parking_lat" to "String", "parking_lng" to "String", "parking_at" to "Long",
        "parking_history" to "String", "voice_model" to "String", "asr_engine" to "String",
        "avoid_tolls" to "Boolean", "avoid_highways" to "Boolean", "avoid_ferries" to "Boolean",
    )
    private fun settingType(key: String): String? = portableSettings[key] ?: if (
        key.startsWith("voice_speaker_") && key.length in 15..120
    ) "Int" else null
    val names: Set<String> = personalSchema.keys + SETTINGS + LauncherBackupPort.schema.keys
    private val json = Json { ignoreUnknownKeys = true }

    fun export(context: Context): String = JSONObject().put("version", 1).put("groups", JSONObject().also { groups ->
        personalSchema.forEach { (name, keys) ->
            groups.put(name, JSONObject().also { values ->
                val prefs = context.getSharedPreferences(name, 0)
                keys.forEach { key -> prefs.getString(key, null)?.let { values.put(key, it) } }
            })
        }
        groups.put(SETTINGS, JSONObject().also { values ->
            context.getSharedPreferences(SETTINGS, 0).all.forEach { (key, value) ->
                val type = settingType(key) ?: return@forEach
                if (value != null) values.put(key, JSONObject().put("type", type).put("value", value))
            }
        })
    }).toString(2)

    fun validate(text: String): JSONObject {
        require(text.toByteArray().size <= BackupFiles.MAX_JSON)
        val root = JSONObject(text)
        require(root.get("version") == 1)
        val groups = root.getJSONObject("groups")
        require(groups.keys().asSequence().toSet() == personalSchema.keys + SETTINGS)
        personalSchema.forEach { (name, keys) ->
            val values = groups.getJSONObject(name)
            values.keys().forEach { key ->
                require(key in keys && values.get(key) is String)
                val value = values.getString(key)
                when (name) {
                    "vela_saved" -> json.decodeFromString<List<SavedPlace>>(value)
                    "vela_lists" -> json.decodeFromString<List<PlaceList>>(value)
                    "vela_shortcuts" -> json.decodeFromString<SavedPlace>(value)
                    "vela_recents" -> if (key == "queries2") json.decodeFromString<List<RecentQuery>>(value) else json.decodeFromString<List<String>>(value)
                    "vela_recent_places" -> if (key == "places2") json.decodeFromString<List<RecentPlace>>(value) else json.decodeFromString<List<SavedPlace>>(value)
                }
            }
        }
        val settings = groups.getJSONObject(SETTINGS)
        settings.keys().forEach { key ->
            val type = settingType(key) ?: error("Unknown portable setting")
            val entry = settings.getJSONObject(key)
            require(entry.getString("type") == type)
            val value = entry.get("value")
            when (type) {
                "String" -> require(value is String && value.length <= BackupFiles.MAX_JSON)
                "Boolean" -> require(value is Boolean)
                "Long" -> require(value is Number && value.toLong() >= 0 && value.toDouble() == value.toLong().toDouble())
                "Int" -> require(value is Number && value.toInt() in 0..100 && value.toDouble() == value.toInt().toDouble())
            }
            when (key) {
                "parking_lat" -> require((value as String).toDouble() in -90.0..90.0)
                "parking_lng" -> require((value as String).toDouble() in -180.0..180.0)
                "parking_history" -> json.decodeFromString<List<ParkedSpot>>(value as String)
                "voice_model", "asr_engine" -> require((value as String).length <= 120 && ".." !in value)
            }
        }
        return groups
    }

    /** Generate complete destination preference XML before any live changes, at cold start. */
    fun stage(context: Context, stage: File): List<String> {
        val personal = validate(File(stage, "personal.json").readText())
        val launcher = LauncherBackupPort.validate(File(stage, "launcher.json").readText())
        val paths = mutableListOf<String>()
        names.filter { it in personalSchema || it == SETTINGS || launcher.has(it) }.forEach { name ->
            // Do not populate Android's process-wide SharedPreferences cache before swapping XML.
            val live = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
            val bak = File(live.path + ".bak")
            val current = readXml(if (bak.exists()) bak else live)
            val values = personal.optJSONObject(name) ?: launcher.getJSONObject(name)
            val oldWidgets = current["widget_config"] as? String
            val keys = personalSchema[name] ?: if (name == SETTINGS) current.keys.filter { settingType(it) != null }.toSet()
                else LauncherBackupPort.schema.getValue(name).keys
            keys.forEach { current.remove(it) }
            values.keys().forEach { key ->
                current[key] = if (name in personalSchema) values.getString(key) else {
                    val e = values.getJSONObject(key)
                    when (e.getString("type")) {
                        "Boolean" -> e.getBoolean("value")
                        "Int" -> e.getInt("value")
                        "Long" -> e.getLong("value")
                        "Float" -> e.getDouble("value").toFloat()
                        else -> e.getString("value")
                    }
                }
            }
            if (name == "vela_car_launcher_widgets") {
                val restored = JSONArray(current["widget_config"] as? String ?: "[]")
                val existing = JSONArray(oldWidgets ?: "[]")
                val ids = (0 until restored.length()).map { restored.getJSONObject(it).getString("id") }.toSet()
                for (i in 0 until existing.length()) {
                    val item = existing.getJSONObject(i)
                    if (item.getString("typeId").startsWith("system:") && item.getString("id") !in ids) restored.put(item)
                }
                current["widget_config"] = restored.toString()
            }
            val relative = "shared_prefs/$name.xml"
            val target = BackupFiles.local(stage, relative)
            target.parentFile!!.mkdirs()
            writeXml(target, current)
            paths += relative
            // An old SharedPreferences .bak would override the restored XML on next load.
            paths += "$relative.bak"
        }
        return paths
    }

    private fun readXml(file: File): MutableMap<String, Any> {
        val values = linkedMapOf<String, Any>()
        if (!file.exists()) return values
        file.inputStream().use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, "UTF-8")
            require(p.nextTag() == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "map")
            while (p.nextTag() == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val name = requireNotNull(p.getAttributeValue(null, "name"))
                val tag = p.name
                val attr = p.getAttributeValue(null, "value")
                val value: Any = when (tag) {
                    "string" -> p.nextText()
                    "set" -> linkedSetOf<String>().also { set ->
                        while (p.nextTag() == org.xmlpull.v1.XmlPullParser.START_TAG) {
                            require(p.name == "string")
                            set += p.nextText()
                        }
                        require(p.name == "set")
                    }
                    else -> {
                        val parsed: Any = when (tag) {
                            "boolean" -> { require(attr == "true" || attr == "false"); attr == "true" }
                            "int" -> requireNotNull(attr).toInt()
                            "long" -> requireNotNull(attr).toLong()
                            "float" -> requireNotNull(attr).toFloat()
                            else -> error("Unsupported preference XML")
                        }
                        require(p.nextTag() == org.xmlpull.v1.XmlPullParser.END_TAG)
                        parsed
                    }
                }
                values[name] = value
            }
        }
        return values
    }

    private fun writeXml(file: File, values: Map<String, *>) {
        FileOutputStream(file).use { out ->
            val x = Xml.newSerializer()
            x.setOutput(out, "UTF-8")
            x.startDocument("UTF-8", true)
            x.startTag(null, "map")
            values.forEach { (name, value) ->
                val tag = when (value) {
                    is String -> "string"
                    is Boolean -> "boolean"
                    is Int -> "int"
                    is Long -> "long"
                    is Float -> "float"
                    is Set<*> -> "set"
                    else -> error("Unsupported preference value")
                }
                x.startTag(null, tag).attribute(null, "name", name)
                when (value) {
                    is String -> x.text(value)
                    is Set<*> -> value.forEach { x.startTag(null, "string").text(it as String).endTag(null, "string") }
                    else -> x.attribute(null, "value", value.toString())
                }
                x.endTag(null, tag)
            }
            x.endTag(null, "map")
            x.endDocument()
            x.flush()
            out.fd.sync()
        }
    }
}
