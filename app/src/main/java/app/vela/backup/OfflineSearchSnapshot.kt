package app.vela.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.UUID

/** Read caches through SQLite (including WAL), never copy a live database file. */
object OfflineSearchSnapshot {
    fun create(context: Context): File? {
        val pois = context.getDatabasePath("vela_offline_pois.db").takeIf { it.isFile }
        val addresses = context.getDatabasePath("vela_offline_addr.db").takeIf { it.isFile }
        if (pois == null && addresses == null) return null
        val file = File(context.cacheDir, "vela-search-${UUID.randomUUID()}.db")
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                if (pois != null) db.execSQL("ATTACH DATABASE ? AS poi_cache", arrayOf(pois.absolutePath))
                if (addresses != null) db.execSQL("ATTACH DATABASE ? AS addr_cache", arrayOf(addresses.absolutePath))
                db.beginTransaction()
                try {
                    db.execSQL("CREATE TABLE poi(id TEXT PRIMARY KEY,name TEXT,lat REAL,lng REAL,category TEXT,address TEXT,phone TEXT,website TEXT,hours TEXT)")
                    db.execSQL("CREATE TABLE streetname(sid INTEGER PRIMARY KEY,street TEXT,street_norm TEXT UNIQUE)")
                    db.execSQL("CREATE TABLE addr(hn TEXT,sid INTEGER,city TEXT,lat REAL,lng REAL)")
                    db.execSQL("CREATE TABLE streetpt(sid INTEGER,lat REAL,lng REAL)")
                    if (pois != null) {
                        db.execSQL("INSERT INTO poi SELECT id,name,lat,lng,category,address,phone,website,hours FROM poi_cache.poi")
                        check(count(db, "poi") == count(db, "poi_cache.poi"))
                    }
                    if (addresses != null) {
                        db.execSQL("INSERT OR IGNORE INTO streetname(street,street_norm) SELECT street,street_norm FROM addr_cache.addr UNION SELECT street,street_norm FROM addr_cache.street")
                        db.execSQL("INSERT INTO addr SELECT a.housenumber,s.sid,a.city,a.lat,a.lng FROM addr_cache.addr a LEFT JOIN streetname s ON a.street_norm=s.street_norm")
                        db.execSQL("INSERT INTO streetpt SELECT s.sid,a.lat,a.lng FROM addr_cache.street a LEFT JOIN streetname s ON a.street_norm=s.street_norm")
                        check(count(db, "addr") == count(db, "addr_cache.addr"))
                        check(count(db, "streetpt") == count(db, "addr_cache.street"))
                    }
                    db.execSQL("CREATE INDEX idx_poi_name ON poi(name COLLATE NOCASE)")
                    db.execSQL("CREATE INDEX idx_addr_sid ON addr(sid)")
                    db.execSQL("CREATE INDEX idx_streetpt_sid ON streetpt(sid)")
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                if (count(db, "poi") + count(db, "addr") + count(db, "streetpt") == 0L) {
                    file.delete()
                    return null
                }
            }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error // A missing search snapshot must not be reported as a complete backup.
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }
}
