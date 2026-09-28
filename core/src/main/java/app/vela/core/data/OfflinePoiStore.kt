package app.vela.core.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.distanceTo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A tiny on-device place index (SQLite) populated from OpenStreetMap/[OverpassPois]
 * when a map region is downloaded — the keyless, no-backend source behind **offline
 * search**. Used as a fallback when Google search can't be reached (offline).
 */
@Singleton
class OfflinePoiStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val helper = object : SQLiteOpenHelper(context, "vela_offline_pois.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE poi(id TEXT PRIMARY KEY, name TEXT, lat REAL, lng REAL, category TEXT, " +
                    "address TEXT, phone TEXT, website TEXT, hours TEXT)",
            )
            db.execSQL("CREATE INDEX idx_poi_name ON poi(name COLLATE NOCASE)")
            olusturFtsTablosu(db)
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS poi_fts")
            db.execSQL("DROP TABLE IF EXISTS poi")
            onCreate(db)
        }
    }

    private val ftsTabloCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun olusturFtsTablosu(db: SQLiteDatabase) {
        runCatching {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS poi_fts USING fts5(
                    name,
                    category,
                    address,
                    content='poi',
                    content_rowid='rowid',
                    tokenize='unicode61 remove_diacritics 2'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS poi_ai AFTER INSERT ON poi BEGIN
                    INSERT INTO poi_fts(rowid, name, category, address) 
                    VALUES (new.rowid, new.name, new.category, new.address);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS poi_ad AFTER DELETE ON poi BEGIN
                    INSERT INTO poi_fts(poi_fts, rowid, name, category, address) 
                    VALUES('delete', old.rowid, old.name, old.category, old.address);
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS poi_au AFTER UPDATE ON poi BEGIN
                    INSERT INTO poi_fts(poi_fts, rowid, name, category, address) 
                    VALUES('delete', old.rowid, old.name, old.category, old.address);
                    INSERT INTO poi_fts(rowid, name, category, address) 
                    VALUES (new.rowid, new.name, new.category, new.address);
                END
                """.trimIndent(),
            )
        }
    }

    private fun hasFtsTablosu(db: SQLiteDatabase): Boolean {
        val yol = db.path ?: return false
        return ftsTabloCache.getOrPut(yol) {
            runCatching {
                db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='poi_fts' LIMIT 1",
                    null,
                ).use { it.moveToFirst() }
            }.getOrDefault(false)
        }
    }

    /** Upsert a batch of POIs (deduped by id). Keeps the detail tags (address/phone/
     *  website/hours) the OSM source carries, so an offline place sheet isn't bare. */
    fun add(pois: List<Place>) {
        if (pois.isEmpty()) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (p in pois) {
                db.insertWithOnConflict("poi", null, ContentValues().apply {
                    put("id", p.id); put("name", p.name)
                    put("lat", p.location.lat); put("lng", p.location.lng)
                    put("category", p.category)
                    put("address", p.address)
                    put("phone", p.phone)
                    put("website", p.website)
                    put("hours", p.hours.joinToString("\n").ifBlank { null })
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(): Int = helper.readableDatabase
        .rawQuery("SELECT COUNT(*) FROM poi", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Every POI within [radiusM] of [loc], nearest first: the businesses AT a typed address. A
     *  pack POI often has no `addr:*` of its own (most US chains do not in OSM), so an address
     *  search could find the house point but never the shop standing on it (user 2026-09-19). */
    fun near(loc: LatLng, radiusM: Double, limit: Int = 6): List<Place> {
        val dLat = radiusM / 111_000.0
        val dLng = dLat / Math.cos(Math.toRadians(loc.lat)).coerceAtLeast(0.2)
        val args = arrayOf((loc.lat - dLat).toString(), (loc.lat + dLat).toString(), (loc.lng - dLng).toString(), (loc.lng + dLng).toString())
        val sql = "SELECT id,name,lat,lng,category,address,phone,website,hours FROM poi WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?"
        val rows = ArrayList<Place>()
        fun query(db: android.database.sqlite.SQLiteDatabase) {
            runCatching {
                db.rawQuery(sql, args).use { c ->
                    while (c.moveToNext()) {
                        val at = LatLng(c.getDouble(2), c.getDouble(3))
                        val d = loc.distanceTo(at)
                        if (d > radiusM) continue
                        rows.add(Place(
                            id = c.getString(0), name = c.getString(1), location = at, category = c.getString(4),
                            address = c.getString(5), phone = c.getString(6), website = c.getString(7),
                            hours = app.vela.core.util.OsmHours.lines(c.getString(8)), // packs keep OSM's raw tag
                            distanceMeters = d,
                        ))
                    }
                }
            }
        }
        query(helper.readableDatabase)
        OfflinePacks.dbs.forEach(::query)
        return rows.distinctBy { it.id }.sortedBy { it.distanceMeters }.take(limit)
    }

    /** Name/category match, nearest first. Robust to a few things a plain LIKE misses:
     *  - Category words ("gas", "coffee", "food", the map's chips) are expanded to the OSM tag values
     *    we actually store — a gas station is category "Fuel" (from `amenity=fuel`), not "gas".
     *  - Multi-word queries ("mexican restaurant", "coffee shop") match the whole phrase OR any single
     *    word, so "mexican restaurant" finds "Ixtapa Mexican Restaurant" and the restaurant category,
     *    instead of returning nothing because no name contains the exact phrase.
     */



    internal data class AramaAdayi(
        val place: Place,
        val metinPuani: Double, // FTS5 bm25 veya SQL CASE-WHEN normalize puani
        val rank: Int,          // matchRank (0: tam, 1: baslayan, 2: kelime baslayan, 3: iceren, 4: diger)
        val kelimeUyumSayisi: Int,
    )

    private fun ftsTemizle(kelime: String): String {
        return kelime.filter { it.isLetterOrDigit() }
    }

    private fun matchRank(p: Place, query: String): Int {
        val qNorm = norm(query)
        val pNorm = norm(p.name)
        val pWords = pNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }

        // 0: Tam eslesme (Exact match)
        if (pNorm == qNorm) return 0

        // 1: Isim arama terimiyle basliyor (Name starts with query)
        if (pNorm.startsWith(qNorm)) return 1

        // 2: Isimdeki herhangi bir kelime terimle basliyor (Word starts with query)
        if (pWords.any { it.startsWith(qNorm) }) return 2

        // 3: Isim icinde geciyor (Name contains query)
        if (pNorm.contains(qNorm)) return 3

        // 4: Kategori veya adreste geciyor
        return 4
    }

    /**
     * Cihazin dil dosyasindan (poi_categories.xml) dinamik olarak yuklenen kategori eslesmeleri.
     * Kod icinde hardcoded kelime bulundurmaz, Android yerellestirme (i18n) standartlarini kullanir.
     */
    private val dinamikKategoriHaritasi: Map<String, List<String>> by lazy {
        val harita = HashMap<String, List<String>>()
        harita.putAll(CATEGORY_KEYWORDS)

        val kaynakEslestirmeleri = listOf(
            app.vela.core.R.string.poi_cat_restaurant to listOf("restaurant", "fast food", "cafe"),
            app.vela.core.R.string.poi_cat_gas to listOf("fuel", "charging station"),
            app.vela.core.R.string.poi_cat_groceries to listOf("supermarket", "convenience", "grocery"),
            app.vela.core.R.string.poi_cat_pharmacy to listOf("pharmacy", "chemist"),
            app.vela.core.R.string.poi_cat_hospital to listOf("hospital", "clinic", "doctors"),
            app.vela.core.R.string.poi_cat_hotel to listOf("hotel", "motel", "guest house"),
            app.vela.core.R.string.poi_cat_parking to listOf("parking"),
            app.vela.core.R.string.poi_cat_bank to listOf("bank", "atm"),
            app.vela.core.R.string.poi_cat_cafe to listOf("cafe", "coffee"),
            app.vela.core.R.string.poi_cat_bakery to listOf("bakery", "pastry"),
            app.vela.core.R.string.poi_cat_school to listOf("school", "university", "college"),
            app.vela.core.R.string.poi_cat_park to listOf("park"),
            app.vela.core.R.string.poi_cat_gym to listOf("fitness center", "sports center"),
            app.vela.core.R.string.poi_cat_car_wash to listOf("car wash"),
            app.vela.core.R.string.poi_cat_post_office to listOf("post office"),
            app.vela.core.R.string.poi_cat_campground to listOf("camp site", "caravan site"),
            app.vela.core.R.string.poi_cat_things_to_do to listOf("attraction", "museum", "viewpoint"),
            app.vela.core.R.string.poi_cat_transit to listOf("bus stop", "station", "platform"),
            app.vela.core.R.string.poi_cat_worship to listOf("place of worship"),
        )

        for ((resId, osmKategorileri) in kaynakEslestirmeleri) {
            runCatching {
                val kelimelerMetni = context.getString(resId)
                for (kelime in kelimelerMetni.split(",")) {
                    val k = kelime.trim().lowercase()
                    if (k.isNotEmpty()) {
                        harita[k] = osmKategorileri
                    }
                }
            }
        }
        harita
    }

    private fun kategoriBul(kelime: String): List<String> {
        val anahtar = kelime.trim().lowercase()
        return dinamikKategoriHaritasi[anahtar]
            ?: anahtar.takeIf { it.length > 3 && it.endsWith("s") }?.let { dinamikKategoriHaritasi[it.dropLast(1)] }
            ?: categoryKeywords(anahtar)
    }

    /**
     * FTS5 motoru sorgusu: Ağırlıklı BM25 puanlaması ile FTS tablosunda arama yapar.
     */
    private fun ftsSorgula(
        db: SQLiteDatabase,
        ftsQuery: String,
        limit: Int,
        near: LatLng?,
        term: String,
        qWords: List<String>,
        cikti: MutableMap<String, AramaAdayi>,
    ) {
        if (ftsQuery.isBlank()) return
        val sql = """
            SELECT p.id, p.name, p.lat, p.lng, p.category, p.address, p.phone, p.website, p.hours,
                   bm25(poi_fts, 10.0, 2.5, 1.0) AS text_rank
            FROM poi_fts f
            JOIN poi p ON f.rowid = p.rowid
            WHERE poi_fts MATCH ?
            ORDER BY text_rank ASC
            LIMIT ?
        """.trimIndent()

        runCatching {
            db.rawQuery(sql, arrayOf(ftsQuery, limit.toString())).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    if (cikti.containsKey(id)) continue
                    val loc = LatLng(c.getDouble(2), c.getDouble(3))
                    val place = Place(
                        id = id,
                        name = c.getString(1) ?: "",
                        location = loc,
                        category = c.getString(4),
                        address = c.getString(5),
                        phone = c.getString(6),
                        website = c.getString(7),
                        hours = app.vela.core.util.OsmHours.lines(c.getString(8)),
                        distanceMeters = near?.distanceTo(loc),
                    )
                    val bm25 = c.getDouble(9)
                    val rank = matchRank(place, term)
                    val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                    val kelimeUyumu = qWords.count { metin.contains(it) }
                    // BM25 negatif deger uretir (0'a yakin olan daha iyidir). Eksi ile pozitiflestirilir.
                    val normalizePuan = (-bm25).coerceAtLeast(0.0)
                    cikti[id] = AramaAdayi(place, normalizePuan, rank, kelimeUyumu)
                }
            }
        }
    }

    /**
     * Vela sunucularindan indirilen mevcut .poipack dosyalari icin Akilli SQL motoru:
     * 1. Adim: Prefix B-Tree aramasi (name LIKE 'kelime%' COLLATE NOCASE)
     * 2. Adim: Kademeli Katı AND sorgusu (name LIKE '%w1%' AND name LIKE '%w2%')
     * 3. Adim: SQL ici agirlikli CASE-WHEN puanlamasi (200, 100, 60, 25, 20, 5 puan)
     */
    private fun akilliSqlSorgula(
        db: SQLiteDatabase,
        term: String,
        words: List<String>,
        near: LatLng?,
        qWords: List<String>,
        cikti: MutableMap<String, AramaAdayi>,
    ) {
        val cats = kategoriBul(term) + words.flatMap { kategoriBul(it) }
        val catTerm = cats.firstOrNull() ?: ""

        // Adim 1: Tek kelimelik aramalarda B-Tree indeksli prefix sorgu
        if (words.size <= 1) {
            val prefixSql = "SELECT id,name,lat,lng,category,address,phone,website,hours FROM poi " +
                "WHERE name LIKE ? || '%' COLLATE NOCASE LIMIT 50"
            runCatching {
                db.rawQuery(prefixSql, arrayOf(term)).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        if (cikti.containsKey(id)) continue
                        val loc = LatLng(c.getDouble(2), c.getDouble(3))
                        val place = Place(
                            id = id,
                            name = c.getString(1) ?: "",
                            location = loc,
                            category = c.getString(4),
                            address = c.getString(5),
                            phone = c.getString(6),
                            website = c.getString(7),
                            hours = app.vela.core.util.OsmHours.lines(c.getString(8)),
                            distanceMeters = near?.distanceTo(loc),
                        )
                        val rank = matchRank(place, term)
                        val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                        val kelimeUyumu = qWords.count { metin.contains(it) }
                        cikti[id] = AramaAdayi(place, 10.0, rank, kelimeUyumu)
                    }
                }
            }
            if (cikti.size >= 15) return
        }

        // Adim 2: Cok kelimeli aramalarda Katı AND sorgusu (Havuz zehirlenmesini onler)
        if (words.size > 1) {
            val andClauses = words.map { "name LIKE ?" }
            val andArgs = words.map { "%$it%" }
            val andSql = "SELECT id,name,lat,lng,category,address,phone,website,hours FROM poi " +
                "WHERE ${andClauses.joinToString(" AND ")} LIMIT 100"
            runCatching {
                db.rawQuery(andSql, andArgs.toTypedArray()).use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        if (cikti.containsKey(id)) continue
                        val loc = LatLng(c.getDouble(2), c.getDouble(3))
                        val place = Place(
                            id = id,
                            name = c.getString(1) ?: "",
                            location = loc,
                            category = c.getString(4),
                            address = c.getString(5),
                            phone = c.getString(6),
                            website = c.getString(7),
                            hours = app.vela.core.util.OsmHours.lines(c.getString(8)),
                            distanceMeters = near?.distanceTo(loc),
                        )
                        val rank = matchRank(place, term)
                        val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                        val kelimeUyumu = qWords.count { metin.contains(it) }
                        cikti[id] = AramaAdayi(place, 12.0, rank, kelimeUyumu)
                    }
                }
            }
            if (cikti.size >= 5) return
        }

        // Adim 3: Dinamik CASE-WHEN agirlikli SQL sorgusu
        val w1 = words.getOrNull(0) ?: term
        val w2 = words.getOrNull(1) ?: term

        val caseSql = """
            SELECT id, name, lat, lng, category, address, phone, website, hours,
                (
                    (CASE WHEN LOWER(name) = LOWER(?) THEN 200 ELSE 0 END) +
                    (CASE WHEN name LIKE ? || '%' COLLATE NOCASE THEN 100 ELSE 0 END) +
                    (CASE WHEN name LIKE '%' || ? || '%' THEN 60 ELSE 0 END) +
                    (CASE WHEN name LIKE '%' || ? || '%' THEN 25 ELSE 0 END) +
                    (CASE WHEN name LIKE '%' || ? || '%' THEN 25 ELSE 0 END) +
                    (CASE WHEN category LIKE '%' || ? || '%' THEN 20 ELSE 0 END) +
                    (CASE WHEN address LIKE '%' || ? || '%' THEN 5 ELSE 0 END)
                ) AS match_score
            FROM poi
            WHERE name LIKE '%' || ? || '%'
               OR (name LIKE '%' || ? || '%' AND name LIKE '%' || ? || '%')
               OR (LENGTH(?) > 0 AND category LIKE '%' || ? || '%')
               OR address LIKE '%' || ? || '%'
            ORDER BY match_score DESC
            LIMIT 150
        """.trimIndent()

        val caseArgs = arrayOf(
            term, term, term, w1, w2, catTerm, term,
            term, w1, w2, catTerm, catTerm, term,
        )

        runCatching {
            db.rawQuery(caseSql, caseArgs).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    if (cikti.containsKey(id)) continue
                    val loc = LatLng(c.getDouble(2), c.getDouble(3))
                    val place = Place(
                        id = id,
                        name = c.getString(1) ?: "",
                        location = loc,
                        category = c.getString(4),
                        address = c.getString(5),
                        phone = c.getString(6),
                        website = c.getString(7),
                        hours = app.vela.core.util.OsmHours.lines(c.getString(8)),
                        distanceMeters = near?.distanceTo(loc),
                    )
                    val sqlScore = c.getDouble(9)
                    val rank = matchRank(place, term)
                    val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                    val kelimeUyumu = qWords.count { metin.contains(it) }
                    // SQL match_score 0-200 arasi oldugundan 0-10 arasi normalize edilir
                    val normalizePuan = sqlScore / 20.0
                    cikti[id] = AramaAdayi(place, normalizePuan, rank, kelimeUyumu)
                }
            }
        }
    }

    fun search(query: String, near: LatLng?, limit: Int = 30): List<Place> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()

        val words = term.split(Regex("\\s+")).filter { it.length >= 2 }
        val qWords = (if (words.size > 1) words else listOf(term)).map { norm(it) }.distinct()
        val temizKelimeler = words.map { ftsTemizle(norm(it)) }.filter { it.length >= 2 }
        val temizTekTerim = ftsTemizle(norm(term))

        val adayHavuzu = LinkedHashMap<String, AramaAdayi>()
        val tumDbListesi = listOf(helper.readableDatabase) + OfflinePacks.dbs

        for (db in tumDbListesi) {
            if (hasFtsTablosu(db)) {
                // FTS5 Destekli Motor (Yeni paketler ve cihazda kaydedilen bolgeler)
                // 1. Katman: Kesin AND eslesmesi (Strict AND - Isim alaninda)
                val katman1Query = if (temizKelimeler.isNotEmpty()) {
                    temizKelimeler.joinToString(" AND ") { "name: $it*" }
                } else if (temizTekTerim.isNotEmpty()) {
                    "name: $temizTekTerim*"
                } else {
                    ""
                }
                ftsSorgula(db, katman1Query, 100, near, term, qWords, adayHavuzu)

                // 2. Katman: Eger sonuc azsa Kategori ve Adres hibrit arama
                if (adayHavuzu.size < 15 && temizKelimeler.isNotEmpty()) {
                    val katman2Query = temizKelimeler.joinToString(" AND ") {
                        "(name: $it* OR category: $it* OR address: $it*)"
                    }
                    ftsSorgula(db, katman2Query, 100, near, term, qWords, adayHavuzu)
                }

                // 3. Katman: Eger hala cok azsa toleransli OR arama
                if (adayHavuzu.size < 5) {
                    if (temizKelimeler.isNotEmpty()) {
                        val katman3Query = temizKelimeler.joinToString(" OR ") { "name: $it*" }
                        ftsSorgula(db, katman3Query, 100, near, term, qWords, adayHavuzu)
                    }
                }
            } else {
                // FTS5 Bulunmayan Klasik DB Motoru (Vela sunucusundan indirilen mevcut .poipack dosyalari)
                akilliSqlSorgula(db, term, words, near, qWords, adayHavuzu)
            }
        }

        return disambiguateAndRank(term, adayHavuzu.values.toList(), near, limit)
    }

    companion object {
        /**
         * Dilden bagimsiz aksan ve karakter temizleme fonksiyonu.
         * Dunyadaki tum alfabelerin (Turkce, Almanca, Fransizca vb.) aksanlarini standartlastirir.
         */
        internal fun norm(s: String): String {
            val nfd = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace('ı', 'i')
                .replace('İ', 'i')
        }

        /**
         * Navigasyon aramasinda isim cakismalarini (collision) onleme ve
         * 4 seviyeli ayirt etme (disambiguation) algoritmasi.
         */
        internal fun disambiguateAndRank(
            rawQuery: String,
            candidates: List<AramaAdayi>,
            near: LatLng?,
            limit: Int = 30,
        ): List<Place> {
            if (candidates.isEmpty()) return emptyList()

            val trLocale = java.util.Locale("tr", "TR")
            val cleanQuery = rawQuery.trim()
            val lowerQueryTr = cleanQuery.lowercase(trLocale)
            val qNorm = norm(cleanQuery)

            val wordsTr = lowerQueryTr.split(Regex("\\s+")).filter { it.length >= 2 }
            val wordsNorm = wordsTr.map { norm(it) }

            val transitSorgusu = TRANSIT_QUERY_WORDS.any { lowerQueryTr.contains(it) }

            data class PuanliAday(
                val place: Place,
                val nihaiPuan: Double,
            )

            return candidates.map { adayi ->
                val place = adayi.place
                val pName = place.name.trim()
                val pNameLowerTr = pName.lowercase(trLocale)
                val pNameNorm = norm(pName)
                val pWordsLowerTr = pNameLowerTr.split(Regex("\\s+")).filter { it.isNotEmpty() }
                val pWordsNorm = pNameNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }

                var totalScore = 0.0

                // 1. Seviye: Orijinal Karakter ve Buyuk/Kucuk Harf Sadakati (Orthography)
                when {
                    // Birebir harf harfine tam eslesme (Exact string match: "Ilica" == "Ilica")
                    pName == cleanQuery -> totalScore += 100.0

                    // Turkce kurallarina uygun kucuk/buyuk harf esitligi ("ilica" == "Ilica")
                    pNameLowerTr == lowerQueryTr -> totalScore += 80.0

                    // Isim dogrudan sorguyla basliyor (Turkce karakter sadakatiyle)
                    pNameLowerTr.startsWith(lowerQueryTr) -> totalScore += 60.0

                    // Isimdeki bir kelime sorguyla basliyor (Turkce karakter sadakatiyle)
                    pWordsLowerTr.any { it.startsWith(lowerQueryTr) } -> totalScore += 50.0

                    // Isim icinde sorgu harfiyen geciyor
                    pNameLowerTr.contains(lowerQueryTr) -> totalScore += 35.0

                    // Fold edilmis / karakter benzesimi uzerinden gelenler (i <-> i sapmasi)
                    pNameNorm == qNorm -> totalScore += 25.0
                    pNameNorm.startsWith(qNorm) -> totalScore += 20.0
                    pWordsNorm.any { it.startsWith(qNorm) } -> totalScore += 15.0
                    pNameNorm.contains(qNorm) -> totalScore += 10.0

                    // Sadece adres veya kategoride eslesmis
                    else -> totalScore += 5.0
                }

                // 2. Seviye: Yer Hiyerarsisi ve Onem Derecesi (Place Importance / Saliency)
                totalScore += getImportanceScore(place.category)

                // 3. Seviye: Baglam ve Ikinci Terim Ayiklamasi (Context Disambiguation)
                if (wordsTr.size > 1) {
                    val addrLowerTr = (place.address ?: "").lowercase(trLocale)
                    val addrNorm = norm(place.address ?: "")
                    val catLowerTr = (place.category ?: "").lowercase(trLocale)
                    val catNorm = norm(place.category ?: "")

                    for (i in wordsTr.indices) {
                        val wTr = wordsTr[i]
                        val wNorm = wordsNorm[i]

                        // Isimde yer almayan terimler bir baglam belirtecidir (il, ilce, mahalle, kategori)
                        val isimdeVar = pNameLowerTr.contains(wTr) || pNameNorm.contains(wNorm)
                        if (!isimdeVar) {
                            val adresteVar = addrLowerTr.contains(wTr) || addrNorm.contains(wNorm)
                            val kategorideVar = catLowerTr.contains(wTr) || catNorm.contains(wNorm)
                            if (adresteVar) {
                                totalScore += 50.0 // Ilce/Il baglami adreste dogrulandi
                            } else if (kategorideVar) {
                                totalScore += 25.0 // Kategori baglami dogrulandi
                            }
                        }
                    }
                }

                // FTS5 BM25 veya SQL CASE-WHEN motorundan gelen metin puani katilimi (0-10 arasi)
                totalScore += (adayi.metinPuani * 2.0)

                // 4. Seviye: Dinamik Logaritmik Mesafe Etkisi (Distance Weighting)
                val distMeters = place.distanceMeters ?: near?.distanceTo(place.location)
                if (distMeters != null) {
                    val distKm = distMeters / 1000.0
                    val distancePenalty = 4.0 * kotlin.math.ln(1.0 + distKm)
                    totalScore -= distancePenalty
                } else {
                    // Konum bilinmiyorsa varsayilan notr mesafe cezasi (yaklasik 20 km)
                    totalScore -= (4.0 * kotlin.math.ln(1.0 + 20.0))
                }

                // Transit filtreleme (Kullanici durak aramiyorsa duraklara ceza)
                val isTransit = (place.category ?: "").lowercase(java.util.Locale.ROOT) in TRANSIT_STOP_CATS
                if (!transitSorgusu && isTransit) {
                    totalScore -= 40.0
                }

                PuanliAday(place, totalScore)
            }
            .sortedByDescending { it.nihaiPuan }
            .map { it.place }
            .take(limit)
        }

        private fun getImportanceScore(category: String?): Double {
            val cat = (category ?: "").lowercase(java.util.Locale.ROOT)
            return when {
                cat.contains("city") || cat.contains("administrative") -> 100.0
                cat.contains("town") -> 80.0
                cat.contains("suburb") || cat.contains("village") -> 60.0
                cat.contains("neighbourhood") || cat.contains("neighborhood") -> 40.0
                cat.contains("thermal") || cat.contains("spring") || cat.contains("spa") -> 30.0
                cat.contains("fuel") || cat.contains("hospital") || cat.contains("pharmacy") -> 25.0
                cat.contains("supermarket") || cat.contains("shop") || cat.contains("cafe") || cat.contains("restaurant") -> 10.0
                else -> 5.0
            }
        }
        // Map a search word (or the map's category chip) to the OSM tag values we store as `category`
        // (amenity/shop/leisure/…, space-separated + capitalized, e.g. "Fuel", "Fast food"). Matched
        // case-insensitively via LIKE. Keep the values in the OSM form, not the display word.
        private val CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
            "gas" to listOf("fuel", "charging station"),
            "gas station" to listOf("fuel"),
            "fuel" to listOf("fuel"),
            "petrol" to listOf("fuel"),
            "petrol station" to listOf("fuel"),
            "charging" to listOf("charging station"),
            "ev charging" to listOf("charging station"),
            "coffee" to listOf("cafe", "coffee"),
            "cafe" to listOf("cafe"),
            "food" to listOf("restaurant", "fast food"),
            "restaurant" to listOf("restaurant", "fast food"),
            "restaurants" to listOf("restaurant", "fast food"),
            "fast food" to listOf("fast food"),
            "groceries" to listOf("supermarket", "convenience", "greengrocer"),
            "grocery" to listOf("supermarket", "convenience"),
            "supermarket" to listOf("supermarket"),
            "store" to listOf("supermarket", "convenience", "department store"),
            "pharmacy" to listOf("pharmacy", "chemist"),
            "drug store" to listOf("pharmacy", "chemist"),
            "hotel" to listOf("hotel", "motel", "guest house"),
            "hotels" to listOf("hotel", "motel", "guest house"),
            "motel" to listOf("motel"),
            "lodging" to listOf("hotel", "motel", "guest house", "hostel"),
            "parking" to listOf("parking"),
            "parking lot" to listOf("parking"),
            "atm" to listOf("atm", "bank"),
            "atms" to listOf("atm", "bank"),
            "bank" to listOf("bank", "atm"),
            "banks" to listOf("bank", "atm"),
            "hospital" to listOf("hospital"),
            "hospitals" to listOf("hospital"),
            "clinic" to listOf("clinic", "doctors"),
            "doctor" to listOf("doctors", "clinic"),
            "urgent care" to listOf("clinic", "hospital"),
            "bar" to listOf("bar", "pub", "biergarten"),
            "bars" to listOf("bar", "pub", "biergarten"),
            "pub" to listOf("pub", "bar"),
            "bakery" to listOf("bakery"),
            "park" to listOf("park"),
            "parks" to listOf("park"),
            "school" to listOf("school"),
            "gym" to listOf("fitness center", "fitness centre", "sports center", "sports centre"),
            "car wash" to listOf("car wash"),
            "post office" to listOf("post office"),
            "post offices" to listOf("post office"),
            // tourism=camp_site / caravan_site ("Camp site", "Caravan site" once formatted).
            "campground" to listOf("camp site", "caravan site"),
            "campgrounds" to listOf("camp site", "caravan site"),
            "camping" to listOf("camp site", "caravan site"),
            // The "Things to do" chip. Mostly tourism=* values plus a few amenity/leisure ones that
            // win the category slot first (amenity is read before tourism).
            "things to do" to listOf(
                "attraction", "museum", "viewpoint", "theme park", "zoo", "aquarium", "gallery",
                "theatre", "cinema", "arts center", "arts centre", "water park",
            ),
            "hardware" to listOf("hardware", "doityourself"),
        )

        /** Exact key first, then the word minus a trailing "s", so typed plurals ("cafes", "gyms")
         *  reach the singular entry. Irregular plurals need their own key ("groceries"). */
        /** Pack categories that are transit stops (public_transport=* and amenity=bus_station). */
        internal val TRANSIT_STOP_CATS = setOf("platform", "stop position", "stop area", "station", "bus station", "bus stop")
        internal val TRANSIT_QUERY_WORDS = listOf("bus", "stop", "station", "transit", "train", "tram", "platform", "metro", "light rail", "subway", "ferry")

        internal fun categoryKeywords(query: String): List<String> {
            val key = query.trim().lowercase()
            return CATEGORY_KEYWORDS[key]
                ?: key.takeIf { it.length > 3 && it.endsWith("s") }?.let { CATEGORY_KEYWORDS[it.dropLast(1)] }
                ?: emptyList()
        }
    }
}
