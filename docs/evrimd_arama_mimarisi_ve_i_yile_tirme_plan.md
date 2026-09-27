# Vela Çevrimdışı Arama Mimarisi Analizi ve İyileştirme Planı

Bu döküman, Vela navigasyon uygulamasındaki çevrimdışı yer (POI) ve adres arama sisteminin mevcut yapısını, tespit edilen yapısal sorunları, FTS5 tabanlı yeni veritabanı şemasını ve konum duyarlı sıralama (re-ranking) mimarisini detaylandırmaktadır.

---

## 1. Mevcut Mimari ve Sorun Analizi

Mevcut sistemde arama akışı `OfflinePoiStore.kt` ve `OfflineAddressStore.kt` üzerinden geleneksel SQLite tabloları ve `LIKE '%terim%'` sorgularıyla yürütülmektedir. Bu kurguda öne çıkan temel sorunlar şunlardır:

### 1.1. B-Tree İndeksinin Geçersiz Kalması
- `poi` tablosunda `CREATE INDEX idx_poi_name ON poi(name COLLATE NOCASE)` indeksi bulunmasına rağmen, sorguda `name LIKE '%terim%'` (başta yüzde işareti) kullanıldığı için SQLite indeks taraması (Index Scan) yapamaz.
- Sistem her aramada **Full Table Scan** (tüm tabloyu baştan sona okuma) yapar. Tablodaki POI sayısı arttıkça arama gecikmesi (latency) katlanarak artar.

### 1.2. Havuz Zehirlenmesi (Pool Poisoning) ve Erken Kesilme
- Kelimeler SQL'e `OR` bağlacı ile gönderilmektedir:
  ```sql
  WHERE (name LIKE '%kahramanmaraş%' OR category LIKE '%kahramanmaraş%')
     OR (name LIKE '%zeytin%' OR category LIKE '%zeytin%')
     OR (name LIKE '%ılıca%' OR category LIKE '%ılıca%')
  LIMIT 400;
  ```
- Bu yaklaşım, `"kahramanmaraş zeytin ılıcası"` arandığında sadece içinde "kahramanmaraş" geçen ilk 400 kaydı (otobüs durakları, bakkallar vb.) çeker. Asıl aranan "Zeytin Ilıcası" kaydı 400 sınırının dışında kalır ve elenir.

### 1.3. Alan Önceliklerinin Birbirine Karışması
- İsim (`name`), kategori (`category`) ve adres (`address`) alanları aynı `OR` koşulu içinde eşit ağırlıkla değerlendirilmektedir.
- Kullanıcı bir mekan ismi ararken, adresi veya kategorisi uyuşan yüzlerce alakasız yer sonuç havuzuna dolarak doğru sonuçların önünü keser.

### 1.4. Mesafenin Metin Uyumu Önüne Geçmesi
- SQL tarafından metin alaka puanı (relevance score) üretilmediği için Kotlin tarafına dönen havuz rastgeledir.
- Kotlin katmanındaki sıralama kullanıcının GPS konumuna fazla öncelik verdiğinde, yakındaki alakasız bir eşleşme uzaktaki birebir tam eşleşmenin önüne geçmektedir.

---

## 2. Önerilen SQLite FTS5 Mimarisi

Çözüm için bölgesel paketlerin (`.poipack` / SQLite `.db`) derleme aşamasında (`poipack_build.py`) SQLite'ın dahili **FTS5 (Full-Text Search)** sanal tablosu ve **BM25** skorlama algoritması entegre edilmelidir.

### 2.1. Optimize Edilmiş Tablo Şeması

```sql
-- 1. Ana POI Tablosu (Mevcut yapı korunur)
CREATE TABLE poi (
    id TEXT PRIMARY KEY,
    name TEXT,
    lat REAL,
    lng REAL,
    category TEXT,
    address TEXT,
    phone TEXT,
    website TEXT,
    hours TEXT
);

-- 2. FTS5 Sanal Tablosu (External Content Modeli ile sıfır veri tekrarı)
CREATE VIRTUAL TABLE poi_fts USING fts5(
    name,
    category,
    address,
    content='poi',
    content_rowid='rowid',
    tokenize='unicode61 remove_diacritics 2'
);

-- 3. Otomatik Senkronizasyon Tetikleyicileri (Triggers)
CREATE TRIGGER poi_ai AFTER INSERT ON poi BEGIN
  INSERT INTO poi_fts(rowid, name, category, address) 
  VALUES (new.rowid, new.name, new.category, new.address);
END;

CREATE TRIGGER poi_ad AFTER DELETE ON poi BEGIN
  INSERT INTO poi_fts(poi_fts, rowid, name, category, address) 
  VALUES('delete', old.rowid, old.name, old.category, old.address);
END;

CREATE TRIGGER poi_au AFTER UPDATE ON poi BEGIN
  INSERT INTO poi_fts(poi_fts, rowid, name, category, address) 
  VALUES('delete', old.rowid, old.name, old.category, old.address);
  INSERT INTO poi_fts(rowid, name, category, address) 
  VALUES (new.rowid, new.name, new.category, new.address);
END;
```

