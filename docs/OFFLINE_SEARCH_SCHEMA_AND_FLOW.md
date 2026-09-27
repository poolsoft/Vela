# Çevrimdışı Arama Mimarisi, Tablo Yapıları ve Arama Akışı

Bu döküman, Vela içerisindeki çevrimdışı yer (POI) ve adres arama sisteminin veritabanı şemasını, SQL sorgularını, sıralama puanlamasını ve çalışma mantığını detaylandırmaktadır.

---

## 1. Veritabanı ve Tablo Şemaları (SQLite)

Çevrimdışı arama iki temel SQLite kaynağından veri okur:
1. **Bölgesel Paketler (`<bolge-id>.db`, örn. `tr-guney.db`):** Sunucudan indirilen `.poipack` dosyası içerisindeki ana veritabanı.
2. **Kullanıcı Alan İndeksi (`vela_offline_pois.db` ve `vela_offline_addr.db`):** Ekranda elle alan indirildiğinde Overpass API üzerinden doldurulan yerel veritabanı.

Her iki kaynak da aşağıdaki tabloları ve şemayı kullanır:

### 1.1. `poi` Tablosu
Bütün işletmeler, yerleşim yerleri, doğal alanlar, tarihi yerler ve kamu noktaları bu tabloda tutulur.

```sql
CREATE TABLE poi (
    id TEXT PRIMARY KEY,     -- OSM benzersiz kimliği (Örn: "osm:node/12345678", "osm:way/98765432")
    name TEXT,               -- Yer adı (Örn: "Zeytin Ilıcası", "Kahramanmaraş", "A101")
    lat REAL,                -- Enlem (WGS84, Örn: 37.5858)
    lng REAL,                -- Boylam (WGS84, Örn: 36.9254)
    category TEXT,           -- Kategori (Örn: "Town", "Spring", "Fast food", "Fuel", "Pharmacy", "Supermarket")
    address TEXT,            -- Adres (Örn: "Ilıca Mah., Onikişubat, Kahramanmaraş")
    phone TEXT,              -- Telefon (Örn: "+90 344 ...")
    website TEXT,            -- Web sitesi (URL)
    hours TEXT               -- Çalışma saatleri (Örn: "Mo-Su 08:00-22:00")
);

-- Mevcut İndeks:
CREATE INDEX idx_poi_name ON poi(name COLLATE NOCASE);
```

### 1.2. `streetname` Tablosu
Adres aramasında sokak isimlerinin tekrarlanarak yer kaplamasını önlemek için sokak isimleri bu sözlük tablosunda tekilleştirilir.

```sql
CREATE TABLE streetname (
    sid INTEGER PRIMARY KEY, -- Sokak ID (Normalize ismin 63-bit stabil hash değeri)
    street TEXT,             -- Orijinal sokak adı (Örn: "Atatürk Caddesi", "İnönü Bulvarı")
    street_norm TEXT         -- Normalize sokak adı (Küçük harf, kısaltmaları açılmış hali)
);

-- Mevcut İndeks:
CREATE INDEX idx_streetname_norm ON streetname(street_norm);
```

### 1.3. `addr` Tablosu
Kapı numarası bazında bina ve adres noktaları.

```sql
CREATE TABLE addr (
    hn TEXT,                 -- Kapı numarası (Örn: "14", "12/A")
    sid INTEGER,             -- streetname tablosundaki sid referansı
    city TEXT,               -- İlçe / Şehir bilgisi (Örn: "Onikişubat")
    lat REAL,                -- Enlem
    lng REAL                 -- Boylam
);

-- Mevcut İndeksler:
CREATE INDEX idx_addr_sid ON addr(sid);
CREATE INDEX idx_addr_hn ON addr(hn);
CREATE INDEX idx_addr_lat ON addr(lat);
```

### 1.4. `streetpt` Tablosu
Kapı numarası bulunmayan sokak aramalarında sokağın konumunu belirlemek için yol çizgisi üzerinden her 120 metrede bir örneklenmiş koordinatlar.

```sql
CREATE TABLE streetpt (
    sid INTEGER,             -- streetname tablosundaki sid referansı
    lat REAL,                -- Örneklem enlem
    lng REAL                 -- Örneklem boylam
);

-- Mevcut İndeksler:
CREATE INDEX idx_streetpt_sid ON streetpt(sid);
CREATE INDEX idx_streetpt_lat ON streetpt(lat);
```

