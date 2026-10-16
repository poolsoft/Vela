package app.vela.carlauncher.map

import android.content.Context
import kotlinx.serialization.json.*

internal fun rendererSettings(context: Context): JsonObject = buildJsonObject {
        context.getSharedPreferences("vela_settings", Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (key in setOf("map_init_inflight", "map_init_crashes", "texture_render_auto_ms")) return@forEach
            val type = when (value) { is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is String -> "string"; is Set<*> -> "set"; else -> null }
            if (type != null) put(key, buildJsonObject {
                put("type", type)
                put("value", when (value) {
                    is Boolean -> JsonPrimitive(value); is Number -> JsonPrimitive(value)
                    is Set<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) }); else -> JsonPrimitive(value.toString())
                })
            })
        }
        // Read the observable choice in the host composition, not just a prefs snapshot.
        put("map_render_profile", buildJsonObject {
            put("type", "string")
            put("value", app.vela.ui.MapRenderProfile.selected.value)
        })
        put(app.vela.ui.MapRenderProfile.FPS_KEY, buildJsonObject {
            put("type", "int"); put("value", app.vela.ui.MapRenderProfile.customMaxFps.intValue)
        })
        put(app.vela.ui.MapRenderProfile.TILT_KEY, buildJsonObject {
            put("type", "boolean"); put("value", app.vela.ui.MapRenderProfile.customTilt.value)
        })
    }
