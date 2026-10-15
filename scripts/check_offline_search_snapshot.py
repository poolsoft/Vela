"""Exercise the actual snapshot SQL with live WAL caches; no Android device required."""
from contextlib import closing
import pathlib
import re
import sqlite3
import tempfile

source = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/java/app/vela/backup/OfflineSearchSnapshot.kt"
statements = re.findall(r'db.execSQL\("([^"\n]+)"', source.read_text(encoding="utf-8"))
with tempfile.TemporaryDirectory() as directory:
    root = pathlib.Path(directory)
    with closing(sqlite3.connect(root / "pois.db")) as pois, closing(sqlite3.connect(root / "addresses.db")) as addresses:
        for db in (pois, addresses):
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("PRAGMA wal_autocheckpoint=0")
        pois.execute("CREATE TABLE poi(id TEXT,name TEXT,lat REAL,lng REAL,category TEXT,address TEXT,phone TEXT,website TEXT,hours TEXT)")
        pois.execute("INSERT INTO poi VALUES ('p','Çınar Lokantası',37.6,36.8,'Restaurant',NULL,NULL,NULL,NULL)")
        addresses.execute("CREATE TABLE addr(id TEXT,housenumber TEXT,street TEXT,street_norm TEXT,city TEXT,lat REAL,lng REAL)")
        addresses.execute("CREATE TABLE street(id TEXT,street TEXT,street_norm TEXT,lat REAL,lng REAL)")
        addresses.execute("INSERT INTO addr VALUES ('a','12','İnönü Caddesi','inonu caddesi','Maraş',37.6,36.8)")
        addresses.execute("INSERT INTO street VALUES ('s','İNÖNÜ CADDESİ','inonu caddesi',37.61,36.81)")
        pois.commit()
        addresses.commit()
        assert pathlib.Path(str(root / "pois.db") + "-wal").stat().st_size > 0
        with closing(sqlite3.connect(root / "snapshot.db")) as target:
            target.execute("ATTACH DATABASE ? AS poi_cache", (str(root / "pois.db"),))
            target.execute("ATTACH DATABASE ? AS addr_cache", (str(root / "addresses.db"),))
            for sql in statements:
                if sql.startswith(("CREATE ", "INSERT ")):
                    target.execute(sql)
            assert target.execute("SELECT name,category FROM poi").fetchone() == ("Çınar Lokantası", "Restaurant")
            assert target.execute("SELECT COUNT(*) FROM streetname").fetchone()[0] == 1
            assert target.execute("SELECT a.hn,s.street_norm,a.city FROM addr a JOIN streetname s USING(sid)").fetchone() == ("12", "inonu caddesi", "Maraş")
            assert target.execute("SELECT COUNT(*) FROM streetpt").fetchone()[0] == 1
            assert target.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
            target.commit()
        with closing(sqlite3.connect(root / "snapshot.db")) as restored:
            assert restored.execute("SELECT name FROM poi").fetchone()[0] == "Çınar Lokantası"
            assert restored.execute("SELECT COUNT(*) FROM addr").fetchone()[0] == 1
print("Offline search snapshot: WAL rows, Turkish text and address/street mapping passed.")