---

## 2. Şu Anki Arama Akışı

Arama işlemi kullanıcı arama çubuğuna bir metin yazdığında aşağıdaki adımlarla yürütülür:

### Adım 1: Arama Çağrısı ve Yönlendirme
* **Dosya:** [`MapViewModel.kt`](file:///d:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/ui/map/MapViewModel.kt) -> `search(q, near)`
* İnternet kapalıyken veya çevrimiçi arama sonuç vermediğinde doğrudan çevrimdışı motora geçilir:
  - `offlinePoiStore.search(q, near)`: Yer (POI) araması.
  - `addressStore.geocode(q, near)`: Adres / sokak araması.

---

### Adım 2: Terim Normalizasyonu ve Kök Ayıklama
* **Dosya:** [`OfflinePoiStore.kt`](file:///d:/Projects/CarWorkspace/Vela/core/src/main/java/app/vela/core/data/OfflinePoiStore.kt)
* Kullanıcının girdiği kelime parçalanır:
  ```kotlin
  val words = term.split(Regex("\\s+")).filter { it.length >= 2 }
  val stems = (listOf(term) + words).flatMap { turkishStemVariants(it) }.distinct()
  val allTerms = (listOf(term) + (if (words.size > 1) words else emptyList()) + stems).distinct()
  ```
* **Türkçe Ek Temizliği (`turkishStemVariants`):**
  - Kelimenin sonundaki Türkçe çekim ve iyelik ekleri (`-si`, `-sı`, `-da`, `-de`, `-dan`, `-den`, `-leri`, `-ları`, `-cası`, `-cesi` vb.) kesilerek kök alternatifleri üretilir.
  - Örneğin: `"ılıcası"` arandığında kök olarak `"ılıca"` da aday listesine eklenir.

---

### Adım 3: Çift Yönlü Karakter Varyantları (`expandTurkishVariants`)
* SQLite `LIKE` operatörü Türkçe büyük/küçük harf ve noktalı/noktasız `i`/`ı` ayrımında duyarlı olduğu için her kelime için alternatifler üretilir:
  - `"ilica"` -> `["ilica", "ILICA", "Ilıca", "ılıca", "ilıca"]`
  - Bu sayede kullanıcı `i` yazsa da veritabanında `Ilıca` yazan yerler SQL seviyesinde yakalanabilir.

---

### Adım 4: Kategori Eşleştirmesi (`CATEGORY_KEYWORDS`)
* Eğer aranan kelime bir kategori terimiyse (Örn: "benzin", "cafe", "otel", "eczane", "market", "hastane"), bu terim OSM kategorilerine dönüştürülür:
  - `"benzin"` -> `["fuel"]`
  - `"eczane"` -> `["pharmacy"]`
  - `"kahve"` / `"cafe"` -> `["cafe", "coffee"]`
  - `"otel"` -> `["hotel", "motel", "guest house"]`

---

### Adım 5: SQL Sorgusunun Oluşturulması ve Çalıştırılması
Üretilen tüm terimler, kökler ve kategoriler SQL üzerinde `OR` mantığı ile sorgulanır:

```sql
SELECT id, name, lat, lng, category, address, phone, website, hours 
FROM poi 
WHERE 
    (name LIKE '%terim1%' OR category LIKE '%terim1%')
    OR (name LIKE '%terim2%' OR category LIKE '%terim2%')
    OR (category LIKE '%kategori_eslesmesi%')
    OR (address LIKE '%arama_metni%')
ORDER BY (name LIKE '%arama_metni%') DESC 
LIMIT 400;
```

* **Havuz Sınırı:** En fazla 400 kayıt SQL'den çekilir.
* Hem yerel SQLite hem de tüm açık `.db` paketlerinde bu sorgu çalıştırılıp sonuçlar birleştirilir.

---

### Adım 6: Bellek İçi (Kotlin) Sıralama ve Puanlama

SQL'den dönen 400 aday kayıt Kotlin tarafında şu öncelik kurallarına göre sıralanır:

```kotlin
rows.distinctBy { it.id }.sortedWith(
    compareBy<Place> { p -> 
        // 1. Öncelik: Toplu taşıma durakları (Aramada açıkça otobüs/durak geçmiyorsa en arkaya atılır)
        if (!transitQuery && (p.category ?: "").lowercase() in TRANSIT_STOP_CATS) 1 else 0 
    }
    .thenBy { p -> 
        // 2. Öncelik: Eşleşme Derecesi (matchRank)
        // 0 = Tam Eşleşme (Exact match: İsim aranan kelime ile birebir aynı)
        // 1 = Terimle Başlayan (Starts with: İsmin başlangıcı aranan kelime)
        // 2 = Kelime Başı Eşleşmesi (İsimdeki herhangi bir kelime arananla başlıyor, örn. "Zeytin Ilıcası")
        // 3 = İsmin İçinde Geçen (Contains: Kelime ortasında eşleşenler)
        // 4 = Sadece Kategori veya Adreste Eşleşenler
        matchRank(p, term, stems) 
    }
    .thenByDescending { p ->
        // 3. Öncelik: Eşleşen Kelime Sayısı
        // Çok kelimeli aramalarda (örn: "kahramanmaraş ılıca") her iki kelimeyi de içerenler öne geçer
        val hay = norm(p.name + " " + (p.category ?: "") + " " + (p.address ?: ""))
        qWords.count { hay.contains(it) }
    }
    .thenBy { 
        // 4. Öncelik: Mesafe
        // Kullanıcının mevcut konumuna kuş uçuşu en yakın olanlar öne geçer
        it.distanceMeters ?: Double.MAX_VALUE 
    }
).take(30)
```

---

## 3. Mevcut Sistemdeki Sorunlar ve Alakasız Sonuçların Nedenleri

Sistemin alakasız sonuçlar döndürmesine neden olan başlıca yapısal sebepler:

1. **Aşırı Geniş `OR` Mantığı:**
   - Çok kelimeli aramalarda (Örn: `kahramanmaraş zeytin ılıcası`), kelimeler `AND` yerine `OR` ile bağlandığı için içinde sadece `kahramanmaraş` geçen binlerce yer veya içinde sadece `zeytin` geçen bakkallar SQL'den çekilen ilk 400 kaydı doldurur. Asıl aranan yer 400 sınırının dışında kalıp elenebilir.
2. **Kategori ve Adres Aramalarının İsme Bulaşması:**
   - SQL içerisinde `category LIKE ?` ve `address LIKE ?` şartları isim aramasıyla aynı ağırlıkta `OR` ile bağlandığı için, ismi aramayla hiç uyuşmayan fakat adresinde veya kategorisinde o kelime geçen yerler havuza dolmaktadır.
3. **`LIMIT 400` Sınırının Erken Kesmesi:**
   - `%terim%` sorgusu SQLite indeksini kullanamaz (Full table scan yapar). `ORDER BY (name LIKE ?)` ifadesi sadece ham arama metnini kontrol ettiğinden, türetilmiş kökler veya kelimeler için tablodaki rastgele ilk 400 satır gelir.
4. **Mesafe Ağırlığı (Distance Bias):**
   - Kullanıcıdan uzaktaki bir il/ilçe arandığında, yakınlardaki zayıf eşleşmeler mesafe sıralaması yüzünden uzaktaki tam eşleşmenin önüne geçebilmektedir.
5. **Full Text Search (FTS5) Yokluğu:**
   - Klasik SQLite tablosunda `LIKE '%...%'` yerine SQLite'ın dahili FTS5 (Tam Metin Arama) modülü kullanılmadığı için kelime kökleri ve BM25 alaka düzeyi puanlaması veritabanı motoruna yaptırılamamaktadır.

---

## 4. İlgili Kaynak Kod Dosyaları

- **Yer Araması Mantığı & SQL:** [`OfflinePoiStore.kt`](file:///d:/Projects/CarWorkspace/Vela/core/src/main/java/app/vela/core/data/OfflinePoiStore.kt)
- **Adres & Geocoding Mantığı:** [`OfflineAddressStore.kt`](file:///d:/Projects/CarWorkspace/Vela/core/src/main/java/app/vela/core/data/OfflineAddressStore.kt)
- **Arama UI ve Birleştirme:** [`MapViewModel.kt`](file:///d:/Projects/CarWorkspace/Vela/app/src/main/java/app/vela/ui/map/MapViewModel.kt)
- **Veritabanı Paket Oluşturucu (Python):** [`poipack_build.py`](file:///d:/Projects/CarWorkspace/Vela/scripts/poipack_build.py)