> **Not:** `tokenize='unicode61 remove_diacritics 2'` parametresi, Türkçe karakterlerdeki aksanları ve harf büyüklüklerini (I/ı, İ/i, Ş/s, Ç/c vb.) otomatik olarak normalize ederek harici varyant üretme yükünü hafifletir.

---

## 3. Yeni Arama Akışı ve Sorgu Yapısı

Arama sorgusu tek bir kontrolsüz `OR` yerine önceliklendirilmiş katmanlar halinde çalışmalıdır.

### 3.1. Çok Seviyeli Sorgu Mantığı (Fallback Pipeline)

1. **Katman 1 (Kesin Eşleşme - Strict AND):**
   Kullanıcının girdiği kelimeler öncelikle `AND` ile aranır:
   `ftsQuery = "name: maraş* AND name: ılıca*"`
2. **Katman 2 (Kategori ve İsim Hibrit):**
   Eğer Katman 1'den sonuç dönmezse veya sonuç sayısı < 5 ise:
   `ftsQuery = "name: ılıca* OR (category: thermal AND name: zeytin*)"`
3. **Katman 3 (Genişletilmiş Toleranslı Arama):**
   Kök varyantları devreye sokularak `OR` tabanlı FTS araması yapılır.

### 3.2. Ağırlıklı BM25 Puanlaması

SQL sorgusunda `bm25` fonksiyonuna alan ağırlıkları verilir:
- **`name` Ağırlığı:** `10.0` (İsimde eşleşme en yüksek puanı alır)
- **`category` Ağırlığı:** `2.5` (Kategori uyumu ikincil)
- **`address` Ağırlığı:** `1.0` (Adres bilgisi tamamlayıcı)

```sql
SELECT 
    p.id, 
    p.name, 
    p.lat, 
    p.lng, 
    p.category, 
    p.address, 
    p.phone, 
    p.website, 
    p.hours,
    bm25(poi_fts, 10.0, 2.5, 1.0) AS text_rank
FROM poi_fts f
JOIN poi p ON f.rowid = p.rowid
WHERE poi_fts MATCH :ftsQuery
ORDER BY text_rank ASC
LIMIT 100;
```

---

## 4. Konum ve Metin Hibrit Skorlaması (Re-Ranking)

SQL'den dönen en iyi 100 sonuç Kotlin katmanında hem metin alaka düzeyine hem de kullanıcıya olan mesafesine göre ağırlıklandırılarak son sıralama yapılır:

```kotlin
data class SearchCandidate(
    val place: Place,
    val bm25Score: Double, // Negatif değer; 0'a yakın olan daha alakalı
    val distanceKm: Double?
)

fun rankResults(candidates: List<SearchCandidate>): List<Place> {
    return candidates.sortedByDescending { item ->
        // 1. Metin Skoru: bm25 negatif olduğu için ters çevrilerek normalize edilir
        val textScore = -item.bm25Score

        // 2. Mesafe Skoru: Logaritmik ceza (mesafe arttıkça ceza yumuşatılarak verilir)
        val distancePenalty = if (item.distanceKm != null) {
            Math.log10(item.distanceKm + 1.0)
        } else {
            2.0 // Konum yoksa nötr ceza
        }

        // Metin Alakası (%75) + Yakınlık Etkisi (%25)
        (textScore * 0.75) - (distancePenalty * 0.25)
    }.map { it.place }.take(30)
}
```

---

## 5. Uygulama ve Entegrasyon Adımları

1. **Paket Üretim Betiği (`poipack_build.py`):**
   - SQLite veritabanı oluşturulurken `poi_fts` sanal tablosu ve tetikleyiciler şemaya dahil edilmeli.
   - İndeks boyutu veritabanına yaklaşık %15-20 ek boyut getirir; ancak sorgu süresini 10 kat hızlandırır.
2. **`OfflinePoiStore.kt` Düzenlemesi:**
   - Dinamik SQL oluşturulan karmaşık string builder yapısı sadeleştirilmeli.
   - Doğrudan FTS5 sanal tablosuna `MATCH` sorgusu atacak yardımcı fonksiyon eklenmeli.
3. **Bellek ve UI İyileştirmesi:**
   - 400 satırlık kontrolsüz ham veri havuzu 100 kayda düşürülmeli, UI thread'ini yoran gereksiz bellek içi döngüler engellenmelidir.