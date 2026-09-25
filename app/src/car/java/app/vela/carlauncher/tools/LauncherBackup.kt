package app.vela.carlauncher.tools

import android.content.Context
import app.vela.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * OsmAnd benzeri moduler, secimli yedekleme ve geri yukleme sistemi.
 * Harita (basemap/pmtiles), Rota motoru (obf), GPX Geziler (trips),
 * Kayitli yerler, Navigasyon ayarlari ve Car Launcher bilesenlerini kapsar.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object LauncherBackup {

    private const val MAX_JSON_BYTES = 16 * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024

    enum class BackupCategory(
        val id: String,
        val titleRes: Int,
        val descRes: Int,
        val isFileCategory: Boolean
    ) {
        ROUTES("routes", R.string.car_backup_cat_routes, R.string.car_backup_cat_routes_desc, true),
        ROUTING_DATA("routing", R.string.car_backup_cat_routing, R.string.car_backup_cat_routing_desc, true),
        MAPS("maps", R.string.car_backup_cat_maps, R.string.car_backup_cat_maps_desc, true),
        PLACES("places", R.string.car_backup_cat_places, R.string.car_backup_cat_places_desc, false),
        NAV_SETTINGS("nav_settings", R.string.car_backup_cat_nav_settings, R.string.car_backup_cat_nav_settings_desc, false),
        LAUNCHER("launcher", R.string.car_backup_cat_launcher, R.string.car_backup_cat_launcher_desc, false),
        DOCK("dock", R.string.car_backup_cat_dock, R.string.car_backup_cat_dock_desc, false),
        WIDGETS("widgets", R.string.car_backup_cat_widgets, R.string.car_backup_cat_widgets_desc, false),
        MUSIC("music", R.string.car_backup_cat_music, R.string.car_backup_cat_music_desc, false),
        TOOLS("tools", R.string.car_backup_cat_tools, R.string.car_backup_cat_tools_desc, false);

        companion object {
            fun fromId(id: String): BackupCategory? = values().firstOrNull { it.id == id }
        }
    }

    data class CategorySummary(
        val category: BackupCategory,
        val fileCount: Int = 0,
        val totalBytes: Long = 0L,
        val itemCount: Int = 0,
        val isAvailable: Boolean = true
    ) {
        fun formatSummary(context: Context): String {
            return if (fileCount > 0) {
                context.getString(R.string.car_backup_count_files, fileCount, formatFileSize(totalBytes))
            } else if (itemCount > 0) {
                context.getString(R.string.car_backup_count_items, itemCount)
            } else {
                ""
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    // Tercih dosyalari ve anahtarlarinin semasi (Sadece Launcher bilesenleri)
    val schema: Map<String, Map<String, String>> = mapOf(
        "vela_car_launcher_prefs" to mapOf(
            "car_launcher_enabled" to "Boolean", "car_launcher_immersive_mode" to "Boolean",
            "car_launcher_status_bar" to "Boolean", "car_launcher_dock_position" to "String",
            "car_launcher_dock_size" to "Int", "widget_panel_position" to "String",
            "car_launcher_expansion_behavior" to "String", "widget_panel_width_percent" to "Float",
            "widget_panel_height_portrait" to "Float", "car_launcher_auto_play_music" to "Boolean",
            "car_launcher_visualizer_type" to "Int", "car_launcher_visualizer_type_large" to "Int",
            "car_launcher_visualizer_fps" to "Int", "car_launcher_ambiance_visualizer" to "Boolean",
            "car_launcher_music_app" to "String", "car_launcher_night_dim_mode" to "Boolean",
            "car_launcher_night_dim_level" to "Float", "car_launcher_desktop_mode" to "Boolean",
            "car_launcher_desktop_in_mode_cycle" to "Boolean", "car_launcher_startup_screen" to "String",
            "car_launcher_floating_button_mode" to "String", "car_launcher_floating_button_size" to "Int",
            "car_launcher_weather_enabled" to "Boolean", "car_launcher_equalizer_app" to "String",
            "car_launcher_floating_button_x" to "Int", "car_launcher_floating_button_y" to "Int"
        ),
        "car_launcher_app_dock" to mapOf("shortcuts" to "String"),
        "vela_car_launcher_widgets" to mapOf("widget_config" to "String"),
        "vela_music_focus" to mapOf("auto_follow" to "Boolean", "startup_source" to "String", "bt_wait_seconds" to "Int"),
        "vela_music_playlists" to mapOf("library" to "String"),
        "vela_launcher_tools" to mapOf(
            "startup_app" to "String", "text_scale" to "Float",
            "antenna_from_lat" to "String", "antenna_from_lon" to "String", "antenna_from_alt" to "String",
            "antenna_to_lat" to "String", "antenna_to_lon" to "String", "antenna_to_alt" to "String"
        )
    )

    private fun getCategoryPrefFiles(category: BackupCategory): List<String> = when (category) {
        BackupCategory.LAUNCHER -> listOf("vela_car_launcher_prefs")
        BackupCategory.DOCK -> listOf("car_launcher_app_dock")
        BackupCategory.WIDGETS -> listOf("vela_car_launcher_widgets")
        BackupCategory.MUSIC -> listOf("vela_music_playlists", "vela_music_focus")
        BackupCategory.TOOLS -> listOf("vela_launcher_tools")
        BackupCategory.NAV_SETTINGS -> listOf("vela_settings")
        BackupCategory.PLACES -> listOf("vela_saved", "vela_lists", "vela_shortcuts", "vela_recents", "vela_recent_places")
        else -> emptyList()
    }

    private fun getCategoryDirectories(context: Context, category: BackupCategory): List<File> = when (category) {
        BackupCategory.ROUTES -> listOf(File(context.filesDir, "trips"))
        BackupCategory.ROUTING_DATA -> listOf(File(context.filesDir, "obf"))
        BackupCategory.MAPS -> listOf(
            File(context.filesDir, "basemap"),
            File(context.filesDir, "sprites"),
            File(context.filesDir, "glyphs"),
            File(context.filesDir, "overlays")
        )
        BackupCategory.PLACES -> listOf(
            File(context.filesDir, "places"),
            File(context.filesDir, "poipacks")
        )
        else -> emptyList()
    }

    /**
     * Cihazdaki mevcut verileri kategorilere gore tarar ve her kategorinin durumunu dondurur.
     */
    fun scanDeviceCategories(context: Context): List<CategorySummary> {
        return BackupCategory.values().map { cat ->
            var fileCount = 0
            var totalBytes = 0L
            var itemCount = 0

            // Dosya tabanli kategoriler
            val dirs = getCategoryDirectories(context, cat)
            for (dir in dirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile && isAllowedBackupFile(it) }.forEach { f ->
                        fileCount++
                        totalBytes += f.length()
                    }
                }
            }

            // Tercih tabanli kategoriler
            val prefFiles = getCategoryPrefFiles(cat)
            for (prefName in prefFiles) {
                val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                itemCount += prefs.all.size
            }

            CategorySummary(
                category = cat,
                fileCount = fileCount,
                totalBytes = totalBytes,
                itemCount = itemCount,
                isAvailable = (fileCount > 0 || itemCount > 0 || !cat.isFileCategory)
            )
        }
    }

    private fun isAllowedBackupFile(file: File): Boolean {
        val name = file.name
        if (name.startsWith(".") || name.endsWith(".tmp") || name.endsWith(".staging") ||
            name.endsWith("-wal") || name.endsWith("-shm") || name.endsWith("-journal")) {
            return false
        }
        return true
    }

    /**
     * Secilen kategorileri ZIP olarak stream eder.
     */
    fun exportZip(
        context: Context,
        selectedCategories: Set<BackupCategory>,
        outStream: OutputStream
    ) {
        val zipOut = ZipOutputStream(outStream.buffered(BUFFER_SIZE))
        try {
            // 1. manifest.json yaz
            val manifestJson = JSONObject().apply {
                put("format", "vela-modular-backup")
                put("version", 2)
                put("created", Instant.now().toString())
                put("categories", JSONArray().apply { selectedCategories.forEach { put(it.id) } })
            }
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Tercih kategorilerini yaz
            val buffer = ByteArray(BUFFER_SIZE)
            for (cat in selectedCategories) {
                val prefFiles = getCategoryPrefFiles(cat)
                for (prefName in prefFiles) {
                    val prefsJson = exportSinglePrefFile(context, prefName)
                    if (prefsJson.length() > 0) {
                        zipOut.putNextEntry(ZipEntry("preferences/$prefName.json"))
                        zipOut.write(prefsJson.toString(2).toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }

                // 3. Dosya kategorilerini yaz
                val dirs = getCategoryDirectories(context, cat)
                for (dir in dirs) {
                    if (dir.exists() && dir.isDirectory) {
                        val baseDirName = dir.name
                        dir.walkTopDown().filter { it.isFile && isAllowedBackupFile(it) }.forEach { file ->
                            val relativePath = file.relativeTo(dir).invariantSeparatorsPath
                            val entryPath = "files/$baseDirName/$relativePath"
                            zipOut.putNextEntry(ZipEntry(entryPath))
                            file.inputStream().buffered(BUFFER_SIZE).use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    zipOut.write(buffer, 0, read)
                                }
                            }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
            zipOut.finish()
            zipOut.flush()
        } finally {
            zipOut.close()
        }
    }

    private fun exportSinglePrefFile(context: Context, prefName: String): JSONObject {
        val keys = schema[prefName] ?: return JSONObject()
        val values = JSONObject()
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            val type = keys[key] ?: return@forEach
            if (value != null) {
                values.put(key, JSONObject().put("type", type).put("value", value))
            }
        }
        if (prefName == "vela_car_launcher_widgets" && values.has("widget_config")) {
            val entry = values.getJSONObject("widget_config")
            val raw = entry.optString("value", "[]")
            val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
            val portable = JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (!item.optString("typeId", "").startsWith("system:")) {
                    portable.put(item)
                }
            }
            entry.put("value", portable.toString())
        }
        return values
    }

    sealed class BackupPackage {
        data class ModularZip(
            val manifestCategories: Set<BackupCategory>,
            val summaries: List<CategorySummary>,
            val zipData: ByteArray
        ) : BackupPackage()

        data class LegacyJson(
            val jsonText: String,
            val summaries: List<CategorySummary>
        ) : BackupPackage()
    }

    /**
     * Secilen dosyanin (ZIP veya JSON) icerigini inceleyip hangi kategorilerin geri yuklenebilecegini cikarir.
     */
    fun inspectBackup(context: Context, inStream: InputStream): BackupPackage {
        val rawBytes = inStream.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                // Bounded transfer: max 1.5 GB limit
                if (total > 1536L * 1024 * 1024) throw IllegalStateException("Backup file too large")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }

        // ZIP magic bytes kontrolu: 0x50, 0x4B, 0x03, 0x04
        val isZip = rawBytes.size >= 4 &&
                rawBytes[0] == 0x50.toByte() &&
                rawBytes[1] == 0x4B.toByte() &&
                rawBytes[2] == 0x03.toByte() &&
                rawBytes[3] == 0x04.toByte()

        if (isZip) {
            val categoriesInZip = mutableSetOf<BackupCategory>()
            val fileCounts = mutableMapOf<BackupCategory, Int>()
            val byteCounts = mutableMapOf<BackupCategory, Long>()
            val itemCounts = mutableMapOf<BackupCategory, Int>()

            ZipInputStream(rawBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "manifest.json") {
                        val text = zip.reader(Charsets.UTF_8).readText()
                        val root = runCatching { JSONObject(text) }.getOrNull()
                        val cats = root?.optJSONArray("categories")
                        if (cats != null) {
                            for (i in 0 until cats.length()) {
                                BackupCategory.fromId(cats.getString(i))?.let { categoriesInZip.add(it) }
                            }
                        }
                    } else if (name.startsWith("files/")) {
                        val parts = name.split('/')
                        if (parts.size >= 3) {
                            val dirName = parts[1]
                            val cat = when (dirName) {
                                "trips" -> BackupCategory.ROUTES
                                "obf" -> BackupCategory.ROUTING_DATA
                                "basemap", "sprites", "glyphs", "overlays" -> BackupCategory.MAPS
                                "places", "poipacks" -> BackupCategory.PLACES
                                else -> null
                            }
                            if (cat != null) {
                                categoriesInZip.add(cat)
                                fileCounts[cat] = (fileCounts[cat] ?: 0) + 1
                                byteCounts[cat] = (byteCounts[cat] ?: 0L) + entry.size.coerceAtLeast(0L)
                            }
                        }
                    } else if (name.startsWith("preferences/") && name.endsWith(".json")) {
                        val prefName = name.removePrefix("preferences/").removeSuffix(".json")
                        val cat = when (prefName) {
                            "vela_car_launcher_prefs" -> BackupCategory.LAUNCHER
                            "car_launcher_app_dock" -> BackupCategory.DOCK
                            "vela_car_launcher_widgets" -> BackupCategory.WIDGETS
                            "vela_music_playlists", "vela_music_focus" -> BackupCategory.MUSIC
                            "vela_launcher_tools" -> BackupCategory.TOOLS
                            "vela_settings" -> BackupCategory.NAV_SETTINGS
                            "vela_saved", "vela_lists", "vela_shortcuts", "vela_recents", "vela_recent_places" -> BackupCategory.PLACES
                            else -> null
                        }
                        if (cat != null) {
                            categoriesInZip.add(cat)
                            itemCounts[cat] = (itemCounts[cat] ?: 0) + 1
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val summaries = categoriesInZip.map { cat ->
                CategorySummary(
                    category = cat,
                    fileCount = fileCounts[cat] ?: 0,
                    totalBytes = byteCounts[cat] ?: 0L,
                    itemCount = itemCounts[cat] ?: 0,
                    isAvailable = true
                )
            }.sortedBy { it.category.ordinal }

            return BackupPackage.ModularZip(categoriesInZip, summaries, rawBytes)
        } else {
            // Legacy JSON kontrolu
            val text = rawBytes.toString(Charsets.UTF_8)
            val jsonRoot = JSONObject(text)
            require(jsonRoot.optString("format") == "vela-launcher" || jsonRoot.has("groups")) {
                "Gecersiz yedek dosyasi"
            }
            val groups = jsonRoot.optJSONObject("groups") ?: JSONObject()
            val categoriesInJson = mutableSetOf<BackupCategory>()

            if (groups.has("vela_car_launcher_prefs")) categoriesInJson.add(BackupCategory.LAUNCHER)
            if (groups.has("car_launcher_app_dock")) categoriesInJson.add(BackupCategory.DOCK)
            if (groups.has("vela_car_launcher_widgets")) categoriesInJson.add(BackupCategory.WIDGETS)
            if (groups.has("vela_music_playlists") || groups.has("vela_music_focus")) categoriesInJson.add(BackupCategory.MUSIC)
            if (groups.has("vela_launcher_tools")) categoriesInJson.add(BackupCategory.TOOLS)

            val summaries = categoriesInJson.map { cat ->
                CategorySummary(category = cat, itemCount = 1, isAvailable = true)
            }.sortedBy { it.category.ordinal }

            return BackupPackage.LegacyJson(text, summaries)
        }
    }

    /**
     * Secilen kategorileri ZIP paketinden guvenle geri yukler.
     */
    fun restoreModularZip(
        context: Context,
        zipData: ByteArray,
        categoriesToRestore: Set<BackupCategory>
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        ZipInputStream(zipData.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name.startsWith("files/")) {
                    val parts = name.split('/')
                    if (parts.size >= 3) {
                        val dirName = parts[1]
                        val cat = when (dirName) {
                            "trips" -> BackupCategory.ROUTES
                            "obf" -> BackupCategory.ROUTING_DATA
                            "basemap", "sprites", "glyphs", "overlays" -> BackupCategory.MAPS
                            "places", "poipacks" -> BackupCategory.PLACES
                            else -> null
                        }
                        if (cat != null && cat in categoriesToRestore) {
                            val relativePath = parts.drop(1).joinToString("/")
                            val destFile = File(context.filesDir, relativePath)
                            // Guvenlik kontrolu (path traversal engelleme)
                            require(destFile.canonicalPath.startsWith(context.filesDir.canonicalPath))
                            destFile.parentFile?.mkdirs()
                            destFile.outputStream().buffered(BUFFER_SIZE).use { out ->
                                while (true) {
                                    val r = zip.read(buffer)
                                    if (r <= 0) break
                                    out.write(buffer, 0, r)
                                }
                            }
                        }
                    }
                } else if (name.startsWith("preferences/") && name.endsWith(".json")) {
                    val prefName = name.removePrefix("preferences/").removeSuffix(".json")
                    val cat = when (prefName) {
                        "vela_car_launcher_prefs" -> BackupCategory.LAUNCHER
                        "car_launcher_app_dock" -> BackupCategory.DOCK
                        "vela_car_launcher_widgets" -> BackupCategory.WIDGETS
                        "vela_music_playlists", "vela_music_focus" -> BackupCategory.MUSIC
                        "vela_launcher_tools" -> BackupCategory.TOOLS
                        "vela_settings" -> BackupCategory.NAV_SETTINGS
                        "vela_saved", "vela_lists", "vela_shortcuts", "vela_recents", "vela_recent_places" -> BackupCategory.PLACES
                        else -> null
                    }
                    if (cat != null && cat in categoriesToRestore) {
                        val jsonText = zip.reader(Charsets.UTF_8).readText()
                        val values = runCatching { JSONObject(jsonText) }.getOrNull()
                        if (values != null) {
                            applyPreferences(context, prefName, values)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Eski JSON formatindaki yedegi secilen kategorilere gore geri yukler.
     */
    fun restoreLegacyJson(
        context: Context,
        jsonText: String,
        categoriesToRestore: Set<BackupCategory>
    ) {
        val root = JSONObject(jsonText)
        val groups = root.optJSONObject("groups") ?: return
        for (cat in categoriesToRestore) {
            val prefFiles = getCategoryPrefFiles(cat)
            for (prefName in prefFiles) {
                val values = groups.optJSONObject(prefName) ?: continue
                applyPreferences(context, prefName, values)
            }
        }
    }

    private fun applyPreferences(context: Context, prefName: String, values: JSONObject) {
        val keys = schema[prefName] ?: return
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        keys.keys.forEach { editor.remove(it) }

        values.keys().forEach { key ->
            val type = keys[key] ?: return@forEach
            val entry = values.optJSONObject(key) ?: return@forEach
            when (type) {
                "Boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "Int" -> editor.putInt(key, entry.optInt("value"))
                "Long" -> editor.putLong(key, entry.optLong("value"))
                "Float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "String" -> editor.putString(key, entry.optString("value"))
            }
        }

        // Widget bilesenlerinde mevcut cihaza bagli sistem widget'larini koru
        if (prefName == "vela_car_launcher_widgets") {
            val existing = runCatching { JSONArray(prefs.getString("widget_config", "[]")) }.getOrDefault(JSONArray())
            val restored = runCatching { JSONArray(values.optJSONObject("widget_config")?.optString("value", "[]") ?: "[]") }.getOrDefault(JSONArray())
            for (i in 0 until existing.length()) {
                val item = existing.getJSONObject(i)
                if (item.optString("typeId", "").startsWith("system:")) {
                    restored.put(item)
                }
            }
            editor.putString("widget_config", restored.toString())
        }

        editor.commit()
    }

    // Geriye donuk tek dosya API (Gerekirse)
    fun export(context: Context): String {
        val groups = JSONObject()
        schema.forEach { (file, keys) ->
            val values = JSONObject()
            val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
            prefs.all.forEach { (key, value) ->
                val type = keys[key] ?: return@forEach
                if (value != null) values.put(key, JSONObject().put("type", type).put("value", value))
            }
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
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES)
        val root = JSONObject(text)
        require(root.getString("format") == "vela-launcher" && root.get("version") == 1)
        val groups = root.getJSONObject("groups")
        val groupNames = groups.keys().asSequence().toSet()
        require(groupNames == schema.keys || groupNames == schema.keys - "vela_music_focus")
        if (!groups.has("vela_music_focus")) groups.put("vela_music_focus", JSONObject())
        return groups
    }

    @Synchronized
    fun restore(context: Context, text: String) {
        val allCats = BackupCategory.values().toSet()
        restoreLegacyJson(context, text, allCats)
    }

    fun read(context: Context, uri: android.net.Uri): String =
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(out.size() + count <= MAX_JSON_BYTES)
                out.write(buffer, 0, count)
            }
            out.toString("UTF-8")
        }
}

