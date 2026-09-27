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
    /** Turkce iyelik, yer tamlamasi ve bulunma eklerini ayristirarak kok kelime varyantlarini dondurur. */
    private fun turkishStemVariants(input: String): Set<String> {
        val out = LinkedHashSet<String>()
        val base = input.trim()
        if (base.length < 3) return out

        val apostropheIdx = base.indexOfAny(charArrayOf('\'', '’', '`'))
        if (apostropheIdx >= 2) {
            out.add(base.substring(0, apostropheIdx))
        }

        val lower = base.lowercase()
        val suffixes = listOf(
            "lari", "leri", "ları", "leri",
            "lar", "ler",
            "casi", "cesi", "cası", "cesi",
            "basi", "besi", "bası", "besi",
            "si", "sı", "su", "sü",
            "da", "de", "ta", "te",
            "ya", "ye",
            "dan", "den", "tan", "ten",
            "i", "ı", "u", "ü",
        )
        for (suf in suffixes) {
            if (lower.endsWith(suf) && base.length - suf.length >= 3) {
                out.add(base.substring(0, base.length - suf.length))
            }
        }
        return out
    }

    private fun norm(s: String): String =
        s.lowercase()
            .replace('ı', 'i').replace('İ', 'i').replace('I', 'i')
            .replace('ş', 's').replace('Ş', 's')
            .replace('ğ', 'g').replace('Ğ', 'g')
            .replace('ü', 'u').replace('Ü', 'u')
            .replace('ö', 'o').replace('Ö', 'o')
            .replace('ç', 'c').replace('Ç', 'c')

    private fun expandTurkishVariants(word: String): List<String> {
        val res = LinkedHashSet<String>()
        val w = word.trim()
        if (w.isEmpty()) return emptyList()
        res.add(w)
        res.add(w.lowercase())
        res.add(w.uppercase())

        val ascii = norm(w)
        res.add(ascii)
        res.add(ascii.uppercase())
        res.add(ascii.replaceFirstChar { it.uppercaseChar() })

        val trDotless = w.lowercase().replace('i', 'ı')
        res.add(trDotless)
        res.add(trDotless.replaceFirstChar { 'I' })
        res.add(trDotless.uppercase())

        val trFull = ascii.replace('i', 'ı').replace('s', 'ş').replace('c', 'ç').replace('g', 'ğ').replace('u', 'ü').replace('o', 'ö')
        res.add(trFull)
        res.add(trFull.replaceFirstChar { if (it == 'ı') 'I' else it.uppercaseChar() })

        return res.filter { it.isNotBlank() }.toList()
    }

    private data class AramaAdayi(
        val place: Place,
        val bm25Skor: Double,
        val rank: Int,
        val kelimeUyumSayisi: Int,
    )

    private fun ftsTemizle(kelime: String): String {
        return kelime.filter { it.isLetterOrDigit() }
    }

    private fun matchRank(p: Place, query: String, stems: List<String>): Int {
        val qNorm = norm(query)
        val pNorm = norm(p.name)
        val candidates = (listOf(qNorm) + stems.map { norm(it) }).filter { it.length >= 2 }.distinct()
        val pWords = pNorm.split(Regex("\\s+")).filter { it.isNotEmpty() }

        // 0: Tam eslesme (Exact match)
        if (pNorm == qNorm || candidates.any { pNorm == it }) return 0

        // 1: Isim arama terimiyle basliyor (Name starts with query)
        if (pNorm.startsWith(qNorm) || candidates.any { pNorm.startsWith(it) }) return 1

        // 2: Isimdeki herhangi bir kelime terimle basliyor (Word starts with query, e.g. Zeytin Ilicasi)
        if (pWords.any { pw -> candidates.any { c -> pw.startsWith(c) } }) return 2

        // 3: Isim icinde geciyor (Name contains query)
        if (pNorm.contains(qNorm) || candidates.any { pNorm.contains(it) }) return 3

        // 4: Kategori veya adreste geciyor
        return 4
    }

    private fun ftsSorgula(
        db: SQLiteDatabase,
        ftsQuery: String,
        limit: Int,
        near: LatLng?,
        term: String,
        stems: List<String>,
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
                    val rank = matchRank(place, term, stems)
                    val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                    val kelimeUyumu = qWords.count { metin.contains(it) }
                    cikti[id] = AramaAdayi(place, bm25, rank, kelimeUyumu)
                }
            }
        }
    }

    private fun likeFallbackSorgula(
        db: SQLiteDatabase,
        term: String,
        words: List<String>,
        stems: List<String>,
        near: LatLng?,
        qWords: List<String>,
        cikti: MutableMap<String, AramaAdayi>,
    ) {
        val allTerms = (listOf(term) + (if (words.size > 1) words else emptyList()) + stems).distinct()
        val nameCat = LinkedHashSet<String>()
        for (w in allTerms) {
            nameCat.addAll(expandTurkishVariants(w))
        }
        val cats = LinkedHashSet<String>().apply {
            addAll(categoryKeywords(term))
            words.forEach { addAll(categoryKeywords(it)) }
        }

        val clauses = ArrayList<String>()
        val args = ArrayList<String>()
        for (t in nameCat) {
            clauses.add("name LIKE ?")
            args.add("%$t%")
            clauses.add("category LIKE ?")
            args.add("%$t%")
        }
        for (c in cats) {
            clauses.add("category LIKE ?")
            args.add("%$c%")
        }
        clauses.add("address LIKE ?")
        args.add("%$term%")
        args.add("%$term%")

        val sql = "SELECT id,name,lat,lng,category,address,phone,website,hours FROM poi " +
            "WHERE ${clauses.joinToString(" OR ")} ORDER BY (name LIKE ?) DESC LIMIT 200"

        runCatching {
            db.rawQuery(sql, args.toTypedArray()).use { c ->
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
                    val rank = matchRank(place, term, stems)
                    val metin = norm(place.name + " " + (place.category ?: "") + " " + (place.address ?: ""))
                    val kelimeUyumu = qWords.count { metin.contains(it) }
                    // LIKE sorgusunda bm25 olmadigi icin rank tabanli varsayilan skor uretilir
                    val varsayilanBm25 = rank.toDouble() * 2.0
                    cikti[id] = AramaAdayi(place, varsayilanBm25, rank, kelimeUyumu)
                }
            }
        }
    }

    fun search(query: String, near: LatLng?, limit: Int = 30): List<Place> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()

        val words = term.split(Regex("\\s+")).filter { it.length >= 2 }
        val stems = (listOf(term) + words).flatMap { turkishStemVariants(it) }.distinct()
        val qWords = ((if (words.size > 1) words else listOf(term)) + stems).map { norm(it) }.distinct()
        val temizKelimeler = words.map { ftsTemizle(norm(it)) }.filter { it.length >= 2 }
        val temizTekTerim = ftsTemizle(norm(term))

        val adayHavuzu = LinkedHashMap<String, AramaAdayi>()
        val tumDbListesi = listOf(helper.readableDatabase) + OfflinePacks.dbs

        for (db in tumDbListesi) {
            if (hasFtsTablosu(db)) {
                // 1. Katman: Kesin AND eslesmesi (Strict AND - Isim alaninda)
                val katman1Query = if (temizKelimeler.isNotEmpty()) {
                    temizKelimeler.joinToString(" AND ") { "name: $it*" }
                } else if (temizTekTerim.isNotEmpty()) {
                    "name: $temizTekTerim*"
                } else {
                    ""
                }
                ftsSorgula(db, katman1Query, 100, near, term, stems, qWords, adayHavuzu)

                // 2. Katman: Eger sonuc azsa Kategori ve Adres hibrit arama
                if (adayHavuzu.size < 15 && temizKelimeler.isNotEmpty()) {
                    val katman2Query = temizKelimeler.joinToString(" AND ") {
                        "(name: $it* OR category: $it* OR address: $it*)"
                    }
                    ftsSorgula(db, katman2Query, 100, near, term, stems, qWords, adayHavuzu)
                }

                // 3. Katman: Eger hala cok azsa toleransli OR arama
                if (adayHavuzu.size < 5) {
                    val tumAdayKokler = (temizKelimeler + stems.map { ftsTemizle(norm(it)) })
                        .filter { it.length >= 2 }
                        .distinct()
                    if (tumAdayKokler.isNotEmpty()) {
                        val katman3Query = tumAdayKokler.joinToString(" OR ") { "name: $it*" }
                        ftsSorgula(db, katman3Query, 100, near, term, stems, qWords, adayHavuzu)
                    }
                }
            } else {
                // FTS5 tablosu bulunmayan eski .poipack dosyalari icin geriye donuk uyumlu LIKE fallback
                likeFallbackSorgula(db, term, words, stems, near, qWords, adayHavuzu)
            }
        }

        // Konum ve Metin Hibrit Skorlamasi (Re-Ranking)
        val transitSorgusu = TRANSIT_QUERY_WORDS.any { term.lowercase().contains(it) }

        return adayHavuzu.values.sortedWith { a1, a2 ->
            // 1. Transit durak filtresi (Kullanici durak aramiyorsa duraklar alta duser)
            val t1 = if (!transitSorgusu && (a1.place.category ?: "").lowercase() in TRANSIT_STOP_CATS) 1 else 0
            val t2 = if (!transitSorgusu && (a2.place.category ?: "").lowercase() in TRANSIT_STOP_CATS) 1 else 0
            if (t1 != t2) return@sortedWith t1.compareTo(t2)

            // 2. Birebir tam eslesme (Exact match daima en ustte)
            val tam1 = if (a1.rank == 0) 0 else 1
            val tam2 = if (a2.rank == 0) 0 else 1
            if (tam1 != tam2) return@sortedWith tam1.compareTo(tam2)

            // 3. Hibrit Skor: Metin Alakasi (%75) - Yakinlik Etkisi (%25)
            // bm25 negatif oldugu icin eksi ile carpilinca pozitiflesir (0'a yakin olan daha alakali -> daha yuksek skor)
            val metinSkoru1 = -a1.bm25Skor
            val distKm1 = a1.place.distanceMeters?.let { it / 1000.0 }
            val ceza1 = if (distKm1 != null) Math.log10(distKm1 + 1.0) else 2.0
            val hibrit1 = (metinSkoru1 * 0.75) - (ceza1 * 0.25)

            val metinSkoru2 = -a2.bm25Skor
            val distKm2 = a2.place.distanceMeters?.let { it / 1000.0 }
            val ceza2 = if (distKm2 != null) Math.log10(distKm2 + 1.0) else 2.0
            val hibrit2 = (metinSkoru2 * 0.75) - (ceza2 * 0.25)

            if (hibrit1 != hibrit2) {
                return@sortedWith hibrit2.compareTo(hibrit1) // Yuksek skor once
            }

            // 4. Terimle baslama (rank 1 once)
            if (a1.rank != a2.rank) return@sortedWith a1.rank.compareTo(a2.rank)

            // 5. Kelime uyusma sayisi (fazla olan once)
            if (a1.kelimeUyumSayisi != a2.kelimeUyumSayisi) {
                return@sortedWith a2.kelimeUyumSayisi.compareTo(a1.kelimeUyumSayisi)
            }

            // 6. Mesafe (yakin olan once)
            val d1 = a1.place.distanceMeters ?: Double.MAX_VALUE
            val d2 = a2.place.distanceMeters ?: Double.MAX_VALUE
            d1.compareTo(d2)
        }.map { it.place }.take(limit)
    }

    companion object {
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
            // Turkish category words & chips
            "restoran" to listOf("restaurant", "fast food", "cafe"),
            "restoranlar" to listOf("restaurant", "fast food", "cafe"),
            "yemek" to listOf("restaurant", "fast food"),
            "lokanta" to listOf("restaurant"),
            "kahve" to listOf("cafe", "coffee"),
            "kafe" to listOf("cafe"),
            "benzinlik" to listOf("fuel", "charging station"),
            "benzin" to listOf("fuel"),
            "akaryakit" to listOf("fuel"),
            "akaryakıt" to listOf("fuel"),
            "petrol" to listOf("fuel"),
            "otopark" to listOf("parking"),
            "park" to listOf("park"),
            "market" to listOf("supermarket", "convenience", "grocery"),
            "bakkal" to listOf("convenience", "supermarket"),
            "manav" to listOf("greengrocer"),
            "firin" to listOf("bakery"),
            "fırın" to listOf("bakery"),
            "pastane" to listOf("bakery", "pastry"),
            "eczane" to listOf("pharmacy", "chemist"),
            "hastane" to listOf("hospital", "clinic"),
            "saglik ocagi" to listOf("clinic", "doctors"),
            "sağlık ocağı" to listOf("clinic", "doctors"),
            "doktor" to listOf("doctors", "clinic"),
            "otel" to listOf("hotel", "motel", "guest house"),
            "oteller" to listOf("hotel", "motel"),
            "pansiyon" to listOf("guest house", "hostel"),
            "cami" to listOf("place of worship"),
            "mescit" to listOf("place of worship"),
            "belediye" to listOf("townhall", "public building"),
            "muhtarlik" to listOf("townhall", "public building"),
            "muhtarlık" to listOf("townhall", "public building"),
            "okul" to listOf("school"),
            "universite" to listOf("university", "college"),
            "üniversite" to listOf("university", "college"),
            "banka" to listOf("bank", "atm"),
            "durak" to listOf("bus stop", "platform", "station"),
            "otogar" to listOf("bus station"),
            "istasyon" to listOf("station", "train station"),
            "toki" to listOf("residential", "neighbourhood", "suburb"),
            "site" to listOf("residential"),
            "sitesi" to listOf("residential"),
            "ilica" to listOf("hot spring", "spring", "spa", "resort", "village"),
            "ılıca" to listOf("hot spring", "spring", "spa", "resort", "village"),
            "kaplica" to listOf("hot spring", "spring", "spa", "resort"),
            "kaplıca" to listOf("hot spring", "spring", "spa", "resort"),
            "termal" to listOf("hot spring", "spring", "spa", "resort"),
            "kale" to listOf("castle", "ruins"),
            "kalesi" to listOf("castle", "ruins"),
            "selale" to listOf("waterfall", "attraction"),
            "şelale" to listOf("waterfall", "attraction"),
            "gol" to listOf("water"),
            "göl" to listOf("water"),
            "yayla" to listOf("locality", "village"),
            "koy" to listOf("village"),
            "köy" to listOf("village"),
            "belde" to listOf("town", "village"),
            "mahalle" to listOf("suburb", "neighbourhood"),
            "mahallesi" to listOf("suburb", "neighbourhood"),
            "plaj" to listOf("beach"),
            "plaji" to listOf("beach"),
            "plajı" to listOf("beach"),
            "magara" to listOf("cave_entrance"),
            "mağara" to listOf("cave_entrance"),
            "tepe" to listOf("peak"),
            "dag" to listOf("peak"),
            "dağ" to listOf("peak"),
            "antik kent" to listOf("archaeological_site", "ruins"),
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
